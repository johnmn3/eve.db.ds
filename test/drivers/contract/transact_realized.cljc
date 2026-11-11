(ns contract.transact-realized
  "Asserts that (realized? (d/transact conn tx)) is true on every host,
   including nbb, now that eve.thread Promise/Future implement IPending."
  (:require [eve.db.ds.core :as d]))

(defn -main [& _]
  (let [conn (d/create-conn)
        fut  (d/transact conn [{:name "Ivan"}])]
    (when-not (realized? fut)
      (throw (ex-info "TRANSACT-REALIZED-FAIL" {:fut (pr-str fut)})))
    (let [report @fut]
      (when-not (and (map? report) (contains? report :db-after))
        (throw (ex-info "TRANSACT-DEREF-FAIL" {:report report}))))
    (println "TRANSACT-REALIZED-OK")))
