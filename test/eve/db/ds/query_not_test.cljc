(ns eve.db.ds.query-not-test
    "U4 slice: port of upstream eve.db.ds.test.query-not (DataScript 1.8.1),
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

(deftest test-not
         ($ (let [db (:db @*test-conn)]
                 (are [q res] (= (set (dq/q (concat '[:find [?e ...] :where] (quote q)) db))
                                 res)
                      [[?e :name]
                       (not [?e :name "Ivan"])]
                      #{3 4}

                      [[?e :name]
                       (not
                        [?e :name "Ivan"]
                        [?e :age  10])]
                      #{2 3 4 6}

                      [[?e :name]
                       (not [?e :name "Ivan"])
                       (not [?e :age 10])]
                      #{4}

    ;; full exclude
                      [[?e :name]
                       (not [?e :age])]
                      #{}

    ;; not-intersecting rels
                      [[?e :name "Ivan"]
                       (not [?e :name "Oleg"])]
                      #{1 2 5 6}

    ;; exclude empty set
                      [[?e :name]
                       (not
                        [?e :name "Ivan"]
                        [?e :name "Oleg"])]
                      #{1 2 3 4 5 6}

    ;; nested excludes
                      [[?e :name]
                       (not [?e :name "Ivan"]
                            (not [?e :age 10]))]
                      #{1 3 4 5}

    ;; extra binding in not
                      [[?e :name ?a]
                       (not [?e :age ?f]
                            [?e :age 10])]
                      #{2 4 6}))))

(deftest test-not-join
         ($ (let [db (:db @*test-conn)]
                 (are [q res] (= res (dq/q (concat '[:find ?e ?a :where] (quote q)) db))
                      [[?e :name]
                       [?e :age  ?a]
                       (not-join [?e]
                                 [?e :name "Oleg"]
                                 [?e :age ?a])]
                      #{[1 10] [2 20] [5 10] [6 20]}

                      [[?e :age  ?a]
                       [?e :age  10]
                       (not-join [?e]
                                 [?e :name "Oleg"]
                                 [?e :age  ?a]
                                 [?e :age  10])]
                      #{[1 10] [5 10]}

    ;; issue-481
                      [[?e :age ?a]
                       (not-join [?a]
                                 [?e :name "Petr"]
                                 [?e :age ?a])]
                      #{[1 10] [2 20] [3 10] [4 20] [5 10] [6 20]}))))

(deftest test-default-source
         (let [a1 (sup/new-conn)
               a2 (sup/new-conn)]
              ($ (let [db1 (sup/built-db a1 nil
                                         [[:db/add 1 :name "Ivan"]
                                          [:db/add 2 :name "Oleg"]])
                       db2 (sup/built-db a2 nil
                                         [[:db/add 1 :age 10]
                                          [:db/add 2 :age 20]])]
                      (are [q res] (= (set (dq/q (concat '[:find [?e ...]
                                                           :in   $ $2
                                                           :where]
                                                         (quote q))
                                                 db1 db2))
                                      res)
      ;; NOT inherits default source
                           [[?e :name]
                            (not [?e :name "Ivan"])]
                           #{2}

      ;; NOT can reference any source
                           [[?e :name]
                            (not [$2 ?e :age 10])]
                           #{2}

      ;; NOT can change default source
                           [[?e :name]
                            ($2 not [?e :age 10])]
                           #{2}

      ;; even with another default source, it can reference any other source explicitly
                           [[?e :name]
                            ($2 not [$ ?e :name "Ivan"])]
                           #{2}

      ;; nested NOT keeps the default source
                           [[?e :name]
                            ($2 not (not [?e :age 10]))]
                           #{1}

      ;; can override nested NOT source
                           [[?e :name]
                            ($2 not ($ not [?e :name "Ivan"]))]
                           #{1})))))

(deftest test-impl-edge-cases
         ($ (let [db (:db @*test-conn)]
                 (are [q res] (= (dq/q (quote q) db)
                                 res)
    ;; const \ empty
                      [:find ?e
                       :where [?e :name "Oleg"]
                       [?e :age  10]
                       (not [?e :age 20])]
                      #{[3]}

    ;; const \ const
                      [:find ?e
                       :where [?e :name "Oleg"]
                       [?e :age  10]
                       (not [?e :age 10])]
                      #{}

    ;; rel \ const
                      [:find ?e
                       :where [?e :name "Oleg"]
                       (not [?e :age 10])]
                      #{[4]}

    ;; 2 rels \ 2 rels
                      [:find ?e ?e2
                       :where [?e  :name "Ivan"]
                       [?e2 :name "Ivan"]
                       (not [?e :age 10]
                            [?e2 :age 20])]
                      #{[2 1] [6 5] [1 1] [2 2] [5 5] [6 6] [2 5] [1 5] [2 6] [6 1] [5 1] [6 2]}

    ;; 2 rels \ rel + const
                      [:find ?e ?e2
                       :where [?e  :name "Ivan"]
                       [?e2 :name "Oleg"]
                       (not [?e :age 10]
                            [?e2 :age 20])]
                      #{[2 3] [1 3] [2 4] [6 3] [5 3] [6 4]}

    ;; 2 rels \ 2 consts
                      [:find ?e ?e2
                       :where [?e  :name "Oleg"]
                       [?e2 :name "Oleg"]
                       (not [?e :age 10]
                            [?e2 :age 20])]
                      #{[4 3] [3 3] [4 4]}))))

(deftest test-insufficient-bindings
         ($ (let [db (:db @*test-conn)]
                 (are [q msg] (thrown-msg? msg
                                           (dq/q (concat '[:find ?e :where] (quote q)) db))
                      [(not [?e :name "Ivan"])
                       [?e :name]]
                      "Insufficient bindings: none of #{?e} is bound in (not [?e :name \"Ivan\"])"

                      [[?e :name]
                       (not-join [?e]
                                 (not [1 :age ?a])
                                 [?e :age ?a])]
                      "Insufficient bindings: none of #{?a} is bound in (not [1 :age ?a])"

                      [[?e :name]
                       (not [?a :name "Ivan"])]
                      "Insufficient bindings: none of #{?a} is bound in (not [?a :name \"Ivan\"])"))))
