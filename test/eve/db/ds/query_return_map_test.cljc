(ns eve.db.ds.query-return-map-test
    "U4 slice: port of upstream eve.db.ds.test.query-return-map (DataScript 1.8.1),
   adapted like entity-test: d/q -> eve.db.ds.query/q, DB construction
   via u4-support, d/entity -> eve.db.ds.impl.entity/entity,
   d/filter -> u4-support/filter-db.
   U4 EXECUTION port: the shared DB is built inside swap! on an Eve atom
   at load (sup/init-tx-conn); queries run inside one ($ ...) region."
    (:require
     [clojure.test :as t :refer [is deftest]]
     [eve.alpha :as eve :refer [$]]
     [eve.db.ds.query :as dq]

     [eve.db.ds.u4-support :as sup])
    #?(:clj
       (:import)))

(def *test-conn
     (sup/init-tx-conn nil
                       [[:db/add 1 :name "Petr"]
                        [:db/add 1 :age 44]
                        [:db/add 2 :name "Ivan"]
                        [:db/add 2 :age 25]
                        [:db/add 3 :name "Sergey"]
                        [:db/add 3 :age 11]]))

(deftest test-find-specs
         ($ (let [db (:db @*test-conn)]
                 (is (= (dq/q '[:find ?name ?age
                                :keys n a
                                :where [?e :name ?name]
                                [?e :age  ?age]]
                              db)
                        #{{:n "Petr" :a 44} {:n "Ivan" :a 25} {:n "Sergey" :a 11}}))
                 (is (= (dq/q '[:find ?name ?age
                                :syms n a
                                :where [?e :name ?name]
                                [?e :age  ?age]]
                              db)
                        #{{'n "Petr" 'a 44} {'n "Ivan" 'a 25} {'n "Sergey" 'a 11}}))
                 (is (= (dq/q '[:find ?name ?age
                                :strs n a
                                :where [?e :name ?name]
                                [?e :age  ?age]]
                              db)
                        #{{"n" "Petr" "a" 44} {"n" "Ivan" "a" 25} {"n" "Sergey" "a" 11}}))

                 (is (= (dq/q '[:find [?name ?age]
                                :keys n a
                                :where [?e :name ?name]
                                [(= ?name "Ivan")]
                                [?e :age  ?age]]
                              db)
                        {:n "Ivan" :a 25})))))
