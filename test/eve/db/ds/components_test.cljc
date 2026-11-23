(ns eve.db.ds.components-test
    "U2 port of upstream eve.db.ds.test.components (test/upstream-ds),
   at the DB level through the u2-support harness.

   The upstream slice also asserts through the entity surface
   (d/entity, d/touch, reverse navigation :_profile) and d/q. Those
   are U4's files (impl/entity, query); their assertions live in
   U4's ported entity slice. What is asserted HERE is everything
   components mean at the DB level: schema validation, the stored
   datoms of the component graph, and the retract cascades
   (retractEntity / retractAttribute on a component attribute
   retract the component entities' datoms too), observed through
   h/datoms — the same facts the upstream q assertions check
   ([e ?a ?v] empty for the retracted entities)."
    (:require
     [clojure.test :as t :refer [is deftest testing]]
     [eve.alpha :as eve :refer [$]]
     [eve.db.ds.db :as db]
     [eve.db.ds.u2-support :as h]))

(t/use-fixtures :once h/no-namespace-maps)

(defn- datoms-of
       "Realized [e a v] triples of entity e in the conn's DB, inside $."
       [a e]
       ($ (mapv (fn [d] [(:e d) (:a d) (:v d)])
                (h/datoms (:db @a) :eavt e nil nil nil))))

(deftest test-components
         (is (thrown-msg? "Bad attribute specification for :profile: {:db/isComponent true} should also have {:db/valueType :db.type/ref}"
                          (db/empty-db {:profile {:db/isComponent true}} {})))
         (is (thrown-msg? "Bad attribute specification for {:profile {:db/isComponent \"aaa\"}}, expected one of #{true false}"
                          (db/empty-db {:profile {:db/isComponent "aaa" :db/valueType :db.type/ref}} {})))

         (let [a (h/new-conn)]
              (h/build! a (fn [_]
                              (-> (db/empty-db {:profile {:db/valueType   :db.type/ref
                                                          :db/isComponent true}} {})
                                  (h/db-with [{:db/id 1 :name "Ivan" :profile 3}
                                              {:db/id 3 :email "@3"}
                                              {:db/id 4 :email "@4"}]))))
              (testing "component graph stored"
                       (is (= [[1 :name "Ivan"] [1 :profile 3]] (datoms-of a 1)))
                       (is (= [[3 :email "@3"]] (datoms-of a 3))))

              (testing "nested component"
                       (h/build! a (fn [db] (h/db-with db [[:db/add 3 :profile 4]])))
                       (is (= [[3 :email "@3"] [3 :profile 4]] (datoms-of a 3)))
                       (is (= [[4 :email "@4"]] (datoms-of a 4))))

              (testing "retractEntity cascades into components"
                       (h/build! a (fn [db] (h/db-with db [[:db.fn/retractEntity 1]])))
                       (is (= [] (datoms-of a 1)))
                       (is (= [] (datoms-of a 3)))))

         (let [a (h/new-conn)]
              (h/build! a (fn [_]
                              (-> (db/empty-db {:profile {:db/valueType   :db.type/ref
                                                          :db/isComponent true}} {})
                                  (h/db-with [{:db/id 1 :name "Ivan" :profile 3}
                                              {:db/id 3 :email "@3"}
                                              {:db/id 4 :email "@4"}]))))
              (testing "retractAttribute cascades into components"
                       (h/build! a (fn [db] (h/db-with db [[:db.fn/retractAttribute 1 :profile]])))
                       (is (= [[1 :name "Ivan"]] (datoms-of a 1)))
                       (is (= [] (datoms-of a 3))))))

(deftest test-components-multival
         (let [schema {:profile {:db/valueType   :db.type/ref
                                 :db/cardinality :db.cardinality/many
                                 :db/isComponent true}}
               build (fn [a]
                         (h/build! a (fn [_]
                                         (-> (db/empty-db schema {})
                                             (h/db-with [{:db/id 1 :name "Ivan" :profile [3 4]}
                                                         {:db/id 3 :email "@3"}
                                                         {:db/id 4 :email "@4"}])))))]
              (let [a (h/new-conn)]
                   (build a)
                   (testing "component graph stored"
                            (is (= [[1 :name "Ivan"] [1 :profile 3] [1 :profile 4]] (datoms-of a 1)))
                            (is (= [[3 :email "@3"]] (datoms-of a 3)))
                            (is (= [[4 :email "@4"]] (datoms-of a 4))))

                   (testing "retractEntity cascades into components"
                            (h/build! a (fn [db] (h/db-with db [[:db.fn/retractEntity 1]])))
                            (is (= [] (datoms-of a 1)))
                            (is (= [] (datoms-of a 3)))
                            (is (= [] (datoms-of a 4)))))

              (let [a (h/new-conn)]
                   (build a)
                   (testing "retractAttribute cascades into components"
                            (h/build! a (fn [db] (h/db-with db [[:db.fn/retractAttribute 1 :profile]])))
                            (is (= [[1 :name "Ivan"]] (datoms-of a 1)))
                            (is (= [] (datoms-of a 3)))
                            (is (= [] (datoms-of a 4)))))))
