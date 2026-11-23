(ns eve.db.ds.test.serialize
    "U5's port of the upstream serialize slice (test/upstream-ds
   test/eve/db/ds/test/serialize.cljc), adapted to the handle
   model and the new eve.db.ds.serialize (plan v2 §3):

   - Upstream compares DB values with =. Handles from different
     atoms are never =, so every round-trip leg compares CONTENT:
     the source's content via conn's helpers against the restored
     handle's content via the public core API (u5-support) — two
     independent read paths.
   - Ported deftests: serialize, test-nan. NOT ported (named in
     U5-STATUS.md): test-pr-read, test-init-db,
     test-max-eid-from-refs — DB/Datom print-read and init-db
     equivalence are U2/U3 surfaces with their own slices.
   - test-synthetic-digest-roundtrip is U5's own: the cliff
     generator is not in this repo, so the digest anchor is a
     deterministic synthetic DB (u5-support/gen-rows) whose
     content is computed two independent ways."
    (:require
     [clojure.edn :as edn]
     [clojure.test :as t :refer [is deftest testing]]
     [eve.db.ds.core :as d]
     [eve.db.ds.u5-support :as u5s]
     #?(:clj [cheshire.core :as cheshire])
     #?(:clj [jsonista.core :as jsonista])))

(def data
     [[1 :name    "Petr"]
      [1 :aka     "Devil"]
      [1 :aka     "Tupen"]
      [1 :age     15]
      [1 :follows 2]
      [1 :email   "petr@gmail.com"]
      [1 :avatar  10]
      [10 :url    "http://"]
      [1 :attach  {:some-key :some-value}]
      [2 :name    "Oleg"]
      [2 :age     30]
      [2 :email   "oleg@gmail.com"]
      [2 :attach  [:just :values]]
      [3 :name    "Ivan"]
      [3 :age     15]
      [3 :follows 2]
      [3 :attach  {:another :map}]
      [3 :avatar  30]
      [4 :name    "Nick" d/tx0]
      [5 :inf     ##Inf]
      [5 :-inf    ##-Inf]
      #?@(:clj [[5 :ratio      22/7]
                [5 :bigint     (bigint 100)]
                [5 :biginteger (biginteger 100)]
                [5 :bigdec     (bigdec 100.005)]])
   ;; check that facts about transactions doesn’t set off max-eid
      [d/tx0      :txInstant 0xdeadbeef]
      [30 :url    "https://"]])

(def schema
     {:name    {} ;; nothing special about name
      :aka     {:db/cardinality :db.cardinality/many}
      :age     {:db/index true}
      :follows {:db/valueType :db.type/ref}
      :email   {:db/unique :db.unique/identity}
      :avatar  {:db/valueType :db.type/ref, :db/isComponent true}
      :url     {}   ;; just a component prop
      :attach  {}}) ;; should skip index

#?(:cljs
   (defn- js-form->clj
          "JSON.parse output -> plain cljs data, for the JSON legs below.
      Two Eve-browser-runtime interop facts shape these legs (U6
      probes, 2026-11-20; evidence in U6-STATUS.md):
      - js/JSON.stringify on cljs data serializes the collection
        object's internal fields (meta/cnt/arr), not the data — the
        form goes through clj->js first.
      - cljs.core/js->clj cannot be used on the parse output:
        js->clj checks (coll? x) before (array? x), Eve's coll
        layer makes (coll? js-array) true, and the branch calls
        (empty x) — JS arrays have no IEmptyableCollection there.
      This converter walks arrays/objects directly."
          [x]
          (cond
           (array? x) (mapv js-form->clj x)
           (identical? (type x) js/Object)
           (into {}
                 (map (fn [k] [(keyword k) (js-form->clj (aget x k))]))
                 (js/Object.keys x))
           :else x)))

(defn- roundtrips-to?
       "True if restored (a handle) has the same content as source
   (a handle), each read through a different path."
       [source restored]
       (= (u5s/content-via-conn source)
          (u5s/content-via-core restored)))

(deftest serialize
         (let [db (d/db-with
                   (d/empty-db schema)
                   (map (fn [[e a v]] [:db/add e a v]) data))]
              (is (roundtrips-to? db (-> db d/serializable d/from-serializable)))
              (is (roundtrips-to? db (-> db d/serializable pr-str edn/read-string d/from-serializable)))
              (is (roundtrips-to? db (-> db (d/serializable {:freeze-fn u5s/transit-write-str}) pr-str edn/read-string (d/from-serializable {:thaw-fn u5s/transit-read-str}))))
              (doseq [type [:json :json-verbose #?(:clj :msgpack)]]
                     (testing type
                              (is (roundtrips-to? db (-> db d/serializable (u5s/transit-write type) (u5s/transit-read type) d/from-serializable)))))
              #?(:clj
                 (is (roundtrips-to? db (-> db d/serializable jsonista/write-value-as-string jsonista/read-value d/from-serializable))))
              #?(:clj
                 (let [mapper (com.fasterxml.jackson.databind.ObjectMapper.)]
                      (is (roundtrips-to? db (-> db d/serializable (jsonista/write-value-as-string mapper) (jsonista/read-value mapper) d/from-serializable)))))
              #?(:clj
                 (is (roundtrips-to? db (-> db d/serializable cheshire/generate-string cheshire/parse-string d/from-serializable))))
              #?(:cljs
                 (is (roundtrips-to? db (-> db d/serializable clj->js js/JSON.stringify js/JSON.parse (js-form->clj) d/from-serializable))))))

(deftest test-nan
         (let [db (d/db-with
                   (d/empty-db schema)
                   [[:db/add 1 :nan ##NaN]])
               valid? #(#?(:clj Double/isNaN :cljs js/isNaN) (:nan (d/entity % 1)))]
              (is (valid? (-> db d/serializable d/from-serializable)))
              (is (valid? (-> db d/serializable pr-str edn/read-string d/from-serializable)))
              (is (valid? (-> db (d/serializable {:freeze-fn u5s/transit-write-str}) pr-str edn/read-string (d/from-serializable {:thaw-fn u5s/transit-read-str}))))
              (doseq [type [:json :json-verbose #?(:clj :msgpack)]]
                     (testing type
                              (is (valid? (-> db d/serializable (u5s/transit-write type) (u5s/transit-read type) d/from-serializable)))))
              #?(:clj
                 (is (valid? (-> db d/serializable jsonista/write-value-as-string jsonista/read-value d/from-serializable))))
              #?(:clj
                 (let [mapper (com.fasterxml.jackson.databind.ObjectMapper.)]
                      (is (valid? (-> db d/serializable (jsonista/write-value-as-string mapper) (jsonista/read-value mapper) d/from-serializable)))))
              #?(:clj
                 (is (valid? (-> db d/serializable cheshire/generate-string cheshire/parse-string d/from-serializable))))
              #?(:cljs
                 (is (valid? (-> db d/serializable clj->js js/JSON.stringify js/JSON.parse (js-form->clj) d/from-serializable))))))

(deftest test-synthetic-digest-roundtrip
         (let [conn (d/create-conn u5s/gen-schema)]
              (d/transact! conn (u5s/gen-rows 40))
              (let [source   (d/db conn)
                    restored (d/from-serializable (d/serializable source))]
                   (is (= 40 (count (filter #(= :page/name (second %))
                                            (:datoms (u5s/content-via-core restored))))))
                   (is (roundtrips-to? source restored))
      ;; and through a string, the persistence-shaped path
                   (is (roundtrips-to? source
                                       (-> source d/serializable pr-str edn/read-string d/from-serializable))))))
