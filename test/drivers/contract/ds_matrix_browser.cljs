(ns contract.ds-matrix-browser
  "The browser column of the README host table (scripts/ds_matrix.sh browser ...,
   driven by scripts/ds_matrix_browser.clj): the page boots as an Eve program does
   (eve.opfs.boot/boot!: isolated, the OPFS pool mounted before any worker), then
   one worker runs contract.ds-matrix/run, the SAME entry and shape code the other
   hosts run, with its persistent conns on OPFS. Each line the run prints is sent to
   the page as it is printed and POSTed to /row, so a capped process keeps the rows
   it finished; /result ends the run. The page's query names the cell:
   ?mode=bench|runtime&dir=/ds/x&args=500,1000"
  (:require [clojure.string :as str]
            [eve.opfs.boot :as boot]
            [eve.thread-host :as h]
            [contract.ds-matrix :as m]))

(defn run
  "A worker's entry: contract.ds-matrix/run, each printed line sent to the page."
  [{:keys [reply mode dir args]}]
  (let [buf (atom "")]
    (set! *print-newline* true)
    (set! *print-fn* (fn [s]
                       (let [t (str @buf s) parts (str/split t #"\n" -1)]
                         (reset! buf (last parts))
                         (doseq [l (butlast parts) :when (seq l)] (h/send! reply {:op :line :s l})))))
    (let [err (try (m/run mode dir "browser" args) nil
                   (catch :default e (h/send! reply {:op :line :s (pr-str {:fatal (str e) :stack (.-stack e)})}) e))]
      (when (seq @buf) (h/send! reply {:op :line :s @buf}))
      (h/send! reply (if err {:op :died :notice (str err)} {:op :done})))))

(defn- post! [path body] (js/fetch path #js {:method "POST" :body body}))

(defn init []
  (when-not (h/run-thread!)
    (let [q (js/URLSearchParams. (.-search js/location))
          mode (or (.get q "mode") "bench")
          dir (or (.get q "dir") "/ds/cell")
          args (vec (remove str/blank? (str/split (or (.get q "args") "") #",")))]
      (-> (boot/boot! {:opfs {:root "ds-matrix" :slots 1024}})
          (.then (fn [_]
                   (let [[w] (h/start-threads! 1 'contract.ds-matrix-browser/run
                                               {:reply (h/self) :mode mode :dir dir :args args} {})
                         timer (atom nil)]
                     (reset! timer
                             (js/setInterval
                              (fn []
                                (loop []
                                  (let [msg (h/receive! 0)]
                                    (cond
                                      (and (map? msg) (= :line (:op msg))) (do (post! "/row" (:s msg)) (recur))
                                      (and (map? msg) (= :done (:op msg))) (do (js/clearInterval @timer) (post! "/result" "{:op :done}"))
                                      (and (map? msg) (= :died (:op msg))) (do (js/clearInterval @timer) (post! "/result" (pr-str msg)))
                                      (and (map? msg) (= w (:eve.thread/exit msg)))
                                      (do (js/clearInterval @timer) (post! "/result" (pr-str {:op :died :notice (pr-str msg)})))
                                      :else nil))))
                              20)))))
          (.catch (fn [e] (post! "/result" (pr-str {:op :died :notice (str e)}))))))))
