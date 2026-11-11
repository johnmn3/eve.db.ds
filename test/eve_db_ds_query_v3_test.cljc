(ns eve-db-ds-query-v3-test
  (:require [clojure.test :refer [deftest is testing]]
            [eve.core :as e]
            [eve.map :as em]
            [eve.vec :as ev]
            [eve.set :as es]
            [eve.db.ds.core :as d]
            [eve.db.ds.query-v3 :as q]))

(defn ensure! [ok]
  (when-not ok (throw (ex-info "query_v3 regression failed" {})))
  true)

(defn result-set [result] (e/into #{} (map #(e/into [] %)) result))

(defn collection-checks! []
 ;; These checks also run directly on hosts without clojure.test/run-tests.
  (ensure! (em/eve-hash-map? (q/fast-map)))
  (ensure! (ev/eve-vector? (q/fast-arr)))
  (ensure! (es/eve-set? (q/fast-set)))
  (ensure! (= 2 (e/get (e/persistent! (e/assoc! (e/transient (q/fast-map)) :x 2)) :x)))
  (ensure! (= [1 2] (e/into [] (e/persistent! (e/conj! (e/conj! (e/transient (q/fast-arr)) 1) 2)))))
  (ensure! (= #{1} (e/into #{} (e/into (q/fast-set) [1 1])))))

(defn query-checks! []
  (let [db (d/db-with (d/empty-db)
             [{:db/id 1 :name "Ivan" :age 30}
              {:db/id 2 :name "Petr" :age 20}
              {:db/id 3 :name "Ivan" :age 40}])]
    (testing "projection copies persistent tuples (previously #{[nil]})"
      (ensure! (= #{["Ivan"] ["Petr"]}
            (result-set (q/q '[:find ?n :where [?e :name ?n]] db)))))
    (testing "hash join preserves groups and concatenates tuple values"
      (ensure! (= #{["Ivan" 30] ["Petr" 20] ["Ivan" 40]}
            (result-set (q/q '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]] db)))))
    (testing "constants, scalar input bindings and predicate arguments"
      (ensure! (= #{["Ivan"]}
            (result-set (q/q '[:find ?n :in $ ?e :where [?e :name ?n]] db 1))))
      (ensure! (= #{["Ivan" 40]}
            (result-set (q/q '[:find ?n ?a :where [?e :name ?n] [?e :age ?a] [(> ?a 30)]] db))))))
  (testing "collection source, product and collection input bindings"
    (ensure! (= #{[1 "A"] [2 "B"]}
          (result-set (q/q '[:find ?e ?n :in $ :where [?e :name ?n]]
                        [[1 :name "A"] [2 :name "B"]]))))
    (ensure! (= #{[1 "x"] [2 "x"]}
          (result-set (q/q '[:find ?x ?y :in [?x ...] ?y :where] [1 2] "x"))))))

(defn run-regressions! []
  (collection-checks!)
  (query-checks!)
  (println "QUERY-V3-REGRESSION-OK 12 checks")
  true)

(deftest eve-work-collections (is (collection-checks!)))
(deftest persistent-tuples-retain-query-values (is (query-checks!)))
