(ns ^:no-doc eve.db.ds.impl.entity
  (:refer-clojure :exclude [keys])
  (:require [#?(:cljs cljs.core :clj clojure.core) :as c]
    [eve.db.ds.db :as db]))

(declare entity ->Entity equiv-entity lookup-entity touch hash-entity)

(defn- entid [db eid]
  (when (or (number? eid)
          (sequential? eid)
          (keyword? eid))
    (db/entid db eid)))

(defn entity
  ([db eid] (entity db eid nil))
  ([db eid handle]
   {:pre [(db/db? db)]}
   (when-let [e (entid db eid)] (when (db/numeric-eid-exists? db e) (->Entity db e (volatile! false) (volatile! {}) handle)))))

(defn- entity-attr [db handle a datoms]
  (if (db/multival? db a)
    (if (db/ref? db a)
      (reduce #(conj %1 (entity db (:v %2) handle)) #{} datoms)
      (reduce #(conj %1 (:v %2)) #{} datoms))
    (if (db/ref? db a)
      (entity db (:v (first datoms)) handle)
      (:v (first datoms)))))

(defn- -lookup-backwards [db handle eid attr not-found]
  (if-let [datoms (not-empty (db/-search db [nil attr eid]))]
    (if (db/component? db attr)
      (entity db (:e (first datoms)) handle)
      (reduce #(conj %1 (entity db (:e %2) handle)) #{} datoms))
    not-found))

#?(:cljs
   (defn- multival->js [val]
     (when val (to-array val))))

#?(:cljs
   (defn- js-seq [e]
     (touch e)
     (for [[a v] @(.-cache e)]
       (if (db/multival? (.-db e) a)
         [a (multival->js v)]
         [a v]))))

#?(:cljs
  (unchecked-set (.-prototype ES6Iterator) cljs.core/ITER_SYMBOL
     (fn []
       (this-as this# this#))))

#?(:cljs
  (unchecked-set (.-prototype ES6EntriesIterator) cljs.core/ITER_SYMBOL
     (fn []
       (this-as this# this#))))

(deftype Entity [db eid touched cache handle]
  #?@(:cljs
      [Object
       (toString [this]
         (pr-str* this))

       IEquiv
       (-equiv [this o] (equiv-entity this o))

       IHash
       (-hash [this]
         (hash-entity this))

       ISeqable
       (-seq [this]
         (touch this)
         (seq @cache))

       ICounted
       (-count [this]
         (touch this)
         (count @cache))

       ILookup
       (-lookup [this attr]           (lookup-entity this attr nil))
       (-lookup [this attr not-found] (lookup-entity this attr not-found))

       IAssociative
       (-contains-key? [this k]
         (not= ::nf (lookup-entity this k ::nf)))

       IFn
       (-invoke [this k]
         (lookup-entity this k))
       (-invoke [this k not-found]
         (lookup-entity this k not-found))

       IPrintWithWriter
       (-pr-writer [_ writer opts]
         (-pr-writer (assoc @cache :db/id eid) writer opts))]

      :clj
      [Object
       (toString [e]      (pr-str (assoc @cache :db/id eid)))
       (hashCode [e]      (hash-entity e))
       (equals [e o]      (equiv-entity e o))

       clojure.lang.Seqable
       (seq [e]           (touch e) (seq @cache))

       clojure.lang.Associative
       (equiv [e o]       (equiv-entity e o))
       (containsKey [e k] (not= ::nf (lookup-entity e k ::nf)))
       (entryAt [e k]     (some->> (lookup-entity e k) (clojure.lang.MapEntry. k)))

       (empty [e]         (throw (UnsupportedOperationException.)))
       (assoc [e k v]     (throw (UnsupportedOperationException.)))
       (cons  [e [k v]]   (throw (UnsupportedOperationException.)))
       (count [e]         (touch e) (count @(.-cache e)))

       clojure.lang.ILookup
       (valAt [e k]       (lookup-entity e k))
       (valAt [e k not-found] (lookup-entity e k not-found))

       clojure.lang.IFn
       (invoke [e k]      (lookup-entity e k))
       (invoke [e k not-found] (lookup-entity e k not-found))]))

;; Interpreted cljs seats (SCI/nbb) register the deftype without
;; interning a var for its name; eve.db.ds.core names Entity in type
;; tags, which resolve through the var there. Capture the type
;; object into a var (SCI's instance? discriminates on the type
;; object, as on compiled seats).
#?(:cljs (def Entity
           (type (->Entity nil nil nil nil nil))))

#?(:cljs
   ;; The JS-Map interface Entity carries for JS consumers (keys /
   ;; entries / values / has / get / forEach + the *_set fallbacks).
   ;; It used to live in the deftype's Object spec; interpreted cljs
   ;; seats (SCI) accept only a minimal Object spec, so the methods
   ;; are installed on the prototype here instead — the same
   ;; unchecked-set mechanism the ES6 iterator patches above use.
   ;; Interpreted seats expose no .-prototype on the type value, so
   ;; the guard skips installation there (Clojure consumers use the
   ;; protocol surface, which the deftype itself carries).
   (when (.-prototype Entity)
     (let [proto (.-prototype Entity)]
       (unchecked-set proto "keys"
         (fn [] (this-as this (es6-iterator (c/keys this)))))
       (unchecked-set proto "entries"
         (fn [] (this-as this (es6-entries-iterator (js-seq this)))))
       (unchecked-set proto "values"
         (fn [] (this-as this (es6-iterator (map second (js-seq this))))))
       (unchecked-set proto "has"
         (fn [attr] (this-as this (not (nil? (.get this attr))))))
       (unchecked-set proto "get"
         (fn [attr]
           (this-as this
             (if (= attr ":db/id")
               (.-eid this)
               (if (db/reverse-ref? attr)
                 (-> (-lookup-backwards (.-db this) (.-handle this) (.-eid this) (db/reverse-ref attr) nil)
                   multival->js)
                 (cond-> (lookup-entity this attr)
                   (db/multival? (.-db this) attr) multival->js))))))
       (unchecked-set proto "forEach"
         (fn [f & [use-as-this]]
           (this-as this
             (doseq [[a v] (js-seq this)]
               (if use-as-this
                 (.call f use-as-this v a this)
                 (f v a this))))))
       (unchecked-set proto "key_set"
         (fn [] (this-as this (to-array (c/keys this)))))
       (unchecked-set proto "entry_set"
         (fn [] (this-as this (to-array (map to-array (js-seq this))))))
       (unchecked-set proto "value_set"
         (fn [] (this-as this (to-array (map second (js-seq this)))))))))

(defn entity? [x] (instance? Entity x))

#?(:cljs
  (when (.-prototype Entity)
    (unchecked-set (.-prototype Entity) cljs.core/ITER_SYMBOL
       (fn []
         (this-as this# (.entries this#))))))

#?(:clj
   (defmethod print-method Entity [e, ^java.io.Writer w]
     (.write w (str e))))

(defn- equiv-entity [^Entity this that]
  (and
    (instance? Entity that)
    (let [h1 (.-handle this) h2 (.-handle ^Entity that)] (if (and h1 h2) (and (identical? (:atom h1) (:atom h2)) (= (:tx h1) (:tx h2))) (eve.alpha/same? (.-db this) (.-db ^Entity that)))) ; db identity: the conn handle when both carry one (GAP-2), else block identity inside a region (GAP-1)
    (= (.-eid this) (.-eid ^Entity that))))

(defn- hash-entity [^Entity e]
  (db/combine-hashes
    (hash (.-eid e))
    ;; The handle's identity when present (region-free); else the DB version hash.
    (if-some [h (.-handle e)] (hash [(#?(:clj System/identityHashCode :cljs goog/getUid) (:atom h)) (:tx h)]) (db/entity-db-hash (.-db e)))))

(defn- lookup-entity
  ([this attr] (lookup-entity this attr nil))
  ([^Entity this attr not-found]
   (if (= attr :db/id)
     (.-eid this)
     (if (db/reverse-ref? attr)
       (-lookup-backwards (.-db this) (.-handle this) (.-eid this) (db/reverse-ref attr) not-found)
       (if-some [v (@(.-cache this) attr)]
         v
         (if @(.-touched this)
           not-found
           (if-some [datoms (not-empty (db/-search (.-db this) [(.-eid this) attr]))]
             (let [value (entity-attr (.-db this) (.-handle this) attr datoms)]
               (vreset! (.-cache this) (assoc @(.-cache this) attr value))
               value)
             not-found)))))))

(defn touch-components [db a->v]
  (reduce-kv
    (fn [acc a v]
      (assoc acc a
        (if (db/component? db a)
          (if (db/multival? db a)
            (set (map touch v))
            (touch v))
          v)))
    {} a->v))

(defn- datoms->cache [db handle datoms]
  (reduce (fn [acc part]
            (let [a (:a (first part))]
              (assoc acc a (entity-attr db handle a part))))
    {} (partition-by :a datoms)))

(defn touch [^Entity e]
  {:pre [(or (nil? e) (entity? e))]}
  (when (some? e)
    (when-not @(.-touched e)
      (when-let [datoms (not-empty (db/-search (.-db e) [(.-eid e)]))]
        (vreset! (.-cache e) (->> datoms
                               (datoms->cache (.-db e) (.-handle e))
                               (touch-components (.-db e))))
        (vreset! (.-touched e) true)))
    e))

#?(:cljs (goog/exportSymbol "eve.db.ds.impl.entity.Entity" Entity))
