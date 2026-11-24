(ns contract.ds-matrix-cljs
  "The compiled ClojureScript (node) entry of contract.ds-matrix, the
   contract.one-cljs launcher shape: the mmap_cas addon loads first and
   loudly (a persistent conn without it must fail, never fall back), then
   contract.ds-matrix/-main, which initializes the process slab.

     EVE_MMAP_ADDON=<eve>/build/Release/mmap_cas.node node out.js bench|runtime <dir> <label> [size...]"
  (:require [eve.region :as region]
            [contract.ds-matrix :as m]))

(defn main [& args]
  (region/load-native-addon! (js/require (aget (.-env js/process) "EVE_MMAP_ADDON")))
  (apply m/-main args))
