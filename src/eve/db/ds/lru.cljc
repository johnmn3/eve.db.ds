(ns ^:no-doc eve.db.ds.lru
  ;; UNIT1 (DISPATCH #56, one-Eve-implementation): LRU is ONE definition,
  ;; eve.deftype/deftype — a process-local type written once in the
  ;; JVM-interface groups, which every host's eve.types translates (the
  ;; :lpy/:clje arms were already this macro; the :cljs/:clj/:cljr arms
  ;; were the same bodies as five per-host plain deftypes). The print
  ;; group is IPrintWithWriter with eve.core's writer surface (e/-write),
  ;; which eve.deftype realizes per host (print-method on JVM/CLR/basilisp
  ;; faces, the protocol on bb/jank/cljs/nbb).
  ;;
  ;; The cache fn is ONE reify over ONE volatile!: eve.host's volatile!
  ;; is the portable spelling on every host (the beam adapter's is a
  ;; process-val cell, whose cross-process visibility this port accepts;
  ;; the old :clje atom fork recorded the concern in F-CLJE-LRU-CELL —
  ;; Eve-side cell semantics are Eve's to revisit, the app no longer forks).
  ;;
  ;; The basilisp star-capture workaround (F-LPY-REIFY-STAR-CAPTURE) is
  ;; subsumed: the one body names its capture `impl` (plain-named).
  (:refer-clojure :exclude [deftype])
  (:require
    [eve.core :as e]
    [eve.deftype :refer [deftype]]
    [eve.host :refer [volatile! vswap!]]))

(declare assoc-lru cleanup-lru)

(deftype LRU [key-value gen-key key-gen gen limit]
  clojure.lang.ILookup
  (valAt [_ k]           (get key-value k))
  (valAt [_ k not-found] (get key-value k not-found))
  clojure.lang.Associative
  (containsKey [_ k] (contains? key-value k))
  (entryAt [_ k]     (find key-value k))
  (assoc [this k v]  (assoc-lru this k v))
  clojure.lang.Seqable
  (seq [_] (seq key-value))
  IPrintWithWriter
  (-pr-writer [_ writer opts]
    (e/-write writer (pr-str key-value))))

(defn assoc-lru [^LRU lru k v]
  (let [key-value (.-key-value lru)
        gen-key   (.-gen-key lru)
        key-gen   (.-key-gen lru)
        gen       (.-gen lru)
        limit     (.-limit lru)]
    (if-let [g (key-gen k nil)]
      (LRU.
        key-value
        (-> gen-key
          (dissoc g)
          (assoc gen k))
        (assoc key-gen k gen)
        (inc gen)
        limit)
      (cleanup-lru
        (LRU.
          (assoc key-value k v)
          (assoc gen-key gen k)
          (assoc key-gen k gen)
          (inc gen)
          limit)))))

(defn cleanup-lru [^LRU lru]
  (if (> (count (.-key-value lru)) (.-limit lru))
    (let [key-value (.-key-value lru)
          gen-key   (.-gen-key lru)
          key-gen   (.-key-gen lru)
          gen       (.-gen lru)
          limit     (.-limit lru)
          [g k]     (first gen-key)]
      (LRU.
        (dissoc key-value k)
        (dissoc gen-key g)
        (dissoc key-gen k)
        gen
        limit))
    lru))

(defn lru [limit]
  (LRU. {} (sorted-map) {} 0 limit))

(defprotocol ICache
  (-get [this key compute-fn]))

(defn cache [limit]
  (let [impl (volatile! (lru limit))]
    (reify ICache
      (-get [_ key compute-fn]
        (if-some [cached (e/get @impl key nil)]
          (do (vswap! impl assoc-lru key cached)
            cached)
          (let [computed (compute-fn)]
            (vswap! impl assoc-lru key computed)
            computed))))))
