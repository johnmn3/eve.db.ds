(ns contract.ds-trace
    (:require [eve.host] [eve.atom] [eve.db.ds.core :as d]))
;; Host driver for clj-family seats (clj/jank/lpy/bb), never
;; compiled as CLJS: kondo's cljs pass reads the host/:default
;; arms and cannot resolve host names (System, Throwable, future,
;; jank classes). Per-form ignore for that pass artifact.
#_{:clj-kondo/ignore [:unresolved-namespace :unresolved-symbol]}
(defn -main [& _]
      (let [conn (d/create-conn)]
           (d/transact! conn [{:db/id 1 :name "Ivan" :age 34}
                              {:db/id 2 :name "Petr" :age 22}
                              {:db/id 3 :name "Oleg" :age 34}])
           (try
            (println {:join-two (d/q '[:find [?n ?a] :where [?e :name ?n] [?e :age ?a]] @conn)})
            (catch Throwable t
                   (println {:threw (str (type t) " " (.getMessage t))})
                   (doseq [f (take 30 (.getStackTrace t))] (println {:frame (str f)}))))
           (System/exit 0)))
