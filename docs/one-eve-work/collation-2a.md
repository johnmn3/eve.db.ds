# #56 collation 2a — d1 serialize + d2 query_v3

Base: `a3b0d8bfdd9e7bf1a18ea445573042c01ac41e13` (`lane-m/eve-db-ds-one-eve-impl`).
Eve :dev pin: `ec22aba4c0ddf4f2ff58f26b04d654d53f247d61`.

Both worker tips were already based on the current impl tip. They are
incorporated by non-fast-forward merges, no conflict resolution required:

| worker | tip | merge commit | files |
|---|---|---|---|
| d1 | `1d02cecaaae23a5ac31778fbae7e68e20541f79e` | `eedd0d8cff0abbbd35d3004bb1bef4712e616fe7` | `serialize.cljc`, `db.cljc` (JS-array lookup-ref deleted), `contract.serialize-portable`, `docs/one-eve-work/d1.md` |
| d2 | `20e23037a6c4e9fa199948a440d5f536dba0f448` | `6469ba9d3dbd2f7342645c5b26e3f2b9721c6f93` | `query_v3.cljc`, `test/eve_db_ds_query_v3_test.cljc`, `docs/one-eve-work/d2.md` |

No overlapping paths. `ort` merged both cleanly. Per-row logs remain in
`d1.md` and `d2.md`.

## Counts

`python3 scripts/one_eve_counts.py` on the base:

```
host-branches=106
plain-type-sites=0
```

On the collated tree (after both merges, before this note):

```
host-branches=91
plain-type-sites=0
```

d1 folded 12 serialize/lookup-ref sites (106→94). d2 folded the 3
query_v3 java.util collection sites (106→103 on its own tip). Combined
15 sites, 106→91. No plain types were introduced.

Known remaining serialize gap, unchanged by collation: exact `N`/`M`/ratio
tokens still need Eve number types on nbb and basilisp. The portable
driver rejects a changed token instead of silently rounding.

## Gate protocol

All runs used `flock /workspace/ds-wt/one-eve-workers/test.lock`. Disk and
inodes were checked first (`df -Pk` / `df -Pi` /workspace; free inodes
were ~1.55M, well above the 50k floor). Logs: `/tmp/collation-2a/all.log`.
Each host loaded once and ran all five checks in one process. Hosts:
JVM (Java 21), bb 1.13.226, nbb 1.6.214, basilisp 0.5.1. No full
DataScript suite was run here.

Final tree gate results (2026-11-11 10:10–10:12 ET), all PASS, exit 0:

| Host | only-core | lru-portable | transact-realized | serialize-portable | query_v3 regressions |
|---|---|---|---|---|---|
| JVM | PASS | PASS | PASS | PASS, exact `N`/`M`/ratio OK | PASS, 12 checks |
| bb | PASS | PASS | PASS | PASS, exact `N`/`M`/ratio OK | PASS, 12 checks |
| nbb | PASS | PASS | PASS | PASS, exact tokens explicitly rejected | PASS, 12 checks |
| basilisp | PASS | PASS | PASS | PASS, exact tokens explicitly rejected | PASS, 12 checks |

The four `SERIALIZE-WIRE` lines are byte-identical. nbb/basilisp exact-number
lines are the intended guard, not a completed cross-host numeric semantic.

## Fair query_v3 perf (JVM, not a gate)

Same DB (100 entities `:name "N0"`…`"N99"`), same query
`[:find ?n :where [?e :name ?n]]`, at this collated tip. 20 warmup
queries, then 5 samples of 100 queries. Compile/load excluded.
`eve.db.ds.query/q` and `eve.db.ds.query-v3/q` both returned the same
correct 100-row set. Logs: `/tmp/collation-2a/bench.log`.

| engine | 100-query samples (ms) | median ms/query |
|---|---|---:|
| `eve.db.ds.query/q` | 55.405, 35.171, 28.665, 36.623, 26.439 | 0.352 |
| `eve.db.ds.query-v3/q` | 307.141, 208.424, 323.287, 189.873, 209.053 | 2.091 |

v3 median 2.091 ms/query is in line with d2's post-port 2.015 on this
query. It is **not** in line with the main engine: v3 is 5.94× slower
(threshold for a brief profile was >2×). d2's "before" 0.500 ms/query
is not a fair baseline; that snapshot returned only `#{[nil]}`.

### Hot spot (no fix in this collation)

Phase timing (200–400 iterations, `/tmp/collation-2a/profile2.log`)
attributes most of the v3 cost to `collect-to` (about 1.57–1.66 ms of
~2.00 ms/query, ~80%). `resolve-ins` is negligible; `resolve-clauses`
is ~0.19 ms. Building the collect transducers is cheap (~0.002 ms).

Inside `collect-to`, `e/into` into `eve.set/empty-hash-set` (`fast-set`)
is the hot path: 1.365 ms/query for the same transducers. The identical
pipeline into a Clojure `#{}` is 0.071 ms, and into a vector 0.065 ms
(~19× faster than the Eve set). The main engine collects with
`(into #{} (map vec) …)`.

A short JFR of 2500 v3 queries (`/tmp/collation-2a/v3-long.jfr`) shows
samples in `eve.set/hamt_conj`, `eve.hamt-util/portable-hash-bytes`,
`eve.encoding/bytes-of`, and `eve.region.JvmByteBufferRegion/_read_bytes`,
alongside seq/`map` force in the collect transducers. This is a report
only; collation does not change the collect accumulator.
