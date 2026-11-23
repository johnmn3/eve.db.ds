(ns contract.ds-dbprobe
    (:require [eve.host] [eve.atom] [eve.db.ds.core :as d]))
;; Host driver for clj-family seats (clj/jank/lpy/bb), never
;; compiled as CLJS: kondo's cljs pass reads the host/:default
;; arms and cannot resolve host names (System, Throwable, future,
;; jank classes). Per-form ignore for that pass artifact.
#_{:clj-kondo/ignore [:unresolved-namespace]}
(defn -main [& _]
      (let [db (d/db-with (d/empty-db {:aka {:db/cardinality :db.cardinality/many}})
                          [{:db/id 1 :name "Ivan" :aka ["IV" "Terrible"]}
                           {:db/id 2 :name "Petr" :age 37 :huh? false}])]
           (println {:count (count db) :keys (set (keys db)) :map? (map? db)
                     :seqable-eavt (seqable? (:eavt db))
                     :find-datom (d/find-datom db :eavt 1 :name)}))
      (System/exit 0))
