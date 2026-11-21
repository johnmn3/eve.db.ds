# eve.db.ds — the guide

This is DataScript 1.8.1's API (`datascript.core`, here
`eve.db.ds.core`) with one structural difference that everything else
follows from: **the database lives in an Eve atom**. This guide states
the rules where they apply, function by function. Call shapes are
upstream's; where a call shape or a return value differs from upstream,
the difference is stated, not implied.

**Status: rewrite in progress.** Sections whose implementing unit has
not landed yet are marked *(in progress)* and describe the plan's
contract (DS-IN-EVE-PLAN v2 §4), not a measured behavior.

## 1. The model

- A **conn** is a host object holding two things: an **Eve atom** and a
  host-side registry of listeners. The Eve atom's value is a plain map:

  ```clojure
  {:db DB  :kept {tx DB, …}}
  ```

  `:db` is the current database. `:kept` holds older databases that
  someone has explicitly kept (§3). The DB, its indexes and its datoms
  are Eve values inside that atom — not host objects, not a cache of
  something stored elsewhere.
- A **DB value**, everywhere in this API, is a **handle**:
  `{:atom a :tx n}` — the conn's Eve atom and a transaction number.
  `@conn` cannot return the DB itself (an Eve atom does not hand its
  contents out by deref), so it returns a handle, and every read
  resolves the handle against the atom at the moment of the read: if
  the atom's current DB is the handle's transaction, you read that; if
  not, you read the handle's entry in `:kept`; if there is none, the
  read throws (§3).
- **Returns are host data only.** Every public function in this guide
  returns ordinary host values. Datoms come out as host Datoms
  (upstream's `Datom` type). No Eve type ever appears in a result.

## 2. Connections *(in progress — U3)*

```clojure
(d/create-conn)                    ;; empty DB, no schema
(d/create-conn schema)             ;; with a schema map
(d/create-conn schema {:persistent "path/to/dir"})  ;; persistent (§8)
(d/conn? x)
```

`create-conn` creates the Eve atom and builds the empty DB inside it in
one transaction. A conn is truthy for `conn?`; DB handles are not (use
`db?`, §4).

```clojure
(d/conn-from-db db)                ;; a new conn whose DB is the given one
(d/conn-from-datoms datoms)        ;; a new conn transacted from datoms
(d/conn-from-datoms datoms schema)
```

`conn-from-db` takes a DB handle and gives it a conn of its own.
`conn-from-datoms` builds a conn by transacting the given host datoms.

```clojure
(d/close-conn! conn)               ;; additive in this rewrite
```

Releases the conn's Eve atom (and, for a persistent conn, its backing
domain). After `close-conn!`, reads through the conn's handles are not
guaranteed to resolve. `close-conn!` is not an upstream var; it exists
because an Eve atom is a resource in a way a host atom is not.

## 3. Keeping old DB values *(in progress — U3)*

An Eve atom holds its current value; yesterday's value is gone unless
something in the atom still refers to it. That rule is the whole
retention story of this library:

```clojure
(def db-before (d/db conn))        ;; handle to the current DB
(d/keep! conn db-before)           ;; pin it in the atom's :kept map
;; … transact as much as you like …
(d/q '[:find ?e :where [?e :name "Ivan"]] db-before)   ;; still reads
(d/release! conn db-before)        ;; remove it from :kept
```

- `keep!` and `release!` are additive vars of this rewrite; upstream
  has no equivalent because upstream DB values are self-contained host
  values. Here they are the mechanism, and there is no other one: **no
  blanket pinning** — the library does not keep old DBs on your behalf
  beyond the current one.
- Reading a handle that is neither current nor kept throws
  `:eve.db.ds/db-not-kept`. The error names both transaction numbers:
  the handle's, and the conn's current one. If you see it, the DB you
  wanted was released (or never kept) — keep it earlier next time.
- The current DB never needs keeping: a handle to it resolves as long
  as it is still current.
- Rule of thumb: **if you want an old DB value to stay referable, the
  reference to it must live in the atom** — `keep!` is how you put it
  there. An atom can do the job of a host garbage collector exactly
  when the things that refer to a value live in the atom alongside it.

## 4. DB values and conn-less values *(in progress — U2, U3)*

```clojure
(d/db conn)        ;; handle to the conn's current DB; @conn is the same
@conn
(d/db? x)          ;; is x a DB handle?
```

Value-level construction, upstream-style — no explicit conn:

```clojure
(d/empty-db)                       ;; empty DB (optionally with schema)
(d/empty-db schema)
(d/init-db datoms)                 ;; DB built from host datoms
(d/init-db datoms schema)
(d/db-with db tx-data)             ;; => handle to the new DB
(d/with db tx-data)                ;; => tx-report; :db-after is a handle
(d/filter db pred)                 ;; => filtered DB handle
(d/is-filtered db)
(d/with-schema db schema)          ;; DB with a replaced schema
(d/schema db)                      ;; the DB's schema map (host data)
```

Underneath, each `empty-db`/`init-db` makes a **private in-memory
conn**, and the value you get back is a handle into it. `db-with` adds
the new DB to that conn's `:kept` set, so chained value-level work
keeps resolving:

```clojure
(-> (d/empty-db)
    (d/db-with [{:db/id 1 :name "Ivan"}])
    (d/db-with [{:db/id 2 :name "Petr"}]))   ;; both steps stay readable
```

Whether conn-less values are released explicitly by the caller or
reclaimed by the host is an **open item in the plan (§8)** — the
mechanism above is settled, the release policy is *(in progress —
awaiting the §8 ruling)*. Do not design around either answer yet.

`filter` returns a filtered view as a handle, with upstream semantics;
`is-filtered` tests for one.

## 5. Transacting *(in progress — U3)*

```clojure
(d/transact! conn tx-data)         ;; => tx-report (host data)
(d/transact! conn tx-data tx-meta)
(d/transact db tx-data)            ;; value-level: => tx-report
(d/transact-async conn tx-data)    ;; => future of tx-report
(d/resolve-tempid tempids tempid)  ;; => real entity id, or nil
(d/tempid part)                    ;; allocate a tempid
(d/tempid part x)
```

`transact!` is **one transaction of the conn's Eve atom**: the new DB
is built inside the atom and becomes its `:db`; nothing is staged in
host memory and copied in. The tx-report is plain host data — `:db-before`
and `:db-after` are handles, `:tx-data` is a vector of host Datoms,
`:tempids` a host map.

Listeners are host-side, as upstream:

```clojure
(d/listen! conn key (fn [tx-report] …))
(d/unlisten! conn key)
(d/reset-conn! conn new-db)        ;; replace the conn's DB (a handle)
(d/reset-schema! conn new-schema)
```

Listeners fire after the transaction commits. `reset-conn!` and
`reset-schema!` replace state inside the atom; handles to the replaced
DB resolve only if they were kept (§3).

### Transaction functions (`:db/fn`)

A transaction function is a fn stored in the DB itself, under a
`:db/ident`, and invoked from tx-data by name:

```clojure
(d/transact! conn [[:inc-age "Petr"]])   ;; runs the fn stored at :inc-age
```

The fn receives the transaction's current DB value and its args, and
returns tx-data, which is transacted in its place. If the fn throws,
the whole transaction aborts and nothing commits.

A stored fn is **data** — it lives in the DB as a datom value, and a
DB is Eve-resident, so the stored value must be a legal Eve value: a
plain map. (A host fn is not an Eve value; see the rejection note
below.) There are two kinds, chosen per fn:

**The SCI kind** — the fn's source travels with the DB:

```clojure
(d/transact! conn [{:db/ident :inc-age
                    :db/fn    (d/db-fn [db name]
                                (if-some [ent (d/entity db [:name name])]
                                  [{:db/id (:db/id ent)
                                    :age   (inc (:age ent))}]
                                  (throw (ex-info "No entity" {}))))}])
```

`d/db-fn` is a macro: it sourcifies the body at compile time into the
stored map `{:db.fn/kind :sci, :db.fn/params [db name], :db.fn/code
"<body>"}`. (ClojureScript consumers: also require the macro —
`(:require-macros [eve.db.ds.core :as d])` in the ns form; this
build does not infer .cljc macros across namespaces.) At transact time the body is evaluated with
[SCI](https://github.com/babashka/sci) in a sandbox: clojure.core's
safe surface, plus the DB read API — `entity`, `entid`, `q`, `datoms`,
`seek-datoms`, `pull`, `pull-many` — callable unqualified, as
`d/<name>`, or as `eve.db.ds.core/<name>`. The evaluated fn is cached
host-side per stored map; the stored map itself stays pure data. The
explicit map form is `d/function` (Datomic's function shape):
`(d/function {:lang :clojure, :params [db name], :code "<body>"})`.

**The resolve kind** — the DB names a host fn:

```clojure
(defn inc-age [db name] …)               ; an ordinary host fn
(d/register-fn! 'my.app/inc-age inc-age) ; once, at app startup
(d/transact! conn [{:db/ident :inc-age
                    :db/fn    (d/fn-ref 'my.app/inc-age)}])
```

The stored map is `{:db.fn/kind :resolve, :db.fn/sym my.app/inc-age}`.
At transact time the symbol is looked up in the registry
(`d/register-fn!` / `d/unregister-fn!` — the portable path, both
hosts) or, on the JVM only, by `requiring-resolve`. An unresolvable
symbol aborts the transaction with `:eve.db.ds/db-fn-unresolved`.
The fn runs at host speed with the full host API.

| | SCI kind | resolve kind |
|---|---|---|
| Travels with the DB | **Yes** — the source is in the DB; any process that can read the DB can run the fn | No — the DB names a symbol; the receiving process must register (or be able to resolve) the same fn |
| Setup | None | `register-fn!` per fn (or a resolvable var, JVM) |
| Body can use | The sandboxed read API above + clojure.core's safe surface | Anything the host fn can do |
| Sandbox | Yes — a stored fn cannot reach host internals | No — it *is* host code; only install fns you trust |
| Invoke cost | One compile per distinct fn (~10–15 ms, cell-class), then interpreted: a tight arithmetic body measured ≈2–3× the host cost; through `transact!` the difference is under the transaction machinery's noise | Host speed, no compile |
| Browser | Works (SCI is a .cljc library; the browser u3 suite runs both kinds — tally in DB-FN-STATUS.md) | Works via the registry (no `requiring-resolve` in ClojureScript) |

**Provisional default: the SCI kind** — it is what the `d/db-fn`
macro builds, and it is the only kind that needs no out-of-band
setup: a DB carrying SCI fns is self-contained on both hosts. Pick
the resolve kind per fn when the body needs the full host API, when
the fn is on a hot path with a compute-heavy body, or when the code
must not live in the DB. The default is one line — the constructor
the `db-fn` macro expands to (`eve.db.ds.db-fn/sci-fn` in
`eve.db.ds.core`) — and stays provisional until it is picked for
good from measurements on a real venue; the numbers above are
cell-class (shared, throttled box) and labeled as such in
DB-FN-STATUS.md.

**Host fns are rejected, before commit.** Transacting a raw host fn
as a `:db/fn` value — or as any stored value — throws
`:eve.db.ds/db-fn-host-fn` (resp. `:eve.db.ds/fn-not-storable`)
*before* anything commits: the conn's DB is unchanged and the error
message names the supported forms. The same check runs on tx-data a
db fn returns. A host fn may still be passed at the call site,
`[:db.fn/call (fn [db] …) …]`, where nothing is stored.

## 6. Querying *(in progress — U2, U4)*

All of these take a **DB handle**, never a conn:

```clojure
(d/q query & inputs)                       ;; DB among the inputs
(d/datoms db index & components)           ;; => seq of host Datoms
(d/seek-datoms db index & components)
(d/rseek-datoms db index & components)     ;; reverse
(d/find-datom db index & components)       ;; => one host Datom or nil
(d/index-range db attr start end)
(d/entid db eid)
```

```clojure
(d/q '[:find ?n :where [?e :name ?n]] (d/db conn))
(d/datoms (d/db conn) :aevt :name)
```

Results are host data: query results are host collections of host
values; datom sequences contain host Datoms. The indexes themselves are
Eve sorted sets (`eve.db.ds.bset`, landed — U1) living inside the atom;
you never see them, you see what comes out of the reads above.

## 7. Entities and pull *(in progress — U4)*

```clojure
(d/entity db eid)                  ;; => entity (host data on access)
(d/entity-db entity)               ;; => the DB handle the entity reads from
(d/touch entity)                   ;; => plain host map of the entity
(d/pull db pattern eid)
(d/pull-many db pattern eids)
```

`entity` returns a lazy host-side entity view over the handle; touching
it or pulling from it resolves the handle at that moment, under the
same kept/current rules as any other read (§3). `pull` and `pull-many`
return plain host data, upstream-shaped.

## 8. Serialization and persistence *(in progress — U5)*

```clojure
(d/serializable db)                ;; => host value capturing the DB
(d/from-serializable x)            ;; => DB handle
d/data-readers                     ;; EDN tag readers for this library
```

`serializable` walks the DB inside the atom and brings its datoms out
as host data; `from-serializable` builds a DB by transacting datoms
into a fresh (private) conn and returns its handle.

Persistence needs no serialization step and no storage API:

```clojure
(def conn (d/create-conn schema {:persistent "path/to/dir"}))
```

The conn's Eve atom is backed by that directory; transactions are
durable as the atom's are, and reopening the same directory with a new
`create-conn` reads the same database back. The old port's `storage`
namespace is **deleted** — there is no storage object to create, pass
around or close. Round-trip and close/reopen equivalence are acceptance
items of U5/U6 and are *(in progress)* until they are measured green.

## 9. Datoms and miscellany

```clojure
(d/datom e a v)                    ;; host Datom (tx defaults)
(d/datom e a v tx added)
(d/datom? x)
(d/settings db)                    ;; the DB's settings map (host data)
(d/squuid)                         ;; a random UUID, time-ordered
(d/squuid-time-millis uuid)        ;; => millis embedded in a squuid
d/tx0                              ;; the first transaction number
```

`datom` constructs host Datoms — the same type that comes out of
`datoms`, `seek-datoms` and tx-reports. Datoms stored in a DB are Eve
values inside the atom; the host Datom is the delivery form at the API
boundary, in both directions.

## 10. The rules, in one place

1. The DB lives in the conn's Eve atom. Nowhere else.
2. `@conn` / `d/db` give you a handle, not the DB. Reads take handles;
   `d/q`, `d/pull`, `d/entity`, `d/datoms` never take a conn.
3. Old DBs stay referable only if kept: `d/keep!` / `d/release!`. An
   unkept, non-current handle throws `:eve.db.ds/db-not-kept`.
4. Every public function returns host data only; Datoms are host
   Datoms.
5. Conn-less values (`empty-db`, `init-db`, `db-with`, `with`,
   `filter`) ride on a private in-memory conn whose kept set holds the
   chain; their release policy is an open plan item (§8).
6. Persistence is `create-conn` with `:persistent dir`. There is no
   storage namespace.
