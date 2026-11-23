(ns eve.db.ds.query-or-test
    "U4 slice: port of upstream eve.db.ds.test.query-or (DataScript 1.8.1),
   adapted like entity-test: d/q -> eve.db.ds.query/q, DB construction
   via u4-support, d/entity -> eve.db.ds.impl.entity/entity,
   d/filter -> u4-support/filter-db.
   U4 EXECUTION port: shared DB built inside swap! on an Eve atom at load
   (sup/init-tx-conn); every deftest runs inside one ($ ...) region."
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

(def *test-conn
     (sup/init-tx-conn nil
                       [{:db/id 1 :name "Ivan" :age 10}
                        {:db/id 2 :name "Ivan" :age 20}
                        {:db/id 3 :name "Oleg" :age 10}
                        {:db/id 4 :name "Oleg" :age 20}
                        {:db/id 5 :name "Ivan" :age 10}
                        {:db/id 6 :name "Ivan" :age 20}]))

(deftest test-or
         ($ (let [db (:db @*test-conn)]
                 (are [q res] (= (dq/q (concat '[:find ?e :where] (quote q)) db)
                                 (into #{} (map vector) res))

    ;; intersecting results
                      [(or [?e :name "Oleg"]
                           [?e :age 10])]
                      #{1 3 4 5}

    ;; one branch empty
                      [(or [?e :name "Oleg"]
                           [?e :age 30])]
                      #{3 4}

    ;; both empty
                      [(or [?e :name "Petr"]
                           [?e :age 30])]
                      #{}

    ;; join with 1 var
                      [[?e :name "Ivan"]
                       (or [?e :name "Oleg"]
                           [?e :age 10])]
                      #{1 5}

    ;; join with 2 vars
                      [[?e :age ?a]
                       (or (and [?e :name "Ivan"]
                                [1  :age  ?a])
                           (and [?e :name "Oleg"]
                                [2  :age  ?a]))]
                      #{1 5 4}

    ;; OR introduces vars
                      [(or (and [?e :name "Ivan"]
                                [1  :age  ?a])
                           (and [?e :name "Oleg"]
                                [2  :age  ?a]))
                       [?e :age ?a]]
                      #{1 5 4}

    ;; OR introduces vars in different order
                      [(or (and [?e :name "Ivan"]
                                [1  :age  ?a])
                           (and [2  :age  ?a]
                                [?e :name "Oleg"]))
                       [?e :age ?a]]
                      #{1 5 4}

    ;; One branch of or short-circuits resolution
                      [(or
                        (and [?e :age 30] ; no matches in db
                             [?e :name ?n])
                        (and [?e :age 20]
                             [?e :name ?n]))
                       [(ground "Ivan") ?n]]
                      #{2 6}))))

(deftest test-or-join
         ($ (let [db (:db @*test-conn)]
                 (are [q res] (= (dq/q (concat '[:find ?e :where] (quote q)) db)
                                 (into #{} (map vector) res))
                      [(or-join [?e]
                                [?e :name ?n]
                                (and [?e :age ?a]
                                     [?e :name ?n]))]
                      #{1 2 3 4 5 6}

                      [[?e  :name ?a]
                       [?e2 :name ?a]
                       (or-join [?e]
                                (and [?e  :age ?a]
                                     [?e2 :age ?a]))]
                      #{1 2 3 4 5 6}

    ;; One branch of or-join short-circuits resolution
                      [(or-join [?e ?n]
                                (and [?e :age 30] ; no matches in db
                                     [?e :name ?n])
                                (and [?e :age 20]
                                     [?e :name ?n]))
                       [(ground "Ivan") ?n]]
                      #{2 6})

  ;; issue-348
                 (is (= #{[1] [3] [4] [5]}
                        (dq/q '[:find ?e
                                :in $ ?a
                                :where (or
                                        [?e :age ?a]
                                        [?e :name "Oleg"])]
                              db 10)))

  ;; issue-348
                 (is (= #{[1] [3] [4] [5]}
                        (dq/q '[:find ?e
                                :in $ ?a
                                :where (or-join [?e ?a]
                                                [?e :age ?a]
                                                [?e :name "Oleg"])]
                              db 10)))

  ;; issue-348
                 (is (= #{[1] [3] [4] [5]}
                        (dq/q '[:find ?e
                                :in $ ?a
                                :where (or-join [[?a] ?e]
                                                [?e :age ?a]
                                                [?e :name "Oleg"])]
                              db 10)))

                 (is (= #{[:a1 :b1 :c1]
                          [:a2 :b2 :c2]}
                        (dq/q '[:find ?a ?b ?c
                                :in $xs $ys
                                :where [$xs ?a ?b ?c] ;; check join by ?a, ignoring ?b, dropping ?c ?d
                                (or-join [?a]
                                         [$ys ?a ?b ?d])]
                              [[:a1 :b1 :c1]
                               [:a2 :b2 :c2]
                               [:a3 :b3 :c3]]
                              [[:a1 :b1  :d1] ;; same ?a, same ?b
                               [:a2 :b2* :d2] ;; same ?a, different ?b. Should still be joined
                               [:a4 :b4 :c4]]))) ;; different ?a, should be dropped

                 (is (= #{[:a1 :c1] [:a2 :c2]}
                        (dq/q '[:find ?a ?c
                                :in $xs $ys
                                :where (or-join [?a ?c]
                                                [$xs ?a ?b ?c] ; rel with hole (?b gets dropped, leaving {?a 0 ?c 2} and 3-element tuples)
                                                [$ys ?a ?c])]
                              [[:a1 :b1 :c1]]
                              [[:a2 :c2]]))))))

(deftest test-default-source
         (let [a1 (sup/new-conn)
               a2 (sup/new-conn)]
              ($ (let [db1 (sup/built-db a1 nil
                                         [[:db/add 1 :name "Ivan"]
                                          [:db/add 2 :name "Oleg"]])
                       db2 (sup/built-db a2 nil
                                         [[:db/add 1 :age 10]
                                          [:db/add 2 :age 20]])]
                      (are [q res] (= (dq/q (concat '[:find ?e :in $ $2 :where] (quote q)) db1 db2)
                                      (into #{} (map vector) res))
      ;; OR inherits default source
                           [[?e :name]
                            (or [?e :name "Ivan"])]
                           #{1}

      ;; OR can reference any source
                           [[?e :name]
                            (or [$2 ?e :age 10])]
                           #{1}

      ;; OR can change default source
                           [[?e :name]
                            ($2 or [?e :age 10])]
                           #{1}

      ;; even with another default source, it can reference any other source explicitly
                           [[?e :name]
                            ($2 or [$ ?e :name "Ivan"])]
                           #{1}

      ;; nested OR keeps the default source
                           [[?e :name]
                            ($2 or (or [?e :age 10]))]
                           #{1}

      ;; can override nested OR source
                           [[?e :name]
                            ($2 or ($ or [?e :name "Ivan"]))]
                           #{1})))))

(deftest ^{:doc "issue-468, issue-469"} test-const-substitution
         (let [a (sup/new-conn)]
              ($ (let [db (sup/built-db a {:parent {:db/valueType :db.type/ref}}
                                        [{:db/id "Ivan" :name "Ivan"}
                                         {:db/id "Oleg" :name "Oleg" :parent "Ivan"}
                                         {:db/id "Petr" :name "Petr" :parent "Oleg"}])]
                      (is (= #{["Ivan" 1 2]}
                             (dq/q '[:find ?name ?x ?y
                                     :in $ ?name
                                     :where
                                     [?x :name ?name]
                                     (or-join [?x ?y]
                                              (and
                                               [?x :parent ?z]
                                               [?z :parent ?y])
                                              [?y :parent ?x])]
                                   db "Ivan")))

                      (is (= #{}
                             (dq/q '[:find ?name ?x ?y
                                     :in $ ?name
                                     :where
                                     [?x :name ?name]
                                     (or-join [?x ?y]
                                              (and
                                               [?x :parent ?z]
                                               [?z :parent ?y])
                                              [?x :parent ?y])]
                                   db "Ivan")))))))

(deftest test-errors
         ($ (let [db (:db @*test-conn)]
                 (is (thrown-with-msg? ExceptionInfo #"All clauses in 'or' must use same set of free vars, had \[#\{\?e\} #\{(\?a \?e|\?e \?a)\}\] in \(or \[\?e :name _\] \[\?e :age \?a\]\)"
                                       (dq/q '[:find ?e
                                               :where (or [?e :name _]
                                                          [?e :age ?a])]
                                             db)))

                 (is (thrown-msg? "Insufficient bindings: #{?e} not bound in (or-join [[?e]] [?e :name \"Ivan\"])"
                                  (dq/q '[:find ?e
                                          :where (or-join [[?e]]
                                                          [?e :name "Ivan"])]
                                        db))))))
