(ns contract.ds-matrix
  "One entry for the README host table (scripts/ds_matrix.sh): the SAME
   shape code on every Eve host, behind that host's start-up. It times
   nothing itself.

     bench   <dir> <label> <size>...  contract.ds-bench/eve-shapes, the
                                      five write shapes; one
                                      {:arm :eve ...} row per shape and size
     runtime <dir> <label>            contract.ds-runtime/-main, the
                                      people-1k reads row

   Start-up: Eve's host completion loads first (the eve.runner.<host>-main
   pattern; bb and basilisp need it before the port analyzes), and
   ClojureScript hosts initialize the process slab before any value is
   made (the nbb entry's shape). The JVM table cells run contract.ds-bench
   and contract.ds-runtime directly; this entry is for the other hosts."
  (:require #?@(:clje [] ;; Eve's clojerl runner loads no completion (its intern is unimplemented there)
                :default [[eve.host-completion]])
            #?(:cljs [eve.process-slab :as ps])
            [contract.ds-bench :as bench]
            [contract.ds-runtime :as rt]))

(defn- size [s] (if (string? s) (parse-long s) s))

(defn run [mode dir label sizes]
  (case mode
    "bench" (doseq [n (map size sizes)]
              (let [rows (bench/eve-shapes dir n)]
                (doseq [shape bench/shapes]
                  (println (pr-str {:arm :eve :host label :size n :shape shape
                                    :ms (get rows shape)})))))
    "runtime" (rt/-main dir label)
    (println (pr-str {:fatal (str "unknown mode " mode)})))
  (flush))

(defn -main [& [mode dir label & sizes]]
  #?(:cljs (-> (ps/init! :force true)
               (.then (fn [_] (run mode dir label sizes)))
               (.catch (fn [e] (println (pr-str {:fatal (str e) :stack (.-stack e)})))))
     :default (run mode dir label sizes)))
