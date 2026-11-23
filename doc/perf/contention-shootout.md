# Contention shootout: every runtime against ONE disk store at once

Question it answers: are the shootout numbers *correct* in Eve's sense — can
each runtime hit the same persistent store at its alleged speed while the
others hit it too, across all Eve operations (atom swap!/deref/reset!, and
eve.db.ds transact/find/pull/query), with every write surviving and every
runtime seeing the same final database?

## Pieces

| file | role |
|---|---|
| `test/drivers/contract/ds_shootout.cljc` | portable driver (`init`, `worker`, `readback`) for JVM, jolt, cljs/node, jank, bb |
| `test/drivers/contract/ds_shootout_cljs.cljs` | node entry (loads the mmap/CAS addon loudly, `ps/init! :force true`) |
| `scripts/contention_shootout.sh` | orchestrates solo + together legs in a fresh scratch store, reads back from every runtime, cleans `$dir` and `$dir.*` |
| `scripts/contention_shootout_check.clj` | bb checker: exactly-once / no-lost-update / saw-foreign / readers-agree, plus the solo→together ns/op table |

## Store and workload

One persistent domain at `<dir>/ds` holds the eve.db.ds conn
(`d/create-conn schema {:persistent dir}`), a shared atom `:shoot/shared`
`{:n :by :cnt :ready}`, and one mailbox atom per writer
`:shoot.mbox/w-<rt>-<proc>`. Schema: `:op/key` unique identity,
`:op/tag` and `:op/rt` indexed, `:ctr/name` unique identity.

Writers join a barrier (`[:ready tag]`) and start together. One round:

* transact ×1 `{:op/key "T/i" :op/tag T :op/rt R :op/proc P :op/seq i}`
* swap! ×4 on the shared atom (`:n` inc, `[:cnt T]` inc, `[:by T] i`)
* deref ×4 (materialized), reset! ×2 of the own mailbox, read ×2 of a
  foreign mailbox (its seq must never go backwards)
* find ×2 (`d/datoms :avet :op/key` of a foreign key known from `:by`),
  pull ×2, query ×1 (`[:find ?r (count ?e) :in $ ?me :where [?e :op/rt ?r] [(not= ?r ?me)]]`)
* every 4th round: an `:aevt` scan and a `:db/cas` counter increment
  (retried on `:transact/cas`)

Every write carries runtime, process and sequence tags. Reads take a fresh
handle and retry `:eve.db.ds/db-not-kept` (a foreign commit between handle
and read). Each op is guarded per rep; errors are counted per kind with the
first two stacks kept. Flags (`WORKLOAD_FLAGS`, comma list): `nocas`,
`nodurable` (open the domain unjournaled), `casonly` (a `:db/cas` storm).

## Checks (per leg, after all writers exit, from a FRESH open in every runtime)

* every committed transaction present exactly once (no duplicates, no lost
  seqs, no extras); entities = tx-ok; datoms = 2 + 5 × entities
* no lost update: shared `:n` = total swaps, `[:cnt T]` and `[:by T]` per
  writer; DB counter = completed `:db/cas` increments; mailbox final seqs
* every writer saw every other writer through the atom, the mailboxes, the
  DB and the scan; find/pull misses = 0
* every runtime's readback identical (rows, shared, counter, mailboxes,
  datom and entity counts); `d/q` rows = the `:aevt` scan

## Run

```
DS_DIR=$PWD EVE_DIR=../eve RUNTIMES="jvm jolt" PHASES="solo together" \
  DUR_MS=20000 OUT=/tmp/shoot bash scripts/contention_shootout.sh
cat /tmp/shoot/verdict.txt
```

Runtimes: `jvm jolt cljs jank bb glj`. The script refuses to start below
2 GB / 60k free inodes. `KEEP=1` keeps the stores. `CLJS_JS=` reuses a
prebuilt :advanced bundle.

## Known failures (documented, not fixed here)

1. **Node + any other family loses commits after reopen (Eve journal).**
   Node's journal lock is a lock directory, which does not exclude the
   JVM's FileChannel lock or jolt's lockf; JVM (`journal_file.clj`
   `journal-append!` :190) and jolt (:330) write at a sampled file size
   while node appends O_APPEND. Overwritten framing stops `journal-scan`
   early and `journal-recover!` (eve `atom.cljc` :858–913) installs an old
   record on the next open. Observed (entities kept / committed):
   jvm+cljs 7/595, jvm+jolt+cljs 82/743, jolt+cljs 225/688, 5-way 18/79.
   With `nodurable` the same mixes are exact. The journal is being
   redesigned (cljc mmapped byte log) in the Eve lane.
2. **`:db/cas` report read after commit — use-after-free (eve.db.ds
   `conn.cljc`).** `-transact!` (:320–337) captures the raw TxReport inside
   the `swap!` fn and `deliver-report` (:310–318) reads `:db-before`,
   `:db-after` and `($ (mapv identity (:tx-data raw)))` after the commit,
   with no epoch pin over the gap. A cas tx-data carries the retracted
   datom, reachable only from db-before, which another process may reclaim.
   Seen as JVM "Unknown EVE type tag" (codec.cljc:388 ← slab_runtime.cljc:35
   ← sister.cljc:385) in jvm×2, JVM IndexOutOfBounds and jolt "invalid
   memory reference" in a 3-way mix, cljs RangeError (DataView) in cljs×2.
   The cas *commits*, the caller gets an exception: the DB counter ends one
   above the caller's count.
3. **cljs :advanced `d/q` binds nil (eve.db.ds query.cljc).**
   `lookup-pattern-db` (:451) binds by property name and the getter's :cljs
   arm (:278–315) reads with `aget`; Closure renames the Eve Datom's
   `v`/`tx` and `src/eve/db/ds/externs.js` declares only
   `datascript.db.Datom`. Correct under :simple. The cljs query cells of
   the shootout time nil-binding queries.
4. **bb:** the aggregate query throws "sci.impl.deftype.SciType cannot be
   cast to java.lang.Iterable" (hash-ordered-coll). Everything else exact.
5. **nbb:** cannot load — "Unable to resolve symbol: util/raise"
   (`db_fn.cljc:118`).
6. **jank from source:** first round ~220 s (JIT); needs AOT to join
   fairly. Also: a query with no `:in` mis-binds (parser.cljc :556–566) and
   jank's hash is unsigned, so `conn.cljc` new-conn-id differs from the
   other runtimes for stores whose path hashes negative (the script picks
   a non-negative store prefix).

## Results

See `Results 2026-11-23` below (appended by the run that produced them).

## Results 2026-11-23 (eve 38e6da6, eve.db.ds 24d8754; journaled default; 20 s per leg; 3 reps)

Correctness (series4, 18 legs, every runtime reading back from a fresh open):

| combo | reps PASS | tx committed (r1/r2/r3) | notes |
|---|---|---|---|
| jvm | 3/3 | 1,265 / 1,297 / 1,313 | |
| jolt | 3/3 | 1,010 / 998 / 993 | |
| jvm×2 | 1/3 | 1,898 / 2,044 / 1,817 | r2, r3: one `:db/cas` threw after committing (known failure 2); every other check exact |
| jolt×2 | 3/3 | 1,347 / 1,507 / 1,482 | |
| jvm+jolt | 3/3 | 1,481 / 1,481 / 1,523 | |
| cljs×2 | 0/3 | 975 / 780 / 946 | `d/q` nil rows (known failure 3) every rep; r3 also the cas throw |

In every leg: entities = transactions, datoms = 2 + 5 × entities, shared `:n`
= swaps, all writers saw all others, find/pull misses 0, readers agree.

Median ns/op inside the shootout (ms-tick timing). Solo find/pull/mailbox
reads have no foreign target, so they are not comparable and are left out.
Query counts foreign entities, so its work grows with the other writers.

| rt | op | solo | ×2 same rt | with the other rt |
|---|---|---|---|---|
| jvm | round (µs) | 15,432 | 20,932 (1.36×) | 24,307 (1.58×) |
| jvm | transact | 4,769,468 | 6,190,624 | 5,990,279 |
| jvm | swap | 1,105,435 | 1,418,595 | 1,204,878 |
| jvm | deref | 19,275 | 24,344 | 21,341 |
| jvm | reset | 1,054,074 | 1,304,160 | 1,031,097 |
| jvm | cas | 4,138,461 | 5,027,310 | 5,213,592 |
| jvm | scan | 6,772,870 | 9,641,200 | 8,087,378 |
| jolt | round (µs) | 20,048 | 27,063 (1.35×) | 30,415 (1.52×) |
| jolt | transact | 6,216,432 | 7,750,862 | 9,098,335 |
| jolt | swap | 859,013 | 829,393 | 1,099,848 |
| jolt | deref | 36,757 | 37,920 | 42,553 |
| jolt | reset | 638,469 | 610,274 | 878,971 |
| jolt | cas | 3,787,148 | 4,761,836 | 6,260,355 |
| jolt | scan | 20,344,000 | 30,579,364 | 29,301,204 |
| cljs | round (µs) | — | 42,324 | — |

With two writers each one slows by 1.35–1.58×, which is less than the 2× that full
serialization would give, so aggregate throughput rises (jvm 65→96
rounds/s, jolt 50→74). Writers in a pair stay within 0–11% of each other's
round count, so neither starves. `:db/cas` retries are 0–5%. Reads hit
`db-not-kept` 0–10 times per 20 s writer and succeed on retry.
