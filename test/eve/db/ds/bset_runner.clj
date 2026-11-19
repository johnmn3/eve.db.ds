(ns eve.db.ds.bset-runner
  "JVM test runner for the U1 bset suites:
     clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.bset-runner [ns ...]
   Defaults to eve.db.ds.bset-test. Exits non-zero on any fail/error."
  (:require [clojure.test :as t]))

(defn -main [& args]
  (let [nss (if (seq args)
              (map symbol args)
              ['eve.db.ds.bset-test])]
    (doseq [n nss] (require n))
    (let [{:keys [fail error] :as res} (apply t/run-tests nss)]
      (println :BSET-RUNNER-RESULT res)
      (System/exit (if (zero? (+ fail error)) 0 1)))))
