(ns eve.db.ds.util
  (:refer-clojure :exclude [find])
  (:require
    [eve.uuid :as eve-uuid]
    ;; PORT DELTA (F-CLJE-HOST-REFERS): clojerl's clojure.core has no
    ;; transient family (bare-boot inventory in STATUS-DS-CLJE-LRU);
    ;; Eve's beam eve.host supplies them with persistent semantics
    ;; (transient/persistent! are identity there), so under :clje this
    ;; ns refers them from eve.host. Same remedy in db/conn/query/
    ;; pull-api/entity/serialize for the names each of those files
    ;; calls bare.
    #?(:clje [eve.host :refer [transient conj! assoc! persistent! volatile! vswap!]])))

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

;; PORT: squuid is Eve's (eve.uuid/squuid — the e2 clock/UUID unit):
;; one implementation on every host, semi-sequential v4 with the
;; unsigned-seconds prefix, instead of per-host UUID/clock branches
;; (java.util.UUID / System.Guid / cljs string building, now deleted
;; with their to-hex-string/rand-bits helpers).
(defn squuid
  ([] (eve-uuid/squuid))
  ([msec] (eve-uuid/squuid msec)))

(defn squuid-time-millis
  "Returns time that was used in [[squuid]] call, in milliseconds, rounded to the closest second."
  [uuid]
  (eve-uuid/squuid-time-millis uuid))

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
