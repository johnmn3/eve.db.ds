(ns eve.db.ds.u2-support
    "Harness for the U2 slices (db / index / components), ported from
   test/upstream-ds. The upstream tests call eve.db.ds.core (U3, not
   landed); here the same assertions run against eve.db.ds.db-level
   fns, with every DB built inside swap! and every read inside $,
   per DS-IN-EVE-PLAN v2 §1. Assertions compute their values inside
   $ (fully realized) and compare outside it."
    (:require [clojure.test :as t]
              [eve.alpha :as eve :refer [$]]
              [eve.db.ds.db :as db])
    #?(:cljs (:require-macros [eve.db.ds.test-macros])))

;; thrown-msg? — same assert-expr the upstream suite uses (defined
;; there in eve.db.ds.test.core, which cannot load until core does).
#?(:clj
   (defmethod t/assert-expr 'thrown-msg? [msg form]
              (let [[_ match & body] form]
                   `(try ~@body
                         (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual nil})
                         (catch Throwable e#
                                (let [m# (.getMessage e#)]
                                     (if (= ~match m#)
                                         (t/do-report {:type :pass, :message ~msg, :expected '~form, :actual e#})
                                         (t/do-report {:type :fail, :message ~msg, :expected '~form, :actual e#})))
                                e#)))))

(defn no-namespace-maps
      "The upstream suite's fixture (eve.db.ds.test.core/no-namespace-maps):
   schema-validation error messages print spec maps, and the expected
   strings are written for *print-namespace-maps* false."
      [f]
      (binding [*print-namespace-maps* false]
               (f)))

(defonce ^:private id-counter (atom 0))

(defn new-conn
      "A fresh Eve atom holding {:db nil}; the U2 stand-in for a conn
   (core/conn are U3). Each test gets its own atom."
      []
      (eve/atom {:id (keyword (str "u2-test-" (swap! id-counter inc)))} nil))

(defn build!
      "Replace the atom's DB with (f current-db), inside swap!."
      [a f]
      (swap! a (fn [m] {:db (f (:db m))}))
      a)

(defn db-with
      "db-level db-with: transact tx-data into db via transact-tx-data
   with a plain-map report (the impl reads the report by keyword)."
      [db tx-data]
      (:db-after (db/transact-tx-data {:db-before db :db-after db
                                       :tx-data [] :tempids {}}
                                      tx-data)))

;; Plain-fn wrappers around the IIndexAccess protocol fns. The
;; upstream tests call core fns (d/datoms, ...) of exactly this
;; shape; the wrappers are also LOAD-BEARING at this pin: a direct
;; protocol-fn call inside the body of a SECOND thrown-msg?
;; assert-expr expansion in a file fails to compile ("No single
;; method: _datoms of interface IIndexAccess found for function
;; -datoms of protocol IIndexAccess" — measured, u2-logs probes
;; 10-17; one expansion is fine, plain try/catch is fine, two
;; expansions with plain bodies are fine). Routing the call through
;; a defn dissolves it, same as upstream's shape.
(defn datoms       [db index & cs] (apply db/-datoms db index (take 4 (concat cs (repeat nil)))))
(defn seek-datoms  [db index & cs] (apply db/-seek-datoms db index (take 4 (concat cs (repeat nil)))))
(defn rseek-datoms [db index & cs] (apply db/-rseek-datoms db index (take 4 (concat cs (repeat nil)))))
(defn index-range  [db attr start end]   (db/-index-range db attr start end))
