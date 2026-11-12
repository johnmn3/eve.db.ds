(ns contract.ds-bench-stock
  "Stock arm of the Eve-vs-stock bench (see contract.ds-bench for the
   full contract): upstream DataScript 1.8.1 with the DB in a
   clojure.core/atom — stock has no persistent domain, so this is its
   heap-atom twin of the same five shapes.

   Runs in its OWN process and deps context (test/bench-stock/,
   harness/run-bench-stock.sh): stock's compiled code links the Java
   classes of the real persistent-sorted-set jar, which must neither
   shadow eve.db.ds's vendored PSS port nor be shadowed by it.

   The person generator below is a verbatim copy of
   contract.ds-bench's — KEEP IN SYNC.
   Usage: contract.ds-bench-stock [size...]  (default 500 1000)
   Prints {:arm :stock ...} EDN rows, one per shape per size."
  (:require [datascript.core :as sd]))

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

(defn stock-shapes [n]
  (let [ps (people n)
        tuples (datom-tuples n)
        k (min 500 n)
        fresh (fn [] (atom (sd/empty-db schema)))]
    {:batched-build
     (let [a (fresh)
           [ms _] (timed-ms (doseq [batch (partition-all 100 ps)]
                              (swap! a (fn [db] (sd/db-with db batch)))))]
       ms)
     :bulk-load
     (let [a (fresh)
           [ms _] (timed-ms (doseq [chunk (partition-all 2000 tuples)]
                              (swap! a (fn [db] (sd/db-with db chunk)))))]
       ms)
     :one-person-per-swap
     (let [a (fresh)
           [ms _] (timed-ms (doseq [p (take k ps)]
                              (swap! a (fn [db] (sd/db-with db [p])))))]
       (/ ms k))
     :one-datom-per-swap
     (let [a (fresh)
           [ms _] (timed-ms (doseq [i (range 1 101)]
                              (swap! a (fn [db] (sd/db-with db [[:db/add i :name (str "person-" i)]])))))]
       (/ ms 100))
     :retract-per-swap
     (let [a (fresh)]
       (doseq [batch (partition-all 100 ps)]
         (swap! a (fn [db] (sd/db-with db batch))))
       (let [[ms _] (timed-ms (doseq [e (range 1 (inc k))]
                                (swap! a (fn [db] (sd/db-with db [[:db.fn/retractEntity e]])))))]
         (/ ms k)))}))

(defn -main [& sizes]
  (let [sizes (if (seq sizes) (mapv #(Long/parseLong %) sizes) [500 1000])]
    (doseq [n sizes]
      (let [rows (stock-shapes n)]
        (doseq [shape shapes]
          (println (pr-str {:arm :stock :size n :shape shape :ms (get rows shape)})))))
    (flush)))
