(ns eve.db.ds.api-surface-test
  "U6 acceptance (plan v2 §4 gate): the public API surface — every var
   in docs/API-VARS.md (44 upstream vars) plus the rewrite's additive
   keep!/release!/close-conn! — exists, is callable with upstream
   shapes, and returns HOST DATA ONLY: no Eve type (flat-layer
   types, Eve deftype sisters outside this library's own public host
   types, bset internals, Eve atoms) appears in any result. The library's
   own public host types are the sanctioned exceptions: Datom
   (slab or delivered sister), DB / FilteredDB (ditto), TxReport,
   and Entity.

   d/filter is asserted for EXISTENCE only: constructing a
   FilteredDB is blocked at this Eve pin by GAP-6 (the ^:host pred
   reads null on every construction path), an Eve-seat defect filed
   in U2-STATUS.md / U6-STATUS.md — the call is not the surface
   test's to paper over."
  (:require
    [clojure.string :as str]
    [clojure.test :as t :refer [is deftest]]
    [eve.db.ds.core :as d]))

(def api-vars
  "docs/API-VARS.md, in full."
  '[conn-from-datoms conn-from-db conn? create-conn data-readers
    datom datom? datoms db db-with db? empty-db entid entity
    entity-db filter find-datom from-serializable index-range
    init-db is-filtered listen! pull pull-many q reset-conn!
    reset-schema! resolve-tempid rseek-datoms schema seek-datoms
    serializable settings squuid squuid-time-millis tempid touch
    transact transact! transact-async tx0 unlisten! with
    with-schema])

(def additive-vars '[keep! release! close-conn!])

(deftest test-all-api-vars-exist
  (doseq [v (concat api-vars additive-vars)]
    (is (some? (ns-resolve 'eve.db.ds.core v)) (str "missing public var: " v))))

#?(:clj
   (def ^:private allowed-host-types
     #{"eve.db.ds.db.Datom" "eve.db.ds.db.Datom__Flat"
       "eve.db.ds.db.DB" "eve.db.ds.db.DB__Flat"
       "eve.db.ds.db.FilteredDB" "eve.db.ds.db.FilteredDB__Flat"
       "eve.db.ds.db.TxReport"
       "eve.db.ds.conn.Conn"
       "eve.db.ds.impl.entity.Entity"}))

#?(:clj
   (defn- eve-leaks
     "Every value reachable from x through plain host collections
      whose class is an Eve-side type outside the sanctioned set.
      Sanctioned types are leaves: a DB's internals are not results."
     [x]
     (let [acc (volatile! [])]
       (letfn [(walk [v]
                 (cond
                   ;; Functions are code, not data (data-readers' values
                   ;; are the library's own reader fns, as upstream's).
                   (fn? v) nil
                   ;; A §4 handle {:atom a :tx n} is a sanctioned leaf:
                   ;; the Eve atom inside it is the identity token the
                   ;; plan itself puts in users' hands, not a leak.
                   (and (map? v) (= #{:atom :tx} (set (keys v)))) nil
                   (or (map? v) (set? v) (sequential? v))
                   (doseq [e (if (map? v) (concat (keys v) (vals v)) v)] (walk e))
                   (some? v)
                   (let [cn (.getName (class v))]
                     ;; An Eve type = a value that lives in Eve
                     ;; memory: a delivered flat sister (__Flat), a
                     ;; bset/db internal, or a class from Eve's own
                     ;; runtime packages. Host classes of this
                     ;; library that are not Eve values (e.g. the
                     ;; realized future d/transact returns) pass.
                     (when (and (not (contains? allowed-host-types cn))
                                (or (str/includes? cn "__Flat")
                                    (str/starts-with? cn "eve.db.ds.bset.")
                                    (str/starts-with? cn "eve.db.ds.db.")
                                    (str/starts-with? cn "eve.deftype")
                                    (str/starts-with? cn "eve.flat")
                                    (str/starts-with? cn "eve.array")
                                    (str/starts-with? cn "eve.atom")
                                    (str/starts-with? cn "eve.slab")
                                    (str/starts-with? cn "eve.thread")
                                    (str/starts-with? cn "eve.mem")
                                    (str/starts-with? cn "eve.record")
                                    (str/starts-with? cn "eve.codec")))
                       (vswap! acc conj cn)))))]
         (walk x)
         @acc))))

#?(:clj
   (deftest test-api-returns-host-data-only
     (let [schema {:name   {:db/unique :db.unique/identity :db/index true}
                   :friend {:db/valueType :db.type/ref
                            :db/cardinality :db.cardinality/many}}
           conn   (d/create-conn schema)
           report (d/transact! conn [{:db/id 1 :name "Ivan" :friend [2]}
                                     {:db/id 2 :name "Oleg"}])
           db0    (d/db-with (d/empty-db schema)
                    [[:db/add 1 :name "Ivan"] [:db/add 2 :name "Oleg"]])
           check  (fn [label v] (is (= [] (eve-leaks v)) (str label " leaked an Eve type")))]
       (check "tx0" d/tx0)
       (check "data-readers" d/data-readers)
       (check "conn?" (d/conn? conn))
       (check "create-conn" (d/create-conn schema))
       (check "conn-from-db" (d/conn-from-db db0))
       (check "conn-from-datoms" (d/conn-from-datoms (d/datoms db0 :eavt) schema))
       (check "empty-db" (d/empty-db schema))
       (check "init-db" (d/init-db [(d/datom 1 :name "Ivan")] schema))
       (check "db" (d/db conn))
       (check "db?" (d/db? db0))
       (check "db-with" db0)
       (check "with" (d/with db0 [[:db/add 3 :name "Petr"]]))
       (check "with-schema" (d/with-schema db0 (assoc schema :age {})))
       (check "datom" (d/datom 1 :name "Ivan"))
       (check "datom?" (d/datom? (d/datom 1 :name "Ivan")))
       (check "datoms" (d/datoms db0 :eavt))
       (check "seek-datoms" (d/seek-datoms db0 :eavt 1))
       (check "rseek-datoms" (d/rseek-datoms db0 :eavt 2))
       (check "find-datom" (d/find-datom db0 :eavt 1 :name "Ivan"))
       (check "index-range" (d/index-range db0 :name "Ivan" "Oleg"))
       (check "entid" (d/entid db0 [:name "Ivan"]))
       (check "entity" (d/entity db0 1))
       (check "entity-db" (d/entity-db (d/entity db0 1)))
       (check "touch" (d/touch (d/entity db0 1)))
       (check "q" (d/q '[:find ?n :where [?e :name ?n]] db0))
       (check "pull" (d/pull db0 [:name] 1))
       (check "pull-many" (d/pull-many db0 [:name] [1 2]))
       (check "is-filtered" (d/is-filtered db0))
       (check "schema" (d/schema db0))
       (check "settings" (d/settings db0))
       (check "serializable" (d/serializable db0))
       (check "from-serializable" (d/from-serializable (d/serializable db0)))
       (check "transact!" report)
       (check "transact" (d/transact (d/create-conn schema) [[:db/add 1 :name "A"]]))
       (check "transact-async" @(d/transact-async (d/create-conn schema) [[:db/add 1 :name "B"]]))
       (check "tempid" (d/tempid :db.part/user))
       (check "resolve-tempid"
         (let [r (d/transact! (d/create-conn schema) [{:db/id -1 :name "Temp"}])]
           (d/resolve-tempid (:db-after r) (:tempids r) -1)))
       (check "squuid" (d/squuid))
       (check "squuid-time-millis" (d/squuid-time-millis (d/squuid)))
       (check "listen!" (d/listen! conn :surface (fn [_ _])))
       (check "unlisten!" (d/unlisten! conn :surface))
       (check "reset-conn!" (d/reset-conn! (d/create-conn schema) db0))
       (check "reset-schema!" (d/reset-schema! (d/create-conn schema) schema))
       (check "keep!" (d/keep! conn (d/db conn)))
       (check "release!" (d/release! conn (d/db conn)))
       (check "close-conn!" (d/close-conn! (d/create-conn schema))))))
