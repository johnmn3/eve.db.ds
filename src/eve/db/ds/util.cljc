(ns eve.db.ds.util
  (:refer-clojure :exclude [find])
  ;; PORT DELTA (F-CLJE-HOST-REFERS): clojerl's clojure.core has no
  ;; transient family (bare-boot inventory in STATUS-DS-CLJE-LRU);
  ;; Eve's beam eve.host supplies them with persistent semantics
  ;; (transient/persistent! are identity there), so under :clje this
  ;; ns refers them from eve.host. Same remedy in db/conn/query/
  ;; pull-api/entity/serialize for the names each of those files
  ;; calls bare.
  #?(:clj
     (:import
       [java.util UUID]) :cljr (:import [System Guid])
     :clje
     (:require
       [eve.host :refer [transient conj! assoc! persistent! volatile! vswap!]])))

(def ^:dynamic *debug*
  false)

;; Portable runtime gate. The old :clj arm also required the compile-time
;; system property eve.db.ds.debug (and :cljr DATASCRIPT_DEBUG) before
;; emitting the body; *debug* defaults to false, so this is equivalent
;; at runtime on the four test hosts.
(defmacro log [& body]
  `(when *debug*
     (println ~@body)))

;; PORT DELTA (F-LPY-RAISE-GENSYM): basilisp's reader rejects a gensym
;; symbol (m#) in a macro body outside syntax-quote even inside a
;; function passed to map at expansion time; the predicate's argument
;; is named plainly (auto-gensym only matters inside the syntax-quote
;; that follows, and m is not referenced there). The body is identical
;; on every host.
(defmacro raise [& fragments]
  (let [msgs (butlast fragments)
        data (last fragments)]
    `(throw (ex-info (str ~@(map (fn [m] (if (string? m) m (list 'pr-str m))) msgs)) ~data))))

(def ^:private ^:dynamic *if+-syms)

(defn- if+-rewrite-cond-impl [cond]
  (clojure.core/cond
    (empty? cond)
    true

    (and
      (= :let (first cond))
      (empty? (second cond)))
    (if+-rewrite-cond-impl (nnext cond))

    (= :let (first cond))
    (let [[var val & rest] (second cond)
          sym                (gensym)]
      (vswap! *if+-syms conj [var sym])
      (list 'let [var (list 'clojure.core/vreset! sym val)]
        (if+-rewrite-cond-impl
          (cons
            :let
            (cons rest
              (nnext cond))))))

    :else
    (list 'and
      (first cond)
      (if+-rewrite-cond-impl (next cond)))))

(defn- if+-rewrite-cond [cond]
  (binding [*if+-syms (volatile! [])]
    [(if+-rewrite-cond-impl cond) @*if+-syms]))

(defn- flatten-1 [xs]
  (vec
    (mapcat identity xs)))

(defmacro if+
  "Allows sharing local variables between condition and then clause.
   
   Use `:let [...]` form (not nested!) inside `and` condition and its bindings
   will be visible in later `and` clauses and inside `then` branch:
   
     (if+ (and
            (= 1 2)
            ;; same :let syntax as in doseq/for
            :let [x 3
                  y (+ x 4)]
            ;; x and y visible downstream
            (> y x))
       
       ;; then: x and y visible here!
       (+ x y 5)
       
       ;; else: no x or y
       6)"
  [cond then else]
  (if (and
        (seq? cond)
        (or
          (= 'and (first cond))
          (= 'clojure.core/and (first cond))))
    (let [[cond' syms] (if+-rewrite-cond (next cond))]
      `(let ~(flatten-1
               (for [[_ sym] syms]
                 [sym '(volatile! nil)]))
         (if ~cond'
           (let ~(flatten-1
                   (for [[binding sym] syms]
                     [binding (list 'deref sym)]))
             ~then)
           ~else)))
    (list 'if cond then else)))

(defmacro cond+ [& clauses]
  (when-some [[test expr & rest] clauses]
    (case test
      :do   `(do ~expr (util/cond+ ~@rest))
      :let  `(let ~expr (util/cond+ ~@rest))
      :some `(or ~expr (util/cond+ ~@rest))
      `(util/if+ ~test ~expr (util/cond+ ~@rest)))))

(defmacro some-of
  ([] nil)
  ([x] x)
  ([x & more]
   `(let [x# ~x] (if (nil? x#) (some-of ~@more) x#))))

(defn- rand-bits [pow]
  (rand-int (bit-shift-left 1 pow)))

#?(:cljr
   (defn- to-hex-string [n l]
     (let [s (System.Convert/ToString (long n) 16)
           c (count s)]
       (cond
         (> c l) (subs s 0 l)
         (< c l) (str (apply str (repeat (- l c) "0")) s)
         :else   s))))

#?(:cljs
   (defn- to-hex-string [n l]
     (let [s (.toString n 16)
           c (count s)]
       (cond
         (> c l) (subs s 0 l)
         (< c l) (str (apply str (repeat (- l c) "0")) s)
         :else   s))))

(defn squuid
  ([]
   (squuid #?(:clj  (System/currentTimeMillis) :cljr (.ToUnixTimeMilliseconds (System.DateTimeOffset/UtcNow))
              :cljs (.getTime (js/Date.)))))
  ([msec]
   #?(:clj
      (let [uuid     (UUID/randomUUID)
            time     (int (/ msec 1000))
            high     (.getMostSignificantBits uuid)
            low      (.getLeastSignificantBits uuid)
            new-high (bit-or (bit-and high 0x00000000FFFFFFFF)
                       (bit-shift-left time 32))]
        (UUID. new-high low)) :cljr (System.Guid/Parse
        (str
          (-> (int (/ msec 1000))
            (to-hex-string 8))
          "-" (-> (rand-bits 16) (to-hex-string 4))
          "-" (-> (rand-bits 16) (bit-and 0x0FFF) (bit-or 0x4000) (to-hex-string 4))
          "-" (-> (rand-bits 16) (bit-and 0x3FFF) (bit-or 0x8000) (to-hex-string 4))
          "-" (-> (rand-bits 16) (to-hex-string 4))
          (-> (rand-bits 16) (to-hex-string 4))
          (-> (rand-bits 16) (to-hex-string 4))))
      :cljs
      (uuid
        (str
          (-> (int (/ msec 1000))
            (to-hex-string 8))
          "-" (-> (rand-bits 16) (to-hex-string 4))
          "-" (-> (rand-bits 16) (bit-and 0x0FFF) (bit-or 0x4000) (to-hex-string 4))
          "-" (-> (rand-bits 16) (bit-and 0x3FFF) (bit-or 0x8000) (to-hex-string 4))
          "-" (-> (rand-bits 16) (to-hex-string 4))
          (-> (rand-bits 16) (to-hex-string 4))
          (-> (rand-bits 16) (to-hex-string 4)))))))

(defn- parse-hex [s]
  (reduce (fn [acc ch]
            (let [c (int ch)
                  n (cond
                      (<= 48 c 57)  (- c 48)
                      (<= 97 c 102) (+ 10 (- c 97))
                      (<= 65 c 70)  (+ 10 (- c 65))
                      :else (throw (ex-info (str "Not a hex digit: " ch) {:ch ch})))]
              (+ (* acc 16) n)))
    0
    s))

(defn squuid-time-millis
  "Returns time that was used in [[squuid]] call, in milliseconds, rounded to the closest second."
  [uuid]
  (-> (subs (str uuid) 0 8)
    parse-hex
    (* 1000)))

(defn distinct-by [f coll]
  (->>
    (reduce
      (fn [[seen res :as acc] el]
        (let [key (f el)]
          (if (contains? seen key)
            acc
            [(conj! seen key) (conj! res el)])))
      [(transient #{}) (transient [])]
      coll)
    second
    persistent!))

(defn find [pred xs]
  (reduce
    (fn [_ x]
      (when (pred x)
        (reduced x)))
    nil xs))

(defn single [coll]
  (assert (nil? (next coll)) "Expected single element")
  (first coll))

(defn concatv [& xs]
  (into [] cat xs))

(defn zip
  ([a b]
   (mapv vector a b))
  ([a b & rest]
   (apply mapv vector a b rest)))

(defn removem [key-pred m]
  (persistent!
    (reduce-kv
      (fn [m k v]
        (if (key-pred k)
          m
          (assoc! m k v)))
      (transient (empty m)) m)))

;; PORT DELTA (F-CLJE-LITERAL-DEF): these two defs were (fnil conj [])
;; and (fnil conj #{}) — clojerl's emitter rejects a def init that
;; evaluates to an anonymous fn object ("Init value for
;; #'eve.db.ds.util/conjv is not a literal", util.cljc:309). The
;; explicit fns are fnil's exact semantics (a nil first arg reads as
;; the empty collection); a fn FORM is emittable on every seat.
(def conjv
  (fn [coll x] (conj (if (nil? coll) [] coll) x)))

(def conjs
  (fn [coll x] (conj (if (nil? coll) #{} coll) x)))

(defn reduce-indexed
  "Same as reduce, but `f` takes [acc el idx]"
  [f init xs]
  (first
    (reduce
      (fn [[acc idx] x]
        (let [res (f acc x idx)]
          (if (reduced? res)
            (reduced [res idx])
            [res (inc idx)])))
      [init 0]
      xs)))
