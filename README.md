# eve.db.ds

DataScript 1.8.1 (by Nikita Prokopov), rebuilt so the database lives in a
real Eve atom.

**Status: ground-up rewrite in progress.** This branch
(`lane-m/ds-in-eve-rewrite`) replaces the previously published port. The
published version claimed the DB lived in an Eve atom while the connection
in fact wrapped a host atom. That claim was false, and this rewrite exists
to make it true. Until the acceptance unit (U6) is green on the JVM and in
the browser, treat everything here as in progress: sections of the guide
whose implementing unit has not landed are marked *(in progress)* rather
than described as working.

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
  separately. *(in progress — U5)*

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

*(in progress — U3: conn and handles; U2: the DB types underneath.)*

## Conn-less DB values

`empty-db`, `init-db`, `db-with`, `with` and `filter` work without an
explicit conn, as upstream. Underneath, each `empty-db`/`init-db` makes a
private in-memory conn, and `db-with` adds the new DB to that conn's kept
set and returns a handle to it — so the handle stays readable exactly as
long as the kept set holds it.

The release policy (plan §8, ruled 2026-11-20): explicit release is the
rule, and chains auto-close their superseded intermediates — the
`with-open` strategy. Once a chain has advanced two steps past a chain
DB, that DB is released automatically, unless it was pinned with
`keep!` or it has more than one chain successor (a fork is never
auto-released). `keep!` any intermediate you still need — a chain DB
can be pinned from its handle alone, `(d/keep! db)` — and `release!`
removes one immediately. Reading a released handle throws
`:eve.db.ds/db-not-kept`. Explicit conns are unchanged: their DBs stay
kept until `release!`.

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

- **Landed:** the upstream source baseline, the frozen trap list and the
  API list (U0); the persistent sorted set that backs the indexes,
  `eve.db.ds.bset`, green on the JVM (U1).
- **In progress:** the DB/Datom types as Eve types (U2); the conn and
  handle layer (U3); query, pull, entity and datafy (U4); serialization
  and persistence (U5); the acceptance run on both hosts (U6).
- Eve itself is a dependency, pinned in `deps.edn` to the public Eve
  release line (`SeniorCareMarket/eve`, `release/eve-0.9.3.3`).

## License

EPL-1.0 (upstream DataScript); notices in LICENSE and
docs/NOTICE-eve-db-ds-epl-1.0.txt. Provenance: docs/PROVENANCE-DS.md.
