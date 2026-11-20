# eve.db.ds

DataScript 1.8.1 (by Nikita Prokopov), rebuilt so the database lives in a
real Eve atom.

**Status: the rewrite is landed and acceptance-measured.** The
previously published port claimed the DB lived in an Eve atom while the
connection in fact wrapped a host atom; that claim was false, and this
rewrite makes it true. The acceptance unit (U6, 2026-11-20) ran the
full battery on the JVM and in a real browser at the pinned Eve
(below): every suite green on both hosts except the rows named under
"What is landed, what is not" — each traces to a filed gap with an
owner. Sections still marked *(in progress)* are exactly the open
items listed there, nothing else.

## The model, exactly

- **The connection holds a real Eve atom, and the DB lives inside it.**
  A `conn` is a host object holding an Eve atom (plus the listener
  registry). The atom's value is a plain map `{:db DB :kept {…}}`. There
  is no host atom standing in for the database, and no second copy of the
  DB outside the Eve atom.
- **`@conn` and `(d/db conn)` return a handle, not the DB.** A handle is
  a small host value `{:atom a :tx n}` — a pointer to the conn's atom and
  the transaction number the handle refers to. Every read through a
  handle resolves it against the atom at read time.
- **`d/q`, `d/pull`, `d/entity`, `d/datoms` and friends take DB values
  (handles), not conns** — the same call shapes as upstream DataScript,
  with the handle in the position upstream puts the DB.
- **Retention is reachability inside the atom.** Nothing pins old DB
  values for you. If you want an old DB to stay referable, keep it:

  ```clojure
  (d/keep! conn db)      ;; the DB stays readable through its handle
  (d/release! conn db)   ;; let it go
  ```

  Reading a handle whose DB was neither kept nor is current throws
  `:eve.db.ds/db-not-kept`, naming the handle's transaction number and
  the conn's current one. An atom can do the job of a garbage collector,
  as long as the things that must refer to a value also live in the atom
  — that is what `:kept` is.
- **Every public function returns host data only.** No Eve types appear
  in results. Datoms come out as ordinary host Datoms, the upstream type.
- **Persistence is the atom's, not a storage layer.** Create a conn with
  `:persistent dir` and its Eve atom is backed by that directory. The old
  port's `storage` namespace is gone; there is nothing to configure
  separately. Measured at acceptance (U5/U6): reopen-by-adopt and
  cross-process round-trips are digest-identical both directions; an
  in-memory conn persists nothing. One caveat, filed as GAP-U5-1:
  `close-conn!` on a `:persistent` conn throws inside Eve's close path
  at the pinned Eve — the data is not corrupted, and process exit or
  adopt-reopen are the measured working closes.

## Quick start

```clojure
(require '[eve.db.ds.core :as d])

(def conn (d/create-conn {:name {:db/unique :db.unique/identity}}))

(d/transact! conn [{:db/id 1 :name "Ivan"} {:db/id 2 :name "Petr"}])

(d/q '[:find ?n :where [?e :name ?n]] (d/db conn))
;; => #{["Ivan"] ["Petr"]}

(def before (d/db conn))          ;; a handle to the current DB
(d/keep! conn before)             ;; keep it referable…
(d/transact! conn [{:db/id 3 :name "Oleg"}])
(d/q '[:find ?n :where [?e :name ?n]] before)   ;; …and it still reads
(d/release! conn before)          ;; done with it
```

## Conn-less DB values

`empty-db`, `init-db`, `db-with`, `with` and `filter` work without an
explicit conn, as upstream. Underneath, each `empty-db`/`init-db` makes a
private in-memory conn, and `db-with` adds the new DB to that conn's kept
set and returns a handle to it — so the handle stays readable exactly as
long as the kept set holds it. Whether conn-less values are released
explicitly by the caller or reclaimed by the host is an open item in the
plan (§8); the mechanism above is the contract, the release policy is
*(in progress — awaiting the plan's §8 ruling)*.

## API

The public surface is upstream DataScript 1.8.1's `datascript.core`,
under `eve.db.ds.core` — 44 vars — plus three additive vars of this
rewrite: `keep!`, `release!`, `close-conn!`. The full guide, with the
handle semantics spelled out per function, is [docs/GUIDE.md](docs/GUIDE.md).

| Group | Vars |
|---|---|
| Connections | `create-conn`, `conn?`, `conn-from-db`, `conn-from-datoms`, `close-conn!` *(additive)* |
| Transacting | `transact!`, `transact`, `transact-async`, `listen!`, `unlisten!`, `reset-conn!`, `reset-schema!`, `resolve-tempid`, `tempid` |
| DB values | `db`, `db?`, `keep!` *(additive)*, `release!` *(additive)*, `empty-db`, `init-db`, `db-with`, `with`, `filter`, `is-filtered`, `with-schema`, `schema` |
| Query & indexes | `q`, `datoms`, `seek-datoms`, `rseek-datoms`, `find-datom`, `index-range`, `entid` |
| Entities & pull | `entity`, `entity-db`, `touch`, `pull`, `pull-many` |
| Datoms & misc | `datom`, `datom?`, `serializable`, `from-serializable`, `data-readers`, `settings`, `squuid`, `squuid-time-millis`, `tx0` |

## What is landed, what is not

- **Landed:** everything the plan names — the upstream source baseline,
  trap list and API list (U0); the persistent sorted set backing the
  indexes (U1); the DB/Datom types as Eve types (U2); the conn and
  handle layer (U3); query, pull, entity and datafy (U4); serialization
  and persistence (U5); docs (U7); and the U6 acceptance battery: JVM
  and browser suites at parity, zero assertion failures, the public-API
  surface test (44 vars + `keep!`/`release!`/`close-conn!`) proving
  host-data-only returns, and the cliff instruments re-measured at the
  pin (the swap leak is flat; the build probe's slab cost is a constant
  ~134 B/datom with no deceleration separable from box load).
- **Open, each with an owner:** `filter` is not constructible at the
  pinned Eve — FilteredDB's `^:host` pred field reads null on every
  construction path (GAP-6, Eve seat; `d/filter` throws a named error
  rather than building a broken DB, and the query/pull rows that need
  a filtered DB carry the same named error). `close-conn!` on a
  `:persistent` conn throws in Eve's close path (GAP-U5-1, Eve seat).
  Stored `:db/fn` values are not Eve values and cannot be stored
  (GAP-U3-5 — a ruling is owed on whether the rewrite drops them).
  The conn-less-values release policy awaits the plan's §8 ruling.
  Writing against a DB value captured inside a read region fails in
  the set layer (filed with U6, Eve seat, F5 family). In the browser,
  the serialize suite's synthetic-digest comparison is intermittent
  (a delivered nested value can read empty mid-comparison; JVM is
  deterministic green) — filed with U6's probe evidence.
- Eve itself is a dependency, pinned in `deps.edn` to
  `SeniorCareMarket/eve` main at `e21be6b` (0.9.3.4 plus the won-swap
  freeing and JVM byte-array typing commits).

## License

EPL-1.0 (upstream DataScript); notices in LICENSE and
docs/NOTICE-eve-db-ds-epl-1.0.txt. Provenance: docs/PROVENANCE-DS.md.
