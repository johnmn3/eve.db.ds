(ns eve.db.ds.u5-support
    "Support for U5's slices (serialize / persistence).

   - transit helpers: ported from upstream test.core (the gated
     upstream tree cannot be required as-is — same reason as
     U3's support ns).
   - content/digest helpers: a DB handle's content as plain host
     data, computed two independent ways — via eve.db.ds.conn's
     helpers (conn/schema-of + conn/datoms-of) and via the public
     core API (d/schema + d/datoms) — so round-trip proofs do not
     grade their own homework.
   - a deterministic synthetic data generator (the cliff
     generator is not in this repo; see U5-STATUS.md)."
    (:require
     [eve.db.ds.conn :as conn]
     [eve.db.ds.core :as d]
     [eve.db.ds.db :as db]
     #?@(:clj  [[cognitect.transit :as transit]]
         :cljs [[cognitect.transit :as transit]]))
    #?(:clj
       (:import
        [java.io ByteArrayInputStream ByteArrayOutputStream])))

;; ---------------------------------------------------------------------------
;; Transit helpers (upstream test.core, ported)

(defn transit-write [o type]
      #?(:clj
         (let [os (ByteArrayOutputStream.)]
              (transit/write (transit/writer os type) o)
              (.toByteArray os))
         :cljs
         (transit/write (transit/writer type) o)))

(defn transit-read [s type]
      #?(:clj
         (transit/read (transit/reader (ByteArrayInputStream. ^bytes s) type))
         :cljs
         (transit/read (transit/reader type) s)))

(defn transit-write-str [o]
      #?(:clj  (String. ^bytes (transit-write o :json) "UTF-8")
         :cljs (transit-write o :json)))

(defn transit-read-str [s]
      #?(:clj  (transit-read (.getBytes ^String s "UTF-8") :json)
         :cljs (transit-read s :json)))

;; ---------------------------------------------------------------------------
;; Content / digest — two independent read paths

(defn- datom-vec [dt]
       [(.-e dt) (.-a dt) (.-v dt) (db/datom-tx dt)])

(defn content-via-conn
      "DB content via conn's own helpers (resolve + $ reads)."
      [handle]
      {:schema (conn/schema-of handle)
       :datoms (mapv datom-vec (conn/datoms-of handle))})

(defn content-via-core
      "DB content via the public core API (d/schema + d/datoms)."
      [handle]
      {:schema (d/schema handle)
       :datoms (mapv datom-vec (d/datoms handle :eavt))})

(defn digest
      "The acceptance digest: datom count + full content, via core."
      [handle]
      (let [c (content-via-core handle)]
           {:count  (count (:datoms c))
            :schema (:schema c)
            :datoms (:datoms c)}))

;; ---------------------------------------------------------------------------
;; Deterministic synthetic data

(def gen-schema
     {:page/name    {:db/unique :db.unique/identity}
      :page/aka     {:db/cardinality :db.cardinality/many}
      :page/age     {:db/index true}
      :page/follows {:db/valueType :db.type/ref}
      :page/attach  {}})

(defn gen-rows
      "n synthetic pages as [:db/add ...] tx rows: strings, longs,
   keywords and map values, refs to later entities, a cardinality-
   many attr. Fully deterministic."
      [n]
      (into []
            (mapcat
             (fn [i]
                 (cond-> [[:db/add i :page/name (str "page-" i)]
                          [:db/add i :page/age (mod (* i 7) 90)]
                          [:db/add i :page/attach {:idx i :tags [:t (keyword (str "k" (mod i 5)))]}]]
                         (< i n)            (conj [:db/add i :page/follows (inc i)])
                         (zero? (mod i 3))  (conj [:db/add i :page/aka (str "aka-" i)]))))
            (range 1 (inc n))))
