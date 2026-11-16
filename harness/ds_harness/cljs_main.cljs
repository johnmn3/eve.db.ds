(ns ds-harness.cljs-main
  "Compiled ClojureScript entry for this repo's harness (shadow-cljs
   :node-script, built by harness/run-cljs.sh): the shootout's `cljs`
   member — Eve's hosts.edn :cljs host, ahead-of-time compiled, NOT the
   SCI-interpreted nbb member. Same shape as contract.one-cljs: the
   mmap_cas addon is loaded explicitly and loudly first (a persistent run
   without it must fail, never fall back to a region nothing forces), then
   the process slab, then the SAME contract.one / ds-harness.smoke code
   every other member runs.

     EVE_MMAP_ADDON=<eve>/build/Release/mmap_cas.node node <out>.js <one|smoke> <args...>"
  (:require [eve.region :as region]
            [eve.process-slab :as ps]
            [contract.one :as one]
            [ds-harness.smoke :as smoke]))

(defn- dispatch [mode args]
  (case mode
    "one" (apply one/-main args)
    "smoke" (apply smoke/-main args)
    (println (pr-str {:fatal (str "unknown mode " mode)}))))

(defn main [& [mode & args]]
  (let [addon-path (or (aget (.-env js/process) "EVE_MMAP_ADDON")
                       (str (.cwd js/process) "/build/Release/mmap_cas.node"))]
    (region/load-native-addon! (js/require addon-path))
    (when-not (region/native-addon-loaded?)
      (throw (ex-info "mmap_cas addon did not load" {:path addon-path})))
    (-> (ps/init! :force true)
        (.then (fn [_] (dispatch mode args)))
        (.catch (fn [e]
                  (println (pr-str {:mark :CLJS-ERROR :error (str e)}))
                  (js/process.exit 1))))))
