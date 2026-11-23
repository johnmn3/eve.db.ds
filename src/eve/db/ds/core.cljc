(ns eve.db.ds.core
  (:refer-clojure :exclude [filter])
  (:require
    [#?(:cljs cljs.reader :default clojure.edn) :as edn]
    [eve.alpha :as eve]
    [eve.crypto :as crypto]
    [eve.net.bytes :as bytes]
    [eve.db.ds.conn :as conn]
    [eve.db.ds.db :as db #?@(:cljs [:refer [Datom DB FilteredDB]] :lpy [:refer [Datom DB FilteredDB]])]
    [eve.db.ds.db-fn :as dbfn]
    #?(:clj [eve.db.ds.pprint])
    [eve.db.ds.pull-api :as dp]
    [eve.db.ds.query :as dq]
    [eve.db.ds.serialize :as serialize]
    [eve.db.ds.impl.entity :as de #?@(:cljs [:refer [Entity]] :lpy [:refer [Entity]])]
    [eve.db.ds.util :as util])
  #?(:clj
     (:import
       [eve.db.ds.db Datom DB FilteredDB]
       [eve.db.ds.impl.entity Entity]
       [java.util UUID])
     :lpy
     (:import hashlib)))
(def ^:const ^:no-doc tx0 
  db/tx0)
(defn- resolve-db-arg
  "Public read fns take DB handles (plan v2 §4); resolve one to the
   DB value it names. Non-handle values (a raw DB value, U4-internal
   use) pass through unchanged."
  [db]
  (if (conn/handle? db)
    (conn/resolve-db db)
    db))
(defn- deliver-datoms
  "Realize an index iterator inside a read region so its Datoms
   arrive in their host delivery form (plan v2 §4). Returns a
   vector: upstream's lazy iterator cannot leave the region — its
   elements are Eve values until delivered."
  [iter]
  (eve/$ (mapv identity iter)))
(defn- deliver-datom
  "Deliver a single Datom (or nil) in its host form."
  [d]
  (when (some? d)
    (eve/$ (identity d))))
(defonce ^:private region-escapes
  ;; {:db n :datom n :entity n}: DBs, datoms and entities that were
  ;; returned from a read region (and so materialized out of it)
  (atom {:db 0 :datom 0 :entity 0}))

(defn region-escape-counts
  "How many DBs, datoms and entities were returned from a read region
   (and were materialized out at its exit) since the last reset."
  []
  @region-escapes)

(defn reset-region-escape-counts! []
  (reset! region-escapes {:db 0 :datom 0 :entity 0}))

(defn read-in
  "(f) inside an Eve read region (eve.alpha/$): every Eve atom deref'd
   in it answers its stored value in place — slab-backed, pinned until
   the region exits — so q, pull, entity, datoms and index seeks read
   the stored DB directly. The region materializes f's result as
   plain data before the holds are let go, so what f returns is safe
   to hold after the region exits. A region inside another joins it.
   A DB, datom or entity f returns is counted in region-escape-counts."
  [f]
  (let [r (eve/$ (f))]
    (cond (db/db? r)     (swap! region-escapes update :db inc)
          (db/datom? r)  (swap! region-escapes update :datom inc)
          (de/entity? r) (swap! region-escapes update :entity inc))
    r))
(def ^{:tag Entity
       :arglists '([db eid])
       :doc "Retrieves an entity by its id from database. Entities are lazy map-like structures to navigate DataScript database content.

             For `eid` pass entity id or lookup attr:
             
                 (entity db 1)
                 (entity db [:unique-attr :value])
            
             If entity does not exist, `nil` is returned:

                 (entity db 100500) ; => nil

             Creating an entity by id is very cheap, almost no-op, as attr access is on-demand:

                 (entity db 1) ; => {:db/id 1}

             Entity attributes can be lazily accessed through key lookups:
             
                 (:attr (entity db 1)) ; => :value
                 (get (entity db 1) :attr) ; => :value

             Cardinality many attributes are returned sequences:

                 (:attrs (entity db 1)) ; => [:v1 :v2 :v3]

             Reference attributes are returned as another entities:

                 (:ref (entity db 1)) ; => {:db/id 2}
                 (:ns/ref (entity db 1)) ; => {:db/id 2}

             References can be walked backwards by prepending `_` to name part of an attribute:

                 (:_ref (entity db 2)) ; => [{:db/id 1}]
                 (:ns/_ref (entity db 2)) ; => [{:db/id 1}]
             
             Reverse reference lookup returns sequence of entities unless attribute is marked as `:db/isComponent`:

                 (:_component-ref (entity db 2)) ; => {:db/id 1}

             Entity gotchas:
               
             - Entities print as map, but are not exactly maps (they have compatible get interface though).
             - Entities are effectively immutable “views” into a particular version of a database.
             - Entities retain reference to the whole database.
             - You can’t change database through entities, only read.
             - Creating an entity by id is very cheap, almost no-op (attributes are looked up on demand).
             - Comparing entities just compares their ids. Be careful when comparing entities taken from different dbs or from different versions of the same db.
             - Accessed entity attributes are cached on entity itself (except backward references).
             - When printing, only cached attributes (the ones you have accessed before) are printed. See [[touch]]."}
  entity (fn [db eid] (de/entity (resolve-db-arg db) eid (when (conn/handle? db) db))))
(def ^{:arglists '([db eid])
       :doc "Given lookup ref `[unique-attr value]`, returns numberic entity id.

             If entity does not exist, returns `nil`."}
  entid (fn [db eid] (db/entid (resolve-db-arg db) eid)))
(defn ^DB entity-db
  "Returns a db that entity was created from."
  [^Entity entity]
  {:pre [(de/entity? entity)]}
  (.-db entity))
(def ^{:tag Entity
       :arglists '([e])
       :doc "Forces all entity attributes to be eagerly fetched and cached. Only usable for debug output.

             Usage:

             ```
             (entity db 1) ; => {:db/id 1}
             (touch (entity db 1)) ; => {:db/id 1, :dislikes [:pie], :likes [:pizza]}
             ```"}
  touch de/touch)
(def ^{:arglists '([db selector eid])
       :doc "Fetches data from database using recursive declarative description. See [docs.datomic.com/on-prem/pull.html](https://docs.datomic.com/on-prem/pull.html).

             Unlike [[entity]], returns plain Clojure map (not lazy).

             Usage:

                 (pull db [:db/id, :name, :likes, {:friends [:db/id :name]}] 1)
                 ; => {:db/id   1,
                 ;     :name    \"Ivan\"
                 ;     :likes   [:pizza]
                 ;     :friends [{:db/id 2, :name \"Oleg\"}]}"}
  pull (fn
         ([db selector eid] (dp/pull (resolve-db-arg db) selector eid))
         ([db selector eid opts] (dp/pull (resolve-db-arg db) selector eid opts))))
(def ^{:arglists '([db selector eids])
       :doc "Same as [[pull]], but accepts sequence of ids and returns sequence of maps.

             Usage:

             ```
             (pull-many db [:db/id :name] [1 2])
             ; => [{:db/id 1, :name \"Ivan\"}
             ;     {:db/id 2, :name \"Oleg\"}]
             ```"}
  pull-many (fn
              ([db selector eids] (dp/pull-many (resolve-db-arg db) selector eids))
              ([db selector eids opts] (dp/pull-many (resolve-db-arg db) selector eids opts))))
(def
  ^{:arglists '([query & inputs])
    :doc "Executes a datalog query. See [docs.datomic.com/on-prem/query.html](https://docs.datomic.com/on-prem/query.html).

          Usage:
          
          ```
          (q '[:find ?value
               :where [_ :likes ?value]]
             db)
          ; => #{[\"fries\"] [\"candy\"] [\"pie\"] [\"pizza\"]}
          ```"}
  q (fn [query & inputs]
      (apply dq/q query (map resolve-db-arg inputs))))
(defn empty-db
  "Creates an empty database with an optional schema. The value
   returned is a DB handle (plan v2 §4): underneath, a private
   in-memory conn is created and the handle names its DB.

   Usage:

   ```
   (empty-db) ; => {:atom <eve atom> :tx tx0}

   (empty-db {:likes {:db/cardinality :db.cardinality/many}})
   ```

   Options are:

   :persistent <dir>. Back the private conn's atom by this directory."
  ([]
   (conn/empty-db nil {}))
  ([schema]
   (conn/empty-db schema {}))
  ([schema opts]
   (conn/empty-db schema opts)))
(def ^{:arglists '([x])
       :doc "Returns `true` if the given value is an immutable database, `false` otherwise."}
  db? conn/handle?)
(def ^{:tag Datom
       :arglists '([e a v] [e a v tx] [e a v tx added])
       :doc "Low-level fn to create raw datoms.

             Optionally with transaction id (number) and `added` flag (`true` for addition, `false` for retraction).

             See also [[init-db]]."}
  datom db/datom)
(def ^{:arglists '([x])
       :doc "Returns `true` if the given value is a datom, `false` otherwise."}
  datom? db/datom?)
(defn init-db
  "Low-level fn for creating database quickly from a trusted sequence of datoms.
   Does no validation on inputs, so `datoms` must be well-formed and match schema.
   Used internally in db (de)serialization. See also [[datom]].
   Returns a DB handle into a fresh private conn (plan v2 §4)."
  ([datoms]
   (conn/init-db datoms nil))
  ([datoms schema]
   (conn/init-db datoms schema)))
(defn serializable
  "Captures the DB a handle names as plain host data: schema,
   max-eid, max-tx and the datoms as [e a v tx] vectors with values
   encoded to survive EDN/transit/JSON round-trips. See
   eve.db.ds.serialize for the form."
  ([db]
   (serialize/serializable db))
  ([db opts]
   (serialize/serializable db opts)))
(defn from-serializable
  "Rebuilds a DB from serializable's form and returns its handle:
   the datoms are transacted into a fresh private conn."
  ([form]
   (serialize/from-serializable form))
  ([form opts]
   (serialize/from-serializable form opts)))
(def ^{:arglists '([db])
       :doc "Returns a schema of a database."}
  schema (fn [db] (if (conn/handle? db) (conn/schema-of db) (db/-schema db))))
(defn is-filtered
  "Returns `true` if this database was filtered using [[filter]], `false` otherwise."
  [x]
  (conn/is-filtered x))
(defn filter
  "Returns a view over database that has same interface but only includes datoms for which the `(pred db datom)` is true. Can be applied multiple times.

   Filtered DB gotchas:

   - All operations on filtered database are proxied to original DB, then filter pred is applied.
   - Not cached. You pay filter penalty every time.
   - Supports entities, pull, queries, index access.
   - Does not support [[with]] and [[db-with]].

   PARKED at the current Eve pin: FilteredDB's ^:host pred field
   reads null on every construction path (U2 probe2), so a filtered
   DB cannot be constructed yet; this throws :eve.db.ds/filter-blocked
   until the Eve fix lands. See eve.db.ds.conn/filter."
  [db pred]
  (conn/filter db pred))
(def ^{:arglists '([db tx-data] [db tx-data tx-meta])} with
  "Same as [[transact!]], but applies to an immutable database value. Returns transaction report (see [[transact!]])."
  conn/with)
(def ^{:arglists '([db tx-data]) :tag DB} db-with
  "Applies transaction to an immutable db value, returning new immutable db value. Same as `(:db-after (with db tx-data))`."
  conn/db-with)
(defn with-schema
  "Warning! No validation or conversion. Only change schema in a compatible way"
  [db schema]
  (if (conn/handle? db)
    (conn/with-schema db schema)
    (db/with-schema db schema)))
(defn datoms
  "Index lookup. Returns a sequence of datoms (lazy iterator over actual DB index) which components (e, a, v) match passed arguments.

   Datoms are sorted in index sort order. Possible `index` values are: `:eavt`, `:aevt`, `:avet`.

   Usage:

       ; find all datoms for entity id == 1 (any attrs and values)
       ; sort by attribute, then value
       (datoms db :eavt 1)
       ; => (#eve.db.ds/Datom [1 :friends 2]
       ;     #eve.db.ds/Datom [1 :likes \"fries\"]
       ;     #eve.db.ds/Datom [1 :likes \"pizza\"]
       ;     #eve.db.ds/Datom [1 :name \"Ivan\"])
  
       ; find all datoms for entity id == 1 and attribute == :likes (any values)
       ; sorted by value
       (datoms db :eavt 1 :likes)
       ; => (#eve.db.ds/Datom [1 :likes \"fries\"]
       ;     #eve.db.ds/Datom [1 :likes \"pizza\"])
       
       ; find all datoms for entity id == 1, attribute == :likes and value == \"pizza\"
       (datoms db :eavt 1 :likes \"pizza\")
       ; => (#eve.db.ds/Datom [1 :likes \"pizza\"])
  
       ; find all datoms for attribute == :likes (any entity ids and values)
       ; sorted by entity id, then value
       (datoms db :aevt :likes)
       ; => (#eve.db.ds/Datom [1 :likes \"fries\"]
       ;     #eve.db.ds/Datom [1 :likes \"pizza\"]
       ;     #eve.db.ds/Datom [2 :likes \"candy\"]
       ;     #eve.db.ds/Datom [2 :likes \"pie\"]
       ;     #eve.db.ds/Datom [2 :likes \"pizza\"])
  
       ; find all datoms that have attribute == `:likes` and value == `\"pizza\"` (any entity id)
       ; `:likes` must be a unique attr, reference or marked as `:db/index true`
       (datoms db :avet :likes \"pizza\")
       ; => (#eve.db.ds/Datom [1 :likes \"pizza\"]
       ;     #eve.db.ds/Datom [2 :likes \"pizza\"])
  
       ; find all datoms sorted by entity id, then attribute, then value
       (datoms db :eavt) ; => (...)

   Useful patterns:

       ; get all values of :db.cardinality/many attribute
       (->> (datoms db :eavt eid attr) (map :v))
  
       ; lookup entity ids by attribute value
       (->> (datoms db :avet attr value) (map :e))
  
       ; find all entities with a specific attribute
       (->> (datoms db :aevt attr) (map :e))
  
       ; find “singleton” entity by its attr
       (->> (datoms db :aevt attr) first :e)
  
       ; find N entities with lowest attr value (e.g. 10 earliest posts)
       (->> (datoms db :avet attr) (take N))
  
       ; find N entities with highest attr value (e.g. 10 latest posts)
       (->> (datoms db :avet attr) (reverse) (take N))

   Gotchas:
   
   - Index lookup is usually more efficient than doing a query with a single clause.
   - Resulting iterator is calculated in constant time and small constant memory overhead.
   - Iterator supports efficient `first`, `next`, `reverse`, `seq` and is itself a sequence.
   - Will not return datoms that are not part of the index (e.g. attributes with no `:db/index` in schema when querying `:avet` index).
     - `:eavt` and `:aevt` contain all datoms.
     - `:avet` only contains datoms for references, `:db/unique` and `:db/index` attributes."
  ([db index]             {:pre [(some? db)]} (deliver-datoms (db/-datoms (resolve-db-arg db) index nil nil nil nil)))
  ([db index c0]          {:pre [(some? db)]} (deliver-datoms (db/-datoms (resolve-db-arg db) index c0  nil nil nil)))
  ([db index c0 c1]       {:pre [(some? db)]} (deliver-datoms (db/-datoms (resolve-db-arg db) index c0  c1  nil nil)))
  ([db index c0 c1 c2]    {:pre [(some? db)]} (deliver-datoms (db/-datoms (resolve-db-arg db) index c0  c1  c2  nil)))
  ([db index c0 c1 c2 c3] {:pre [(some? db)]} (deliver-datoms (db/-datoms (resolve-db-arg db) index c0  c1  c2  c3))))
(defn ^Datom find-datom
  "Same as [[datoms]], but only returns single datom. Faster than `(first (datoms ...))`"
  ([db index]             {:pre [(some? db)]} (deliver-datom (db/find-datom (resolve-db-arg db) index nil nil nil nil)))
  ([db index c0]          {:pre [(some? db)]} (deliver-datom (db/find-datom (resolve-db-arg db) index c0  nil nil nil)))
  ([db index c0 c1]       {:pre [(some? db)]} (deliver-datom (db/find-datom (resolve-db-arg db) index c0  c1  nil nil)))
  ([db index c0 c1 c2]    {:pre [(some? db)]} (deliver-datom (db/find-datom (resolve-db-arg db) index c0  c1  c2  nil)))
  ([db index c0 c1 c2 c3] {:pre [(some? db)]} (deliver-datom (db/find-datom (resolve-db-arg db) index c0  c1  c2  c3))))
(defn seek-datoms
  "Similar to [[datoms]], but will return datoms starting from specified components and including rest of the database until the end of the index.

   If no datom matches passed arguments exactly, iterator will start from first datom that could be considered “greater” in index order.

   Usage:

       (seek-datoms db :eavt 1)
       ; => (#eve.db.ds/Datom [1 :friends 2]
       ;     #eve.db.ds/Datom [1 :likes \"fries\"]
       ;     #eve.db.ds/Datom [1 :likes \"pizza\"]
       ;     #eve.db.ds/Datom [1 :name \"Ivan\"]
       ;     #eve.db.ds/Datom [2 :likes \"candy\"]
       ;     #eve.db.ds/Datom [2 :likes \"pie\"]
       ;     #eve.db.ds/Datom [2 :likes \"pizza\"])

       (seek-datoms db :eavt 1 :name)
       ; => (#eve.db.ds/Datom [1 :name \"Ivan\"]
       ;     #eve.db.ds/Datom [2 :likes \"candy\"]
       ;     #eve.db.ds/Datom [2 :likes \"pie\"]
       ;     #eve.db.ds/Datom [2 :likes \"pizza\"])
  
       (seek-datoms db :eavt 2) 
       ; => (#eve.db.ds/Datom [2 :likes \"candy\"]
       ;     #eve.db.ds/Datom [2 :likes \"pie\"]
       ;     #eve.db.ds/Datom [2 :likes \"pizza\"])
  
       ; no datom [2 :likes \"fish\"], so starts with one immediately following such in index
       (seek-datoms db :eavt 2 :likes \"fish\")
       ; => (#eve.db.ds/Datom [2 :likes \"pie\"]
       ;     #eve.db.ds/Datom [2 :likes \"pizza\"])"
  ([db index]             {:pre [(some? db)]} (deliver-datoms (db/-seek-datoms (resolve-db-arg db) index nil nil nil nil)))
  ([db index c0]          {:pre [(some? db)]} (deliver-datoms (db/-seek-datoms (resolve-db-arg db) index c0  nil nil nil)))
  ([db index c0 c1]       {:pre [(some? db)]} (deliver-datoms (db/-seek-datoms (resolve-db-arg db) index c0  c1  nil nil)))
  ([db index c0 c1 c2]    {:pre [(some? db)]} (deliver-datoms (db/-seek-datoms (resolve-db-arg db) index c0  c1  c2  nil)))
  ([db index c0 c1 c2 c3] {:pre [(some? db)]} (deliver-datoms (db/-seek-datoms (resolve-db-arg db) index c0  c1  c2  c3))))
(defn rseek-datoms
  "Same as [[seek-datoms]], but goes backwards until the beginning of the index."
  ([db index]             {:pre [(some? db)]} (deliver-datoms (db/-rseek-datoms (resolve-db-arg db) index nil nil nil nil)))
  ([db index c0]          {:pre [(some? db)]} (deliver-datoms (db/-rseek-datoms (resolve-db-arg db) index c0  nil nil nil)))
  ([db index c0 c1]       {:pre [(some? db)]} (deliver-datoms (db/-rseek-datoms (resolve-db-arg db) index c0  c1  nil nil)))
  ([db index c0 c1 c2]    {:pre [(some? db)]} (deliver-datoms (db/-rseek-datoms (resolve-db-arg db) index c0  c1  c2  nil)))
  ([db index c0 c1 c2 c3] {:pre [(some? db)]} (deliver-datoms (db/-rseek-datoms (resolve-db-arg db) index c0  c1  c2  c3))))
(defn index-range
  "Returns part of `:avet` index between `[_ attr start]` and `[_ attr end]` in AVET sort order.
  
   Same properties as [[datoms]].
   
   `attr` must be a reference, unique attribute or marked as `:db/index true`.
   
   Usage:

       (index-range db :likes \"a\" \"zzzzzzzzz\")
       ; => (#eve.db.ds/Datom [2 :likes \"candy\"]
       ;     #eve.db.ds/Datom [1 :likes \"fries\"]
       ;     #eve.db.ds/Datom [2 :likes \"pie\"]
       ;     #eve.db.ds/Datom [1 :likes \"pizza\"]
       ;     #eve.db.ds/Datom [2 :likes \"pizza\"])
        
       (index-range db :likes \"egg\" \"pineapple\")
       ; => (#eve.db.ds/Datom [1 :likes \"fries\"]
       ;     #eve.db.ds/Datom [2 :likes \"pie\"])
           
   Useful patterns:
     
       ; find all entities with age in a specific range (inclusive)
       (->> (index-range db :age 18 60) (map :e))"
  [db attr start end]
  {:pre [(some? db)]} ;; U6: was (db/db? db) on the raw arg — the only
  ;; precondition in core that rejected a §4 handle its own body
  ;; resolves (every sibling: some? + resolve-db-arg in the body).
  (deliver-datoms (db/-index-range (resolve-db-arg db) attr start end)))
(def ^{:arglists '([conn])} conn?
  "Returns `true` if this is a connection to a DataScript db, `false` otherwise."
  conn/conn?)
(def ^{:arglists '([db])} conn-from-db
  "Creates a mutable reference to a given immutable database. See [[create-conn]]."
  conn/conn-from-db)
(def ^{:arglists '([datoms] [datoms schema] [datoms schema opts])} conn-from-datoms
  "Creates an empty DB and a mutable reference to it. See [[create-conn]]."
  conn/conn-from-datoms)
(def ^{:arglists '([] [schema] [schema opts])} create-conn
  "Creates a mutable reference (a “connection”) to an empty immutable database.

   Connections are lightweight in-memory structures (~atoms) with direct support of transaction listeners ([[listen!]], [[unlisten!]]) and other handy DataScript APIs ([[transact!]], [[reset-conn!]], [[db]]).

   To access underlying immutable DB value, deref: `@conn`.
   
   For list of options, see [[empty-db]].
   
   If you specify `:storage` option, conn will be stored automatically after each transaction"
  conn/create-conn)
(def ^{:arglists '([conn db] [db])} keep!
  "Pin the DB the handle names in its conn's atom, so reads through
   the handle keep resolving after the conn moves on (plan v2 §4).
   The DB must still be reachable — current, or already kept.
   The one-argument form pins in the handle's own atom — for
   conn-less values, whose holder has a handle but no conn; a
   keep!'d chain DB is never auto-released as the chain advances.
   Additive in this rewrite; upstream has no equivalent."
  conn/keep!)
(def ^{:arglists '([conn db] [db])} release!
  "Remove the DB the handle names from its conn's atom :kept map.
   Afterwards, reads through the handle throw :eve.db.ds/db-not-kept
   unless the DB is the conn's current one again. The one-argument
   form removes from the handle's own atom — for conn-less values.
   Additive in this rewrite; upstream has no equivalent."
  conn/release!)
(def ^{:arglists '([conn])} close-conn!
  "Release the conn's Eve atom (and, for a :persistent conn, its
   backing domain). Additive in this rewrite; upstream has no
   equivalent."
  conn/close-conn!)
(def ^{:arglists '([conn tx-data] [conn tx-data tx-meta])} transact!
  "Applies transaction the underlying database value and atomically updates connection reference to point to the result of that transaction, new db value.
  
   Returns transaction report, a map:

       {:db-before ...      ; db value before transaction
        :db-after  ...      ; db value after transaction
        :tx-data   [...]    ; plain datoms that were added/retracted from db-before
        :tempids   {...}    ; map of tempid from tx-data => assigned entid in db-after
        :tx-meta   tx-meta} ; the exact value you passed as `tx-meta`

  Note! `conn` will be updated in-place and is not returned from [[transact!]].
  
  Usage:

      ; add a single datom to an existing entity (1)
      (transact! conn [[:db/add 1 :name \"Ivan\"]])
  
      ; retract a single datom
      (transact! conn [[:db/retract 1 :name \"Ivan\"]])
  
      ; retract single entity attribute
      (transact! conn [[:db.fn/retractAttribute 1 :name]])
  
      ; ... or equivalently (since Datomic changed its API to support this):
      (transact! conn [[:db/retract 1 :name]])
      
      ; retract all entity attributes (effectively deletes entity)
      (transact! conn [[:db.fn/retractEntity 1]])
  
      ; create a new entity (`-1`, as any other negative value, is a tempid
      ; that will be replaced with DataScript to a next unused eid)
      (transact! conn [[:db/add -1 :name \"Ivan\"]])
  
      ; check assigned id (here `*1` is a result returned from previous `transact!` call)
      (def report *1)
      (:tempids report) ; => {-1 296}
  
      ; check actual datoms inserted
      (:tx-data report) ; => [#eve.db.ds/Datom [296 :name \"Ivan\"]]
  
      ; tempid can also be a string
      (transact! conn [[:db/add \"ivan\" :name \"Ivan\"]])
      (:tempids *1) ; => {\"ivan\" 297}
  
      ; reference another entity (must exist)
      (transact! conn [[:db/add -1 :friend 296]])
  
      ; create an entity and set multiple attributes (in a single transaction
      ; equal tempids will be replaced with the same yet unused entid)
      (transact! conn [[:db/add -1 :name \"Ivan\"]
                       [:db/add -1 :likes \"fries\"]
                       [:db/add -1 :likes \"pizza\"]
                       [:db/add -1 :friend 296]])
  
      ; create an entity and set multiple attributes (alternative map form)
      (transact! conn [{:db/id  -1
                        :name   \"Ivan\"
                        :likes  [\"fries\" \"pizza\"]
                        :friend 296}])
      
      ; update an entity (alternative map form). Can’t retract attributes in
      ; map form. For cardinality many attrs, value (fish in this example)
      ; will be added to the list of existing values
      (transact! conn [{:db/id  296
                        :name   \"Oleg\"
                        :likes  [\"fish\"]}])

      ; ref attributes can be specified as nested map, that will create nested entity as well
      (transact! conn [{:db/id  -1
                        :name   \"Oleg\"
                        :friend {:db/id -2
                                 :name \"Sergey\"}}])
                                 
      ; reverse attribute name can be used if you want created entity to become
      ; a value in another entity reference
      (transact! conn [{:db/id  -1
                        :name   \"Oleg\"
                        :_friend 296}])
      ; equivalent to
      (transact! conn [{:db/id  -1, :name   \"Oleg\"}
                       {:db/id 296, :friend -1}])
      ; equivalent to
      (transact! conn [[:db/add  -1 :name   \"Oleg\"]
                       [:db/add 296 :friend -1]])"
  conn/transact!)
(def ^{:arglists '([conn db] [conn db tx-meta])} reset-conn!
  "Forces underlying `conn` value to become `db`. Will generate a tx-report that will remove everything from old value and insert everything from the new one."
  conn/reset-conn!)
(def ^{:arglists '([conn schema])} reset-schema!
  conn/reset-schema!)
(def ^{:arglists '([conn callback] [conn key callback])} listen!
  "Listen for changes on the given connection. Whenever a transaction is applied to the database via [[transact!]], the callback is called
   with the transaction report. `key` is any opaque unique value.
   
   Idempotent. Calling [[listen!]] with the same key twice will override old callback with the new value.
   
   Returns the key under which this listener is registered. See also [[unlisten!]]."
  conn/listen!)
(def ^{:arglists '([conn key])} unlisten!
  "Removes registered listener from connection. See also [[listen!]]."
  conn/unlisten!)
(def ^{:doc "Data readers for EDN readers. In CLJS they’re registered automatically. In CLJ, if `data_readers.clj` do not work, you can always do

             ```
             (clojure.edn/read-string {:readers data-readers} \"...\")
             ```"}
  data-readers {'eve.db.ds/Datom db/datom-from-reader
                'eve.db.ds/DB    db/db-from-reader})
#?(:cljs
   (doseq [[tag cb] data-readers] (edn/register-tag-parser! tag cb)))
(def ^:private last-tempid (atom -1000000))
(defn tempid
  "Allocates and returns an unique temporary id (a negative integer). Ignores `part`. Returns `x` if it is specified.
  
   Exists for Datomic API compatibility. Prefer using negative integers directly if possible."
  ([part]
   (if (= part :db.part/tx)
     :db/current-tx
     (swap! last-tempid dec)))
  ([part x]
   (if (= part :db.part/tx)
     :db/current-tx
     x)))
(defn resolve-tempid
  "Does a lookup in tempids map, returning an entity id that tempid was resolved to.
   
   Exists for Datomic API compatibility. Prefer using map lookup directly if possible."
  [_db tempids tempid]
  (get tempids tempid))
(defn ^DB db
  "Returns the underlying immutable database value from a connection.
   
   Exists for Datomic API compatibility. Prefer using `@conn` directly if possible."
  [conn]
  {:pre [(conn? conn)]}
  @conn)
(defn transact
  "Same as [[transact!]], but returns an immediately realized future.
  
   Exists for Datomic API compatibility. Prefer using [[transact!]] if possible."
  ([conn tx-data] (transact conn tx-data nil))
  ([conn tx-data tx-meta]
   {:pre [(conn? conn)]}
   (let [res (transact! conn tx-data tx-meta)]
     #?(:cljs
        (reify
          IDeref
          (-deref [_] res)
          IDerefWithTimeout
          (-deref-with-timeout [_ _ _] res)
          IPending
          (-realized? [_] true))
        :clj
        (reify
          clojure.lang.IDeref
          (deref [_] res)
          clojure.lang.IBlockingDeref
          (deref [_ _ _] res)
          clojure.lang.IPending
          (isRealized [_] true))
        :lpy
        (let [p (promise)]
          (deliver p res)
          p)))))


;; ersatz future without proper blocking
#?(:clj
   (defn- future-call [f]
     (future (f)))
   :default
   (defn- future-call [f]
     (future (f)))
   :cljs
   (defn- future-call [f]
     (let [res      (atom nil)
           realized (atom false)]
       (js/setTimeout #(do (reset! res (f)) (reset! realized true)) 0)
       (reify
         IDeref
         (-deref [_] @res)
         IDerefWithTimeout
         (-deref-with-timeout [_ _ timeout-val] (if @realized @res timeout-val))
         IPending
         (-realized? [_] @realized)))))
(defn transact-async
  "In CLJ, calls [[transact!]] on a future thread pool, returning immediately.
  
   In CLJS, just calls [[transact!]] and returns a realized future."
  ([conn tx-data] (transact-async conn tx-data nil))
  ([conn tx-data tx-meta]
   {:pre [(conn? conn)]}
   (future-call #(transact! conn tx-data tx-meta))))
#?(:clj
   (defn- db-fn-var-name
     "The generated var name for a db-fn form: content-addressed
      from the caller's namespace and the printed fn form (SHA-256,
      first 12 hex chars, db-fn- prefixed). Deterministic: the
      same form in the same namespace always derives the same
      name, so re-evaluating a form re-interns and re-registers
      the same var; distinct forms derive distinct names. Two
      textually identical forms intentionally share one var —
      they are the same fn. The digest goes through eve.crypto
      (with eve.net.bytes for the UTF-8 bytes and the hex) rather
      than java.security.MessageDigest directly: the bytes are
      the same SHA-256 on every host, and a :clj-reading host
      without a JDK provider for MessageDigest (jolt, measured)
      still answers — the same route eve.flat.chunk's fingerprint
      takes, for the same reason."
     [ns-sym params body]
     (let [input (str ns-sym " " (pr-str (list* 'fn params body)))
           hex   (bytes/hex (crypto/sha256 (bytes/utf8 input)))]
       (symbol (str "db-fn-" (subs hex 0 12))))))

#?(:lpy
   (defn- db-fn-var-name
     "The generated var name for a db-fn form: content-addressed
      from the caller's namespace and the printed fn form (SHA-1,
      first 12 hex chars, db-fn- prefixed) — the :clj arm's
      contract, over Python's hashlib (the same SHA-1 of the same
      UTF-8 bytes, so the derived names agree across hosts)."
     [ns-sym params body]
     (let [input (str ns-sym " " (pr-str (list* 'fn params body)))
           hex   (.hexdigest (hashlib/sha1 (.encode input "utf-8")))]
       (symbol (str "db-fn-" (subs hex 0 12))))))

#?(:cljs nil
   :default
   (defmacro db-fn
     "Builds a stored transaction function from a plain fn form —
      the DEFAULT kind (John's ruling, 2026-11-20: \"Let's default
      to host fns and let you opt into sci fns\"). The body stays
      a host fn: the expansion defines it as a var in YOUR
      namespace, registers it for :resolve lookup, and evaluates
      to the stored map {:db.fn/kind :resolve, :db.fn/sym
      <your-ns>/db-fn-<hash>} — pure data, safe to transact as a
      :db/fn value.

      The generated var name is content-addressed (see
      db-fn-var-name): db-fn-<hash>, derived from your namespace
      and the form itself, so re-evaluating the same form
      re-registers the same name, and the var is a real var in
      your namespace that you can inspect and call. The body is
      ordinary code in your namespace — the full host API at host
      speed, no sandbox. The DB stores only the name, so a process
      that reads the DB must evaluate the defining form (the
      registration runs then; on ClojureScript this is the only
      path) or register the same symbol itself; on the JVM,
      requiring-resolve is the fallback.

      Usage:

          (transact! conn [{:db/ident :inc-age
                            :db/fn    (db-fn [db name]
                                        (if-some [ent (d/entity db [:name name])]
                                          [{:db/id (:db/id ent)
                                            :age   (inc (:age ent))}]
                                          (throw (ex-info \"No entity\" {}))))}])
          (transact! conn [[:inc-age \"Petr\"]])

      For a fn whose source travels with the DB instead, see
      [[sci-fn]] (the SCI opt-in) and [[function]]. See GUIDE §5
      for the two stored-fn kinds and their trade-offs."
     {:arglists '([params & body])}
     [params & body]
     (let [ns-sym (or (get-in &env [:ns :name]) (ns-name *ns*))
           vname  (db-fn-var-name ns-sym params body)
           fq-sym (symbol (str ns-sym) (str vname))]
       (list 'do
         (list 'def vname (list* 'fn params body))
         (list 'eve.db.ds.db-fn/register-fn! (list 'quote fq-sym) vname)
         (list 'eve.db.ds.db-fn/resolve-fn (list 'quote fq-sym))))))

#?(:cljs nil
   :default
   (defmacro sci-fn
     "Builds a stored transaction function as DATA, the SCI kind —
      the explicit opt-in (the default is a host fn; see
      [[db-fn]]). The fn body is sourcified at macro time into a
      SCI stored db fn ({:db.fn/kind :sci, :db.fn/params params,
      :db.fn/code \"<body>\"}), safe to transact as a :db/fn
      value — it lives in the DB as data and is evaluated by SCI,
      sandboxed, at transact time, so the fn travels with the DB:
      any process that can read the DB can run it, with no code
      of its own registered or resolvable.

      Usage is identical in shape to [[db-fn]]: (sci-fn [db name]
      body...). Inside the body, the DB read API ([[entity]],
      [[entid]], [[q]], [[datoms]], [[seek-datoms]], [[pull]],
      [[pull-many]]) is available unqualified, as d/<name>, or
      fully qualified — and nothing beyond that surface and
      clojure.core: the body cannot reach host internals. See
      GUIDE §5 for the two stored-fn kinds and their trade-offs."
     {:arglists '([params & body])}
     [params & body]
     (list 'eve.db.ds.db-fn/sci-fn
       (list 'quote params)
       (apply str (interpose "\n" (map pr-str body))))))
(def ^{:arglists '([sym])
       :doc "Builds a stored transaction function that resolves a
             host fn by name at transact time:
             {:db.fn/kind :resolve, :db.fn/sym sym}. The DB stays
             pure data; the named fn is found through the registry
             ([[register-fn!]], both hosts) or, on the JVM, by
             requiring-resolve. The fn receives the DB value and
             the call args and returns tx-data, at host speed.

             Usage:

                 (register-fn! 'my.app/inc-age inc-age)
                 (transact! conn [{:db/ident :inc-age
                                   :db/fn    (fn-ref 'my.app/inc-age)}])

             Or see [[db-fn]], which defines the fn as a var and
             registers it for you."}
  fn-ref dbfn/resolve-fn)
(def ^{:arglists '([m])
       :doc "The explicit stored-fn map form (Datomic's function
             shape): (function {:lang :clojure, :params [db & args],
             :code \"<body>\"}) normalizes to a SCI stored db fn —
             the SCI opt-in in map form. :code may be a string or
             a single form, which is sourcified. See [[sci-fn]] for
             the macro form."}
  function dbfn/function)
(def ^{:arglists '([sym f])} register-fn!
  "Registers host fn f under fully-qualified symbol sym for
   :resolve stored db fns (see [[fn-ref]]). The registry is the
   portable path: it works on both hosts, unlike JVM-only
   requiring-resolve. Returns sym."
  dbfn/register-fn!)
(def ^{:arglists '([sym])} unregister-fn!
  "Removes sym from the :resolve db-fn registry. Returns sym."
  dbfn/unregister-fn!)
(def ^{:arglists '([] [msec])} squuid
  "Generates a UUID that grow with time. Such UUIDs will always go to the end  of the index and that will minimize insertions in the middle.
  
   Consist of 64 bits of current UNIX timestamp (in seconds) and 64 random bits (2^64 different unique values per second)."
  util/squuid)
(def ^{:arglists '([uuid])} squuid-time-millis
  "Returns time that was used in [[squuid]] call, in milliseconds, rounded to the closest second."
  util/squuid-time-millis)
(defn settings
  "The DB's settings map. The indexes are eve.db.ds.bset trees; the
   one setting that survives the port is the branching factor — a
   bset leaf holds up to 32 keys (plan v2 §5)."
  [db]
  {:branching-factor 32})
(dbfn/register-sci-surface!
  {'entity     entity
   'entid      entid
   'q          q
   'datoms     datoms
   'seek-datoms seek-datoms
   'pull       pull
   'pull-many  pull-many})
