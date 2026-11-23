(ns eve.db.ds.db-test
    "U2 port of upstream eve.db.ds.test.db (test/upstream-ds), run
   against db-level fns through the u2-support harness.

   Named exclusions:
   - test-db-hash-cache — tests the baseline's mutable hash-cache
     mechanism itself (@(.-hash db)); plan v2 §3 removes the cache.
     Replaced by test-db-hash-recomputed, asserting the §3 semantics
     (hash recomputed from content: equal DBs hash equal, a transacted
     DB hashes differently, repeated hashes are stable)."
    (:require
     [clojure.data]
     [clojure.test :as t :refer [is are deftest testing]]
     [eve.alpha :as eve :refer [$]]
     [eve.db.ds.db :as db :refer [defrecord-updatable]]
     [eve.db.ds.u2-support :as h]
     [eve.db.ds.util :as util]))

;;
;; verify that defrecord-updatable works with compiler/core macro configuration
;; define dummy class which redefines hash, could produce either
;; compiler or runtime error
;;
(defrecord-updatable HashBeef [x]
                     #?@(:cljs [IHash                (-hash  [hb] 0xBEEF)]
                         :clj  [clojure.lang.IHashEq (hasheq [hb] 0xBEEF)]))

(deftest test-defrecord-updatable
         (is (= 0xBEEF (-> (map->HashBeef {:x :ignored}) hash))))

(deftest test-db-hash-recomputed
         (let [a (h/new-conn)
               b (h/new-conn)]
              (h/build! a (fn [_] (-> (db/empty-db {} {})
                                      (h/db-with [[:db/add 1 :name "Petr"]]))))
              (h/build! b (fn [_] (-> (db/empty-db {} {})
                                      (h/db-with [[:db/add 1 :name "Petr"]]))))
              (is (= ($ (hash (:db @a))) ($ (hash (:db @b)))))
              (is (= ($ (hash (:db @a))) ($ (hash (:db @a)))))
              (h/build! b (fn [db] (h/db-with db [[:db/add 2 :name "Ivan"]])))
              (is (not= ($ (hash (:db @a))) ($ (hash (:db @b)))))))

(defn- now []
       #?(:clj  (System/currentTimeMillis)
          :cljs (.getTime (js/Date.))))

(deftest test-uuid
         (let [now-ms (loop []
                            (let [ts (now)]
                                 (if (> (mod ts 1000) 900) ;; sleeping over end of a second
                                     (recur)
                                     ts)))
               now    (int (/ now-ms 1000))]
              (is (= (* 1000 now) (util/squuid-time-millis (util/squuid))))
              (is (not= (util/squuid) (util/squuid)))
              (is (= (subs (str (util/squuid)) 0 8)
                     (subs (str (util/squuid)) 0 8)))))

(deftest test-diff
         (let [a (h/new-conn)]
              (swap! a (fn [_] {:db1 (-> (db/empty-db {} {})
                                         (h/db-with [{:a 1 :b 2 :c 4} {:a 1}]))
                                :db2 (-> (db/empty-db {} {})
                                         (h/db-with [{:b 3 :d 5}])
                                         (h/db-with [{:db/id 1 :a 1}]))}))
              (is (= [[(db/datom 1 :b 2) (db/datom 1 :c 4) (db/datom 2 :a 1)]
                      [(db/datom 1 :b 3) (db/datom 1 :d 5)]
                      [(db/datom 1 :a 1)]]
                     ($ (clojure.data/diff (:db1 @a) (:db2 @a)))))))
