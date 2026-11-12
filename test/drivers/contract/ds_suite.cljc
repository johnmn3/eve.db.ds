(ns contract.ds-suite
  "Runs DataScript's own upstream test suite against eve.db.ds on any host.
   The suite is test/upstream-ds (upstream tag 1.8.1, commit 34915cf673,
   renamed mechanically datascript. -> eve.db.ds. by
   scripts/vendor_upstream_ds.sh; test bodies unchanged). Classpath root:
   test/upstream-ds/test (JVM: clojure -M:dev:ds-suite -m contract.ds-suite).

   Each namespace is required, then its tests run. A namespace that fails
   to load is counted: loaded stays short of nses AND it adds one ERROR —
   never a silent catch-and-continue. A test run that throws outside an
   assertion adds one ERROR too. Ends with one aggregate line

     DS-SUITE-SUMMARY host=<h> nses=N loaded=L tests=T pass=P fail=F error=E skipped=S

   and exits non-zero on any fail, error or load failure.
   Args: --list, --load-only, or optional namespace symbols.
   scripts/ds_suite.py is the process-isolated per-namespace timeout entry.

   Hosts: JVM and bb (:clj, clojure.test/test-ns), nbb (:cljs,
   cljs.test/test-vars + the env's report counters), basilisp (:lpy,
   basilisp.test deftests are functions answering {:failures [...]}; passes
   are counted by wrapping basilisp.test/is, so a test namespace compiled
   from a stale basilisp cache before this driver loaded would count no
   passes — run with a fresh PYTHONPYCACHEPREFIX). On bb and basilisp load
   eve.host-completion before this namespace, as the contract gates do."
  (:require [eve.host] [eve.atom] [eve.db.ds.db :as ds-db] [clojure.test :as t] [clojure.string :as str])
  #?(:lpy (:import importlib os.path sys)))

#?(:lpy
   (defmethod t/gen-assert 'thrown-msg? [expr msg line-num]
     (let [[_ expected & body] expr
           actual (gensym "actual")]
       `(let [~actual (try ~@body ::not-thrown
                          (catch python/Exception e# (ex-message e#)))]
          ~(t/gen-assert (list '= expected actual) msg line-num)))))

(def host
  #?(:bb "bb" :clj "jvm" :org.babashka/nbb "nbb" :cljs "cljs"
     :lpy "basilisp" :cljr "clr" :default "unknown"))

(def suite-nss
  "upstream test.cljc's list (eve.db.ds.test.cljs is a :cljs side-effect
   namespace with no tests; storage is .clj, :clj-only upstream too)."
  (vec (concat
        '[eve.db.ds.test.core eve.db.ds.test.components eve.db.ds.test.conn
          eve.db.ds.test.db eve.db.ds.test.entity eve.db.ds.test.explode
          eve.db.ds.test.filter eve.db.ds.test.ident eve.db.ds.test.index
          eve.db.ds.test.listen eve.db.ds.test.lookup-refs eve.db.ds.test.lru
          eve.db.ds.test.parser eve.db.ds.test.parser-find
          eve.db.ds.test.parser-return-map eve.db.ds.test.parser-rules
          eve.db.ds.test.parser-query eve.db.ds.test.parser-where
          eve.db.ds.test.pull-api eve.db.ds.test.pull-parser
          eve.db.ds.test.query eve.db.ds.test.query-aggregates
          eve.db.ds.test.query-find-specs eve.db.ds.test.query-fns
          eve.db.ds.test.query-not eve.db.ds.test.query-or
          eve.db.ds.test.query-pull eve.db.ds.test.query-return-map
          eve.db.ds.test.query-rules eve.db.ds.test.query-v3
          eve.db.ds.test.serialize]
        #?(:clj '[eve.db.ds.test.storage] :default [])
        '[eve.db.ds.test.transact eve.db.ds.test.tuples
          eve.db.ds.test.validation eve.db.ds.test.upsert
          eve.db.ds.test.issues eve.db.ds.test.datafy])))

(defn- describe [e]
  #?(:lpy (python/repr e)
     :cljs (str (.-name e) " " (ex-message e))
     :default (str (type e) " " (ex-message e))))

(defn- exit! [code]
  #?(:clj (System/exit code)
     :cljs (js/process.exit code)
     :lpy (sys/exit code)
     :default (when-not (zero? code)
                (throw (ex-info "DS-SUITE-FAILED" {:exit code})))))

(defn- skip-reason [n v]
  (when (and (= n 'eve.db.ds.test.serialize)
             (contains? #{'serialize 'test-nan} (:name (meta v))))
    (case host
      "basilisp" "No Transit implementation is supplied on the Basilisp test classpath"
      nil)))

(defn- test-vars-for [n]
  (let [vs (->> (vals (ns-interns n))
                (filter #(or (:test (meta %)) (:basilisp.test/test (meta %))))
                (sort-by #(str (:name (meta %)))))]
    (doseq [v vs :let [reason (skip-reason n v)] :when reason]
      (println "SKIP" (str n "/" (:name (meta v))) reason))
    (remove #(skip-reason n %) vs)))

;; ---- basilisp: count passes and run one namespace ------------------------

#?(:lpy
   (def lpy-assertions
     "basilisp.test counts failures only; every (is expr msg) compiled after
      this driver loads counts itself here (pass = assertions - fail - error)."
     (volatile! 0)))

#?(:lpy
   (let [v (resolve 'basilisp.test/is)
         is* @v]
     ;; basilisp's (is expr) expands to (is expr msg): count the 2-arg form
     ;; only, or a message-less assertion counts twice.
     (.bind-root v (fn [&env &form & args]
                     (if (= 1 (count args))
                       (apply is* &env &form args)
                       `(do (vswap! @(var contract.ds-suite/lpy-assertions) inc)
                            ~(apply is* &env &form args)))))))

#?(:lpy
   (defn- package-paths!
     "basilisp finds a namespace that is also a directory (eve.db.ds.test:
      test.cljc beside test/) as a package whose submodules are looked for
      only beside that file; add every sys.path root's same-named directory."
     [n]
     (let [parts (str/split (.replace (str n) "-" "_") #"\.")]
       (doseq [i (range 1 (count parts))
               :let [pkg (str/join "." (take i parts))
                     m (importlib/import-module pkg)
                     path (python/getattr m "__path__" nil)]
               :when (and path (seq path))
               root sys/path
               :let [d (python/str (apply os.path/join root (take i parts)))]
               :when (and (os.path/isdir d) (not (some #(= d %) path)))]
         (.append path d)))))

#?(:lpy
   (defn- lpy-join-fixtures [fixtures]
     (reduce (fn [outer fixture] (fn [f] (outer (fn [] (fixture f)))))
             (fn [f] (f))
             fixtures)))

#?(:lpy
   (defn- lpy-test-ns [n]
     (let [tests (map (fn [v] [(:name (meta v)) v]) (test-vars-for n))
           m (meta (find-ns n))
           once (lpy-join-fixtures (:basilisp.test/once-fixtures m))
           each (lpy-join-fixtures (:basilisp.test/each-fixtures m))
           c (atom {:test 0 :pass 0 :fail 0 :error 0})
           before @lpy-assertions]
       (once
        (fn []
          (doseq [[s v] tests]
            (swap! c update :test inc)
            (try
              (let [r (volatile! nil)
                    _ (each (fn [] (vreset! r (@v))))
                    {fails :failure errs :error} (group-by :type (:failures @r))]
                (swap! c update :fail + (count fails))
                (swap! c update :error + (count errs))
                (doseq [f (:failures @r)]
                  (println (if (= :error (:type f)) "ERROR in" "FAIL in") (str n "/" s)
                           (pr-str (select-keys f [:message :expected :actual :test-section])))))
              (catch python/Exception e
                (swap! c update :error inc)
                (println "ERROR in" (str n "/" s) (python/repr e)))))))
       (assoc @c :pass (max 0 (- @lpy-assertions before (:fail @c) (:error @c)))))))

;; ---- one namespace on any host -------------------------------------------

(defn- test-ns* [n]
  #?(:lpy (lpy-test-ns n)
     :cljs (do (t/set-env! (t/empty-env))
               (t/test-vars (test-vars-for n))
               (select-keys (:report-counters (t/get-current-env))
                            [:test :pass :fail :error]))
     :default (select-keys (t/test-ns n) [:test :pass :fail :error])))

;; nbb's runtime require answers a promise, so loading is async there; the
;; other hosts load synchronously. Either way a load failure is one ERROR.

(def ^:private load-error {:loaded 0 :test 0 :pass 0 :fail 0 :error 1 :skipped 0})

(defn- load-failed [n e]
  (println "ERROR loading" n (describe e))
  #?(:lpy nil
     :default (loop [c (ex-cause e) depth 1]
                (when c
                  (println "  caused by" depth (describe c))
                  (recur (ex-cause c) (inc depth)))))
  load-error)

(def load-only? (atom false))

(defn- run-loaded
  "{:loaded 1 :test :pass :fail :error} for the already-loaded namespace n."
  [n]
  (let [r (try (if @load-only? {:test 0 :pass 0 :fail 0 :error 0} (test-ns* n))
               (catch #?(:clj Throwable :cljs :default :lpy python/Exception :default Exception) e
                 (println "ERROR running" n (describe e))
                 {:test 0 :pass 0 :fail 0 :error 1}))]
    (println {:suite-ns n :result r})
    (assoc r :loaded 1 :skipped (if @load-only? 0 (count (filter #(skip-reason n %) (vals (ns-interns n))))))))

(defn- in-test-domain
  "Operator ruling (2026-11-12): the suite driver binds the process
   domain around each namespace's load + run. Upstream's tests
   construct Eve values freely — (d/datom ...), parser ->Records as
   expected values, top-level DBs/conns — and the construction law
   requires a domain context; the driver (ds's own harness, not the
   product, not the test text) supplies the process domain. Product
   paths that need a specific domain bind their own (ambient context
   wins), and the unbound paths stay exercised by the smoke and
   acceptance probes, which do not run under this driver. On :cljs
   the async require cannot be covered by a synchronous binding, so
   only the run is wrapped there."
  [f]
  (ds-db/call-in-process-domain f))

#?(:cljs
   (defn- run-one
     "A promise of the counts for namespace n."
     [n]
     (-> (js/Promise. (fn [res _] (res (require n))))
         (.then (fn [_] (in-test-domain #(run-loaded n)))
                (fn [e] (load-failed n e))))))

#?(:cljs nil
   :default
   (defn- run-one
     "The counts for namespace n."
     [n]
     (in-test-domain
       (fn []
         (if (try #?(:lpy (package-paths! n))
                  (require n)
                  true
                  (catch #?(:clj Throwable :lpy python/Exception :default Exception) e
                    (load-failed n e)
                    false))
           (run-loaded n)
           load-error)))))

(def ^:private zero {:loaded 0 :test 0 :pass 0 :fail 0 :error 0 :skipped 0})

(defn- summarize
  "Prints the summary line; answers the exit code."
  [nses tot]
  (let [n (count nses)]
    (println (str "DS-SUITE-SUMMARY host=" host " nses=" n " loaded=" (:loaded tot)
                  " tests=" (:test tot) " pass=" (:pass tot)
                  " fail=" (:fail tot) " error=" (:error tot) " skipped=" (:skipped tot)))
    (if (and (= n (:loaded tot)) (zero? (:fail tot)) (zero? (:error tot))) 0 1)))

(defn run
  "Runs nses (default suite-nss), prints the summary line and answers the
   exit code (on nbb: a promise of it)."
  [nses]
  #?(:cljs (-> (reduce (fn [p n] (.then p (fn [acc] (.then (run-one n) #(merge-with + acc %)))))
                       (js/Promise.resolve zero)
                       nses)
               (.then #(summarize nses %)))
     :default (summarize nses (reduce (fn [acc n] (merge-with + acc (run-one n))) zero nses))))

(defn -main [& args]
  (if (= "--list" (first args))
    (do (doseq [n suite-nss] (println "DS-SUITE-NS" n)) (exit! 0))
    (do
      (reset! load-only? (= "--load-only" (first args)))
      (let [args (if @load-only? (rest args) args)
            nses (if (seq args) (mapv symbol args) suite-nss)]
        #?(:cljs (-> (run nses)
                     (.then exit!)
                     (.catch (fn [e] (println "ERROR driver" (describe e)) (exit! 1))))
           :default (exit! (run nses)))))))
