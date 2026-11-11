(ns contract.serialize-portable
  (:require [eve.db.ds.core :as d]
            [eve.db.ds.serialize :as ser]))

(def wire-keys
  ["count" "tx0" "max-eid" "max-tx" "schema" "attrs" "keywords"
   "eavt" "aevt" "avet" "branching-factor" "ref-type"])

(def expected-wire
  [8 536870912 2 536870913 "nil" [":active" ":age" ":name" ":tag"] [":person"] [[1 0 true 1] [1 1 17 1] [1 2 "Ivan" 1] [1 3 [0 0] 1] [2 0 false 1] [2 1 23 1] [2 2 "Petr" 1] [2 3 [0 0] 1]] [0 4 1 5 2 6 3 7] [] 32 "strong"])

(defn check [ok message data]
  (when-not ok (throw (ex-info message data))))

(defn datoms [db]
  (mapv (fn [x] [(:e x) (:a x) (:v x) (:tx x)]) (d/datoms db :eavt)))

(defn exact-numbers []
  (doseq [token ["9007199254740993N" "1/3" "1.2300M"]]
    (let [result (try {:value (#'ser/read-edn token)}
                  (catch #?(:cljs :default :lpy python/Exception :default Exception) e
                    {:error (ex-data e)}))]
      (if-let [error (:error result)]
        (do (check (= :serialize/exact-number (:error error))
              "unexpected numeric decoder error" error)
            (println "EXACT-NUMBER-UNSUPPORTED" token))
        (do (check (= token (pr-str (:value result)))
              "numeric decoder lost precision or type" result)
            (check (#'ser/exact-number? (:value result))
              "exact number would not use marker-other" result)
            (let [db (d/db-with (d/empty-db) [{:db/id 1 :n (:value result)}])
                  frozen (d/serializable db)
                  value (nth (first (get frozen "eavt")) 2)
                  restored (d/from-serializable frozen)]
              (check (= [1 token] value) "exact number wire marker mismatch" {:value value})
              (check (= (datoms db) (datoms restored)) "exact number DB round trip changed value" {})
              (check (= frozen (d/serializable restored)) "exact number wire round trip changed" {}))
            (println "EXACT-NUMBER-OK" token))))))

(defn -main [& _]
  (let [db (d/db-with (d/empty-db)
             [{:db/id 1 :age 17 :name "Ivan" :tag :person :active true}
              {:db/id 2 :age 23 :name "Petr" :tag :person :active false}])
        frozen (d/serializable db)
        wire (mapv #(get frozen %) wire-keys)
        restored (d/from-serializable frozen)]
    (check (= expected-wire wire) "cross-host wire fixture mismatch" {:wire wire})
    (check (= (pr-str expected-wire) (pr-str wire)) "wire bytes differ" {})
    (check (map? frozen) "wire must be a Clojure map" {})
    (check (every? vector? (map #(get frozen %) ["attrs" "keywords" "eavt" "aevt" "avet"]))
      "wire arrays must all be vectors" {})
    (check (= (datoms db) (datoms restored)) "round trip changed datoms" {})
    (check (= frozen (d/serializable restored)) "round trip changed wire structure" {})
    (check (= #{"branching-factor" "ref-type"} (set (filter #(contains? frozen %) ["branching-factor" "ref-type"])))
      "missing settings" {})
    ;; A canonical vector order makes the output byte comparable by the caller;
    ;; no host map iteration ordering or JSON object convention is involved.
    (println "SERIALIZE-WIRE" (pr-str wire))
    (exact-numbers)
    (println "SERIALIZE-PORTABLE-OK")))
