(ns eve.db.ds.bset-slice-smoke
  "Runs test-slice with its iteration count bound from upstream's 5
   to 2. Exists because at the pinned Eve's measured per-write cost
   (reflective dispatch in eve.deftype.array/write-slot!) a full
   5-iteration run takes over an hour on a shared cell; a bound run
   is reported as exactly that, never as the full test.
     clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.bset-slice-smoke"
  (:require [clojure.test :as t]
            [eve.db.ds.bset-test]))

(defn -main []
  (let [iters-var (ns-resolve 'eve.db.ds.bset-test 'iters)
        test-var  (ns-resolve 'eve.db.ds.bset-test 'test-slice)]
    (with-redefs-fn {iters-var 2}
      (fn []
        (let [res (binding [t/*report-counters* (ref t/*initial-report-counters*)]
                    (t/test-vars [test-var])
                    @t/*report-counters*)]
          (println :BSET-SLICE-SMOKE-RESULT res)
          (System/exit (if (zero? (+ (:fail res) (:error res))) 0 1)))))))
