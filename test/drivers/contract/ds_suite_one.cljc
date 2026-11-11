(ns contract.ds-suite-one
  "Runs ONE upstream suite namespace (arg) in this process through
   contract.ds-suite (same counting, summary line and exit code). Fresh
   process = fresh default domain (F14 isolation)."
  (:require [contract.ds-suite :as suite]))
(defn -main [& [ns-name]]
  (apply suite/-main (when ns-name [ns-name])))
