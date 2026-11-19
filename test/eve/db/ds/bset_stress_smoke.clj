(ns eve.db.ds.bset-stress-smoke
  "Smoke runner for the stress suite: binds the iteration vars down
   (*iters* 3, *rslice-iters* 3, *overflow-len* 200000) and runs all
   five stress deftests. This verifies the stress SHAPES pass; it is
   not the full upstream iteration counts (see the stress ns
   docstring for why, with the measured rate).
     clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.bset-stress-smoke"
  (:require [clojure.test :as t]
            [eve.db.ds.bset-stress-test :as stress]))

(defn -main []
  (binding [stress/*iters*        3
            stress/*rslice-iters* 3
            stress/*overflow-len* 200000]
    (let [res (t/run-tests 'eve.db.ds.bset-stress-test)]
      (println :BSET-STRESS-SMOKE-RESULT res)
      (System/exit (if (zero? (+ (:fail res) (:error res))) 0 1)))))
