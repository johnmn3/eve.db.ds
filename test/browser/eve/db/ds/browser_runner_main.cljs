(ns eve.db.ds.browser-runner-main
  "The DS suites in a browser — scripts/test-browser.clj builds this into a page and drives a headless
   Chromium. Modeled on Eve's eve.runner.thread-browser-main (Eve's scripts/test-browser.clj shape): the
   page's main thread cannot block, so the suite runs in a worker this page starts — eve.thread-host's own,
   by the fat-kernel route — and the page polls for its summary and POSTs it to /result.

   The suite is chosen by window.DS_SUITE, set by the driver's generated index.html: \"laws\" (the
   deftype/atom laws probe, test/eve), \"bset\" (U1's sorted-set suite, test/eve/db/ds) or \"u3\"
   (the U3 slices — conn/listen/upsert/transact whole, plus the stored-:db/fn feature slice; the
   GAP-U3-1 parks are a JVM-runner notion, the browser runs the namespaces as they stand). The test
   namespaces themselves touch Eve through eve.alpha only; eve.thread-host here is the harness's own
   plumbing, exactly as in Eve's runner."
  (:require [cljs.test :as t]
            [eve.thread-host :as h]
            [eve.deftype-atom-laws-test]
            [eve.db.ds.bset-test]
            [eve.db.ds.test.conn]
            [eve.db.ds.test.listen]
            [eve.db.ds.test.upsert]
            [eve.db.ds.test.transact]
            [eve.db.ds.test.db-fn]))

(def ^:private report-to (atom nil))
(def ^:private failures (atom []))

(defn- note! [m]
  (swap! failures conj (pr-str (select-keys m [:type :message :expected :actual :testing-contexts]))))

(defmethod t/report [::t/default :fail] [m]
  (t/inc-report-counter! :fail)
  (note! (assoc m :testing-contexts (t/testing-contexts-str)))
  (println "FAIL" (pr-str (select-keys m [:message :expected :actual]))))

(defmethod t/report [::t/default :error] [m]
  (t/inc-report-counter! :error)
  (note! (assoc m :actual (str (:actual m)) :testing-contexts (t/testing-contexts-str)))
  (println "ERROR" (str (:actual m))))

(defmethod t/report [::t/default :end-run-tests] [m]
  (when-let [r @report-to]
    (h/send! r {:op :suite :summary (select-keys m [:test :pass :fail :error]) :failures @failures})))

(defn run-suite
  "A worker's entry: the suite named by the boot's :suite, run with cljs.test; the summary is sent to
   the page by the :end-run-tests report above. cljs.test/run-tests is a macro in ClojureScript, so
   each suite is a literal call — an earlier (apply t/run-tests ...) here compiled to a call of an
   undefined value and killed the worker with a cljs$lang$applyTo TypeError (run 2, 2026-11-19)."
  [boot]
  (reset! report-to (:reply boot))
  (case (:suite boot)
    "laws" (t/run-tests 'eve.deftype-atom-laws-test)
    "bset" (t/run-tests 'eve.db.ds.bset-test)
    "u3" (t/run-tests 'eve.db.ds.test.conn 'eve.db.ds.test.listen 'eve.db.ds.test.upsert 'eve.db.ds.test.transact 'eve.db.ds.test.db-fn)))

(defn- post! [result]
  (js/fetch "/result" #js {:method "POST" :body (pr-str result)}))

(defn init []
  (when-not (h/run-thread!)
    (let [suite (or (aget js/window "DS_SUITE") "laws")
          t0 (js/Date.now)
          [w] (h/start-threads! 1 'eve.db.ds.browser-runner-main/run-suite
                                {:reply (h/self) :suite suite} {})
          timer (atom nil)]
      (reset! timer
              (js/setInterval
               (fn []
                 (let [m (h/receive! 0)]
                   (cond
                     (and (map? m) (= :suite (:op m)))
                     (do (js/clearInterval @timer)
                         (post! (assoc m :suite suite
                                       :ms (- (js/Date.now) t0)
                                       :isolated (boolean (aget js/self "crossOriginIsolated")))))
                     (and (map? m) (= w (:eve.thread/exit m)))
                     (do (js/clearInterval @timer)
                         (post! {:op :died :suite suite :notice (pr-str m)})))))
               20)))))
