(ns eve.db.ds.u2-runner
    "JVM test runner for the U2 slices:
     clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.u2-runner [ns ...]
   Defaults to the db, index and components slices. Exits non-zero
   on any fail/error."
    (:require [clojure.test :as t]))

(defn -main [& args]
      (let [ns-syms (map symbol (or (seq args)
                                    ["eve.db.ds.db-test"
                                     "eve.db.ds.index-test"
                                     "eve.db.ds.components-test"]))]
           (doseq [ns-sym ns-syms] (require ns-sym))
           (let [res (apply t/run-tests ns-syms)]
                (println :U2-RUNNER-RESULT res)
                (System/exit (if (zero? (+ (:fail res) (:error res))) 0 1)))))
