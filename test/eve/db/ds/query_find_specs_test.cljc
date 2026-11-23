(ns eve.db.ds.query-find-specs-test
    "U4 slice: port of upstream eve.db.ds.test.query-find-specs (DataScript 1.8.1),
   adapted like entity-test: d/q -> eve.db.ds.query/q, DB construction
   via u4-support, d/entity -> eve.db.ds.impl.entity/entity,
   d/filter -> u4-support/filter-db.
   U4 EXECUTION port: the shared DB is built inside swap! on an Eve atom
   at load (sup/init-tx-conn); queries run inside one ($ ...) region."
    (:require
     [clojure.test :as t :refer [is deftest testing]]
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
                 (is (= (set (dq/q '[:find [?name ...]
                                     :where [_ :name ?name]] db))
                        #{"Ivan" "Petr" "Sergey"}))
                 (is (= (dq/q '[:find [?name ?age]
                                :where [1 :name ?name]
                                [1 :age  ?age]] db)
                        ["Petr" 44]))
                 (is (= "Petr" (dq/q '[:find ?name . :where [1 :name ?name]] db)))

                 (testing "Multiple results get cut"
                          (is (contains?
                               #{["Petr" 44] ["Ivan" 25] ["Sergey" 11]}
                               (dq/q '[:find [?name ?age]
                                       :where [?e :name ?name]
                                       [?e :age  ?age]] db)))
                          (is (contains?
                               #{"Ivan" "Petr" "Sergey"}
                               (dq/q '[:find ?name .
                                       :where [_ :name ?name]] db))))

                 (testing "Aggregates work with find specs"
                          (is (= (dq/q '[:find [(count ?name) ...]
                                         :where [_ :name ?name]] db)
                                 [3]))
                          (is (= (dq/q '[:find [(count ?name)]
                                         :where [_ :name ?name]] db)
                                 [3]))
                          (is (= 3 (dq/q '[:find (count ?name) . :where [_ :name ?name]] db)))))))
