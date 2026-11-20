(ns eve.db.ds.u4-runner
  "JVM test runner for the U4 slices:
     clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.u4-runner [ns [test-name ...]]
   With test names, runs only those vars (via clojure.test/test-vars).
   Defaults to eve.db.ds.entity-test in full. Exits non-zero on any
   fail/error."
  (:require [clojure.test :as t]))

(defn -main [& args]
  (let [ns-sym (symbol (or (first args) "eve.db.ds.entity-test"))]
    (require ns-sym)
    (let [res (if (next args)
                (let [vars (map #(ns-resolve ns-sym (symbol %)) (next args))]
                  (binding [t/*report-counters* (ref t/*initial-report-counters*)]
                    (doseq [v vars] (t/test-vars [v]))
                    @t/*report-counters*))
                (t/run-tests ns-sym))]
      (println :U4-RUNNER-RESULT res)
      (System/exit (if (zero? (+ (:fail res) (:error res))) 0 1)))))
