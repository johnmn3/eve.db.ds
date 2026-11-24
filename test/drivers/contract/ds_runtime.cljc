(ns contract.ds-runtime
  "eve.db.ds runtime on the rewrite's public API (2026-11-22, eve-perf lane).
   ds-harness.perf (people-1k) no longer runs at this ds sha: it stores
   (d/empty-db) in an mmap atom, which the rewrite refuses (a conn handle
   is held by reference), and names d/read-in and d/region-escape-counts,
   which are gone. This driver measures the same people-1k shapes the way
   contract.one opens a DB: a persistent conn on the given directory.

     build      10 transact! x 100 people (each with 2 :friend refs)
     find       1000 x (d/datoms db :avet :name ...) first
     pull       1000 x (d/pull db [:name :age {:friend [:name]}] e)
     query      the 7 queries of ds-harness.perf's key set, once
     seq        reduce over (d/datoms db :eavt)
     tx-single  100 x transact! of one [:db/add e :age v]
   The key set is checked against a heap conn built the same way before
   the row prints (:keys-exact). Times in ms, one process."
  (:require [eve.db.ds.core :as d]
            [eve.core :as e]
            [eve.clock :as clock]))

(def ^:private schema
  {:name {:db/index true}
   :friend {:db/valueType :db.type/ref :db/cardinality :db.cardinality/many}})

(defn- person [i]
  {:db/id i :name (str "person-" i) :age (mod (+ (* i 37) 11) 90)
   :friend [(inc (mod i 1000)) (inc (mod (+ i 7) 1000))]})

(defn- now-ms []
  #?(:jank (double (clock/current-time-millis))
     :clj (/ (double (System/nanoTime)) 1e6)
     :cljs (js/performance.now)
     :default (double (clock/current-time-millis))))

(defn- key-set [db]
  {:entities (count (d/q '[:find [?e ...] :where [?e :name _]] db))
   :datoms (e/count (d/datoms db :eavt))
   :names (count (d/q '[:find [?n ...] :where [_ :name ?n]] db))
   :age-sum (reduce + (d/q '[:find [?a ...] :where [_ :age ?a]] db))
   :friends (e/count (d/datoms db :aevt :friend))
   :age-34 (count (d/q '[:find [?e ...] :where [?e :age 34]] db))
   :max-age (reduce max (d/q '[:find [?a ...] :where [_ :age ?a]] db))})

(defn- build! [conn]
  (doseq [b (range 10)]
    (d/transact! conn (mapv person (range (inc (* b 100)) (inc (* (inc b) 100)))))))

(defn- r2 [x]
  ;; basilisp has no Math/round (it fails at macroexpansion); same rounding
  #?(:lpy (/ (python/round (* 100.0 x)) 100.0)
     :jank (/ (long (+ (* 100.0 x) 0.5)) 100.0)
     :default (/ (Math/round (* 100.0 x)) 100.0)))

(defn -main [& [dir label]]
  (let [heap (d/create-conn schema)
        _ (build! heap)
        expected (key-set (d/db heap))
        t0 (now-ms)
        conn (d/create-conn schema {:persistent dir})
        t1 (now-ms)
        _ (build! conn)
        t2 (now-ms)
        db (d/db conn)
        _ (dotimes [i 1000] (first (d/datoms db :avet :name (str "person-" (inc i)))))
        t3 (now-ms)
        _ (dotimes [i 1000] (d/pull db [:name :age {:friend [:name]}] (inc i)))
        t4 (now-ms)
        actual (key-set db)
        t5 (now-ms)
        n (reduce (fn [c _] (inc c)) 0 (d/datoms db :eavt))
        t6 (now-ms)
        _ (dotimes [i 100] (d/transact! conn [[:db/add (inc i) :age (mod i 90)]]))
        t7 (now-ms)]
    (println (pr-str {:ds-runtime (or label "host") :keys-exact (= expected actual)
                      :create-ms (r2 (- t1 t0)) :build-ms (r2 (- t2 t1)) :find-1000-ms (r2 (- t3 t2))
                      :pull-1000-ms (r2 (- t4 t3)) :query-7-ms (r2 (- t5 t4)) :seq-ms (r2 (- t6 t5))
                      :seq-count n :tx-single-100-ms (r2 (- t7 t6))
                      :tx-single-us (r2 (* 10 (- t7 t6)))}))
    (flush)))
