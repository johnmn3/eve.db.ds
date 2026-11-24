(ns contract.ds-matrix
  "One entry for the README host table (scripts/ds_matrix.sh): the SAME
   shape code on every Eve host, behind that host's start-up. It times
   nothing itself.

     bench   <dir> <label> [:shape] <size>...  contract.ds-bench/eve-shapes,
                                      the five write shapes (or the one
                                      named); one {:arm :eve ...} row per
                                      shape and size
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
    "bench" (let [one (bench/shape-arg (first sizes))
                  sizes (if one (rest sizes) sizes)]
              (doseq [n (map size sizes)]
                (if one
                  ;; one shape per process (scripts/ds_matrix.sh's 180 s cap)
                  (do (println (pr-str {:arm :eve :host label :size n :shape one
                                        :ms (bench/eve-shape dir n one)}))
                      (flush))
                  ;; the five shapes in order, each row printed as it
                  ;; finishes, so a capped process keeps the shapes it ran
                  (doseq [shape bench/shapes]
                    (println (pr-str {:arm :eve :host label :size n :shape shape
                                      :ms (bench/eve-shape dir n shape)}))
                    (flush)))))
    "runtime" (rt/-main dir label)
    (println (pr-str {:fatal (str "unknown mode " mode)})))
  (flush))

(defn -main [& [mode dir label & sizes]]
  #?(:cljs (-> (ps/init! :force true)
               (.then (fn [_] (run mode dir label sizes)))
               (.catch (fn [e] (println (pr-str {:fatal (str e) :stack (.-stack e)})))))
     :default (run mode dir label sizes)))
