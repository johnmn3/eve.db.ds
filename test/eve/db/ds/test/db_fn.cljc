(ns eve.db.ds.test.db-fn
    "Stored :db/fn feature tests (John's rulings, 2026-11-20;
   DB-FN-STATUS.md). The reshaped upstream scenario lives in
   eve.db.ds.test.transact/test-db-ident-fn; this ns carries the
   feature-level proofs: the canonical unique-email invariant in
   the default kind (the d/db-fn macro — a host fn, stored as
   :resolve), in the SCI opt-in kind (d/sci-fn), and in the
   explicit resolve kind (d/fn-ref + registry); the macro's
   stored shape as pure data; the macro invoking through
   transact!; the explicit d/function map form; the SCI read
   surface; the sandbox boundary; the unresolved-resolve error;
   and the pre-commit host-fn rejection on both entry points
   (transact! and with) and on a db fn's own result."
    (:require
     [clojure.test :as t :refer [is deftest testing]]
     [eve.db.ds.core :as d]
     [eve.db.ds.db-fn :as dbfn]
     #?(:cljs [cljs.reader :as reader]))
  ;; d/db-fn is a macro (see the note in eve.db.ds.test.transact).
    #?(:cljs (:require-macros [eve.db.ds.core :as d])))

(defn- edn-read [s]
       #?(:clj (read-string s) :cljs (reader/read-string s)))

;; ---------------------------------------------------------------------------
;; The canonical invariant: a db fn that aborts unless an email is
;; unique. The same semantics in both kinds.

(defn claim-email-fn
      "Host form of the invariant fn (the :resolve kind calls this;
   the :sci kind sourcifies the same body)."
      [db email owner]
      (if-some [holder (d/entity db [:email email])]
               (if (= (:db/id holder) owner)
                   []
                   (throw (ex-info (str "Email already claimed: " email) {})))
               [[:db/add owner :email email]]))

(def claim-email-macro
     "The invariant fn built by the DEFAULT path: d/db-fn defines the
   body as a host fn var in this namespace, registers it, and this
   value is the stored :resolve map."
     (d/db-fn [db email owner]
              (if-some [holder (d/entity db [:email email])]
                       (if (= (:db/id holder) owner)
                           []
                           (throw (ex-info (str "Email already claimed: " email) {})))
                       [[:db/add owner :email email]])))

(def claim-email-sci
     "The same body through the SCI opt-in: d/sci-fn sourcifies it
   into a stored :sci map."
     (d/sci-fn [db email owner]
               (if-some [holder (d/entity db [:email email])]
                        (if (= (:db/id holder) owner)
                            []
                            (throw (ex-info (str "Email already claimed: " email) {})))
                        [[:db/add owner :email email]])))

(defn- unique-email-scenario [conn]
       (d/transact! conn [{:db/id 1 :name "Ann"}
                          {:db/id 2 :name "Bob"}])
       (d/transact! conn [[:claim-email "a@x.com" 1]])
       (is (= "a@x.com" (:email (d/entity @conn 1))))
       (let [tx-before (:tx @conn)
             err       (try (d/transact! conn [[:claim-email "a@x.com" 2]])
                            nil
                            (catch #?(:clj Throwable :cljs :default) e e))]
            (is (= "Email already claimed: a@x.com"
                   #?(:clj (.getMessage err) :cljs (.-message err))))
            (is (= tx-before (:tx @conn)) "an aborted tx fn commits nothing")
            (is (nil? (:email (d/entity @conn 2)))))
       (d/transact! conn [[:claim-email "b@x.com" 2]])
       (is (= "b@x.com" (:email (d/entity @conn 2)))))

(deftest test-unique-email-invariant-macro
  ;; The default path: the stored value is a :resolve map naming
  ;; the var the macro defined in this namespace.
         (is (= :resolve (:db.fn/kind claim-email-macro)))
         (is (= 'eve.db.ds.test.db-fn
                (symbol (namespace (:db.fn/sym claim-email-macro)))))
         (let [conn (d/create-conn {:email {:db/unique :db.unique/identity}})]
              (d/transact! conn [{:db/ident :claim-email :db/fn claim-email-macro}])
              (unique-email-scenario conn)))

(deftest test-unique-email-invariant-sci
         (let [conn (d/create-conn {:email {:db/unique :db.unique/identity}})]
              (d/transact! conn [{:db/ident :claim-email :db/fn claim-email-sci}])
              (unique-email-scenario conn)))

(deftest test-unique-email-invariant-resolve
         (let [conn (d/create-conn {:email {:db/unique :db.unique/identity}})]
              (d/register-fn! 'eve.db.ds.test.db-fn/claim-email-fn claim-email-fn)
              (try
               (d/transact! conn [{:db/ident :claim-email
                                   :db/fn    (d/fn-ref 'eve.db.ds.test.db-fn/claim-email-fn)}])
               (unique-email-scenario conn)
               (finally
                (d/unregister-fn! 'eve.db.ds.test.db-fn/claim-email-fn)))))

#?(:clj
   (deftest test-resolve-requiring-resolve-jvm
     ;; No registration: on the JVM the symbol resolves through
     ;; requiring-resolve. (ClojureScript has no requiring-resolve;
     ;; the registry is the portable floor, covered above.)
            (let [conn (d/create-conn {:email {:db/unique :db.unique/identity}})]
                 (d/transact! conn [{:db/ident :claim-email
                                     :db/fn    (d/fn-ref 'eve.db.ds.test.db-fn/claim-email-fn)}])
                 (unique-email-scenario conn))))

;; ---------------------------------------------------------------------------
;; Shapes: the macro, the map form, pure data in the DB

(deftest test-macro-shape-is-pure-data
  ;; The default macro stores a :resolve map naming a var in the
  ;; caller's namespace; the name is content-addressed
  ;; (db-fn-<12 hex>), so the same form derives the same map.
         (let [stored (d/db-fn [_db x] (+ x 1))]
              (is (= :resolve (:db.fn/kind stored)))
              (is (= 'eve.db.ds.test.db-fn
                     (symbol (namespace (:db.fn/sym stored)))))
              (is (re-find #"^db-fn-[0-9a-f]{12}$" (name (:db.fn/sym stored))))
              (is (dbfn/stored-fn? stored))
    ;; pure data: an EDN round-trip is identity
              (is (= stored (edn-read (pr-str stored))))
    ;; deterministic: re-evaluating the same form re-registers
    ;; the same name and yields the same stored map
              (is (= stored (d/db-fn [_db x] (+ x 1))))
    ;; and it is what actually sits in the DB as the datom value
              (let [conn (d/create-conn)]
                   (d/transact! conn [{:db/ident :plus-one :db/fn stored}])
                   (is (= #{[stored]}
                          (d/q '[:find ?v :where [?e :db/fn ?v]] @conn))))))

(deftest test-macro-default-invokes-through-transact
  ;; A fn built by the default macro, installed under an ident,
  ;; invoked by name through transact!: the host body runs (it
  ;; uses the full host read API of the caller's namespace).
         (let [conn (d/create-conn)]
              (d/transact! conn [{:db/id 1 :age 31}
                                 {:db/ident :inc-age
                                  :db/fn    (d/db-fn [db eid]
                                                     (let [e (d/entity db eid)]
                                                          [[:db/add eid :age (inc (:age e))]]))}])
              (d/transact! conn [[:inc-age 1]])
              (is (= 32 (:age (d/entity @conn 1))))))

(deftest test-sci-fn-opt-in-shape
  ;; The SCI opt-in macro sourcifies, exactly as d/db-fn did
  ;; before the default flipped: the stored map carries the body
  ;; as a string, and it is pure data.
         (let [stored (d/sci-fn [db x] (+ x 1))]
              (is (= {:db.fn/kind   :sci
                      :db.fn/lang   :clojure
                      :db.fn/params '[db x]
                      :db.fn/code   "(+ x 1)"}
                     stored))
              (is (dbfn/stored-fn? stored))
              (is (= stored (edn-read (pr-str stored))))))

(deftest test-function-map-form
         (let [stored (d/function {:lang   :clojure
                                   :params '[db x]
                                   :code   "[[:db/add 1 :n x]]"})]
              (is (dbfn/stored-fn? stored))
              (let [conn (d/create-conn)]
                   (d/transact! conn [[:db.fn/call stored 41]])
                   (is (= 41 (:n (d/entity @conn 1))))))
         (testing "a form :code is sourcified"
                  (is (= "[[:db/add 1 :n x]]"
                         (:db.fn/code (d/function {:params '[db x]
                                                   :code   '[[:db/add 1 :n x]]}))))))

;; ---------------------------------------------------------------------------
;; The SCI surface and its boundary

(deftest test-sci-surface-has-the-read-api
         (let [conn (d/create-conn)]
              (d/transact! conn [{:db/id 1 :name "Ann"}
                                 {:db/id 2 :name "Bob"}
                                 {:db/ident :count-users
                                  :db/fn    (d/sci-fn [db]
                                                      (let [n (ffirst (d/q '[:find (count ?e)
                                                                             :where [?e :name]]
                                                                           db))]
                                                           [[:db/add 1 :user-count n]]))}])
              (d/transact! conn [[:count-users]])
              (is (= 2 (:user-count (d/entity @conn 1))))))

(deftest test-sci-sandbox-boundary
  ;; eve.db.ds.db/transact-tx-data is not in the surface: the body
  ;; cannot even name it, the invoke fails, and nothing commits.
         (let [conn (d/create-conn)
               _    (d/transact! conn [{:db/ident :evil
                                        :db/fn    (d/sci-fn [db]
                                                            (eve.db.ds.db/transact-tx-data db []))}])
               tx-before (:tx @conn)
               err  (try (d/transact! conn [[:evil]])
                         nil
                         (catch #?(:clj Throwable :cljs :default) e e))]
              (is (some? err))
              (is (= tx-before (:tx @conn)))))

;; ---------------------------------------------------------------------------
;; Errors

(deftest test-unresolved-resolve-fn
         (let [conn (d/create-conn)]
              (d/transact! conn [{:db/ident :ghost
                                  :db/fn    (d/fn-ref 'no.such.ns/fn)}])
              (let [err (try (d/transact! conn [[:ghost]])
                             nil
                             (catch #?(:clj Throwable :cljs :default) e e))]
                   (is (= :eve.db.ds/db-fn-unresolved (:error (ex-data err)))))))

(deftest test-host-fn-rejected-via-with
         (let [db  (d/empty-db)
               err (try (d/with db [{:db/ident :x :db/fn (fn [_db] [])}])
                        nil
                        (catch #?(:clj Throwable :cljs :default) e e))]
              (is (= :eve.db.ds/db-fn-host-fn (:error (ex-data err))))))

(deftest test-fn-in-any-value-position-rejected
         (let [conn (d/create-conn)
               err  (try (d/transact! conn [{:db/id 1 :payload (fn [] 1)}])
                         nil
                         (catch #?(:clj Throwable :cljs :default) e e))]
              (is (= :eve.db.ds/fn-not-storable (:error (ex-data err))))
              (is (nil? (d/entity @conn 1)))))

(defn evil-fn
      "A db fn whose RESULT smuggles a host fn: invoke validates the
   result with the same rule, so the tx still fails pre-commit."
      [_db]
      [{:db/ident :smuggled :db/fn (fn [_db] [])}])

(deftest test-db-fn-result-is-validated
         (let [conn (d/create-conn)]
              (d/register-fn! 'eve.db.ds.test.db-fn/evil-fn evil-fn)
              (try
               (d/transact! conn [{:db/ident :evil
                                   :db/fn    (d/fn-ref 'eve.db.ds.test.db-fn/evil-fn)}])
               (let [tx-before (:tx @conn)
                     err       (try (d/transact! conn [[:evil]])
                                    nil
                                    (catch #?(:clj Throwable :cljs :default) e e))]
                    (is (= :eve.db.ds/db-fn-host-fn (:error (ex-data err))))
                    (is (= tx-before (:tx @conn)))
                    (is (nil? (d/entid @conn :smuggled))))
               (finally
                (d/unregister-fn! 'eve.db.ds.test.db-fn/evil-fn)))))
