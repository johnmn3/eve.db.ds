(ns contract.ds-shootout-cljs
  "The compiled ClojureScript (node) row of contract.ds-shootout: load
   eve's mmap_cas addon loudly, ready the process slab, then run the
   driver's mode.

     EVE_MMAP_ADDON=<eve>/build/Release/mmap_cas.node node ds-shootout.js <mode> <dir> ..."
  (:require [eve.region :as region]
            [eve.process-slab :as ps]
            [contract.ds-shootout]))

(defn main [& args]
  (let [addon-path (or (aget (.-env js/process) "EVE_MMAP_ADDON")
                       (str (.cwd js/process) "/build/Release/mmap_cas.node"))]
    (region/load-native-addon! (js/require addon-path))
    (when-not (region/native-addon-loaded?)
      (throw (ex-info "mmap_cas addon did not load" {:path addon-path})))
    (-> (ps/init! :force true)
        (.then (fn [_] (apply contract.ds-shootout/-main args)))
        (.catch (fn [e]
                  (println (pr-str {:mode :cljs-main :fatal (str e)}))
                  (js/process.exit 1))))))
