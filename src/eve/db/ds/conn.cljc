(ns eve.db.ds.conn
  (:require
    ;; PORT DELTA (F-NBB-MACROS round): vanilla refers the DB types
    ;; on :cljs; the interpreted seat (nbb/SCI) cannot refer a type
    ;; name (a class entry, not a var), so the types are named
    ;; qualified (db/DB) at their uses in this file
    [eve.db.ds.db :as db]
    ;; PORT: eve.deftype referred on every host — the refer is what
    ;; extend/deftype-atom's expansion resolves `deftype` through on
    ;; the interpreted seats; referring it unconditionally is the same
    ;; wiring with no branch (this ns defines no deftype of its own).
    [eve.deftype :refer [deftype]]
    [eve.db.ds.storage :as storage]
    [extend-clj.core :as extend]
    [me.tonsky.persistent-sorted-set :as set]
    ;; PORT DELTA (F-CLJE-HOST-REFERS): see eve.db.ds.util
    #?(:clje [eve.host :refer [volatile! swap! reset! compare-and-set! locking]])))

(extend/deftype-atom Conn [atom]
  (deref-impl [this]
    (:db @atom))
  (compare-and-set-impl [this oldv newv]
    (compare-and-set!
      atom
      ;; the expected state: when :db already holds oldv, reuse @atom so
      ;; CAS compares against the atom's current identity. JVM/cljs
      ;; array-maps already shortcut assoc to the same object; jank maps
      ;; copy on assoc, so the explicit identical? is required there and
      ;; is equivalent on hosts with the shortcut (F-JANK-CAS-ASSOC-IDENTITY).
      (if (identical? (:db @atom) oldv) @atom (assoc @atom :db oldv))
      (assoc @atom :db newv))))

(defn- make-conn [opts]
  (->Conn (atom opts)))

(defn with
  ([db tx-data] (with db tx-data nil))
  ([db tx-data tx-meta]
   {:pre [(db/db? db)]}
   (if (not (identical? db (db/unfiltered-db db)))
     (throw (ex-info "Filtered DB cannot be modified" {:error :transaction/filtered}))
     ;; Construction law: Conn is a host-atom-backed deftype (above) —
     ;; verified 2026-11-11: its swap! is the HOST atom's swap and does
     ;; NOT bind Eve's *domain-sio* (that binding exists only in
     ;; eve.atom/swap-attempt!). So the transaction runs under an
     ;; explicit binding of the DB's own domain: -transact! reaches this
     ;; fn from inside Conn's swap!, and everything the transaction
     ;; constructs (TxReport, the new DB, tempid maps) belongs to the
     ;; DB's domain.
     (db/call-in-db-domain db
       (fn [] (db/transact-tx-data (db/->TxReport db db [] {} tx-meta) tx-data))))))

(defn db-with
  "Applies transaction to an immutable db value, returning new immutable db value. Same as `(:db-after (with db tx-data))`."
  [db tx-data]
  {:pre [(db/db? db)]}
  (:db-after (with db tx-data)))

(defn conn? [conn]
  (and
    (instance? Conn conn)
    (if-some [db @conn]
      (db/db? db)
      true)))

(defn conn-from-db [db]
  {:pre [(db/db? db)]}
  (if-some [storage (storage/storage db)]
    (do
      (storage/store db)
      (make-conn
        {:db db
         :tx-tail []
         :db-last-stored db}))
    (make-conn {:db db})))

(defn conn-from-datoms
  ([datoms]
   (conn-from-db (db/init-db datoms nil {})))
  ([datoms schema]
   (conn-from-db (db/init-db datoms schema {})))
  ([datoms schema opts]
   (conn-from-db (db/init-db datoms schema (storage/maybe-adapt-storage opts)))))

(defn create-conn
  ([]
   (conn-from-db (db/empty-db nil {})))
  ([schema]
   (conn-from-db (db/empty-db schema {})))
  ([schema opts]
   (conn-from-db (db/empty-db schema (storage/maybe-adapt-storage opts)))))

(defn restore-conn
  "On Eve, a DB lives in an Eve atom; there is no separate IStorage.
   storage/restore-impl raises that guidance."
  ([storage]
   (restore-conn storage {}))
  ([storage opts]
   (when-some [[db tail] (storage/restore-impl storage opts)]
     (make-conn
       {:db (storage/db-with-tail db tail)
        :tx-tail tail
        :db-last-stored db}))))

(defn ^:no-doc -transact! [conn tx-data tx-meta]
  {:pre [(conn? conn)]}
  (let [*report (volatile! nil)]
    (swap! conn
      (fn [db]
        (let [r (with db tx-data tx-meta)]
          (vreset! *report r)
          (:db-after r))))
    ;; Persistence is the Eve atom that holds the DB. storage/storage is
    ;; always nil here (no separate IStorage adapter), so the vanilla
    ;; tail-store side channel is gone.
    @*report))

(defn transact!
  ([conn tx-data]
   (transact! conn tx-data nil))
  ([conn tx-data tx-meta]
   {:pre [(conn? conn)]}
   (locking conn
     (let [report (-transact! conn tx-data tx-meta)]
       (doseq [[_ callback] (:listeners @(:atom conn))]
         (callback report))
       report))))

(defn reset-conn!
  ([conn db]
   (reset-conn! conn db nil))
  ([conn db tx-meta]
   {:pre [(conn? conn)
          (db/db? db)]}
   (let [db-before @conn
         report    (db/map->TxReport
                     {:db-before db-before
                      :db-after  db
                      :tx-data   (concat
                                   (when db-before
                                     (map #(assoc % :added false) (db/-datoms db-before :eavt nil nil nil nil)))
                                   (db/-datoms db :eavt nil nil nil nil))
                      :tx-meta   tx-meta})]
     (if (storage/storage db-before)
       (do
         (storage/store db)
         (swap! (:atom conn) assoc
           :db             db
           :tx-tail        []
           :db-last-stored db))
       (reset! conn db))
     (doseq [[_ callback] (:listeners @(:atom conn))]
       (callback report))
     db)))

(defn reset-schema! [conn schema]
  {:pre [(conn? conn)]}
  (let [db (swap! conn db/with-schema schema)]
    ;; Persistence is the Eve atom that holds the DB; no IStorage side channel.
    db))

(defn listen!
  ([conn callback]
   (listen! conn (rand) callback))
  ([conn key callback]
   {:pre [(conn? conn)]}
   (swap! (:atom conn) update :listeners assoc key callback)
   key))

(defn unlisten! [conn key]
  {:pre [(conn? conn)]}
  (swap! (:atom conn) update :listeners dissoc key))
