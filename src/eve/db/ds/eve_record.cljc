(ns eve.db.ds.eve-record
  "UNIT3 (#56): process-local records written once as eve.deftype/deftype.
   Keyword access, assoc/update/dissoc, extra keys (extmap), meta, ->Type
   and map->Type are preserved so existing consumers keep working."
  (:refer-clojure :exclude [deftype])
  #?(:cljs (:require-macros [eve.db.ds.eve-record :refer [def-eve-record]]))
  (:require
    [eve.core :as e]
    [eve.deftype :refer [deftype]]
    [eve.encoding :as enc]
    [eve.map-entry :as me]))

(defn- unhinted [field]
  (with-meta (symbol (name field)) nil))

(defn- field-keyword [field]
  (keyword (name field)))

(defn- field-access [this field]
  (list (symbol (str ".-" (name field))) this))

(defmacro def-eve-record
  "Define a process-local Eve type with defrecord's usual constructors,
   field lookup, extmap, and metadata. Extra protocol groups may follow
   the field vector. An optional opts map may follow the fields:

     {:map-like false}  — parser AST types: ILookup + meta only, no
       Associative/IPersistentMap. On bb, ILookup+Associative+Counted
       is SciMap mode, and extend-protocol against a SciMap type
       misses (all instances share babashka.impl.SciMap). AST types
       are extended later (IFindVars, IBinding, ...) and must stay
       SciType. Default is map-like true (TxReport, Context, ...).

   On SCI (bb), (Type. ...) is compiled through the ->Type var
   (eve.types factory-via-var?). eve.deftype's positional factory is
   therefore saved as Type-new before ->Type is rebound to the
   public, defrecord-style arity (fields only, hidden rec-meta/rec-ext
   nil)."
  [type-name fields & extras]
  (let [opts (if (map? (first extras)) (first extras) {})
        extras (if (map? (first extras)) (next extras) extras)
        map-like? (get opts :map-like true)
        fields (mapv unhinted fields)
        hidden '[rec-meta rec-ext]
        all-fields (into fields hidden)
        ctor (symbol (str "->" (name type-name)))
        map-ctor (symbol (str "map->" (name type-name)))
        raw (symbol (str (name type-name) "-new"))
        this (gensym "this")
        k (gensym "k")
        v (gensym "v")
        nf (gensym "nf")
        o (gensym "o")
        w (gensym "w")
        pr-opts (gensym "opts")
        m (gensym "m")
        type-str (name type-name)
        meta-acc (field-access this 'rec-meta)
        ext-acc (field-access this 'rec-ext)
        lookup-cases (mapcat (fn [f] [(field-keyword f) (field-access this f)]) fields)
        field-assoc (mapcat
                      (fn [f]
                        [(field-keyword f)
                         (cons raw
                               (concat
                                 (map (fn [g] (if (= g f) v (field-access this g))) fields)
                                 [meta-acc ext-acc]))])
                      fields)
        contains-cases (mapcat (fn [f] [(field-keyword f) true]) fields)
        equiv-checks (map (fn [f] `(= ~(field-access this f) ~(field-access o f))) fields)
        entry-pairs (map (fn [f] `(me/map-entry ~(field-keyword f) ~(field-access this f))) fields)
        print-pairs (mapcat (fn [f] [(field-keyword f) (field-access this f)]) fields)
        as-map `(into (hash-map ~@print-pairs) (or ~ext-acc {}))
        tag (str "#" (name (ns-name *ns*)) "." type-str)
        known-keys (mapv field-keyword fields)
        raw-fields (fn [meta-form ext-form]
                     (concat (map (fn [f] (field-access this f)) fields)
                             [meta-form ext-form]))
        lookup-group
        `(~'clojure.lang.ILookup
          (~'valAt [~this ~k]
            (case ~k
              ~@lookup-cases
              (get (or ~ext-acc {}) ~k nil)))
          (~'valAt [~this ~k ~nf]
            (case ~k
              ~@lookup-cases
              (get (or ~ext-acc {}) ~k ~nf))))
        map-group
        (when map-like?
          `(~'clojure.lang.Associative
            (~'containsKey [~this ~k]
              (or (case ~k ~@contains-cases false)
                  (contains? (or ~ext-acc {}) ~k)))
            (~'entryAt [~this ~k]
              (let [found# (case ~k ~@lookup-cases (get (or ~ext-acc {}) ~k ::absent#))]
                (when (not (identical? found# ::absent#))
                  (me/map-entry ~k found#))))
            (~'assoc [~this ~k ~v]
              (case ~k
                ~@field-assoc
                (~raw ~@(raw-fields meta-acc `(assoc (or ~ext-acc {}) ~k ~v)))))
            ~'clojure.lang.IPersistentMap
            (~'without [~this ~k]
              (if (case ~k ~@contains-cases false)
                (dissoc ~as-map ~k)
                (~raw ~@(raw-fields meta-acc `(not-empty (dissoc (or ~ext-acc {}) ~k))))))
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
            ;; SciMap hash/set membership walks iterator, not seq
            ;; (EveVector as SciMap: "Method not implemented: iterator").
            ~'java.lang.Iterable
            (~'iterator [~this]
              (let [s# (volatile! (seq ~this))]
                (reify java.util.Iterator
                  (hasNext [_#] (some? @s#))
                  (next [_#]
                    (if-let [xs# @s#]
                      (do (vreset! s# (next xs#)) (first xs#))
                      (throw (java.util.NoSuchElementException.)))))))
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
            (~'empty [_#] (~raw ~@(repeat (count fields) nil) nil nil))))
        always-group
        `(~'clojure.lang.IHashEq
          (~'hasheq [~this] (hash ~as-map))
          ~'clojure.lang.IMeta
          (~'meta [~this] ~meta-acc)
          ~'clojure.lang.IObj
          (~'withMeta [~this ~m]
            (~raw ~@(raw-fields m ext-acc)))
          ~'Object
          (~'hashCode [~this] (hash ~as-map))
          (~'equals [~this ~o]
            (and (instance? ~type-name ~o)
                 ~@equiv-checks
                 (= (or ~ext-acc {}) (or ~(field-access o 'rec-ext) {}))))
          ~'IPrintWithWriter
          (~'-pr-writer [~this ~w ~pr-opts]
            (e/-write ~w (str ~tag (pr-str ~as-map)))))]
    `(do
       (declare ~ctor ~map-ctor ~raw)
       (deftype ~type-name ~all-fields
         ~@lookup-group
         ~@(or map-group ())
         ~@always-group
         ~@extras)
       (def ~raw ~ctor)
       ;; bb's present-deftype only stamps ->T when it appends
       ;; IPersistentMap to a piecemeal ILookup/Associative type.
       ;; We already name IPersistentMap, so that hook is skipped;
       ;; stamp Type-new ourselves so instance? honors the :type
       ;; meta (all SciMap instances share one Java class). resolve
       ;; keeps this off the reader-conditional count.
       ~@(when map-like?
           `((when-let [present# (resolve 'eve.host-completion/present-map-mode-type!)]
               (present# (var ~raw) ~type-name))))
       (defn ~ctor [~@fields]
         (~raw ~@fields nil nil))
       (defn ~map-ctor [~m]
         (~raw
           ~@(map (fn [f] `(get ~m ~(field-keyword f))) fields)
           (meta ~m)
           (not-empty (apply dissoc ~m ~known-keys)))))))
