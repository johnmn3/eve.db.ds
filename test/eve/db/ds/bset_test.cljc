(ns eve.db.ds.bset-test
  "Upstream me.tonsky.persistent-sorted-set 0.3.1 tests (test/core and
   test/small — the version DataScript 1.8.1 pins) ported to
   eve.db.ds.bset and run through the swap!/$ harness of plan v2 §5:
   the set under test lives in an Eve atom in an in-memory domain;
   every mutation is a swap! whose fn calls the bset ops; every
   assertion reads the current set inside a ($ ...) region and
   materializes plain host data before comparing.

   Adaptations from upstream, all mechanical:
   - (set/sorted-set) / (set/sorted-set-by cmp) become a fresh harness
     atom holding (bset/empty). The comparator is never stored; it is
     passed to every op, as upstream DataScript calls them.
   - (into s ks) / (reduce conj s ks) become chunked swap!s of
     (or (bset/conj root k cmp) root); disj likewise.
   - (seq s), (vec s), (count s), (= s host-coll) become, inside $:
     (vec (bset/slice root nil nil cmp)), (bset/count root), and
     host-vector equality.
   - (set/slice s f t) becomes (bset/slice root f t cmp) read inside
     $; (set/seek it k) becomes (bset/seek it k cmp).
   - ireduce becomes clojure.core/reduce, which dispatches to the
     iterator's IReduce exactly as upstream's .reduce did.
   Excluded, named: iter-over-transient (bset has no transients in
   §5). The stress suite lives in eve.db.ds.bset-stress-test."
  (:require [eve.alpha :as eve :refer [$]]
            [eve.db.ds.bset :as bset]
            [clojure.test :as t :refer [is are deftest testing]])
  #?(:clj
     (:import
       [clojure.lang ExceptionInfo])))

;; ---------------------------------------------------------------------------
;; harness

(def ^:private id-counter (atom 0))

(defn- new-conn
  "A fresh Eve atom holding an empty bset root."
  []
  (let [a (eve/atom {:id (keyword (str "u1-bset-" (swap! id-counter inc)))} nil)]
    (swap! a (fn [_] (bset/empty)))
    a))

(defn- conj-all! [a cmp ks]
  (doseq [chunk (partition-all 1000 ks)]
    (swap! a (fn [root]
               (reduce (fn [r k] (or (bset/conj r k cmp) r)) root chunk))))
  a)

(defn- disj-all! [a cmp ks]
  (doseq [chunk (partition-all 1000 ks)]
    (swap! a (fn [root]
               (reduce (fn [r k] (or (bset/disj r k cmp) r)) root chunk))))
  a)

(defn- build
  "A fresh atom holding all of ks under cmp."
  [cmp ks]
  (conj-all! (new-conn) cmp ks))

(defn- all-vec
  "Inside $: the whole set as a host vector."
  [a cmp]
  ($ (vec (bset/slice @a nil nil cmp))))

(defn- slice-vec [a from to cmp]
  ($ (some-> (bset/slice @a from to cmp) vec)))

(defn- rslice-vec [a from to cmp]
  ($ (some-> (bset/rslice @a from to cmp) vec)))

;; ---------------------------------------------------------------------------
;; upstream test/small

(deftest test-small
  (is (= (range 10 20)
         (all-vec (build compare (range 10 20)) compare)))

  (is (= (range 10 20)
         (all-vec (build compare (reverse (range 10 20))) compare)))

  (is (= (range 10 20)
         (slice-vec (build compare (reverse (range 0 100))) 10 19 compare)))

  (is (= (range 19 9 -1)
         ($ (vec (rseq (bset/slice @ (build compare (range 10 20)) nil nil compare))))))

  (is (= (range 19 9 -1)
         (rslice-vec (build compare (range 0 40)) 19 10 compare)))

  (is (= (range 10 20)
         (slice-vec (build compare (range 0 40)) 10 19 compare)))

  (let [a (build compare (shuffle (range 0 5001)))]
    (is (= (rslice-vec a 5000 nil compare)
           ($ (some-> (bset/rslice @a 5000 nil compare) rseq reverse vec))))))

;; ---------------------------------------------------------------------------
;; upstream test/core: semantic-test-btset-by

(defn- cmp* [x y]
  (if (and x y)
    (compare x y)
    0))

(defn- cmp-s [[x0 x1] [y0 y1]]
  (let [c0 (cmp* x0 y0)
        c1 (cmp* x1 y1)]
    (cond
      (= c0 0) c1
      (< c0 0) -1
      (> c0 0)  1)))

(deftest semantic-test-btset-by
  (let [a  (build cmp-s [[:a :b] [:b :x] [:b :q] [:a :d]])
        ds [[:a :b] [:b :x] [:b :q] [:a :d]]]
    (is (= (count ds)        ($ (bset/count @a))))
    (is (= (all-vec a cmp-s) (slice-vec a [nil nil] [nil nil] cmp-s)))
    (is (= [[:a :b] [:a :d]] (slice-vec a [:a nil]  [:a nil]  cmp-s)))
    (is (= [[:b :q]]         (slice-vec a [:b :q]   [:b :q]   cmp-s)))
    (is (= [[:a :d] [:b :q]] (slice-vec a [:a :d]   [:b :q]   cmp-s)))
    (is (= [[:a :d] [:b :q]] (slice-vec a [:a :c]   [:b :r]   cmp-s)))
    (is (= [[:b :x]]         (slice-vec a [:b :r]   [:c nil]  cmp-s)))
    (is (= []                (or (slice-vec a [:c nil] [:c nil] cmp-s) [])))))

;; ---------------------------------------------------------------------------
;; upstream test/core: test-slice

(def ^:private iters 5)

(defn- irange [from to]
  (if (< from to)
    (range from (inc to))
    (range from (dec to) -1)))

(deftest test-slice
  (dotimes [_ iters]
    (testing "straight 3 layers"
      (let [a (build compare (shuffle (irange 0 5000)))]
        (are [from to expected] (= (vec expected) (or (slice-vec a from to compare) []))
          nil    nil    (irange 0 5000)

          -1     nil    (irange 0 5000)
          0      nil    (irange 0 5000)
          0.5    nil    (irange 1 5000)
          1      nil    (irange 1 5000)
          4999   nil    [4999 5000]
          4999.5 nil    [5000]
          5000   nil    [5000]
          5000.5 nil    nil

          nil    -1     nil
          nil    0      [0]
          nil    0.5    [0]
          nil    1      [0 1]
          nil    4999   (irange 0 4999)
          nil    4999.5 (irange 0 4999)
          nil    5000   (irange 0 5000)
          nil    5001   (irange 0 5000)

          -2     -1     nil
          -1     5001   (irange 0 5000)
          0      5000   (irange 0 5000)
          0.5    4999.5 (irange 1 4999)
          2499.5 2500.5 [2500]
          2500   2500   [2500]
          2500.1 2500.9 nil
          5001   5002   nil)))

    (testing "straight 1 layer, leaf == root"
      (let [a (build compare (shuffle (irange 0 10)))]
        (are [from to expected] (= (vec expected) (or (slice-vec a from to compare) []))
          nil  nil  (irange 0 10)

          -1   nil  (irange 0 10)
          0    nil  (irange 0 10)
          0.5  nil  (irange 1 10)
          1    nil  (irange 1 10)
          9    nil  [9 10]
          9.5  nil  [10]
          10   nil  [10]
          10.5 nil  nil

          nil -1   nil
          nil 0    [0]
          nil 0.5  [0]
          nil 1    [0 1]
          nil 9    (irange 0 9)
          nil 9.5  (irange 0 9)
          nil 10   (irange 0 10)
          nil 11   (irange 0 10)

          -2   -1  nil
          -1   10  (irange 0 10)
          0    10  (irange 0 10)
          0.5  9.5 (irange 1 9)
          4.5  5.5 [5]
          5    5   [5]
          5.1  5.9 nil
          11   12  nil)))

    (testing "reverse 3 layers"
      (let [a (build compare (shuffle (irange 0 5000)))]
        (are [from to expected] (= (vec expected) (or (rslice-vec a from to compare) []))
          nil    nil    (irange 5000 0)

          5001   nil    (irange 5000 0)
          5000   nil    (irange 5000 0)
          4999.5 nil    (irange 4999 0)
          4999   nil    (irange 4999 0)
          1      nil    [1 0]
          0.5    nil    [0]
          0      nil    [0]
          -1     nil    nil

          nil    5001   nil
          nil    5000   [5000]
          nil    4999.5 [5000]
          nil    4999   [5000 4999]
          nil    1      (irange 5000 1)
          nil    0.5    (irange 5000 1)
          nil    0      (irange 5000 0)
          nil    -1     (irange 5000 0)

          5002   5001   nil
          5001   -1     (irange 5000 0)
          5000   0      (irange 5000 0)
          4999.5 0.5    (irange 4999 1)
          2500.5 2499.5 [2500]
          2500   2500   [2500]
          2500.9 2500.1 nil
          -1     -2     nil)))

    (testing "reverse 1 layer, leaf == root"
      (let [a (build compare (shuffle (irange 0 10)))]
        (are [from to expected] (= (vec expected) (or (rslice-vec a from to compare) []))
          nil nil (irange 10 0)

          11  nil (irange 10 0)
          10  nil (irange 10 0)
          9.5 nil (irange 9 0)
          9   nil (irange 9 0)
          1   nil [1 0]
          0.5 nil [0]
          0   nil [0]
          -1  nil nil

          nil 11  nil
          nil 10  [10]
          nil 9.5 [10]
          nil 9   [10 9]
          nil 1   (irange 10 1)
          nil 0.5 (irange 10 1)
          nil 0   (irange 10 0)
          nil -1  (irange 10 0)

          12  11  nil
          11  -1  (irange 10 0)
          10  0   (irange 10 0)
          9.5 0.5 (irange 9 1)
          5.5 4.5 [5]
          5   5   [5]
          5.9 5.1 nil
          -1  -2  nil)))

    (testing "seq-rseq equivalence"
      (let [a (build compare (shuffle (irange 0 5000)))]
        (are [from to] ($ (let [it (bset/slice @a from to compare)]
                            (= it (some-> it rseq reverse))))
          -1     nil
          0      nil
          2500   nil
          5000   nil
          5001   nil

          nil    -1
          nil    0
          nil    1
          nil    2500
          nil    5000
          nil    5001

          nil    nil

          -1     5001
          0      5000
          1      4999
          2500   2500
          2500.1 2500.9)))

    (testing "rseq-seq equivalence"
      (let [a (build compare (shuffle (irange 0 5000)))]
        (are [from to] ($ (let [it (bset/rslice @a from to compare)]
                            (= it (some-> it rseq reverse))))
          -1     nil
          0      nil
          2500   nil
          5000   nil
          5001   nil

          nil    -1
          nil    0
          nil    1
          nil    2500
          nil    5000
          nil    5001

          nil    nil

          5001   -1
          5000   0
          4999   1
          2500   2500
          2500.9 2500.1)))

    (testing "Slice with equal elements"
      (let [cmp10 (fn [a b] (compare (quot a 10) (quot b 10)))
            a10   (build compare (shuffle (irange 0 5000)))]
        (are [from to expected] (= (vec expected) (or (slice-vec a10 from to cmp10) []))
          30 30      (irange 30 39)
          130 4970   (irange 130 4979)
          -100 6000  (irange 0 5000))
        (are [from to expected] (= (vec expected) (or (rslice-vec a10 from to cmp10) []))
          30 30      (irange 39 30)
          4970 130   (irange 4979 130)
          6000 -100  (irange 5000 0)))

      (let [cmp100 (fn [a b] (compare (quot a 100) (quot b 100)))
            a100   (build compare (shuffle (irange 0 5000)))]
        (are [from to expected] (= (vec expected) (or (slice-vec a100 from to cmp100) []))
          30  30     (irange 0 99)
          2550 2550  (irange 2500 2599)
          130 4850   (irange 100 4899)
          -100 6000  (irange 0 5000))
        (are [from to expected] (= (vec expected) (or (rslice-vec a100 from to cmp100) []))
          30 30      (irange 99 0)
          2550 2550  (irange 2599 2500)
          4850 130   (irange 4899 100)
          6000 -100  (irange 5000 0))))))

;; ---------------------------------------------------------------------------
;; upstream test/core: test-reduces

(defn- reduce-chunked [f val coll]
  (if-some [s (seq coll)]
    (if (chunked-seq? s)
      (recur f (reduce f val (chunk-first s)) (chunk-next s))
      (recur f (f val (first s)) (next s)))
    val))

(deftest test-reduces
  (testing "IReduced"
    (testing "Empty"
      (let [a (new-conn)]
        (is (= 0 ($ (reduce + (bset/slice @a nil nil compare)))))
        (is (= 0 ($ (reduce + 0 (bset/slice @a nil nil compare)))))))

    (testing "~3 layers"
      (let [a (build compare (irange 0 5000))]
        (is (= 12502500 ($ (reduce +   (bset/slice @a nil nil compare)))))
        (is (= 12502500 ($ (reduce + 0 (bset/slice @a nil nil compare)))))
        (is (= 12502500 ($ (reduce +   (seq (bset/slice @a nil nil compare))))))
        (is (= 12502500 ($ (reduce + 0 (seq (bset/slice @a nil nil compare))))))
        (is (= 7502500  ($ (reduce +   (bset/slice @a 1000 4000 compare)))))
        (is (= 7502500  ($ (reduce + 0 (bset/slice @a 1000 4000 compare)))))
        (is (= 12502500 ($ (reduce +   (bset/rslice @a nil nil compare)))))
        (is (= 12502500 ($ (reduce + 0 (bset/rslice @a nil nil compare)))))
        (is (= 7502500  ($ (reduce +   (bset/rslice @a 4000 1000 compare)))))
        (is (= 7502500  ($ (reduce + 0 (bset/rslice @a 4000 1000 compare)))))))

    (testing "~1 layer"
      (let [a (build compare (irange 0 10))]
        (is (= 55 ($ (reduce +   (bset/slice @a nil nil compare)))))
        (is (= 55 ($ (reduce + 0 (bset/slice @a nil nil compare)))))
        (is (= 55 ($ (reduce +   (seq (bset/slice @a nil nil compare))))))
        (is (= 55 ($ (reduce + 0 (seq (bset/slice @a nil nil compare))))))
        (is (= 35 ($ (reduce +   (bset/slice @a 2 8 compare)))))
        (is (= 35 ($ (reduce + 0 (bset/slice @a 2 8 compare)))))
        (is (= 55 ($ (reduce +   (bset/rslice @a nil nil compare)))))
        (is (= 55 ($ (reduce + 0 (bset/rslice @a nil nil compare)))))
        (is (= 35 ($ (reduce +   (bset/rslice @a 8 2 compare)))))
        (is (= 35 ($ (reduce + 0 (bset/rslice @a 8 2 compare))))))))

  (testing "IChunkedSeq"
    (testing "~3 layers"
      (let [a (build compare (irange 0 5000))]
        (is (= 12502500 ($ (reduce-chunked + 0 (bset/slice @a nil nil compare)))))
        (is (= 7502500  ($ (reduce-chunked + 0 (bset/slice @a 1000 4000 compare)))))
        (is (= 12502500 ($ (reduce-chunked + 0 (bset/rslice @a nil nil compare)))))
        (is (= 7502500  ($ (reduce-chunked + 0 (bset/rslice @a 4000 1000 compare)))))))

    (testing "~1 layer"
      (let [a (build compare (irange 0 10))]
        (is (= 55 ($ (reduce-chunked + 0 (bset/slice @a nil nil compare)))))
        (is (= 35 ($ (reduce-chunked + 0 (bset/slice @a 2 8 compare)))))
        (is (= 55 ($ (reduce-chunked + 0 (bset/rslice @a nil nil compare)))))
        (is (= 35 ($ (reduce-chunked + 0 (bset/rslice @a 8 2 compare)))))))))

;; ---------------------------------------------------------------------------
;; upstream test/core: seek-for-seq-test

(deftest seek-for-seq-test
  (let [size 1000
        a    (build compare (range size))]
    (testing "simple seek for seq testing"
      (doseq [seek-loc (map #(* 100 %) (range 10))]
        (is (= seek-loc ($ (first (bset/seek (bset/slice @a nil nil compare) seek-loc compare))))))
      (doseq [seek-loc (map #(* 100 %) (range 10))]
        (is (= seek-loc ($ (first (bset/seek (bset/rslice @a nil nil compare) seek-loc compare)))))))

    (testing "multiple seek testing"
      (is (= 500 ($ (-> (bset/slice @a nil nil compare) (bset/seek 250 compare) (bset/seek 500 compare) first))))
      (is (= 500 ($ (-> (bset/rslice @a nil nil compare) (bset/seek 750 compare) (bset/seek 500 compare) first)))))

    (testing "normal seq behaviour after seek"
      (is (= (range 500 1000) ($ (vec (-> (bset/slice @a nil nil compare) (bset/seek 250 compare) (bset/seek 500 compare))))))
      (is (= (range 999 499 -1) ($ (vec (-> (bset/slice @a nil nil compare) (bset/seek 250 compare) (bset/seek 500 compare) rseq)))))
      (is (= (range 500 -1 -1) ($ (vec (-> (bset/rslice @a nil nil compare) (bset/seek 750 compare) (bset/seek 500 compare))))))
      (is (= (range 0 501) ($ (vec (-> (bset/rslice @a nil nil compare) (bset/seek 750 compare) (bset/seek 500 compare) rseq))))))

    (testing "nil behaviour"
      (is (thrown-with-msg? #?(:clj ExceptionInfo :lpy basilisp.lang.exception/ExceptionInfo :default ExceptionInfo) #"seek can't be called with a nil key!"
            ($ (bset/seek (bset/slice @a nil nil compare) nil compare)))))

    (testing "slicing together with seek"
      (let [a2 (build compare (range 10000))]
        (is (= (range 5000 7501) ($ (vec (-> (bset/slice @a2 2500 7500 compare)
                                             (bset/seek 5000 compare))))))
        (is (= (list 7500) ($ (vec (-> (bset/slice @a2 2500 7500 compare)
                                       (bset/seek 5000 compare)
                                       (bset/seek 7500 compare))))))
        (is (= (range 5000 2499 -1) ($ (vec (-> (bset/rslice @a2 7500 2500 compare)
                                                (bset/seek 5000 compare))))))
        (is (= (list 2500) ($ (vec (-> (bset/rslice @a2 7500 2500 compare)
                                       (bset/seek 5000 compare)
                                       (bset/seek 2500 compare))))))))))
