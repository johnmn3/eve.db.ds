(ns ^:no-doc eve.db.ds.lru
  ;; eve.core is required for the :default deftype arm below: it
  ;; spells its protocols ecore/… (the spelling Eve's own type
  ;; sections use), and a qualified protocol name resolves on
  ;; let-go only in a namespace that requires eve.core.
  (:require [eve.core :as ecore]))

(declare assoc-lru cleanup-lru)

#?(:cljs
   (deftype LRU [key-value gen-key key-gen gen limit]
     IAssociative
     (-assoc [this k v] (assoc-lru this k v))
     (-contains-key? [_ k] (-contains-key? key-value k))
     ILookup
     (-lookup [_ k]    (-lookup key-value k nil))
     (-lookup [_ k nf] (-lookup key-value k nf))
     IPrintWithWriter
     (-pr-writer [_ writer opts]
       (-pr-writer key-value writer opts)))
   :clj
   (deftype LRU [^clojure.lang.Associative key-value gen-key key-gen gen limit]
     clojure.lang.ILookup
     (valAt [_ k]           (.valAt key-value k))
     (valAt [_ k not-found] (.valAt key-value k not-found))
     clojure.lang.Associative
     (containsKey [_ k] (.containsKey key-value k))
     (entryAt [_ k]     (.entryAt key-value k))
     (assoc [this k v]  (assoc-lru this k v)))
   :lpy
   (deftype LRU [key-value gen-key key-gen gen limit]
     basilisp.lang.interfaces/IAssociative
     (val-at [_ & args]
       (case (count args)
         1 (get key-value (nth args 0))
         2 (get key-value (nth args 0) (nth args 1))))
     (contains [_ k] (contains? key-value k))
     (entry [_ k]     (find key-value k))
     (assoc [this k v]  (assoc-lru this k v))
     (empty [_] (LRU. {} {} {} 0 limit))
     (seq [_] (seq key-value))
     (__iter__ [_] (python/iter (seq key-value)))
     (cons [this e] (assoc-lru this (key e) (val e))))
   ;; A reader with neither :cljs nor :clj in its features (let-go:
   ;; #{:lg} + :default) selected no arm at all before — the type and
   ;; its ->LRU factory were never defined while the fns below
   ;; reference them (Can't resolve ->LRU). The :default arm carries
   ;; the portable spelling: the protocol names every Eve host's
   ;; type language translates from, unhinted fields.
   :default
   (deftype LRU [key-value gen-key key-gen gen limit]
     ecore/IAssociative
     (-assoc [this k v] (assoc-lru this k v))
     (-contains-key? [_ k] (ecore/-contains-key? key-value k))
     ecore/ILookup
     (-lookup [_ k]    (ecore/-lookup key-value k nil))
     (-lookup [_ k nf] (ecore/-lookup key-value k nf))
     ecore/IPrintWithWriter
     (-pr-writer [_ writer opts]
       (ecore/-pr-writer key-value writer opts))))

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
  (let [*impl (volatile! (lru limit))]
    (reify ICache
      (-get [_ key compute-fn]
        (if-some [cached (get @*impl key nil)]
          (do (vswap! *impl assoc key cached)
            cached)
          (let [computed (compute-fn)]
            (vswap! *impl assoc key computed)
            computed))))))
