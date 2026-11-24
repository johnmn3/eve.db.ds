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

### Every Eve host

One table: each of this repo's perf shapes (rows) on each Eve host (columns), on one box, in one sitting. Every number is a time in milliseconds; the row names the unit (a total, or per swap / per transaction). Each cell is the median of 5 fresh processes unless it says `(n=…)`.

| shape (people-1k; unit in the row) | JVM | jolt | cljs (node) | browser | bb | nbb | lpy | glj | clje | cljr | jank |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| **writes** (`contract.ds-bench`, 1,000 people) | | | | | | | | | | | |
| batched build: 10 swaps of 100 people (ms total) | 866 (n=4) | 2,665 (n=4) | 3,343 (n=3) | no disk ¹ | 97,586 (n=1) | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| bulk load: 2 swaps of 2,000 datoms (ms total) | 889 (n=4) | 3,047 (n=4) | 3,476 (n=3) | no disk ¹ | 95,286 (n=1) | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| one person per swap (ms per swap, 500 swaps) | 1.83 (n=4) | 4.41 (n=4) | 4.99 (n=3) | no disk ¹ | 126 (n=1) | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| one datom per swap (ms per swap, 100 swaps) | 0.67 (n=4) | 1.43 (n=4) | 1.60 (n=3) | no disk ¹ | 26.5 (n=1) | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| retract one entity per swap (ms per swap, 500 swaps) | 2.12 (n=4) | 4.98 (n=4) | 5.89 (n=3) | no disk ¹ | 160 (n=1) | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| **reads** (`contract.ds-runtime`, people-1k) | | | | | | | | | | | |
| open a persistent conn (ms) | 99.3 (n=4) | 55.6 (n=3) | 21.9 (n=3) | no disk ¹ | not measured yet ⁶ | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| build people-1k: 10 transact! of 100 (ms total) | 1,171 (n=4) | 2,530 (n=3) | 3,632 (n=3) | no disk ¹ | not measured yet ⁶ | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| find-datom :avet x1000 (ms total) | 132 (n=4) | 274 (n=3) | 307 (n=3) | no disk ¹ | not measured yet ⁶ | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| pull with a :friend join x1000 (ms total) | 106 (n=4) | 227 (n=3) | 290 (n=3) | no disk ¹ | not measured yet ⁶ | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| d/q, the 7 key-set queries (ms total) | 104 (n=4) | 241 (n=3) | 449 (n=3) | no disk ¹ | not measured yet ⁶ | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| datoms :eavt full scan, 4,000 datoms (ms) | 41.9 (n=4) | 152 (n=3) | 292 (n=3) | no disk ¹ | not measured yet ⁶ | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |
| one-datom transact! x100 (ms per tx) | 1.50 (n=4) | 2.42 (n=3) | 3.31 (n=3) | no disk ¹ | not measured yet ⁶ | ds doesn't load ² | not measured yet ⁶ | ds doesn't load ³ | Eve doesn't load ⁴ | Eve doesn't compile ⁵ | not measured yet ⁶ |

¹ browser: not run. These shapes open a `:persistent` (disk) conn and a browser has no disk domain at this Eve pin: `eve.process` reaches Node's `fs`, and Eve's own browser `disk-swap-counter` fails the same way (`require is not defined`, perf-all at `507235b` on this box). The OPFS disk tier that would give it one is being scoped separately.

² nbb: `Unable to resolve symbol: util/raise` at `src/eve/db/ds/db_fn.cljc:118`. The `eve.db.ds.util` macros are defined for `:default` readers only and nbb reads `:cljs`; with that one gate opened in a scratch copy, the next wall is `Unable to resolve symbol: defn+` at `db.cljc:386`.

³ glj: `eve/uuid.cljc:46:50: invalid regex: error parsing regexp: invalid or unsupported Perl syntax: (?=` (Go's regexp has no lookahead), while loading `eve.db.ds.util`.

⁴ clje: `adapters/flat-io/portable/src/eve/flat_io.cljc:304:13: Unable to resolve symbol: long in this context`. With the uncommitted clje flat-io draft: `Could not dereference eve.flat-io/wBytes. There is no Erlang function to back it up.`

⁵ cljr (.NET 8.0.428, Clojure.Main 1.12.6): `Syntax error (InvalidOperationException) compiling at (eve/flat_io.cljc:308:23). Unable to resolve symbol: u8 in this context`.

⁶ lpy, jank, and bb's reads: the code runs (size-20 smokes: lpy about 1 s per swap; jank interpreted, rows print, then a segfault at exit), but a full lpy process takes about an hour, and the box's disk fell under 2 GB free from other workloads at 11:42 PM ET, which stopped the run before these cells came up. They land in a follow-up.

How it was measured (2026-11-23, 11:08 PM ET onward): box J (8-core Intel Xeon VM, 15 GB RAM, Linux 6.12), shared with other benchmark workers; every process held the box's benchmark lock, so no two benchmarks overlapped, and load1 ran 2.1–8.8 over the cells. Pins: this repo at the commit that added the runner (`scripts/ds_matrix.sh`, on top of `d4325c6`), Eve main `441ae29`. Hosts: JVM = OpenJDK 21.0.13 + Clojure (`harness/run-jvm.sh`, AOT); jolt v0.8.28 from source; cljs = shadow-cljs 3.3.6 `:simple` on Node v24.22.1 (not `:advanced`: under `:advanced` the eve `Datom` fields the query engine binds by name are renamed and query values come back nil); bb v1.13.226 (static, so Eve's native CAS falls back to the lock-only region); lpy = Basilisp 0.5.1 on Python 3.13.5; jank = the 2026-11-20 PPA build, interpreted (`jank run-main`, JIT), and it segfaults at exit after printing its rows.

What runs: the shape code is this repo's, unchanged. Writes are `contract.ds-bench/eve-shapes` (the five shapes of the Eve-vs-stock table below): 500 people, then 1,000 in the same process; the table is the 1,000 pass, so the 500 pass is its warm-up. Every shape gets its own fresh persistent (journaled, on-disk) conn. Reads are `contract.ds-runtime` (people-1k): a heap conn is built and its key set checked against the persistent one (`:keys-exact`, true on every run), then the timed reads run over the persistent conn. The JVM runs `contract.ds-bench` / `contract.ds-runtime` directly; every other host enters `contract.ds-matrix`, which calls the same functions after the host's start-up.

Where a cell says `(n=…)`, the box's disk ran short (other workloads) before its fifth process. The contention shootout's solo per-op times are not in the table: its loop times cheap ops with a millisecond clock, and each host's DB grows to a different size in the same 30 s, so its cells don't compare across hosts.

Reproduce one cell (one fresh process; take the median of 5):

```bash
EVE_ROOT=<eve checkout> scripts/ds_matrix.sh <jvm|jolt|cljs|bb|lpy|jank|nbb|glj|clje|cljr> bench   <fresh-dir> 500 1000
EVE_ROOT=<eve checkout> scripts/ds_matrix.sh <host>                                    runtime <fresh-dir>
# cljs: build once with  scripts/ds_matrix.sh cljs cljs-build out.js  then set CLJS_JS=out.js
```

### Eve vs stock DataScript (JVM)

Measured on J on 2026-11-23, at this repo's main (`f35f5d0`) against Eve
`507235b`. The bench is `scripts/ds_bench.sh`: five write shapes at 500
and 1000 people; each number is the median of 3 fresh-process reps
(load1 1.84→2.00). The Eve arm is a **persistent** conn: every write is
journaled to disk. The stock arm is stock DataScript 1.8.1, which is
**in-memory only**.

| Shape | Eve | Stock | |
|---|---|---|---|
| **500 people** | | | |
| batched build, 100-person swaps (total ms) | 1223.27 | 62.32 | 20× |
| bulk-load, 2,000-datom swaps (total ms) | 607.42 | 14.47 | 42× |
| one person per swap (ms per swap) | 2.52 | 0.04 | 57× |
| one datom per swap (ms per swap) | 0.91 | 0.01 | 67× |
| retract one entity per swap (ms per swap) | 2.06 | 0.05 | 41× |
| **1000 people** | | | |
| batched build, 100-person swaps (total ms) | 933.14 | 23.33 | 40× |
| bulk-load, 2,000-datom swaps (total ms) | 932.74 | 21.00 | 44× |
| one person per swap (ms per swap) | 1.92 | 0.02 | 78× |
| one datom per swap (ms per swap) | 0.70 | 0.01 | 109× |
| retract one entity per swap (ms per swap) | 2.22 | 0.02 | 110× |

Read the table with the asymmetry in plain view. The Eve numbers buy
durability on every write: the journal, the slab, the disk. The stock
numbers buy none of it, because stock DataScript has no durable mode to
pay for. The gap is real, and it is not dressed up here: on these write
shapes, a durable Eve database is 20 to 110 times slower than an
in-memory database that pays nothing for durability. That is the honest
picture at this pin.

Cold start is a known cost. The JVM one-datum cold total — process
start through one datum committed to a disk-backed database — is
**1,591 ms**, the median of 5 fresh-process runs on J
(`harness/shootout.sh` wrapping `harness/run-jvm.sh contract.one`).
The steady runs sit in a 1,474 to 1,595 ms band; the first run paid a
one-time compile (8,000 ms) that the median excludes by construction.
load1 2.00→1.92.

Scale was not re-run at this pin: the Logseq-shaped driver that produced
the 2026-11-20 figures (500 pages, about 95,700 datoms, about 133 bytes
per datom at 250 pages and at 500 pages) is not in this repository
(only a smaller stand-in, `test/drivers/cliff/build_probe.clj`, is).
Those figures are not carried forward as current.

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

**The host kind (the default)** — the DB names a host fn, and
`d/db-fn` builds one from a plain fn form:

```clojure
(d/transact! conn [{:db/ident :inc-age
                    :db/fn    (d/db-fn [db name]
                                (if-some [ent (d/entity db [:name name])]
                                  [{:db/id (:db/id ent)
                                    :age   (inc (:age ent))}]
                                  (throw (ex-info "No entity" {}))))}])

(d/transact! conn [[:inc-age "Petr"]])   ;; runs the fn stored at :inc-age
```

`d/db-fn` is a macro: its expansion defines the body as a var in your
namespace, registers it, and evaluates to the stored map
`{:db.fn/kind :resolve, :db.fn/sym <your-ns>/db-fn-<hash>}`. The
generated name is content-addressed — derived from your namespace and
a hash of the form — so re-evaluating the same form (a reload, a REPL
re-eval) re-interns and re-registers the same name. The body is
ordinary code in your namespace: the full host API, at host speed, no
sandbox.

To name a fn yourself instead, register it and store a reference:

```clojure
(defn inc-age [db name] …)                 ; an ordinary host fn
(d/register-fn! 'my.app/inc-age inc-age)   ; once, at app startup
(d/transact! conn [{:db/ident :inc-age
                    :db/fn    (d/fn-ref 'my.app/inc-age)}])
```

Either way the stored map is `{:db.fn/kind :resolve, :db.fn/sym
my.app/inc-age}`. At transact time the symbol is looked up in the
registry (`d/register-fn!` / `d/unregister-fn!` — the portable path,
both hosts) or, on the JVM only, by `requiring-resolve`. An
unresolvable symbol aborts the transaction with
`:eve.db.ds/db-fn-unresolved`.

**The SCI kind (opt-in)** — the fn's source travels with the DB:

```clojure
(d/transact! conn [{:db/ident :inc-age
                    :db/fn    (d/sci-fn [db name]
                                (if-some [ent (d/entity db [:name name])]
                                  [{:db/id (:db/id ent)
                                    :age   (inc (:age ent))}]
                                  (throw (ex-info "No entity" {}))))}])
```

`d/sci-fn` is a macro: it sourcifies the body at compile time into the
stored map `{:db.fn/kind :sci, :db.fn/params [db name], :db.fn/code
"<body>"}`. At transact time the body is evaluated with SCI in a
sandbox: clojure.core's safe surface, plus the DB read API — `entity`,
`entid`, `q`, `datoms`, `seek-datoms`, `pull`, `pull-many` — callable
unqualified, as `d/<name>`, or as `eve.db.ds.core/<name>`. The explicit
map form is `d/function`, Datomic's function shape: `(d/function
{:lang :clojure, :params [db name], :code "<body>"})`.

| | host kind | SCI kind |
|---|---|---|
| Travels with the DB | No — the receiving process must register (or be able to resolve) the same fn | Yes — the source is in the DB; any process that can read the DB can run the fn |
| Setup | `register-fn!` per fn (or a resolvable var, JVM); `d/db-fn` registers its own var for you | None |
| Body can use | Anything the host fn can do | The sandboxed read API above + clojure.core's safe surface |
| Sandbox | No — it is host code; only install fns you trust | Yes — a stored fn cannot reach host internals |
| Invoke cost | Host speed, no compile | One compile per distinct fn (~10–15 ms, measured on a shared cell), then interpreted — about 2–3× host cost for a tight body; through `transact!`, under the transaction noise |
| Browser | Works via the registry (no `requiring-resolve` in ClojureScript) | Works — SCI is a `.cljc` library |

**Default: the host kind.** It is what `d/db-fn` — the constructor a
user reaches for first — builds: host speed, the full API, no sandbox.
Opt into the SCI kind per fn when the fn's source must live in and
travel with the DB, so the DB stays self-contained in any process that
can read it. The invoke costs above are cell-class and labeled as such
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
- **JVM cold start is about 1.6 s** (see Performance).
- **The library does not load on Babashka.** Loading the DB namespace
  dies in the sorted-set code with an SCI error, `Protocol not found:
  clojure.lang.IChunk` — the bset references a JVM host interface that
  bb's SCI cannot resolve. Measured: every shootout run fails at load.
- **Eve does not load on nbb at the pinned Eve.** Eve's own
  `type_readers` namespace fails (`reader/register-tag-parser!`
  unresolvable) before any library code loads. (Eve seat.)
- **The library does not load on Basilisp.** Importing
  `eve.db.ds.core` fails after a long load, about 95 s per attempt.
  The cause is not yet isolated.

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
