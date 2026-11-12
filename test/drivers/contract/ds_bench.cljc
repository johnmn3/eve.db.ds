(ns contract.ds-bench
  "Eve-DataScript vs stock DataScript — the driver behind Eve's
   doc/performance.md section 1 table, RECONSTRUCTED in this repo.
   The original contract.ds-bench existed in no current tree (perf
   PLAN gap 1); this driver family reproduces its contract from the
   doc: same five shapes, same sizes, two arms, table rows printed
   in performance.md section 1's shape.

   THE ARMS RUN IN SEPARATE PROCESSES (scripts/ds_bench.sh drives
   both and merges): stock DataScript's compiled code links the Java
   classes of the real persistent-sorted-set jar, while eve.db.ds
   runs on this repo's vendored PSS port (lib/) — the jar both
   shadows the port on a shared classpath and is the only source of
   those classes, so no single classpath serves both arms.
   - THIS namespace is the Eve arm: this repo's eve.db.ds, the
     flagship shape — swap! on a journaled persistent Eve atom
     domain (the release default). Prints {:arm :eve ...} EDN rows.
   - contract.ds-bench-stock is the stock arm: upstream DataScript
     1.8.1 (Maven dep, isolated in test/bench-stock/deps.edn) with
     the DB in a clojure.core/atom — stock has no persistent domain,
     so this is its heap-atom twin of the same shapes.
   - contract.ds-bench-table merges the two arms' EDN rows
     (medians across reps) and prints the doc's markdown tables.
   The stock bb column prints GAPPED: stock on bb needs bb v1.13.226
   rebuilt natively with datascript 1.8.1 (PLAN gap 2).

   Person generator: the repo's deterministic one (ds-harness.perf,
   generalized from people-1k to size n): person i in 1..n is
   {:db/id i :name \"person-<i>\" :age (mod (+ (* i 37) 11) 90)
    :friend [1+(mod i n) 1+(mod (+ i 7) n)]} — 4 datoms per person.
   KEEP IN SYNC with contract.ds-bench-stock's copy.
   Schema: {:name {:db/index true}
            :friend {:db/valueType :db.type/ref
                     :db/cardinality :db.cardinality/many}}.

   Shapes (per size n; single-process timings — medians across
   fresh processes are scripts/ds_bench.sh's job):
   1. batched build — 100-person swaps; total ms for all n people.
   2. bulk-load — all datoms in swaps of at most 2,000 datoms
      ([:db/add e a v] tuples); total ms.
   3. one person per swap — ms per swap, first min(500,n) people.
   4. one datom per swap — ms per swap, x100 single-datom swaps.
   5. retract one entity per swap — ms per swap, first min(500,n)
      entities of a pre-built db (the build itself is not timed).

   Usage (via harness/run-jvm.sh): contract.ds-bench <domain-dir> [size...]
   The domain dir must be FRESH (the Eve arm's atoms persist in it).
   Default sizes: 500 1000."
  (:refer-clojure :exclude [swap!])
  (:require [eve.host :refer [swap!]]
            [eve.atom :as atom]
            [eve.db.ds.core :as ed]))

(def schema
  {:name {:db/index true}
   :friend {:db/valueType :db.type/ref
            :db/cardinality :db.cardinality/many}})

(defn person [n i]
  {:db/id i
   :name (str "person-" i)
   :age (mod (+ (* i 37) 11) 90)
   :friend [(inc (mod i n)) (inc (mod (+ i 7) n))]})

(defn people [n] (mapv #(person n %) (range 1 (inc n))))

(defn datom-tuples [n]
  (vec (mapcat (fn [{:keys [db/id name age friend]}]
                 [[:db/add id :name name]
                  [:db/add id :age age]
                  [:db/add id :friend (nth friend 0)]
                  [:db/add id :friend (nth friend 1)]])
               (people n))))

(defn now-ms [] (double (/ (System/nanoTime) 1e6)))

(defmacro timed-ms [& body]
  `(let [t0# (now-ms) r# (do ~@body)] [(- (now-ms) t0#) r#]))

(def shapes [:batched-build :bulk-load :one-person-per-swap
             :one-datom-per-swap :retract-per-swap])

(defn- eve-atom [domain name]
  (let [a (atom/lookup-or-create-mmap-atom! domain name nil)]
    (when (nil? @a) (swap! a (fn [_] (ed/empty-db schema))))
    a))

(defn eve-shapes [domain n]
  (let [ps (people n)
        tuples (datom-tuples n)
        k (min 500 n)]
    {:batched-build
     (let [a (eve-atom domain (str "ds-bench/eve-batched-" n))
           [ms _] (timed-ms (doseq [batch (partition-all 100 ps)]
                              (swap! a (fn [db] (ed/db-with db batch)))))]
       ms)
     :bulk-load
     (let [a (eve-atom domain (str "ds-bench/eve-bulk-" n))
           [ms _] (timed-ms (doseq [chunk (partition-all 2000 tuples)]
                              (swap! a (fn [db] (ed/db-with db chunk)))))]
       ms)
     :one-person-per-swap
     (let [a (eve-atom domain (str "ds-bench/eve-one-person-" n))
           [ms _] (timed-ms (doseq [p (take k ps)]
                              (swap! a (fn [db] (ed/db-with db [p])))))]
       (/ ms k))
     :one-datom-per-swap
     (let [a (eve-atom domain (str "ds-bench/eve-one-datom-" n))
           [ms _] (timed-ms (doseq [i (range 1 101)]
                              (swap! a (fn [db] (ed/db-with db [[:db/add i :name (str "person-" i)]])))))]
       (/ ms 100))
     :retract-per-swap
     (let [a (eve-atom domain (str "ds-bench/eve-retract-" n))]
       (doseq [batch (partition-all 100 ps)]
         (swap! a (fn [db] (ed/db-with db batch))))
       (let [[ms _] (timed-ms (doseq [e (range 1 (inc k))]
                                (swap! a (fn [db] (ed/db-with db [[:db.fn/retractEntity e]])))))]
         (/ ms k)))}))

(defn -main [& [dir & sizes]]
  (let [dir (or dir (str (System/getProperty "java.io.tmpdir") "/ds-bench"))
        sizes (if (seq sizes) (mapv #(Long/parseLong %) sizes) [500 1000])
        domain (atom/persistent-atom-domain dir)]
    (doseq [n sizes]
      (let [rows (eve-shapes domain n)]
        (doseq [shape shapes]
          (println (pr-str {:arm :eve :size n :shape shape :ms (get rows shape)})))))
    (flush)))
