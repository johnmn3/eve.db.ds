(ns eve.db.ds.bset-stress-test
    "Upstream me.tonsky.persistent-sorted-set 0.3.1 test/stress, ported
   to eve.db.ds.bset through the same swap!/$ harness as
   eve.db.ds.bset-test (see that ns for the adaptation rules).

   Iteration counts are dynamic vars (*iters*, *rslice-iters*,
   *overflow-len*) defaulting to upstream's exact numbers; a smoke
   run binds them down (see eve.db.ds.bset-stress-smoke) — at the
   pinned Eve's measured per-write cost on a shared cell, the full
   counts are wall-clock-infeasible, and a bound-down run is reported
   as a smoke, never as the full suite.

   Excluded, named: the \"bulk\" build method (upstream's
   from-sorted-array — §5 has no bulk constructor), the \"lazy\"
   storage-roundtrip method (no storage in §5), and the transient
   disj legs (no transients in §5). Everything else is upstream's,
   including iteration counts."
    (:require [eve.alpha :as eve :refer [$]]
              [eve.db.ds.bset :as bset]
              [clojure.test :as t :refer [is deftest testing]]))

(def ^:private id-counter (atom 100000))

(defn- new-conn []
       (let [a (eve/atom {:id (keyword (str "u1-bset-stress-" (swap! id-counter inc)))} nil)]
            (swap! a (fn [_] (bset/empty)))
            a))

(defn- conj-all! [a cmp ks]
       (doseq [chunk (partition-all 5000 ks)]
              (swap! a (fn [root]
                           (reduce (fn [r k] (or (bset/conj r k cmp) r)) root chunk))))
       a)

(defn- disj-all! [a cmp ks]
       (doseq [chunk (partition-all 5000 ks)]
              (swap! a (fn [root]
                           (reduce (fn [r k] (or (bset/disj r k cmp) r)) root chunk))))
       a)

(defn- build [cmp ks] (conj-all! (new-conn) cmp ks))

(defn- all-vec [a cmp]
       ($ (vec (bset/slice @a nil nil cmp))))

(def ^:dynamic *iters* 100)
(def ^:dynamic *rslice-iters* 1000)
(def ^:dynamic *overflow-len* 4000000)

(defn- into-via-doseq [to from]
       (let [res (transient [])]
            (doseq [x from]
                   (conj! res x))
            (persistent! res)))

(deftest stresstest-btset
         (println "  testing stresstest-btset...")
         (dotimes [i *iters*]
                  (let [size      10000
                        xs        (vec (repeatedly (+ 1 (rand-int size)) #(rand-int size)))
                        xs-sorted (vec (distinct (sort xs)))
                        rm        (vec (repeatedly (rand-int (* size 5)) #(rand-nth xs)))
                        full-rm   (shuffle (concat xs rm))
                        xs-rm     (reduce disj (into (sorted-set) xs) rm)
                        a0        (build compare xs)
                        a1        (disj-all! (build compare xs) compare rm)
                        a3        (disj-all! (build compare xs) compare full-rm)]
                       (testing
                        (str "Iter:" (inc i)  "/" *iters*
                             " adds:" (str (count xs) " (" (count xs-sorted) " distinct),")
                             " removals:" (str (count rm) " (down to " (count xs-rm) ")"))
                        (testing "conj, seq"
                                 (is (= (all-vec a0 compare) xs-sorted)))
                        (testing "eq"
                                 (is (= (all-vec a0 compare) xs-sorted) xs-sorted))
                        (testing "count"
                                 (is (= ($ (bset/count @a0)) (count xs-sorted))))
                        (testing "doseq"
                                 (is (= ($ (into-via-doseq [] (bset/slice @a0 nil nil compare))) xs-sorted)))
                        (testing "disj"
                                 (is (= (all-vec a1 compare) (vec xs-rm)))
                                 (is (= ($ (bset/count @a1)) (count xs-rm)))
                                 (is (= (all-vec a1 compare) (vec xs-rm))))
                        (testing "full disj"
                                 (is (= (all-vec a3 compare) [])))))))

(deftest stresstest-slice
         (println "  testing stresstest-slice...")
         (dotimes [i *iters*]
                  (let [xs        (repeatedly (+ 1 (rand-int 20000)) #(rand-int 20000))
                        xs-sorted (distinct (sort xs))
                        [from to] (sort [(- 10000 (rand-int 20000)) (+ 10000 (rand-int 20000))])
                        expected  (filter #(<= from % to) xs-sorted)
                        a         (build compare xs)]
                       (testing
                        (str
                         "Iter: " (inc i) "/" *iters*
                         ", from:" (count xs-sorted) " elements"
                         ", down to:" (count expected))
                        (let [a2 (build compare (shuffle xs-sorted))]
                             (is (= ($ (vec (bset/rslice @a2 30000 -10 compare)))
                                    ($ (some-> (bset/rslice @a2 30000 -10 compare) rseq reverse vec)))))
                        (is (= ($ (vec (bset/slice @a from to compare))) (vec expected)))
                        (is (= ($ (vec (bset/slice @a from to compare))) (vec expected))) ;; checking IReduce on Iter
                        (is (= ($ (into-via-doseq [] (bset/slice @a from to compare))) (vec expected)))
                        (is (= ($ (vec (rseq (bset/slice @a from to compare)))) (reverse expected)))
                        (is (= ($ (vec (rseq (rseq (bset/slice @a from to compare))))) (vec expected)))))))

(deftest stresstest-rslice
         (println "  testing stresstest-rslice...")
         (dotimes [i *rslice-iters*]
                  (let [xs (vec (shuffle (range 0 (inc 3000))))
                        a  (build compare xs)]
                       (testing (str "Iter: " i "/" *rslice-iters*)
                                (is (= ($ (vec (bset/rslice @a (+ 3000 100) -100 compare)))
                                       ($ (some-> (bset/rslice @a (+ 3000 100) -100 compare) rseq reverse vec))))))))

(deftest stresstest-seek
         (println "  testing stresstest-seek...")
         (dotimes [i *iters*]
                  (let [xs        (repeatedly (inc (rand-int 20000)) #(rand-int 20000))
                        xs-sorted (distinct (sort xs))
                        seek-to   (rand-int 20000)
                        a         (build compare xs-sorted)]
                       (testing (str "Iter: " i "/" *iters* ", seek to " seek-to)
                                (is (= (seq (drop-while #(< % seek-to) xs-sorted))
                                       ($ (seq (bset/seek (bset/slice @a nil nil compare) seek-to compare)))))

                                (is (= (seq (drop-while #(< % 19999) xs-sorted))
                                       ($ (seq (bset/seek (bset/slice @a nil nil compare) 19999 compare)))))

                                (is (= (seq (reverse (take-while #(<= % seek-to) xs-sorted)))
                                       ($ (seq (bset/seek (bset/rslice @a nil nil compare) seek-to compare)))))

                                (is (= (seq (reverse (take-while #(<= % 1) xs-sorted)))
                                       ($ (seq (bset/seek (bset/rslice @a nil nil compare) 1 compare)))))))))

(deftest test-overflow
         (println "  testing test-overflow...")
         (let [len  *overflow-len*
               part (quot len 100)
               xss  (partition-all part (shuffle (range 0 len)))
               a    (new-conn)]
              (doseq [xs xss] (conj-all! a compare xs))
              (is (= 10 ($ (count (take 10 (bset/slice @a nil nil compare))))))))
