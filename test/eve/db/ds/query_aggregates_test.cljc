(ns eve.db.ds.query-aggregates-test
  "U4 slice: port of upstream eve.db.ds.test.query-aggregates (DataScript 1.8.1),
   adapted like entity-test: d/q -> eve.db.ds.query/q, DB construction
   via u4-support, d/entity -> eve.db.ds.impl.entity/entity,
   d/filter -> u4-support/filter-db."
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    [eve.db.ds.query :as dq]
    [eve.db.ds.db :as db]
    [eve.db.ds.impl.entity :as de]
    [eve.db.ds.u4-support :as sup])
  #?(:clj
     (:import
       [clojure.lang ExceptionInfo])))

(defn sort-reverse [xs]
  (reverse (sort xs)))

(deftest test-aggregates
  (let [monsters [["Cerberus" 3]
                  ["Medusa" 1]
                  ["Cyclops" 1]
                  ["Chimera" 1]]]
    (testing "with"
      (is (= (dq/q '[:find ?heads
                    :with ?monster
                    :in   [[?monster ?heads]]]
               [["Medusa" 1]
                ["Cyclops" 1]
                ["Chimera" 1]])
            [[1] [1] [1]])))

    (testing "Wrong grouping without :with"
      (is (= (dq/q '[:find (sum ?heads)
                    :in   [[?monster ?heads]]]
               monsters)
            [[4]])))

    (testing "Multiple aggregates, correct grouping with :with"
      (is (= (dq/q '[:find (sum ?heads) (min ?heads) (max ?heads) (count ?heads) (count-distinct ?heads)
                    :with ?monster
                    :in   [[?monster ?heads]]]
               monsters)
            [[6 1 3 4 2]])))
    
    (testing "Min and max are using comparator instead of default compare"
      ;; Wrong: using js '<' operator
      ;; (apply min [:a/b :a-/b :a/c]) => :a-/b
      ;; (apply max [:a/b :a-/b :a/c]) => :a/c
      ;; Correct: use IComparable interface
      ;; (sort compare [:a/b :a-/b :a/c]) => (:a/b :a/c :a-/b)
      (is (= (dq/q '[:find (min ?x) (max ?x)
                    :in [?x ...]]
               [:a-/b :a/b])
            [[:a/b :a-/b]]))

      (is (= (dq/q '[:find (min 2 ?x) (max 2 ?x)
                    :in [?x ...]]
               [:a/b :a-/b :a/c])
            [[[:a/b :a/c] [:a/c :a-/b]]])))

    (testing "Grouping and parameter passing"
      (is (= (set (dq/q '[:find ?color (max ?amount ?x) (min ?amount ?x)
                         :in   [[?color ?x]] ?amount]
                    [[:red 1]  [:red 2] [:red 3] [:red 4] [:red 5]
                     [:blue 7] [:blue 8]]
                    3))
            #{[:red  [3 4 5] [1 2 3]]
              [:blue [7 8]   [7 8]]})))

    (testing "avg aggregate" 
      (is (= (ffirst (dq/q '[:find (avg ?x) 
                            :in [?x ...]]
                       [10 15 20 35 75]))
            31)))

    (testing "median aggregate"
      (is (= (ffirst (dq/q '[:find (median ?x)
                            :in [?x ...]]
                       [10 15 20 35 75]))
            20)))
    
    (testing "variance aggregate"
      (is (= (ffirst (dq/q '[:find (variance ?x)
                            :in [?x ...]]
                       [10 15 20 35 75]))
            554)))

    (testing "stddev aggregate"
      (is (= (ffirst (dq/q '[:find (stddev ?x) 
                            :in [?x ...]]
                       [10 15 20 35 75]))
            23.53720459187964)))

    (testing "Custom aggregates"
      (let [data   [[:red 1]  [:red 2] [:red 3] [:red 4] [:red 5]
                    [:blue 7] [:blue 8]]
            result #{[:red [5 4 3 2 1]] [:blue [8 7]]}]
        
        (is (= (set (dq/q '[:find ?color (aggregate ?agg ?x)
                           :in   [[?color ?x]] ?agg]
                      data
                      sort-reverse))
              result))
        
        #?(:clj
           (is (= (set (dq/q '[:find ?color (eve.db.ds.test.query-aggregates/sort-reverse ?x)
                              :in   [[?color ?x]]]
                         data))
                 result)))))))
