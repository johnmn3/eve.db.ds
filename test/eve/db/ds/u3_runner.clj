(ns eve.db.ds.u3-runner
    "U3 slice runner (JVM): the conn, listen, upsert slices in full,
   plus the transact slice's runnable deftests and the stored
   :db/fn feature slice (eve.db.ds.test.db-fn). No transact parks
   remain: GAP-U3-1 was fixed by U4 (parks lifted by U6), and
   GAP-U3-5 (stored :db/fn) is RESOLVED (John's rulings,
   2026-11-20; DB-FN-STATUS.md) — test-db-ident-fn is unparked,
   reshaped to run in the default (host), SCI opt-in and resolve
   stored modes, and eve.db.ds.test.db-fn carries the feature
   proofs.

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
     [eve.db.ds.test.db-fn]
     [eve.db.ds.test.transact]))

(def parked-transact
     "Transact deftests parked: none. GAP-U3-1's parks were lifted by
   U6 and GAP-U3-5's park by the stored :db/fn implementation."
     '[])

(defn- test-vars-in [ns-sym]
       (->> (ns-interns ns-sym)
            vals
            (filter (fn [v] (:test (meta v))))))

(defn -main [& _]
      (println "PARKED:"
               (mapv #(symbol "eve.db.ds.test.transact" (str %)) parked-transact))
      (let [s1 (t/run-tests 'eve.db.ds.test.conn 'eve.db.ds.test.listen 'eve.db.ds.test.upsert 'eve.db.ds.test.db-fn)
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
