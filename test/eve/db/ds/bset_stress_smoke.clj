(ns eve.db.ds.bset-stress-smoke
    "Smoke runner for the stress suite: binds the iteration vars down
   (*iters* 1, *rslice-iters* 2, *overflow-len* 50000) and runs the
   five stress deftests ONE AT A TIME, flushing a checkpoint line
   after each so a killed process still leaves per-test verdicts in
   the log. This verifies the stress SHAPES pass; it is not the full
   upstream iteration counts (see the stress ns docstring for why,
   with the measured rate).
     clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.bset-stress-smoke"
    (:require [clojure.test :as t]
              [eve.db.ds.bset-stress-test :as stress]))

(defn -main []
      (binding [stress/*iters*        1
                stress/*rslice-iters* 2
                stress/*overflow-len* 50000]
               (let [vars [#'stress/stresstest-btset
                           #'stress/stresstest-slice
                           #'stress/stresstest-rslice
                           #'stress/stresstest-seek
                           #'stress/test-overflow]
                     totals (atom {:pass 0 :fail 0 :error 0})]
                    (doseq [v vars]
                           (let [res (binding [t/*report-counters* (ref t/*initial-report-counters*)]
                                              (t/test-vars [v])
                                              @t/*report-counters*)]
                                (swap! totals (fn [m] (merge-with + m (select-keys res [:pass :fail :error]))))
                                (println :BSET-STRESS-CHECKPOINT (-> v meta :name) res)
                                (.flush *out*)))
                    (println :BSET-STRESS-SMOKE-RESULT @totals)
                    (.flush *out*)
                    (System/exit (if (zero? (+ (:fail @totals) (:error @totals))) 0 1)))))
