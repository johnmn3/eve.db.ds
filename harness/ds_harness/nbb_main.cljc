(ns ds-harness.nbb-main
  "nbb entry point for this repo's harness (the eve.runner.<host>-main
   pattern, after test/drivers/contract/ds-nbb-main): the process slab
   is initialized before any value is constructed, then the mode
   dispatches to the shared harness namespaces — the SAME
   ds-harness.perf / contract.one code the JVM and bb entries run.

   Usage: nbb --classpath <eve :nbb roots>:<lib>:<drivers>:<harness> -m ds-harness.nbb-main <perf|one> <args...>"
  (:require #?(:cljs [eve.process-slab :as ps])
            [ds-harness.perf :as perf]
            [contract.one :as one]))

(defn- dispatch [mode args]
  (case mode
    "perf" (apply perf/-main args)
    "one" (apply one/-main args)
    (println (pr-str {:fatal (str "unknown mode " mode)}))))

(defn -main [& [mode & args]]
  #?(:cljs (-> (ps/init! :force true)
               (.then (fn [_] (dispatch mode args))))
     :default (dispatch mode args)))
