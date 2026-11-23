(ns eve.db.ds.chain-autoclose-runner
    "JVM runner for the chain auto-close tests (§8 ruling).

   Run: clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.chain-autoclose-runner
   Exit code is 0 only when every test passes."
    (:require
     [clojure.test :as t]
     [eve.db.ds.test.chain-autoclose]))

(defn -main [& _]
      (let [s (t/run-tests 'eve.db.ds.test.chain-autoclose)]
           (println "CHAIN-AUTOCLOSE SUMMARY:"
                    (select-keys s [:test :pass :fail :error]))
           (System/exit (if (zero? (+ (:fail s 0) (:error s 0))) 0 1))))
