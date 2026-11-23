(ns ^:no-doc eve.db.ds.arrays
    "The array substrate eve.db.ds.query was vendored against:
   upstream me.tonsky.persistent-sorted-set.arrays (persistent-
   sorted-set 0.3.1, the version DataScript 1.8.1 pins), whose
   contract is NATIVE arrays — Java Object[] on :clj, JS arrays
   on :cljs — mutated in place after allocation.

   The repo's lib/ port of that namespace re-bases the same names
   on Eve persistent vectors (its PSS consumer is written for
   persistent semantics); query.cljc's tuple machinery (aclone,
   ^objects aget/aset, .isArray dispatch in -collect-tuples) is
   written for the native contract and cannot run on vectors.
   This namespace restores the upstream contract for query's use.
   See U4-STATUS.md GAP-5."
    (:refer-clojure :exclude [make-array aget aset aclone array?]))

(defn make-array
      "A native array of n empty (nil) slots."
      [size]
      #?(:clj  (object-array size)
         :cljs (into-array (repeat size nil))))

(defn aget
      "The element at index i."
      [arr i]
      #?(:clj  (clojure.core/aget ^objects arr i)
         :cljs (cljs.core/aget arr i)))

(defn aset
      "Mutates arr at index i to v. Returns arr."
      [arr i v]
      #?(:clj  (do (clojure.core/aset ^objects arr i v) arr)
         :cljs (do (cljs.core/aset arr i v) arr)))

(defn aclone
      "A shallow copy of arr, independently mutable."
      [arr]
      #?(:clj  (clojure.core/aclone ^objects arr)
         :cljs (.slice arr)))

(defn array?
      "True when x is a native array."
      [x]
      #?(:clj  (and (some? x) (.isArray (class x)))
         :cljs (cljs.core/array? x)))
