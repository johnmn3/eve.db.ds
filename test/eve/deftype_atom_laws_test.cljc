(ns eve.deftype-atom-laws-test
    "The deftype/atom laws probe — EVE-FIX-PLAN §4, copied into eve.db.ds
   as its laws test (DS-IN-EVE-PLAN v2 §7, unit U0).

   One deftest per row E1–E8 of EVE-FIX-PLAN §0, written against
   eve.alpha ONLY, asserting the FIXED behaviour. Probe shape: an
   in-process eve/atom holding {:db (->Box ...)} where Box is an
   eve/deftype built inside swap!.

   Measured state at the pre-fix Eve pin (release/eve-0.9.3.3
   f4a1c3ae97ee76f2651ce600a857675aa5c227ef): E1/E2/E4/E5/E7/E8
   green, E3 red in altered form (compare-and-set! returns false —
   a delivered value is a fresh copy per read, so it can never
   identity-match the stored root; plan v2 §2 bans CAS on the conn
   atom for this reason), E6 pending (eve/same? absent).

   At the fixed pin (release tip 323bfe3, F3 landed): E6 asserts
   the real eve/same? behaviour. E1 asserts the Eve seat's ruling
   (2026-11-19): a delivered deftype value is the flat sister type,
   not the host class — delivery is by value, and the delivered
   value answers the type's protocols. Do not work around a red
   row from this repo.

   Run on the JVM:
     clojure -M:laws -e \"(require 'eve.deftype-atom-laws-test)
                         (clojure.test/run-tests 'eve.deftype-atom-laws-test)\"
   Row results are recorded in docs/LAWS-RESULTS.md."
    (:refer-clojure :exclude [->Box Box])
    (:require [clojure.test :as t :refer [deftest is]]
              [eve.alpha :as eve :refer [$]]))

(defprotocol IBox
             (-n [_] "The number the box was built with."))

(eve/deftype Box [n]
             IBox
             (-n [_] n))

(defn- fresh-atom
       "A fresh Eve atom whose value is {:db (->Box 1)}, the Box built
   inside the swap, per the probe shape."
       [id]
       (let [a (eve/atom {:id id} nil)]
            (swap! a (fn [_] {:db (->Box 1)}))
            a))

(deftest e1-deref-returns-deftype-value
  ;; E1: @a on an atom holding an eve/deftype value returns the value
  ;; (flat packing, EVE-FIX F1). Per the Eve seat's ruling the
  ;; delivered value is the flat sister type, not the host class:
  ;; what is asserted is that the delivered value answers the
  ;; type's protocols, not host-class identity.
         (let [a (fresh-atom ::e1)
               m @a]
              (is (map? m))
              (is (satisfies? IBox (:db m)))
              (is (= 1 (-n (:db m))))))

(deftest e2-add-watch-then-swap
  ;; E2: add-watch + swap! works on an atom holding a deftype value;
  ;; the watch fires with the delivered new value. Today: same throw
  ;; as E1, on the swap.
         (let [a    (fresh-atom ::e2)
               seen (atom [])]
              (add-watch a :laws (fn [_k _r _old new] (swap! seen conj new)))
              (swap! a (fn [m] (assoc m :db (->Box 2))))
              (is (= 1 (count @seen)))
              (is (= 2 (-n (:db (first @seen)))))))

(deftest e3-compare-and-set
  ;; E3: compare-and-set! works on an atom holding a deftype value.
  ;; Today: same throw as E1 (the initial deref already throws).
         (let [a   (fresh-atom ::e3)
               old @a]
              (is (true? (compare-and-set! a old {:db (->Box 2)})))
              (is (= 2 (-n (:db @a))))))

(deftest e4-reset-with-deftype-built-inside-swap
  ;; E4: reset! accepts a value holding an eve/deftype that was built
  ;; inside a swap (captured out through a volatile!). The final read
  ;; goes through $ so this row asserts reset! itself, not E1's deref.
  ;; (EVE-FIX-PLAN §0 measured the outside-built variant of this row;
  ;; F1's flat packing is what fixes both.)
         (let [a   (fresh-atom ::e4)
               cap (volatile! nil)]
              (swap! a (fn [m] (vreset! cap (->Box 7)) m))
              (is (some? @cap))
              (reset! a {:db @cap})
              (is (= 7 ($ (-n (:db @a)))))))

(deftest e5-dollar-returns-flat-stable-value
  ;; E5: ($ ...) delivers a flat value (EVE-FIX F2): what it returns
  ;; stays correct after 1,000 further swaps on the atom. Today: $
  ;; hands out a live Box pointing into the atom, unpinned.
         (let [a    (fresh-atom ::e5)
               kept ($ (:db @a))]
              (dotimes [i 1000]
                       (swap! a (fn [m] (assoc m :db (->Box (+ 100 i))))))
              (is (= 1 (-n kept)))))

(deftest e6-same?
  ;; E6: eve/same? (EVE-FIX F3) — true when two handles name the same
  ;; block of the same domain, false for distinct blocks, where
  ;; identical? and = are both false on deftype handles. PENDING
  ;; until eve/same? resolves in eve.alpha; see the ns docstring.
         (if-let [same? (resolve 'eve.alpha/same?)]
                 (let [a (eve/atom {:id ::e6} nil)]
                      (swap! a (fn [_] {:db (->Box 1) :other (->Box 1)}))
                      (is (true?  ($ (same? (:db @a) (:db @a)))))
                      (is (false? ($ (same? (:db @a) (:other @a))))))
                 (is true "PENDING: eve/same? not in eve.alpha yet (EVE-FIX F3)")))

(deftest e7-old-root-kept-beside-new-stays-readable
  ;; E7: an old root kept in the atom beside the new one stays
  ;; readable. This row is green today (EVE-FIX-PLAN §0) and is the
  ;; mechanism DS-IN-EVE v2 §4's :kept map relies on.
         (let [a (eve/atom {:id ::e7} nil)]
              (swap! a (fn [_] {:db (->Box 1)}))
              (swap! a (fn [m] {:db (->Box 2) :kept (:db m)}))
              (is (= 2 ($ (-n (:db @a)))))
              (is (= 1 ($ (-n (:kept @a)))))))

(deftest e8-swap-return-not-used
  ;; E8 (documentation row): swap!'s return value is slab-backed and
  ;; unsafe outside the swap, so transact flows never use it
  ;; (DS-IN-EVE v2 §2/§4). This asserts a transact-shaped flow —
  ;; tx-data and tempids copied out as host data through a volatile!
  ;; inside the swap fn — completes and is fully readable without
  ;; ever touching swap!'s return value.
         (let [a   (fresh-atom ::e8)
               cap (volatile! nil)]
              (swap! a (fn [m]
                           (let [db' (->Box 2)]
                                (vreset! cap {:tx-data [[:db/add 1 :x 2]]
                                              :tempids {-1 1}
                                              :db-after-n 2})
                                (assoc m :db db'))))
              (is (= [[:db/add 1 :x 2]] (:tx-data @cap)))
              (is (= {-1 1} (:tempids @cap)))
              (is (= 2 ($ (-n (:db @a)))))))
