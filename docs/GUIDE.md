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

The release policy is settled (plan §8, ruled 2026-11-20): explicit
release is the rule, and chains auto-close their superseded
intermediates — the `with-open` strategy. Once a chain has advanced
two steps past a chain DB, that DB is released automatically, unless
it was pinned with `keep!` or it has more than one chain successor
(a fork is never auto-released). Pin any intermediate you still
need — from its handle alone, `(d/keep! db)` — and `release!`
removes one immediately. Reading a released handle throws
`:eve.db.ds/db-not-kept`. Explicit conns are unchanged: their DBs
stay kept until `release!`.

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
   chain. Release is explicit (`keep!` / `release!`), and a chain
   auto-closes a DB once it has advanced two steps past it — unless
   the DB was `keep!`'d or has more than one chain successor.
6. Persistence is `create-conn` with `:persistent dir`. There is no
   storage namespace.
