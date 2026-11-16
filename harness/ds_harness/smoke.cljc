(ns ds-harness.smoke
  "The shootout's smoke cases (scripts/perf_shootout.sh SMOKE=1 / --smoke):
   short, small-N, one cold process per case. Every host runs the SAME
   cases through its harness entry (run-<host>.sh smoke <case> <dir>).

     one     fresh durable domain, one datum (the classic shootout case)
     reopen  the domain a previous `one` left on disk: open it, add one
             datum, count — the durable wake path (nothing rebuilt)
     small   fresh durable domain, 20 entities in one transaction,
             one query, one pull

   Prints one EDN line with :mark :ONE-DONE (what harness/shootout.sh
   counts) and the in-process phase ms (:open :tx :q :pull :main), where
   :main is -main entry to done; the shell's wall minus :main is process
   start + namespace load."
  #?(:clje (:refer-clojure :exclude [swap!]))
  (:require [eve.atom :as atom]
            [eve.clock :as clock]
            [eve.core :as e]
            [eve.db.ds.core :as d]
            ;; clojerl's swap! takes a clojerl.Atom only; eve.host's takes an Eve atom
            #?(:clje [eve.host :refer [swap!]])))

(def ^:private atom-name "contract/ds-cli-update-db")

(defn- now [] (clock/current-time-millis))

(defn- open [dir]
  (let [dom (atom/persistent-atom-domain dir)]
    (atom/lookup-or-create-mmap-atom! dom atom-name nil)))

(defn- done [case-name t0 ms extra]
  (println (pr-str (merge {:mark :ONE-DONE :case case-name
                           :ms (assoc ms :main (- (now) t0))}
                          extra)))
  (flush))

(defn- case-one [dir reopen?]
  (let [t0 (now)
        a  (open dir)
        t1 (now)
        _  (when (nil? @a)
             (when reopen?
               (throw (ex-info "smoke reopen: no durable DB at dir (run `one` on it first)" {:dir dir})))
             (swap! a (fn [_] (d/empty-db))))
        _  (swap! a (fn [db] (d/db-with db [{:name "one" :age 1}])))
        t2 (now)
        n  (e/count (d/datoms @a :eavt))
        t3 (now)]
    (done (if reopen? :reopen :one) t0 {:open (- t1 t0) :tx (- t2 t1) :q (- t3 t2)} {:datoms n})))

;; small enough that the slowest member (basilisp: ~1.4 s per entity at
;; 100 entities in one transaction) keeps the smoke run in minutes
(def ^:private small-n 20)

(defn- case-small [dir]
  (let [t0 (now)
        a  (open dir)
        t1 (now)
        _  (swap! a (fn [_] (d/empty-db {:name {:db/unique :db.unique/identity}})))
        _  (swap! a (fn [db] (d/db-with db (mapv (fn [i] {:db/id (inc i) :name (str "p" i) :age (mod i 90)})
                                                 (range small-n)))))
        t2 (now)
        ;; a plain find (no aggregate: bb's aggregate path is a known
        ;; suite red, not a startup measure)
        r  (e/count (d/q '[:find ?e :where [?e :age ?a] [(< ?a 30)]] @a))
        t3 (now)
        p  (d/pull @a [:name :age] [:name "p7"])
        t4 (now)]
    (done :small t0 {:open (- t1 t0) :tx (- t2 t1) :q (- t3 t2) :pull (- t4 t3)}
          {:datoms (e/count (d/datoms @a :eavt)) :q r :pull-age (:age p)})))

(defn -main [& [case-name dir]]
  (case case-name
    "one"    (case-one dir false)
    "reopen" (case-one dir true)
    "small"  (case-small dir)
    (println (pr-str {:fatal (str "unknown smoke case " case-name)}))))
