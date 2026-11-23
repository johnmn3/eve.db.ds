(ns eve.db.ds.test.conn
    "U3 port of upstream 1.8.1's test.conn (plan v2 §7, U3 slice).

   Port changes, all forced by the handle model (plan v2 §4, GUIDE §2):
   - the tdc require becomes eve.db.ds.u3-support (helpers only);
   - (:schema @conn) / (:schema db) become (d/schema handle): a DB
     value is a handle {:atom a :tx n} now, and @conn answers the
     handle, so schema reads go through the public fn.
   Everything else is upstream's test, unchanged."
    (:require
     [clojure.test :as t :refer [is are deftest testing]]
     [eve.db.ds.core :as d]
     [eve.db.ds.db :as db]
     [eve.db.ds.u3-support]))

(def schema
     {:aka {:db/cardinality :db.cardinality/many}})

(def datoms
     #{(d/datom 1 :age  17)
       (d/datom 1 :name "Ivan")})

(deftest test-ways-to-create-conn
         (let [conn (d/create-conn)]
              (is (= #{} (set (d/datoms @conn :eavt))))
              (is (= nil (d/schema @conn))))

         (let [conn (d/create-conn schema)]
              (is (= #{} (set (d/datoms @conn :eavt))))
              (is (= schema (d/schema @conn))))

         (let [conn (d/conn-from-datoms datoms)]
              (is (= datoms (set (d/datoms @conn :eavt))))
              (is (= nil (d/schema @conn))))

         (let [conn (d/conn-from-datoms datoms schema)]
              (is (= datoms (set (d/datoms @conn :eavt))))
              (is (= schema (d/schema @conn))))

         (let [conn (d/conn-from-db (d/init-db datoms))]
              (is (= datoms (set (d/datoms @conn :eavt))))
              (is (= nil (d/schema @conn))))

         (let [conn (d/conn-from-db (d/init-db datoms schema))]
              (is (= datoms (set (d/datoms @conn :eavt))))
              (is (= schema (d/schema @conn)))))

(deftest test-reset-conn!
         (let [conn    (d/conn-from-datoms datoms schema)
               report  (atom nil)
               _       (d/listen! conn #(reset! report %))
               datoms' #{(d/datom 1 :age 20)
                         (d/datom 1 :sex :male)}
               schema' {:email {:db/unique :db.unique/identity}}
               db'     (d/init-db datoms' schema')]
    ;; port change (handle model, §7.1): the report's :db-before
    ;; resolves only while the outgoing DB stays reachable in the
    ;; atom, so the port pins it with keep! before the reset —
    ;; upstream read the DB value straight out of the report.
              (d/keep! conn @conn)
              (d/reset-conn! conn db' :meta)
              (is (= datoms' (set (d/datoms @conn :eavt))))
              (is (= schema' (d/schema @conn)))

              (let [{:keys [db-before db-after tx-data tx-meta]} @report]
                   (is (= datoms  (set (d/datoms db-before :eavt))))
                   (is (= schema  (d/schema db-before)))
                   (is (= datoms' (set (d/datoms db-after :eavt))))
                   (is (= schema' (d/schema db-after)))
                   (is (= :meta   tx-meta))
                   (is (= [[1 :age  17     false]
                           [1 :name "Ivan" false]
                           [1 :age  20     true]
                           [1 :sex  :male  true]]
                          (map (juxt :e :a :v :added) tx-data))))))
