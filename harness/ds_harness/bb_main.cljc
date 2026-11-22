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
    ;; ds-harness.perf is required lazily: it is a pre-rewrite driver
    ;; that references eve.db.ds.core/read-in, deleted by the rewrite
    ;; (it fails to load on the JVM too — "No such var: d/read-in"),
    ;; and must not block the modes that do not use it.
    "perf" (do (require 'ds-harness.perf)
               (apply (resolve 'ds-harness.perf/-main) args))
    "one" (apply one/-main args)
    (println (pr-str {:fatal (str "unknown mode " mode)}))))
