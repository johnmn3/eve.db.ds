(ns eve.db.ds.query-fns-test
  "U4 slice: port of upstream eve.db.ds.test.query-fns (DataScript 1.8.1),
   adapted like entity-test: d/q -> eve.db.ds.query/q, DB construction
   via u4-support, d/entity -> eve.db.ds.impl.entity/entity,
   d/filter -> u4-support/filter-db.
   U4 EXECUTION port: DBs are built inside swap! on a fresh Eve atom and
   every query runs inside one ($ ...) region."
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    [eve.alpha :as eve :refer [$]]
    [eve.db.ds.query :as dq]
    [eve.db.ds.db :as db]
    [eve.db.ds.impl.entity :as de]
    [eve.db.ds.u4-support :as sup])
  #?(:clj
     (:import
       [clojure.lang ExceptionInfo])))

(deftest test-query-fns
  (let [a (sup/new-conn)]
    ($ (testing "predicate without free variables"
         (is (= (dq/q '[:find ?x
                       :in [?x ...]
                       :where [(> 2 1)]] [:a :b :c])
               #{[:a] [:b] [:c]})))

       (let [db (sup/built-db a {:parent {:db/valueType :db.type/ref}}
                             [{:db/id 1, :name  "Ivan",  :age   15}
                              {:db/id 2, :name  "Petr",  :age   22, :height 240, :parent 1}
                              {:db/id 3, :name  "Slava", :age   37, :parent 2}])]

    (testing "ground"
      (is (= (dq/q '[:find ?vowel
                    :where [(ground [:a :e :i :o :u]) [?vowel ...]]])
            #{[:a] [:e] [:i] [:o] [:u]})))

    (testing "get-else"
      (is (= (dq/q '[:find ?e ?age ?height
                    :where [?e :age ?age]
                    [(get-else $ ?e :height 300) ?height]] db)
            #{[1 15 300] [2 22 240] [3 37 300]}))
      
      (is (thrown-with-msg? ExceptionInfo #"get-else: nil default value is not supported"
            (dq/q '[:find ?e ?height
                   :where [?e :age]
                   [(get-else $ ?e :height nil) ?height]] db))))

    (testing "get-some"
      (is (= (dq/q '[:find ?e ?a ?v
                    :where [?e :name _]
                    [(get-some $ ?e :height :age) [?a ?v]]] db)
            #{[1 :age 15]
              [2 :height 240]
              [3 :age 37]})))

    (testing "missing?"
      (is (= (dq/q '[:find ?e ?age
                    :in $
                    :where [?e :age ?age]
                    [(missing? $ ?e :height)]] db)
            #{[1 15] [3 37]})))

    (testing "missing? back-ref"
      (is (= (dq/q '[:find ?e
                    :in $
                    :where [?e :age ?age]
                    [(missing? $ ?e :_parent)]] db)
            #{[3]})))

    (testing "Built-ins"
      (is (= (dq/q '[:find  ?e1 ?e2
                    :where [?e1 :age ?a1]
                    [?e2 :age ?a2]
                    [(< ?a1 18 ?a2)]] db)
            #{[1 2] [1 3]}))
      (is (= (dq/q '[:find  ?a1
                    :where [_ :age ?a1]
                    [(< ?a1 22)]] db)
            #{[15]}))
      (is (= (dq/q '[:find  ?a1
                    :where [_ :age ?a1]
                    [(<= ?a1 22)]] db)
            #{[15] [22]}))
      (is (= (dq/q '[:find  ?a1
                    :where [_ :age ?a1]
                    [(> ?a1 22)]] db)
            #{[37]}))
      (is (= (dq/q '[:find  ?a1
                    :where [_ :age ?a1]
                    [(>= ?a1 22)]] db)
            #{[22] [37]}))

      (testing "compare values of different types"
        (is (= (dq/q '[:find  ?e
                      :where [?e]
                      [(< ?e 1)]] [[0] [1] [""]])
              #{[0]}))
        (is (= (dq/q '[:find  ?e
                      :where [?e]
                      [(<= ?e 1)]] [[0] [1] [""]])
              #{[0] [1]}))
        (is (= (dq/q '[:find  ?e
                      :where [?e]
                      [(> ?e 1)]] [[0] [1] [""]])
              #{[""]}))
        (is (= (dq/q '[:find  ?e
                      :where [?e]
                      [(>= ?e 1)]] [[0] [1] [""]])
              #{[1] [""]})))
      
      (is (= (dq/q '[:find  ?x ?c
                    :in    [?x ...]
                    :where [(count ?x) ?c]]
               ["a" "abc"])
            #{["a" 1] ["abc" 3]})))

    (testing "Built-in vector, hashmap"
      (is (= (dq/q '[:find [?tx-data ...]
                    :where
                    [(ground :db/add) ?op]
                    [(vector ?op -1 :attr 12) ?tx-data]])
            [[:db/add -1 :attr 12]]))

      (is (= (dq/q '[:find [?tx-data ...]
                    :where
                    [(hash-map :db/id -1 :age 92 :name "Aaron") ?tx-data]])
            [{:db/id -1 :age 92 :name "Aaron"}])))

    (testing "Passing predicate as source"
      (is (= (dq/q '[:find  ?e
                    :in    $ ?adult
                    :where [?e :age ?a]
                    [(?adult ?a)]]
               db
               #(> % 18))
            #{[2] [3]})))

    (testing "Calling a function"
      (is (= (dq/q '[:find  ?e1 ?e2 ?e3
                    :where [?e1 :age ?a1]
                    [?e2 :age ?a2]
                    [?e3 :age ?a3]
                    [(+ ?a1 ?a2) ?a12]
                    [(= ?a12 ?a3)]]
               db)
            #{[1 2 3] [2 1 3]})))

    (testing "Two conflicting function values for one binding."
      (is (= (dq/q '[:find  ?n
                    :where
                    [(identity 1) ?n]
                    [(identity 2) ?n]])
            #{})))

    (testing "Destructured conflicting function values for two bindings."
      (is (= (dq/q '[:find  ?n ?x
                    :where
                    [(identity [3 4]) [?n ?x]]
                    [(identity [1 2]) [?n ?x]]])
            #{})))

    (testing "Rule bindings interacting with function binding. (fn, rule)"
      (is (= (dq/q '[:find  ?n
                    :in $ %
                    :where
                    [(identity 2) ?n]
                    (my-vals ?n)]
               db
               '[[(my-vals ?x)
                  [(identity 1) ?x]]
                 [(my-vals ?x)
                  [(identity 2) ?x]]
                 [(my-vals ?x)
                  [(identity 3) ?x]]])
            #{[2]})))

    (testing "Rule bindings interacting with function binding. (rule, fn)"
      (is (= (dq/q '[:find  ?n
                    :in $ %
                    :where (my-vals ?n)
                    [(identity 2) ?n]]
               db
               '[[(my-vals ?x)
                  [(identity 1) ?x]]
                 [(my-vals ?x)
                  [(identity 2) ?x]]
                 [(my-vals ?x)
                  [(identity 3) ?x]]])
            #{[2]})))

    (testing "Conflicting relational bindings with function binding. (rel, fn)"
      (is (= (dq/q '[:find  ?age
                    :where [_ :age ?age]
                    [(identity 100) ?age]]
               db)
            #{})))

    (testing "Conflicting relational bindings with function binding. (fn, rel)"
      (is (= (dq/q '[:find  ?age
                    :where [(identity 100) ?age]
                    [_ :age ?age]]
               db)
            #{})))

    (testing "Function on empty rel"
      (is (= (dq/q '[:find  ?e ?y
                    :where [?e :salary ?x]
                    [(+ ?x 100) ?y]]
               [[0 :age 15] [1 :age 35]])
            #{})))
    
    (testing "Returning nil from function filters out tuple from result"
      (is (= (dq/q '[:find ?x
                    :in    [?in ...] ?f
                    :where [(?f ?in) ?x]]
               [1 2 3 4]
               #(when (even? %) %))
            #{[2] [4]})))

    (testing "Result bindings"
      (is (= (dq/q '[:find ?a ?c
                    :in ?in
                    :where [(ground ?in) [?a _ ?c]]]
               [:a :b :c])
            #{[:a :c]}))

      (is (= (dq/q '[:find ?in
                    :in ?in
                    :where [(ground ?in) _]]
               :a)
            #{[:a]}))

      (is (= (dq/q '[:find ?x ?z
                    :in ?in
                    :where [(ground ?in) [[?x _ ?z]...]]]
               [[:a :b :c] [:d :e :f]])
            #{[:a :c] [:d :f]}))
      
      (is (= (dq/q '[:find ?in
                    :in [?in ...]
                    :where [(ground ?in) _]]
               [])
            #{})))))))

;; issue-490
(deftest test-fn-call-results-unification
  (is (= #{[[:a :a] :a]}
        (dq/q '[:find ?pair ?x
               :in $ ?first ?second
               :where
               [_ _ ?pair]
               [(?first  ?pair) ?x]
               [(?second ?pair) ?x]]
          [[1 :pair [:a :a]]
           [2 :pair [:b :c]]]
          first
          second))))

(deftest test-predicates
  (let [a (sup/new-conn)]
    ($ (let [entities [{:db/id 1 :name "Ivan" :age 10}
                       {:db/id 2 :name "Ivan" :age 20}
                       {:db/id 3 :name "Oleg" :age 10}
                       {:db/id 4 :name "Oleg" :age 20}]
             db (sup/built-db a nil entities)]
    (are [q res] (= (dq/q (quote q) db) res)
      ;; plain predicate
      [:find  ?e ?a
       :where [?e :age ?a]
       [(> ?a 10)]]
      #{[2 20] [4 20]}

      ;; join in predicate
      [:find  ?e ?e2
       :where [?e  :name]
       [?e2 :name]
       [(< ?e ?e2)]]
      #{[1 2] [1 3] [1 4] [2 3] [2 4] [3 4]}
         
      ;; join with extra symbols
      [:find  ?e ?e2
       :where [?e  :age ?a]
       [?e2 :age ?a2]
       [(< ?e ?e2)]]
      #{[1 2] [1 3] [1 4] [2 3] [2 4] [3 4]}

      ;; empty result
      [:find  ?e ?e2
       :where [?e  :name "Ivan"]
       [?e2 :name "Oleg"]
       [(= ?e ?e2)]]
      #{}

      ;; pred over const, true
      [:find  ?e
       :where [?e :name "Ivan"]
       [?e :age 20]
       [(= ?e 2)]]
      #{[2]}

      ;; pred over const, false
      [:find  ?e
       :where [?e :name "Ivan"]
       [?e :age 20]
       [(= ?e 1)]]
      #{})
    (let [pred (fn [db e a]
                 (= a (:age (de/entity db e))))]
      (is (= (dq/q '[:find ?e
                    :in $ ?pred
                    :where [?e :age ?a]
                    [(?pred $ ?e 10)]]
               db pred)
            #{[1] [3]})))))))

(deftest test-exceptions
  (is (thrown-msg? "Unknown predicate 'fun in [(fun ?e)]"
        (dq/q '[:find ?e
               :in   [?e ...]
               :where [(fun ?e)]]
          [1])))
  
  (is (thrown-msg? "Unknown function 'fun in [(fun ?e) ?x]"
        (dq/q '[:find ?e ?x
               :in   [?e ...]
               :where [(fun ?e) ?x]]
          [1])))

  (is (thrown-msg? "Insufficient bindings: #{?x} not bound in [(zero? ?x)]"
        (dq/q '[:find ?x
               :where [(zero? ?x)]])))

  (is (thrown-msg? "Insufficient bindings: #{?x} not bound in [(inc ?x) ?y]"
        (dq/q '[:find ?x
               :where [(inc ?x) ?y]])))

  (is (thrown-msg? "Where uses unknown source vars: [$2]"
        (dq/q '[:find ?x
               :where [?x] [(zero? $2 ?x)]])))

  (is (thrown-msg? "Where uses unknown source vars: [$]"
        (dq/q '[:find  ?x
               :in    $2 
               :where [$2 ?x] [(zero? $ ?x)]]))))

(deftest test-issue-180
  (let [a (sup/new-conn)]
    ($ (is (= #{}
             (dq/q '[:find ?e ?a
                    :where [_ :pred ?pred]
                    [?e :age ?a]
                    [(?pred ?a)]]
               (sup/built-db a nil [[:db/add 1 :age 20]])))))))

(defn sample-query-fn []
  42)

#?(:clj
   (deftest test-symbol-resolution
     (is (= 42 (dq/q '[:find ?x .
                      :where [(eve.db.ds.query-fns-test/sample-query-fn) ?x]])))))

(deftest test-issue-445
  (let [a (sup/new-conn)]
    ($ (let [db (sup/built-db a {:name {:db/unique :db.unique/identity}}
                              [{:db/id 1 :name "Ivan" :age 15}
                               {:db/id 2 :name "Petr" :age 22 :height 240}])]
    (testing "get-else using lookup ref"
      (is (= "Unknown"
            (dq/q '[:find ?height .
                   :in $ ?e
                   :where [(get-else $ ?e :height "Unknown") ?height]]
              db
              [:name "Ivan"]))))

    (testing "get-some using lookup ref"
      (is (= #{[[:name "Petr"] :age 22]}
            (dq/q '[:find ?e ?a ?v
                   :in $ ?e
                   :where [(get-some $ ?e :weight :age :height) [?a ?v]]]
              db
              [:name "Petr"]))))))))
