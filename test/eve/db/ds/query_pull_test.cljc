(ns eve.db.ds.query-pull-test
  "U4 slice: port of upstream eve.db.ds.test.query-pull (DataScript 1.8.1),
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
      [{:db/id 1 :name "Petr" :age 44}
       {:db/id 2 :name "Ivan" :age 25}
       {:db/id 3 :name "Oleg" :age 11}])))

(deftest test-basics
  (are [find res] (= (set (dq/q {:find find
                                :where '[[?e :age ?a]
                                         [(>= ?a 18)]]}
                            @*test-db))
                    res)
    '[(pull ?e [:name])]
    #{[{:name "Ivan"}] [{:name "Petr"}]}

    '[(pull ?e [*])]
    #{[{:db/id 2 :age 25 :name "Ivan"}] [{:db/id 1 :age 44 :name "Petr"}]}

    '[?e (pull ?e [:name])]
    #{[2 {:name "Ivan"}] [1 {:name "Petr"}]}

    '[?e ?a (pull ?e [:name])]
    #{[2 25 {:name "Ivan"}] [1 44 {:name "Petr"}]}

    '[?e (pull ?e [:name]) ?a]
    #{[2 {:name "Ivan"} 25] [1 {:name "Petr"} 44]}))

(deftest test-var-pattern
  (are [find pattern res] (= (set (dq/q {:find find
                                        :in   '[$ ?pattern]
                                        :where '[[?e :age ?a]
                                                 [(>= ?a 18)]]}
                                    @*test-db pattern))
                            res)
    '[(pull ?e ?pattern)] [:name]
    #{[{:name "Ivan"}] [{:name "Petr"}]}
       
    '[?e ?a ?pattern (pull ?e ?pattern)] [:name]
    #{[2 25 [:name] {:name "Ivan"}] [1 44 [:name] {:name "Petr"}]}))

;; not supported
#_(deftest test-multi-pattern
    (is (= (set (dq/q '[:find ?e ?p (pull ?e ?p)
                       :in $ [?p ...]
                       :where [?e :age ?a]
                       [>= ?a 18]]
                  @*test-db [[:name] [:age]]))
          #{[2 [:name] {:name "Ivan"}]
            [2 [:age]  {:age 25}]
            [1 [:name] {:name "Petr"}]
            [1 [:age]  {:age 44}]})))

(deftest test-multiple-sources
  (let [db1 (sup/db-with (sup/empty-db) [{:db/id 1 :name "Ivan" :age 25}])
        db2 (sup/db-with (sup/empty-db) [{:db/id 1 :name "Petr" :age 25}])]
    (is (= (set (dq/q '[:find ?e (pull $1 ?e [:name])
                       :in $1 $2
                       :where [$1 ?e :age 25]]
                  db1 db2))
          #{[1 {:name "Ivan"}]}))
    
    (is (= (set (dq/q '[:find ?e (pull $2 ?e [:name])
                       :in $1 $2
                       :where [$2 ?e :age 25]]
                  db1 db2))
          #{[1 {:name "Petr"}]}))
    
    (testing "$ is default source"
      (is (= (set (dq/q '[:find ?e (pull ?e [:name])
                         :in $1 $
                         :where [$ ?e :age 25]]
                    db1 db2))
            #{[1 {:name "Petr"}]})))))

(deftest test-find-spec
  (is (= (dq/q '[:find (pull ?e [:name]) .
                :where [?e :age 25]]
           @*test-db)
        {:name "Ivan"}))
       
  (is (= (set (dq/q '[:find [(pull ?e [:name]) ...]
                     :where [?e :age ?a]]
                @*test-db))
        #{{:name "Ivan"} {:name "Petr"} {:name "Oleg"}}))

  (is (= (dq/q '[:find [?e (pull ?e [:name])]
                :where [?e :age 25]]
           @*test-db)
        [2 {:name "Ivan"}])))

(deftest test-find-spec-input
  (is (= (dq/q '[:find (pull ?e ?p) .
                :in $ ?p
                :where [(ground 2) ?e]]
           @*test-db [:name])
        {:name "Ivan"}))
  (is (= (dq/q '[:find (pull ?e p) .
                :in $ p
                :where [(ground 2) ?e]]
           @*test-db [:name])
        {:name "Ivan"})))

(deftest test-aggregates
  (let [db (sup/db-with (sup/empty-db {:value {:db/cardinality :db.cardinality/many}})
             [{:db/id 1 :name "Petr" :value [10 20 30 40]}
              {:db/id 2 :name "Ivan" :value [14 16]}
              {:db/id 3 :name "Oleg" :value 1}])]
    (is (= (set (dq/q '[:find ?e (pull ?e [:name]) (min ?v) (max ?v)
                       :where [?e :value ?v]]
                  db))
          #{[1 {:name "Petr"} 10 40]
            [2 {:name "Ivan"} 14 16]
            [3 {:name "Oleg"} 1 1]}))))

(deftest test-lookup-refs
  (let [db (sup/db-with (sup/empty-db {:name {:db/unique :db.unique/identity}})
             [{:db/id 1 :name "Petr" :age 44}
              {:db/id 2 :name "Ivan" :age 25}
              {:db/id 3 :name "Oleg" :age 11}])]
    (is (= (set (dq/q '[:find ?ref ?a (pull ?ref [:db/id :name])
                       :in   $ [?ref ...]
                       :where [?ref :age ?a]
                       [(>= ?a 18)]]
                  db [[:name "Ivan"] [:name "Oleg"] [:name "Petr"]]))
          #{[[:name "Petr"] 44 {:db/id 1 :name "Petr"}]
            [[:name "Ivan"] 25 {:db/id 2 :name "Ivan"}]}))))
