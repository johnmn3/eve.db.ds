(ns eve.db.ds.u3-runner
  "U3 slice runner (JVM): the conn, listen, upsert slices in full,
   plus the transact slice's runnable deftests. One transact park,
   printed on every run so the exclusion is not silent:

   - GAP-U3-5 (stored :db/fn): test-db-ident-fn stores a host fn as
     a datom value; a fn is not an Eve value (delivery pack throws
     the flat-chunk unwritable error), so an Eve-resident DB cannot hold
     it. See U3-STATUS.md.

   The six GAP-U3-1 parks were lifted by U6: GAP-U3-1 was fixed by
   U4's arrays.cljc and GAP-7 by U1c (both merged), so test-with,
   test-retract-fns, test-transact!, test-db-fn,
   test-resolve-eid-refs and test-resolve-current-tx run again.

   Run: clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m eve.db.ds.u3-runner
   Exit code is 0 only when every run test passes."
  (:require
    [clojure.test :as t]
    [eve.db.ds.test.conn]
    [eve.db.ds.test.listen]
    [eve.db.ds.test.upsert]
    [eve.db.ds.test.transact]))

(def parked-transact
  "Transact deftests parked on GAP-U3-5 (stored :db/fn) only."
  '[test-db-ident-fn])

(defn- test-vars-in [ns-sym]
  (->> (ns-interns ns-sym)
    vals
    (filter (fn [v] (:test (meta v))))))

(defn -main [& _]
  (println "PARKED (GAP-U3-5 stored :db/fn):"
    (mapv #(symbol "eve.db.ds.test.transact" (str %)) parked-transact))
  (let [s1 (t/run-tests 'eve.db.ds.test.conn 'eve.db.ds.test.listen 'eve.db.ds.test.upsert)
        transact-vars (remove (fn [v] (contains? (set parked-transact) (:name (meta v))))
                        (test-vars-in 'eve.db.ds.test.transact))
        counters (binding [t/*report-counters* (ref t/*initial-report-counters*)]
                   (t/test-vars (vec transact-vars))
                   @t/*report-counters*)
        fail  (+ (:fail s1 0) (:fail counters 0))
        error (+ (:error s1 0) (:error counters 0))
        pass  (+ (:pass s1 0) (:pass counters 0))
        tests (+ (:test s1 0) (:test counters 0))]
    (println "U3 SUMMARY:" {:test tests :pass pass :fail fail :error error})
    (System/exit (if (zero? (+ fail error)) 0 1))))
