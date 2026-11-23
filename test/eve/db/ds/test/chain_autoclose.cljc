(ns eve.db.ds.test.chain-autoclose
  "Chain auto-close for conn-less values (John's §8 ruling,
   2026-11-20): explicit release is the rule, and db-with chains
   auto-close their superseded intermediates — the with-open
   strategy. A chain DB is released in the swap! that builds its
   grandchild, iff it has exactly one chain child and was never
   keep!'d; a forked DB (two or more chain children) and a keep!'d
   DB are never auto-released. Explicit conns are out of scope and
   keep the legacy keep-everything behavior.

   Portable (.cljc, public API only) so the browser u3 suite can
   carry it once that harness arm is on this branch."
  (:require
    [clojure.test :as t :refer [is deftest testing]]
    [eve.db.ds.core :as d]))

(defn- resolves? [h]
  (try (d/datoms h :eavt) true
       (catch #?(:clj Throwable :cljs :default :lpy python/BaseException :default Throwable) _ false)))

(defn- not-kept? [h]
  (try (d/datoms h :eavt) false
       (catch #?(:clj Throwable :cljs :default :lpy python/BaseException :default Throwable) e
         (= :eve.db.ds/db-not-kept (:error (ex-data e))))))

(defn- names [h]
  (set (map :v (filter #(= :name (:a %)) (d/datoms h :eavt)))))

(deftest test-linear-chain-auto-close
  (let [h0 (d/empty-db)
        h1 (d/db-with h0 [[:db/add 1 :name "Ivan"]])
        ;; one link through with (the report form) — same chain
        h2 (:db-after (d/with h1 [[:db/add 2 :name "Petr"]]))
        h3 (d/db-with h2 [[:db/add 3 :name "Oleg"]])]
    (testing "the head resolves with the full chain's data"
      (is (resolves? h3))
      (is (= #{"Ivan" "Petr" "Oleg"} (names h3))))
    (testing "the head's parent still resolves (it is released when
              the head itself advances, not before)"
      (is (resolves? h2))
      (is (= #{"Ivan" "Petr"} (names h2))))
    (testing "an intermediate two steps superseded is auto-released:
              reads through it throw db-not-kept"
      (is (not-kept? h1)))
    (testing "the seed is the conn's current DB and resolves"
      (is (resolves? h0)))
    (testing "advancing the head releases its parent in turn"
      (let [h4 (d/db-with h3 [[:db/add 4 :name "Anna"]])]
        (is (resolves? h4))
        (is (not-kept? h2))
        (is (resolves? h3))))))

(deftest test-kept-intermediate-survives
  (let [h0 (d/empty-db)
        h1 (d/db-with h0 [[:db/add 1 :name "Ivan"]])
        h2 (d/db-with h1 [[:db/add 2 :name "Petr"]])
        _  (d/keep! h1) ;; handle arity: no conn in scope
        h3 (d/db-with h2 [[:db/add 3 :name "Oleg"]])
        h4 (d/db-with h3 [[:db/add 4 :name "Anna"]])]
    (testing "the keep!'d intermediate still resolves after the
              chain has moved well past it"
      (is (resolves? h1))
      (is (= #{"Ivan"} (names h1))))
    (testing "its un-kept neighbour was auto-released on schedule"
      (is (not-kept? h2)))
    (testing "the head is unaffected"
      (is (resolves? h4))
      (is (= #{"Ivan" "Petr" "Oleg" "Anna"} (names h4))))))

(deftest test-fork-is-not-stranded
  (let [b  (-> (d/empty-db)
             (d/db-with [[:db/add 1 :name "Ivan"]]))
        fa (d/db-with b [[:db/add 2 :name "A"]])
        fb (d/db-with b [[:db/add 3 :name "B"]])]
    (testing "the two successors are distinct DBs (no tx collision:
              each handle reads its own fork's data)"
      (is (not= (:tx fa) (:tx fb)))
      (is (= #{"Ivan" "A"} (names fa)))
      (is (= #{"Ivan" "B"} (names fb))))
    (testing "advancing one successor strands neither the other
              successor nor the forked base"
      (let [fa2 (d/db-with fa [[:db/add 4 :name "A2"]])]
        (is (resolves? fa2))
        (is (resolves? fb))
        (is (= #{"Ivan" "B"} (names fb)))
        (is (resolves? b))
        (testing "and advancing it again releases only that
                  successor — the fork and the base still resolve"
          (let [fa3 (d/db-with fa2 [[:db/add 5 :name "A3"]])]
            (is (resolves? fa3))
            (is (not-kept? fa))
            (is (resolves? fb))
            (is (resolves? b))))))))

(deftest test-release!-on-a-chain
  (let [h0 (d/empty-db)
        h1 (d/db-with h0 [[:db/add 1 :name "Ivan"]])
        h2 (d/db-with h1 [[:db/add 2 :name "Petr"]])]
    (testing "release! (handle arity) removes a chain DB at once,
              ahead of any auto-close schedule"
      (is (resolves? h1))
      (d/release! h1)
      (is (not-kept? h1)))
    (testing "the rest of the chain is untouched"
      (is (resolves? h2))
      (is (= #{"Ivan" "Petr"} (names h2))))))

(deftest test-explicit-conn-db-with-unchanged
  (let [conn (d/create-conn)
        _    (d/transact! conn [[:db/add 1 :name "Ivan"]])
        h1   @conn
        h2   (d/db-with h1 [[:db/add 2 :name "Petr"]])
        h3   (d/db-with h2 [[:db/add 3 :name "Oleg"]])
        h4   (d/db-with h3 [[:db/add 4 :name "Anna"]])]
    (testing "on an explicit conn, db-with keeps every intermediate
              — no auto-close, however far the chain advances"
      (is (resolves? h2))
      (is (resolves? h3))
      (is (resolves? h4))
      (is (= #{"Ivan" "Petr"} (names h2))))
    (testing "release! [conn db] behaves as before"
      (d/release! conn h2)
      (is (not-kept? h2))
      (is (resolves? h3)))
    (testing "keep! [conn db] behaves as before"
      (d/keep! conn h3)
      (is (resolves? h3)))))
