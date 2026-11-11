(ns contract.only-core
  ;; Pins F-DS-EVE-WRITERS: requiring ONLY eve.db.ds.core must be enough for
  ;; db-with + q. Eve's codec writers (:map, :set, :vec, :list ...) register
  ;; when eve.map / eve.set / eve.vec / eve.list / eve.atom load; the library
  ;; must require them itself. Do NOT add any other eve.* require to this ns.
  (:require [eve.db.ds.core :as d]))

(defn -main [& _]
  (let [db (d/db-with (d/empty-db) [{:db/id 1 :name "Ivan" :age 5}
                                    {:db/id 2 :name "Petr" :age 7 :friends [1]}])
        names (d/q '[:find ?n :where [?e :name ?n]] db)]
    (println "ONLY-CORE-OK" (pr-str (set names)) (count (d/datoms db :eavt)))
    (System/exit (if (= #{["Ivan"] ["Petr"]} (set names)) 0 3))))
