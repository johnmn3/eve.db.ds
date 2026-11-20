(ns eve.db.ds.entity-test
  "U4 slice: port of upstream eve.db.ds.test.entity (DataScript 1.8.1),
   adapted to exercise eve.db.ds.impl.entity directly. Execution shape
   (post-U2, plan v2 §1): DBs are built inside swap! on a per-test
   Eve atom (u4-support/build!) and every assertion runs inside one
   $ with each DB version bound once — host-built DBs are DB__Flat
   values whose indexes cannot be read (u4-logs/probe1-4).
   d/entity -> de/entity, d/touch -> de/touch, d/entity-db -> (.-db e)."
  (:require
    [clojure.edn :as edn]
    [clojure.test :as t :refer [is are deftest testing]]
    [eve.alpha :as eve :refer [$]]
    [eve.db.ds.impl.entity :as de]
    [eve.db.ds.u4-support :as sup])
  #?(:clj
     (:import
       [clojure.lang ExceptionInfo])))

(t/use-fixtures :once sup/no-namespace-maps)

(deftest test-entity
  (let [a (sup/new-conn)]
    (sup/build! a {:aka {:db/cardinality :db.cardinality/many}}
      [[{:db/id 1, :name "Ivan", :age 19, :aka ["X" "Y"]}
        {:db/id 2, :name "Ivan", :sex "male", :aka ["Z"]}
        [:db/add 3 :huh? false]]])
    ($ (let [db (:db @a)
             e  (de/entity db 1)]
         (is (= (:db/id e) 1))
         (is (identical? (.-db e) db))
         (is (= (:name e) "Ivan"))
         (is (= (e :name) "Ivan")) ; IFn form
         (is (= (:age  e) 19))
         (is (= (:aka  e) #{"X" "Y"}))
         (is (= true (contains? e :age)))
         (is (= false (contains? e :not-found)))
         (is (= (into {} e)
               {:name "Ivan", :age 19, :aka #{"X" "Y"}}))
         (is (= (into {} (de/entity db 1))
               {:name "Ivan", :age 19, :aka #{"X" "Y"}}))
         (is (= (into {} (de/entity db 2))
               {:name "Ivan", :sex "male", :aka #{"Z"}}))
         (let [e3 (de/entity db 3)]
           (is (= (into {} e3) {:huh? false})) ; Force caching.
           (is (false? (:huh? e3))))

         (is (= (pr-str (de/entity db 1)) "{:db/id 1}"))
         (is (= (pr-str (let [e (de/entity db 1)] (:unknown e) e)) "{:db/id 1}"))
         ;; read back in to account for unordered-ness
         (is (= (edn/read-string (pr-str (let [e (de/entity db 1)] (:name e) e)))
               (edn/read-string "{:name \"Ivan\", :db/id 1}")))))))

(deftest test-entity-refs
  (let [a (sup/new-conn)]
    (sup/build! a {:father   {:db/valueType   :db.type/ref}
                   :children {:db/valueType   :db.type/ref
                              :db/cardinality :db.cardinality/many}}
      [[{:db/id 1, :children [10]}
        {:db/id 10, :father 1, :children [100 101]}
        {:db/id 100, :father 10}
        {:db/id 101, :father 10}]])
    ($ (let [db (:db @a)
             e  #(de/entity db %)]

         (is (= (:children (e 1))   #{(e 10)}))
         (is (= (:children (e 10))  #{(e 100) (e 101)}))

         (testing "empty attribute"
           (is (= (:children (e 100)) nil)))

         (testing "nested navigation"
           (is (= (-> (e 1) :children first :children) #{(e 100) (e 101)}))
           (is (= (-> (e 10) :children first :father) (e 10)))
           (is (= (-> (e 10) :father :children) #{(e 10)}))

           (testing "after touch"
             (let [e1  (e 1)
                   e10 (e 10)]
               (de/touch e1)
               (de/touch e10)
               (is (= (-> e1 :children first :children) #{(e 100) (e 101)}))
               (is (= (-> e10 :children first :father) (e 10)))
               (is (= (-> e10 :father :children) #{(e 10)})))))

         (testing "backward navigation"
           (is (= (:_children (e 1))  nil))
           (is (= (:_father   (e 1))  #{(e 10)}))
           (is (= (:_children (e 10)) #{(e 1)}))
           (is (= (:_father   (e 10)) #{(e 100) (e 101)}))
           (is (= (-> (e 100) :_children first :_children) #{(e 1)})))))))

(deftest test-missing-refs
  (let [schema {:ref       {:db/valueType   :db.type/ref}
                :comp      {:db/valueType   :db.type/ref
                            :db/isComponent true}
                :multiref  {:db/valueType   :db.type/ref
                            :db/cardinality :db.cardinality/many}
                :multicomp {:db/valueType   :db.type/ref
                            :db/isComponent true
                            :db/cardinality :db.cardinality/many}}
        a (sup/new-conn)]
    (sup/build! a schema [])
    ($ (let [db (:db @a)]
         (sup/advance! a [[:db/add 1 :ref       2]
                          [:db/add 1 :comp      3]
                          [:db/add 1 :multiref  4]
                          [:db/add 1 :multiref  5]
                          [:db/add 1 :multicomp 6]
                          [:db/add 1 :multicomp 6]])
         (let [db' (:db @a)]
           (de/touch (de/entity db 1)) ;; does not throw
           (is (= nil (:ref (de/entity db 1))))
           (is (= nil (:comp (de/entity db 1))))
           (is (= nil (:multiref (de/entity db 1))))
           (is (= nil (:multicomp (de/entity db 1)))))))))

(deftest test-entity-misses
  (let [a (sup/new-conn)]
    (sup/build! a {:name {:db/unique :db.unique/identity}}
      [[{:db/id 1, :name "Ivan"}
        {:db/id 2, :name "Oleg"}]])
    ($ (let [db (:db @a)]
         (is (nil? (de/entity db nil)))
         (is (nil? (de/entity db "abc")))
         (is (nil? (de/entity db :keyword)))
         (is (nil? (de/entity db [:name "Petr"])))
         (is (nil? (de/entity db 777)))
         (is (thrown-msg? "Lookup ref attribute should be marked as :db/unique: [:not-an-attr 777]"
               (de/entity db [:not-an-attr 777])))))))

(deftest test-entity-equality
  (let [a (sup/new-conn)]
    (sup/build! a {} [[{:db/id 1, :name "Ivan"}]])
    ($ (let [db1 (:db @a)
             e1  (de/entity db1 1)]
         (sup/advance! a [])
         (let [db2 (:db @a)]
           (sup/advance! a [{:db/id 2, :name "Oleg"}])
           (let [db3 (:db @a)]

             (testing "Two entities are equal if they have the same :db/id"
               (is (= e1 e1))
               (is (= e1 (de/entity db1 1)))

               (testing "and refer to the same database"
                 (is (not= e1 (de/entity db2 1)))
                 (is (not= e1 (de/entity db3 1)))))))))))

(deftest test-entity-hash
  (let [a (sup/new-conn)]
    (sup/build! a {} [[{:db/id 1, :name "Ivan"}]])
    ($ (let [db1 (:db @a)
             e1  (de/entity db1 1)]
         (sup/advance! a [])
         (let [db2 (:db @a)]
           (sup/advance! a [{:db/id 2, :name "Oleg"}])
           (let [db3 (:db @a)]

             (testing "Two entities have the same hash if they have the same :db/id"
               (is (= (hash e1) (hash e1)))
               (is (= (hash e1) (hash (de/entity db1 1))))

               (testing "and refer to the same database"
                 (is (not= (hash e1) (hash (de/entity db2 1))))
                 (is (not= (hash e1) (hash (de/entity db3 1))))))))))))
