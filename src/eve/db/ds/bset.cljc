(ns eve.db.ds.bset
  "A B+ tree persistent sorted set over Eve value arrays — upstream
   me.tonsky.persistent-sorted-set 0.3.1 (the version DataScript 1.8.1
   pins) re-expressed per DS-IN-EVE-PLAN v2 §5.

   Two types only: Leaf [slots] and Node [slots], both eve/deftype,
   slots an Eve value array.

     Leaf: slots are the keys, 0..32 of them.
     Node: slots are [child_0 .. child_{n-1} lim_0 .. lim_{n-1}] —
           the children, then each child's largest key. Length 2n.

   A set IS its root node. There is no wrapper and no stored
   comparator: every op takes cmp as an argument, the way upstream
   DataScript already calls it — (conj s datom cmp).

   conj and disj return nil when nothing changed. No identity test
   is used anywhere: no identical? and no = on a node, a set, or a
   stored key (handles read inside a $ region answer neither — see
   EVE-FIX E6). Change is signaled by nil returns from the recursive
   node ops, exactly as upstream's node-conj/node-disj signal it.

   All path-copying; untouched children are shared by pointer.
   Values are built inside swap! fns and read inside ($ ...) —
   this namespace never derefs an atom and never reaches past
   eve.alpha."
  (:refer-clojure :exclude [conj disj count empty])
  (:require [eve.alpha :as eve :refer [$]]))

;; ---------------------------------------------------------------------------
;; constants (upstream: bits-per-level 5, max-len 32, min-len 16, avg 24)

(def ^:const max-len 32)
(def ^:const min-len 16)
(def ^:const avg-len 24)

;; ---------------------------------------------------------------------------
;; paths — upstream encodes a root-to-leaf path in one number, 5 bits per
;; level. Here paths are plain numbers and levels are divided out of a
;; factor table, so one implementation serves every host (upstream's
;; bit-op form is a JS-number workaround; quot on exact integers is the
;; same arithmetic).

(def ^:private factors
  ;; 32^0 .. 32^12 — enough for 2^60 keys. Spelled *' (promoting):
  ;; realizing the vec evaluates the iterate fn one element past
  ;; the take (a discarded 32^13), and plain * is the CHECKED form
  ;; on Eve hosts — it throws on overflow instead of promoting
  ;; (measured under jank + Eve's completion, 2026-11-21: the
  ;; checked * turned that discarded product into a load failure
  ;; for this namespace). The taken values are identical either way.
  (vec (take 13 (iterate #(*' 32 %) 1))))

(def ^:const empty-path 0)

(defn- path-get [path level]
  (bit-and (quot path (nth factors level)) 31))

(defn- path-set [path level idx]
  (let [f (nth factors level)]
    (+ (- path (* (path-get path level) f))
       (* idx f))))

(defn- path-inc [path] (inc path))
(defn- path-dec [path] (dec path))
(defn- path-lt  [p1 p2] (< p1 p2))
(defn- path-lte [p1 p2] (<= p1 p2))
(defn- path-eq  [p1 p2] (== p1 p2))

(defn- path-same-leaf [p1 p2]
  (== (quot p1 max-len) (quot p2 max-len)))

;; ---------------------------------------------------------------------------
;; Eve value-array helpers — upstream's arrays.cljc, re-expressed on
;; eve.alpha's value-array family. Every array in this namespace is an
;; Eve value array.

(defn- arr
  "An Eve value array of the arguments."
  ([] (eve/make-array 0))
  ([a1] (let [r (eve/make-array 1)] (eve/aset r 0 a1) r))
  ([a1 a2] (let [r (eve/make-array 2)] (eve/aset r 0 a1) (eve/aset r 1 a2) r))
  ([a1 a2 a3] (let [r (eve/make-array 3)]
                (eve/aset r 0 a1) (eve/aset r 1 a2) (eve/aset r 2 a3) r)))

(defn- alast [a]
  (eve/aget a (dec (eve/alength a))))

(defn- half [x] (quot x 2))

(defn- sub-arr
  "A new Eve array holding a[from .. to)."
  [a from to]
  (let [n (- to from)
        r (eve/make-array n)]
    (eve/array-copy a from r 0 n)))

(defn- arr-concat [a b]
  (let [la (eve/alength a)
        lb (eve/alength b)
        r  (eve/make-array (+ la lb))]
    (eve/array-copy a 0 r 0 la)
    (eve/array-copy b 0 r la lb)))

(defn- arr-map [f a]
  (let [n (eve/alength a)
        r (eve/make-array n)]
    (loop [i 0]
      (when (< i n)
        (eve/aset r i (f (eve/aget a i)))
        (recur (inc i))))
    r))

(defn- cut-n-splice [a cut-from cut-to splice-from splice-to xs]
  (let [xs-l (eve/alength xs)
        l1   (- splice-from cut-from)
        l2   (- cut-to splice-to)
        r    (eve/make-array (+ l1 xs-l l2))]
    (eve/array-copy a cut-from r 0 l1)
    (eve/array-copy xs 0 r l1 xs-l)
    (eve/array-copy a splice-to r (+ l1 xs-l) l2)))

(defn- splice [a splice-from splice-to xs]
  (cut-n-splice a 0 (eve/alength a) splice-from splice-to xs))

(defn- merge-n-split [a1 a2]
  (let [a1-l    (eve/alength a1)
        a2-l    (eve/alength a2)
        total-l (+ a1-l a2-l)
        r1-l    (half total-l)
        r2-l    (- total-l r1-l)
        r1      (eve/make-array r1-l)
        r2      (eve/make-array r2-l)]
    (if (<= a1-l r1-l)
      (do
        (eve/array-copy a1 0 r1 0 a1-l)
        (eve/array-copy a2 0 r1 a1-l (- r1-l a1-l))
        (eve/array-copy a2 (- r1-l a1-l) r2 0 r2-l))
      (do
        (eve/array-copy a1 0 r1 0 r1-l)
        (eve/array-copy a1 r1-l r2 0 (- a1-l r1-l))
        (eve/array-copy a2 0 r2 (- a1-l r1-l) a2-l)))
    (arr r1 r2)))

(defn- eq-arr [cmp a1 a1-from a1-to a2 a2-from a2-to]
  (let [len (- a1-to a1-from)]
    (and
      (== len (- a2-to a2-from))
      (loop [i 0]
        (cond
          (== i len) true
          (not (== 0 (cmp (eve/aget a1 (+ i a1-from))
                          (eve/aget a2 (+ i a2-from))))) false
          :else (recur (inc i)))))))

(defn- check-n-splice [cmp a from to new-a]
  (if (eq-arr cmp a from to new-a 0 (eve/alength new-a))
    a
    (splice a from to new-a)))

(defn- return-array
  "Drop nil references and return an Eve array of the arguments."
  ([a1] (arr a1))
  ([a1 a2]
   (if a1
     (if a2 (arr a1 a2) (arr a1))
     (arr a2)))
  ([a1 a2 a3]
   (if a1
     (if a2
       (if a3 (arr a1 a2 a3) (arr a1 a2))
       (if a3 (arr a1 a3) (arr a1)))
     (if a2
       (if a3 (arr a2 a3) (arr a2))
       (arr a3)))))

;; ---------------------------------------------------------------------------
;; binary search (upstream, verbatim in shape)

(defn- binary-search-l [cmp a r k]
  (loop [l 0
         r (long r)]
    (if (<= l r)
      (let [m  (half (+ l r))
            mk (eve/aget a m)]
        (if (neg? (cmp mk k))
          (recur (inc m) r)
          (recur l (dec m))))
      l)))

(defn- binary-search-r [cmp a r k]
  (loop [l 0
         r (long r)]
    (if (<= l r)
      (let [m  (half (+ l r))
            mk (eve/aget a m)]
        (if (pos? (cmp mk k))
          (recur l (dec m))
          (recur (inc m) r)))
      l)))

(defn- binary-search-l-off [cmp a off r k]
  (loop [l (long off)
         r (long r)]
    (if (<= l r)
      (let [m  (half (+ l r))
            mk (eve/aget a m)]
        (if (neg? (cmp mk k))
          (recur (inc m) r)
          (recur l (dec m))))
      l)))

(defn- binary-search-r-off [cmp a off r k]
  (loop [l (long off)
         r (long r)]
    (if (<= l r)
      (let [m  (half (+ l r))
            mk (eve/aget a m)]
        (if (pos? (cmp mk k))
          (recur l (dec m))
          (recur (inc m) r)))
      l)))

(defn- lookup-exact [cmp a key]
  (let [a-l (eve/alength a)
        idx (binary-search-l cmp a (dec a-l) key)]
    (if (and (< idx a-l)
             (== 0 (cmp (eve/aget a idx) key)))
      idx
      -1)))

(defn- lookup-range [cmp a key]
  (let [a-l (eve/alength a)
        idx (binary-search-l cmp a (dec a-l) key)]
    (if (== idx a-l)
      -1
      idx)))

;; ---------------------------------------------------------------------------
;; the two types

(eve/deftype Leaf [slots])
(eve/deftype Node [slots])

(defn leaf? [x] (instance? Leaf x))
(defn node? [x] (instance? Node x))

;; Node slot layout: [child_0 .. child_{n-1} lim_0 .. lim_{n-1}]

(defn- node-children-count [node]
  (quot (eve/alength (:slots node)) 2))

(defn- node-child [node i]
  (eve/aget (:slots node) i))

(defn- node-children [node]
  (sub-arr (:slots node) 0 (node-children-count node)))

(defn- node-lims [node]
  (let [n (node-children-count node)]
    (sub-arr (:slots node) n (* 2 n))))

(defn- lim-idx-l
  "Child index for key, searching the lim segment of node's slots in
   place (all lims but the last, as upstream) — no scratch copy."
  [node cmp key]
  (let [n (node-children-count node)]
    (- (binary-search-l-off cmp (:slots node) n (- (* 2 n) 2) key) n)))

(defn- lim-idx-r
  "binary-search-r over the lim segment in place."
  [node cmp key]
  (let [n (node-children-count node)]
    (- (binary-search-r-off cmp (:slots node) n (- (* 2 n) 2) key) n)))

(defn- lim-idx-range
  "lookup-range over the lim segment in place: first child whose lim
   is >= key, or -1."
  [node cmp key]
  (let [n   (node-children-count node)
        idx (binary-search-l-off cmp (:slots node) n (dec (* 2 n)) key)]
    (if (== idx (* 2 n))
      -1
      (- idx n))))

(defn- make-node
  "A Node over an Eve array of children; lim keys are computed."
  [children]
  (let [n     (eve/alength children)
        slots (eve/make-array (* 2 n))]
    (loop [i 0]
      (when (< i n)
        (eve/aset slots i (eve/aget children i))
        (recur (inc i))))
    (loop [i 0]
      (when (< i n)
        (eve/aset slots (+ n i) (alast (:slots (eve/aget children i))))
        (recur (inc i))))
    (->Node slots)))

(defn- node-from-parts
  "A Node from an Eve array of children and the parallel Eve array of
   their largest keys (the pairing node-conj/node-disj maintain)."
  [children lims]
  (->Node (arr-concat children lims)))

;; ---------------------------------------------------------------------------
;; node ops — upstream's INode protocol as plain fns dispatching on the
;; two types. Each returns what upstream's returns: node-lookup a stored
;; key or nil; node-conj/node-disj an Eve array of replacement nodes,
;; or nil when nothing changed.

(declare node-conj node-disj node-merge node-merge-n-split)

(defn- node-lim-key [node]
  ;; Leaf: last key. Node: last lim, which is the last child's lim key.
  (alast (:slots node)))

(defn- node-len [node]
  (if (leaf? node)
    (eve/alength (:slots node))
    (node-children-count node)))

(defn- node-merge [node next]
  (if (leaf? node)
    (->Leaf (arr-concat (:slots node) (:slots next)))
    (make-node (arr-concat (node-children node) (node-children next)))))

(defn- node-merge-n-split [node next]
  (if (leaf? node)
    (let [ks (merge-n-split (:slots node) (:slots next))]
      (return-array (->Leaf (eve/aget ks 0))
                    (->Leaf (eve/aget ks 1))))
    (let [cs (merge-n-split (node-children node) (node-children next))]
      (return-array (make-node (eve/aget cs 0))
                    (make-node (eve/aget cs 1))))))

(defn- node-lookup [node cmp key]
  (if (leaf? node)
    (let [idx (lookup-exact cmp (:slots node) key)]
      (when-not (== -1 idx)
        (eve/aget (:slots node) idx)))
    (let [idx (lim-idx-range node cmp key)]
      (when-not (== -1 idx)
        (node-lookup (node-child node idx) cmp key)))))

(defn- rotate [node root? left right]
  (cond
    ;; root never merges
    root?
    (return-array node)

    ;; enough keys, nothing to merge
    (> (node-len node) min-len)
    (return-array left node right)

    ;; left and this can be merged to one
    (and left (<= (node-len left) min-len))
    (return-array (node-merge left node) right)

    ;; right and this can be merged to one
    (and right (<= (node-len right) min-len))
    (return-array left (node-merge node right))

    ;; left has fewer nodes, redistribute with it
    (and left (or (nil? right)
                  (< (node-len left) (node-len right))))
    (let [nodes (node-merge-n-split left node)]
      (return-array (eve/aget nodes 0) (eve/aget nodes 1) right))

    ;; right has fewer nodes, redistribute with it
    :else
    (let [nodes (node-merge-n-split node right)]
      (return-array left (eve/aget nodes 0) (eve/aget nodes 1)))))

(defn- leaf-conj [slots cmp key]
  (let [idx    (binary-search-l cmp slots (dec (eve/alength slots)) key)
        keys-l (eve/alength slots)]
    (cond
      ;; element already here
      (and (< idx keys-l)
           (== 0 (cmp key (eve/aget slots idx))))
      nil

      ;; splitting
      (== keys-l max-len)
      (let [middle (half (inc keys-l))]
        (if (> idx middle)
          ;; new key goes to the second half
          (arr
            (->Leaf (sub-arr slots 0 middle))
            (->Leaf (cut-n-splice slots middle keys-l idx idx (arr key))))
          ;; new key goes to the first half
          (arr
            (->Leaf (cut-n-splice slots 0 middle idx idx (arr key)))
            (->Leaf (sub-arr slots middle keys-l)))))

      ;; ok as is
      :else
      (arr (->Leaf (splice slots idx idx (arr key)))))))

(defn- node-conj* [node cmp key]
  (let [idx   (lim-idx-l node cmp key)
        nodes (node-conj (node-child node idx) cmp key)]
    (when nodes
      (let [lims         (node-lims node)
            children     (node-children node)
            new-lims     (check-n-splice cmp lims     idx (inc idx) (arr-map node-lim-key nodes))
            new-children (splice             children idx (inc idx) nodes)]
        (if (<= (eve/alength new-children) max-len)
          ;; ok as is
          (arr (node-from-parts new-children new-lims))
          ;; gotta split it up
          (let [middle (half (eve/alength new-children))]
            (arr
              (node-from-parts (sub-arr new-children 0 middle)
                               (sub-arr new-lims 0 middle))
              (node-from-parts (sub-arr new-children middle (eve/alength new-children))
                               (sub-arr new-lims middle (eve/alength new-lims))))))))))

(defn- node-conj [node cmp key]
  (if (leaf? node)
    (leaf-conj (:slots node) cmp key)
    (node-conj* node cmp key)))

(defn- leaf-disj [slots cmp key root? left right]
  (let [idx (lookup-exact cmp slots key)]
    (when-not (== -1 idx) ;; key is here
      (let [new-keys (splice slots idx (inc idx) (arr))]
        (rotate (->Leaf new-keys) root? left right)))))

(defn- node-disj* [node cmp key root? left right]
  (let [idx (lim-idx-range node cmp key)]
    (when-not (== -1 idx) ;; short-circuit, key not here
      (let [child       (node-child node idx)
            left-child  (when (>= (dec idx) 0)
                          (node-child node (dec idx)))
            right-child (when (< (inc idx) (node-children-count node))
                          (node-child node (inc idx)))
            disjned     (node-disj child cmp key false left-child right-child)]
        (when disjned ;; short-circuit, key not here
          (let [lims         (node-lims node)
                children     (node-children node)
                left-idx     (if left-child  (dec idx) idx)
                right-idx    (if right-child (+ 2 idx) (+ 1 idx))
                new-lims     (check-n-splice cmp lims     left-idx right-idx (arr-map node-lim-key disjned))
                new-children (splice             children left-idx right-idx disjned)]
            (rotate (node-from-parts new-children new-lims) root? left right)))))))

(defn- node-disj [node cmp key root? left right]
  (if (leaf? node)
    (leaf-disj (:slots node) cmp key root? left right)
    (node-disj* node cmp key root? left right)))

;; ---------------------------------------------------------------------------
;; iteration machinery — upstream's path walking, with the tree's root and
;; shift threaded explicitly (there is no BTSet wrapper to carry them).

(defn- root-shift
  "Depth of the tree minus one: 0 when the root is a Leaf."
  [root]
  (loop [node root
         s    0]
    (if (leaf? node)
      s
      (recur (node-child node 0) (inc s)))))

(defn- keys-for [root shift path]
  (loop [level shift
         node  root]
    (if (pos? level)
      (recur (dec level)
             (node-child node (path-get path level)))
      (:slots node))))

(defn- -next-path [node path level]
  (let [idx (path-get path level)]
    (if (pos? level)
      ;; inner node
      (let [sub-path (-next-path (node-child node idx) path (dec level))]
        (if (nil? sub-path)
          ;; nested node overflow
          (if (< (inc idx) (node-children-count node))
            ;; advance current node idx, reset subsequent indexes
            (path-set empty-path level (inc idx))
            ;; current node overflow
            nil)
          ;; keep current idx
          (path-set sub-path level idx)))
      ;; leaf
      (if (< (inc idx) (eve/alength (:slots node)))
        ;; advance leaf idx
        (path-set empty-path 0 (inc idx))
        ;; leaf overflow
        nil))))

(defn- -rpath
  "Rightmost path possible starting from node and going deeper."
  [node path level]
  (loop [node  node
         path  path
         level level]
    (if (pos? level)
      (recur
        (node-child node (dec (node-children-count node)))
        (path-set path level (dec (node-children-count node)))
        (dec level))
      (path-set path 0 (dec (eve/alength (:slots node)))))))

(defn- next-path
  "Path representing the next item after `path` in natural traversal
   order. Will overflow at leaf if at the end of the tree."
  [root shift path]
  (if (neg? path)
    empty-path
    (or
      (-next-path root path shift)
      (path-inc (-rpath root empty-path shift)))))

(defn- -prev-path [node path level]
  (let [idx (path-get path level)]
    (cond
      ;; leaf overflow
      (and (== 0 level) (== 0 idx))
      nil

      ;; leaf
      (== 0 level)
      (path-set empty-path 0 (dec idx))

      ;; branch that was overflow before
      (>= idx (node-len node))
      (-rpath node path level)

      :else
      (let [path' (-prev-path (node-child node idx) path (dec level))]
        (cond
          ;; no sub-overflow, keep current idx
          (some? path')
          (path-set path' level idx)

          ;; nested overflow + this node overflow
          (== 0 idx)
          nil

          ;; nested overflow, advance current idx, reset subsequent indexes
          :else
          (let [path'' (-rpath (node-child node (dec idx)) path (dec level))]
            (path-set path'' level (dec idx))))))))

(defn- prev-path
  "Path representing the previous item before `path` in natural
   traversal order. Will overflow at leaf if at beginning of tree."
  [root shift path]
  (if (> (path-get path (inc shift)) 0) ;; overflow
    (-rpath root path shift)
    (or
      (-prev-path root path shift)
      (path-dec empty-path))))

;; distance

(defn- -distance [node left right level]
  (let [idx-l (path-get left level)
        idx-r (path-get right level)]
    (if (pos? level)
      ;; inner node
      (if (== idx-l idx-r)
        (-distance (node-child node idx-l) left right (dec level))
        (loop [level level
               res   (- idx-r idx-l)]
          (if (== 0 level)
            res
            (recur (dec level) (* res avg-len)))))
      (- idx-r idx-l))))

(defn- distance [root shift path-l path-r]
  (cond
    (path-eq path-l path-r)
    0

    (path-eq (path-inc path-l) path-r)
    1

    (path-eq (next-path root shift path-l) path-r)
    1

    :else
    (-distance root path-l path-r shift)))

;; seek paths

(defn- -seek*
  "Path to first element >= key, or nil if all elements in the set
   are < key. A nil key returns the start path."
  [root shift key cmp]
  (if (nil? key)
    empty-path
    (loop [node  root
           path  empty-path
           level shift]
      (let [keys-l (node-len node)]
        (if (== 0 level)
          (let [keys (:slots node)
                idx  (binary-search-l cmp keys (dec keys-l) key)]
            (if (== keys-l idx)
              nil
              (path-set path 0 idx)))
          (let [idx (lim-idx-l node cmp key)]
            (recur
              (node-child node idx)
              (path-set path level idx)
              (dec level))))))))

(defn- -rseek*
  "Path to the first element that is > key. If all elements in the
   set are <= key, returns (-rpath) + 1: a virtual path bigger than
   any path in the tree."
  [root shift key cmp]
  (if (nil? key)
    (path-inc (-rpath root empty-path shift))
    (loop [node  root
           path  empty-path
           level shift]
      (let [keys-l (node-len node)]
        (if (== 0 level)
          (let [keys (:slots node)
                idx  (binary-search-r cmp keys (dec keys-l) key)]
            (path-set path 0 idx))
          (let [idx (lim-idx-r node cmp key)
                res (path-set path level idx)]
            (recur
              (node-child node idx)
              res
              (dec level))))))))

;; ---------------------------------------------------------------------------
;; iterators — upstream's Iter/ReverseIter, with root+shift in place of
;; the BTSet wrapper. An iterator is a value: [root shift left right
;; keys idx], keys the cached Eve array of the leaf the current path
;; points into (nil when the iterator is exhausted). Iterators are
;; built and consumed inside $ regions; their seq/reduce/chunk
;; behavior is upstream's.
;;
;; The iterators are HOST deftypes, not eve/deftypes — measured, not
;; preference: at the pinned Eve (f4a1c3a), constructing an
;; eve/deftype inside a $ region routes through
;; construct-flat, the flat layer's sister constructor, which
;; refuses fields held by
;; reference ("a value held by reference cannot be packed") — and an
;; iterator's fields are exactly live node/array handles. The set's
;; own types (Leaf/Node) are eve/deftype and are only ever built
;; inside swap! fns, where construction is slab construction and
;; works. Provenance: U1 probe stack, 2026-11-19, this pin.

(declare -iter-next -iter-chunked-next -iter-rseq iter-reduce
         -riter-next -riter-rseq riter-reduce seq-equiv)

(defn- seq-equiv [a b]
  (loop [sa (seq a)
         sb (seq b)]
    (cond
      (and (nil? sa) (nil? sb)) true
      (or  (nil? sa) (nil? sb)) false
      (= (first sa) (first sb)) (recur (next sa) (next sb))
      :else false)))

(deftype Chunk [arr off end]
  #?@(:clj
      [clojure.lang.IChunk
       (dropFirst [_]
         (if (== off end)
           (throw (ex-info "-drop-first of empty chunk" {}))
           (Chunk. arr (inc off) end)))
       clojure.lang.Indexed
       (nth [_ i]
         (eve/aget arr (+ off i)))
       (nth [_ i not-found]
         (if (and (>= i 0) (< i (- end off)))
           (eve/aget arr (+ off i))
           not-found))
       clojure.lang.Counted
       (count [_] (- end off))
       clojure.lang.IReduce
       (reduce [this f]
         (if (== off end)
           (f)
           (clojure.core/reduce f (eve/aget arr off) (Chunk. arr (inc off) end))))
       (reduce [_ f start]
         (loop [val start
                n   off]
           (if (< n end)
             (let [val' (f val (eve/aget arr n))]
               (if (reduced? val')
                 @val'
                 (recur val' (inc n))))
             val)))]
      :cljs
      [IChunk
       (-drop-first [_]
         (if (== off end)
           (throw (js/Error. "-drop-first of empty chunk"))
           (Chunk. arr (inc off) end)))
       IIndexed
       (-nth [_ i]
         (eve/aget arr (+ off i)))
       (-nth [_ i not-found]
         (if (and (>= i 0) (< i (- end off)))
           (eve/aget arr (+ off i))
           not-found))
       ICounted
       (-count [_] (- end off))
       IReduce
       (-reduce [this f]
         (if (== off end)
           (f)
           (-reduce (Chunk. arr (inc off) end) f (eve/aget arr off))))
       (-reduce [_ f start]
         (loop [val start
                n   off]
           (if (< n end)
             (let [val' (f val (eve/aget arr n))]
               (if (reduced? val')
                 @val'
                 (recur val' (inc n))))
             val)))]))

(deftype Iter [root shift left right keys idx]
  #?@(:clj
      [clojure.lang.ISeq
       (first [_]
         (when keys
           (eve/aget keys idx)))
       (next [this]
         (-iter-next this))
       (more [this]
         (or (-iter-next this) ()))
       (cons [this x]
         (clojure.core/cons x this))
       clojure.lang.Seqable
       (seq [this]
         (when keys this))
       java.lang.Iterable
       (iterator [this]
         (clojure.lang.SeqIterator. (.seq this)))
       clojure.lang.Sequential
       clojure.lang.IPersistentCollection
       (count [_]
         (distance root shift left right))
       (empty [_] ())
       (equiv [this other]
         (boolean (and (sequential? other) (seq-equiv this other))))
       clojure.lang.IObj
       (meta [_] nil)
       (withMeta [this _] this)
       clojure.lang.IReduce
       (reduce [this f]
         (iter-reduce this f))
       (reduce [this f start]
         (iter-reduce this f start))
       clojure.lang.IChunkedSeq
       (chunkedFirst [this]
         (let [end-idx (if (path-same-leaf left right)
                         (path-get right 0)
                         (eve/alength keys))]
           (->Chunk keys idx end-idx)))
       (chunkedNext [this]
         (-iter-chunked-next this))
       (chunkedMore [this]
         (or (-iter-chunked-next this) ()))
       clojure.lang.Reversible
       (rseq [this]
         (when keys
           (-iter-rseq this)))
       clojure.lang.IHashEq
       (hasheq [this]
         (clojure.core/hash-ordered-coll this))
       Object
       (equals [this other]
         (boolean (and (sequential? other) (seq-equiv this other))))
       (hashCode [this]
         (clojure.core/hash-ordered-coll this))
       (toString [this]
         (pr-str this))]
      :cljs
      [ISeq
       (-first [_]
         (when keys
           (eve/aget keys idx)))
       (-rest [this]
         (or (-iter-next this) ()))
       INext
       (-next [this]
         (-iter-next this))
       ISeqable
       (-seq [this]
         (when keys this))
       IIterable
       ;; cljs's -iterator contract is the hasNext/next Iterator
       ;; (iter-reduce and TransformerIterator call .hasNext on it),
       ;; NOT the ES2015 {value,done} shape: seq-iter is the mirror
       ;; of the :clj arm's SeqIterator over the seq.
       (-iterator [this]
         (seq-iter this))
       ICollection
       (-conj [this x]
         (clojure.core/cons x this))
       ICounted
       (-count [_]
         (distance root shift left right))
       IEmptyableCollection
       (-empty [_] ())
       IEquiv
       (-equiv [this other]
         (boolean (and (sequential? other) (seq-equiv this other))))
       IHash
       (-hash [this]
         (hash-ordered-coll this))
       IReduce
       (-reduce [this f]
         (iter-reduce this f))
       (-reduce [this f start]
         (iter-reduce this f start))
       IChunkedSeq
       (-chunked-first [this]
         (let [end-idx (if (path-same-leaf left right)
                         (path-get right 0)
                         (eve/alength keys))]
           (->Chunk keys idx end-idx)))
       (-chunked-rest [this]
         (or (-iter-chunked-next this) ()))
       IChunkedNext
       (-chunked-next [this]
         (-iter-chunked-next this))
       ISequential
       IReversible
       (-rseq [this]
         (when keys
           (-iter-rseq this)))
       IMeta
       (-meta [_] nil)
       IWithMeta
       (-with-meta [this _] this)]))

(deftype ReverseIter [root shift left right keys idx]
  #?@(:clj
      [clojure.lang.ISeq
       (first [_]
         (when keys
           (eve/aget keys idx)))
       (next [this]
         (-riter-next this))
       (more [this]
         (or (-riter-next this) ()))
       (cons [this x]
         (clojure.core/cons x this))
       clojure.lang.Seqable
       (seq [this]
         (when keys this))
       java.lang.Iterable
       (iterator [this]
         (clojure.lang.SeqIterator. (.seq this)))
       clojure.lang.Sequential
       clojure.lang.IPersistentCollection
       (count [_]
         (distance root shift left right))
       (empty [_] ())
       (equiv [this other]
         (boolean (and (sequential? other) (seq-equiv this other))))
       clojure.lang.IObj
       (meta [_] nil)
       (withMeta [this _] this)
       clojure.lang.IReduce
       (reduce [this f]
         (riter-reduce this f))
       (reduce [this f start]
         (riter-reduce this f start))
       clojure.lang.Reversible
       (rseq [this]
         (when keys
           (-riter-rseq this)))
       clojure.lang.IHashEq
       (hasheq [this]
         (clojure.core/hash-ordered-coll this))
       Object
       (equals [this other]
         (boolean (and (sequential? other) (seq-equiv this other))))
       (hashCode [this]
         (clojure.core/hash-ordered-coll this))
       (toString [this]
         (pr-str this))]
      :cljs
      [ISeq
       (-first [_]
         (when keys
           (eve/aget keys idx)))
       (-rest [this]
         (or (-riter-next this) ()))
       INext
       (-next [this]
         (-riter-next this))
       ISeqable
       (-seq [this]
         (when keys this))
       IIterable
       ;; cljs's -iterator contract is the hasNext/next Iterator
       ;; (iter-reduce and TransformerIterator call .hasNext on it),
       ;; NOT the ES2015 {value,done} shape: seq-iter is the mirror
       ;; of the :clj arm's SeqIterator over the seq.
       (-iterator [this]
         (seq-iter this))
       ICollection
       (-conj [this x]
         (clojure.core/cons x this))
       ICounted
       (-count [_]
         (distance root shift left right))
       IEmptyableCollection
       (-empty [_] ())
       IEquiv
       (-equiv [this other]
         (boolean (and (sequential? other) (seq-equiv this other))))
       IHash
       (-hash [this]
         (hash-ordered-coll this))
       ISequential
       IReversible
       (-rseq [this]
         (when keys
           (-riter-rseq this)))
       IMeta
       (-meta [_] nil)
       IWithMeta
       (-with-meta [this _] this)]))

;; ---------------------------------------------------------------------------
;; iterator helpers

(defn- make-iter [root shift l r]
  (->Iter root shift l r (keys-for root shift l) (path-get l 0)))

(defn- make-riter [root shift l r]
  (->ReverseIter root shift l r (keys-for root shift r) (path-get r 0)))

(defn- iter-copy [^Iter it l r]
  (->Iter (.-root it) (.-shift it) l r
          (keys-for (.-root it) (.-shift it) l)
          (path-get l 0)))

(defn- riter-copy [^ReverseIter it l r]
  (->ReverseIter (.-root it) (.-shift it) l r
                 (keys-for (.-root it) (.-shift it) r)
                 (path-get r 0)))

(defn- -iter-next [^Iter it]
  (when (.-keys it)
    (if (< (inc (.-idx it)) (eve/alength (.-keys it)))
      ;; can use cached array to move forward
      (let [left' (path-inc (.-left it))]
        (when (path-lt left' (.-right it))
          (->Iter (.-root it) (.-shift it) left' (.-right it)
                  (.-keys it) (inc (.-idx it)))))
      (let [left' (next-path (.-root it) (.-shift it) (.-left it))]
        (when (path-lt left' (.-right it))
          (iter-copy it left' (.-right it)))))))

(defn- -riter-next [^ReverseIter it]
  (when (.-keys it)
    (if (> (.-idx it) 0)
      ;; can use cached array to advance
      (let [right' (path-dec (.-right it))]
        (when (path-lt (.-left it) right')
          (->ReverseIter (.-root it) (.-shift it) (.-left it) right'
                         (.-keys it) (dec (.-idx it)))))
      (let [right' (prev-path (.-root it) (.-shift it) (.-right it))]
        (when (path-lt (.-left it) right')
          (riter-copy it (.-left it) right'))))))

(defn- -iter-chunked-next [^Iter it]
  (let [last  (path-set (.-left it) 0 (dec (eve/alength (.-keys it))))
        left' (next-path (.-root it) (.-shift it) last)]
    (when (path-lt left' (.-right it))
      (iter-copy it left' (.-right it)))))

(defn- iter-reduce
  ([^Iter it f]
   (if (nil? (.-keys it))
     (f)
     (let [v (eve/aget (.-keys it) (.-idx it))]
       (if-some [nxt (-iter-next it)]
         (clojure.core/reduce f v nxt)
         v))))
  ([^Iter it f start]
   (loop [left (.-left it)
          keys (.-keys it)
          idx  (.-idx it)
          acc  start]
     (if (nil? keys)
       acc
       (let [new-acc (f acc (eve/aget keys idx))]
         (cond
           (reduced? new-acc)
           @new-acc

           (< (inc idx) (eve/alength keys)) ;; can use cached array to move forward
           (let [left' (path-inc left)]
             (if (path-lt left' (.-right it))
               (recur left' keys (inc idx) new-acc)
               new-acc))

           :else
           (let [left' (next-path (.-root it) (.-shift it) left)]
             (if (path-lt left' (.-right it))
               (recur left' (keys-for (.-root it) (.-shift it) left') (path-get left' 0) new-acc)
               new-acc))))))))

(defn- riter-reduce
  ;; JVM parity only (upstream's JVM reverse seq is reducible; its
  ;; cljs ReverseIter is not): a downward mirror of iter-reduce.
  ([^ReverseIter it f]
   (if (nil? (.-keys it))
     (f)
     (let [v (eve/aget (.-keys it) (.-idx it))]
       (if-some [nxt (-riter-next it)]
         (clojure.core/reduce f v nxt)
         v))))
  ([^ReverseIter it f start]
   (loop [right (.-right it)
          keys  (.-keys it)
          idx   (.-idx it)
          acc   start]
     (if (nil? keys)
       acc
       (let [new-acc (f acc (eve/aget keys idx))]
         (cond
           (reduced? new-acc)
           @new-acc

           (> idx 0) ;; can use cached array to advance
           (let [right' (path-dec right)]
             (if (path-lt (.-left it) right')
               (recur right' keys (dec idx) new-acc)
               new-acc))

           :else
           (let [right' (prev-path (.-root it) (.-shift it) right)]
             (if (path-lt (.-left it) right')
               (recur right' (keys-for (.-root it) (.-shift it) right') (path-get right' 0) new-acc)
               new-acc))))))))

(defn- -iter-rseq [^Iter it]
  (make-riter (.-root it) (.-shift it)
              (prev-path (.-root it) (.-shift it) (.-left it))
              (prev-path (.-root it) (.-shift it) (.-right it))))

(defn- -riter-rseq [^ReverseIter it]
  (make-iter (.-root it) (.-shift it)
             (next-path (.-root it) (.-shift it) (.-left it))
             (next-path (.-root it) (.-shift it) (.-right it))))

(defn- -slice [root shift from to cmp]
  (when-some [path (-seek* root shift from cmp)]
    (let [till-path (-rseek* root shift to cmp)]
      (when (path-lt path till-path)
        (make-iter root shift path till-path)))))

(defn- iter-seek [^Iter it key cmp]
  (cond
    (nil? key)
    (throw (ex-info "seek can't be called with a nil key!" {:key key}))

    (nat-int? (cmp (eve/aget (.-keys it) (.-idx it)) key))
    it

    :else
    (when-some [left' (-seek* (.-root it) (.-shift it) key cmp)]
      (->Iter (.-root it) (.-shift it) left' (.-right it)
              (keys-for (.-root it) (.-shift it) left')
              (path-get left' 0)))))

(defn- riter-seek [^ReverseIter it key cmp]
  (cond
    (nil? key)
    (throw (ex-info "seek can't be called with a nil key!" {:key key}))

    (nat-int? (cmp key (eve/aget (.-keys it) (.-idx it))))
    it

    :else
    (let [right' (prev-path (.-root it) (.-shift it)
                            (-rseek* (.-root it) (.-shift it) key cmp))]
      (when (and
              (nat-int? right')
              (path-lte (.-left it) right')
              (path-lt  right' (.-right it)))
        (->ReverseIter (.-root it) (.-shift it) (.-left it) right'
                       (keys-for (.-root it) (.-shift it) right')
                       (path-get right' 0))))))

;; ---------------------------------------------------------------------------
;; public ops — §5. Every op takes the set (its root node) and, where
;; ordering is involved, cmp. conj/disj return nil when nothing changed.

(defn empty
  "An empty set: a Leaf with no keys. Call inside a swap! fn — the
   value array is allocated in the current domain."
  []
  (->Leaf (arr)))

(defn conj
  "The set with key added, or nil if key was already present."
  [root key cmp]
  (let [roots (node-conj root cmp key)]
    (cond
      ;; tree not changed
      (nil? roots)
      nil

      ;; keeping single root
      (== (eve/alength roots) 1)
      (eve/aget roots 0)

      ;; introducing new root
      :else
      (make-node roots))))

(defn disj
  "The set with key removed, or nil if key was not present."
  [root key cmp]
  (let [new-roots (node-disj root cmp key true nil nil)]
    (if (nil? new-roots) ;; nothing changed, key wasn't in the set
      nil
      (let [new-root (eve/aget new-roots 0)]
        (if (and (node? new-root)
                 (== 1 (node-children-count new-root)))
          ;; root has one child, make him new root
          (node-child new-root 0)
          ;; keeping root level
          new-root)))))

(defn lookup
  "The stored key equal to key under cmp, or nil."
  [root key cmp]
  (node-lookup root cmp key))

(defn count
  "The number of keys in the set. Walks the tree: §5 gives a node no
   slot for a cached count, so callers that need a rolling count
   keep it beside the root (upstream kept it in the BTSet wrapper)."
  [root]
  (if (leaf? root)
    (eve/alength (:slots root))
    (loop [i 0
           s 0]
      (if (< i (node-children-count root))
        (recur (inc i) (+ s (count (node-child root i))))
        s))))

(defn slice
  "An iterator (seqable, reducible, reversible) over all Xs where
   from <= X <= to under cmp. A nil bound is unbounded on that side."
  [root from to cmp]
  (-slice root (root-shift root) from to cmp))

(defn rslice
  "A reverse iterator over all Xs where from <= X <= to under cmp,
   descending: from is the high bound, to the low bound. A nil bound
   is unbounded on that side."
  ([root key cmp]
   (rslice root key key cmp))
  ([root from to cmp]
   (let [shift (root-shift root)]
     (some-> (-slice root shift to from cmp)
             (-iter-rseq)))))

(defn seek
  "Advance an iterator to the first element >= key (for a reverse
   iterator: <= key) under cmp. Returns nil when no such element
   remains in the iterator's bounds."
  [it key cmp]
  (if (instance? ReverseIter it)
    (riter-seek it key cmp)
    (iter-seek it key cmp)))

(defn est-count
  "Estimated number of elements remaining in an iterator
   (upstream's distance heuristic: exact at the edges, avg-len
   based in between)."
  [^Iter it]
  (distance (.-root it) (.-shift it) (.-left it) (.-right it)))
