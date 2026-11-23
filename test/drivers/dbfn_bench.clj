(ns dbfn-bench
    "Comparison measurement for the two stored :db/fn kinds
   (DB-FN-STATUS.md): N ident-invocations through transact! on a
   small conn, per kind, plus the non-storable host-fn call-site
   path as the speed ceiling. The fn body is minimal
   ([db n] -> [[:db/add 1 :n n]]) so the numbers isolate invocation
   cost; every mode pays the same transact!/swap!/report cost.

   CELL-CLASS numbers: measured on the shared hatch cell (1.5
   shared vCPUs, throttled) — ratios are the signal, absolutes
   are smoke-class, not venue measurements.

   Run: clojure -Sdeps '{:paths [\"src\" \"test\"]}' -M -m dbfn-bench"
    (:require
     [eve.db.ds.core :as d]
     [eve.db.ds.db-fn :as dbfn]))

(def N 200)

(defn- time-ms [f]
       (let [t0 (System/nanoTime)]
            (f)
            (/ (double (- (System/nanoTime) t0)) 1e6)))

(defn- bench [label txf]
  ;; 3 warmup invocations, then 3 measured rounds of N
       (dotimes [i 3] (txf i))
       (let [rounds (mapv (fn [_] (time-ms #(dotimes [i N] (txf i)))) (range 3))
             med    (nth (sort rounds) 1)]
            {:label label :rounds-ms (mapv #(Math/round ^double %) rounds)
             :median-ms (Math/round med) :per-invoke-us (Math/round (/ (* med 1000.0) N))}))

(defn -main [& _]
      (let [host-fn (fn [db n] [[:db/add 1 :n n]])
            sci-stored (d/sci-fn [db n] [[:db/add 1 :n n]])
        ;; cold: first-ever SCI invoke (context init + compile)
            cold-conn (d/create-conn)
            _ (d/transact! cold-conn [{:db/id 1} {:db/ident :b :db/fn sci-stored}])
            cold-ms (time-ms #(d/transact! cold-conn [[:b 0]]))
        ;; distinct-fn compile cost: N distinct stored maps, one
        ;; invoke each (every invoke is a cache miss + compile)
            distinct-conn (d/create-conn)
            _ (d/transact! distinct-conn [{:db/id 1}])
            distinct-ms (time-ms
                         #(dotimes [i N]
                                   (d/transact! distinct-conn
                                                [[:db.fn/call
                                                  (dbfn/sci-fn '[db n]
                                                               (str "[[:db/add 1 :n (+ n " i ")]]"))
                                                  i]])))
            sci-conn (d/create-conn)
            _ (d/transact! sci-conn [{:db/id 1} {:db/ident :b :db/fn sci-stored}])
            res-conn (d/create-conn)
            _ (d/register-fn! 'dbfn-bench/f host-fn)
            _ (d/transact! res-conn [{:db/id 1} {:db/ident :b :db/fn (d/fn-ref 'dbfn-bench/f)}])
            host-conn (d/create-conn)
            _ (d/transact! host-conn [{:db/id 1}])
        ;; compute-heavy body: where interpretation overhead shows
            compute-host (fn [db n]
                             (loop [i 1 acc 0]
                                   (if (> i 1000) [[:db/add 1 :n acc]] (recur (inc i) (+ acc i)))))
            compute-sci (d/sci-fn [db n]
                                  (loop [i 1 acc 0]
                                        (if (> i 1000) [[:db/add 1 :n acc]] (recur (inc i) (+ acc i)))))
            csci-conn (d/create-conn)
            _ (d/transact! csci-conn [{:db/id 1} {:db/ident :b :db/fn compute-sci}])
            _ (d/register-fn! 'dbfn-bench/compute compute-host)
            cres-conn (d/create-conn)
            _ (d/transact! cres-conn [{:db/id 1} {:db/ident :b :db/fn (d/fn-ref 'dbfn-bench/compute)}])
            rows [(bench "resolve (registry host fn)" #(d/transact! res-conn [[:b %]]))
                  (bench "sci (warm, compiled + cached)" #(d/transact! sci-conn [[:b %]]))
                  (bench "host fn via [:db.fn/call f] (not storable)" #(d/transact! host-conn [[:db.fn/call host-fn %]]))
                  (bench "resolve, compute body (sum 1..1000)" #(d/transact! cres-conn [[:b %]]))
                  (bench "sci, compute body (sum 1..1000)" #(d/transact! csci-conn [[:b %]]))]]
           (println "DB-FN BENCH — N =" N "invocations/round, median of 3 (cell-class, hatch shared cell)")
           (doseq [r rows] (println r))
           (println {:label "sci cold (first invoke: ctx init + compile)" :ms (Math/round cold-ms)})
           (println {:label "sci distinct-fn (compile per invoke)" :rounds-ms [(Math/round distinct-ms)]
                     :per-invoke-us (Math/round (/ (* distinct-ms 1000.0) N))})
           (d/unregister-fn! 'dbfn-bench/f)
           (d/unregister-fn! 'dbfn-bench/compute)
    ;; machinery-free: db-fn/invoke directly on a raw DB, 2000
    ;; invokes of the compute body — isolates body execution cost
    ;; from the transact machinery that dominates the rows above
           (let [db      (d/empty-db)
                 host    (fn [db n] (loop [i 1 acc 0]
                                          (if (> i 1000) [[:db/add 1 :n acc]] (recur (inc i) (+ acc i)))))
                 sci-st  (d/sci-fn [db n] (loop [i 1 acc 0]
                                                (if (> i 1000) [[:db/add 1 :n acc]] (recur (inc i) (+ acc i)))))]
                (d/register-fn! 'dbfn-bench/micro host)
                (let [res-st (d/fn-ref 'dbfn-bench/micro)
                      micro  (fn [label f]
                                 (f)
                                 (let [t0 (System/nanoTime)]
                                      (dotimes [_ 2000] (f))
                                      {:label label
                                       :per-invoke-us (Math/round (/ (double (- (System/nanoTime) t0)) 2000 1000.0))}))]
                     (println (micro "micro: host fn direct" #(host db 0)))
                     (println (micro "micro: resolve invoke" #(dbfn/invoke db res-st [0])))
                     (println (micro "micro: sci invoke (warm)" #(dbfn/invoke db sci-st [0]))))
                (d/unregister-fn! 'dbfn-bench/micro))
           (System/exit 0)))
