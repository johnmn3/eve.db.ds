(ns eve.db.ds.eve-record
  "UNIT3 (#56): process-local records written once as eve.deftype/deftype.
   Keyword access, assoc/update/dissoc, extra keys (extmap), meta, ->Type
   and map->Type are preserved so existing consumers keep working."
  (:refer-clojure :exclude [deftype])
  #?(:cljs (:require-macros [eve.db.ds.eve-record :refer [def-eve-record]]))
  (:require
    [eve.core :as e]
    [eve.deftype :refer [deftype]]
    [eve.encoding :as enc]))

(defn- unhinted [field]
  (with-meta (symbol (name field)) nil))

(defn- field-keyword [field]
  (keyword (name field)))

(defn- field-access [this field]
  (list (symbol (str ".-" (name field))) this))

(defn- ctor-call
  "Positional ctor including hidden rec-meta and rec-ext."
  [type-name fields this subst-sym subst-val meta-form ext-form]
  (cons (symbol (str (name type-name) "."))
        (concat
          (map (fn [g]
                 (cond
                   (= g subst-sym) subst-val
                   (= g :rec-meta) meta-form
                   (= g :rec-ext) ext-form
                   :else (field-access this g)))
               fields)
          [meta-form ext-form])))

(defmacro def-eve-record
  "Define a process-local Eve type with defrecord's usual constructors,
   field lookup, extmap, and metadata. Extra protocol groups may follow
   the field vector."
  [type-name fields & extras]
  (let [fields (mapv unhinted fields)
        hidden '[rec-meta rec-ext]
        all-fields (into fields hidden)
        ctor (symbol (str "->" (name type-name)))
        map-ctor (symbol (str "map->" (name type-name)))
        this (gensym "this")
        k (gensym "k")
        v (gensym "v")
        nf (gensym "nf")
        o (gensym "o")
        kk (gensym "kk")
        vv (gensym "vv")
        w (gensym "w")
        opts (gensym "opts")
        m (gensym "m")
        type-str (name type-name)
        type-ctor (symbol (str type-str "."))
        meta-acc (field-access this 'rec-meta)
        ext-acc (field-access this 'rec-ext)
        lookup-cases (mapcat (fn [f] [(field-keyword f) (field-access this f)]) fields)
        field-assoc (mapcat
                      (fn [f]
                        [(field-keyword f)
                         (cons type-ctor
                               (concat
                                 (map (fn [g] (if (= g f) v (field-access this g))) fields)
                                 [meta-acc ext-acc]))])
                      fields)
        contains-cases (mapcat (fn [f] [(field-keyword f) true]) fields)
        equiv-checks (map (fn [f] `(= ~(field-access this f) ~(field-access o f))) fields)
        entry-pairs (map (fn [f] `(enc/map-entry ~(field-keyword f) ~(field-access this f))) fields)
        print-pairs (mapcat (fn [f] [(field-keyword f) (field-access this f)]) fields)
        as-map `(into (hash-map ~@print-pairs) (or ~ext-acc {}))
        tag (str "#" (name (ns-name *ns*)) "." type-str)
        known-keys (mapv field-keyword fields)]
    `(do
       (declare ~ctor ~map-ctor)
       (deftype ~type-name ~all-fields
         ~'clojure.lang.ILookup
         (~'valAt [~this ~k]
           (case ~k
             ~@lookup-cases
             (get (or ~ext-acc {}) ~k nil)))
         (~'valAt [~this ~k ~nf]
           (case ~k
             ~@lookup-cases
             (get (or ~ext-acc {}) ~k ~nf)))
         ~'clojure.lang.Associative
         (~'containsKey [~this ~k]
           (or (case ~k ~@contains-cases false)
               (contains? (or ~ext-acc {}) ~k)))
         (~'entryAt [~this ~k]
           (let [found# (case ~k ~@lookup-cases (get (or ~ext-acc {}) ~k ::absent#))]
             (when (not (identical? found# ::absent#))
               (enc/map-entry ~k found#))))
         (~'assoc [~this ~k ~v]
           (case ~k
             ~@field-assoc
             (~type-ctor ~@(map (fn [f] (field-access this f)) fields)
                         ~meta-acc
                         (assoc (or ~ext-acc {}) ~k ~v))))
         ~'clojure.lang.IPersistentMap
         (~'without [~this ~k]
           (if (case ~k ~@contains-cases false)
             (dissoc ~as-map ~k)
             (~type-ctor ~@(map (fn [f] (field-access this f)) fields)
                         ~meta-acc
                         (not-empty (dissoc (or ~ext-acc {}) ~k)))))
         (~'assocEx [~this ~k ~v]
           (if (or (case ~k ~@contains-cases false)
                   (contains? (or ~ext-acc {}) ~k))
             (throw (ex-info (str "Key already present: " ~k) {:key ~k}))
             (.assoc ~this ~k ~v)))
         ~'clojure.lang.Counted
         (~'count [~this] (+ ~(count fields) (count (or ~ext-acc {}))))
         ~'clojure.lang.Seqable
         (~'seq [~this]
           (seq (concat (vector ~@entry-pairs) (seq (or ~ext-acc {})))))
         ~'clojure.lang.IPersistentCollection
         (~'equiv [~this ~o]
           (and (instance? ~type-name ~o)
                ~@equiv-checks
                (= (or ~ext-acc {}) (or ~(field-access o 'rec-ext) {}))))
         (~'cons [~this ~o]
           (cond
             (and (vector? ~o) (= 2 (count ~o)))
             (.assoc ~this (nth ~o 0) (nth ~o 1))
             (map? ~o)
             (reduce (fn [acc# [kk# vv#]] (.assoc acc# kk# vv#)) ~this ~o)
             :else
             (throw (ex-info (str "cons expects a [k v] pair or a map on " ~type-str)
                             {:value ~o}))))
         (~'empty [_#] (~type-ctor ~@(repeat (count fields) nil) nil nil))
         ~'clojure.lang.IHashEq
         (~'hasheq [~this] (hash ~as-map))
         ~'clojure.lang.IMeta
         (~'meta [~this] ~meta-acc)
         ~'clojure.lang.IObj
         (~'withMeta [~this ~m]
           (~type-ctor ~@(map (fn [f] (field-access this f)) fields) ~m ~ext-acc))
         ~'Object
         (~'hashCode [~this] (hash ~as-map))
         (~'equals [~this ~o]
           (and (instance? ~type-name ~o)
                ~@equiv-checks
                (= (or ~ext-acc {}) (or ~(field-access o 'rec-ext) {}))))
         ~'IPrintWithWriter
         (~'-pr-writer [~this ~w ~opts]
           (e/-write ~w (str ~tag (pr-str ~as-map))))
         ~@extras)
       (defn ~ctor [~@fields]
         (~type-ctor ~@fields nil nil))
       (defn ~map-ctor [~m]
         (~type-ctor
           ~@(map (fn [f] `(get ~m ~(field-keyword f))) fields)
           (meta ~m)
           (not-empty (apply dissoc ~m ~known-keys)))))))
