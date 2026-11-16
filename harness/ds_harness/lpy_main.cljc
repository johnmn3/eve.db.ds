(ns ds-harness.lpy-main
  "Basilisp entry point for this repo's harness (the ds-harness.bb-main
   pattern, entered the way scripts/ds_suite.py enters drivers on lpy):
   eve.host-completion must load before the port's namespaces analyze,
   then the mode dispatches to the shared harness namespaces — the SAME
   ds-harness.perf / contract.one code the JVM and bb entries run.

   Usage: basilisp run -p <cp>... -c '(require (quote ds-harness.lpy-main)) (apply (resolve (quote ds-harness.lpy-main/-main)) *command-line-args*)' -- <perf|one> <args...>
   (harness/run-lpy.sh composes exactly that command.)"
  (:require [eve.host-completion]
            [ds-harness.perf :as perf]
            [contract.one :as one]))

(defn -main [& [mode & args]]
  (case mode
    "perf" (apply perf/-main args)
    "one" (apply one/-main args)
    (println (pr-str {:fatal (str "unknown mode " mode)}))))
