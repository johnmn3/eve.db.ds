(ns ds-harness.nbb-suite-main
  "nbb entry for the ds test suites (U3, bset): the process slab is
   initialized before any value is constructed (the nbb-main
   pattern), then the suite runs under clojure.test and the process
   exits non-zero on any fail/error.

   SCI's clojure.test keeps its counters internal (run-tests returns
   nil and *report-counters* is unbound outside a run), so the totals
   are read back from run-tests' own printed summary, captured with
   with-out-str — the same numbers the JVM runner prints.

   Usage: nbb --classpath <eve :nbb roots>:<ds src:lib:lib-ds-deps:test>:<harness> -m ds-harness.nbb-suite-main <u3|bset>"
  (:require #?(:cljs [eve.process-slab :as ps])
            [clojure.test :as t]
            [eve.db.ds.test.conn]
            [eve.db.ds.test.listen]
            [eve.db.ds.test.upsert]
            [eve.db.ds.test.db-fn]
            [eve.db.ds.test.transact]
            [eve.db.ds.bset-test]))

(defn- run-suites [label ns-syms]
  (let [out (with-out-str (apply t/run-tests ns-syms))
        m   (re-find #"Ran (\d+) tests containing (\d+) assertions\.\s+(\d+) failures, (\d+) errors" out)
        _   (print out)
        [tests pass fail error] (if m
                                  (let [[_ n a f e] m
                                        n (js/parseInt n) a (js/parseInt a)
                                        f (js/parseInt f) e (js/parseInt e)]
                                    [n (- a f e) f e])
                                  [0 0 0 1])]
    (println label {:test tests :pass pass :fail fail :error error})
    (js/process.exit (if (zero? (+ fail error)) 0 1))))

(defn- run-bset-vars
  "bset runs one test var at a time, printing each var's name before
   and after: the suite's stress tests run for minutes of CPU under
   SCI, and on a contended host a whole-suite run can be starved past
   any wall budget — per-var output keeps completed vars banked as
   evidence instead of losing the run whole. Fail/error lines are
   the suite's own (captured per var and reprinted verbatim). An
   optional name filter runs a single var (a starved suite's
   remaining vars, one process each)."
  [only]
  (let [ns-sym 'eve.db.ds.bset-test
        vars   (->> (ns-interns ns-sym)
                    vals
                    (filter (fn [v] (:test (meta v))))
                    (filter (fn [v] (or (nil? only)
                                        (= only (str (:name (meta v)))))))
                    (sort-by (fn [v] (:name (meta v)))))]
    (loop [vs vars, fails 0]
      (if (empty? vs)
        (do (println :BSET-RUNNER-RESULT {:test (count vars) :fail-errors fails})
            (js/process.exit (if (zero? fails) 0 1)))
        (let [v    (first vs)
              _    (println :BSET-VAR-START (:name (meta v))) 
              out  (with-out-str (t/test-var v))
              bad  (count (re-seq #"FAIL in|ERROR in" out))]
          (print out)
          (println :BSET-VAR-DONE (:name (meta v)) :fail-errors bad)
          (recur (rest vs) (+ fails bad)))))))

(defn -main [& [mode only]]
  #?(:cljs (-> (ps/init! :force true)
               (.then (fn [_] (case mode
                                "u3"   (run-suites "U3 SUMMARY:"
                                                   ['eve.db.ds.test.conn 'eve.db.ds.test.listen
                                                    'eve.db.ds.test.upsert 'eve.db.ds.test.db-fn
                                                    'eve.db.ds.test.transact])
                                "bset" (run-bset-vars only)
                                (println (pr-str {:fatal (str "unknown mode " mode)}))))))
     :default (println (pr-str {:fatal "nbb suite main is a cljs entry"}))))
