(ns cliff.read-probe
    "The read side: build PAGES pages (cliff.build-probe's generator), then time delivering every datom out of the conn
   ((ds/datoms db :eavt) through its $ region) N times. Prints microseconds per datom delivered.
     clojure -M:test -m cliff.read-probe [PAGES] [N]"
    (:require [eve.db.ds.core :as ds]
              [cliff.build-probe :as bp]))

(defn -main [& args]
      (let [pages (Long/parseLong (or (first args) "30"))
            n     (Long/parseLong (or (second args) "10"))
            conn  (ds/create-conn bp/schema)
            rng   (java.util.Random. 42)]
           (dotimes [p pages]
                    (doseq [tx (#'bp/page-txs p 30 rng)]
                           (let [tags (keep #(some-> (:block/tags %) first second) tx)]
                                (ds/transact! conn (into (mapv (fn [nm] {:block/name nm}) tags) tx)))))
           (let [db (ds/db conn)
                 cnt (count (ds/datoms db :eavt))]
                (dotimes [_ 3] (count (ds/datoms db :eavt)))   ;; warm
                (let [t0 (System/nanoTime)]
                     (dotimes [_ n] (count (ds/datoms db :eavt)))
                     (let [secs (/ (- (System/nanoTime) t0) 1e9)]
                          (println (format "read: %d datoms x %d deliveries  %.3f s  %.2f us/datom" cnt n secs (/ (* secs 1e6) (* cnt n)))))))
           (shutdown-agents)
           (System/exit 0)))
