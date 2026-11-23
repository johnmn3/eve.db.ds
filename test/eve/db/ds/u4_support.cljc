(ns eve.db.ds.u4-support
    "U4 slice fixtures, execution shape (post-U2): DB values are built
   inside swap! on a per-test Eve atom and read inside $, per
   DS-IN-EVE-PLAN v2 §1 — the shape U2's harness proved. Measured
   at pin 323bfe3 (u4-logs/probe1-4): a DB built host-side or in a
   bare $ region is a DB__Flat whose index roots cannot be sliced
   (transact itself throws), and a pure transact inside $ throws
   (datoms built in a read region are Datom__Flat; db.cljc's
   validate-datom casts to Datom — filed in U4-STATUS). So:
   construction and transacts happen in swap! (build! / advance!);
   test bodies bind each DB version once inside one $ and assert
   there. eve.db.ds.core / conn still do not load (they require
   the deleted eve.db.ds.storage; conn is U3), so slices exercise
   U4's namespaces directly on (:db @conn)."
    (:require
     [clojure.test :as t]
     [eve.alpha :as eve] ; loaded before impl.entity compiles: entity's
                        ; trap-row replacement calls eve.alpha/same?
                        ; fully qualified (db.cljc's own require of
                        ; eve.alpha guarantees this order in the lib)
     [eve.db.ds.db :as db])
    #?(:cljs (:require-macros [eve.db.ds.test-macros])))

;; ---------------------------------------------------------------------
;; pure value fns — lawful inside swap! (the construction context)

(defn with
      "Baseline conn/with, verbatim shape: a TxReport from db/transact-tx-data."
      [db tx-data]
      (db/transact-tx-data (db/->TxReport db db [] {} nil) tx-data))

(defn db-with
      "Baseline conn/db-with: (:db-after (with db tx-data))."
      [db tx-data]
      (:db-after (with db tx-data)))

(defn filter-db
      "Baseline core/filter, verbatim shape (core.cljc L247ff): a FilteredDB
   whose pred wraps the caller's [db datom] pred. NOTE: FilteredDB's
   pred is a ^:host field that reads null at pin 323bfe3 (U2 probe2,
   U4-STATUS GAP-6) — pred-dependent reads are blocked on the Eve pin."
      [db pred]
      (db/->FilteredDB db #(pred db %)))

;; ---------------------------------------------------------------------
;; atom harness

(defonce ^:private id-counter (atom 0))

(defn new-conn
      "A fresh Eve atom holding {:db nil}; the U4 stand-in for a conn
   (core/conn are U3). Each test gets its own atom."
      []
      (eve/atom {:id (keyword (str "u4-test-" (swap! id-counter inc)))} nil))

(defn build!
      "Replace the atom's DB with an empty DB over schema, transacted
   with each of txs in order — all inside swap!."
      ([a schema txs]
       (swap! a (fn [_] {:db (reduce db-with (db/empty-db schema {}) txs)}))
       a)
      ([a txs] (build! a nil txs)))

(defn build1!
      "build! with a single tx-data collection."
      [a schema tx-data]
      (build! a schema [tx-data]))

(defn built-db
      "Build a DB from a single tx-data collection inside swap! on atom a
   and return the DB itself. Call INSIDE ($ ...) so the returned DB is
   the live slab value, not a flat sister."
      [a schema tx-data]
      (build1! a schema tx-data)
      (:db @a))

(defn init-tx-conn
      "Build a DB from tx-data inside swap! on a fresh Eve atom, at namespace
   load (outside any region); returns the atom. Read the DB inside
   ($ ...) as (:db @atom). The tx-data is consumed inside the swap!, so
   values are constructed in the atom's domain."
      [schema tx-data]
      (let [a (new-conn)]
           (build1! a schema tx-data)
           a))

(defn init-conn
      "A fresh Eve atom whose DB is (db/init-db (datoms-fn) schema {}).
   datoms-fn is a 0-arg fn invoked INSIDE swap!: Datom values are
   domain-bound — a datom allocated in one atom's domain (or the
   process domain) cannot be conjed into another atom's indexes
   (codec refuses, u4-logs/pull-exec1), and a lazy datom seq cached
   across atoms poisons every later consumer. Constructing the
   datoms inside the target atom's swap! is the lawful shape."
      [datoms-fn schema]
      (let [a (new-conn)]
           (swap! a (fn [_] {:db (db/init-db (datoms-fn) schema {})}))
           a))

(defn advance!
      "Transact tx into the atom's current DB, inside swap!. Lawful
   inside a $ body too (measured, u4-logs/probe4 S1); re-read
   (:db @a) afterwards for the new version."
      [a tx]
      (swap! a (fn [m] (assoc m :db (db-with (:db m) tx))))
      a)

(defn no-namespace-maps [f]
      (binding [*print-namespace-maps* false]
               (f)))

#?(:clj
   (defmethod t/assert-expr 'thrown-msg? [msg form]
              (let [[_ match & body] form]
                   `(try ~@body
                         (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
                         (catch Throwable e#
                                (let [m# (.getMessage e#)]
                                     (if (= ~match m#)
                                         (t/do-report {:type :pass, :message ~msg, :expected '~form, :actual e#})
                                         (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual e#})))
                                e#)))))
