(ns eve.db.ds.conn
  "The conn and DB-handle layer (DS-IN-EVE-PLAN v2 §4).

   A conn is a host object holding an Eve atom and a host atom of
   listeners. The Eve atom's value is a plain map:

     {:db DB  :kept {tx DB}}

   :db is the current database; :kept holds older DBs that were
   explicitly kept (see keep!). The DB, its indexes and its datoms are
   Eve values inside that atom — built inside swap!, read inside a
   read region ($). Nothing else in this library holds a database.

   A DB value at the public boundary is a HANDLE: a plain host map
   {:atom a :tx n} naming one DB version in one conn's atom. Reads
   resolve the handle against the atom at the moment of the read:
   the current DB if its max-tx is n, else :kept[n], else an
   :eve.db.ds/db-not-kept error naming both tx numbers. Retention is
   reachability inside the atom (§7.1 ruling): there is no blanket
   pinning of old DBs.

   Delivery: values that leave a swap! or a read region are host data.
   A slab DB captured out of a read region stays readable anywhere
   (it is an immutable Eve value, not a delivered sister) — measured,
   u3-logs/probe1.log P2/P4. Datoms are delivered through a region so
   they arrive in their flat delivery form (probe1 P3)."
  (:require
    [eve.alpha :as eve :refer [$]]
    [eve.db.ds.db :as db]
    [eve.db.ds.util :as util]))

;; ---------------------------------------------------------------------------
;; Handles

(defn handle?
  "True if x is a DB handle: a plain host map {:atom a :tx n} where a
   is an Eve atom."
  [x]
  (and (map? x)
    (eve/shared-atom? (:atom x))
    (integer? (:tx x))))

(defn ensure-handle
  "Return x if it is a DB handle, else throw a named error."
  [x]
  (if (handle? x)
    x
    (util/raise "Expected a DB handle {:atom a :tx n}, got " (pr-str x)
      {:error :eve.db.ds/not-a-handle :value x})))

(defn- not-kept! [tx current-tx]
  (util/raise
    (str "DB at tx " tx " is not kept (current tx is " current-tx
      "); keep it with keep! while it is still reachable")
    {:error :eve.db.ds/db-not-kept :tx tx :current-tx current-tx}))

(defn- resolve-in-map
  "Resolution against an atom value map, for use inside a swap fn
   (already in the atom's region): the current DB if its max-tx is
   the handle's tx, else the :kept entry, else throw db-not-kept."
  [m tx]
  (let [cur (:db m)]
    (if (= tx (:max-tx cur))
      cur
      (if-some [kept (get (:kept m) tx)]
        kept
        (not-kept! tx (:max-tx cur))))))

(defn resolve-db
  "Resolve a handle to the DB value it names. The DB is the atom's
   own immutable value, captured in a read region; it stays readable
   after the region (probe1 P2). Throws :eve.db.ds/db-not-kept when
   the handle's tx is neither current nor kept."
  [handle]
  (ensure-handle handle)
  (let [{:keys [atom tx]} handle
        *db (volatile! nil)]
    ;; the region's body answers nil: $ materializes what its body answers, and the DB itself would be packed whole
    ($ (do (vreset! *db (resolve-in-map @atom tx)) nil))
    @*db))

(defn current-handle
  "The handle to an Eve atom's current DB."
  [a]
  {:atom a :tx ($ (:max-tx (:db @a)))})

(defn schema-of
  "The schema map of the DB a handle names, as host data."
  [handle]
  (let [db (resolve-db handle)]
    ($ (:schema db))))

(defn datoms-of
  "All datoms of the DB a handle names, in :eavt order, delivered as
   host Datoms. Used to copy a DB by value across atoms."
  [handle]
  (let [db (resolve-db handle)]
    ($ (mapv identity (db/-datoms db :eavt nil nil nil nil)))))

;; ---------------------------------------------------------------------------
;; Conn

(declare db)

#?(:clj
   (deftype Conn [eve-atom listeners closed persistent]
     clojure.lang.IDeref
     (deref [this] (db this)))
   :cljs
   (deftype Conn [eve-atom listeners closed persistent]
     IDeref
     (-deref [this] (db this))))

(defn conn? [x]
  (instance? Conn x))

(defn- check-open [conn]
  (when @(.-closed conn)
    (util/raise "Conn is closed" {:error :eve.db.ds/conn-closed})))

(defn db
  "The handle to the conn's current DB. Same as @conn."
  [conn]
  {:pre [(conn? conn)]}
  (current-handle (.-eve-atom conn)))

(defonce ^:private id-counter (atom 0))

(defn- new-conn-id [opts]
  (if-some [dir (:persistent opts)]
    ;; A persistent conn's atom id must be stable for its directory:
    ;; reopening the same dir must find the same atom in its domain.
    (keyword "eve.db.ds.persistent" (str "dir-" (hash (str dir))))
    (keyword "eve.db.ds"
      (str "conn-" (swap! id-counter inc) "-" (random-uuid)))))

(defn- make-conn
  "Create the Eve atom and install {:db (build-fn) :kept {}} with one
   swap!. If the atom already holds a DB (a reopened persistent
   conn), it is adopted untouched."
  [build-fn opts]
  (let [a    (if-some [dir (:persistent opts)]
               (eve/atom {:id (new-conn-id opts) :persistent (str dir)} nil)
               (eve/atom {:id (new-conn-id opts)} nil))
        conn (->Conn a (atom {}) (atom false) (boolean (:persistent opts)))]
    (swap! a (fn [m]
               (if (and (map? m) (some? (:db m)))
                 m
                 {:db (build-fn) :kept {}})))
    conn))

(defn create-conn
  "Create a conn over a fresh empty DB. Options:

   :persistent <dir> — the conn's Eve atom is backed by this
   directory; reopening the same directory adopts the stored DB."
  ([]
   (create-conn nil {}))
  ([schema]
   (create-conn schema {}))
  ([schema opts]
   (make-conn #(db/empty-db schema {}) opts)))

(defn conn-from-db
  "A new conn whose DB is the one the given handle names. The DB is
   copied in by value (its datoms, through a build inside the new
   atom): a DB lives in the atom that holds it."
  [db-handle]
  (let [datoms (datoms-of db-handle)
        schema (schema-of db-handle)]
    (make-conn #(db/init-db datoms schema {}) {})))

(defn conn-from-datoms
  "A new conn built from host Datoms."
  ([datoms]
   (conn-from-datoms datoms nil))
  ([datoms schema]
   (make-conn #(db/init-db datoms schema {}) {})))

(defn close-conn!
  "Release the conn's Eve atom (additive in this rewrite; upstream
   has no equivalent). For a :persistent conn this also closes the
   backing atom domain. After close, conn operations throw
   :eve.db.ds/conn-closed and the conn's handles are not guaranteed
   to resolve."
  [conn]
  {:pre [(conn? conn)]}
  (reset! (.-closed conn) true)
  (when (.-persistent conn)
    (eve/close-atom-domain! (eve/atom-domain (.-eve-atom conn))))
  conn)

;; ---------------------------------------------------------------------------
;; Keeping old DBs (§7.1: retention is reachability inside the atom)

(defn keep!
  "Pin the DB the handle names in its atom's :kept map, in one swap!.
   The DB must still be reachable (current, or already kept): an
   unkept, non-current DB is gone, and keep! on its handle throws
   :eve.db.ds/db-not-kept. Returns the handle."
  [conn db-handle]
  {:pre [(conn? conn)]}
  (ensure-handle db-handle)
  (when-not (identical? (:atom db-handle) (.-eve-atom conn))
    (util/raise "Handle does not belong to this conn"
      {:error :eve.db.ds/keep-wrong-conn}))
  (swap! (.-eve-atom conn)
    (fn [m] (assoc-in m [:kept (:tx db-handle)]
              (resolve-in-map m (:tx db-handle)))))
  db-handle)

(defn release!
  "Remove the DB the handle names from its atom's :kept map, in one
   swap!. The current DB is unaffected (it resolves as current, not
   via :kept). Returns the handle."
  [conn db-handle]
  {:pre [(conn? conn)]}
  (ensure-handle db-handle)
  (swap! (.-eve-atom conn)
    (fn [m] (update m :kept dissoc (:tx db-handle))))
  db-handle)

;; ---------------------------------------------------------------------------
;; Transactions

(defn- deliver-report
  "Make a raw db/TxReport public: handles for :db-before/:db-after,
   delivered host Datoms for :tx-data. :tempids and :tx-meta are
   already host data."
  [a raw]
  (assoc raw
    :db-before {:atom a :tx (:max-tx (:db-before raw))}
    :db-after  {:atom a :tx (:max-tx (:db-after raw))}
    :tx-data   ($ (mapv identity (:tx-data raw)))))

(defn -transact! [conn tx-data tx-meta]
  (check-open conn)
  (let [a     (.-eve-atom conn)
        *raw  (volatile! nil)]
    (swap! a
      (fn [m]
        (let [db-before (:db m)
              report    (db/transact-tx-data
                          (db/->TxReport db-before db-before [] {} tx-meta)
                          tx-data)]
          (vreset! *raw report)
          (assoc m :db (:db-after report)))))
    (deliver-report a @*raw)))

(defn transact!
  ([conn tx-data]
   (transact! conn tx-data nil))
  ([conn tx-data tx-meta]
   {:pre [(conn? conn)]}
   (locking conn
     (let [report (-transact! conn tx-data tx-meta)]
       (doseq [[_ callback] @(.-listeners conn)]
         (callback report))
       report))))

(defn with
  "Value-level transaction on a handle: one swap! on the handle's
   atom. The resulting DB is added to the atom's :kept map (the
   conn-less chain stays readable); the atom's current DB is not
   replaced — for a conn, use transact!. Returns the tx-report,
   whose :db-before and :db-after are handles.

   RELEASE POLICY for conn-less values is OPEN (plan §8, awaiting
   John's ruling, blocked on EVE-FIX F5): this is the mechanism only
   — kept entries accumulate until release! removes them."
  ([db tx-data] (with db tx-data nil))
  ([db tx-data tx-meta]
   (ensure-handle db)
   (let [a     (:atom db)
         *raw  (volatile! nil)]
     (swap! a
       (fn [m]
         (let [base   (resolve-in-map m (:tx db))
               report (db/transact-tx-data
                        (db/->TxReport base base [] {} tx-meta)
                        tx-data)
               after  (:db-after report)]
           (vreset! *raw report)
           (update m :kept assoc (:max-tx after) after))))
     (deliver-report a @*raw))))

(defn db-with
  "Applies transaction to a DB handle, returning the handle to the
   new DB. Same as (:db-after (with db tx-data))."
  [db tx-data]
  (:db-after (with db tx-data)))

(defn reset-conn!
  "Replace the conn's DB with the one the handle names, in one
   swap!. A handle from another atom is copied in by value (schema
   + delivered datoms, rebuilt by init-db inside this atom). The
   installed DB's max-tx is bumped above every tx reachable in this
   atom when it would otherwise collide — (atom, tx) must name
   exactly one DB version (GAP-2). The report's :db-before names
   the outgoing DB: it resolves only if that DB stays reachable —
   keep! it first if it must outlive the reset (§7.1)."
  ([conn db-handle]
   (reset-conn! conn db-handle nil))
  ([conn db-handle tx-meta]
   {:pre [(conn? conn)]}
   (check-open conn)
   (ensure-handle db-handle)
   (locking conn
     (let [a         (.-eve-atom conn)
           same-atom (identical? a (:atom db-handle))
           ;; cross-atom sources are copied in by value: delivered
           ;; host Datoms + schema, read from the source handle
           src-datoms (when-not same-atom (datoms-of db-handle))
           src-schema (when-not same-atom (schema-of db-handle))
           *raw       (volatile! nil)]
       (swap! a
         (fn [m]
           (let [db-before (:db m)
                 db-after0 (if same-atom
                             (resolve-in-map m (:tx db-handle))
                             (db/init-db src-datoms src-schema {}))
                 ;; GAP-2's premise is that (atom, tx) names exactly
                 ;; one DB version. An init-db'd DB carries its
                 ;; datoms' max tx, which can collide with a version
                 ;; already reachable in this atom (measured: two
                 ;; init-dbs both at tx0 — the reset report's
                 ;; db-before then resolved to the AFTER db). Bump
                 ;; the installed DB's max-tx above every reachable
                 ;; tx so the invariant holds.
                 ceil      (reduce max (:max-tx db-before) (keys (:kept m)))
                 db-after  (if (<= (:max-tx db-after0) ceil)
                             (db/->DB (.-schema db-after0) (.-eavt db-after0)
                               (.-aevt db-after0) (.-avet db-after0)
                               (.-max-eid db-after0) (inc ceil)
                               (.-rschema db-after0))
                             db-after0)
                 tx-data   (into []
                             (concat
                               (map (fn [d] (db/datom (.-e d) (.-a d) (.-v d) (db/datom-tx d) false))
                                 (db/-datoms db-before :eavt nil nil nil nil))
                               (db/-datoms db-after :eavt nil nil nil nil)))]
             (vreset! *raw {:before-tx (:max-tx db-before)
                            :after     db-after
                            :tx-data   tx-data})
             (assoc m :db db-after))))
       (let [{:keys [before-tx after tx-data]} @*raw
             db-after-handle {:atom a :tx (:max-tx after)}
             report (db/->TxReport {:atom a :tx before-tx}
                      db-after-handle
                      ($ (mapv identity tx-data))
                      {}
                      tx-meta)]
         (doseq [[_ callback] @(.-listeners conn)]
           (callback report))
         db-after-handle)))))

(defn with-schema
  "The DB the handle names, with a replaced schema, as the same
   handle: the replacement is installed in the handle's atom (as
   the current DB if the handle is current, else in its :kept
   entry). Warning! No validation or conversion. Only change schema
   in a compatible way."
  [db-handle schema]
  (ensure-handle db-handle)
  (swap! (:atom db-handle)
    (fn [m]
      (if (= (:tx db-handle) (:max-tx (:db m)))
        (assoc m :db (db/with-schema (:db m) schema))
        (if (contains? (:kept m) (:tx db-handle))
          (update m :kept update (:tx db-handle) db/with-schema schema)
          (not-kept! (:tx db-handle) (:max-tx (:db m)))))))
  db-handle)

(defn reset-schema! [conn schema]
  {:pre [(conn? conn)]}
  (check-open conn)
  (swap! (.-eve-atom conn)
    (fn [m] (assoc m :db (db/with-schema (:db m) schema))))
  (db conn))

;; ---------------------------------------------------------------------------
;; Listeners (host-side, as upstream)

(defn listen!
  ([conn callback]
   (listen! conn (rand) callback))
  ([conn key callback]
   {:pre [(conn? conn)]}
   (check-open conn)
   (swap! (.-listeners conn) assoc key callback)
   key))

(defn unlisten! [conn key]
  {:pre [(conn? conn)]}
  (swap! (.-listeners conn) dissoc key))

;; ---------------------------------------------------------------------------
;; Conn-less values (plan §4): each makes a private in-memory conn;
;; the value you get back is a handle into it.

(defn empty-db
  "An empty DB as a handle into a fresh private conn."
  ([]
   (empty-db nil {}))
  ([schema]
   (empty-db schema {}))
  ([schema opts]
   (db (create-conn schema opts))))

(defn init-db
  "A DB built from host Datoms, as a handle into a fresh private
   conn."
  ([datoms]
   (init-db datoms nil))
  ([datoms schema]
   (db (conn-from-datoms datoms schema))))

;; ---------------------------------------------------------------------------
;; Filtered DBs — PARKED at this Eve pin.
;;
;; FilteredDB (db.cljc, per plan §3) carries its pred in a ^:host
;; field, and at pin 323bfe3 a ^:host field reads null on every
;; construction path (U2 probe2 Q3/Q4/Q6 — the Eve seat owns the
;; fix). A filtered DB therefore cannot be constructed at this pin.
;; filter throws a named error rather than a broken value;
;; is-filtered still answers for values that carry the type.

(defn filter
  "PARKED: returns a filtered view of a DB handle. Blocked at the
   current Eve pin on FilteredDB's ^:host pred field (see above)."
  [db pred]
  (ensure-handle db)
  (util/raise
    "Filtered DBs are not constructible at this Eve pin: FilteredDB's ^:host pred field reads null (U2 probe2)"
    {:error :eve.db.ds/filter-blocked :pin "323bfe35901f3f892921eecd41fc5e85be4ef8ec"}))

(defn is-filtered
  "Returns true if this database was filtered using filter."
  [x]
  (boolean (and (some? x) (db/FilteredDB? x))))
