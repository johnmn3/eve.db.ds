(ns ^:no-doc eve.db.ds.impl.entity
  ;; UNIT1 (DISPATCH #56, one-Eve-implementation): Entity is ONE definition,
  ;; eve.deftype/deftype — a process-local type written once in the
  ;; JVM-interface groups (plus Object and IPrintWithWriter), which every
  ;; host's eve.types translates. The old body carried four per-host face
  ;; splices (:cljs/:clj/:cljr/:lpy/:clje arms of the same methods) and the
  ;; JS-Map/ES6 prototype installs; the JS face is the JS-family Eve types
  ;; adapters' edge (one Eve fn name per JS host), tracked for the Eve side,
  ;; not a host fork in this app.
  (:refer-clojure :exclude [deftype])
  (:require
    [eve.core :as e]
    [eve.deftype :refer [deftype]]
    [eve.host :refer [volatile!]]
    [eve.encoding :as enc]
    [eve.process :as proc]
    [eve.db.ds.db :as db]))

(declare entity ->Entity equiv-entity lookup-entity touch hash-entity)

(defn- entid [db eid]
  (when (or (number? eid)
          (sequential? eid)
          (keyword? eid))
    (db/entid db eid)))

(defn entity [db eid]
  {:pre [(db/db? db)]}
  (when-let [e (entid db eid)]
    (when (db/numeric-eid-exists? db e)
      (->Entity db e (volatile! false) (volatile! {})))))

(defn- entity-attr [db a datoms]
  (if (db/multival? db a)
    (if (db/ref? db a)
      (reduce #(conj %1 (entity db (:v %2))) #{} datoms)
      (reduce #(conj %1 (:v %2)) #{} datoms))
    (if (db/ref? db a)
      (entity db (:v (first datoms)))
      (:v (first datoms)))))

(defn- -lookup-backwards [db eid attr not-found]
  (if-let [datoms (not-empty (db/-search db [nil attr eid]))]
    (if (db/component? db attr)
      (entity db (:e (first datoms)))
      (reduce #(conj %1 (entity db (:e %2))) #{} datoms))
    not-found))

(deftype Entity [db eid touched cache]
  Object
  (toString [this]
    (pr-str (assoc @cache :db/id eid)))

  clojure.lang.Seqable
  (seq [this]           (touch this) (seq @cache))

  clojure.lang.Associative
  (equiv [this o]       (equiv-entity this o))
  (containsKey [this k] (not= ::nf (lookup-entity this k ::nf)))
  (entryAt [this k]     (some->> (lookup-entity this k) (enc/map-entry k)))
  (empty [this]         (throw (enc/unsupported "Entity empty is not supported")))
  (assoc [this k v]     (throw (enc/unsupported "Entity assoc is not supported")))
  (cons  [this [k v]]   (throw (enc/unsupported "Entity cons is not supported")))
  (count [this]         (touch this) (count @cache))

  clojure.lang.ILookup
  (valAt [this k]       (lookup-entity this k))
  (valAt [this k not-found] (lookup-entity this k not-found))

  clojure.lang.IFn
  (invoke [this k]      (lookup-entity this k))
  (invoke [this k not-found] (lookup-entity this k not-found))

  IHash
  (-hash [this]
    (hash-entity this))

  IPrintWithWriter
  (-pr-writer [this writer opts]
    (e/-write writer (pr-str (assoc @(.-cache this) :db/id (.-eid this))))))

(defn entity? [x] (instance? Entity x))

(defn- equiv-entity [this that]
  (and
    (instance? Entity that)
    (identical? (.-db this) (.-db that)) ; `=` and `hash` on db is expensive
    (= (.-eid this) (.-eid that))))

(defn- hash-entity [e]
  (db/combine-hashes
    (hash (.-eid e))
    ;; A hash compatible with `identical?`. Consistent with `=`.
    (proc/identity-hash (.-db e))))

(defn- lookup-entity
  ([this attr] (lookup-entity this attr nil))
  ([this attr not-found]
   (if (= attr :db/id)
     (.-eid this)
     (if (db/reverse-ref? attr)
       (-lookup-backwards (.-db this) (.-eid this) (db/reverse-ref attr) not-found)
       (if-some [v (@(.-cache this) attr)]
         v
         (if @(.-touched this)
           not-found
           (if-some [datoms (not-empty (db/-search (.-db this) [(.-eid this) attr]))]
             (let [value (entity-attr (.-db this) attr datoms)]
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

(defn- datoms->cache [db datoms]
  (reduce (fn [acc part]
            (let [a (:a (first part))]
              (assoc acc a (entity-attr db a part))))
    {} (partition-by :a datoms)))

(defn touch [e]
  {:pre [(or (nil? e) (entity? e))]}
  (when (some? e)
    (when-not @(.-touched e)
      (when-let [datoms (not-empty (db/-search (.-db e) [(.-eid e)]))]
        (vreset! (.-cache e) (->> datoms
                               (datoms->cache (.-db e))
                               (touch-components (.-db e))))
        (vreset! (.-touched e) true)))
    e))
