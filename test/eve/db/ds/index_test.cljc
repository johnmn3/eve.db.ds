(ns eve.db.ds.index-test
    "U2 port of upstream eve.db.ds.test.index (test/upstream-ds), run
   against db-level fns through the u2-support harness: d/datoms ->
   h/datoms (realized inside $), d/seek-datoms -> h/seek-datoms,
   d/rseek-datoms -> h/rseek-datoms, d/index-range ->
   h/index-range, d/find-datom -> db/find-datom."
    (:require
     [clojure.test :as t :refer [is are deftest testing]]
     [eve.alpha :as eve :refer [$]]
     [eve.db.ds.db :as db]
     [eve.db.ds.u2-support :as h]))

(defn- dvec [d] (vector (:e d) (:a d) (:v d)))
(defn- dvec-or-nil [d] (when d (vector (:e d) (:a d) (:v d))))

(defn- sample-conn []
       (let [a (h/new-conn)]
            (h/build! a (fn [_]
                            (-> (db/empty-db {:age {:db/index true}} {})
                                (h/db-with [[:db/add 1 :name "Petr"]
                                            [:db/add 1 :age 44]
                                            [:db/add 2 :name "Ivan"]
                                            [:db/add 2 :age 25]
                                            [:db/add 3 :name "Sergey"]
                                            [:db/add 3 :age 11]]))))
            a))

(deftest test-datoms
         (let [a (sample-conn)]
              (testing "Main indexes, sort order"
                       (is (= [[1 :age 44]
                               [2 :age 25]
                               [3 :age 11]
                               [1 :name "Petr"]
                               [2 :name "Ivan"]
                               [3 :name "Sergey"]]
                              ($ (mapv dvec (h/datoms (:db @a) :aevt nil nil nil nil)))))

                       (is (= [[1 :age 44]
                               [1 :name "Petr"]
                               [2 :age 25]
                               [2 :name "Ivan"]
                               [3 :age 11]
                               [3 :name "Sergey"]]
                              ($ (mapv dvec (h/datoms (:db @a) :eavt nil nil nil nil)))))

                       (is (= [[3 :age 11]
                               [2 :age 25]
                               [1 :age 44]]
                              ($ (mapv dvec (h/datoms (:db @a) :avet nil nil nil nil)))))) ;; name non-indexed, excluded from avet

              (testing "Components filtration"
                       (is (= [[1 :age 44]
                               [1 :name "Petr"]]
                              ($ (mapv dvec (h/datoms (:db @a) :eavt 1 nil nil nil)))))

                       (is (= [[1 :age 44]]
                              ($ (mapv dvec (h/datoms (:db @a) :eavt 1 :age nil nil)))))

                       (is (= [[3 :age 11]
                               [2 :age 25]
                               [1 :age 44]]
                              ($ (mapv dvec (h/datoms (:db @a) :avet :age nil nil nil))))))

              (testing "Error reporting"
      ;; upstream: (d/datoms db :avet) ;; no error — the full avet
      ;; slice (indexed attrs only) comes back:
                       (is (= [[3 :age 11]
                               [2 :age 25]
                               [1 :age 44]]
                              ($ (mapv dvec (h/datoms (:db @a) :avet)))))

                       (is (thrown-msg? "Attribute :name should be marked as :db/index true"
                                        ($ (h/datoms (:db @a) :avet :name nil nil nil))))

                       (is (thrown-msg? "Attribute :alias should be marked as :db/index true"
                                        ($ (h/datoms (:db @a) :avet :alias nil nil nil))))

                       (is (thrown-msg? "Attribute :name should be marked as :db/index true"
                                        ($ (h/datoms (:db @a) :avet :name "Ivan" nil))))

                       (is (thrown-msg? "Attribute :name should be marked as :db/index true"
                                        ($ (h/datoms (:db @a) :avet :name "Ivan" 1)))))

              (testing "Sequence compare issue-470"
                       (let [b (h/new-conn)]
                            (h/build! b (fn [_]
                                            (-> (db/empty-db {:path {:db/index true}} {})
                                                (h/db-with [{:db/id 1 :path [1 2]}
                                                            {:db/id 2 :path [1 2 3]}]))))
                            (are [value result] (= result ($ (mapv :e (h/datoms (:db @b) :avet :path value nil))))
                                 [1]                 []
                                 [1 1]               []
                                 [1 2]               [1]
                                 (list 1 2)          [1]
                                 (butlast [1 2 3])   [1]
                                 [1 3]               []
                                 [1 2 2]             []
                                 [1 2 3]             [2]
                                 (list 1 2 3)        [2]
                                 (butlast [1 2 3 4]) [2]
                                 [1 2 4]             []
                                 [1 2 3 4]           [])))))

(deftest test-datom
         (let [a (sample-conn)]
              (is (= [1 :age 44] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt nil nil nil nil)))))
              (is (= [1 :age 44] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 1 nil nil nil)))))
              (is (= [1 :age 44] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 1 :age nil nil)))))
              (is (= [1 :name "Petr"] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 1 :name nil nil)))))
              (is (= [1 :name "Petr"] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 1 :name "Petr" nil)))))

              (is (= [2 :age 25] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 2 nil nil nil)))))
              (is (= [2 :age 25] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 2 :age nil nil)))))
              (is (= [2 :name "Ivan"] ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 2 :name nil nil)))))

              (is (nil? ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 1 :name "Ivan" nil)))))
              (is (nil? ($ (dvec-or-nil (db/find-datom (:db @a) :eavt 4 nil nil nil)))))

    ;; issue-477 (built/read through the harness: bset ops do not run
    ;; on delivered roots, so even the empty-db case goes through $)
              (let [e1 (h/new-conn)
                    e2 (h/new-conn)]
                   (h/build! e1 (fn [_] (db/empty-db {} {})))
                   (h/build! e2 (fn [_] (db/empty-db {:age {:db/index true}} {})))
                   (is (nil? ($ (db/find-datom (:db @e1) :eavt nil nil nil nil))))
                   (is (nil? ($ (db/find-datom (:db @e2) :eavt nil nil nil nil)))))))

(defn- seek-conn []
       (let [a (h/new-conn)]
            (h/build! a (fn [_]
                            (-> (db/empty-db {:name {:db/index true}
                                              :age  {:db/index true}} {})
                                (h/db-with [[:db/add 1 :name "Petr"]
                                            [:db/add 1 :age 44]
                                            [:db/add 2 :name "Ivan"]
                                            [:db/add 2 :age 25]
                                            [:db/add 3 :name "Sergey"]
                                            [:db/add 3 :age 11]]))))
            a))

(deftest test-seek-datoms
         (let [a (seek-conn)]
              (testing "Non-termination"
                       (is (= ($ (mapv dvec (h/seek-datoms (:db @a) :avet :age 10 nil nil)))
                              [[3 :age 11]
                               [2 :age 25]
                               [1 :age 44]
                               [2 :name "Ivan"]
                               [1 :name "Petr"]
                               [3 :name "Sergey"]])))

              (testing "Closest value lookup"
                       (is (= ($ (mapv dvec (h/seek-datoms (:db @a) :avet :name "P" nil nil)))
                              [[1 :name "Petr"]
                               [3 :name "Sergey"]])))

              (testing "Exact value lookup"
                       (is (= ($ (mapv dvec (h/seek-datoms (:db @a) :avet :name "Petr" nil nil)))
                              [[1 :name "Petr"]
                               [3 :name "Sergey"]])))

              (is (thrown-msg? "Attribute :alias should be marked as :db/index true"
                               ($ (h/seek-datoms (:db @a) :avet :alias nil nil nil))))))

(deftest test-rseek-datoms
         (let [a (seek-conn)]
              (testing "Non-termination"
                       (is (= ($ (mapv dvec (h/rseek-datoms (:db @a) :avet :name "Petr" nil nil)))
                              [[1 :name "Petr"]
                               [2 :name "Ivan"]
                               [1 :age 44]
                               [2 :age 25]
                               [3 :age 11]])))

              (testing "Closest value lookup"
                       (is (= ($ (mapv dvec (h/rseek-datoms (:db @a) :avet :age 26 nil nil)))
                              [[2 :age 25]
                               [3 :age 11]])))

              (testing "Exact value lookup"
                       (is (= ($ (mapv dvec (h/rseek-datoms (:db @a) :avet :age 25 nil nil)))
                              [[2 :age 25]
                               [3 :age 11]])))

              (is (thrown-msg? "Attribute :alias should be marked as :db/index true"
                               ($ (h/rseek-datoms (:db @a) :avet :alias nil nil nil))))))

(deftest test-index-range
         (let [a (h/new-conn)]
              (h/build! a (fn [_]
                              (-> (db/empty-db {:name {:db/index true}
                                                :age  {:db/index true}} {})
                                  (h/db-with [{:db/id 1 :name "Ivan"   :age 15}
                                              {:db/id 2 :name "Oleg"   :age 20}
                                              {:db/id 3 :name "Sergey" :age 7}
                                              {:db/id 4 :name "Pavel"  :age 45}
                                              {:db/id 5 :name "Petr"   :age 20}]))))
              (is (= ($ (mapv dvec (h/index-range (:db @a) :name "Pe" "S")))
                     [[5 :name "Petr"]]))
              (is (= ($ (mapv dvec (h/index-range (:db @a) :name "O" "Sergey")))
                     [[2 :name "Oleg"]
                      [4 :name "Pavel"]
                      [5 :name "Petr"]
                      [3 :name "Sergey"]]))

              (is (= ($ (mapv dvec (h/index-range (:db @a) :name nil "P")))
                     [[1 :name "Ivan"]
                      [2 :name "Oleg"]]))
              (is (= ($ (mapv dvec (h/index-range (:db @a) :name "R" nil)))
                     [[3 :name "Sergey"]]))
              (is (= ($ (mapv dvec (h/index-range (:db @a) :name nil nil)))
                     [[1 :name "Ivan"]
                      [2 :name "Oleg"]
                      [4 :name "Pavel"]
                      [5 :name "Petr"]
                      [3 :name "Sergey"]]))

              (is (= ($ (mapv dvec (h/index-range (:db @a) :age 15 20)))
                     [[1 :age 15]
                      [2 :age 20]
                      [5 :age 20]]))
              (is (= ($ (mapv dvec (h/index-range (:db @a) :age 7 45)))
                     [[3 :age 7]
                      [1 :age 15]
                      [2 :age 20]
                      [5 :age 20]
                      [4 :age 45]]))
              (is (= ($ (mapv dvec (h/index-range (:db @a) :age 0 100)))
                     [[3 :age 7]
                      [1 :age 15]
                      [2 :age 20]
                      [5 :age 20]
                      [4 :age 45]]))

              (is (thrown-msg? "Attribute :alias should be marked as :db/index true"
                               ($ (h/index-range (:db @a) :alias "e" "u"))))))
