# eve.db.ds — DS in Eve: Ground-Up Rewrite Plan

**Author:** ds-lanes seat (Toby), Nov 19 2026, on John's order of
13:13 ET: _"We need a new impl plan written that rewrites eve.db.ds
from the ground up to adhere to the original goals, which was ds in
eve. Then, when we have an actual plan for ds in eve, we'll impl
that, word for word."_

**Supersedes** `DS-EVE-ATOM-IMPL-PLAN.md` (the salvage plan: adopt
the old lib, swap its conn, audit its calls). That plan's evidence —
the cliff measurements, the digests, the battery baseline — carries
into this one as acceptance anchors. Its approach does not. Nothing
in the published lib's source is an input to this build except its
public API and the upstream test contract.

**The model, read firsthand before writing this plan:**
`narcis/src/narcis/store_eve.cljc` (92 lines) and
`narcis/src/eve/text.cljc` — working Eve implementations by the same
author who asked for this one. Everything in §2 is taken from them.

---

## 1. The goal

eve.db.ds is DataScript whose database **lives in an Eve atom**:

- The conn's DB is the value of an Eve atom. There is no host atom
  anywhere in the chain, no separate storage layer, no reclamation
  bookkeeping of the library's own.
- A transact is a stock `swap!` on that atom. Eve's swap path does
  the work: it binds the atom's domain while the swap fn runs, holds
  the new DB, retires the superseded DB by reachability, and tiers —
  a disk-backed Eve atom **is** the store.
- The public DataScript API is unchanged. Users of the published
  lib change no lines; what changes is that the thing underneath
  becomes the thing the docs already claim it is.
- The library consumes Eve the way narcis does: **one require,
  `[eve.alpha :as eve :refer [$]]`, and everything through it.**
  No Eve internals anywhere — not in src, not in test, not in
  harness, not in scripts.

## 2. The model — six rules taken from narcis

R1. **One atom holds the state; the value is plain Eve data.**
store_eve keeps the whole app state as one plain map in one atom.
For ds: the atom's value is the DB, an `eve/deftype` whose fields
are Eve collections and Eve arrays. Nothing else is in the atom —
no listeners, no caches, no functions (Eve memory holds no
functions; that is standing law, not a preference).

R2. **Values are built inside the swap fn.** A deftype value nested
in an atom is Eve pointers; it is sound because it is only ever
built and read inside the atom's own machinery — constructed in a
swap fn, where the domain is bound. eve.text's form of the same
rule: a document is edited in the domain it lives in; a value from
another domain is copied in by value. For ds: **all DB construction
happens inside the transact swap fn** (or inside another conn swap
fn). No DB value is ever constructed on the host and handed in.

R3. **Reads run in place, inside `($ …)`.** A plain deref of an
atom holding by-reference values refuses delivery, so no path in
store_eve derefs raw: reads are read regions. For ds: every
internal read of a conn's DB — query, pull, entity, datoms,
serialization — runs inside `($ …)` against the atom. What a read
returns across the region boundary is plain materialized data
(query results, pulled maps, datom vectors), never a live branch
of the DB.

R4. **Delivered DB values are copies, and that is the semantics.**
`@conn` and `d/db` deliver the DB through Eve's deref, as users
expect. A delivered value is a flat, self-contained value: valid
forever as a value, queryable with `d/q` / `d/pull` directly.
What it is not is a window into the atom. John's §7.1 ruling
governs the other direction: **retention inside the atom is
reachability inside the atom** — if the library (or a user) wants
an old DB to survive _as the atom's own state_, the reference to
it must itself live in an Eve atom. The library stores no old DBs
anywhere; tx-reports carry delivered copies. The README states
this rule where users see it (already landed on the salvage
branch; the text carries into this build's README verbatim).

R5. **The domain is the store's lifecycle.** store_eve opens the
disk domain at open (Eve's defaults, journal included) and closes
it at close. For ds: `create-conn` opens what it needs and the
conn owns it; a persistent conn's domain is opened at creation
and closed by `close-conn!` (new public fn, additive — closing
was implicit-host before and simply didn't exist on Eve).

R6. **Process-local machinery is the host's own.** Caches,
registries and listener lists are the guest program's business:
host types, host atoms, host volatiles — the host's facilities,
used openly as host code, never dressed as Eve and never stored
in Eve. (This is what narcis's non-Eve store does and what the
old lib violated in both directions: host state smuggled into
the conn map, Eve internals reached for host jobs.)

## 3. Target and pin

- **Repo/branch:** `johnmn3/eve.db.ds`, one branch for the whole
  rewrite: `lane-m/ds-in-eve-rewrite`. The published tree is
  replaced increment by increment (§5); when an increment lands,
  the old namespace it replaces is deleted in the same commit.
- **Eve pin:** the current Eve release line at implementation
  start (the `release/eve-0.9.3.3` collation tip in
  SeniorCareMarket/eve — narcis pairs with this line, whose
  eve.alpha carries the value-array family: `make-array`,
  `alength`, `array-seq`, `aget`, `aset`). The exact commit is
  recorded in deps.edn and in increment 0's commit message.
- **Step 0 of the build re-derives eve.alpha's export table from
  the pinned tree** and banks it in the record. Every Eve call in
  this plan is spelled against that table. A spelling the table
  doesn't have is a surface gap (§4), never a reason to reach
  into an internal namespace.

## 4. The alpha contract

- The only Eve require in the entire tree — src, test, harness,
  scripts — is `[eve.alpha :as eve :refer [$]]`.
- Enforcement is mechanical: a grep gate in the build script
  fails on any other `eve.*` require or fully-qualified `eve.*`
  call outside `eve.alpha`. The gate runs on every increment.
- **Surface-gap protocol:** when an increment needs an Eve
  operation alpha doesn't spell, the increment STOPS at that
  point. The gap is filed as one named item — the `eve/…` fn
  that should exist, its contract, the call sites waiting on
  it — in the record and to the Eve seat. It is never filled
  from internals, never worked around with host tricks that
  fake the semantics. The filed gap list from the salvage
  campaign (fix/ALPHA-GAPS.md) is the starting backlog; this
  build re-derives it against the pinned alpha table in
  increment 0 and works the list down only by Eve growing the
  surface or by a design in this plan that doesn't need it.

## 5. Architecture

Namespaces (all `.cljc`, one Eve require each at most):

- `eve.db.ds` — the public API, re-exporting the impl namespaces
  the way the published lib's `datascript.core`-shaped API does
  today. API parity is defined by the published lib's public
  vars, listed mechanically in increment 0.
- `eve.db.ds.db` — the DB value: `eve/deftype DB` [schema, eavt,
  aevt, avet, vaet, max-eid, max-tx]; `eve/deftype Datom`
  [e a v tx added]. DB construction ONLY inside swap fns (R2):
  this namespace provides the pure step fns (`db-with`-steps)
  that a swap fn calls; it never builds a DB "on the host" for
  delivery except the empty DB at conn creation, built inside
  the creating swap.
- `eve.db.ds.bset` — the persistent sorted set the indexes are
  made of, rewritten as `eve/deftype` Node/Leaf over Eve arrays
  (the eve.text store shape: a leaf is its size and its slots;
  a node is its size and its child slots). Its contract is the
  upstream persistent-sorted-set test suite, adopted as tests.
  This is the one hard data structure in the build; it gets its
  own increment and its own green gate before anything uses it.
- `eve.db.ds.conn` — `Conn`, a host deftype wrapping exactly two
  things: the Eve atom, and the host listener registry (a host
  atom of host fns, R6). `create-conn`:
  `(eve/atom {:id <id> :persistent <dir>} empty-db)` with a dir,
  `(eve/atom {:id <id>} empty-db)` without; the id is derived
  from the dir for persistent conns (same dir → same atom →
  restore is reopening) and gensymmed for in-memory conns.
  `transact!` = `(swap! atom (fn [db] (step db tx-data)))`
  with the report assembled from the swap's before/after values
  per R4. Listeners fire after the swap, from the host registry,
  with the report. `listen!`/`unlisten!` unchanged.
- `eve.db.ds.query` — the datalog engine. Conn entry points run
  inside `($ …)` (R3); DB-value entry points run on the delivered
  value directly. The parse cache is a host LRU (R6).
- `eve.db.ds.pull` — pull, same read discipline as query.
- `eve.db.ds.entity` — entity API over the same reads.
- `eve.db.ds.serialize` — DB → plain data and back, over datoms:
  write reads the DB inside `($ …)` and materializes a datom
  vector; read builds the DB by transacting into a fresh conn
  (R2 — construction inside the target atom's swap). No Eve
  internals, no slab file formats: a serialized DB is data.
- **There is no `storage` namespace.** Persistence is the disk
  atom (R5). `restore-conn` over a foreign storage raises the
  guidance error naming the disk-atom route, as the started
  salvage build already did.

Decisions made here, not left open (the old plan's §7, closed):

- **Old DB values:** R4 + the §7.1 ruling. Delivered copies are
  forever; in-atom retention is reachability. README states it.
- **Domains:** one atom per conn; a persistent conn's domain is
  its directory. DB values do not move between conns except as
  delivered copies or by re-transaction from datoms (serialize's
  read path). No cross-domain adoption operation is required by
  this design; if delivery of a large DB proves too costly at
  the cliff gate, that is a surface-gap filing (in-place adoption),
  not a redesign.
- **Deref:** `@conn` works because Conn implements the host's
  deref by dereferencing its Eve atom. Internal code never relies
  on it (R3); users get the delivery semantics of R4.
- **Capacities/atom options:** Eve's defaults. The cliff gate
  measures them; tuning is an atom-options change in
  `create-conn`, not a design change.

## 6. Build order — increments, each with its gate

Every increment: implemented as written here, committed as it
goes, green on its gate before the next begins. The test contract
throughout is the repo's own suite (the upstream DataScript
battery as ported in the published lib's test tree), adopted as
tests — test code obeys the alpha contract too (§4); a test that
touched Eve internals is rewritten to the public surface or the
assertion moves to a driver.

0. **Skeleton.** Branch, deps at the pinned Eve, alpha table
   re-derived and banked, public-API var list banked, grep gate
   live, suite harness runs and reports the (failing) battery.
   Gate: harness runs; grep gate passes on the skeleton.
1. **Datom + bset.** `eve.db.ds.bset` and the Datom type.
   Gate: upstream persistent-sorted-set suite green; Datom
   compare/hash/printing round-trips in its suite slice.
2. **DB value.** Indexes over bset, db-init, datoms/index reads,
   DB equality/hash. Gate: the battery's db namespace rows match
   their baseline (pre-existing failures named, not inherited
   silently).
3. **Conn + transact.** Conn, create-conn (in-memory and
   :persistent), transact!, listeners, reset-conn!,
   conn-from-datoms, tx-reports. Gate: transact + conn suite
   slices green; smoke: create/transact/query/listen/unlisten/
   reopen-persistent round-trip.
4. **Query.** The datalog engine end to end. Gate: query,
   query-rules, query-find-specs, query-aggregates,
   query-intrinsics slices green.
5. **Pull + entity + datafy.** Gate: their slices green.
6. **Serialize + persistence proofs.** Serialize round-trip on a
   cliff-shaped DB; persistent conn close/reopen with identical
   digests. Gate: serialize slice green (rewritten to the atom
   model — the old storage slice is deleted, not ported).
7. **The gates of §7, all of them.** Battery row-for-row, cliff,
   plateau, grep. Gate: §7 itself.
8. **Docs + landing.** README (truth pass incl. the §7.1 rule),
   GUIDE updated to the built shape, PROVENANCE unchanged.
   Landing to eve.db.ds main follows John's direction for that
   push — asked for, not assumed.

## 7. Acceptance — the build is done when all of these are banked

All at the pinned Eve (§3); drivers in
`realworld-suites/cliff/`; timings reported separate from
process wall (JVM cold compile is not a measurement).

1. **Battery row-for-row.** Every namespace row of the JVM suite
   matches the banked 44ca06c baseline; the known pre-existing
   rows (db hash-cache errors; the old storage load failure —
   deleted with its namespace in increment 6, its replacement
   slice green) match as named, not as new failures.
2. **Cliff gate.** Plain build, `-Xmx4g`: 500 pages **completes**
   (the published lib OOMs at ≈390). Reported: wall, heap-used,
   live census. 2,000 pages if 500 is comfortable. Stock's 500
   reference: 3.35 s / 76 MB heap-used — heap is the gate, wall
   is reported, parity on wall is not claimed.
3. **Digests identical.** 100 pp: 25,763 datoms, sample hash
   423396658, backlinks 897, open tasks 237, tree 31. 250 pp:
   64,355 datoms, same hash. 500 pp: 128,783 datoms.
4. **Plateau.** 50-page DB + 10,000 identical 2-datom edits:
   used data plateaus in the class the one honest measurement
   of reclamation set — flat, single-digit MB over the run —
   and the number is stated, whatever it is.
5. **Alpha audit clean.** The §4 grep gate over the whole tree:
   zero Eve-internal call sites. Survivors: none — a survivor
   is a filed surface gap with the increment stopped, per §4.
6. **Persistence.** A persistent conn closed and reopened at
   250 pages answers the §7.3 digests identically, from the
   disk atom alone.

## 8. What is NOT carried over

- The published lib's source. It is the specification of the
  API and the source of the test port — it is not an input to
  the implementation. Where this plan and the old code disagree,
  this plan governs.
- The salvage branch (`lane-m/ds-reclamation-proto`) and its
  conn. Its measurements stay in the record as evidence; its
  code does not merge. Candidate-(a) (manual reclamation on a
  host conn) stays rejected, permanently.
- `storage.cljc` and the IStorage shape. Deleted by design (§5).
- Every `eve.*` require outside `eve.alpha`, everywhere (§4).

## 9. Rules that bind this work (John's standing orders)

- Implement this plan word for word. A place where the plan is
  wrong is reported to John with the evidence and the proposed
  change — the plan is amended on his word, not drifted from.
- Commit to the branch as you go; bank progress in the record
  at every landed step — silence reads as stall.
- A defect is a measurement, not a rumor: every defect claim is
  UNVERIFIED until re-derived at the current pin (tree, tool
  versions on the actual PATH, a fresh-staging reproduction,
  reconcile against any seat where the shape passes). A claim
  that dies is banked as a correction naming the discriminator.
  No defect report leaves this lane without its provenance
  line: tree, tool versions, fresh-repro result.
- Local 10-second rule for dev commands; heavy legs check
  load1 first (never start while load1 > 8) and belong on
  venues at scale. Memory-traffic verdicts are venue-judged,
  never closed on a local parity reading.
- One branch for the whole body of work. Landing and any push
  to a public repo follow John's explicit direction for that
  push.
