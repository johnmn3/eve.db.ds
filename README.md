# eve.db.ds

DataScript 1.8.1 (Nikita Prokopov), rebuilt so the database lives in an
Eve atom.

## Status

Alpha. The rewrite is landed and acceptance-measured.

The acceptance battery ran the full suite on the JVM and in a real
browser at the pinned Eve, green except the rows named under Known
limitations, each of which traces to a filed defect with an owner.

Eve is a dependency, not a vendored copy. `deps.edn` pins
`SeniorCareMarket/eve` main at `abba331`.

## The model

- **The connection holds an Eve atom, and the DB lives inside it.**
  A conn is a host object holding an Eve atom and the listener registry.
  The atom's value is a plain map, `{:db DB :kept {tx DB}}`. There is no
  host atom standing in for the database, and no second copy of the DB
  outside the Eve atom.
- **`@conn` and `(d/db conn)` return a handle, not the DB.** A handle is
  a small host value, `{:atom a :tx n}`: the conn's atom and the
  transaction number the handle refers to. Every read through a handle
  resolves it against the atom at read time.
- **`d/q`, `d/pull`, `d/entity`, `d/datoms` and friends take DB values
  (handles), not conns** — the same call shapes as upstream DataScript,
  with the handle in the position upstream puts the DB.
- **Every public function returns host data only.** No Eve types appear
  in results. Datoms come out as ordinary host Datoms, the upstream type.

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
;; => #{["Ivan"] ["Petr"]}
(d/release! conn before)          ;; done with it
```

This example runs as printed, outputs included.

## Running it

The API is one `.cljc` namespace, `eve.db.ds.core`, on every host.
There is no host-specific API surface to learn per runtime.

**JVM (clojure CLI).** From a checkout of this repo, the quick start
above runs as-is:

```bash
git clone https://github.com/johnmn3/eve.db.ds
cd eve.db.ds
clojure -M -e "(require '[eve.db.ds.core :as d]) …"
```

As a dependency, the same git machinery this repo uses for Eve:

```clojure
;; deps.edn
{:deps {johnmn3/eve.db.ds {:git/url "https://github.com/johnmn3/eve.db.ds"
                            :git/sha "b1c739caab3c076df18193fa71a03b54193d9f00"}}}
```

**ClojureScript in the browser.** The same namespace, the same calls,
compiled with shadow-cljs. This repo's own build is the working
reference: the `:ds-browser` build in `shadow-cljs.edn`, driven by
`scripts/test-browser.sh`, compiles `src` for the browser and runs the
acceptance suites in a real browser. A consumer project puts this
library's `src` on its shadow-cljs source paths and requires
`eve.db.ds.core` from `.cljs` or `.cljc` code — nothing else changes.

**Babashka.** The sources are `.cljc` and the API is the same under
`bb`:

```bash
bb -cp src:<eve>/src -e "(require '[eve.db.ds.core :as d]) …"
```

Measured versus by-construction, stated plainly: the JVM and the
browser are the acceptance hosts — the full battery runs green on both,
except the rows under Known limitations. The Babashka example is
by-construction (the same `.cljc` sources, no bb-specific code in this
library) and is not acceptance-measured; this repo carries no `bb.edn`
yet.

## Performance

Measured on J on 2026-11-20, at this repo's main against the pinned Eve.
The bench runs five write shapes at 500 and 1000 people; each number is
the median of 3 fresh-process reps. The Eve arm is a **persistent**
conn: every write is journaled to disk. The stock arm is stock
DataScript 1.8.1, which is **in-memory only**.

| Shape | Eve | Stock | |
|---|---|---|---|
| **500 people** | | | |
| batched build, 100-person swaps (total ms) | 3360.63 | 222.33 | 15× |
| bulk-load, 2,000-datom swaps (total ms) | 1588.80 | 48.89 | 32× |
| one person per swap (ms per swap) | 4.22 | 0.22 | 19× |
| one datom per swap (ms per swap) | 0.70 | 0.05 | 14× |
| retract one entity per swap (ms per swap) | 3.04 | 0.22 | 14× |
| **1000 people** | | | |
| batched build, 100-person swaps (total ms) | 1718.96 | 98.05 | 18× |
| bulk-load, 2,000-datom swaps (total ms) | 1277.04 | 49.68 | 26× |
| one person per swap (ms per swap) | 1.95 | 0.09 | 21× |
| one datom per swap (ms per swap) | 0.61 | 0.03 | 22× |
| retract one entity per swap (ms per swap) | 2.00 | 0.08 | 25× |

Read the table with the asymmetry in plain view. The Eve numbers buy
durability on every write: the journal, the slab, the disk. The stock
numbers buy none of it, because stock DataScript has no durable mode to
pay for. The gap is real, and it is not dressed up here: on these write
shapes, a durable Eve database is 14 to 32 times slower than an
in-memory database that pays nothing for durability. That is the honest
picture at this pin.

Cold start is a known cost. The JVM one-datum cold total — process start
to one datom written and read back — ran over the harness's 10,000 ms
cap on a calm box and is recorded as **10s+**. The harness caps the
measurement, so no raw median exists.

Scale: a Logseq-shaped build of 500 pages, about 95,700 datoms,
completes. The marginal cost of the database in Eve's slab is flat:
about 133 bytes per datom at 250 pages and at 500 pages. The database
grows linearly, at a constant rate, with no cliff.

## Retention

Retention is reachability inside the atom. Nothing pins old DB values
for you. The rule is explicit release:

```clojure
(d/keep! conn db)      ;; the DB stays readable through its handle
(d/release! conn db)   ;; let it go
```

Reading a handle whose DB is neither current nor kept throws
`:eve.db.ds/db-not-kept`, naming the handle's transaction number and the
conn's current one. An atom can do the job of a garbage collector, as
long as the things that must refer to a value also live in the atom.
That is what `:kept` is.

Conn-less DB values work without an explicit conn, as upstream:
`empty-db`, `init-db`, `db-with`, `with`, `filter`. Underneath, each
`empty-db`/`init-db` makes a private in-memory conn, and `db-with` adds
the new DB to that conn's kept set and returns a handle to it.

Chains of `db-with` auto-close their superseded intermediates — the
`with-open` strategy. Once a chain has advanced two steps past a chain
DB, that DB is released automatically:

```clojure
(def db1 (d/db-with (d/empty-db) [{:db/id 1 :name "Ivan"}]))
(def db2 (d/db-with db1 [{:db/id 2 :name "Petr"}]))
(def db3 (d/db-with db2 [{:db/id 3 :name "Oleg"}]))

(d/q '[:find ?n :where [?e :name ?n]] db2)
;; => #{["Ivan"] ["Petr"]}        — the head's parent still reads
(d/q '[:find ?n :where [?e :name ?n]] db1)
;; throws :eve.db.ds/db-not-kept  — released, two steps back
```

The lag is deliberate: a DB with more than one chain successor — a
fork — is never auto-released, and neither is a DB pinned with `keep!`.
A chain DB can be pinned from its handle alone, `(d/keep! db)`, and
`release!` removes one immediately, ahead of the schedule. Explicit
conns are unchanged by all of this: their DBs stay kept until
`release!`.

## Persistence

Persistence is the atom's, not a storage layer. Create a conn with
`:persistent dir` and its Eve atom is backed by that directory:

```clojure
(def conn (d/create-conn schema {:persistent "path/to/dir"}))
```

The old port's `storage` namespace is gone. There is nothing to
configure separately. Measured at acceptance: reopen-by-adopt and
cross-process round-trips are digest-identical in both directions, and
an in-memory conn persists nothing. One caveat, under Known limitations:
`close-conn!` on a `:persistent` conn throws inside Eve's close path at
the pinned Eve.

## Transaction functions (`:db/fn`)

A transaction function is a fn stored in the DB itself, under a
`:db/ident`, and invoked from tx-data by name. The fn receives the
transaction's current DB value and its args, and returns tx-data, which
is transacted in its place. If the fn throws, the whole transaction
aborts and nothing commits.

A stored fn is **data**: it lives in the DB as a datom value, and the DB
is Eve-resident, so the stored value must be a legal Eve value — a plain
map. There are two kinds, chosen per fn.

**The SCI kind** — the fn's source travels with the DB:

```clojure
(d/transact! conn [{:db/ident :inc-age
                    :db/fn    (d/db-fn [db name]
                                (if-some [ent (d/entity db [:name name])]
                                  [{:db/id (:db/id ent)
                                    :age   (inc (:age ent))}]
                                  (throw (ex-info "No entity" {}))))}])

(d/transact! conn [[:inc-age "Petr"]])   ;; runs the fn stored at :inc-age
```

`d/db-fn` is a macro: it sourcifies the body at compile time into the
stored map `{:db.fn/kind :sci, :db.fn/params [db name], :db.fn/code
"<body>"}`. At transact time the body is evaluated with SCI in a
sandbox: clojure.core's safe surface, plus the DB read API — `entity`,
`entid`, `q`, `datoms`, `seek-datoms`, `pull`, `pull-many` — callable
unqualified, as `d/<name>`, or as `eve.db.ds.core/<name>`. The explicit
map form is `d/function`, Datomic's function shape: `(d/function
{:lang :clojure, :params [db name], :code "<body>"})`.

**The resolve kind** — the DB names a host fn:

```clojure
(defn inc-age [db name] …)                 ; an ordinary host fn
(d/register-fn! 'my.app/inc-age inc-age)   ; once, at app startup
(d/transact! conn [{:db/ident :inc-age
                    :db/fn    (d/fn-ref 'my.app/inc-age)}])
```

The stored map is `{:db.fn/kind :resolve, :db.fn/sym my.app/inc-age}`.
At transact time the symbol is looked up in the registry
(`d/register-fn!` / `d/unregister-fn!` — the portable path, both hosts)
or, on the JVM only, by `requiring-resolve`. An unresolvable symbol
aborts the transaction with `:eve.db.ds/db-fn-unresolved`. The fn runs
at host speed, with the full host API.

| | SCI kind | resolve kind |
|---|---|---|
| Travels with the DB | Yes — the source is in the DB; any process that can read the DB can run the fn | No — the receiving process must register (or be able to resolve) the same fn |
| Setup | None | `register-fn!` per fn (or a resolvable var, JVM) |
| Body can use | The sandboxed read API above + clojure.core's safe surface | Anything the host fn can do |
| Sandbox | Yes — a stored fn cannot reach host internals | No — it is host code; only install fns you trust |
| Invoke cost | One compile per distinct fn (~10–15 ms, measured on a shared cell), then interpreted — about 2–3× host cost for a tight body; through `transact!`, under the transaction noise | Host speed, no compile |
| Browser | Works — SCI is a `.cljc` library | Works via the registry (no `requiring-resolve` in ClojureScript) |

**Provisional default: the SCI kind.** It is what the `d/db-fn` macro
builds, and it is the only kind that needs no out-of-band setup: a DB
carrying SCI fns is self-contained on both hosts. Pick the resolve kind
per fn when the body needs the full host API, when the fn is on a hot
path with a compute-heavy body, or when the code must not live in the
DB. The default is one line — the constructor the macro expands to —
and stays provisional until it is picked for good from measurements on
a real venue; the invoke costs above are cell-class and labeled as such
where they are recorded.

**Host fns are rejected, before commit.** Transacting a raw host fn as
a `:db/fn` value — or as any stored value — throws
`:eve.db.ds/db-fn-host-fn` (resp. `:eve.db.ds/fn-not-storable`) before
anything commits: the conn's DB is unchanged, and the error names the
supported forms. A host fn may still be passed at the call site,
`[:db.fn/call (fn [db] …) …]`, where nothing is stored.

## Known limitations

Each of these is measured, filed, and owned. None is hidden in a
footnote of the docs.

- **`d/filter` is not constructible at the pinned Eve.** FilteredDB's
  host-predicate field reads null on every construction path — an Eve
  defect (GAP-6, Eve seat). `d/filter` throws a named error rather than
  building a broken DB, and the acceptance rows that need a filtered DB
  carry the same error: 11 rows per host.
- **`close-conn!` on a `:persistent` conn throws** inside Eve's close
  path (GAP-U5-1, Eve seat). The data is not corrupted. Reopen-by-adopt
  and process exit are the working closes.
- **Writing against a DB value captured inside a bare read region
  fails** in the sorted-set layer (Eve seat, F5 family). Reads, and
  writing through the public conn and handle paths, are unaffected.
- **In the browser, the serialize suite fails deterministically at the
  pinned Eve:** Eve's ClojureScript delivery cannot materialize Datom
  values out of a read region. The JVM serialize suite is green. (Eve
  seat.)
- **JVM cold start is over 10,000 ms** (see Performance).

## API

The public surface is upstream DataScript 1.8.1's `datascript.core`,
under `eve.db.ds.core` — 44 vars — plus the additive vars of this
rewrite: `keep!`, `release!`, `close-conn!`, and the transaction-fn
constructors `db-fn`, `fn-ref`, `register-fn!`, `unregister-fn!`. The
full guide, with the handle semantics spelled out per function, is
[docs/GUIDE.md](docs/GUIDE.md).

| Group | Vars |
|---|---|
| Connections | `create-conn`, `conn?`, `conn-from-db`, `conn-from-datoms`, `close-conn!` *(additive)* |
| Transacting | `transact!`, `transact`, `transact-async`, `listen!`, `unlisten!`, `reset-conn!`, `reset-schema!`, `resolve-tempid`, `tempid` |
| DB values | `db`, `db?`, `keep!` *(additive)*, `release!` *(additive)*, `empty-db`, `init-db`, `db-with`, `with`, `filter`, `is-filtered`, `with-schema`, `schema` |
| Transaction functions | `db-fn` *(macro, additive)*, `fn-ref` *(additive)*, `function`, `register-fn!` *(additive)*, `unregister-fn!` *(additive)* |
| Query & indexes | `q`, `datoms`, `seek-datoms`, `rseek-datoms`, `find-datom`, `index-range`, `entid` |
| Entities & pull | `entity`, `entity-db`, `touch`, `pull`, `pull-many` |
| Datoms & misc | `datom`, `datom?`, `serializable`, `from-serializable`, `data-readers`, `settings`, `squuid`, `squuid-time-millis`, `tx0` |

## License

EPL-1.0 (upstream DataScript); notices in LICENSE and
docs/NOTICE-eve-db-ds-epl-1.0.txt. Provenance: docs/PROVENANCE-DS.md.
