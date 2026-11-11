(ns eve.db.ds.serialize
  (:refer-clojure :exclude [amap array?])
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    ;; PORT DELTA (F-NBB-MACROS round): as in eve.db.ds.conn — no
    ;; :cljs type refer; Datom is named qualified at its uses
    [eve.db.ds.db :as db]
    [eve.db.ds.lru :as lru]
    [eve.db.ds.storage :as storage]
    [eve.db.ds.util :as util]
    [me.tonsky.persistent-sorted-set :as set]
    [me.tonsky.persistent-sorted-set.arrays :as arrays]
    [eve.host :as eh]
    ;; PORT DELTA (F-CLJE-HOST-REFERS): see eve.db.ds.util
    #?(:clje [eve.host :refer [transient persistent! conj! assoc! volatile! locking]]))
  #?(:cljs (:require-macros [eve.db.ds.serialize :refer [array dict]]))
  #?(:clj
     (:import
       [eve.db.ds.db Datom]) :cljr (:import
       [eve.db.ds.db Datom])))
  ;; PORT (PORTING-DS.md): vanilla also imports
  ;; [me.tonsky.persistent_sorted_set PersistentSortedSet] here — the JVM
  ;; form's class, which does not exist in the Eve world (the port's type
  ;; is me.tonsky.persistent_sorted_set.BTSet) and is never referenced in
  ;; this file. Dropped.

(def ^:const ^:private marker-kw 0)
(def ^:const ^:private marker-other 1)
(def ^:const ^:private marker-inf 2)
(def ^:const ^:private marker-minus-inf 3)
(def ^:const ^:private marker-nan 4)

;; PORT DELTA (F-CLJE-INF): ##Inf/##-Inf/##NaN literals are gone from
;; this file — clojerl's reader parses ## as a tagged literal and cannot
;; even skip-read them inside a non-selected #? arm. Values are produced
;; by overflowing the max finite double (IEEE Infinity / NaN). BEAM
;; floats have neither, so the overflow stays finite (or is unusable)
;; and these bindings stay nil; a clje db cannot contain an infinite
;; value in the first place. Write-side tests guard with some? / ==.
(defn- ieee-overflow
  [sign]
  (let [x (* sign 1.7976931348623157E308 2.0)]
    (when (and (number? x) (== x (* x 2.0)) (not (zero? x)))
      ;; reject saturation to the max finite double (non-IEEE hosts)
      (when-not (== (if (pos? sign) x (- x)) 1.7976931348623157E308)
        x))))

(def ^:private pos-inf (ieee-overflow 1.0))
(def ^:private neg-inf (ieee-overflow -1.0))
(def ^:private nan-val
  (when (some? pos-inf)
    (let [x (- pos-inf pos-inf)]
      (when (not (== x x))
        x))))

(defn- if-cljs [env then else]
  (if (:ns env) then else))

#?(:clj
(defmacro array
     "Platform-native array representation (java.util.List on JVM, Array on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "[" (str/join "," (repeat (count args) "~{}")) "]") args)
       (vec args))) :cljr (defmacro array
     "Platform-native array representation (java.util.List on JVM, Array on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "[" (str/join "," (repeat (count args) "~{}")) "]") args)
       (vec args)))
   ;; PORT DELTA (F-NBB-MACROS): visible to the :cljs macro read
   :cljs
(defmacro array
     "Platform-native array representation (java.util.List on JVM, Array on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "[" (str/join "," (repeat (count args) "~{}")) "]") args)
       (vec args)))
   ;; PORT DELTA (F-LPY-SERIALIZE): visible to the :lpy macro read;
   ;; basilisp is a macro seat like :clj, and the expansion here is
   ;; the non-cljs one (a vector)
   :lpy
(defmacro array
     "Platform-native array representation (java.util.List on JVM, Array on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "[" (str/join "," (repeat (count args) "~{}")) "]") args)
       (vec args)))
   ;; PORT DELTA (F-CLJE-SERIALIZE): :clje twin of array (the :clj arm's
   ;; expansion is seat-correct here: vectors are the array representation)
   :clje
(defmacro array
     "Platform-native array representation (java.util.List on JVM, Array on JS)"
     [& args]
     (vec args)))

#?(:clj
(defmacro dict
     "Platform-native dictionary representation (java.util.Map on JVM, Object on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "{" (str/join "," (repeat (/ (count args) 2) "~{}:~{}")) "}") args)
       `(array-map ~@args))) :cljr (defmacro dict
     "Platform-native dictionary representation (java.util.Map on JVM, Object on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "{" (str/join "," (repeat (/ (count args) 2) "~{}:~{}")) "}") args)
       `(array-map ~@args)))
   ;; PORT DELTA (F-NBB-MACROS): visible to the :cljs macro read
   :cljs
(defmacro dict
     "Platform-native dictionary representation (java.util.Map on JVM, Object on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "{" (str/join "," (repeat (/ (count args) 2) "~{}:~{}")) "}") args)
       `(array-map ~@args)))
   ;; PORT DELTA (F-LPY-SERIALIZE): visible to the :lpy macro read
   :lpy
(defmacro dict
     "Platform-native dictionary representation (java.util.Map on JVM, Object on JS)"
     [& args]
     (if-cljs &env
       (list* 'js* (str "{" (str/join "," (repeat (/ (count args) 2) "~{}:~{}")) "}") args)
       `(array-map ~@args)))
   ;; PORT DELTA (F-CLJE-SERIALIZE): :clje twin of dict (array-map branch)
   :clje
(defmacro dict
     "Platform-native dictionary representation (java.util.Map on JVM, Object on JS)"
     [& args]
     `(array-map ~@args)))

;; PORT DELTA (F-LPY-SERIALIZE): on this seat the array macro's
;; expansion is a vector (as on :clj) and dict an array-map, so the
;; accessors are the portable ones and amap builds vectors.
(defn- array-get [d i]
  (nth d i))

(defn- dict-get [d k]
  (get d k))

(defn- array? [a]
  #?(:clj  (instance? java.util.List a) :cljr (instance? System.Collections.IList a)
     :cljs (or (cljs.core/array? a) (vector? a))
     :lpy  (vector? a)
     :clje (vector? a)))

(defn- amap [f xs]
  #?(:clj
     (let [arr (java.util.ArrayList. (count xs))]
       (reduce (fn [idx x] (.add arr (f x)) (inc idx)) 0 xs)
       arr) :cljr (let [arr (System.Collections.ArrayList. (count xs))]
       (reduce (fn [idx x] (.add arr (f x)) (inc idx)) 0 xs)
       arr)
     :cljs
     (let [arr (js/Array. (count xs))]
       (reduce (fn [idx x] (arrays/aset arr idx (f x)) (inc idx)) 0 xs)
       arr)
     :lpy
     (reduce (fn [arr x] (conj arr (f x))) [] xs)
     :clje
     (vec (map f xs))))

(defn- amap-indexed [f xs]
  #?(:clj
     (let [arr (java.util.ArrayList. (count xs))]
       (reduce (fn [idx x] (.add arr (f idx x)) (inc idx)) 0 xs)
       arr) :cljr (let [arr (System.Collections.ArrayList. (count xs))]
       (reduce (fn [idx x] (.add arr (f idx x)) (inc idx)) 0 xs)
       arr)
     :cljs
     (let [arr (js/Array. (count xs))]
       (reduce (fn [idx x] (arrays/aset arr idx (f idx x)) (inc idx)) 0 xs)
       arr)
     :lpy
     (reduce (fn [arr x] (conj arr (f (count arr) x))) [] xs)
     :clje
     (vec (map-indexed f xs))))

(defn- attr-comparator
  "Looks for a datom with attribute exactly bigger than the given one"
  [d1 d2]
  (cond 
    (nil? (.-a d2)) -1
    (<= (compare (.-a d1) (.-a d2)) 0) -1
    true 1))

(defn- all-attrs
  "All attrs in a DB, distinct, sorted"
  [db]
  (if (empty? (:aevt db))
    []
    (loop [attrs (transient [(:a (first (:aevt db)))])]
      (let [attr      (nth attrs (dec (count attrs)))
            left      (db/datom 0 attr nil)
            right     (db/datom db/emax nil nil)
            next-attr (:a (first (set/slice (:aevt db) left right attr-comparator)))]
        (if (some? next-attr)
          (recur (conj! attrs next-attr))
          (persistent! attrs))))))

(def ^{:arglists '([kw])} freeze-kw str)

(defn- thaw-kw [s]
  (if (str/starts-with? s ":")
    (keyword (subs s 1))
    s))

(defn- serializable-impl
  "Serialized structure breakdown:

   count    :: number    
   tx0      :: number
   max-eid  :: number
   max-tx   :: number
   schema   :: freezed :schema
   attrs    :: [keywords ...]
   keywords :: [keywords ...]
   eavt     :: [[e a-idx v dtx] ...]
   a-idx    :: index in attrs
   v        :: (string | number | boolean | [0 <index in keywords>] | [1 <freezed v>])
   dtx      :: tx - tx0
   aevt     :: [<index in eavt> ...]
   avet     :: [<index in eavt> ...]"
  [db {:keys [freeze-fn freeze-kw]
       :or   {freeze-fn pr-str
              freeze-kw freeze-kw}}]
  (when (storage/storage db)
    (throw (ex-info "serializable doesn't work with databases that have :storage" {})))
  (let [attrs       (all-attrs db)
        attrs-map   (into {} (map vector attrs (range)))
        *kws        (volatile! (transient []))
        *kw-map     (volatile! (transient {}))
        write-kw    (fn [kw]
                      (let [idx (or
                                  (get @*kw-map kw)
                                  (let [keywords (vswap! *kws conj! kw)
                                        idx      (dec (count keywords))]
                                    (vswap! *kw-map assoc! kw idx)
                                    idx))]
                        (array marker-kw idx)))
        write-other (fn [v] (array marker-other (freeze-fn v)))
        write-v     (fn [v]
                      (cond
                        (string? v)  v
                        #?@(:clj [(or
                                    (instance? BigInteger v)
                                    (instance? BigDecimal v)
                                    (instance? clojure.lang.Ratio v)
                                    (instance? clojure.lang.BigInt v))
                                  (write-other v)] :cljr [(or
                                    (instance? BigInteger v)
                                    (instance? BigDecimal v)
                                    (instance? clojure.lang.Ratio v)
                                    (instance? clojure.lang.BigInt v))
                                  (write-other v)])
                        
                        (number? v)  
                        (cond
                          (and (some? pos-inf) (== pos-inf v)) (array marker-inf)
                          (and (some? neg-inf) (== neg-inf v)) (array marker-minus-inf)
                          (not (== v v)) (array marker-nan)
                          :else v)

                        (boolean? v) v
                        (keyword? v) (write-kw v)
                        true         (write-other v)))
        ;; PORT (PORTING-DS.md T1): vanilla assigns each datom's idx by
        ;; mutation (datom-set-idx) and reads it back for the other two
        ;; indexes. Datom idx is fixed at construction in this port, so
        ;; the freeze builds a side table keyed by the datom value.
        idx-of      (into {} (map-indexed (fn [i d] [d i]) (:eavt db)))
        eavt        (amap-indexed
                      (fn [_idx d]
                        (let [e  (.-e d)
                              a  (attrs-map (.-a d))
                              v  (write-v (.-v d))
                              tx (- (.-tx d) db/tx0)]
                          (array e a v tx)))
                      (:eavt db))
        aevt        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [^Datom d] :cljs [^db/Datom d] :clje [d] :lpy [d])] (idx-of d)) (:aevt db))
        avet        (amap-indexed (fn [_ #?@(:jank [^db/Datom d] :clj [^Datom d] :cljr [^Datom d] :cljs [^db/Datom d] :clje [d] :lpy [d])] (idx-of d)) (:avet db))
        schema      (freeze-fn (:schema db))
        attrs       (amap freeze-kw attrs)
        kws         (amap freeze-kw (persistent! @*kws))
        #?@(:clj
            [settings (set/settings (:eavt db))] :cljr [settings (set/settings (:eavt db))])]
    (dict
      "count"    (count (:eavt db))
      "tx0"      db/tx0
      "max-eid"  (:max-eid db)
      "max-tx"   (:max-tx db)
      "schema"   schema
      "attrs"    attrs
      "keywords" kws
      "eavt"     eavt
      "aevt"     aevt
      "avet"     avet
      #?@(:clj
          ["branching-factor" (:branching-factor settings)
           "ref-type"         (name (:ref-type settings))] :cljr ["branching-factor" (:branching-factor settings)
           "ref-type"         (name (:ref-type settings))]))))

(def ^:private serializable-lock (eh/token))

(defn serializable
  ([db] (eh/locking serializable-lock (serializable-impl db {})))
  ([db opts] (eh/locking serializable-lock (serializable-impl db opts))))

(defn from-serializable
  ([from] 
   (from-serializable from {}))
  ([from {:keys [thaw-fn thaw-kw]
          :or   {thaw-fn edn/read-string
                 thaw-kw thaw-kw}
          :as opts}]
   (let [tx0      (dict-get from "tx0")
         schema   (thaw-fn (dict-get from "schema"))
         _        (#'db/validate-schema schema)
         attrs    (->> (dict-get from "attrs") (mapv thaw-kw))
         keywords (->> (dict-get from "keywords") (mapv thaw-kw))
         eavt     (->> (dict-get from "eavt")
                    (amap (fn [arr]
                            (let [e  (array-get arr 0)
                                  a  (nth attrs (array-get arr 1))
                                  v  (array-get arr 2)
                                  v  (cond
                                       (number? v)  v
                                       (string? v)  v
                                       (boolean? v) v
                                       (array? v) (let [marker (array-get v 0)]
                                                    (condp == marker
                                                      marker-kw    (nth keywords (array-get v 1))
                                                      marker-other (thaw-fn (array-get v 1))
                                                      marker-inf   pos-inf
                                                      marker-minus-inf neg-inf
                                                      marker-nan   nan-val
                                                      (util/raise "Unexpected value marker " marker " in " (pr-str v)
                                                        {:error :serialize :value v})))
                                       true (util/raise "Unexpected value type " (type v) " (" (pr-str v) ")"
                                              {:error :serialize :value v}))
                                  tx (+ tx0 (array-get arr 3))]
                              (db/datom e a v tx))))
                    arrays/into-array)
         aevt     (some->> (dict-get from "aevt") (amap #(arrays/aget eavt %)) #?(:clj arrays/into-array :cljr arrays/into-array))
         avet     (some->> (dict-get from "avet") (amap #(arrays/aget eavt %)) #?(:clj arrays/into-array :cljr arrays/into-array))
         settings (merge
                    {:branching-factor (dict-get from "branching-factor")
                     :ref-type         (some-> (dict-get from "ref-type") keyword)}
                    (select-keys opts [:branching-factor :ref-type]))]
     (db/restore-db
       {:schema  schema
        :eavt    (set/from-sorted-array :eve.db.ds.db/cmp-eavt eavt (arrays/alength eavt) settings)
        :aevt    (set/from-sorted-array :eve.db.ds.db/cmp-aevt aevt (arrays/alength aevt) settings)
        :avet    (set/from-sorted-array :eve.db.ds.db/cmp-avet avet (arrays/alength avet) settings)
        :max-eid (dict-get from "max-eid")
        :max-tx  (dict-get from "max-tx")}))))
