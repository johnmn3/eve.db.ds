(ns eve.db.ds.query-return-map-test
  "U4 slice: port of upstream eve.db.ds.test.query-return-map (DataScript 1.8.1),
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

(def *test-db
  (delay
    (sup/db-with (sup/empty-db)
      [[:db/add 1 :name "Petr"]
       [:db/add 1 :age 44]
       [:db/add 2 :name "Ivan"]
       [:db/add 2 :age 25]
       [:db/add 3 :name "Sergey"]
       [:db/add 3 :age 11]])))

(deftest test-find-specs
  (is (= (dq/q '[:find ?name ?age
                :keys n a
                :where [?e :name ?name]
                [?e :age  ?age]]
           @*test-db)
        #{{:n "Petr" :a 44} {:n "Ivan" :a 25} {:n "Sergey" :a 11}}))
  (is (= (dq/q '[:find ?name ?age
                :syms n a
                :where [?e :name ?name]
                [?e :age  ?age]]
           @*test-db)
        #{{'n "Petr" 'a 44} {'n "Ivan" 'a 25} {'n "Sergey" 'a 11}}))
  (is (= (dq/q '[:find ?name ?age
                :strs n a
                :where [?e :name ?name]
                [?e :age  ?age]]
           @*test-db)
        #{{"n" "Petr" "a" 44} {"n" "Ivan" "a" 25} {"n" "Sergey" "a" 11}}))

  (is (= (dq/q '[:find [?name ?age]
                :keys n a
                :where [?e :name ?name]
                [(= ?name "Ivan")]
                [?e :age  ?age]]
           @*test-db)
        {:n "Ivan" :a 25})))


