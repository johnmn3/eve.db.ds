(ns ^:no-doc eve.db.ds.db
  (:require
    ;; PORT: the :cljs goog.array require is deleted — nothing in this
    ;; ns calls garray/ anymore (the last use was the JS -compare arm,
    ;; long folded); a require with no uses is not glue, it's litter.
    [clojure.walk]
    [clojure.data]
    #?(:clj [eve.db.ds.inline :refer [update]])
    ;; PORT DELTA (F-CLJE-HOST-REFERS): see eve.db.ds.util
    #?(:clje [eve.host :refer [transient persistent! conj! dissoc! long volatile!]])
    [eve.db.ds.lru :as lru]
    [eve.db.ds.util :as util]
    [eve.core :as e]
    [eve.encoding :as enc]
    [eve.platform]
    [eve.map-entry :as me]
    ;; F-DS-EVE-WRITERS: the Eve value namespaces whose codec writers a DB
    ;; needs (value+sio->eve-bytes finds the :map/:set/:vec/:list writers
    ;; only once these have loaded). Required here so that requiring
    ;; eve.db.ds.core alone is enough, on every host arm.
    [eve.atom :as eve-atom]
    [eve.deftype-proto.alloc :as eve-alloc]
    [eve.deftype-proto.data :as eve-data]
    [eve.map]
    [eve.set]
    [eve.vec]
    [eve.list]
    [eve.deftype :refer [eve-deftype]]
    ;; UNIT1 (#56): eve.deftype/deftype is the one portable deftype on
    ;; every host — the per-host require branch is gone (ledger rows
    ;; db.cljc:22 et al.).
    [eve.deftype :refer [deftype]]
    [eve.record :as erecord]
    #?(:cljs [eve.record])
    [me.tonsky.persistent-sorted-set :as set]
    [me.tonsky.persistent-sorted-set.arrays :as arrays])
  ;; PORT DELTA (D6, PORTING-DS.md): vanilla's (:import
  ;; clojure.lang.IFn$OOL) is gone — SCI hosts (bb) cannot register
  ;; that class, and its only use was defcomp's invokePrim arm,
  ;; which D6 drops. The ns needs no :import on the :clj read.
  #?(:cljs (:require-macros [eve.db.ds.db :refer [case-tree combine-cmp defn+ defcomp defrecord-updatable if-cljs int-compare validate-attr validate-val]]
                            [eve.record :as erecord]))
  (:refer-clojure :exclude [seqable? #?(:clj update) deftype]))

#?(:clj (set! *warn-on-reflection* true) :cljr (set! *warn-on-reflection* true))

;; ----------------------------------------------------------------------------

#?(:cljs
   (do
     (def Exception js/Error)
     (def IllegalArgumentException js/Error)
     (def UnsupportedOperationException js/Error)))

(def ^:const e0
  0)

(def ^:const tx0
  0x20000000)

(def ^:const emax
  0x7FFFFFFF)

(def ^:const txmax
  0x7FFFFFFF)

(def ^:const implicit-schema
  {:db/ident {:db/unique :db.unique/identity}})

;; ----------------------------------------------------------------------------
;; Construction law (John, 2026-11-11): "All eve data must be constructed
;; in a swapping operation, in the atom domain. Instantiating eve data
;; outside of an eve context should throw."
;;
;; Eve constructors resolve their domain from eve.deftype-proto.data/
;; *domain-sio*: bound by an Eve atom's swap! while the swap fn runs, or
;; bound explicitly. The contexts eve.db.ds constructs in:
;;
;; - a DB's own domain: the sio the DB value itself carries (its indexes
;;   live there) — transactions, query/pull execution, entity creation;
;; - the parse domain: one explicit small heap domain holding parsed
;;   queries/pull patterns and the caches over them. Parse output is
;;   immutable, domain-agnostic compiled data (symbols, keywords, records
;;   of them), consumed read-only by execution and never persisted as
;;   user data, so it belongs to no DB's domain;
;; - the process domain for a brand-new DB: a DB's home domain is the
;;   process domain, bound explicitly (call-in-process-domain). It
;;   becomes resident in an atom's domain by being swapped into that
;;   atom, and that swap's binding governs later construction.

(defn db-sio
  "The Eve domain sio of a DB value (the domain its indexes live in)."
  [db]
  (eve-data/-eve-sio db))

(defn call-in-domain
  "Call f with Eve's *domain-sio* bound to sio: an explicit Eve domain
   context for construction outside a swap!."
  [sio f]
  (binding [eve-data/*domain-sio* sio]
    (f)))

(defn call-in-db-domain
  "Call f in db's own Eve domain (see db-sio) — unless a domain context
   is already bound (e.g. an Eve atom's swap! over the atom that holds
   the DB): the ambient context wins, because construction for that
   atom belongs to the atom's domain."
  [db f]
  (if (some? eve-data/*domain-sio*)
    (f)
    (call-in-domain (db-sio db) f)))

(declare call-in-db-ish-domain)

(defn process-sio
  "This process's own Eve domain sio (eve.deftype-proto.alloc/default-sio)."
  []
  (eve-alloc/default-sio))

(defn call-in-process-domain
  "Call f with the process domain explicitly bound. A DB value's home
   domain is the process domain: host values (exact numbers, host
   collections) stay themselves there, where copying a value into a
   foreign slab domain would canonicalize it through the codec —
   measured at the 383097e pin: a clojure.lang.BigInt stored into a
   DB built in a fresh heap domain came back as a plain long and the
   serialize-portable gate's exact-number rows failed. A DB becomes
   resident in an atom's domain by being swapped into that atom; the
   swap's own binding then governs new construction (call-in-db-domain
   defers to it)."
  [f]
  (call-in-domain (process-sio) f))

(defonce ^:private parse-domain-sio
  (delay (eve-alloc/heap-domain-sio! {})))

(defn call-in-parse-domain
  "Call f in the parse domain (see the construction-law note above)."
  [f]
  (call-in-domain @parse-domain-sio f))

(defn in-new-domain
  "Construct a value in a brand-new Eve domain: run build-fn inside a
   fresh Eve atom's swap! and return the built value. NOT used for DB
   construction: a DB's home domain is the process domain (see
   call-in-process-domain for the measured reason)."
  [build-fn]
  (let [a (eve-atom/atom nil)]
    (swap! a (fn [_] (build-fn)))
    @a))

;; ----------------------------------------------------------------------------

(defn seqable? [x]
  ;; Portable: exclude strings (clojure.core/seqable? is true for them)
  ;; and accept nil, seqs, collections, and host arrays. basilisp has
  ;; seqable? only as basilisp.core/seqable?, not clojure.core/seqable?,
  ;; so this body does not call a qualified core seqable?.
  (and (not (string? x))
    (or (nil? x)
      (seq? x)
      (coll? x)
      (arrays/array? x))))

;; ----------------------------------------------------------------------------
;; macros and funcs to support writing defrecords and updating
;; (replacing) builtins, i.e., Object/hashCode, IHashEq hasheq, etc.
;; code taken from prismatic:
;;  https://github.com/Prismatic/schema/commit/e31c419c56555c83ef9ee834801e13ef3c112597
;;

(do #?@(:clj [(defn- cljs-env?
     "Take the &env from a macro, and tell whether we are expanding into cljs."
     [env]
     (boolean (:ns env)))] :cljr [(defn- cljs-env?
     "Take the &env from a macro, and tell whether we are expanding into cljs."
     [env]
     (boolean (:ns env)))]
 :lpy  [(defn- cljs-env?
     "Take the &env from a macro, and tell whether we are expanding into cljs."
     [env]
     (boolean (:ns env)))]
 :cljs [(defn- cljs-env?
     "Take the &env from a macro, and tell whether we are expanding into cljs."
     [_env]
     true)]))

;; PORT DELTA (F-CLJE-MACROS): the :clje arm of this file's macro
;; layer. The #?@ blocks name :clj/:lpy/:cljs only, and a MATCHING
;; top-level #?@ is refused by clojerl's reader (F-CLJE-DECLARE), so
;; each :clje definition is appended as a plain #? twin right after
;; its block; the blocks themselves are untouched and no other seat's
;; read changes. cljs-env? is false here: clje code is not cljs code
;; (defn+ takes its plain branch, if-cljs its else branch).
#?(:clje (defn- cljs-env?
           [_env]
           false))

(do #?@(:clj [(defmacro if-cljs
     "Return then if we are generating cljs code and else for Clojure code.
     https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
     [then else]
     (if (cljs-env? &env) then else))] :cljr [(defmacro if-cljs
     "Return then if we are generating cljs code and else for Clojure code.
     https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
     [then else]
     (if (cljs-env? &env) then else))]
 :lpy  [(defmacro if-cljs
     "Return then if we are generating cljs code and else for Clojure code.
     https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
     [then else]
     (if (cljs-env? &env) then else))]
 :cljs [(defmacro if-cljs
     "Return then if we are generating cljs code and else for Clojure code.
     https://groups.google.com/d/msg/clojurescript/iBY5HaQda4A/w1lAQi9_AwsJ"
     [then else]
     (if (cljs-env? &env) then else))]))

#?(:clje (defmacro if-cljs
           [then else]
           (if (cljs-env? &env) then else)))

(do #?@(:clj [(defn patch-tag [meta cljs-env?]
     (if cljs-env?
       meta
       (condp = (:tag meta)
         'boolean (assoc meta :tag java.lang.Boolean)
         ;; PORT DELTA (D7, PORTING-DS.md): the tag is quoted —
         ;; vanilla's bare clojure.core$long resolves on the JVM
         ;; only because that compiled class exists; SCI (bb)
         ;; cannot resolve it at analysis. :tag values are symbols
         ;; by convention; the analyzer resolves the name either way.
         'number  (assoc meta :tag 'clojure.core$long)
         meta)))] :cljr [(defn patch-tag [meta cljs-env?]
     (if cljs-env?
       meta
       (dissoc meta :tag)))]
 :lpy  [(defn patch-tag [meta cljs-env?]
     (if cljs-env?
       meta
       ;; basilisk's analyzer resolves a :tag symbol to the TYPE ITSELF
       ;; and then analyzes the type as a form — every class tag dies
       ;; ("Unexpected form type <class 'type'>") and the dotted
       ;; 'clojure.core$long spelling resolves differently inside a
       ;; macro-produced defn than in a literal one. Tags are hints
       ;; only; on this seat they are dropped.
       (dissoc meta :tag)))]
 :cljs [(defn patch-tag [meta cljs-env?]
     (if cljs-env?
       meta
       (condp = (:tag meta)
         'boolean (assoc meta :tag 'boolean)
         'number  (assoc meta :tag 'number)
         meta)))]))

#?(:clje (defn patch-tag
           ;; 'boolean/'number are the portable spellings the other
           ;; arms rewrite to seat classes. Clojerl has neither: an
           ;; unresolvable tag symbol fails the def itself
           ;; (eval_var_meta resolving the meta), and a Var-valued
           ;; tag fails call sites (F-CLJE-TAG) — so both spellings
           ;; are dropped on this seat. Real type tags (^Datom, and
           ;; ^long via the eve.host refer) resolve and pass through.
           [meta _cljs-env?]
           (if (contains? #{'boolean 'number} (:tag meta))
             (dissoc meta :tag)
             meta)))

(do #?@(:clj [(defmacro defn+
     "CLJS really don’t like :declared metadata on vars (generates less
      efficient code), but it needs it to skip warnings. So we redefine
      first with ^:declared and empty implementation, and then immediately
      redefine again without ^:declared. This way both `declare` and `defn+`
      versions have no ^:declared meta, thus allowing CLJS to generate direct
      invocations and see type hints."
     [name & rest]
     (let [[doc rest] (if (string? (first rest))
                        [(first rest) (next rest)]
                        [nil rest])
           name'    (cond-> (vary-meta name patch-tag (cljs-env? &env))
                      doc (vary-meta assoc :doc doc))
           arglists (if (vector? (first rest))
                      [(first rest)]
                      (map #(list (first %)) rest))]
       (if (cljs-env? &env)
         `(do
            (defn ~(vary-meta name' assoc :declared true) ~@arglists)
            (defn ~name' ~@rest))
         `(defn ~name' ~@rest))))] :cljr [(defmacro defn+
     "CLJS really don’t like :declared metadata on vars (generates less
      efficient code), but it needs it to skip warnings. So we redefine
      first with ^:declared and empty implementation, and then immediately
      redefine again without ^:declared. This way both `declare` and `defn+`
      versions have no ^:declared meta, thus allowing CLJS to generate direct
      invocations and see type hints."
     [name & rest]
     (let [[doc rest] (if (string? (first rest))
                        [(first rest) (next rest)]
                        [nil rest])
           name'    (cond-> (vary-meta name patch-tag (cljs-env? &env))
                      doc (vary-meta assoc :doc doc))
           arglists (if (vector? (first rest))
                      [(first rest)]
                      (map #(list (first %)) rest))]
       (if (cljs-env? &env)
         `(do
            (defn ~(vary-meta name' assoc :declared true) ~@arglists)
            (defn ~name' ~@rest))
         `(defn ~name' ~@rest))))]
 :lpy  [(defmacro defn+
     "CLJS really don’t like :declared metadata on vars (generates less
      efficient code), but it needs it to skip warnings. So we redefine
      first with ^:declared and empty implementation, and then immediately
      redefine again without ^:declared. This way both `declare` and `defn+`
      versions have no ^:declared meta, thus allowing CLJS to generate direct
      invocations and see type hints."
     [name & rest]
     (let [[doc rest] (if (string? (first rest))
                        [(first rest) (next rest)]
                        [nil rest])
           name'    (cond-> (vary-meta name patch-tag (cljs-env? &env))
                      doc (vary-meta assoc :doc doc))
           arglists (if (vector? (first rest))
                      [(first rest)]
                      (map #(list (first %)) rest))]
       (if (cljs-env? &env)
         `(do
            (defn ~(vary-meta name' assoc :declared true) ~@arglists)
            (defn ~name' ~@rest))
         `(defn ~name' ~@rest))))]
 :cljs [(defmacro defn+
     "CLJS really don’t like :declared metadata on vars (generates less
      efficient code), but it needs it to skip warnings. So we redefine
      first with ^:declared and empty implementation, and then immediately
      redefine again without ^:declared. This way both `declare` and `defn+`
      versions have no ^:declared meta, thus allowing CLJS to generate direct
      invocations and see type hints."
     [name & rest]
     (let [[doc rest] (if (string? (first rest))
                        [(first rest) (next rest)]
                        [nil rest])
           name'    (cond-> (vary-meta name patch-tag (cljs-env? &env))
                      doc (vary-meta assoc :doc doc))
           arglists (if (vector? (first rest))
                      [(first rest)]
                      (map #(list (first %)) rest))]
       (if (cljs-env? &env)
         `(do
            (defn ~(vary-meta name' assoc :declared true) ~@arglists)
            (defn ~name' ~@rest))
         `(defn ~name' ~@rest))))]))

#?(:clje (defmacro defn+
           "CLJS really don’t like :declared metadata on vars (generates less
            efficient code), but it needs it to skip warnings. So we redefine
            first with ^:declared and empty implementation, and then immediately
            redefine again without ^:declared. This way both `declare` and `defn+`
            versions have no ^:declared meta, thus allowing CLJS to generate direct
            invocations and see type hints."
           [name & rest]
           (let [[doc rest] (if (string? (first rest))
                              [(first rest) (next rest)]
                              [nil rest])
                 name'    (cond-> (vary-meta name patch-tag (cljs-env? &env))
                            doc (vary-meta assoc :doc doc))
                 arglists (if (vector? (first rest))
                            [(first rest)]
                            (map #(list (first %)) rest))]
             (if (cljs-env? &env)
               `(do
                  (defn ~(vary-meta name' assoc :declared true) ~@arglists)
                  (defn ~name' ~@rest))
               `(defn ~name' ~@rest)))))

;; Portable mix, bit-for-bit with clojure.lang.Util/hashCombine
;; (0x9e3779b9 = 2654435769). The JVM implementation uses a *signed*
;; 32-bit right shift (ishr), not unsigned (iushr); eve.platform/int32
;; keeps every intermediate in signed 32-bit on seats whose bit ops
;; are wider than int.
(defn combine-hashes [x y]
  (let [seed (eve.platform/int32 x)
        h    (eve.platform/int32 y)]
    (eve.platform/int32
      (bit-xor seed
               (+ h 2654435769
                  (eve.platform/int32 (bit-shift-left seed 6))
                  (eve.platform/int32 (bit-shift-right seed 2)))))))

;; PORT: vanilla's defrecord-updatable macro machinery is RETAINED:
;; it is part of the port's tested surface — the upstream suite
;; (test/eve/db/ds/test/db.cljc, HashBeef) exercises it directly.
;; (An earlier close-out batch deleted it on a src/-only usage grep
;; and broke the JVM suite load; restored verbatim, measured.)
(do #?@(:clj [   (defn- get-sig [method]
     ;; expects something like '(method-symbol [arg arg arg] ...)
     ;; if the thing matches, returns [fully-qualified-symbol arity], otherwise nil
     (and (sequential? method)
       (symbol? (first method))
       (vector? (second method))
       (let [sym (first method)
             ns  (or (some->> sym resolve meta :ns str) "clojure.core")]
         [(symbol ns (name sym)) (-> method second count)])))] :cljr [   (defn- get-sig [method]
     ;; expects something like '(method-symbol [arg arg arg] ...)
     ;; if the thing matches, returns [fully-qualified-symbol arity], otherwise nil
     (and (sequential? method)
       (symbol? (first method))
       (vector? (second method))
       (let [sym (first method)
             ns  (or (some->> sym resolve meta :ns str) "clojure.core")]
         [(symbol ns (name sym)) (-> method second count)])))]
 :lpy  [(defn- get-sig [& args] nil)]
 :cljs []))

;; PORT DELTA (F-CLJE-MACROS): get-sig/dedupe-interfaces serve only
;; the JVM deftype* postwalk in make-record-updatable-clj, which the
;; :clje seat never takes (it delegates to the cljs maker, like
;; :lpy); the twins mirror the :lpy stubs.
#?(:clje (defn- get-sig [& _args] nil))

(do #?@(:clj [   (defn- dedupe-interfaces [deftype-form]
     ;; get the interfaces list, remove any duplicates, similar to remove-nil-implements in potemkin
     ;; verified w/ deftype impl in compiler:
     ;; (deftype* tagname classname [fields] :implements [interfaces] :tag tagname methods*)
     (let [[deftype* tagname classname fields implements interfaces & rest] deftype-form]
       (when (or (not= deftype* 'deftype*) (not= implements :implements))
         (throw (IllegalArgumentException. "deftype-form mismatch")))
       (list* deftype* tagname classname fields implements (vec (distinct interfaces)) rest)))] :cljr [   (defn- dedupe-interfaces [deftype-form]
     ;; get the interfaces list, remove any duplicates, similar to remove-nil-implements in potemkin
     ;; verified w/ deftype impl in compiler:
     ;; (deftype* tagname classname [fields] :implements [interfaces] :tag tagname methods*)
     (let [[deftype* tagname classname fields implements interfaces & rest] deftype-form]
       (when (or (not= deftype* 'deftype*) (not= implements :implements))
         (throw (System.ArgumentException. "deftype-form mismatch")))
       (list* deftype* tagname classname fields implements (vec (distinct interfaces)) rest)))]
 :lpy  [(defn- dedupe-interfaces [& args] nil)]
 :cljs []))

#?(:clje (defn- dedupe-interfaces [& _args] nil))

(do #?@(:clj [(defn- make-record-updatable-cljs [name fields & impls]
     `(do
        (defrecord ~name ~fields)
        (extend-type ~name ~@impls)))] :cljr [(defn- make-record-updatable-cljs [name fields & impls]
     `(do
        (defrecord ~name ~fields)
        (extend-type ~name ~@impls)))]
 :lpy  [(defn- make-record-updatable-cljs [name fields & impls]
     ;; NOT syntax-quoted: basilisk's syntax quote ns-qualifies the
     ;; impls' group symbols (clojure.lang.IHashEq becomes
     ;; eve.db.ds.db/clojure.lang.IHashEq, unresolvable) and eve's
     ;; deftype consumes the first group positionally, so the quoted
     ;; expansion mangles the record. Build the expansion as data:
     ;; the impls ride INLINE in the deftype (the proven DB path —
     ;; eve.deftype translates the JVM-interface groups itself).
     (list 'do
           (list* 'deftype name fields impls)))]
 :cljs [(defn- make-record-updatable-cljs [name fields & impls]
     `(do
        (defrecord ~name ~fields)
        (extend-type ~name ~@impls)))]))

#?(:clje (defn- make-record-updatable-cljs [name fields & impls]
           `(do
              (defrecord ~name ~fields)
              (extend-type ~name ~@impls))))

(do #?@(:clj [(defn- make-record-updatable-clj [name fields & impls]
     (let [impl-map (->> impls (map (juxt get-sig identity)) (filter first) (into {}))
           body     (macroexpand-1 (list* 'defrecord name fields impls))]
       (clojure.walk/postwalk
         (fn [form]
           (if (and (sequential? form) (= 'deftype* (first form)))
             (->> form
               dedupe-interfaces
               (remove (fn [method]
                         (when-some [impl (-> method get-sig impl-map)]
                           (not= method impl)))))
             form))
         body)))] :cljr [(defn- make-record-updatable-clj [name fields & impls]
     (let [impl-map (->> impls (map (juxt get-sig identity)) (filter first) (into {}))
           body     (macroexpand-1 (list* 'defrecord name fields impls))]
       (clojure.walk/postwalk
         (fn [form]
           (if (and (sequential? form) (= 'deftype* (first form)))
             (->> form
               dedupe-interfaces
               (remove (fn [method]
                         (when-some [impl (-> method get-sig impl-map)]
                           (not= method impl)))))
             form))
         body)))]
 :lpy  [(defn- make-record-updatable-clj [name fields & impls]
     ;; F-LPY-APPLY: was a non-apply delegation — the shape the
     ;; clje lane's F-CLJE-APPLY proved wraps impls in an extra
     ;; list inside the generated form and crashes expansion
     ;; (their stage-16 wall). The :lpy cljs-maker builds
     ;; (list* 'deftype name fields impls); wrapped, its impls
     ;; tail is one seq where protocol groups are expected.
     (apply make-record-updatable-cljs name fields impls))]
 :cljs []))

;; PORT DELTA (F-CLJE-MACROS + F-CLJE-APPLY): like :lpy, the :clje
;; maker delegates to the cljs maker — the :clj arm's deftype*
;; postwalk is a JVM compiler shape clojerl does not have. The
;; delegation must APPLY: passing the & impls list as one argument
;; wraps the impls in an extra list inside the generated
;; extend-type, and clojerl's expansion then seqs a protocol
;; symbol (stage-16 wall, bisected in fix/clje-probe/fdb_probe.cljc;
;; the :lpy arm carries the same non-apply shape — flagged in the
;; lane report, untouched here).
#?(:clje (defn- make-record-updatable-clj [name fields & impls]
           (apply make-record-updatable-cljs name fields impls)))

(do #?@(:clj [(defmacro defrecord-updatable [name fields & impls]
     `(if-cljs
        ~(apply make-record-updatable-cljs name fields impls)
        ~(apply make-record-updatable-clj  name fields impls)))] :cljr [(defmacro defrecord-updatable [name fields & impls]
     `(if-cljs
        ~(apply make-record-updatable-cljs name fields impls)
        ~(apply make-record-updatable-clj  name fields impls)))]
 :lpy  [(defmacro defrecord-updatable [name fields & impls]
     `(if-cljs
        ~(apply make-record-updatable-cljs name fields impls)
        ~(apply make-record-updatable-clj  name fields impls)))]
 :cljs [(defmacro defrecord-updatable [name fields & impls]
     (apply make-record-updatable-cljs name fields impls))]))

#?(:clje (defmacro defrecord-updatable [name fields & impls]
           `(if-cljs
              ~(apply make-record-updatable-cljs name fields impls)
              ~(apply make-record-updatable-clj  name fields impls))))

;; ----------------------------------------------------------------------------

;; Forward declares via if-cljs so the Datom eve-deftype's method bodies
;; can resolve these before the defn+ that defines them. The cljs arm keeps
;; the type-hinted stub (declare alone drops the hint on that compiler).
(if-cljs (defn ^number hash-datom [d]) (declare hash-datom))
(if-cljs (defn ^boolean equiv-datom [d o]) (declare equiv-datom))
(if-cljs (defn seq-datom [d]) (declare seq-datom))
(if-cljs (defn nth-datom ([d i]) ([d i not-found])) (declare nth-datom))
(if-cljs (defn assoc-datom [d k v]) (declare assoc-datom))
(if-cljs (defn val-at-datom [d k not-found]) (declare val-at-datom))

(defprotocol IDatom
  (datom-tx [this])
  (datom-added [this])
  (datom-get-idx [this])
  (datom-set-idx [this value]))

;; PORT (PORTING-DS.md T1): Datom is an eve-deftype — it lives inside the
;; index BTSets, inside DBs, inside Eve atoms, so it must be a slab value.
;; Vanilla's ^:unsynchronized-mutable _hash cache is dropped (hash-datom
;; computes on demand, the PSS E2 pattern) and idx is fixed at
;; construction: datom-set-idx throws (serialize's freeze path, its only
;; caller, uses a side table in this port).
(eve-deftype Datom [e a v tx idx]
  IDatom
  (datom-tx [d] (if (pos? tx) tx (- tx)))
  (datom-added [d] (pos? tx))
  (datom-get-idx [_] idx)
  (datom-set-idx [_ _value]
    (throw (ex-info "Datom idx is fixed at construction in the Eve port" {})))

  Object
  (hashCode [d] (int (hash-datom d)))
  (toString [d] (pr-str d))

  clojure.lang.IHashEq
  (hasheq [d] (hash-datom d))

  clojure.lang.Seqable
  (seq [d] (seq-datom d))

  clojure.lang.IPersistentCollection
  (equiv [d o] (and (instance? Datom o) (equiv-datom d o)))
  (empty [d] (throw (enc/unsupported "empty is not supported on Datom")))
  (count [d] 5)
  (cons [d [k v]] (assoc-datom d k v))

  clojure.lang.Indexed
  (nth [this i]           (nth-datom this i))
  (nth [this i not-found] (nth-datom this i not-found))

  clojure.lang.ILookup
  (valAt [d k] (val-at-datom d k nil))
  (valAt [d k nf] (val-at-datom d k nf))

  clojure.lang.Associative
  ;; UNIT2 (#56): ONE Eve map-entry (eve.map-entry/map-entry) on every host; the
  ;; per-host MapEntry constructor branches delete (supersedes
  ;; F-CLJE-MAPENTRY — the beam adapter's [k v] face is eve.map-entry/map-entry's
  ;; on that host, so the app never branches).
  (entryAt [d k] (some->> (val-at-datom d k nil)
                   (me/map-entry k)))
  (containsKey [_ k] (#{:e :a :v :tx :added} k))
  (assoc [d k v] (assoc-datom d k v))

  IPrintWithWriter
  (-pr-writer [d w _opts]
    (e/-write w (str "#eve.db.ds/Datom " (pr-str [(.-e d) (.-a d) (.-v d) (datom-tx d) (datom-added d)])))))

#?(:cljs (goog/exportSymbol "eve.db.ds.db.Datom" Datom))

(defn ^Datom datom
  ([e a v] (->Datom e a v tx0 0))
  ([e a v tx] (->Datom e a v tx 0))
  ([e a v tx added] (->Datom e a v (if added tx (- tx)) 0)))

(defn datom? [x] (instance? Datom x))

(defn+ ^:private hash-datom [^Datom d]
  (-> (hash (.-e d))
    (combine-hashes (hash (.-a d)))
    (combine-hashes (hash (.-v d)))))

(defn+ ^:private equiv-datom [^Datom d ^Datom o]
  (and (== (.-e d) (.-e o))
    (= (.-a d) (.-a o))
    (= (.-v d) (.-v o))))

(defn+ ^:private seq-datom [^Datom d]
  (list (.-e d) (.-a d) (.-v d) (datom-tx d) (datom-added d)))

;; keep it fast by duplicating for both keyword and string cases
;; instead of using sets or some other matching func
(defn+ ^:private val-at-datom [^Datom d k not-found]
  (cond
    (keyword? k)
    (case k
      :e     (.-e d)
      :a     (.-a d)
      :v     (.-v d)
      :tx    (datom-tx d)
      :added (datom-added d)
      not-found)
    
    (string? k)
    (case k
      "e"     (.-e d)
      "a"     (.-a d)
      "v"     (.-v d)
      "tx"    (datom-tx d)
      "added" (datom-added d)
      not-found)
    
    :else
    not-found))

(defn+ ^:private nth-datom
  ([^Datom d ^long i]
   (case i
     0 (.-e d)
     1 (.-a d)
     2 (.-v d)
     3 (datom-tx d)
     4 (datom-added d)
     (throw (enc/index-out-of-bounds (str "Datom/-nth: Index out of bounds: " i)))))
  ([^Datom d ^long i not-found]
   (case i
     0 (.-e d)
     1 (.-a d)
     2 (.-v d)
     3 (datom-tx d)
     4 (datom-added d)
     not-found)))

(defn+ ^:private ^Datom assoc-datom [^Datom d k v]
  (case k
    :e     (datom v       (.-a d) (.-v d) (datom-tx d) (datom-added d))
    :a     (datom (.-e d) v       (.-v d) (datom-tx d) (datom-added d))
    :v     (datom (.-e d) (.-a d) v       (datom-tx d) (datom-added d))
    :tx    (datom (.-e d) (.-a d) (.-v d) v            (datom-added d))
    :added (datom (.-e d) (.-a d) (.-v d) (datom-tx d) v)
    (throw (enc/illegal-argument (str "invalid key for #eve.db.ds/Datom: " k)))))

;; printing and reading
;; #datomic/DB {:schema <map>, :datoms <vector of [e a v tx]>}

(defn ^Datom datom-from-reader [vec]
  (apply datom vec))

#?(:clj
   (defmethod print-method Datom [^Datom d, ^java.io.Writer w]
     (.write w (str "#eve.db.ds/Datom "))
     (binding [*out* w]
       (pr [(.-e d) (.-a d) (.-v d) (datom-tx d) (datom-added d)]))) :cljr (defmethod print-method Datom [^Datom d, ^System.IO.TextWriter w]
     (.Write w (str "#eve.db.ds/Datom "))
     (binding [*out* w]
       (pr [(.-e d) (.-a d) (.-v d) (datom-tx d) (datom-added d)]))))

;; ----------------------------------------------------------------------------
;; datom cmp macros/funcs
;;

;; PORT: one portable defmacro — vanilla's per-host arms (and the
;; :clje twin) were textually identical except the :cljs arm's literal
;; 0 for (num 0); at macro time both are the same number.
(defmacro combine-cmp [& comps]
  (loop [comps (reverse comps)
         res   0]
    (if (not-empty comps)
      (recur
        (next comps)
        `(let [c# ~(first comps)]
           (if (== 0 c#)
             ~res
             c#)))
      res)))


;; PORT: one portable form — vanilla's arms were identical; the :clje
;; twin spelled the halving quot (clojerl's / is float division, and
;; this runs at macro time). quot is the portable spelling: variant
;; counts in case-tree are even at every split (each query doubles the
;; variant list), where quot and / agree.
(defn- -case-tree [queries variants]
  (if queries
    (let [v1 (take (quot (count variants) 2) variants)
          v2 (drop (quot (count variants) 2) variants)]
      (list 'if (first queries)
        (-case-tree (next queries) v1)
        (-case-tree (next queries) v2)))
    (first variants)))

(defmacro case-tree [qs vs]
  (-case-tree qs vs))


(defn cmp
  ;; PORT: the :clj/:cljr :inline metadata branch is dropped — the
  ;; inline expansions were textually the fn body; one portable defn.
  ^long [x y]
  (if (nil? x) 0 (if (nil? y) 0 (long (compare x y)))))

(defn class-identical?
  ;; PORT: :inline metadata branch dropped, as for cmp above.
  [x y]
  (identical? (type x) (type y)))

;; Seat-local type-name order (compared only against other names on
;; the same host). str/type is available on every seat; the JVM
;; Class/getName spelling is not required for a total order.
(defn class-name
  [x]
  (if (nil? x) x (str (type x))))

(defn class-compare
  ^long [x y]
  (long (compare (class-name x) (class-name y))))

(defmacro int-compare [x y]
  `(if-cljs
     (- ~x ~y)
     (long (compare ~x ~y))))

(defn ihash
  ^long [x]
  (hash x))

(if-cljs (defn ^number value-compare [x y]) (declare value-compare))

(defn- seq-compare [xs ys]
  (let [cx (count xs)
        cy (count ys)]
    (cond
      (< cx cy)
      -1
      
      (> cx cy)
      1
      
      :else
      (loop [xs xs
             ys ys]
        (if (empty? xs)
          0
          (let [x (first xs)
                y (first ys)]
            (cond
              (and (nil? x) (nil? y))
              (recur (next xs) (next ys))
                
              (nil? x)
              -1
                
              (nil? y)
              1
                
              :else
              (let [v (value-compare x y)]
                (if (= v 0)
                  (recur (next xs) (next ys))
                  v)))))))))

;; Portable value order: compare only known-orderable same-kind values
;; (numbers across numeric classes; other scalars only when class-identical?)
;; so we never depend on a host Comparable/ClassCast catch. Different
;; classes fall through to class-compare; same-class unorderable values
;; fall through to hash order, matching the previous :else arm.
(defn- orderable-value? [x]
  (or (number? x) (string? x) (keyword? x) (symbol? x)
      (boolean? x) (uuid? x) (inst? x) (char? x)
      (arrays/array? x)))

(defn+ ^number value-compare [x y]
  (cond
    (= x y) 0
    (and (sequential? x) (sequential? y)) (seq-compare x y)
    (and (number? x) (number? y)) (compare x y)
    (and (orderable-value? x) (orderable-value? y) (class-identical? x y))
    (compare x y)
    (not (class-identical? x y)) (class-compare x y)
    :else (int-compare (ihash x) (ihash y))))

(defn cmp-or-incomparable
  "Apply cmp to a and b. If the host compare throws because the values
   are incomparable, return :incomparable. Public so diff-sorted (w2,
   db.cljc ~1081 at base) can reuse this instead of a per-site catch
   branch. value-compare itself no longer throws: it only compares
   orderable values and otherwise uses class/hash order."
  [cmp a b]
  (try
    (cmp a b)
    (catch #?@(:clj [ClassCastException] :cljr [System.InvalidCastException]
            :lpy  [python/TypeError]
            :clje [_]
            :cljs [js/Error]) _
      :incomparable)))

(defn value-cmp
  ;; PORT: :inline metadata branch dropped, as for cmp above.
  ^long [x y]
  (if (nil? x)
    0
    (if (nil? y)
      0
      (value-compare x y))))

;; Slower cmp-* fns allows for datom fields to be nil.
;; Such datoms come from slice method where they are used as boundary markers.

;; Eve seats pass comparators as fns (PSS register-comparator! stores an IFn;
;; BTSet invokes it as (cmp a b)). A portable defn is enough on every host,
;; including JVM — the old :clj arm's java.util.Comparator reify is gone.
(defmacro defcomp [sym [arg1 arg2] & body]
  `(defn ~sym [~arg1 ~arg2]
     ~@body))

(defcomp cmp-datoms-eavt ^long [^Datom d1, ^Datom d2]
  (combine-cmp
    (int-compare (.-e d1) (.-e d2))
    (cmp (.-a d1) (.-a d2))
    (value-cmp (.-v d1) (.-v d2))
    (int-compare (datom-tx d1) (datom-tx d2))))

(defcomp cmp-datoms-aevt ^long [^Datom d1, ^Datom d2]
  (combine-cmp
    (cmp (.-a d1) (.-a d2))
    (int-compare (.-e d1) (.-e d2))
    (value-cmp (.-v d1) (.-v d2))
    (int-compare (datom-tx d1) (datom-tx d2))))

(defcomp cmp-datoms-avet ^long [^Datom d1, ^Datom d2]
  (combine-cmp
    (cmp (.-a d1) (.-a d2))
    (value-cmp (.-v d1) (.-v d2))
    (int-compare (.-e d1) (.-e d2))
    (int-compare (datom-tx d1) (datom-tx d2))))

;; fast versions without nil checks

(defn- cmp-attr-quick
  ^long [a1 a2]
  ;; either both are keywords or both are strings. Portable
  ;; clojure.core/compare covers both on every host (the old cljs
  ;; arm used -compare / garray/defaultCompare for the same order).
  (long (compare a1 a2)))

(defcomp cmp-datoms-eav-quick ^long [^Datom d1, ^Datom d2]
  (combine-cmp
    (int-compare (.-e d1) (.-e d2))
    (cmp-attr-quick (.-a d1) (.-a d2))
    (value-compare (.-v d1) (.-v d2))))

(defcomp cmp-datoms-eavt-quick ^long [^Datom d1, ^Datom d2]
  (combine-cmp
    (int-compare (.-e d1) (.-e d2))
    (cmp-attr-quick (.-a d1) (.-a d2))
    (value-compare (.-v d1) (.-v d2))
    (int-compare (datom-tx d1) (datom-tx d2))))

(defcomp cmp-datoms-aevt-quick ^long [^Datom d1, ^Datom d2]
  (combine-cmp
    (cmp-attr-quick (.-a d1) (.-a d2))
    (int-compare (.-e d1) (.-e d2))
    (value-compare (.-v d1) (.-v d2))
    (int-compare (datom-tx d1) (datom-tx d2))))

(defcomp cmp-datoms-avet-quick ^long [^Datom d1, ^Datom d2]
  (combine-cmp
    (cmp-attr-quick (.-a d1) (.-a d2))
    (value-compare (.-v d1) (.-v d2))
    (int-compare (.-e d1) (.-e d2))
    (int-compare (datom-tx d1) (datom-tx d2))))

;; PORT (PORTING-DS.md T3): the three index comparators are registered
;; under keyword names in the PSS port's comparator registry, and every
;; index construction below passes the NAME, not the fn. A keyword is a
;; slab value on every host; a host fn is not — this is what lets a DB's
;; indexes cross Eve's codec and lets DBs built on different hosts hold
;; field-identical index sets. cmp-of resolves the name at each use.
(set/register-comparator! :eve.db.ds.db/cmp-eavt cmp-datoms-eavt)
(set/register-comparator! :eve.db.ds.db/cmp-aevt cmp-datoms-aevt)
(set/register-comparator! :eve.db.ds.db/cmp-avet cmp-datoms-avet)

(defn- diff-sorted [a b cmp]
  (loop [only-a []
         only-b []
         both   []
         a      a
         b      b]
    (cond
      (empty? a) [(not-empty only-a) (not-empty (into only-b b)) (not-empty both)]
      (empty? b) [(not-empty (into only-a a)) (not-empty only-b) (not-empty both)]
      :else
      (let [first-a (first a)
            first-b (first b)
            diff (cmp-or-incomparable cmp first-a first-b)]
        (cond
          (= diff :incomparable) (recur (conj only-a first-a) (conj only-b first-b) both                (next a) (next b))
          (== diff 0)            (recur only-a                only-b                (conj both first-a) (next a) (next b))
          (< diff 0)             (recur (conj only-a first-a) only-b                both                (next a) b)
          (> diff 0)             (recur only-a                (conj only-b first-b) both                a        (next b)))))))

;; ----------------------------------------------------------------------------

(declare hash-db pr-db hash-fdb equiv-db restore-db indexing? resolve-datom components->pattern)

;;;;;;;;;; Fast validation

(defmacro validate-attr [attr at]
  `(let [attr# ~attr]
     (when-not (or
                 (keyword? attr#)
                 (string? attr#))
       (let [at# ~at]
         (util/raise "Bad entity attribute " attr# " at " at# ", expected keyword or string"
           {:error :transact/syntax, :attribute attr#, :context at#})))))

(defmacro validate-val [v at]
  `(when (nil? ~v)
     (let [at# ~at]
       (util/raise "Cannot store nil as a value at " at#
         {:error :transact/syntax, :value nil, :context at#}))))

;;;;;;;;;; Searching

(defprotocol ISearch
  (-search [data pattern]))

(defn- ^Datom fsearch [data pattern]
  (first (-search data pattern)))

(defprotocol IIndexAccess
  (-datoms [db index c0 c1 c2 c3])
  (-seek-datoms [db index c0 c1 c2 c3])
  (-rseek-datoms [db index c0 c1 c2 c3])
  (-index-range [db attr start end]))

(defn validate-indexed [db index c0 c1 c2 c3]
  (when (= index :avet)
    (when-some [attr c0]
      (when-not (indexing? db attr)
        (util/raise "Attribute " attr " should be marked as :db/index true"
          {:error :index-access :index :avet :components [c0 c1 c2 c3]})))))

(defprotocol IDB
  (-schema [db])
  (-attrs-by [db property]))

;; ----------------------------------------------------------------------------

(defn db-transient [db]
  (-> db
    (update :eavt transient)
    (update :aevt transient)
    (update :avet transient)))

(defn db-persistent! [db]
  (-> db
    (update :eavt persistent!)
    (update :aevt persistent!)
    (update :avet persistent!)))

(defn vpred [v]
  (cond
    (string? v)  (fn [x] (and (string? x) (= v x)))
    (int? v)     (fn [x] (and (int? x) (= (long v) (long x))))
    (keyword? v) (fn [x] (= v x))
    (nil? v)     (fn [x] (nil? x))
    :else        (fn [x] (= v x))))

;; PORT (PORTING-DS.md T2): DB is an eve-deftype with record semantics
;; implemented as protocol groups. Vanilla's defrecord carried three
;; fields that are host objects with no Eve bytes — :pull-patterns and
;; :pull-attrs (LRU caches) and :hash (a host atom caching the DB hash).
;; The pull caches become namespace-level caches (pure memoization;
;; pull_api reads them from here), and hash-db computes on demand with
;; the same combine-hashes formula vanilla cached. FilteredDB stays a
;; host record: it is an ephemeral query view holding a host fn.
(def pull-patterns-cache (call-in-parse-domain (fn [] (lru/cache 100))))
(def pull-attrs-cache (call-in-parse-domain (fn [] (lru/cache 100))))

(defn- db-lookup [db k nf]
  (case k
    :schema  (.-schema db)
    :eavt    (.-eavt db)
    :aevt    (.-aevt db)
    :avet    (.-avet db)
    :max-eid (.-max-eid db)
    :max-tx  (.-max-tx db)
    :rschema (.-rschema db)
    nf))

(declare db-assoc)
;; basilisp resolves deftype method bodies at analysis, so these are
;; declared for that seat; a portable declare is harmless on the others.
(declare -schema -attrs-by -search -datoms -seek-datoms -rseek-datoms -index-range hash-fdb equiv-db pr-db)
(declare multival?)


(defn- db-entries [db]
  ;; PORT (T2 follow-up, upstream suite test-protocols): the record's
  ;; map-entry seq over the port's seven fields, in declaration order.
  ;; The collection group obliges Seqable; vanilla's record inherited
  ;; APersistentMap's seq. The three host-cache fields vanilla carried
  ;; (:hash :pull-patterns :pull-attrs) are NOT fields here (T2), so
  ;; (keys db) differs from vanilla by exactly those three — recorded
  ;; divergence, not a defect.
  ;; PORT DELTA (F-NBB-MACROS round): the entry type is the host's —
  ;; UNIT2 (#56): ONE Eve map-entry (eve.map-entry/map-entry) on every host.
  ;; Construction law: the Eve map-entries are constructed in the DB's
  ;; own domain.
  (call-in-db-domain db (fn []
  (let [entry (fn [k v] (me/map-entry k v))]
    [(entry :schema (.-schema db))
     (entry :eavt (.-eavt db))
     (entry :aevt (.-aevt db))
     (entry :avet (.-avet db))
     (entry :max-eid (.-max-eid db))
     (entry :max-tx (.-max-tx db))
     (entry :rschema (.-rschema db))]))))

(defn- search-result
  "PORT DELTA (F-BB-SEARCH-SEQ): -search (DB) returns slices (PSS
  Iters), Eduction wrappers, or raw index BTSet values -- all
  eve.deftype (SciType) values, and on the :bb seat SCI's vec
  cannot consume them (\"Unable to convert SciType to Object[]\").
  Three transact sites materialize a search with (vec (-search
  ...)) -- :db.fn/cas, :db.fn/retractAttribute and
  :db.fn/retractEntity -- and all three died on bb. The :bb arm
  returns the result's seq instead: same datoms, same order,
  still lazy. Every -search consumer in the port walks the result
  with seq machinery or is one of those vec sites; FilteredDB's
  -search delegates to DB's and filters the seq. Sibling exposure,
  same shape, deliberately not armed here: -seek-datoms,
  -rseek-datoms and -index-range still return Iters on bb (their
  in-port consumers are seq-only today)."
  [x]
  ;; Always seq: under bb/SCI a PSS Iter (eve.deftype / SciType) is
  ;; not vec-able ("Unable to convert SciType to Object[]"). Every
  ;; -search consumer walks with seq machinery or (vec ...), and
  ;; (seq x) is the same datoms in the same order on every host.
  (seq x))

(eve-deftype DB [schema eavt aevt avet max-eid max-tx rschema meta]
  Object
  (hashCode [db] (hash-db db))
  (toString [db] (pr-str db))

  ;; PORT (T2 addendum): the record's meta slot. An eighth field,
  ;; ignored by equiv/hash/keys exactly as a record's __meta is.
  clojure.lang.IMeta
  (meta [db] (.-meta db))

  clojure.lang.IObj
  (withMeta [db m]
    (->DB (.-schema db) (.-eavt db) (.-aevt db) (.-avet db)
      (.-max-eid db) (.-max-tx db) (.-rschema db) m))

  clojure.lang.Seqable
  (seq [db] (seq (db-entries db)))

  ;; marker only (T5 pattern): the collection group already emits the
  ;; count() method; this group makes the class answer Counted so
  ;; RT.count dispatches to it (datom count) instead of walking seq.
  clojure.lang.Counted

  clojure.lang.IHashEq
  (hasheq [db] (hash-db db))

  clojure.lang.ILookup
  (valAt [db k] (db-lookup db k nil))
  (valAt [db k nf] (db-lookup db k nf))

  clojure.lang.Associative
  (assoc [db k v] (db-assoc db k v))
  (entryAt [db k] (let [v (db-lookup db k ::absent)]
                     (when (not (identical? ::absent v))
                       ;; UNIT2 (#56): ONE Eve map-entry (eve.map-entry/map-entry).
                       (me/map-entry k v))))
  (containsKey [db k] (boolean (#{:schema :eavt :aevt :avet :max-eid :max-tx :rschema} k)))

  clojure.lang.IPersistentCollection
  (count [db] (count eavt))
  (equiv [db other] (equiv-db db other))
  (empty [db] ;; Rebuilding the indexes constructs Eve data (BTSet leaves):
             ;; run it in the DB's home domain, like the read paths.
             (call-in-db-domain db
               (fn [] (-> (restore-db
                            {:schema  (.-schema db)
                             :rschema (.-rschema db)
                             :eavt    (empty (.-eavt db))
                             :aevt    (empty (.-aevt db))
                             :avet    (empty (.-avet db))})
                        ;; (.-meta db), not (meta db): the field shadows
                        ;; clojure.core/meta inside method bodies.
                        (with-meta (.-meta db))))))
  (cons [db _v] (throw (ex-info "eve.db.ds.db.DB/cons is not supported" {})))

  clojure.lang.IEditableCollection
  (asTransient [db] (db-transient db))

  clojure.lang.ITransientCollection
  (conj [db _v] (throw (ex-info "eve.db.ds.db.DB/conj! is not supported" {})))
  (persistent [db] (db-persistent! db))

  clojure.lang.Reversible
  (rseq [db] (rseq (.-eavt db)))

  IPrintWithWriter
  (-pr-writer [db w opts] (pr-db db w opts))

  IDB
  ;; The method-body field local, not (.-schema db): on clojerl the
  ;; method and the generated accessor are one module function, so
  ;; the dot spelling recursed without bound. The field local is
  ;; valid on every seat.
  (-schema [db] schema)
  (-attrs-by [db property] ((.-rschema db) property))

  ISearch
  (-search [db pattern]
    ;; Construction law: the slice-bound Datoms are constructed in the
    ;; DB's own domain (see the IIndexAccess note below).
    (call-in-db-domain db (fn []
    (let [[e a v tx] pattern
          eavt       (.-eavt db)
          aevt       (.-aevt db)
          avet       (.-avet db)
          pred       (vpred v)
          multival?  (contains? (-attrs-by db :db.cardinality/many) a)]
      (search-result (case-tree [e a (some? v) tx]
        [(set/slice eavt (datom e a v tx) (datom e a v tx))                   ;; e a v tx
         (set/slice eavt (datom e a v tx0) (datom e a v txmax))               ;; e a v _
         (->> (set/slice eavt (datom e a nil tx0) (datom e a nil txmax))      ;; e a _ tx
           (->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))
         (set/slice eavt (datom e a nil tx0) (datom e a nil txmax))           ;; e a _ _
         (->> (set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))  ;; e _ v tx
           (->Eduction (filter (fn [^Datom d] (and (pred (.-v d))
                                                (= tx (datom-tx d)))))))
         (->> (set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))  ;; e _ v _
           (->Eduction (filter (fn [^Datom d] (pred (.-v d))))))
         (->> (set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))  ;; e _ _ tx
           (->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))
         (set/slice eavt (datom e nil nil tx0) (datom e nil nil txmax))       ;; e _ _ _
         (if (indexing? db a)                                                 ;; _ a v tx
           (->> (set/slice avet (datom e0 a v tx0) (datom emax a v txmax))      
             (->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))
           (->> (set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))
             (->Eduction (filter (fn [^Datom d] (and (pred (.-v d))
                                                  (= tx (datom-tx d))))))))
         (if (indexing? db a)                                                 ;; _ a v _
           (set/slice avet (datom e0 a v tx0) (datom emax a v txmax))
           (->> (set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))
             (->Eduction (filter (fn [^Datom d] (pred (.-v d)))))))
         (->> (set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))  ;; _ a _ tx
           (->Eduction (filter (fn [^Datom d] (= tx (datom-tx d))))))
         (set/slice aevt (datom e0 a nil tx0) (datom emax a nil txmax))       ;; _ a _ _
         (filter (fn [^Datom d] (and (pred (.-v d))
                                  (= tx (datom-tx d)))) eavt)                 ;; _ _ v tx
         (filter (fn [^Datom d] (pred (.-v d))) eavt)                         ;; _ _ v 
         (filter (fn [^Datom d] (= tx (datom-tx d))) eavt)                    ;; _ _ _ tx
         eavt]))))))                                                           ;; _ _ _ _

  IIndexAccess
  ;; Construction law: the pattern/bound Datoms these methods build
  ;; belong to the DB's own domain. The binding sits on the DB methods
  ;; themselves — the choke point every public read entry point
  ;; (core/datoms, core/seek-datoms, core/find-datom, entity, pull,
  ;; datafy) funnels through — and call-in-db-domain defers to an
  ;; ambient context (a swap!, conn/with, q) when one is bound.
  (-datoms [db index c0 c1 c2 c3]
    (call-in-db-domain db (fn []
    (validate-indexed db index c0 c1 c2 c3)
    ;; Always seq the slice: under bb/SCI a PSS Iter is not a host
    ;; ISeq (mapv/vec die on SciType). Every -datoms consumer walks
    ;; with seq machinery; (seq (set/slice ...)) is the same datoms
    ;; in the same order on every host.
    (seq (set/slice (get db index)
           (components->pattern db index c0 c1 c2 c3 e0 tx0)
           (components->pattern db index c0 c1 c2 c3 emax txmax))))))

  (-seek-datoms [db index c0 c1 c2 c3]
    (call-in-db-domain db (fn []
    (validate-indexed db index c0 c1 c2 c3)
    (set/slice (get db index)
      (components->pattern db index c0 c1 c2 c3 e0 tx0)
      (datom emax nil nil txmax)))))

  (-rseek-datoms [db index c0 c1 c2 c3]
    (call-in-db-domain db (fn []
    (validate-indexed db index c0 c1 c2 c3)
    (set/rslice (get db index)
      (components->pattern db index c0 c1 c2 c3 emax txmax)
      (datom e0 nil nil tx0)))))

  (-index-range [db attr start end]
    (call-in-db-domain db (fn []
    (validate-indexed db :avet attr nil nil nil)
    (validate-attr attr (list '-index-range 'db attr start end))
    (set/slice (.-avet db)
      (resolve-datom db nil attr start nil e0 tx0)
      (resolve-datom db nil attr end nil emax txmax)))))
                
  clojure.data/EqualityPartition
  (equality-partition [x] :eve.db.ds/db)

  clojure.data/Diff
  (diff-similar [a b]
    (diff-sorted (:eavt a) (:eavt b) cmp-datoms-eav-quick)))

(defn map->DB [m]
  (->DB (:schema m) (:eavt m) (:aevt m) (:avet m) (:max-eid m) (:max-tx m) (:rschema m) (:meta m)))

(defn- db-assoc [db k v]
  (case k
    :schema  (->DB v (.-eavt db) (.-aevt db) (.-avet db) (.-max-eid db) (.-max-tx db) (.-rschema db) (.-meta db))
    :eavt    (->DB (.-schema db) v (.-aevt db) (.-avet db) (.-max-eid db) (.-max-tx db) (.-rschema db) (.-meta db))
    :aevt    (->DB (.-schema db) (.-eavt db) v (.-avet db) (.-max-eid db) (.-max-tx db) (.-rschema db) (.-meta db))
    :avet    (->DB (.-schema db) (.-eavt db) (.-aevt db) v (.-max-eid db) (.-max-tx db) (.-rschema db) (.-meta db))
    :max-eid (->DB (.-schema db) (.-eavt db) (.-aevt db) (.-avet db) v (.-max-tx db) (.-rschema db) (.-meta db))
    :max-tx  (->DB (.-schema db) (.-eavt db) (.-aevt db) (.-avet db) (.-max-eid db) v (.-rschema db) (.-meta db))
    :rschema (->DB (.-schema db) (.-eavt db) (.-aevt db) (.-avet db) (.-max-eid db) (.-max-tx db) v (.-meta db))
    (throw (ex-info (str "eve.db.ds.db.DB has no field " k) {:key k}))))


(defn db? [x]
  (and (satisfies? ISearch x)
    (satisfies? IIndexAccess x)
    (satisfies? IDB x)))

;; ----------------------------------------------------------------------------
;; No functions in Eve memory (John, 2026-11-11): a FilteredDB is a
;; long-lived DB value, so it must not hold its predicate fn. The pred
;; travels with the filter call and is kept in this host-side registry
;; (the Conn-listener pattern: host handles stay host-side); the
;; FilteredDB value carries only the registry id. A FilteredDB whose
;; pred is not registered in this process — e.g. one restored from
;; durable Eve memory in a fresh process — cannot be searched; it fails
;; with a clear error naming the remedy (re-create it with filter).
(defonce ^:private filter-preds (atom {}))
(defonce ^:private filter-pred-ids (atom 0))

(defn register-filter-pred!
  "Register a FilteredDB predicate fn host-side; returns its id."
  [pred]
  (let [id (swap! filter-pred-ids inc)]
    (swap! filter-preds assoc id pred)
    id))

(defn filter-pred-of
  "Resolve a FilteredDB's predicate from the host-side registry."
  [fdb]
  (let [id (.-pred-id fdb)]
    (or (get @filter-preds id)
      (throw (ex-info "FilteredDB predicate is not available in this process: predicates are host-side call state and are not stored in Eve memory. Re-create this DB view with (filter db pred)."
               {:eve.db.ds/error :filter/pred-not-registered, :pred-id id})))))

(deftype FilteredDB [unfiltered-db pred-id]
  ;; UNIT3 (#56): ONE definition, eve.deftype/deftype. The per-host arm
  ;; splices (cljs IHash/IEquiv/ICounted/IPrintWithWriter/... and the JVM
  ;; Object/IHashEq/IPersistentCollection/ILookup and the CLR arms) are
  ;; Eve's types-layer translations of these groups; the un-filterable
  ;; operations throw through eve.encoding/unsupported (adapter edge).
  IHash                (-hash  [db]        (hash-fdb db))
  IEquiv               (-equiv [db other]  (equiv-db db other))
  ICounted             (-count [db]        (count (-datoms db :eavt nil nil nil nil)))
  IPrintWithWriter     (-pr-writer [db w opts] (pr-db db w opts))

  IEmptyableCollection (-empty [_]         (throw (enc/unsupported "empty is not supported on FilteredDB")))

  ILookup              (-lookup [_ _]      (throw (enc/unsupported "-lookup is not supported on FilteredDB")))
                       (-lookup [_ _ _]    (throw (enc/unsupported "-lookup is not supported on FilteredDB")))

  IAssociative         (-contains-key? [_ _] (throw (enc/unsupported "-contains-key? is not supported on FilteredDB")))
                       (-assoc [_ _ _]       (throw (enc/unsupported "-assoc is not supported on FilteredDB")))

  ICollection          (-conj [db o]       (throw (enc/unsupported "cons is not supported on FilteredDB")))
  IDB
  (-schema [db]
    (-schema (.-unfiltered-db db)))
  (-attrs-by [db property]
    (-attrs-by (.-unfiltered-db db) property))
  ISearch
  (-search [db pattern]
    (filter (filter-pred-of db) (-search (.-unfiltered-db db) pattern)))
  IIndexAccess
  (-datoms [db index c0 c1 c2 c3]
    (filter (filter-pred-of db) (-datoms (.-unfiltered-db db) index c0 c1 c2 c3)))
  (-seek-datoms [db index c0 c1 c2 c3]
    (filter (filter-pred-of db) (-seek-datoms (.-unfiltered-db db) index c0 c1 c2 c3)))
  (-rseek-datoms [db index c0 c1 c2 c3]
    (filter (filter-pred-of db) (-rseek-datoms (.-unfiltered-db db) index c0 c1 c2 c3)))
  (-index-range [db attr start end]
    (filter (filter-pred-of db) (-index-range (.-unfiltered-db db) attr start end))))

;; PORT DELTA (D5, PORTING-DS.md — AS CORRECTED by measurement):
;; an earlier form of this delta, (def FilteredDB FilteredDB),
;; is ILLEGAL in the class's home namespace ("Expecting var, but
;; FilteredDB is mapped to class") — a def cannot intern over a
;; class mapping. It is also unnecessary: the port's consumers
;; splice the class name per dialect at every FilteredDB use
;; (conn.cljc:31, core.cljc:247/261 — (instance? #?(:clj FilteredDB
;; :cljs db/FilteredDB) x), (FilteredDB. ...) on :clj), the shape
;; the F-NBB-MACROS round established for exactly these names. No
;; form belongs here; D5's entry records the full diagnosis.

(defn filter-db
  "Build a FilteredDB view over db with pred (a fn of one datom, with
   any db it needs closed over). Composes with an existing FilteredDB
   the way vanilla's nested filters do. The pred is registered
   host-side; the FilteredDB value holds only its id."
  [db pred]
  ;; Construction law: the FilteredDB value is constructed in the
  ;; underlying DB's own domain.
  (call-in-db-ish-domain db (fn []
  (if (instance? FilteredDB db)
    (let [orig-db   (.-unfiltered-db db)
          orig-pred (filter-pred-of db)]
      (->FilteredDB orig-db
        (register-filter-pred! (fn [d] (and (orig-pred d) (pred d))))))
    (->FilteredDB db (register-filter-pred! pred))))))

(defn unfiltered-db [db]
  (if (instance? FilteredDB db)
    (.-unfiltered-db ^FilteredDB db)
    db))

(defn filtered-db? [x]
  (instance? FilteredDB x))

(defn db-domain-sio
  "The Eve domain sio to construct results in for a DB-ish value (a DB
   or a FilteredDB view of one): the underlying DB's own sio."
  [db]
  (db-sio (unfiltered-db db)))

(defn call-in-db-ish-domain
  "Call f in the domain of a DB-ish value (see db-domain-sio), deferring
   to an ambient domain context when one is bound (as call-in-db-domain)."
  [db f]
  (if (some? eve-data/*domain-sio*)
    (f)
    (call-in-domain (db-domain-sio db) f)))

;; ----------------------------------------------------------------------------

(defn attr->properties [k v]
  (cond
    (= :db/tupleAttrs k)                  [:db.type/tuple :db/index :db/tupleAttrs]
    (= :db/tupleTypes k)                  [:db.type/tuple :db/tupleTypes]
    (= :db/tupleType k)                   [:db.type/tuple :db/tupleType]
    (= :db.unique/identity v)             [:db/unique :db.unique/identity :db/index]
    (= :db.unique/value v)                [:db/unique :db.unique/value :db/index]
    (= :db.cardinality/many v)            [:db.cardinality/many]
    (= :db.type/ref v)                    [:db.type/ref :db/index]
    (and (= :db/isComponent k) (true? v)) [:db/isComponent]
    (and (= :db/index k) (true? v))       [:db/index]
    :else                                 []))

(defn attr-tuples
  "e.g. :reg/semester => #{:reg/semester+course+student ...}"
  [schema rschema]
  (reduce
    (fn [m tuple-attr] ;; e.g. :reg/semester+course+student
      (util/reduce-indexed
        (fn [m src-attr idx] ;; e.g. :reg/semester
          (update m src-attr assoc tuple-attr idx))
        m
        (-> schema (get tuple-attr) :db/tupleAttrs)))
    {}
    (:db/tupleAttrs rschema)))

(defn- rschema
  ":db/unique           => #{attr ...}
   :db.unique/identity  => #{attr ...}
   :db.unique/value     => #{attr ...}
   :db/index            => #{attr ...}
   :db.cardinality/many => #{attr ...}
   :db.type/ref         => #{attr ...}
   :db/isComponent      => #{attr ...}
   :db.type/tuple       => #{attr ...}
   :db/tupleAttrs       => #{attr ...}
   :db/tupleTypes       => #{attr ...}
   :db/tupleType        => #{attr ...}
   :db/attrTuples       => {attr => {tuple-attr => idx}}"
  [schema]
  (let [rschema (reduce-kv
                  (fn [rschema attr attr-schema]
                    (reduce-kv
                      (fn [rschema key value]
                        (reduce
                          (fn [rschema prop]
                            (update rschema prop util/conjs attr))
                          rschema (attr->properties key value)))
                      rschema attr-schema))
                  {} schema)]
    (assoc rschema :db/attrTuples (attr-tuples schema rschema))))

(defn- validate-schema-key [a k v expected]
  (when-not (or (nil? v)
              (contains? expected v))
    (throw (ex-info (str "Bad attribute specification for " (pr-str {a {k v}}) ", expected one of " expected)
             {:error :schema/validation
              :attribute a
              :key k
              :value v}))))

(defn- validate-schema [schema]
  (doseq [[a kv] schema]

    ;; isComponent
    (let [comp? (:db/isComponent kv false)]
      (validate-schema-key a :db/isComponent (:db/isComponent kv) #{true false})
      (when (and comp? (not= (:db/valueType kv) :db.type/ref))
        (util/raise "Bad attribute specification for " a ": {:db/isComponent true} should also have {:db/valueType :db.type/ref}"
          {:error     :schema/validation
           :attribute a
           :key       :db/isComponent})))

    (validate-schema-key a :db/unique (:db/unique kv) #{:db.unique/value :db.unique/identity})
    (validate-schema-key a :db/valueType (:db/valueType kv) #{:db.type/ref :db.type/tuple})
    (validate-schema-key a :db/cardinality (:db/cardinality kv) #{:db.cardinality/one :db.cardinality/many})

    ;; tuple should have exactly one of tupleAttrs | tupleTypes | tupleType
    (let [tuple-props (filterv #(contains? kv %) [:db/tupleAttrs :db/tupleTypes :db/tupleType])]
      (when (and
              (= :db.type/tuple (:db/valueType kv))
              (empty? tuple-props))
        (util/raise "Bad attribute specification for " a ": {:db/valueType :db.type/tuple} should also have :db/tupleAttrs, :db/tupleTypes or :db/tupleType"
          {:error :schema/validation
           :attribute a
           :key :db/valueType}))

      (when (< 1 (count tuple-props))
        (util/raise "Bad attribute specification for " a ": only one of :db/tupleAttrs, :db/tupleTypes, :db/tupleType is allowed, got " tuple-props
          {:error :schema/validation
           :attribute a
           :key (second tuple-props)})))

    ;; :db/tupleAttrs is a non-empty sequential coll
    (when (contains? kv :db/tupleAttrs)
      (let [ex-data {:error :schema/validation
                     :attribute a
                     :key :db/tupleAttrs}]
        (when (= :db.cardinality/many (:db/cardinality kv))
          (util/raise a " has :db/tupleAttrs, must be :db.cardinality/one" ex-data))

        (let [attrs (:db/tupleAttrs kv)]
          (when-not (sequential? attrs)
            (util/raise a " :db/tupleAttrs must be a sequential collection, got: " attrs ex-data))

          (when (empty? attrs)
            (util/raise a " :db/tupleAttrs can’t be empty" ex-data))

          (doseq [attr attrs
                  :let [ex-data (assoc ex-data :value attr)]]
            (when (contains? (get schema attr) :db/tupleAttrs)
              (util/raise a " :db/tupleAttrs can’t depend on another tuple attribute: " attr ex-data))

            (when (= :db.cardinality/many (:db/cardinality (get schema attr)))
              (util/raise a " :db/tupleAttrs can’t depend on :db.cardinality/many attribute: " attr ex-data))))))

    ;; :db/tupleTypes is a sequential coll of at least 2 keywords
    (when (contains? kv :db/tupleTypes)
      (let [types (:db/tupleTypes kv)]
        (when-not (and (sequential? types)
                    (<= 2 (count types))
                    (every? keyword? types))
          (util/raise a " :db/tupleTypes must be a sequential collection of at least 2 keywords, got: " types
            {:error :schema/validation
             :attribute a
             :key :db/tupleTypes}))))

    ;; :db/tupleType is a keyword
    (when (contains? kv :db/tupleType)
      (let [t (:db/tupleType kv)]
        (when-not (keyword? t)
          (util/raise a " :db/tupleType must be a keyword, got: " t
            {:error :schema/validation
             :attribute a
             :key :db/tupleType}))))))
  
(defn ^DB empty-db [schema opts]
  {:pre [(or (nil? schema) (map? schema))]}
  (validate-schema schema)
  ;; Construction law: a new DB is built in its home domain — the
  ;; process domain, explicitly bound (see call-in-process-domain).
  (call-in-process-domain (fn []
  (map->DB
    {:schema        schema
     :rschema       (rschema (merge implicit-schema schema))
     :eavt          (set/sorted-set* (assoc opts :cmp :eve.db.ds.db/cmp-eavt))
     :aevt          (set/sorted-set* (assoc opts :cmp :eve.db.ds.db/cmp-aevt))
     :avet          (set/sorted-set* (assoc opts :cmp :eve.db.ds.db/cmp-avet))
     :max-eid       e0
     :max-tx        tx0
     :pull-patterns (lru/cache 100)
     :pull-attrs    (lru/cache 100)
     :hash          (atom 0)}))))

(defn- init-max-eid [rschema eavt avet]
  (let [max     #(if (and %2 (> %2 %1)) %2 %1)
        max-eid (some->
                  (set/rslice eavt
                    (datom (dec tx0) nil nil txmax)
                    (datom e0 nil nil tx0))
                  first :e)
        res     (max e0 max-eid)
        max-ref (fn [attr]
                  (some->
                    (set/rslice avet
                      (datom (dec tx0) attr (dec tx0) txmax)
                      (datom e0 attr e0 tx0))
                    first :v))
        refs    (:db.type/ref rschema)
        res     (reduce
                  (fn [res attr]
                    (max res (max-ref attr)))
                  res refs)]
    res))

(defn ^DB init-db [datoms schema opts]
  ;; Construction law: as empty-db — the process domain, explicitly
  ;; bound. The binding covers the datom? check too: `datoms` may be a
  ;; lazy seq of not-yet-constructed Datoms (e.g. a caller's
  ;; (map #(apply datom %) rows)), and drop-while realizes it — that
  ;; construction belongs to the new DB's home domain, the process
  ;; domain.
  (call-in-process-domain (fn []
  (when-some [not-datom (first (drop-while datom? datoms))]
    (util/raise "init-db expects list of Datoms, got " (type not-datom)
      {:error :init-db}))
  (validate-schema schema)
  (let [rschema     (rschema (merge implicit-schema schema))
        indexed     (:db/index rschema)
        arr         (cond-> datoms
                      (not (arrays/array? datoms)) (arrays/into-array))
        ;; PORT (D2/S2): vanilla's asort sorts the host array in place
        ;; and the result is discarded; the port's asort returns the
        ;; sorted Eve vector, so it must be THREADED. The discarded
        ;; sorts left init-db's indexes in insertion order (upstream
        ;; pull-api's init-db-built *test-db exposed it).
        arr         (arrays/asort arr cmp-datoms-eavt-quick)
        eavt        (set/from-sorted-array :eve.db.ds.db/cmp-eavt arr (arrays/alength arr) opts)
        arr         (arrays/asort arr cmp-datoms-aevt-quick)
        aevt        (set/from-sorted-array :eve.db.ds.db/cmp-aevt arr (arrays/alength arr) opts)
        avet-datoms (filter (fn [^Datom d] (contains? indexed (.-a d))) datoms)
        avet-arr    (arrays/into-array avet-datoms) ;; PORT (D2 addendum): vanilla's to-array makes a host Object[]; the port's array substrate is Eve vectors (PORTING.md S)
        avet-arr    (arrays/asort avet-arr cmp-datoms-avet-quick)
        avet        (set/from-sorted-array :eve.db.ds.db/cmp-avet avet-arr (arrays/alength avet-arr) opts)
        max-eid     (init-max-eid rschema eavt avet)
        max-tx      (transduce (map (fn [^Datom d] (datom-tx d))) max tx0 eavt)]
    (map->DB
      {:schema        schema
       :rschema       rschema
       :eavt          eavt
       :aevt          aevt
       :avet          avet
       :max-eid       max-eid
       :max-tx        max-tx
       :pull-patterns (lru/cache 100)
       :pull-attrs    (lru/cache 100)
       :hash          (atom 0)})))))

(defn+ ^DB restore-db [{:keys [schema eavt aevt avet max-eid max-tx] :as keys}]
  ;; Construction law: the restored DB adopts the domain of the index
  ;; sets it is rebuilt from (they carry the target domain with them).
  (call-in-domain (eve-data/-eve-sio eavt) (fn []
  (map->DB
    {:schema        schema
     :rschema       (or (:rschema keys)
                      (rschema (merge implicit-schema schema)))
     :eavt          eavt
     :aevt          aevt
     :avet          avet
     :max-eid       (or max-eid e0)
     :max-tx        (or max-tx tx0)
     :pull-patterns (lru/cache 100)
     :pull-attrs    (lru/cache 100)
     :hash          (atom 0)}))))

(defn with-schema [db schema]
  {:pre [(db? db) (or (nil? schema) (map? schema))]}
  ;; PORT (T2): vanilla assocs the record; the port reconstructs.
  ;; Construction law: the reconstruction happens in db's own domain.
  (call-in-db-domain db (fn []
    (->DB schema
      (.-eavt db) (.-aevt db) (.-avet db) (.-max-eid db) (.-max-tx db)
      (rschema (merge implicit-schema schema)) (.-meta db)))))

(defn- equiv-db-index [x y]
  (loop [xs (seq x)
         ys (seq y)]
    (cond
      (nil? xs) (nil? ys)
      (= (first xs) (first ys)) (recur (next xs) (next ys))
      :else false)))

(defn+ ^:private ^number hash-db [^DB db]
  ;; PORT (T2): vanilla cached this in a host atom field; the port
  ;; computes the same value on demand.
  (combine-hashes (hash (.-schema db))
    (hash (.-eavt db))))

(defn+ ^:private ^number hash-fdb [^FilteredDB db]
  ;; Computed on demand, like hash-db: the port carries no host atom
  ;; hash cache in the value (and no fn — see filter-preds above).
  (let [datoms (or (-datoms db :eavt nil nil nil nil) #{})]
    (combine-hashes (hash (-schema db))
      (hash-unordered-coll datoms))))

(defn+ ^:private ^boolean equiv-db [db other]
  (and (or (instance? DB other) (instance? FilteredDB other))
    (= (-schema db) (-schema other))
    (equiv-db-index (-datoms db :eavt nil nil nil nil) (-datoms other :eavt nil nil nil nil))))

(defn pr-db
  "Print a DB (or FilteredDB) as #eve.db.ds/DB {:schema ... :datoms [...]}.
   One writer surface (eve.core/-write) on every host. eve.deftype
   turns the type's IPrintWithWriter into the host print-method, so
   the old clj/cljr defmethod print-method twins are gone. The
   printed shape and db-from-reader contract are unchanged."
  ([db w] (pr-db db w nil))
  ([db w _opts]
   (e/-write w "#eve.db.ds/DB {")
   (e/-write w ":schema ")
   (e/-write w (pr-str (-schema db)))
   (e/-write w ", :datoms ")
   (e/-write w (pr-str (mapv (fn [d] [(.-e d) (.-a d) (.-v d) (datom-tx d)])
                             (-datoms db :eavt nil nil nil nil))))
   (e/-write w "}")))

(defn db-from-reader [{:keys [schema datoms]}]
  (init-db (map (fn [[e a v tx]] (datom e a v tx)) datoms) schema {}))

;; ----------------------------------------------------------------------------

(declare entid-strict ref? tuple? resolve-tuple-refs)

(defn+ resolve-datom [db e a v t default-e default-tx]
  (when (some? a)
    (validate-attr a (list 'resolve-datom 'db e a v t)))
  (datom
    (if (some? e) (entid-strict db e) default-e)
    a
    (cond
      (nil? v)      v
      (ref? db a)   (entid-strict db v)
      (tuple? db a) (resolve-tuple-refs db a v (list 'resolve-datom 'db e a v t))
      :else         v)
    (if (some? t) (entid-strict db t) default-tx)))

(defn+ components->pattern [db index c0 c1 c2 c3 default-e default-tx]
  (case index
    :eavt (resolve-datom db c0 c1 c2 c3 default-e default-tx)
    :aevt (resolve-datom db c1 c0 c2 c3 default-e default-tx)
    :avet (resolve-datom db c2 c0 c1 c3 default-e default-tx)))

(defn find-datom [db index c0 c1 c2 c3]
  ;; Construction law: the pattern Datoms are constructed in the DB's
  ;; own domain (db-ish: db may be a FilteredDB view).
  (call-in-db-ish-domain db (fn []
  (validate-indexed db index c0 c1 c2 c3)
  (let [set     (get db index)
        ;; Index sets store a registered comparator name; name the
        ;; cmp-datoms-* fn directly. (first (set/slice set from to))
        ;; is the first element >= from, bounded by to — the same
        ;; window the old seek form used, and it works under SCI
        ;; where (seq btset) is a plain LazySeq with no ISeek.
        cmp     (case index
                  :eavt cmp-datoms-eavt
                  :aevt cmp-datoms-aevt
                  :avet cmp-datoms-avet)
        from    (components->pattern db index c0 c1 c2 c3 e0 tx0)
        to      (components->pattern db index c0 c1 c2 c3 emax txmax)
        datom   (first (set/slice set from to))]
    (when (and (some? datom) (<= 0 (cmp to datom)))
      datom)))))

;; ----------------------------------------------------------------------------

;; TxReport is a HOST record (John, 2026-11-12): it is never put into an
;; Eve atom; it lives host-side in relation with Eve, and mid-transaction
;; it legitimately holds call-time host values (tempid bookkeeping, tx
;; data in flight) that a slab eve.record's normalize gate rightly
;; refuses. Only types that enter Eve atoms (DB, Datom) are slab records.
(defrecord TxReport [db-before db-after tx-data tempids tx-meta])

(defn+ ^boolean is-attr? [db attr property]
  (contains? (-attrs-by db property) attr))

(defn+ ^boolean multival? [db attr]
  (is-attr? db attr :db.cardinality/many))

(defn+ ^boolean multi-value? [db attr value]
  (and
    (is-attr? db attr :db.cardinality/many)
    (or
      (arrays/array? value)
      (and (coll? value) (not (map? value))))))

(defn+ ^boolean ref? [db attr]
  (is-attr? db attr :db.type/ref))

(defn+ ^boolean component? [db attr]
  (is-attr? db attr :db/isComponent))

(defn+ ^boolean indexing? [db attr]
  (is-attr? db attr :db/index))

(defn+ ^boolean tuple? [db attr]
  (is-attr? db attr :db.type/tuple))

(defn+ ^boolean tuple-source? [db attr]
  (is-attr? db attr :db/attrTuples))

(defn+ ^boolean composite-tuple? [db attr]
  (is-attr? db attr :db/tupleAttrs))

(defn+ ^boolean reverse-ref? [attr]
  (cond
    (keyword? attr)
    (= \_ (nth (name attr) 0))
    
    (string? attr)
    (boolean (re-matches #"(?:([^/]+)/)?_([^/]+)" attr))
   
    :else
    (util/raise "Bad attribute type: " attr ", expected keyword or string"
      {:error :transact/syntax, :attribute attr})))

(defn reverse-ref [attr]
  (cond
    (keyword? attr)
    (if (reverse-ref? attr)
      (keyword (namespace attr) (subs (name attr) 1))
      (keyword (namespace attr) (str "_" (name attr))))

    (string? attr)
    (let [[_ ns name] (re-matches #"(?:([^/]+)/)?([^/]+)" attr)]
      (if (= \_ (nth name 0))
        (if ns (str ns "/" (subs name 1)) (subs name 1))
        (if ns (str ns "/_" name) (str "_" name))))
   
    :else
    (util/raise "Bad attribute type: " attr ", expected keyword or string"
      {:error :transact/syntax, :attribute attr})))

(defn- validate-tuple
  "Validates that a heterogeneous/homogeneous tuple attr value is a vector
   of the declared arity. nils are checked separately in validate-val"
  ([db a v context]
   (validate-tuple db a (-> db -schema (get a)) v context))
  ([_db a schema-a v context]
   (when (some? v)
     (when (contains? schema-a :db/tupleTypes)
      (let [types (:db/tupleTypes schema-a)]
         (when-not (and (vector? v) (= (count v) (count types)))
           (util/raise "Attribute " a " expected a " (count types) "-element vector, got: " v " in " context
             {:error :transact/syntax, :attribute a, :value v, :context context}))))
     (when (contains? schema-a :db/tupleType)
       (when-not (vector? v)
         (util/raise "Attribute " a " expected a vector, got: " v " in " context
           {:error :transact/syntax, :attribute a, :value v, :context context}))))))

(defn- tuple-ref-slot? [db schema-a idx]
  (cond
    (:db/tupleAttrs schema-a) (ref? db (nth (:db/tupleAttrs schema-a) idx nil))
    (:db/tupleTypes schema-a) (= :db.type/ref (nth (:db/tupleTypes schema-a) idx nil))
    :else                     (= :db.type/ref (:db/tupleType schema-a))))

(defn- resolved-eid? [x]
  (or
    (nil? x)
    (and (number? x) (not (neg? x)))))


(defn+ resolve-tuple-refs
  "Resolves lookup refs and idents in ref slots of a tuple value.
   Read path: no tempids, unresolved refs raise"
  [db a vs context]
  (let [schema-a (-> db -schema (get a))]
    (validate-tuple db a schema-a vs context)
    (if-not (sequential? vs) ;; e.g. nil ov in :db.fn/cas
      vs
      (into []
        (map-indexed
          (fn [idx v]
            (if (and
                  (tuple-ref-slot? db schema-a idx)
                  (not (resolved-eid? v)))
              (entid-strict db v)
              v)))
        vs))))

(defn+ ^number entid [db eid]
  {:pre [(db? db)]}
  (cond
    (and (number? eid) (pos? eid))
    (if (> eid emax)
      (util/raise "Highest supported entity id is " emax ", got " eid {:error :entity-id :value eid})
      eid)
    
    (sequential? eid)
    (let [[attr value] eid]
      (cond
        (not= (count eid) 2)
        (util/raise "Lookup ref should contain 2 elements: " eid
          {:error :lookup-ref/syntax, :entity-id eid})
        
        (not (is-attr? db attr :db/unique))
        (util/raise "Lookup ref attribute should be marked as :db/unique: " eid
          {:error :lookup-ref/unique, :entity-id eid})
        
        (nil? value)
        nil

        (tuple? db attr)
        (let [value' (resolve-tuple-refs db attr value eid)]
          (-> (-datoms db :avet attr value' nil nil) first :e))

        :else
        (-> (-datoms db :avet attr value nil nil) first :e)))
    
    (keyword? eid)
    (-> (-datoms db :avet :db/ident eid nil nil) first :e)

    :else
    (util/raise "Expected number or lookup ref for entity id, got " eid
      {:error :entity-id/syntax, :entity-id eid})))

(defn+ ^boolean numeric-eid-exists? [db eid]
  (= eid (-> (-seek-datoms db :eavt eid nil nil nil) first :e)))

(defn+ ^number entid-strict [db eid]
  (or
    (entid db eid)
    (util/raise "Nothing found for entity id " eid
      {:error :entity-id/missing
       :entity-id eid})))

(defn+ ^number entid-some [db eid]
  (when (some? eid)
    (entid-strict db eid)))

;;;;;;;;;; Transacting

(def *last-auto-tempid
  (atom 0))

;; UNIT1 (DISPATCH #56, one-Eve-implementation): AutoTempid is ONE
;; definition, eve.deftype/deftype — print fidelity only (tempid
;; resolution reads the type and field, not Object methods). The print
;; body is the Datom house style (IPrintWithWriter over e/-write +
;; pr-str): eve.deftype realizes it per host (print-method on the
;; JVM/CLR/basilisp faces, the protocol on bb/jank/cljs). The old four
;; per-host arm splices and the two print-method defmethods delete.
(deftype AutoTempid [id]
  Object
  (toString [this]
    (str "#eve.db.ds/AutoTempid [" id "]"))
  IPrintWithWriter
  (-pr-writer [this writer opts]
    (e/-write writer (str "#eve.db.ds/AutoTempid [" (.-id this) "]"))))

(defn auto-tempid []
  (AutoTempid. (swap! *last-auto-tempid inc)))

(defn+ ^boolean auto-tempid? [x]
  (instance? AutoTempid x))

(defn assoc-auto-tempids [db tx-data]
  (for [entity tx-data]
    (util/cond+
      (map? entity)
      (reduce-kv
        (fn [entity a v]
          (cond
            (not (or (keyword? a) (string? a)))
            (assoc entity a v)
             
            (and (ref? db a) (multi-value? db a v))
            (assoc entity a (assoc-auto-tempids db v))
                
            (ref? db a)
            (assoc entity a (first (assoc-auto-tempids db [v])))
             
            (and (reverse-ref? a) (sequential? v))
            (assoc entity a (assoc-auto-tempids db v))
             
            (reverse-ref? a)
            (assoc entity a (first (assoc-auto-tempids db [v])))
                
            :else
            (assoc entity a v)))
        {}
        (if (contains? entity :db/id)
          entity
          (assoc entity :db/id (auto-tempid))))
       
      (and
        (sequential? entity)
        :let [[op e a v] entity]
        (= :db/add op)
        (ref? db a))
      (if (multi-value? db a v)
        [op e a (assoc-auto-tempids db v)]
        [op e a (first (assoc-auto-tempids db [v]))])
        
      :else
      entity)))

(defn validate-datom [db ^Datom datom]
  (when (and (datom-added datom)
          (is-attr? db (.-a datom) :db/unique))
    (when-some [found (not-empty (-datoms db :avet (.-a datom) (.-v datom) nil nil))]
      (util/raise "Cannot add " datom " because of unique constraint: " found
        {:error :transact/unique
         :attribute (.-a datom)
         :datom datom}))))

(defn- current-tx
  ^long [report]
  (-> report :db-before :max-tx long inc))

(defn- next-eid
  ^long [db]
  (inc (long (:max-eid db))))

(defn- tx-id?
  [e]
  (or (= e :db/current-tx)
    (= e ":db/current-tx") ;; for JS interop
    (= e "datomic.tx")
    (= e "eve.db.ds.tx")))

(defn- tempid? [x]
  (or
    (and (number? x) (neg? x))
    (string? x)
    (auto-tempid? x)))

(defn- new-eid? [db eid]
  (and
    (> eid (:max-eid db))
    (< eid tx0))) ;; tx0 is max eid

(defn- advance-max-eid [db eid]
  (cond-> db
    (new-eid? db eid)
    (assoc :max-eid eid)))

(defn- allocate-eid
  ([report eid]
   (update report :db-after advance-max-eid eid))
  ([report e eid]
   (cond-> report
     (tx-id? e)
     (->
       (update :tempids assoc e eid)
       (update ::reverse-tempids update eid util/conjs e))
     
     (tempid? e)
     (->
       (update :tempids assoc e eid)
       (update ::reverse-tempids update eid util/conjs e))

     (and (not (tempid? e)) (new-eid? (:db-after report) eid))
     (update :tempids assoc eid eid)

     true
     (update :db-after advance-max-eid eid))))

(defn- resolve-tuple-value
  "Resoleves refs, tempids and :db/current-tx in ref slots. Returns [report' vs']"
  [report a vs context]
  (let [db       (:db-after report)
        schema-a (-> db -schema (get a))
        _        (validate-tuple db a schema-a vs context)]
    (if-not (vector? vs)
      [report vs]
      (loop [idx     0
             report' report
             vs'     (transient [])]
        (if (>= idx (count vs))
          [report' (persistent! vs')]
          (let [v (nth vs idx)]
            (if (tuple-ref-slot? db schema-a idx)
              (let [[report'' v'] (cond
                                    (resolved-eid? v)
                                    [report' v]

                                    (tx-id? v)
                                    (let [id (current-tx report')]
                                      [(allocate-eid report' v id) id])

                                    (tempid? v)
                                    (if-some [resolved (get (:tempids report') v)]
                                      [(update report' ::value-tempids assoc resolved v) resolved]
                                      (let [resolved (next-eid db)]
                                        [(-> report'
                                             (allocate-eid v resolved)
                                             (update ::value-tempids assoc resolved v))
                                         resolved]))

                                    :else ;; lookup ref or ident
                                    [report' (entid-strict db v)])]

                (recur (inc idx) report'' (conj! vs' v')))
              (recur (inc idx) report' (conj! vs' v)))))))))

;; In context of `with-datom` we can use faster comparators which
;; do not check for nil (~10-15% performance gain in `transact`)

(defn with-datom [db ^Datom datom]
  (validate-datom db datom)
  (let [indexing? (indexing? db (.-a datom))]
    (if (datom-added datom)
      (cond-> db
        true      (update :eavt set/conj datom cmp-datoms-eavt-quick)
        true      (update :aevt set/conj datom cmp-datoms-aevt-quick)
        indexing? (update :avet set/conj datom cmp-datoms-avet-quick)
        true      (advance-max-eid (.-e datom))
)
      (if-some [removing (fsearch db [(.-e datom) (.-a datom) (.-v datom)])]
        (cond-> db
          true      (update :eavt set/disj removing cmp-datoms-eavt-quick)
          true      (update :aevt set/disj removing cmp-datoms-aevt-quick)
          indexing? (update :avet set/disj removing cmp-datoms-avet-quick)
  )
        db))))

(defn- queue-tuple [queue tuple idx db e a v]
  (let [tuple-value  (or (get queue tuple)
                       (:v (first (-datoms db :eavt e tuple nil nil)))
                       (vec (repeat (-> db (-schema) (get tuple) :db/tupleAttrs count) nil)))
        tuple-value' (assoc tuple-value idx v)]
    (assoc queue tuple tuple-value')))

(defn- queue-tuples [queue tuples db e a v]
  (reduce-kv
    (fn [queue tuple idx]
      (queue-tuple queue tuple idx db e a v))
    queue
    tuples))

(defn- transact-report [report datom]
  (let [db      (:db-after report)
        a       (:a datom)
        report' (-> report
                  (assoc :db-after (with-datom db datom))
                  (update :tx-data conj datom))]
    (if (tuple-source? db a)
      (let [e      (:e datom)
            v      (if (datom-added datom) (:v datom) nil)
            queue  (or (-> report' ::queued-tuples (get e)) {})
            tuples (get (-attrs-by db :db/attrTuples) a)
            queue' (queue-tuples queue tuples db e a v)]
        (update report' ::queued-tuples assoc e queue'))
      report')))

(defn- tuple-has-tempids? [db a vs]
  (and
    (sequential? vs)
    (let [schema-a (-> db -schema (get a))]
      (loop [idx 0]
        (cond
          (>= idx (count vs))
          false

          (and
            (tuple-ref-slot? db schema-a idx)
            (tempid? (nth vs idx)))
          true

          :else
          (recur (inc idx)))))))

(defn- resolve-upserts
  "Returns [entity' upserts]. Upsert attributes that resolve to existing entities
   are removed from entity, rest are kept in entity for insertion. No validation is performed.

   upserts :: {:name  {\"Ivan\"  1}
               :email {\"ivan@\" 2}
               :alias {\"abc\"   3
                       \"def\"   4}}}"
  [db entity]
  (if-some [idents (not-empty (-attrs-by db :db.unique/identity))]
    (let [resolve (fn [a v]
                    (cond
                      (and (tuple? db a) (tuple-has-tempids? db a v))
                      nil

                      (tuple? db a)
                      (:e (first (-datoms db :avet a (resolve-tuple-refs db a v entity) nil nil)))

                      (not (ref? db a))
                      (:e (first (-datoms db :avet a v nil nil)))
                      
                      (not (tempid? v))
                      (:e (first (-datoms db :avet a (entid db v) nil nil)))))
          split   (fn [a vs]
                    (reduce
                      (fn [acc v]
                        (if-some [e (resolve a v)]
                          (update acc 1 assoc v e)
                          (update acc 0 conj v)))
                      [[] {}] vs))]
      (reduce-kv
        (fn [[entity' upserts] a v]
          (validate-attr a entity)
          (validate-val v entity)
          (cond
            (not (contains? idents a))
            [(assoc entity' a v) upserts]

            (multi-value? db a v)
            (let [[insert upsert] (split a v)]
              [(cond-> entity'
                 (not (empty? insert)) (assoc a insert))
               (cond-> upserts
                 (not (empty? upsert)) (assoc a upsert))])

            :else
            (if-some [e (resolve a v)]
              [entity' (assoc upserts a {v e})]
              [(assoc entity' a v) upserts])))
        [{} {}]
        entity))
    [entity nil]))

(defn validate-upserts
  "Throws if not all upserts point to the same entity. 
   Returns single eid that all upserts point to, or null."
  [entity upserts]
  (let [upsert-ids (reduce-kv
                     (fn [m a v->e]
                       (reduce-kv
                         (fn [m v e]
                           (assoc m e [a v]))
                         m v->e))
                     {} upserts)]
    (if (<= 2 (count upsert-ids))
      (let [[e1 [a1 v1]] (first upsert-ids)
            [e2 [a2 v2]] (second upsert-ids)]
        (util/raise "Conflicting upserts: " [a1 v1] " resolves to " e1 ", but " [a2 v2] " resolves to " e2
          {:error     :transact/upsert
           :assertion [e1 a1 v1]
           :conflict  [e2 a2 v2]}))
      (let [[upsert-id [a v]] (first upsert-ids)
            eid (:db/id entity)]
        (when (and
                (some? upsert-id)
                (some? eid)
                (not (tempid? eid))
                (not= upsert-id eid))
          (util/raise "Conflicting upsert: " [a v] " resolves to " upsert-id ", but entity already has :db/id " eid
            {:error     :transact/upsert
             :assertion [upsert-id a v]
             :conflict  {:db/id eid}}))
        upsert-id))))

;; multivals/reverse can be specified as coll or as a single value, trying to guess
(defn- maybe-wrap-multival [db a vs]
  (cond
    ;; not a multival context
    (not (or (reverse-ref? a)
           (multival? db a)))
    [vs]

    ;; not a collection at all, so definitely a single value
    (not (or (arrays/array? vs)
           (and (coll? vs) (not (map? vs)))))
    [vs]
    
    ;; probably lookup ref
    (and (= (count vs) 2)
      (is-attr? db (first vs) :db.unique/identity))
    [vs]
    
    :else vs))

(defn- explode [db entity]
  (let [eid  (:db/id entity)
        ;; sort tuple attrs after non-tuple
        a+vs (apply concat
               (reduce
                 (fn [acc [a vs]]
                   (update acc (if (tuple? db a) 1 0) conj [a vs]))
                 [[] []] entity))]
    (for [[a vs] a+vs
          :when  (not= a :db/id)
          :let   [_          (validate-attr a {:db/id eid, a vs})
                  reverse?   (reverse-ref? a)
                  straight-a (if reverse? (reverse-ref a) a)
                  _          (when (and reverse? (not (ref? db straight-a)))
                               (util/raise "Bad attribute " a ": reverse attribute name requires {:db/valueType :db.type/ref} in schema"
                                 {:error :transact/syntax, :attribute a, :context {:db/id eid, a vs}}))]
          v      (maybe-wrap-multival db a vs)]
      (if (and (ref? db straight-a) (map? v)) ;; another entity specified as nested map
        (assoc v (reverse-ref a) eid)
        (if reverse?
          [:db/add v   straight-a eid]
          [:db/add eid straight-a v])))))

(defn- transact-add [report [_ e a v tx :as ent]]
  (validate-attr a ent)
  (validate-val  v ent)
  (let [tx        (or tx (current-tx report))
        db        (:db-after report)
        _         (validate-tuple db a v ent)
        e         (entid-strict db e)
        v         (if (ref? db a) (entid-strict db v) v)
        new-datom (datom e a v tx)
        multival? (multival? db a)
        old-datom ^Datom (if multival?
                           (fsearch db [e a v])
                           (fsearch db [e a]))]
    (cond
      (nil? old-datom)
      (transact-report report new-datom)

      (= (.-v old-datom) v)
      (update report ::tx-redundant util/conjv new-datom)

      :else
      (-> report
        (transact-report (datom e a (.-v old-datom) tx false))
        (transact-report new-datom)))))

(defn- transact-retract-datom [report ^Datom d]
  (let [tx (current-tx report)]
    (transact-report report (datom (.-e d) (.-a d) (.-v d) tx false))))

(defn- retract-components [db datoms]
  (into #{} (comp
              (filter (fn [^Datom d] (component? db (.-a d))))
              (map (fn [^Datom d] [:db.fn/retractEntity (.-v d)]))) datoms))

(declare transact-tx-data-impl)

(defn- retry-with-tempid [initial-report report es tempid upserted-eid]
  (if-some [eid (get (::upserted-tempids initial-report) tempid)]
    (util/raise "Conflicting upsert: " tempid " resolves"
      " both to " upserted-eid " and " eid
      {:error :transact/upsert})
    ;; try to re-run from the beginning
    ;; but remembering that `tempid` will resolve to `upserted-eid`
    (let [tempids' (-> (:tempids report)
                     (assoc tempid upserted-eid))
          report'  (-> initial-report
                     (assoc :tempids tempids')
                     (update ::upserted-tempids assoc tempid upserted-eid))]
      (util/log "retry" tempid "->" upserted-eid)
      (transact-tx-data-impl report' es))))

(def builtin-fn?
  #{:db.fn/call
    :db.fn/cas
    :db/cas
    :db/add
    :db/retract
    :db.fn/retractAttribute
    :db.fn/retractEntity
    :db/retractEntity})

(defn flush-tuples [report]
  (let [db (:db-after report)]
    (reduce-kv
      (fn [entities eid tuples+values]
        (reduce-kv
          (fn [entities tuple value]
            (let [value   (if (every? nil? value) nil value)
                  current (:v (first (-datoms db :eavt eid tuple nil nil)))]
              (cond
                (= value current) entities
                (nil? value)      (conj entities ^::internal [:db/retract eid tuple current])
                :else             (conj entities ^::internal [:db/add eid tuple value]))))
          entities
          tuples+values))
      []
      (::queued-tuples report))))

(defn check-value-tempids [report]
  ;; Persistent dissoc reduce, NOT (transient tempids): on SCI
  ;; seats (bb) the ::value-tempids map is a SciMap, which does
  ;; not implement IEditableCollection — (transient sci-map)
  ;; throws ClassCastException whenever d/with runs outside a
  ;; host-built report (shootout F1, bank 26). The tempids map
  ;; is tx-sized; the transient bought nothing measurable.
  ;; One disclosed delta: the raise's ex-data :tempids is now
  ;; the persistent map itself (it was the live transient).
  (if-let [tempids (::value-tempids report)]
    (let [reduce-fn (fn [tempids datom]
                      (if (datom-added datom)
                        (dissoc tempids (:e datom))
                        tempids))
          unused    (reduce reduce-fn tempids (:tx-data report))
          unused    (reduce reduce-fn unused (::tx-redundant report))]
      (if (zero? (count unused))
        (dissoc report ::value-tempids ::tx-redundant)
        (util/raise "Tempids used only as value in transaction: " (sort (vals unused))
          {:error :transact/syntax, :tempids unused})))
    (dissoc report ::value-tempids ::tx-redundant)))

(defn+ transact-tx-data-impl [initial-report initial-es]
  (let [initial-report' (-> initial-report
                          (update :db-after transient))
        has-tuples?     (not (empty? (-attrs-by (:db-after initial-report) :db/attrTuples)))
        initial-es'     (if has-tuples?
                          (interleave initial-es (repeat ::flush-tuples))
                          initial-es)]
    (loop [report initial-report'
           es     initial-es']
      (util/log "transact" es)
      (util/cond+
        (empty? es)
        (-> report
          (check-value-tempids)
          (dissoc ::upserted-tempids)
          (dissoc ::reverse-tempids)
          (update :tempids #(util/removem auto-tempid? %))
          (update :tempids assoc :db/current-tx (current-tx report))
          (update :db-after update :max-tx inc)
          (update :db-after persistent!))

        :let [[entity & entities] es]

        (nil? entity)
        (recur report entities)

        (= ::flush-tuples entity)
        (if (contains? report ::queued-tuples)
          (recur
            (dissoc report ::queued-tuples)
            (concat (flush-tuples report) entities))
          (recur report entities))

        :let [db      (:db-after report)
              tempids (:tempids report)]

        (map? entity)
        (let [old-eid (:db/id entity)]
          (util/cond+
            ;; trivial entity
            ; (if (contains? entity :db/id)
            ;   (= 1 (count entity))
            ;   (= 0 (count entity)))
            ; (recur report entities)

            ;; :db/current-tx / "datomic.tx" => tx
            (tx-id? old-eid)
            (let [id (current-tx report)]
              (recur (allocate-eid report old-eid id)
                (cons (assoc entity :db/id id) entities)))
           
            ;; lookup-ref => resolved | error
            (sequential? old-eid)
            (let [id (entid-strict db old-eid)]
              (recur report
                (cons (assoc entity :db/id id) entities)))
           
            ;; upserted => explode | error
            :let [[entity' upserts] (resolve-upserts db entity)
                  upserted-eid      (validate-upserts entity' upserts)]

            (some? upserted-eid)
            (if (and
                  (tempid? old-eid)
                  (contains? tempids old-eid)
                  (not= upserted-eid (get tempids old-eid)))
              (retry-with-tempid initial-report report initial-es old-eid upserted-eid)
              (recur
                (-> report
                  (allocate-eid old-eid upserted-eid)
                  (update ::tx-redundant util/conjv (datom upserted-eid nil nil tx0)))
                (concat (explode db (assoc entity' :db/id upserted-eid)) entities)))
           
            ;; resolved | allocated-tempid | tempid | nil => explode
            (or
              (number? old-eid)
              (nil?    old-eid)
              (string? old-eid)
              (auto-tempid? old-eid))
            (recur report (concat (explode db entity) entities))
           
            ;; trash => error
            :else
            (util/raise "Expected number, string or lookup ref for :db/id, got " old-eid
              {:error :entity-id/syntax, :entity entity})))

        (sequential? entity)
        (let [[op e a v] entity]
          (util/cond+
            (= op :db.fn/call)
            (let [[_ f & args] entity]
              (recur report (concat (assoc-auto-tempids db (apply f db args)) entities)))
            
            (and (keyword? op)
              (not (builtin-fn? op)))
            (if-some [ident (entid db op)]
              (let [stored (:v (fsearch db [ident :db/fn]))
                    args (next entity)]
                (if (some? stored)
                  ;; No functions in Eve memory (John, 2026-11-11):
                  ;; transaction functions are transact-time input,
                  ;; never a stored DB value. A :db/fn found in storage
                  ;; is not executed from storage — it fails clearly.
                  (util/raise "Entity " op " has a stored :db/fn value. Functions can't be stored in Eve memory; pass the transaction function in the transaction instead ([:db.fn/call f & args])"
                    {:error :transact/stored-fn, :operation :db.fn/call, :tx-data entity})
                  (util/raise "Entity " op " expected to have :db/fn attribute with fn? value"
                    {:error :transact/syntax, :operation :db.fn/call, :tx-data entity})))
              (util/raise "Can’t find entity for transaction fn " op
                {:error :transact/syntax, :operation :db.fn/call, :tx-data entity}))
            
            (and (tempid? e)
              (not= op :db/add))
            (util/raise "Can't use tempid in '" entity "'. Tempids are allowed in :db/add only"
              {:error :transact/syntax, :op entity})

            (or (= op :db.fn/cas)
              (= op :db/cas))
            (let [[_ e a ov nv] entity
                  e      (entid-strict db e)
                  _      (validate-attr a entity)
                  ov     (cond
                           (ref? db a)   (entid-strict db ov)
                           (tuple? db a) (resolve-tuple-refs db a ov entity)
                           :else         ov)
                  nv     (cond
                           (ref? db a)   (entid-strict db nv)
                           (tuple? db a) (resolve-tuple-refs db a nv entity)
                           :else         nv)
                  _      (validate-val nv entity)
                  datoms (vec (-search db [e a]))]
              (if (multival? db a)
                (if (some (fn [^Datom d] (= (.-v d) ov)) datoms)
                  (recur (transact-add report [:db/add e a nv]) entities)
                  (util/raise ":db.fn/cas failed on datom [" e " " a " " (map :v datoms) "], expected " ov
                    {:error :transact/cas, :old datoms, :expected ov, :new nv}))
                (let [v (:v (first datoms))]
                  (if (= v ov)
                    (recur (transact-add report [:db/add e a nv]) entities)
                    (util/raise ":db.fn/cas failed on datom [" e " " a " " v "], expected " ov
                      {:error :transact/cas, :old (first datoms), :expected ov, :new nv})))))

            (tx-id? e)
            (recur (allocate-eid report e (current-tx report)) (cons [op (current-tx report) a v] entities))

            (and (ref? db a) (tx-id? v))
            (recur (allocate-eid report v (current-tx report)) (cons [op e a (current-tx report)] entities))

            (and (ref? db a) (tempid? v))
            (if-some [resolved (get tempids v)]
              (let [report' (update report ::value-tempids assoc resolved v)]
                (recur report' (cons [op e a resolved] entities)))
              (let [resolved (next-eid db)
                    report'  (-> report
                               (allocate-eid v resolved)
                               (update ::value-tempids assoc resolved v))]
                (recur report' es)))
            
            (and
              (or (= op :db/add) (= op :db/retract))
              (not (::internal (meta entity)))
              (tuple? db a)
              :let [[report' v'] (resolve-tuple-value report a v entity)]
              (not= v v'))
            (recur report' (cons [op e a v'] entities))

            (tempid? e)
            (let [upserted-eid  (when (is-attr? db a :db.unique/identity)
                                  (:e (first (-datoms db :avet a v nil nil))))
                  allocated-eid (get tempids e)]
              (if (and upserted-eid allocated-eid (not= upserted-eid allocated-eid))
                (retry-with-tempid initial-report report initial-es e upserted-eid)
                (let [eid (or upserted-eid allocated-eid (next-eid db))]
                  (recur (allocate-eid report e eid) (cons [op eid a v] entities)))))

            (and
              (is-attr? db a :db.unique/identity)
              (contains? (::reverse-tempids report) e)
              :let [upserted-eid (:e (first (-datoms db :avet a v nil nil)))]
              e
              upserted-eid
              (not= e upserted-eid))
            (let [tempids      (get (::reverse-tempids report) e)
                  tempid       (util/find #(not (contains? (::upserted-tempids report) %)) tempids)]
              (if tempid
                (retry-with-tempid initial-report report initial-es tempid upserted-eid)
                (util/raise "Conflicting upsert: " e " resolves to " upserted-eid " via " entity
                  {:error :transact/upsert})))
            
            (and
              (not (::internal (meta entity)))
              (composite-tuple? db a))
            ;; allow transacting in tuples if they fully match already existing values
            (let [tuple-attrs (get-in db [:schema a :db/tupleAttrs])]
              (if (and
                    (= (count tuple-attrs) (count v))
                    (every? some? v)
                    (every? 
                      (fn [[tuple-attr tuple-value]]
                        (let [db-value (:v (first (-datoms db :eavt e tuple-attr nil nil)))]
                          (= tuple-value db-value)))
                      (map vector tuple-attrs v)))
                (recur report entities)
                (util/raise "Can’t modify tuple attrs directly: " entity
                  {:error :transact/syntax, :tx-data entity})))

            (= op :db/add)
            (recur (transact-add report entity) entities)

            (and (= op :db/retract) (some? v))
            (if-some [e (entid db e)]
              (let [v (if (ref? db a) (entid-strict db v) v)]
                (validate-attr a entity)
                (validate-val v entity)
                (validate-tuple db a v entity)
                (if-some [old-datom (fsearch db [e a v])]
                  (recur (transact-retract-datom report old-datom) entities)
                  (recur report entities)))
              (recur report entities))

            (or (= op :db.fn/retractAttribute)
              (= op :db/retract))
            (if-some [e (entid db e)]
              (let [_      (validate-attr a entity)
                    datoms (vec (-search db [e a]))]
                (recur (reduce transact-retract-datom report datoms)
                  (concat (retract-components db datoms) entities)))
              (recur report entities))

            (or (= op :db.fn/retractEntity)
              (= op :db/retractEntity))
            (if-some [e (entid db e)]
              (let [e-datoms (vec (-search db [e]))
                    v-datoms (vec (mapcat (fn [a] (-search db [nil a e])) (-attrs-by db :db.type/ref)))]
                (recur (reduce transact-retract-datom report (concat e-datoms v-datoms))
                  (concat (retract-components db e-datoms) entities)))
              (recur report entities))

            :else
            (util/raise "Unknown operation at " entity ", expected :db/add, :db/retract, :db.fn/call, :db.fn/retractAttribute, :db.fn/retractEntity or an ident corresponding to an installed transaction function (e.g. {:db/ident <keyword> :db/fn <Ifn>}, usage of :db/ident requires {:db/unique :db.unique/identity} in schema)" {:error :transact/syntax, :operation op, :tx-data entity})))
       
        (datom? entity)
        (let [[e a v tx added] entity]
          (if added
            (recur (transact-add report [:db/add e a v tx]) entities)
            (recur report (cons [:db/retract e a v] entities))))

        :else
        (util/raise "Bad entity type at " entity ", expected map or vector"
          {:error :transact/syntax, :tx-data entity})))))

(defn transact-tx-data [report es]
  (when-not (or
              (nil? es)
              (sequential? es))
    (util/raise "Bad transaction data " es ", expected sequential collection"
      {:error :transact/syntax, :tx-data es}))
  (let [es' (assoc-auto-tempids (:db-before report) es)]
    (transact-tx-data-impl report es')))