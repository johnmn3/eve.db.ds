(ns contract.ds-shootout
  "Cross-runtime contention shootout: every runtime of the perf shootout
   bashing ONE persistent Eve domain at once — a shared disk atom, one
   mailbox atom per writer, and an eve.db.ds persistent conn, all on the
   same directory prefix.

   Modes (argv after the mode word):
     init     <dir>                       create the conn (schema below),
                                          the shared atom and the barrier
     worker   <dir> <rt> <proc> <n> <ms>  join; wait at the barrier until
                                          n writers are in; run the mixed
                                          workload for ms; print one EDN
                                          row (counts, timings, what this
                                          writer saw of the others)
     readback <dir> <rt>                  join fresh; print the whole final
                                          state as EDN for the checker

   One round of the workload, writer tag T = rt:proc, round i:
     transact  1  x {:op/key \"T/i\" :op/tag T :op/rt rt :op/proc proc :op/seq i}
     swap      4  x shared atom: :n +1, [:cnt T] +1, [:by T] i
                  (after the transact, so [:by T] = i promises T/i is in the DB)
     deref     4  x shared atom, materialised in a read region
     reset     2  x this writer's mailbox atom {:tag T :seq i :k k}
     mbox-read 2  x a foreign writer's mailbox (seq must never go back)
     find      2  x (d/datoms db :avet :op/key \"U/s\"), U foreign, s <= [:by U]
     pull      2  x (d/pull db [...] [:op/key \"U/s\"])
     query     1  x foreign entity count per runtime
     cas       every 4th round: the DB counter +1 by :db/cas, retried on
                  :transact/cas until it lands
   Every block is timed with eve.process/now-ms (ms ticks; the difference of
   two ticks is unbiased, so block sums over a run are ns/op estimates)."
  (:require [eve.alpha :as eve :refer [$]]
            [eve.core :as e]
            [eve.process :as proc]
            [eve.db.ds.core :as d]))

(def schema
  {:op/key {:db/unique :db.unique/identity}
   :op/tag {:db/index true}
   :op/rt {:db/index true}
   :ctr/name {:db/unique :db.unique/identity}})

(defn- ds-dir [dir] (str dir "/ds"))

(defn- flag?
  "Whether the comma list flags names f (plain string ops: no regex)."
  [flags f]
  (boolean (and flags
                (loop [s flags]
                  (let [n (count s)
                        i (loop [i 0] (cond (>= i n) n (= \, (nth s i)) i :else (recur (inc i))))]
                    (cond (= f (subs s 0 i)) true
                          (>= i n) false
                          :else (recur (subs s (inc i)))))))))

(defn- open-domain!
  "With the nodurable flag, open the store's domain unjournaled first (the
   domain is cached by path, so the conn and the atoms then join it): a
   control that takes the journal out of the picture. Default: journaled,
   as every perf cell runs."
  [dir flags]
  (when (flag? flags "nodurable")
    (eve/persistent-atom-domain (str dir "/ds") :durable? false)))

(defn- open-conn [dir] (d/create-conn schema {:persistent (ds-dir dir)}))

(defn- open-atom [dir id] (eve/atom {:id id :persistent (ds-dir dir)} nil))

(defn- shared-atom [dir] (open-atom dir :shoot/shared))

(defn- mbox-id [tag] (keyword "shoot.mbox" (apply str "w-" (map (fn [c] (if (= c \:) \- c)) tag))))

(defn- plain [x]
  (cond (map? x) (into {} (map (fn [[k v]] [(plain k) (plain v)])) x)
        (vector? x) (mapv plain x)
        (seq? x) (mapv plain x)
        :else x))

(defn- read-shared [a] (plain ($ (let [m @a] {:n (:n m) :by (:by m) :cnt (:cnt m) :ready (:ready m)}))))

(defn- read-mbox [a] (plain ($ (let [m @a] (when (some? m) {:tag (:tag m) :seq (:seq m) :k (:k m)})))))

(defn- now [] (proc/now-ms))

(defn- err-str
  "A throwable as a short string: its message and its ex-data's keys and
   :error, never the ex-data's values (a DB in them prints through the
   host's writer, which jank's cannot do)."
  [t]
  (let [m (ex-data t)]
    (str (or (ex-message t) (str t))
         (when m (str " ex-data keys " (pr-str (vec (keys m))) " :error " (pr-str (:error m)))))))

(defn- attempt
  "Run f; on any throw answer {:throws <err>} (a fn, not a macro: the
   ClojureScript build reads this file as one namespace)."
  [f]
  (try (f)
       (catch #?(:cljs :default :lpy python/Exception :jank jank.runtime.object_ref :default Exception) t
         {:throws (err-str t)
          :stack #?(:clj (mapv str (take 60 (.getStackTrace ^Throwable t)))
                    :cljs (some-> (.-stack t) (subs 0 (min 4000 (count (.-stack t)))))
                    :default nil)})))

(defn- cas-error? [t]
  (let [m (ex-data t)]
    (= :transact/cas (:error m))))

(defn- not-kept? [t] (= :eve.db.ds/db-not-kept (:error (ex-data t))))

(def ^:private not-kept-retries (volatile! {}))

(defn- with-db
  "(f db) on a fresh handle to the conn's current DB. A handle names one DB
   version and resolves at read time: when another writer (any process)
   commits between (d/db conn) and the read, the read throws
   :eve.db.ds/db-not-kept. That retry is counted under kind k, then the read
   runs again on a new handle."
  [k conn f]
  (loop []
    (let [r (try {:v (f (d/db conn))}
                 (catch #?(:cljs :default :lpy python/Exception :jank jank.runtime.object_ref :default Exception) t
                   (if (not-kept? t) nil (throw t))))]
      (if r
        (:v r)
        (do (vswap! not-kept-retries update k (fnil inc 0)) (recur))))))

(defn- ctr-value [conn]
  (let [r (with-db :cas-read conn (fn [db] (d/pull db [:ctr/n] [:ctr/name "shared"])))]
    (or (:ctr/n r) 0)))

(defn- cas-inc!
  "The DB counter +1 by :db/cas: read, cas old -> old+1, retry while another
   writer got there first. Answers the attempts it took."
  [conn]
  (loop [tries 1]
    (let [old (ctr-value conn)
          ok (try (d/transact! conn [[:db/cas [:ctr/name "shared"] :ctr/n old (inc old)]]) true
                  (catch #?(:cljs :default :lpy python/Exception :jank jank.runtime.object_ref :default Exception) t
                    (if (cas-error? t) false (throw t))))]
      (if ok tries (recur (inc tries))))))

(defn- init! [dir & [flags]]
  (open-domain! dir flags)
  (let [conn (open-conn dir)
        a (shared-atom dir)]
    (d/transact! conn [{:ctr/name "shared" :ctr/n 0}])
    (swap! a (fn [m] (if (some? m) m {:n 0 :by {} :cnt {} :ready {}})))
    (println (pr-str {:mode :init :datoms (e/count (d/datoms (d/db conn) :eavt))
                      :shared (read-shared a)}))))

(defn- barrier!
  "Join the shared atom's :ready map, then poll it until n writers are in
   (every runtime reads the others' joins through the disk atom)."
  [a tag n]
  (swap! a (fn [m] (assoc-in m [:ready tag] (now))))
  (loop [k 0]
    (let [r (:ready (read-shared a))]
      (when (< (count r) n)
        (proc/sleep-ms! 5)
        (recur (inc k))))))

(def ^:private kinds [:transact :swap :deref :reset :mbox-read :find :pull :query :scan :cas])

(defn- worker! [dir rt pr n dur-ms & [flags]]
  (let [tag (str rt ":" pr)
        n (parse-long n)
        dur (parse-long dur-ms)
        _ (open-domain! dir flags)
        conn (open-conn dir)
        a (shared-atom dir)
        mine (open-atom dir (mbox-id tag))
        mboxes (volatile! {})
        mbox-of (fn [t] (or (get @mboxes t)
                            (let [m (open-atom dir (mbox-id t))] (vswap! mboxes assoc t m) m)))
        _ (barrier! a tag n)
        t-start (now)
        t-end (+ t-start dur)
        ms (volatile! (zipmap kinds (repeat 0)))
        ops (volatile! (zipmap kinds (repeat 0)))
        round (volatile! 0)
        err-count (volatile! {})         ; kind -> ops that threw
        err-samples (volatile! {})       ; kind -> the first two throws
        tx-failed (volatile! [])         ; rounds whose transact threw
        ;; each rep guarded on its own: an op that throws on this host is
        ;; counted (and its first throws kept) and the run goes on; only
        ;; reps that returned count as ops
        timed (fn [k reps f]
                (let [t0 (now)
                      okn (volatile! 0)]
                  (dotimes [j reps]
                    (let [r (attempt (fn [] (f j) nil))]
                      (if (map? r)
                        (do (vswap! err-count update k (fnil inc 0))
                            (when (< (count (get @err-samples k)) 2)
                              (vswap! err-samples update k (fnil conj []) (assoc r :round @round)))
                            (when (= k :transact) (vswap! tx-failed conj @round)))
                        (vswap! okn inc))))
                  (vswap! ms update k + (- (now) t0))
                  (vswap! ops update k + @okn)))
        last-shared (volatile! {:by {}})
        saw-atom (volatile! {})          ; foreign tag -> max [:by U] seen
        saw-mbox (volatile! {})          ; foreign tag -> max mailbox seq seen
        mbox-back (volatile! 0)          ; a mailbox seq that went backwards
        saw-ds (volatile! {})            ; foreign rt -> max entity count in a query
        saw-scan (volatile! {})          ; foreign rt -> max entity count in an :aevt scan
        find-miss (volatile! [])         ; atom said U/s committed, DB had no U/s
        pull-bad (volatile! [])
        cas-tries (volatile! 0)
        cas-done (volatile! 0)
        pick (fn [i j]
               (let [by (dissoc (:by @last-shared) tag)]
                 (when (seq by)
                   (let [ks (vec (sort (keys by)))
                         u (nth ks (mod (+ i j) (count ks)))
                         s (get by u)]
                     [u (mod (+ (* 7 i) j) (inc s))]))))]
    (loop [i 0]
      (when (and (< (now) t-end) (flag? flags "casonly"))
        ;; casonly: the :db/cas op alone, every round (a storm on one datom)
        (vreset! round i)
        (timed :cas 1 (fn [_] (vswap! cas-tries + (cas-inc! conn)) (vswap! cas-done inc)))
        (recur (inc i))))
    (loop [i 0]
      (when (and (< (now) t-end) (not (flag? flags "casonly")))
        (vreset! round i)
        (timed :transact 1
               (fn [_] (d/transact! conn [{:op/key (str tag "/" i) :op/tag tag :op/rt rt
                                           :op/proc pr :op/seq i}])))
        (timed :swap 4
               (fn [_] (swap! a (fn [m] (-> m (update :n inc)
                                            (update-in [:cnt tag] (fnil inc 0))
                                            (assoc-in [:by tag] i))))))
        (timed :deref 4 (fn [_] (vreset! last-shared (read-shared a))))
        (doseq [[u s] (dissoc (:by @last-shared) tag)]
          (vswap! saw-atom update u (fnil max 0) s))
        (timed :reset 2 (fn [k] (reset! mine {:tag tag :seq i :k k})))
        (timed :mbox-read 2
               (fn [j] (when-some [[u _] (pick i j)]
                         (let [v (read-mbox (mbox-of u))]
                           (when-some [s (:seq v)]
                             (when (< s (get @saw-mbox u -1)) (vswap! mbox-back inc))
                             (vswap! saw-mbox update u (fnil max 0) s))))))
        (timed :find 2
               (fn [j] (when-some [[u s] (pick i j)]
                         (when (nil? (with-db :find conn (fn [db] (first (d/datoms db :avet :op/key (str u "/" s))))))
                           (vswap! find-miss conj (str u "/" s))))))
        (timed :pull 2
               (fn [j] (when-some [[u s] (pick i (+ j 1))]
                         (let [p (with-db :pull conn (fn [db] (d/pull db [:op/tag :op/seq] [:op/key (str u "/" s)])))]
                           (when-not (and (= u (:op/tag p)) (= s (:op/seq p)))
                             (vswap! pull-bad conj [(str u "/" s) p]))))))
        (timed :query 1
               (fn [_] (doseq [[r2 c] (with-db :query conn
                                        (fn [db] (d/q '[:find ?r (count ?e) :in $ ?me
                                                        :where [?e :op/rt ?r] [(not= ?r ?me)]]
                                                      db rt)))]
                         (vswap! saw-ds update r2 (fnil max 0) c))))
        (when (zero? (mod i 4))
          (timed :scan 1
                 (fn [_] (doseq [[r2 c] (with-db :scan conn
                                          (fn [db] (frequencies (map :v (d/datoms db :aevt :op/rt)))))
                                 :when (not= r2 rt)]
                           (vswap! saw-scan update r2 (fnil max 0) c)))))
        (when (and (zero? (mod i 4)) (not (flag? flags "nocas")))
          (timed :cas 1 (fn [_] (vswap! cas-tries + (cas-inc! conn)) (vswap! cas-done inc))))
        (recur (inc i))))
    (let [elapsed (- (now) t-start)
          o @ops
          m @ms]
      (println (pr-str {:mode :worker :tag tag :rt rt :proc pr :writers n
                        :rounds (inc @round) :tx-ok (:transact o) :tx-failed @tx-failed :elapsed-ms elapsed
                        :ops o :ms m
                        :ns-op (into {} (for [k kinds :when (pos? (get o k))]
                                          [k (long (/ (* 1e6 (get m k)) (get o k)))]))
                        :swaps (:swap o) :cas-done @cas-done :cas-tries @cas-tries
                        :saw-atom @saw-atom :saw-mbox @saw-mbox :mbox-back @mbox-back
                        :saw-ds @saw-ds :saw-scan @saw-scan :find-miss (count @find-miss) :find-miss-first (take 5 @find-miss)
                        :pull-bad (count @pull-bad) :pull-bad-first (take 3 @pull-bad)
                        :not-kept-retries @not-kept-retries
                        :errors @err-count :error-samples @err-samples})))))

(defn- readback! [dir rt & [flags]]
  (open-domain! dir flags)
  (let [conn (open-conn dir)
        a (shared-atom dir)
        db (d/db conn)
        _ (vreset! not-kept-retries {})
        col (fn [attr] (into {} (map (fn [dt] [(:e dt) (:v dt)])) (d/datoms db :aevt attr)))
        ks (col :op/key)
        cols (mapv col [:op/tag :op/rt :op/proc :op/seq])
        rows (for [[e k] ks] (into [k] (map (fn [c] (get c e))) cols))
        ;; every query names its :in: jank's parser drops the default [$]
        qrows (d/q '[:find ?k ?t ?r ?p ?s :in $ :where [?e :op/key ?k] [?e :op/tag ?t] [?e :op/rt ?r]
                     [?e :op/proc ?p] [?e :op/seq ?s]] db)
        sh (read-shared a)]
    (println (pr-str {:mode :readback :reader rt
                      :datoms (e/count (d/datoms db :eavt))
                      :entities (count (d/q '[:find [?e ...] :in $ :where [?e :op/key _]] db))
                      :rows (vec (sort rows))
                      :query-rows (count qrows)
                      :query-rows-sample (vec (take 2 (sort (map vec qrows))))
                      :query-equals-scan (= (set (map vec qrows)) (set rows))
                      :ctr (ctr-value conn)
                      :shared (dissoc sh :ready)
                      :ready (vec (sort (keys (:ready sh))))
                      :mboxes (into (sorted-map) (for [t (keys (:cnt sh))]
                                                   [t (read-mbox (open-atom dir (mbox-id t)))]))}))))

(defn -main [& [mode dir & more]]
  (try
    (case mode
      "init" (apply init! dir more)
      "worker" (apply worker! dir more)
      "readback" (apply readback! dir more))
    (catch #?(:cljs :default :lpy python/Exception :jank jank.runtime.object_ref :default Exception) t
      (println (pr-str {:mode (keyword (or mode "none")) :fatal (err-str t)
                        :stack #?(:clj (mapv str (take 60 (.getStackTrace ^Throwable t)))
                                  :cljs (some-> (.-stack t) (subs 0 (min 4000 (count (.-stack t)))))
                                  :default nil)}))))
  (flush))
