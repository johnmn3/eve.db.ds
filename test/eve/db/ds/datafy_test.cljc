(ns eve.db.ds.datafy-test
  "U4 slice: port of upstream eve.db.ds.test.datafy (DataScript 1.8.1),
   adapted like entity-test: the DB is built inside swap! on a test
   atom (u4-support/build!) and navigation runs inside $, entities
   from eve.db.ds.impl.entity; eve.db.ds.datafy required for its
   extend-protocol on Entity."
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    [eve.alpha :as eve :refer [$]]
    [eve.db.ds.datafy :as datafy]
    [eve.db.ds.u4-support :as sup]
    [clojure.core.protocols :as cp]
    [eve.db.ds.impl.entity :as e]))

(defn- test-conn []
  (let [a (sup/new-conn)]
    (sup/build! a {:ref {:db/valueType :db.type/ref}
                   :namespace/ref {:db/valueType :db.type/ref}
                   :many/ref {:db/valueType :db.type/ref
                              :db/cardinality :db.cardinality/many}}
      [[{:db/id 1 :name "Parent1"}
        {:db/id 2 :name "Child1" :ref 1 :namespace/ref 1}
        {:db/id 3 :name "GrandChild1" :ref 2 :namespace/ref 2}
        {:db/id 4 :name "Master" :many/ref [1 2 3]}]])
    a))

(defn- nav [coll k]
  (cp/nav coll k (coll k)))

(defn d+n
  "Helper function to datafy/navigate for a path"
  [coll [k & ks]]
  (if (nil? k)
    coll
    (d+n (nav (cp/datafy coll) k)
      ks)))

(deftest test-navigation
  (let [a (test-conn)]
    ($ (let [db (:db @a)
             entity (e/entity db 3)]
         (is (= 2 (:db/id (d+n entity [:ref]))))
         (is (= 2 (:db/id (d+n entity [:namespace/ref]))))
         (is (= 1 (:db/id (d+n entity [:ref :namespace/ref]))))
         (is (= 3 (:db/id (d+n entity [:namespace/ref :ref :_ref 0 :namespace/_ref 0]))))
         (is (= #{1 2 3} (set (map :db/id (d+n entity [:many/_ref 0 :many/ref])))))))))
