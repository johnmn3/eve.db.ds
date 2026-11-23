(ns ds-harness.lpy-main
  "Basilisp entry point for this repo's harness (the ds-harness.bb-main
   pattern, entered the way scripts/ds_suite.py enters drivers on lpy):
   eve.host-completion must load before the port's namespaces analyze,
   then the mode dispatches to the shared harness namespaces — the SAME
   ds-harness.perf / contract.one code the JVM and bb entries run.

   Usage: basilisp run -p <cp>... -c '(require (quote ds-harness.lpy-main)) (apply (resolve (quote ds-harness.lpy-main/-main)) *command-line-args*)' -- <perf|one> <args...>
   (harness/run-lpy.sh composes exactly that command.)"
  (:require [eve.host-completion]
            [contract.one :as one]))

(defn -main [& [mode & args]]
  (case mode
    ;; ds-harness.perf is required lazily: it is a pre-rewrite driver
    ;; that references eve.db.ds.core/read-in, deleted by the rewrite
    ;; (it fails to load on the JVM too — "No such var: d/read-in"),
    ;; and must not block the modes that do not use it.
    "perf" (do (require 'ds-harness.perf)
               (apply (resolve 'ds-harness.perf/-main) args))
    "one" (apply one/-main args)
    (println (pr-str {:fatal (str "unknown mode " mode)}))))
