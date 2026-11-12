(ns contract.ds-bench-table
  "Merge step for the Eve-vs-stock bench (see contract.ds-bench):
   reads the Eve arm's and stock arm's EDN row files (one file per
   fresh-process rep), takes the MEDIAN of each cell across reps —
   the doc's write cells are medians of 3 fresh processes — and
   prints the merged EDN rows plus the markdown tables in Eve
   doc/performance.md section 1's shape. The stock bb column is
   GAPPED (stock on bb needs the special native bb build; PLAN
   gap 2).

   Usage: contract.ds-bench-table <eve-file,eve-file,...> <stock-file,stock-file,...>
   Pure clojure.core + java.io: runs in either arm's deps context
   (scripts/ds_bench.sh uses harness/run-jvm.sh)."
  (:require [clojure.string :as str]))

(def ^:private shape-rows
  [[:batched-build "batched build, 100-person swaps (total ms)"]
   [:bulk-load "bulk-load, 2,000-datom swaps (total ms)"]
   [:one-person-per-swap "one person per swap (ms per swap; first 500)"]
   [:one-datom-per-swap "one datom per swap (ms per swap; ×100)"]
   [:retract-per-swap "retract one entity per swap (ms per swap; first 500)"]])

(defn- read-rows [file]
  (with-open [r (java.io.PushbackReader. (java.io.FileReader. file))]
    (doall (take-while some? (repeatedly #(read {:eof nil} r))))))

(defn- median [xs]
  (let [s (sort xs) n (count s)]
    (nth s (quot n 2))))

(defn- fmt [x] (format "%.2f" (double x)))

(defn -main [& [eve-files stock-files]]
  (let [eve-rows (mapcat read-rows (str/split eve-files #","))
        stock-rows (mapcat read-rows (str/split stock-files #","))
        cell (fn [rows size shape]
               (median (map :ms (filter #(and (= size (:size %)) (= shape (:shape %))) rows))))
        sizes (sort (distinct (concat (map :size eve-rows) (map :size stock-rows))))]
    (doseq [n sizes]
      (doseq [[shape _] shape-rows]
        (println (pr-str {:size n :shape shape
                          :eve-ms (cell eve-rows n shape)
                          :stock-ms (cell stock-rows n shape)
                          :stock-bb :GAPPED})))
      (println)
      (println (str "**@" n " people**"))
      (println)
      (println "| shape | Eve-DS, JVM | stock 1.8.1 heap atom, JVM | **Eve ÷ stock JVM** | stock 1.8.1 heap atom, bb (native) |")
      (println "|---|---|---:|---:|---|")
      (doseq [[shape label] shape-rows]
        (let [e (cell eve-rows n shape) s (cell stock-rows n shape)]
          (println (str "| " label " | **" (fmt e) "** | " (fmt s)
                        " | **" (Math/round (double (/ e s))) "×** | GAPPED |"))))
      (println))
    (flush)))
