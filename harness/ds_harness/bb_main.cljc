(ns ds-harness.bb-main
  "Babashka entry point for this repo's harness (the eve.runner.<host>-main
   pattern, after the shipped contract.bb-flagship-main): Eve's bb
   completion must load before the port's namespaces analyze — entering
   eve.db.ds directly on bb dies in SCI's deftype analysis because the
   completion's deftype presentation is not yet interned. With
   eve.host-completion required first, the whole port loads.

   Usage: bb -m ds-harness.bb-main <contract|perf|one> <args...>"
  (:require [eve.host-completion]
            [ds-harness.contract-check :as cc]
            [contract.one :as one]))

(defn -main [& [mode & args]]
  (case mode
    "contract" (apply cc/-main args)
    ;; perf is required in the branch, not the ns form: the perf
    ;; namespace carries a dangling reference at this pin
    ;; (d/read-in, defined nowhere), and an eager require made
    ;; every mode pay for it at load.
    "perf" (do (require 'ds-harness.perf)
               (apply (ns-resolve 'ds-harness.perf '-main) args))
    "one" (apply one/-main args)
    (println (pr-str {:fatal (str "unknown mode " mode)}))))
