(ns eve.db.ds.pull-api-test
  "U4 slice: port of upstream eve.db.ds.test.pull-api (DataScript 1.8.1),
   adapted like entity-test: d/pull -> eve.db.ds.pull-api/pull,
   d/datom -> db/datom. Execution shape (post-U2, plan v2 §1): DBs
   are built inside swap! on Eve atoms (u4-support/init-conn and
   build!) and every pull runs inside $ with the DB bound once.
   One leg is PARKED: the serializable round-trip in
   test-pull-other-dbs exercises eve.db.ds.serialize (U5's file;
   the baseline serialize ns requires the deleted eve.db.ds.storage)
   — it is commented out below and re-enters when U5 lands."
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    [eve.alpha :as eve :refer [$]]
    [eve.db.ds.pull-api :as dp]
    [eve.db.ds.db :as db]
    [eve.db.ds.u4-support :as sup]))

(def ^:private test-schema
  {:name   {:db/unique :db.unique/identity}
   :aka    {:db/cardinality :db.cardinality/many}
   :child  {:db/cardinality :db.cardinality/many
            :db/valueType :db.type/ref}
   :friend {:db/cardinality :db.cardinality/many
            :db/valueType :db.type/ref}
   :enemy  {:db/cardinality :db.cardinality/many
            :db/valueType :db.type/ref}
   :father {:db/valueType :db.type/ref}

   :part   {:db/valueType :db.type/ref
            :db/isComponent true
            :db/cardinality :db.cardinality/many}
   :spec   {:db/valueType :db.type/ref
            :db/isComponent true
            :db/cardinality :db.cardinality/one}})

(def test-datom-vecs
  [[1 :name  "Petr"]
     [1 :aka   "Devil"]
     [1 :aka   "Tupen"]
     [2 :name  "David"]
     [3 :name  "Thomas"]
     [4 :name  "Lucy"]
     [5 :name  "Elizabeth"]
     [6 :name  "Matthew"]
     [7 :name  "Eunan"]
     [8 :name  "Kerri"]
     [9 :name  "Rebecca"]
     [1 :child 2]
     [1 :child 3]
     [2 :father 1]
     [3 :father 1]
     [6 :father 3]
     [10 :name  "Part A"]
     [11 :name  "Part A.A"]
     [10 :part 11]
     [12 :name  "Part A.A.A"]
     [11 :part 12]
     [13 :name  "Part A.A.A.A"]
     [12 :part 13]
     [14 :name  "Part A.A.A.B"]
     [12 :part 14]
     [15 :name  "Part A.B"]
     [10 :part 15]
     [16 :name  "Part A.B.A"]
     [15 :part 16]
     [17 :name  "Part A.B.A.A"]
     [16 :part 17]
     [18 :name  "Part A.B.A.B"]
     [16 :part 18]])

(defn- datoms-of
  "Construct Datom values from [e a v] vectors. Call only inside
   swap! (u4-support/init-conn's datoms-fn): datoms are
   domain-bound and cannot cross atoms."
  [vecs]
  (map (fn [[e a v]] (db/datom e a v)) vecs))

(defn- test-datoms []
  (datoms-of test-datom-vecs))

(def ^:private *test-conn
  ;; built at ns load, outside any region: a delay forced inside a
  ;; $ would run the building swap! under the read region and the
  ;; resulting DB would not answer db/db? inside later regions
  ;; (measured, u4-logs/pull-exec1).
  (sup/init-conn test-datoms test-schema))

(defn- tdb
  "The shared test DB. Call only inside $."
  []
  (:db @*test-conn))

(deftest test-pull-attr-spec
  ($ (let [db (tdb)]
       (is (= {:name "Petr" :aka ["Devil" "Tupen"]}
             (dp/pull db '[:name :aka] 1)))

       (is (= {:name "Matthew" :father {:db/id 3} :db/id 6}
             (dp/pull db '[:name :father :db/id] 6)))

       (is (= [{:name "Petr"} {:name "Elizabeth"}
               {:name "Eunan"} {:name "Rebecca"}]
             (dp/pull-many db '[:name] [1 5 7 9]))))))

(deftest test-pull-reverse-attr-spec
  ($ (let [db (tdb)]
       (is (= {:name "David" :_child [{:db/id 1}]}
             (dp/pull db '[:name :_child] 2)))

       (is (= {:name "David" :_child [{:name "Petr"}]}
             (dp/pull db '[:name {:_child [:name]}] 2)))

       (testing "Reverse non-component references yield collections"
         (is (= {:name "Thomas" :_father [{:db/id 6}]}
               (dp/pull db '[:name :_father] 3)))

         (is (= {:name "Petr" :_father [{:db/id 2} {:db/id 3}]}
               (dp/pull db '[:name :_father] 1)))

         (is (= {:name "Thomas" :_father [{:name "Matthew"}]}
               (dp/pull db '[:name {:_father [:name]}] 3)))

         (is (= {:name "Petr" :_father [{:name "David"} {:name "Thomas"}]}
               (dp/pull db '[:name {:_father [:name]}] 1))))

       (testing "Multiple reverse refs issue-412"
         (is (= {:name "Petr" :_father [{:db/id 2} {:db/id 3}]}
               (dp/pull db '[:name :_father :_child] 1)))))))

(deftest test-pull-component-attr
  (let [parts {:name "Part A",
               :part
               [{:db/id 11
                 :name "Part A.A",
                 :part
                 [{:db/id 12
                   :name "Part A.A.A",
                   :part
                   [{:db/id 13 :name "Part A.A.A.A"}
                    {:db/id 14 :name "Part A.A.A.B"}]}]}
                {:db/id 15
                 :name "Part A.B",
                 :part
                 [{:db/id 16
                   :name "Part A.B.A",
                   :part
                   [{:db/id 17 :name "Part A.B.A.A"}
                    {:db/id 18 :name "Part A.B.A.B"}]}]}]}
        rpart (update-in parts [:part 0 :part 0 :part]
                #(into [{:db/id 10
                         :name "Part A",
                         :part [{:db/id 11}
                                {:name "Part A.B",
                                 :part [{:name "Part A.B.A",
                                         :part [{:name "Part A.B.A.A", :db/id 17}
                                                {:name "Part A.B.A.B", :db/id 18}],
                                         :db/id 16}],
                                 :db/id 15}]}] %))
        rec-conn (sup/init-conn
                   #(concat (test-datoms) [(db/datom 12 :part 10)])
                   test-schema)]

    ($ (let [db (tdb)
             recdb (:db @rec-conn)]
         (testing "Component entities are expanded recursively"
           (is (= parts (dp/pull db '[:name :part] 10))))

         (testing "Reverse component references yield a single result"
           (is (= {:name "Part A.A" :_part {:db/id 10}}
                 (dp/pull db [:name :_part] 11)))

           (is (= {:name "Part A.A" :_part {:name "Part A"}}
                 (dp/pull db [:name {:_part [:name]}] 11))))

         (testing "Like explicit recursion, expansion will not allow loops"
           (is (= rpart (dp/pull recdb '[:name :part] 10))))

         (testing "Reverse recursive component issue-411"
           (is (= {:name "Part A.A.A.B" :_part {:name "Part A.A.A" :_part {:name "Part A.A" :_part {:name "Part A"}}}}
                 (dp/pull db '[:name {:_part ...}] 14)))
           (is (= {:name "Part A.A.A.B" :_part {:name "Part A.A.A" :_part {:name "Part A.A"}}}
                 (dp/pull db '[:name {:_part 2}] 14))))))))

(deftest test-pull-wildcard
  ($ (let [db (tdb)]
       (is (= {:db/id 1
               :name "Petr"
               :aka ["Devil" "Tupen"]
               :child [{:db/id 2} {:db/id 3}]}
             (dp/pull db '[*] 1)))

       (is (= {:db/id 2 :name "David" :_child [{:db/id 1}] :father {:db/id 1}}
             (dp/pull db '[* :_child] 2)))

       (is (= {:aka ["Devil" "Tupen"], :child [{:db/id 2} {:db/id 3}], :name "Petr", :db/id 1}
             (dp/pull db '[:name *] 1)))

       (is (= {:aka ["Devil" "Tupen"], :child [{:db/id 2} {:db/id 3}], :name "Petr", :db/id 1}
             (dp/pull db '[:aka :name *] 1)))

       (is (= {:aka ["Devil" "Tupen"], :child [{:db/id 2} {:db/id 3}], :name "Petr", :db/id 1}
             (dp/pull db '[:aka :child :name *] 1)))

       (is (= {:alias ["Devil" "Tupen"], :child [{:db/id 2} {:db/id 3}], :first-name "Petr", :db/id 1}
             (dp/pull db '[[:aka :as :alias] [:name :as :first-name] *] 1)))

       (is (= {:db/id 1
               :name "Petr"
               :aka ["Devil" "Tupen"]
               :child [{:db/id 2
                        :father {:db/id 1}
                        :name "David"}
                       {:db/id 3
                        :father {:db/id 1}
                        :name "Thomas"}]}
             (dp/pull db '[* {:child ...}] 1))))))

(deftest test-pull-limit
  (let [a (sup/init-conn
            #(concat
               (test-datoms)
               [(db/datom 4 :friend 5)
                (db/datom 4 :friend 6)
                (db/datom 4 :friend 7)
                (db/datom 4 :friend 8)]
               (for [idx (range 2000)]
                 (db/datom 8 :aka (str "aka-" idx))))
            test-schema)]
    ($ (let [db (:db @a)]
         (testing "Without an explicit limit, the default is 1000"
           (is (= 1000 (->> (dp/pull db '[:aka] 8) :aka count))))

         (testing "Explicit limit can reduce the default"
           (is (= 500 (->> (dp/pull db '[(limit :aka 500)] 8) :aka count)))
           (is (= 500 (->> (dp/pull db '[[:aka :limit 500]] 8) :aka count))))

         (testing "Explicit limit can increase the default"
           (is (= 1500 (->> (dp/pull db '[(limit :aka 1500)] 8) :aka count))))

         (testing "A nil limit produces unlimited results"
           (is (= 2000 (->> (dp/pull db '[(limit :aka nil)] 8) :aka count))))

         (testing "Limits can be used as map specification keys"
           (is (= {:name "Lucy"
                   :friend [{:name "Elizabeth"} {:name "Matthew"}]}
                 (dp/pull db '[:name {(limit :friend 2) [:name]}] 4))))))))

(deftest test-pull-default
  ($ (let [db (tdb)]
       (testing "Empty results return nil"
         (is (nil? (dp/pull db '[:foo] 1))))

       (testing "A default can be used to replace nil results"
         (is (= {:foo "bar"}
               (dp/pull db '[(default :foo "bar")] 1)))
         (is (= {:foo "bar"}
               (dp/pull db '[[:foo :default "bar"]] 1)))
         (is (= {:foo false}
               (dp/pull db '[[:foo :default false]] 1)))
         (is (= {:bar false}
               (dp/pull db '[[:foo :as :bar :default false]] 1))))

       (testing "default does not override results"
         (is (= {:name "Petr", :aka  ["Devil" "Tupen"]
                 :child [{:name "David", :aka "[aka]", :child "[child]"}
                         {:name "Thomas", :aka "[aka]", :child "[child]"}]}
               (dp/pull db
                 '[[:name :default "[name]"]
                   [:aka :default "[aka]"]
                   {[:child :default "[child]"] ...}]
                 1)))
         (is (= {:name "David", :aka  "[aka]", :child "[child]"}
               (dp/pull db
                 '[[:name :default "[name]"]
                   [:aka :default "[aka]"]
                   {[:child :default "[child]"] ...}]
                 2))))

       (testing "Ref default"
         (is (= {:child 1 :db/id 2}
               (dp/pull db '[:db/id [:child :default 1]] 2)))
         (is (= {:_child 2 :db/id 1}
               (dp/pull db '[:db/id [:_child :default 2]] 1)))))))

(deftest test-pull-as
  ($ (is (= {"Name" "Petr", :alias ["Devil" "Tupen"]}
           (dp/pull (tdb) '[[:name :as "Name"] [:aka :as :alias]] 1)))))

(deftest test-pull-attr-with-opts
  ($ (is (= {"Name" "Nothing"}
           (dp/pull (tdb) '[[:x :as "Name" :default "Nothing"]] 1)))))

(deftest test-pull-map
  ($ (let [db (tdb)]
       (testing "Single attrs yield a map"
         (is (= {:name "Matthew" :father {:name "Thomas"}}
               (dp/pull db '[:name {:father [:name]}] 6))))

       (testing "Multi attrs yield a collection of maps"
         (is (= {:name "Petr" :child [{:name "David"}
                                      {:name "Thomas"}]}
               (dp/pull db '[:name {:child [:name]}] 1))))

       (testing "Missing attrs are dropped"
         (is (= {:name "Petr"}
               (dp/pull db '[:name {:father [:name]}] 1))))

       (testing "Non matching results are removed from collections"
         (is (= {:name "Petr"}
               (dp/pull db '[:name {:child [:foo]}] 1))))

       (testing "Map specs can override component expansion"
         (is (= {:name "Part A" :part [{:name "Part A.A"} {:name "Part A.B"}]}
               (dp/pull db '[:name {:part [:name]}] 10)))

         (is (= {:name "Part A" :part [{:name "Part A.A"} {:name "Part A.B"}]}
               (dp/pull db '[:name {:part 1}] 10)))))))

(deftest test-pull-recursion
  (let [a (sup/init-conn test-datoms test-schema)]
    (sup/advance! a [[:db/add 4 :friend 5]
                     [:db/add 5 :friend 6]
                     [:db/add 6 :friend 7]
                     [:db/add 7 :friend 8]
                     [:db/add 4 :enemy 6]
                     [:db/add 5 :enemy 7]
                     [:db/add 6 :enemy 8]
                     [:db/add 7 :enemy 4]])
    (let [friends {:db/id 4
                   :name "Lucy"
                   :friend
                   [{:db/id 5
                     :name "Elizabeth"
                     :friend
                     [{:db/id 6
                       :name "Matthew"
                       :friend
                       [{:db/id 7
                         :name "Eunan"
                         :friend
                         [{:db/id 8
                           :name "Kerri"}]}]}]}]}
          enemies {:db/id 4
                   :name "Lucy"
                   :friend
                   [{:db/id 5
                     :name "Elizabeth"
                     :friend
                     [{:db/id 6
                       :name "Matthew"
                       :enemy [{:db/id 8 :name "Kerri"}]}]
                     :enemy
                     [{:db/id 7
                       :name "Eunan"
                       :friend
                       [{:db/id 8
                         :name "Kerri"}]
                       :enemy
                       [{:db/id 4
                         :name "Lucy"
                         :friend [{:db/id 5}]}]}]}]
                   :enemy
                   [{:db/id 6
                     :name "Matthew"
                     :friend
                     [{:db/id 7
                       :name "Eunan"
                       :friend
                       [{:db/id 8
                         :name "Kerri"}]
                       :enemy [{:db/id 4
                                :name "Lucy"
                                :enemy [{:db/id 6}]
                                :friend [{:db/id 5
                                          :name "Elizabeth"
                                          :enemy [{:db/id 7}],
                                          :friend [{:db/id 6}]}]}]}]
                     :enemy
                     [{:db/id 8
                       :name "Kerri"}]}]}]
      ($ (let [db (:db @a)]
           (testing "Infinite recursion"
             (is (= friends (dp/pull db '[:db/id :name {:friend ...}] 4))))

           (testing "Multiple recursion specs in one pattern"
             (is (= enemies (dp/pull db '[:db/id :name {:friend 2 :enemy 2}] 4))))

           (testing "Reverse recursion"
             (is (= {:db/id 8, :_friend [{:db/id 7, :_friend [{:db/id 6, :_friend [{:db/id 5, :_friend [{:db/id 4}]}]}]}]}
                   (dp/pull db '[:db/id {:_friend ...}] 8)))
             (is (= {:db/id 8, :_friend [{:db/id 7, :_friend [{:db/id 6}]}]}
                   (dp/pull db '[:db/id {:_friend 2}] 8))))

           (testing "Cycles are handled by returning only the :db/id of entities which have been seen before"
             (sup/advance! a [[:db/add 8 :friend 4]])
             (let [db (:db @a)]
               (is (= (update-in friends (take 8 (cycle [:friend 0]))
                        assoc :friend [{:db/id 4 :name "Lucy" :friend [{:db/id 5}]}])
                     (dp/pull db '[:db/id :name {:friend ...}] 4)))))

           (testing "Seen ids are tracked independently for different branches"
             (let [a2 (sup/new-conn)]
               (sup/build! a2 {:friend {:db/valueType :db.type/ref}
                               :enemy  {:db/valueType :db.type/ref}}
                 [[{:db/id 1 :name "1" :friend 2 :enemy 2}
                   {:db/id 2 :name "2"}]])
               (let [db (:db @a2)]
                 (is (= {:name "1" :friend {:name "2"} :enemy {:name "2"}}
                       (dp/pull db '[:name {:friend [:name], :enemy [:name]}] 1)))))))))))

(deftest test-dual-recursion
  (let [a (sup/new-conn)]
    (sup/build! a {:friend {:db/valueType :db.type/ref}
                   :enemy {:db/valueType :db.type/ref}}
      [[{:db/id 1 :friend 2}
        {:db/id 2 :enemy 3}
        {:db/id 3 :friend 4}
        {:db/id 4 :enemy 5}
        {:db/id 5 :friend 6}
        {:db/id 6 :enemy 7}]])
    ($ (let [recursion-db (:db @a)]
         (is (= {:db/id 1 :friend {:db/id 2}}
               (dp/pull recursion-db '[:db/id {:friend ...}] 1)))
         (is (= {:db/id 1 :friend {:db/id 2 :enemy {:db/id 3}}}
               (dp/pull recursion-db '[:db/id {:friend 1 :enemy 1}] 1)))
         (is (= {:db/id 1 :friend {:db/id 2 :enemy {:db/id 3 :friend {:db/id 4}}}}
               (dp/pull recursion-db '[:db/id {:friend 2 :enemy 1}] 1)))
         (is (= {:db/id 1 :friend {:db/id 2 :enemy {:db/id 3 :friend {:db/id 4 :enemy {:db/id 5}}}}}
               (dp/pull recursion-db '[:db/id {:friend 2 :enemy 2}] 1))))))

  (let [a (sup/new-conn)]
    (sup/build! a {:part {:db/valueType :db.type/ref}
                   :spec {:db/valueType :db.type/ref}}
      [])
    (sup/advance! a [[:db/add 1 :part 2]
                     [:db/add 2 :part 3]
                     [:db/add 3 :part 1]
                     [:db/add 1 :spec 2]
                     [:db/add 2 :spec 1]])
    ($ (let [db (:db @a)]
         (is (= (dp/pull db '[:db/id {:part ...} {:spec ...}] 1)
               {:db/id 1,
                :spec {:db/id 2
                       :spec {:db/id 1,
                              :spec {:db/id 2}, :part {:db/id 2}}
                       :part {:db/id 3,
                              :part {:db/id 1,
                                     :spec {:db/id 2},
                                     :part {:db/id 2}}}}
                :part {:db/id 2
                       :spec {:db/id 1, :spec {:db/id 2}, :part {:db/id 2}}
                       :part {:db/id 3,
                              :part {:db/id 1,
                                     :spec {:db/id 2},
                                     :part {:db/id 2}}}}}))))))

(deftest test-deep-recursion
  (let [start 100
        depth 3000
        a     (sup/init-conn
                #(concat
                   (test-datoms)
                   [(db/datom start :name (str "Person-" start))]
                   (mapcat
                     (fn [idx]
                       [(db/datom idx :name (str "Person-" idx))
                        (db/datom (dec idx) :friend idx)])
                     (range (inc start) depth)))
                test-schema)]
    ($ (let [db     (:db @a)
             pulled (dp/pull db '[:name {:friend ...}] start)
             path   (->> [:friend 0]
                      (repeat (dec (- depth start)))
                      (into [] cat))]
         (is (= (str "Person-" (dec depth))
               (:name (get-in pulled path))))))))

; issue-430
(deftest test-component-reverse
  (let [schema {:ref  {:db/valueType :db.type/ref
                       :db/isComponent true}}
        a (sup/new-conn)]
    (sup/build! a schema
      [[{:name "1"
         :ref {:name "2"
               :ref {:name "3"}}}]])
    ($ (let [db (:db @a)]
         (is (= {:name "1", :ref {:name "2", :ref {:name "3", :_ref {:name "2"}}}}
               (dp/pull db
                 [:name {:ref [:name {:ref [:name {:_ref [:name]}]}]}]
                 1)))))))

(deftest test-lookup-ref-pull
  ($ (let [db (tdb)]
       (is (= {:name "Petr" :aka ["Devil" "Tupen"]}
             (dp/pull db '[:name :aka] [:name "Petr"])))
       (is (= nil
             (dp/pull db '[:name :aka] [:name "NotInDatabase"])))
       (is (= [nil {:aka ["Devil" "Tupen"]} nil nil nil]
             (dp/pull-many db
               '[:aka]
               [[:name "Elizabeth"]
                [:name "Petr"]
                [:name "Eunan"]
                [:name "Rebecca"]
                [:name "Unknown"]])))
       (is (nil? (dp/pull db '[*] [:name "No such name"]))))))

(deftest test-xform
  ($ (let [db (tdb)]
       (is (= {:db/id [1]
               :name  ["Petr"]
               :aka   [["Devil" "Tupen"]]
               :child [[{:db/id [2], :name ["David"],  :aka [nil], :child [nil]}
                        {:db/id [3], :name ["Thomas"], :aka [nil], :child [nil]}]]}
             (dp/pull db
               [[:db/id :xform vector]
                [:name :xform vector]
                [:aka :xform vector]
                {[:child :xform vector] '...}]
               1)))

       (testing ":xform on cardinality/one ref issue-455"
         (is (= {:name "David" :father "Petr"}
               (dp/pull db [:name {[:father :xform #(:name %)] ['*]}] 2))))

       (testing ":xform on reverse ref"
         (is (= {:name "Petr" :_father ["David" "Thomas"]}
               (dp/pull db [:name {[:_father :xform #(mapv :name %)] [:name]}] 1))))

       (testing ":xform on reverse component ref"
         (is (= {:name "Part A.A" :_part "Part A"}
               (dp/pull db [:name {[:_part :xform #(:name %)] [:name]}] 11))))

       (testing "missing attrs are processed by xform"
         (is (= {:normal [nil]
                 :aka [nil]
                 :child [nil]}
               (dp/pull db
                 '[[:normal :xform vector]
                   [:aka :xform vector]
                   {[:child :xform vector] ...}]
                 2))))
       (testing "default takes precedence"
         (is (= {:unknown "[unknown]"}
               (dp/pull db '[[:unknown :default "[unknown]" :xform vector]] 1)))))))

(deftest test-visitor
  ($ (let [db (tdb)
           *trace (volatile! nil)
           opts   {:visitor (fn [k e a v] (vswap! *trace conj [k e a v]))}
           test-fn (fn [pattern id]
                     (vreset! *trace [])
                     (dp/pull db pattern id opts)
                     @*trace)]
       (is (= [[:db.pull/attr 1 :name nil]]
             (test-fn [:name] 1)))

       (testing "multival"
         (is (= [[:db.pull/attr 1 :aka  nil]
                 [:db.pull/attr 1 :name nil]]
               (test-fn [:name :aka] 1))))

       (testing ":db/id is ignored"
         (is (= [] (test-fn [:db/id] 1)))
         (is (= [[:db.pull/attr 1 :name nil]]
               (test-fn [:db/id :name] 1))))

       (testing "wildcard"
         (is (= [[:db.pull/wildcard 1 nil    nil]
                 [:db.pull/attr     1 :aka   nil]
                 [:db.pull/attr     1 :child nil]
                 [:db.pull/attr     1 :name  nil]]
               (test-fn ['*] 1))))

       (testing "missing"
         (is (= [[:db.pull/attr 1 :missing nil]]
               (test-fn [:missing] 1)))
         (is (= [[:db.pull/wildcard 1 nil      nil]
                 [:db.pull/attr     1 :aka     nil]
                 [:db.pull/attr     1 :child   nil]
                 [:db.pull/attr     1 :missing nil]
                 [:db.pull/attr     1 :name    nil]]
               (test-fn ['* :missing] 1))))

       (testing "default"
         (is (= [[:db.pull/attr 1 :missing nil]]
               (test-fn [[:missing :default 10]] 1)))
         (is (= [[:db.pull/attr 2 :child nil]]
               (test-fn [[:child :default 10]] 2))))

       (testing "recursion"
         (is (= [[:db.pull/attr 1 :child nil]]
               (test-fn [:child] 1)))
         (is (= [[:db.pull/attr 1 :child nil]
                 [:db.pull/attr 2 :name  nil]
                 [:db.pull/attr 3 :name  nil]]
               (test-fn [{:child [:name]}] 1)))
         (is (= [[:db.pull/attr 1 :child nil]
                 [:db.pull/attr 2 :child nil]
                 [:db.pull/attr 2 :name  nil]
                 [:db.pull/attr 3 :child nil]
                 [:db.pull/attr 3 :name  nil]
                 [:db.pull/attr 1 :name  nil]]
               (test-fn [:name {:child '...}] 1))))

       (testing "reverse"
         (is (= [[:db.pull/attr    2   :name  nil]
                 [:db.pull/reverse nil :child 2]]
               (test-fn [:name :_child] 2)))))))

(deftest test-pull-other-dbs
  ($ (let [db (sup/filter-db (tdb) (fn [_ datom] (not= "Tupen" (:v datom))))]
       (is (= {:name "Petr" :aka ["Devil"]}
             (dp/pull db '[:name :aka] 1)))))
  ;; PARKED (U5): the serializable round-trip leg —
  ;; (d/serializable @*test-db) -> pr-str -> read-string ->
  ;; d/from-serializable, then the same pull assertion as the legs
  ;; around it. eve.db.ds.serialize is U5's file and does not load at
  ;; baseline (requires the deleted eve.db.ds.storage).
  (let [vecs ($ (mapv (fn [d] [(:e d) (:a d) (:v d) (:tx d)])
                  (db/-datoms (tdb) :eavt nil nil nil nil)))
        a (sup/init-conn #(map (fn [[e a v tx]] (db/datom e a v tx)) vecs) test-schema)]
    ($ (let [db (:db @a)]
         (is (= {:name "Petr" :aka ["Devil" "Tupen"]}
               (dp/pull db '[:name :aka] 1)))))))
