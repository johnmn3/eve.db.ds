(ns eve.db.ds.serialize
  "DB -> plain data and back, over datoms (DS-IN-EVE-PLAN v2 §3).

   A serialized DB is data — no Eve internals, no slab formats:

     {:schema  (freeze-fn schema)      ; a string
      :max-eid <long>
      :max-tx  <long>
      :datoms  [[e a v' tx] ...]}      ; :eavt order

   `serializable` takes a DB handle (plan v2 §4): the schema and
   datoms are read from the DB inside its atom (datoms via
   eve.db.ds.conn/datoms-of, which reads inside a $ region) and
   come out as host data. `from-serializable` thaws the schema,
   creates a fresh private conn with it, transacts the datoms in
   as host Datoms — each datom keeps its own tx, which
   db/transact-tx-data honors — and returns the new conn's DB
   handle.

   Value encoding (upstream's marker scheme; the keyword payload
   is inlined instead of held in an intern table): strings,
   booleans and finite numbers travel as-is; a keyword travels as
   [0 \":kw\"]; ##Inf as [2], ##-Inf as [3], ##NaN as [4];
   everything else (maps, vectors, sets, ratios, bigints,
   bigdecimals, nil, ...) travels as [1 (freeze-fn v)] and is
   restored with thaw-fn. Real data vectors are always frozen
   under marker 1, so the markers are unambiguous. Attribute
   keywords travel as their str (\":name\") and are re-keywordized
   on thaw. The form therefore survives EDN, transit and JSON
   round-trips; from-serializable accepts keyword or string
   top-level keys (a JSON round-trip stringifies them).

   freeze-fn / thaw-fn mirror upstream's opts: freeze-fn defaults
   to pr-str, thaw-fn to clojure.edn/read-string.

   :max-eid / :max-tx are carried for parity with upstream's form.
   The rebuild derives its own by transaction: datom content and
   max-tx match the source exactly (measured, U5 probes/slices);
   a source max-eid above every live datom eid (fully retracted
   entities) is not preserved — the transaction that would install
   it does not exist in the public API."
  #?(:cljs (:require-macros [eve.db.ds.util :as util]))
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [eve.alpha :as eve :refer [$]]
    [eve.db.ds.conn :as conn]
    [eve.db.ds.db :as db]
    [eve.db.ds.util :as util]))

(def ^:const ^:private marker-kw 0)
(def ^:const ^:private marker-other 1)
(def ^:const ^:private marker-inf 2)
(def ^:const ^:private marker-minus-inf 3)
(def ^:const ^:private marker-nan 4)

(defn- write-v [freeze-fn v]
  (cond
    (string? v)  v
    (boolean? v) v
    (keyword? v) [marker-kw (str v)]
    (number? v)
    (cond
      #?@(:clj [(or (instance? java.math.BigInteger v)
                  (instance? java.math.BigDecimal v)
                  (instance? clojure.lang.Ratio v)
                  (instance? clojure.lang.BigInt v))
                [marker-other (freeze-fn v)]])
      (== ##Inf v)  [marker-inf]
      (== ##-Inf v) [marker-minus-inf]
      #?(:clj  (and (instance? Double v) (Double/isNaN ^double v))
         :cljs (js/isNaN v)) [marker-nan]
      :else v)
    ;; freeze-fn's contract is plain host data on every host: a
    ;; delivered flat collection is converted first (transit-cljs's
    ;; writer is protocol-strict and refuses flat sisters with
    ;; "Cannot write" where transit-clj's generic Map writer does
    ;; not — U6 browser finding, 2026-11-20).
    :else [marker-other (freeze-fn (eve/eve->clj v))]))

(defn- thaw-kw [s]
  (if (and (string? s) (str/starts-with? s ":"))
    (keyword (subs s 1))
    s))

(defn- read-v [thaw-fn v]
  (cond
    (string? v)  v
    (boolean? v) v
    (number? v)  v
    (nil? v)     nil
    ;; a marker vector — coerced with vec first: a bare-mapper
    ;; Jackson read returns java.util.ArrayList rows, a cljs
    ;; JSON.parse returns JS arrays; neither is `sequential?`
    :else
    (let [[marker payload] (vec v)]
      (condp == marker
        marker-kw        (thaw-kw payload)
        marker-other     (thaw-fn payload)
        marker-inf       ##Inf
        marker-minus-inf ##-Inf
        marker-nan       ##NaN
        (util/raise "Unexpected value marker " marker " in " (pr-str v)
          {:error :serialize :value v})))))

(defn- field
  "Top-level form access tolerant of JSON round-trips: keys may
   come back as keywords, as \"name\" strings (cheshire/jsonista
   defaults), or as \":name\" strings (a bare Jackson
   ObjectMapper writes a keyword key by its toString)."
  [form k]
  (let [v (get form k)]
    (if (some? v)
      v
      (let [v (get form (name k))]
        (if (some? v)
          v
          (get form (str k)))))))

(defn serializable
  "Captures the DB a handle names as plain host data (see the ns
   docstring for the form). The handle must resolve — serializing
   an unkept, non-current DB throws :eve.db.ds/db-not-kept, like
   any other read."
  ([db-handle]
   (serializable db-handle {}))
  ([db-handle {:keys [freeze-fn] :or {freeze-fn pr-str}}]
   (conn/ensure-handle db-handle)
   (let [dbv     (conn/resolve-db db-handle)
         max-eid ($ (:max-eid dbv))
         max-tx  ($ (:max-tx dbv))]
     {:schema  (freeze-fn (eve/eve->clj (conn/schema-of db-handle)))
      :max-eid max-eid
      :max-tx  max-tx
      :datoms  (mapv (fn [d]
                       [(:e d) (str (:a d))
                        (write-v freeze-fn (:v d))
                        (db/datom-tx d)])
                 (conn/datoms-of db-handle))})))

(defn from-serializable
  "Rebuilds a DB from serializable's form: thaws the schema,
   creates a fresh private conn with it, transacts the datoms in
   (one transact!, datoms keep their txs) and returns the new
   conn's DB handle.

   The transact's tx-data is [:db/add e a v tx] vectors carrying
   the thawed HOST values, not host Datoms: at this Eve pin,
   transacting a Datom entity whose v is an already-flat
   collection (a map/vector value read back from a DB) corrupts
   the stored v — it reads back as the datom itself (GAP-U5-2,
   U5-STATUS.md; scalar values are unaffected). The vector form
   with host values is the measured-good path."
  ([form]
   (from-serializable form {}))
  ([form {:keys [thaw-fn] :or {thaw-fn edn/read-string}}]
   (let [schema (thaw-fn (field form :schema))
         tx-data (mapv (fn [row]
                         (let [[e a v tx] row]
                           [:db/add e (thaw-kw a) (read-v thaw-fn v) tx]))
                   (field form :datoms))
         conn   (conn/create-conn schema)]
     (when (seq tx-data)
       (conn/transact! conn tx-data))
     (conn/db conn))))
