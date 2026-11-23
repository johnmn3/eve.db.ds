;; contention_shootout_check.clj — the verdict for scripts/contention_shootout.sh's
;; results directory (bb). For every leg: the invariants (exactly-once, no lost
;; update, cas counter, mailboxes, foreign visibility, one final value read by
;; every runtime), then the solo vs together ns/op table.
;;   bb scripts/contention_shootout_check.clj <results-dir>
(ns contention-shootout-check
  (:require [clojure.edn :as edn] [clojure.java.io :as io] [clojure.string :as str]))

(def results-dir (first *command-line-args*))

(defn read-edn [f]
  (let [s (str/trim (slurp f))]
    (when (seq s) (edn/read-string {:default (fn [_ v] v)} s))))

(defn leg-data [d]
  (let [fs (.listFiles (io/file d))
        pick (fn [prefix] (into (sorted-map)
                                (for [f fs :let [n (.getName f)]
                                      :when (and (str/starts-with? n prefix) (str/ends-with? n ".edn"))]
                                  [(subs n (count prefix) (- (count n) 4)) (read-edn f)])))]
    {:name (.getName (io/file d)) :workers (pick "worker-") :readbacks (pick "readback-")}))

(def kinds [:transact :swap :deref :reset :mbox-read :find :pull :query :scan :cas])

(defn check-leg [{:keys [name workers readbacks]}]
  (let [ws (vals workers)
        problems (transient [])
        bad! (fn [& xs] (conj! problems (str/join " " xs)))
        ok-ws (filter :tag ws)
        _ (doseq [[k w] workers]
            (cond (or (nil? w) (:fatal w)) (bad! "writer" k "failed:" (pr-str (or (:fatal w) "no EDN row")))
                  (seq (:errors w)) (bad! "writer" k "ops threw" (pr-str (:errors w)) "first:"
                                          (pr-str (into {} (for [[kind xs] (:error-samples w)]
                                                             [kind (mapv #(select-keys % [:round :throws]) xs)]))))))
        tags (set (map :tag ok-ws))
        rts (set (map :rt ok-ws))
        rounds (into {} (map (juxt :tag :rounds)) ok-ws)
        tx-ok (into {} (map (fn [w] [(:tag w) (or (:tx-ok w) (:rounds w))])) ok-ws)
        tx-failed (into {} (map (juxt :tag (comp set :tx-failed))) ok-ws)
        swaps (into {} (map (juxt :tag :swaps)) ok-ws)
        ref-rt (first (keys readbacks))
        ref (get readbacks ref-rt)
        rows (:rows ref)
        by-tag (group-by second rows)]
    (when-not ref (bad! "no readback"))
    (doseq [[rt rb] readbacks] (when (or (nil? rb) (:fatal rb)) (bad! "readback" rt "failed:" (pr-str (:fatal rb)))))
    (when ref
      ;; exactly once
      ;; exactly once: every transact that returned is in the DB once; one that
      ;; threw may or may not be (counted); nothing else is
      (doseq [t tags]
        (let [seqs (map #(nth % 4) (get by-tag t))
              n (get rounds t)
              failed (get tx-failed t)
              ok-seqs (remove failed (range n))
              have (set seqs)]
          (when-not (= (count seqs) (count have))
            (bad! "tag" t "has duplicate seqs in the DB:" (- (count seqs) (count have))))
          (when-let [lost (seq (remove have ok-seqs))]
            (bad! "tag" t "lost" (count lost) "of" (count ok-seqs) "committed transactions, e.g." (pr-str (take 5 lost))))
          (when-let [extra (seq (remove (set (range n)) have))]
            (bad! "tag" t "has seqs it never wrote:" (pr-str (take 5 extra))))
          (when (seq failed)
            (bad! "tag" t (count failed) "transacts threw;" (count (filter have failed)) "of them landed anyway"))))
      (when-not (= (set (keys by-tag)) tags) (bad! "DB holds tags" (pr-str (keys by-tag)) "but writers were" (pr-str tags)))
      (let [tx (reduce + (vals tx-ok))
            landed-failed (reduce + (for [t tags] (count (filter (set (map #(nth % 4) (get by-tag t))) (get tx-failed t)))))]
        (when-not (= (:entities ref) (+ tx landed-failed))
          (bad! "entities" (:entities ref) "!= committed transactions" (+ tx landed-failed)))
        (when-not (= (:datoms ref) (+ 2 (* 5 (:entities ref))))
          (bad! "datoms" (:datoms ref) "!= 2 + 5 x entities" (+ 2 (* 5 (:entities ref))))))
      ;; no lost update on the shared atom
      (let [sh (:shared ref)]
        (when-not (= (:n sh) (reduce + (vals swaps))) (bad! "shared :n" (:n sh) "!= swaps" (reduce + (vals swaps))))
        (doseq [t tags]
          (when-not (= (get-in sh [:cnt t]) (get swaps t)) (bad! "shared :cnt" t (get-in sh [:cnt t]) "!=" (get swaps t)))
          (when-not (= (get-in sh [:by t]) (dec (get rounds t))) (bad! "shared :by" t (get-in sh [:by t]) "!=" (dec (get rounds t))))))
      ;; the DB counter: every :db/cas increment that landed, once
      (let [cas (reduce + (map :cas-done ok-ws))]
        (when-not (= (:ctr ref) cas) (bad! "DB counter" (:ctr ref) "!= cas increments" cas)))
      ;; mailboxes
      (doseq [t tags]
        (when-not (= (get-in ref [:mboxes t :seq]) (dec (get rounds t)))
          (bad! "mailbox" t "final seq" (get-in ref [:mboxes t :seq]) "!=" (dec (get rounds t)))))
      ;; one final value, read by every runtime
      (doseq [[rt rb] readbacks :when rb]
        (doseq [k [:rows :shared :ctr :mboxes :datoms :entities]]
          (when-not (= (get rb k) (get ref k)) (bad! "reader" rt "differs from" ref-rt "on" k)))))
    ;; what each writer saw of the others while it ran
    (doseq [w ok-ws :let [others (disj tags (:tag w)) other-rts (disj rts (:rt w))]]
      (when (seq others)
        (when-not (= others (set (keys (:saw-atom w)))) (bad! (:tag w) "saw-atom" (pr-str (keys (:saw-atom w))) "not all of" (pr-str others)))
        (when-not (= others (set (keys (:saw-mbox w)))) (bad! (:tag w) "saw-mbox" (pr-str (keys (:saw-mbox w))) "not all of" (pr-str others))))
      (when (seq other-rts)
        (when-not (= other-rts (set (keys (:saw-scan w)))) (bad! (:tag w) "saw-scan" (pr-str (keys (:saw-scan w))) "not all of" (pr-str other-rts))))
      (when (pos? (:mbox-back w 0)) (bad! (:tag w) "read a mailbox seq go backwards" (:mbox-back w) "times"))
      (when (pos? (:find-miss w 0)) (bad! (:tag w) "find-miss" (:find-miss w) (pr-str (:find-miss-first w))))
      (when (pos? (:pull-bad w 0)) (bad! (:tag w) "pull-bad" (:pull-bad w) (pr-str (:pull-bad-first w)))))
    ;; query vs scan (d/q answers against the datoms): reported per reader
    (let [qe (into (sorted-map) (for [[rt rb] readbacks :when rb] [rt (:query-equals-scan rb)]))]
      (doseq [[rt v] qe :when (not v)] (bad! "reader" rt "d/q rows differ from the :aevt scan (query-rows-sample"
                                             (pr-str (:query-rows-sample (get readbacks rt))) ")"))
      {:leg name :problems (persistent! problems) :writers (count ok-ws) :rounds rounds :tx-ok tx-ok :swaps swaps
       :entities (:entities ref) :datoms (:datoms ref) :ctr (:ctr ref) :shared-n (get-in ref [:shared :n])
       :cas (reduce + (map :cas-done ok-ws)) :cas-tries (reduce + (map :cas-tries ok-ws))
       :query-equals-scan qe
       :saw (into (sorted-map) (for [w ok-ws] [(:tag w) {:atom (:saw-atom w) :mbox (:saw-mbox w) :scan (:saw-scan w)
                                                         :query (:saw-ds w) :find-miss (:find-miss w) :pull-bad (:pull-bad w)}]))
       :ns-op (into (sorted-map) (for [w ok-ws] [(:rt w) (:ns-op w)]))
       :ops (into (sorted-map) (for [w ok-ws] [(:rt w) (:ops w)]))})))

(def legs (for [f (sort-by #(.getName %) (.listFiles (io/file results-dir))) :when (.isDirectory f)] (check-leg (leg-data f))))

(doseq [l legs]
  (println (str "LEG " (:leg l) ": " (if (empty? (:problems l)) "PASS" (str "FAIL (" (count (:problems l)) ")"))
                " writers " (:writers l) " transactions " (reduce + (vals (:tx-ok l))) " entities " (:entities l)
                " datoms " (:datoms l) " swaps " (reduce + (vals (:swaps l))) " shared-n " (:shared-n l)
                " cas " (:cas l) "/" (:cas-tries l) " tries, counter " (:ctr l)))
  (doseq [p (:problems l)] (println "  PROBLEM" p)))

(let [solo (into {} (for [l legs :when (str/starts-with? (:leg l) "solo-")] [(subs (:leg l) 5) l]))
      tog (first (filter #(= "together" (:leg %)) legs))
      w (:writers tog)]
  (when tog
    (println)
    (println (str "ns/op, solo -> together (" w " writers at once); x = together / solo"))
    (println (str "| runtime | " (str/join " | " (map name kinds)) " |"))
    (println (str "|---|" (str/join "|" (repeat (count kinds) "---:")) "|"))
    (doseq [[rt t] (:ns-op tog)]
      (let [s (get-in solo [rt :ns-op rt])]
        (println (str "| " rt " | "
                      (str/join " | " (for [k kinds :let [a (get s k) b (get t k)]]
                                        (cond (and a b (pos? a)) (format "%,d → %,d (%.1f×)" a b (/ (double b) a))
                                              b (format "— → %,d" b)
                                              :else "—")))
                      " |"))))
    (println)
    (println "ops completed, solo -> together:")
    (doseq [[rt t] (:ops tog)]
      (println " " rt (pr-str (get-in solo [rt :ops rt])) "->" (pr-str t)))
    (spit (str results-dir "/verdict.edn") (pr-str (mapv #(dissoc % :saw) legs)))
    (spit (str results-dir "/saw.edn") (pr-str (into {} (map (juxt :leg :saw)) legs)))))

(System/exit (if (every? (comp empty? :problems) legs) 0 1))
