(ns ^:no-doc eve.db.ds.query
  #?(:cljs (:require-macros [eve.record :as erecord]))
  (:require
    ;; Eve's EDN reader: one portable libspec (see eve.db.ds.core).
    [eve.edn :as edn]
    [clojure.set :as set]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [eve.db.ds.built-ins :as built-ins]
    [eve.db.ds.db :as db]
    [eve.record :as erecord]
    #?(:cljs [eve.record])
    [me.tonsky.persistent-sorted-set.arrays :as da]
    [eve.db.ds.lru :as lru]
    [eve.db.ds.impl.entity :as de]
    ;; PORT DELTA (F-NBB-MACROS round): as in eve.db.ds.conn — no
    ;; :cljs type refers; parser types are named qualified (dp/...)
    ;; at their uses in this file
    [eve.db.ds.parser :as dp]
    [eve.db.ds.pull-api :as dpa]
    [eve.db.ds.util :as util]
    ;; PORT DELTA (F-CLJE-HOST-REFERS): see eve.db.ds.util
    #?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! to-array into-array long]]))
  #?(:clj
     (:import
       [eve.db.ds.parser BindColl BindIgnore BindScalar BindTuple
        Constant FindColl FindRel FindScalar FindTuple PlainSymbol
        RulesVar SrcVar Variable]) :cljr (:import
       [eve.db.ds.parser BindColl BindIgnore BindScalar BindTuple
        Constant FindColl FindRel FindScalar FindTuple PlainSymbol
        RulesVar SrcVar Variable])))

#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))

;; ----------------------------------------------------------------------------

(def ^:dynamic *query-cache*
  (db/call-in-parse-domain (fn [] (lru/cache 100))))

(declare -collect collect -resolve-clause resolve-clause)

;; Records

(erecord/defrecord Context [rels sources rules])

;; attrs:
;;    {?e 0, ?v 1} or {?e2 "a", ?age "v"}
;; tuples:
;;    [#js [1 "Ivan" 5 14] ...]
;; or [(Datom. 2 "Oleg" 1 55) ...]
(erecord/defrecord Relation [attrs tuples])

#?(:clj
   (defmethod print-method Relation [r, ^java.io.Writer w]
     (.write w "#Relation{:attrs ")
     (.write w (pr-str (:attrs r)))
     (.write w ", :tuples [")
     (.write w (str/join " " (map seq (:tuples r))))
     (.write w "]}")) :cljr (defmethod print-method Relation [r, ^System.IO.TextWriter w]
     (.Write w "#Relation{:attrs ")
     (.Write w (pr-str (:attrs r)))
     (.Write w ", :tuples [")
     (.Write w (str/join " " (map seq (:tuples r))))
     (.Write w "]}")))


;; Utilities

(defn intersect-keys [attrs1 attrs2]
  (set/intersection (set (keys attrs1))
    (set (keys attrs2))))

(defn same-keys? [a b]
  (and (= (count a) (count b))
    (every? #(contains? b %) (keys a))
    (every? #(contains? a %) (keys b))))

(defn- looks-like? [pattern form]
  (cond
    (= '_ pattern)
    true
    (= '[*] pattern)
    (sequential? form)
    (symbol? pattern)
    (= form pattern)
    (sequential? pattern)
    (if (= (last pattern) '*)
      (and (sequential? form)
        (every? (fn [[pattern-el form-el]] (looks-like? pattern-el form-el))
          (map vector (butlast pattern) form)))
      (and (sequential? form)
        (= (count form) (count pattern))
        (every? (fn [[pattern-el form-el]] (looks-like? pattern-el form-el))
          (map vector pattern form))))
    :else ;; (predicate? pattern)
    (pattern form)))

(defn source? [sym]
  (and (symbol? sym)
    (= \$ (first (name sym)))))

(defn free-var? [sym]
  (and (symbol? sym)
    (= \? (first (name sym)))))

(defn attr? [form]
  (or (keyword? form) (string? form)))

(defn lookup-ref? [form]
  (and
    (or (sequential? form) (da/array? form))
    (= 2 (count form))
    (attr? (first form))))

;; Relation algebra

#?(:clj (set! *unchecked-math* true) :cljr (set! *unchecked-math* true))

;; PORT DELTA (F-NBB-PRED-TUPLE, fix-for-release B-extra): vanilla's
;; :cljs arm reads every relation tuple with a JS aget — an index into a
;; JS array, or a property read on a Datom whose fields are named
;; e/a/v/tx. In the port da/aget is nth on a vector, so a Datom tuple
;; read by a field name threw "Index argument to nth must be a number"
;; on nbb for every predicate / fn-binding clause and every join over a
;; Datom relation. tuple-get reads a port array (vector) or a JS array by
;; index and anything else (a Datom) by get, the :clj arm's read.
;; PORT DELTA (F-NBB-PRED-TUPLE): read a port array (Eve vector) by
;; index via da/aget and anything else (a Datom, or a JS array on cljs)
;; via get. Lifted off the :cljs-only defn so the getter-fn call sites
;; can share one portable helper. #274/#275/#277/#278 also read through
;; this helper (array by index, Datom by get).
(defn- tuple-get [t i]
  (if (da/array? t)
    (da/aget t i)
    (get t i)))

;; PORT (PORTING-DS.md / F-NBB-PRED-TUPLE): tuples and idx arrays are
;; da arrays (Eve vectors) in this port, not host Object[]; writes are
;; functional (da/aset returns the new array). tuple-get reads a port
;; array by index and a Datom by get.
(defn join-tuples [t1 idxs1 t2 idxs2]
  (let [l1   (count idxs1)
        l2   (count idxs2)
        pick (fn [t idxs i] (tuple-get t (tuple-get idxs i)))
        res  (reduce (fn [r i] (da/aset r i (pick t1 idxs1 i)))
                     (da/make-array (+ l1 l2)) (range l1))
        res  (reduce (fn [r i] (da/aset r (+ l1 i) (pick t2 idxs2 i)))
                     res (range l2))]
    res))

#?(:clj (set! *unchecked-math* false) :cljr (set! *unchecked-math* false))

(defn- sum-rel* [attrs-a tuples-a attrs-b tuples-b]
  (let [idxb->idxa (vec (for [[sym idx-b] attrs-b]
                          [idx-b (attrs-a sym)]))
        tlen    (->> (vals attrs-a) (reduce max) (inc))
        tuples' (persistent!
                  (reduce
                    (fn [acc tuple-b]
                      ;; PORT (D2): tuple' is a da array (Eve vector);
                      ;; vanilla's aset mutation becomes a threaded
                      ;; da/aset reduce. Was the query-or suite error:
                      ;; "No matching method aset found taking 3 args".
                      (let [tuple' (reduce (fn [t [idx-b idx-a]]
                                             (da/aset t idx-a (get tuple-b idx-b)))
                                     (da/make-array tlen)
                                     idxb->idxa)]
                        (conj! acc tuple')))
                    (transient (vec tuples-a))
                    tuples-b))]
    (->Relation attrs-a tuples')))

(defn sum-rel [a b]
  (let [{attrs-a :attrs, tuples-a :tuples} a
        {attrs-b :attrs, tuples-b :tuples} b]
    (cond
      (= attrs-a attrs-b)
      (->Relation attrs-a (into (vec tuples-a) tuples-b))

      ;; BEFORE checking same-keys
      ;; because one rel could have had its resolution shortcircuited
      (empty? tuples-a) b
      (empty? tuples-b) a

      (not (same-keys? attrs-a attrs-b))
      (util/raise "Can’t sum relations with different attrs: " attrs-a " and " attrs-b
        {:error :query/where})

      (every? number? (vals attrs-a)) ;; can’t conj into BTSetIter
      (sum-rel* attrs-a tuples-a attrs-b tuples-b)

      :else
      (let [number-attrs (zipmap (keys attrs-a) (range))]
        (-> (sum-rel* number-attrs [] attrs-a tuples-a)
          (sum-rel b))))))

(defn prod-rel
  ([]
   (->Relation {} [(da/make-array 0)]))
  ([rel1 rel2]
   (let [attrs1 (keys (:attrs rel1))
         attrs2 (keys (:attrs rel2))
         idxs1  (to-array (map (:attrs rel1) attrs1))
         idxs2  (to-array (map (:attrs rel2) attrs2))]
     (->Relation
       (zipmap (concat attrs1 attrs2) (range))
       (persistent!
         (reduce
           (fn [acc t1]
             (reduce (fn [acc t2]
                       (conj! acc (join-tuples t1 idxs1 t2 idxs2)))
               acc (:tuples rel2)))
           (transient []) (:tuples rel1)))))))

;;

(defn parse-rules [rules]
  (let [rules (if (string? rules) (edn/read-string rules) rules)] ;; for JS interop
    (dp/parse-rules rules) ;; validation
    (group-by ffirst rules)))

(defn empty-rel [binding]
  (let [vars (->> (dp/collect-vars-distinct binding)
               (map :symbol))]
    (->Relation (zipmap vars (range)) [])))

;; Dispatch via instance?, not extend-protocol: parser types are SciMap
;; on bb (see eve.db.ds.parser/-find-vars).
(defn in->rel [binding value]
  (cond
    (instance? #?(:jank dp/BindIgnore :clj BindIgnore :cljr BindIgnore :cljs dp/BindIgnore :clje dp/BindIgnore :lpy dp/BindIgnore) binding)
    (prod-rel)

    (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar :clje dp/BindScalar :lpy dp/BindScalar) binding)
    (->Relation {(get-in binding [:variable :symbol]) 0} [(into-array [value])])

    (instance? #?(:jank dp/BindColl :clj BindColl :cljr BindColl :cljs dp/BindColl :clje dp/BindColl :lpy dp/BindColl) binding)
    (cond
      (not (db/seqable? value))
      (util/raise "Cannot bind value " value " to collection " (dp/source binding)
        {:error :query/binding, :value value, :binding (dp/source binding)})
      (empty? value)
      (empty-rel binding)
      :else
      (->> value
        (map #(in->rel (:binding binding) %))
        (reduce sum-rel)))

    (instance? #?(:jank dp/BindTuple :clj BindTuple :cljr BindTuple :cljs dp/BindTuple :clje dp/BindTuple :lpy dp/BindTuple) binding)
    (cond
      (not (db/seqable? value))
      (util/raise "Cannot bind value " value " to tuple " (dp/source binding)
        {:error :query/binding, :value value, :binding (dp/source binding)})
      (< (count value) (count (:bindings binding)))
      (util/raise "Not enough elements in a collection " value " to bind tuple " (dp/source binding)
        {:error :query/binding, :value value, :binding (dp/source binding)})
      :else
      (reduce prod-rel
        (map #(in->rel %1 %2) (:bindings binding) value)))

    :else
    (throw (ex-info (str "No in->rel impl for " (pr-str (type binding))) {:binding binding}))))

(defn resolve-in [context [binding value]]
  (cond
    (and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar :clje dp/BindScalar :lpy dp/BindScalar) binding)
      (instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/SrcVar :lpy dp/SrcVar) (:variable binding)))
    (update context :sources assoc (get-in binding [:variable :symbol]) value)
    (and (instance? #?(:jank dp/BindScalar :clj BindScalar :cljr BindScalar :cljs dp/BindScalar :clje dp/BindScalar :lpy dp/BindScalar) binding)
      (instance? #?(:jank dp/RulesVar :clj RulesVar :cljr RulesVar :cljs dp/RulesVar :clje dp/RulesVar :lpy dp/RulesVar) (:variable binding)))
    (assoc context :rules (parse-rules value))
    :else
    (update context :rels conj (in->rel binding value))))

(defn resolve-ins [context bindings values]
  (let [cb (count bindings)
        cv (count values)]
    (cond
      (< cb cv)
      (util/raise "Extra inputs passed, expected: " (mapv #(:source (meta %)) bindings) ", got: " cv
        {:error :query/inputs :expected bindings :got values})

      (> cb cv)
      (util/raise "Too few inputs passed, expected: " (mapv #(:source (meta %)) bindings) ", got: " cv
        {:error :query/inputs :expected bindings :got values})

      :else
      (reduce resolve-in context (zipmap bindings values)))))

;;

(def ^{:dynamic true
       :doc "Map of symbol in current pattern that might potentially be resolved to refs
             => fn that resolves its values"}
  *lookup-attrs* nil)

(def ^{:dynamic true
       :doc "Default pattern source. Lookup refs, patterns, rules will be resolved with it"}
  *implicit-source* nil)

(defn getter-fn [attrs attr]
  (let [idx (attrs attr)]
    (if-some [resolve-fn (get *lookup-attrs* attr)]
      (if (int? idx)
        (let [idx (int idx)]
          (fn contained-int-getter-fn [tuple]
            (let [eid (tuple-get tuple idx)]
              (cond
                (number? eid)     eid ;; quick path to avoid fn call
                (sequential? eid) (resolve-fn eid)
                (da/array? eid)   (resolve-fn eid)
                :else             eid))))
        ;; If the index is not an int?, the target can never be an array
        (fn contained-getter-fn [tuple]
          (let [eid (tuple-get tuple idx)]
            ;; PORT DELTA (F-CLJE-GETTER-VALAT): a non-int idx is a
            ;; datom component name, read via get (ILookup on clj).
            (cond
              (number? eid)     eid ;; quick path to avoid fn call
              (sequential? eid) (resolve-fn eid)
              (da/array? eid)   (resolve-fn eid)
              :else             eid))))
      (if (int? idx)
        (let [idx (int idx)]
          (fn int-getter [tuple]
            (tuple-get tuple idx)))
        ;; If the index is not an int?, the target can never be an array
        (fn getter [tuple]
          ;; PORT DELTA (F-CLJE-GETTER-VALAT): a non-int idx is a datom
          ;; component name ("e"/"a"/"v"/"tx"); tuple-get uses get, the
          ;; ILookup read, not nth.
          (tuple-get tuple idx))))))

(defn tuple-key-fn
  [attrs common-attrs]
  (let [n (count common-attrs)]
    (if (== n 1)
      (getter-fn attrs (first common-attrs))
      ;; Multi-attr join keys are a vector of the common attrs' values.
      ;; :clje/:lpy already used mapv; the JVM/cljs/jank arms built a
      ;; host Object[] and wrapped it (LazilyPersistentVector, list*,
      ;; vec). mapv is the portable equivalent (hasheq-equal to those
      ;; wrappers) and matches this port's functional arrays.
      (let [getters (mapv #(getter-fn attrs %) common-attrs)]
        (fn [tuple] (mapv #(% tuple) getters))))))

(defn -group-by
  [f init coll]
  (persistent!
    (reduce
      (fn [ret x]
        (let [k (f x)]
          (assoc! ret k (conj (get ret k init) x))))
      (transient {}) coll)))

(defn hash-attrs [key-fn tuples]
  (-group-by key-fn '() tuples))

(defn hash-join [rel1 rel2]
  (let [tuples1       (:tuples rel1)
        tuples2       (:tuples rel2)
        attrs1        (:attrs rel1)
        attrs2        (:attrs rel2)
        common-attrs  (vec (intersect-keys (:attrs rel1) (:attrs rel2)))
        keep-attrs1   (keys attrs1)
        keep-attrs2   (->> attrs2
                        (reduce-kv (fn keeper [vec k _]
                                     (if (attrs1 k)
                                       vec
                                       (conj! vec k)))
                          (transient []))
                        persistent!) ; keys in attrs2-attrs1
        keep-idxs1    (to-array (vals attrs1))
        keep-idxs2    (to-array (->Eduction (map attrs2) keep-attrs2)) ; vals in attrs2-attrs1 by keys
        key-fn1       (tuple-key-fn attrs1 common-attrs)
        key-fn2       (tuple-key-fn attrs2 common-attrs)
        hash          (hash-attrs key-fn1 tuples1)
        new-tuples    (->>
                        tuples2
                        (reduce (fn outer [acc tuple2]
                                  (let [key (key-fn2 tuple2)]
                                    (if-some [tuples1 (get hash key)]
                                      (reduce (fn inner [acc tuple1]
                                                (conj! acc (join-tuples tuple1 keep-idxs1 tuple2 keep-idxs2)))
                                        acc tuples1)
                                      acc)))
                          (transient []))
                        (persistent!))]
    (->Relation (zipmap (concat keep-attrs1 keep-attrs2) (range))
      new-tuples)))

(defn subtract-rel [a b]
  (let [{attrs-a :attrs, tuples-a :tuples} a
        {attrs-b :attrs, tuples-b :tuples} b
        attrs     (vec (intersect-keys attrs-a attrs-b))
        key-fn-b  (tuple-key-fn attrs-b attrs)
        hash      (hash-attrs key-fn-b tuples-b)
        key-fn-a  (tuple-key-fn attrs-a attrs)]
    (assoc a
      :tuples (filterv #(nil? (hash (key-fn-a %))) tuples-a))))

(defn- rel-with-attr [context sym]
  (some #(when (contains? (:attrs %) sym) %) (:rels context)))

(defn substitute-constant [context pattern-el]
  (when (free-var? pattern-el)
    (when-some [rel (rel-with-attr context pattern-el)]
      (when-some [tuple (first (:tuples rel))]
        (when (nil? (fnext (:tuples rel)))
          (let [idx (get (:attrs rel) pattern-el)]
            (tuple-get tuple idx)))))))

(defn substitute-constants [context pattern]
  (mapv #(or (substitute-constant context %) %) pattern))

(defn resolve-pattern-lookup-refs [source pattern]
  (if (satisfies? db/IDB source)
    (let [[e a v tx] pattern
          e'         (if (or (lookup-ref? e) (attr? e))
                       (db/entid-strict source e)
                       e)
          v'         (cond
                       (or (nil? v) (not (attr? a)))
                       v

                       (db/ref? source a)
                       (if (or (lookup-ref? v) (attr? v))
                         (db/entid-strict source v)
                         v)

                       (and (db/tuple? source a) (sequential? v))
                       (db/resolve-tuple-refs source a v pattern)

                       :else
                       v)
          tx'        (if (lookup-ref? tx)
                       (db/entid-strict source tx)
                       tx)]
      (subvec [e' a v' tx'] 0 (count pattern)))
    pattern))

(defn lookup-pattern-db [context db pattern]
  ;; TODO optimize with bound attrs min/max values here
  (let [search-pattern (->> pattern
                         (substitute-constants context)
                         (resolve-pattern-lookup-refs db)
                         (mapv #(if (or (= % '_) (free-var? %)) nil %)))
        datoms         (db/-search db search-pattern)
        attr->prop     (->> (map vector pattern ["e" "a" "v" "tx"])
                         (filter (fn [[s _]] (free-var? s)))
                         (into {}))]
    (->Relation attr->prop datoms)))

(defn matches-pattern? [pattern tuple]
  (loop [tuple   tuple
         pattern pattern]
    (if (and tuple pattern)
      (let [t (first tuple)
            p (first pattern)]
        (if (or (= p '_) (free-var? p) (= t p))
          (recur (next tuple) (next pattern))
          false))
      true)))

(defn lookup-pattern-coll [context coll pattern]
  (let [data       (filter #(matches-pattern? pattern %) coll)
        attr->idx  (->> (map vector pattern (range))
                     (filter (fn [[s _]] (free-var? s)))
                     (into {}))]
    (->Relation attr->idx (mapv to-array data)))) ;; FIXME to-array

(defn normalize-pattern-clause [clause]
  (if (source? (first clause))
    clause
    (concat ['$] clause)))

(defn lookup-pattern [context source pattern]
  (if (satisfies? db/ISearch source)
    (lookup-pattern-db context source pattern)
    (lookup-pattern-coll context source pattern)))

(defn collapse-rels [rels new-rel]
  (loop [rels    rels
         new-rel new-rel
         acc     []]
    (if-some [rel (first rels)]
      (if (not-empty (intersect-keys (:attrs new-rel) (:attrs rel)))
        (recur (next rels) (hash-join rel new-rel) acc)
        (recur (next rels) new-rel (conj acc rel)))
      (conj acc new-rel))))

(defn- context-resolve-val [context sym]
  (when-some [rel (rel-with-attr context sym)]
    (when-some [tuple (first (:tuples rel))]
      (tuple-get tuple ((:attrs rel) sym)))))

(defn- rel-contains-attrs? [rel attrs]
  (some #(contains? (:attrs rel) %) attrs))

(defn- rel-prod-by-attrs [context attrs]
  (let [rels       (filter #(rel-contains-attrs? % attrs) (:rels context))
        production (reduce prod-rel rels)]
    [(update context :rels #(remove (set rels) %)) production]))

(defn -call-fn [context rel f args]
  ;; PORT (PORTING-DS.md): static-args/tuples-args are da arrays — Eve
  ;; vectors here — so the build threads da/aset results instead of
  ;; mutating, and the per-call fill below builds the argument array
  ;; functionally (vanilla mutates a shared static-args; the observable
  ;; result per call is the same).
  (let [sources     (:sources context)
        attrs       (:attrs rel)
        len         (count args)
        [static-args tuples-args]
        (reduce
          (fn [[sa ta] i]
            (let [arg (nth args i)]
              (if (symbol? arg)
                (if-some [source (get sources arg)]
                  [(da/aset sa i source) ta]
                  [sa (da/aset ta i (get attrs arg))])
                [(da/aset sa i arg) ta])))
          [(da/make-array len) (da/make-array len)]
          (range len))]
    ;; issue-262 (cljs apply+vector aliasing a mutable args array) does
    ;; not apply: da arrays are persistent Eve vectors (da/aset is assoc,
    ;; da/aclone is identity), so one portable body is enough.
    (fn [tuple]
      ;; TODO raise if not all args are bound
      (let [args (reduce
                   (fn [a i]
                     (if-some [tuple-idx (da/aget tuples-args i)]
                       ;; vanilla reads via `get`: tuple may be a Datom
                       ;; whose attrs are field names, not indexes.
                       (da/aset a i (tuple-get tuple tuple-idx))
                       a))
                   static-args
                   (range len))]
        (apply f args)))))

(defn- resolve-sym [sym]
  #?(:cljs nil
     :clj (when (namespace sym)
            (when-some [v (resolve sym)] @v)) :cljr (when (namespace sym)
            (when-some [v (resolve sym)] @v))
     :lpy (when (namespace sym)
            (when-some [v (resolve sym)] @v))
     :clje (when (namespace sym)
             (when-some [v (resolve sym)] @v))))

(defn filter-by-pred [context clause]
  (let [[[f & args]] clause
        pred         (or (get built-ins/query-fns f)
                       (context-resolve-val context f)
                       (resolve-sym f)
                       (when (nil? (rel-with-attr context f))
                         (util/raise "Unknown predicate '" f " in " clause
                           {:error :query/where, :form clause, :var f})))
        [context production] (rel-prod-by-attrs context (filter symbol? args))
        new-rel      (if pred
                       (let [tuple-pred (-call-fn context production pred args)]
                         (update production :tuples #(filter tuple-pred %)))
                       (assoc production :tuples []))]
    (update context :rels conj new-rel)))

(defn bind-by-fn [context clause]
  (let [[[f & args] out] clause
        binding  (dp/parse-binding out)
        fun      (or (get built-ins/query-fns f)
                   (context-resolve-val context f)
                   (resolve-sym f)
                   (when (nil? (rel-with-attr context f))
                     (util/raise "Unknown function '" f " in " clause
                       {:error :query/where, :form clause, :var f})))
        [context production] (rel-prod-by-attrs context (filter symbol? args))
        new-rel  (if fun
                   (let [tuple-fn (-call-fn context production fun args)
                         rels     (for [tuple (:tuples production)
                                        :let  [val (tuple-fn tuple)]
                                        :when (not (nil? val))]
                                    (reduce prod-rel
                                      (collapse-rels
                                         [(->Relation (:attrs production) [tuple])]
                                         (in->rel binding val))))]
                     (if (empty? rels)
                       (prod-rel
                         production
                         (empty-rel binding))
                       (reduce sum-rel rels)))
                   (prod-rel
                     (assoc production :tuples [])
                     (empty-rel binding)))]
    (update context :rels collapse-rels new-rel)))

;;; RULES

(defn rule? [context clause]
  (util/cond+
    (not (sequential? clause))
    false

    :let [head (if (source? (first clause))
                 (second clause)
                 (first clause))]

    (not (symbol? head))
    false

    (free-var? head)
    false

    (contains? #{'_ 'or 'or-join 'and 'not 'not-join} head)
    false

    (not (contains? (:rules context) head))
    (util/raise "Unknown rule '" head " in " clause
      {:error :query/where
       :form  clause})

    :else true))

(def rule-seqid (atom 0))

(defn expand-rule [clause context used-args]
  (let [[rule & call-args] clause
        seqid              (swap! rule-seqid inc)
        branches           (get (:rules context) rule)]
    (for [branch branches
          :let [[[_ & rule-args] & clauses] branch
                replacements (zipmap rule-args call-args)]]
      (walk/postwalk
        #(if (free-var? %)
           (util/some-of
             (replacements %)
             (symbol (str (name %) "__auto__" seqid)))
           %)
        clauses))))

(defn remove-pairs [xs ys]
  (let [pairs (->> (map vector xs ys)
                (remove (fn [[x y]] (= x y))))]
    [(map first pairs)
     (map second pairs)]))

(defn rule-gen-guards [rule-clause used-args]
  (let [[rule & call-args] rule-clause
        prev-call-args     (get used-args rule)]
    (for [prev-args prev-call-args
          :let [[call-args prev-args] (remove-pairs call-args prev-args)]]
      [(concat ['-differ?] call-args prev-args)])))

(defn walk-collect [form pred]
  (let [res (atom [])]
    (walk/postwalk #(do (when (pred %) (swap! res conj %)) %) form)
    @res))

(defn collect-vars [clause]
  (set (walk-collect clause free-var?)))

(defn split-guards [clauses guards]
  (let [bound-vars (collect-vars clauses)
        pred       (fn [[[_ & vars]]] (every? bound-vars vars))]
    [(filter pred guards)
     (remove pred guards)]))

(defn solve-rule [context clause]
  (let [final-attrs     (filter free-var? clause)
        final-attrs-map (zipmap final-attrs (range))
        ;;         clause-cache    (atom {}) ;; TODO
        solve           (fn [prefix-context clauses]
                          (reduce -resolve-clause prefix-context clauses))
        empty-rels?     (fn [context]
                          (some #(empty? (:tuples %)) (:rels context)))]
    (loop [stack (list {:prefix-clauses []
                        :prefix-context context
                        :clauses        [clause]
                        :used-args      {}
                        :pending-guards {}})
           rel   (->Relation final-attrs-map [])]
      (if-some [frame (first stack)]
        (let [[clauses [rule-clause & next-clauses]] (split-with #(not (rule? context %)) (:clauses frame))]
          (if (nil? rule-clause)

            ;; no rules -> expand, collect, sum
            (let [context (solve (:prefix-context frame) clauses)
                  tuples  (util/distinct-by vec (-collect context final-attrs))
                  new-rel (->Relation final-attrs-map tuples)]
              (recur (next stack) (sum-rel rel new-rel)))

            ;; has rule -> add guards -> check if dead -> expand rule -> push to stack, recur
            (let [[rule & call-args]     rule-clause
                  guards                 (rule-gen-guards rule-clause (:used-args frame))
                  [active-gs pending-gs] (split-guards (concat (:prefix-clauses frame) clauses)
                                           (concat guards (:pending-guards frame)))]
              (if (some #(= % '[(-differ?)]) active-gs) ;; trivial always false case like [(not= [?a ?b] [?a ?b])]

                ;; this branch has no data, just drop it from stack
                (recur (next stack) rel)

                (let [prefix-clauses (concat clauses active-gs)
                      prefix-context (solve (:prefix-context frame) prefix-clauses)]
                  (if (empty-rels? prefix-context)

                    ;; this branch has no data, just drop it from stack
                    (recur (next stack) rel)

                    ;; need to expand rule to branches
                    (let [used-args  (assoc (:used-args frame) rule
                                       (conj (get (:used-args frame) rule []) call-args))
                          branches   (expand-rule rule-clause context used-args)]
                      (recur (concat
                               (for [branch branches]
                                 {:prefix-clauses prefix-clauses
                                  :prefix-context prefix-context
                                  :clauses        (util/concatv branch next-clauses)
                                  :used-args      used-args
                                  :pending-guards pending-gs})
                               (next stack))
                        rel))))))))
        rel))))

(defn- resolve-eid [eid]
  (db/entid *implicit-source* eid))

(defn dynamic-lookup-attrs [source pattern]
  (let [[e a v tx] pattern
        v-ref?     (and (free-var? v) (attr? a) (db/ref? source a))
        v-tuple?   (and (free-var? v) (attr? a) (db/tuple? source a))]
    (cond-> {}
      (free-var? e)  (assoc e  resolve-eid)
      (free-var? tx) (assoc tx resolve-eid)
      v-ref?         (assoc v  resolve-eid)
      v-tuple?       (assoc v  #(db/resolve-tuple-refs *implicit-source* a %1 pattern)))))

(defn limit-rel [rel vars]
  (when-some [attrs' (not-empty (select-keys (:attrs rel) vars))]
    (assoc rel :attrs attrs')))

(defn limit-context [context vars]
  (assoc context
    :rels (->> (:rels context)
            (keep #(limit-rel % vars)))))

(defn bound-vars [context]
  (into #{} (mapcat #(keys (:attrs %)) (:rels context))))

(defn check-bound [bound vars form]
  (when-not (set/subset? vars bound)
    (let [missing (set/difference (set vars) bound)]
      (util/raise "Insufficient bindings: " missing " not bound in " form
        {:error :query/where
         :form  form
         :vars  missing}))))

(defn check-free-same [bound branches form]
  (let [free (mapv #(set/difference (collect-vars %) bound) branches)]
    (when-not (apply = free)
      (util/raise "All clauses in 'or' must use same set of free vars, had " free " in " form
        {:error :query/where
         :form  form
         :vars  free}))))

(defn check-free-subset [bound vars branches]
  (let [free (set (remove bound vars))]
    (doseq [branch branches]
      (when-some [missing (not-empty (set/difference free (collect-vars branch)))]
        (prn branch bound vars free)
        (util/raise "All clauses in 'or' must use same set of free vars, had " missing " not bound in " branch
          {:error :query/where
           :form  branch
           :vars  missing})))))

(defn -resolve-clause
  ([context clause]
   (-resolve-clause context clause clause))
  ([context clause orig-clause]
   (condp looks-like? clause
     [[symbol? '*]] ;; predicate [(pred ?a ?b ?c)]
     (do
       (check-bound (bound-vars context) (filter free-var? (nfirst clause)) clause)
       (filter-by-pred context clause))
     
     [[symbol? '*] '_] ;; function [(fn ?a ?b) ?res]
     (do
       (check-bound (bound-vars context) (filter free-var? (nfirst clause)) clause)
       (bind-by-fn context clause))
     
     [source? '*] ;; source + anything
     (let [[source-sym & rest] clause]
       (binding [*implicit-source* (get (:sources context) source-sym)]
         (-resolve-clause context rest clause)))
     
     '[or *] ;; (or ...)
     (let [[_ & branches] clause
           _        (check-free-same (bound-vars context) branches clause)
           contexts (map #(resolve-clause context %) branches)
           rels     (map #(reduce hash-join (:rels %)) contexts)]
       (assoc (first contexts) :rels [(reduce sum-rel rels)]))
     
     '[or-join [[*] *] *] ;; (or-join [[req-vars] vars] ...)
     (let [[_ [req-vars & vars] & branches] clause
           bound (bound-vars context)]
       (check-bound bound req-vars orig-clause)
       (check-free-subset bound vars branches)
       (recur context (list* 'or-join (concat req-vars vars) branches) clause))
     
     '[or-join [*] *] ;; (or-join [vars] ...)
     (let [[_ vars & branches] clause
           vars         (set vars)
           _            (check-free-subset (bound-vars context) vars branches)
           join-context (limit-context context vars)
           contexts     (map #(-> join-context (resolve-clause %) (limit-context vars)) branches)
           rels         (map #(reduce hash-join (:rels %)) contexts)
           sum-rel      (reduce sum-rel rels)]
       (update context :rels collapse-rels sum-rel))
     
     '[and *] ;; (and ...)
     (let [[_ & clauses] clause]
       (reduce resolve-clause context clauses))
     
     '[not *] ;; (not ...)
     (let [[_ & clauses] clause
           bound            (bound-vars context)
           negation-vars    (collect-vars clauses)
           _                (when (empty? (set/intersection bound negation-vars))
                              (util/raise "Insufficient bindings: none of " negation-vars " is bound in " orig-clause
                                {:error :query/where
                                 :form  orig-clause}))
           context'         (assoc context :rels [(reduce hash-join (:rels context))])
           negation-context (reduce resolve-clause context' clauses)
           negation         (subtract-rel
                              (util/single (:rels context'))
                              (reduce hash-join (:rels negation-context)))]
       (assoc context' :rels [negation]))
     
     '[not-join [*] *] ;; (not-join [vars] ...)
     (let [[_ vars & clauses] clause
           bound            (bound-vars context)
           _                (check-bound bound vars orig-clause)
           context'         (assoc context :rels [(reduce hash-join (:rels context))])
           join-context     (limit-context context' vars)
           negation-context (-> (reduce resolve-clause join-context clauses)
                              (limit-context vars))
           negation         (subtract-rel
                              (util/single (:rels context'))
                              (reduce hash-join (:rels negation-context)))]
       (assoc context' :rels [negation]))
     
     '[*] ;; pattern
     (let [source   *implicit-source*
           pattern' (resolve-pattern-lookup-refs source clause)
           relation (lookup-pattern context source pattern')]
       (binding [*lookup-attrs* (if (satisfies? db/IDB source)
                                  (dynamic-lookup-attrs source pattern')
                                  *lookup-attrs*)]
         (update context :rels collapse-rels relation))))))

(defn short-circuit-empty-rel [context]
  (if (some #(empty? (:tuples %)) (:rels context))
    (assoc context
      :rels
      [(->Relation
         (zipmap (mapcat #(keys (:attrs %)) (:rels context)) (range))
         [])])
    context))

(defn resolve-clause [context clause]
  (if (->> (:rels context) (some (comp empty? :tuples)))
    context ; The result is empty; short-circuit processing
    (short-circuit-empty-rel
      (if (rule? context clause)
        (if (source? (first clause))
          (binding [*implicit-source* (get (:sources context) (first clause))]
            (resolve-clause context (next clause)))
          (update context :rels collapse-rels (solve-rule context clause)))
        (-resolve-clause context clause)))))

(defn -q [context clauses]
  (binding [*implicit-source* (get (:sources context) '$)]
    (reduce resolve-clause context clauses)))

(defn -collect-tuples
  [acc rel ^long len copy-map]
  (->Eduction
    (comp
      (map
        (fn [t1]
          (->Eduction
            (map
              (fn [t2]
                ;; PORT (PORTING-DS.md): res is a da array (an Eve vector
                ;; here); the copy loop threads da/aset functionally.
                (reduce
                  (fn [r i]
                    (if-some [idx (da/aget copy-map i)]
                      (da/aset r i (if (da/array? t2) (da/aget t2 idx) (get t2 idx)))
                      r))
                  (da/aclone t1)
                  (range len))))
            (:tuples rel))))
      cat)
    acc))

(defn -collect
  ([context symbols]
   (let [rels (:rels context)]
     (-collect [(da/make-array (count symbols))] rels symbols)))
  ([acc rels symbols]
   (util/cond+
     :let [rel (first rels)]
 
     (nil? rel) acc
 
     ;; one empty rel means final set has to be empty
     (empty? (:tuples rel)) []
 
     :let [keep-attrs (select-keys (:attrs rel) symbols)]
 
     (empty? keep-attrs) (recur acc (next rels) symbols)
 
     :let [copy-map (to-array (map #(get keep-attrs %) symbols))
           len      (count symbols)]

     :else
     (recur (-collect-tuples acc rel len copy-map) (next rels) symbols))))

(defn collect [context symbols]
  (into #{} (map vec) (-collect context symbols)))

(defn -context-resolve [var context]
  (cond
    (instance? #?(:jank dp/Variable :clj Variable :cljr Variable :cljs dp/Variable :clje dp/Variable :lpy dp/Variable) var)
    (context-resolve-val context (.-symbol var))
    (instance? #?(:jank dp/SrcVar :clj SrcVar :cljr SrcVar :cljs dp/SrcVar :clje dp/SrcVar :lpy dp/SrcVar) var)
    (get-in context [:sources (.-symbol var)])
    (instance? #?(:jank dp/PlainSymbol :clj PlainSymbol :cljr PlainSymbol :cljs dp/PlainSymbol :clje dp/PlainSymbol :lpy dp/PlainSymbol) var)
    (or (get built-ins/aggregates (.-symbol var))
      (resolve-sym (.-symbol var)))
    (instance? #?(:jank dp/Constant :clj Constant :cljr Constant :cljs dp/Constant :clje dp/Constant :lpy dp/Constant) var)
    (.-value var)
    :else
    (throw (ex-info (str "No -context-resolve impl for " (pr-str (type var))) {:var var}))))

(defn -aggregate [find-elements context tuples]
  (mapv (fn [element fixed-value i]
          (if (dp/aggregate? element)
            (let [f    (-context-resolve (:fn element) context)
                  args (map #(-context-resolve % context) (butlast (:args element)))
                  vals (map #(nth % i) tuples)]
              (apply f (concat args [vals])))
            fixed-value))
    find-elements
    (first tuples)
    (range)))

(defn- idxs-of [pred coll]
  (->> (map #(when (pred %1) %2) coll (range))
    (remove nil?)))

(defn aggregate [find-elements context resultset]
  (let [group-idxs (idxs-of (complement dp/aggregate?) find-elements)
        group-fn   (fn [tuple]
                     (map #(nth tuple %) group-idxs))
        grouped    (group-by group-fn resultset)]
    (for [[_ tuples] grouped]
      (-aggregate find-elements context tuples))))

(defn map* [f xs]
  (reduce #(conj %1 (f %2)) (empty xs) xs))

(defn tuples->return-map [return-map tuples]
  (let [symbols (:symbols return-map)
        idxs    (range 0 (count symbols))]
    (map*
      (fn [tuple]
        (reduce
          (fn [m i] (assoc m (nth symbols i) (nth tuple i)))
          {} idxs))
      tuples)))

(defn -post-process [find return-map tuples]
  (cond
    (instance? #?(:jank dp/FindRel :clj FindRel :cljr FindRel :cljs dp/FindRel :clje dp/FindRel :lpy dp/FindRel) find)
    (if (nil? return-map)
      tuples
      (tuples->return-map return-map tuples))

    (instance? #?(:jank dp/FindColl :clj FindColl :cljr FindColl :cljs dp/FindColl :clje dp/FindColl :lpy dp/FindColl) find)
    (into [] (map first) tuples)

    (instance? #?(:jank dp/FindScalar :clj FindScalar :cljr FindScalar :cljs dp/FindScalar :clje dp/FindScalar :lpy dp/FindScalar) find)
    (ffirst tuples)

    (instance? #?(:jank dp/FindTuple :clj FindTuple :cljr FindTuple :cljs dp/FindTuple :clje dp/FindTuple :lpy dp/FindTuple) find)
    (if (some? return-map)
      (first (tuples->return-map return-map [(first tuples)]))
      (first tuples))

    :else
    (throw (ex-info (str "No -post-process impl for " (pr-str (type find))) {:find find}))))

(defn- pull [find-elements context resultset]
  (let [resolved (for [find find-elements]
                   (when (dp/pull? find)
                     (let [db (-context-resolve (:source find) context)
                           pattern (-context-resolve (:pattern find) context)]
                       (dpa/parse-opts db pattern))))]
    (for [tuple resultset]
      (mapv
        (fn [parsed-opts el]
          (if parsed-opts
            (dpa/pull-impl parsed-opts el)
            el))
        resolved
        tuple))))

(declare q-impl)

(defn q [q & inputs]
  ;; Construction law: Relations, result sets and return maps are
  ;; constructed in the queried DB's own domain (the aggregates in
  ;; built-ins run inside this binding, so they inherit it); a query
  ;; with no DB input constructs in the parse domain.
  (let [db (first (filter #(or (db/db? %) (db/filtered-db? %)) inputs))]
    (if (some? db)
      (db/call-in-db-ish-domain db (fn [] (apply q-impl q inputs)))
      (db/call-in-parse-domain (fn [] (apply q-impl q inputs))))))

(defn- q-impl [q & inputs]
  (let [parsed-q      (lru/-get *query-cache* q #(dp/parse-query q))
        find          (:qfind parsed-q)
        find-elements (dp/find-elements find)
        find-vars     (dp/find-vars find)
        result-arity  (count find-elements)
        with          (:qwith parsed-q)
        ;; TODO utilize parser
        all-vars      (concat find-vars (map :symbol with))
        q             (cond-> q
                        (sequential? q) dp/query->map)
        wheres        (:where q)
        context       (-> (->Context [] {} {})
                        (resolve-ins (:qin parsed-q) inputs))
        resultset     (-> context
                        (-q wheres)
                        (collect all-vars))]
    (cond->> resultset
      (:with q)
      (mapv #(vec (subvec % 0 result-arity)))
      (some dp/aggregate? find-elements)
      (aggregate find-elements context)
      (some dp/pull? find-elements)
      (pull find-elements context)
      true
      (-post-process find (:qreturn-map parsed-q)))))
