(ns ds-harness.host-main
  "Entry point for the hosts that run the harness through eve's generic
   launcher pattern (jank, jolt, clojerl, let-go, glojure): Eve's
   host completion first, then the mode dispatches to the SAME
   contract.one / ds-harness.smoke code every other entry runs. On jank
   the process ends through eve.process/exit!, so exit hooks run (the
   domain's close) and buffered output is flushed.

   Usage: harness/run-<host>.sh <one|smoke> <args...>"
  ;; clojerl has no host-completion layer in Eve (eve.runner.clje-main
  ;; loads none)
  (:require #?@(:clje [] :default [[eve.host-completion]])
            [eve.process :as proc]
            [contract.one :as one]
            [ds-harness.smoke :as smoke]))

(defn -main [& [mode & args]]
  (case mode
    "one" (apply one/-main args)
    "smoke" (apply smoke/-main args)
    (println (pr-str {:fatal (str "unknown mode " mode)})))
  ;; jank's own teardown can crash under a live timer thread
  #?(:jank (proc/exit! 0) :default nil))
