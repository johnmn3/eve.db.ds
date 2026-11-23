# Shootout startup history (this box: 8 cpu, 15 GB RAM + 12 GB swap)

Contract everywhere: `contract.one` — fresh process, persistent conn, one
`transact!`, `{:mark :ONE-DONE :datoms 2}`; median of 5 fresh processes;
cap 10,000 ms. Times ET.

## Pre-t98u cells (stamp on every cljs/jolt/jank launch; JVM uberjar without AppCDS)

Run 2026-11-22 14:27 ET, `scripts/eve_perf_run.sh`, Eve `127061038b`, ds `4d12d2c8fc`.

| leg | median ms | runs ms | load1 start/end |
|---|---|---|---|
| jvm-aot | 1204 | 1204 1215 1204 1183 1144 | 1.92 / 2.00 |
| jvm-src | 1422 | 7879 1335 1442 1422 1284 | 2.00 / 2.13 |
| cljs | 263 | 273 263 259 263 267 | 2.13 / 2.47 |
| jolt | 864 | 903 859 864 864 854 | 2.47 / 2.36 |
| jank (-O3) | 426 | 827 510 394 426 394 | 2.36 / 1.16 |
| glj | 5657 | 5685 5657 5314 5523 5805 | 1.16 / 3.15 |

Earlier quiet-box medians (same pre-t98u cells, BASELINE.md §1/§5):
cljs 243, jank 362, jolt 816, jvm-aot 1329, jvm-src 1374.

## t98u cells (pointer file for cljs/jolt/jank; AppCDS for jvm-aot)


Run 2026-11-22 17:34–17:45 ET, `scripts/eve_perf_run.sh` (COMPARE=1, LOAD_MAX=3),
Eve `127061038b` (frozen `git archive` snapshot, same sha as the 14:27 run),
ds `961bb57b6a`, env `/workspace/eve-perf/env.sh` (jolt v0.8.28, jank PPA
2026-11-20, JDK 21). Every changed cell is followed by its pre-t98u twin on
the **same artifact** (`run-nocds` / `run-stamped`), so old vs new isolates
the harness change. Builds (and AppCDS training) are in `ensure`, untimed,
and rebuild when the basis stamp / jar sha256 changes.

| leg | cell | median ms | runs ms | load1 start/end |
|---|---|---|---|---|
| jvm-aot | CHANGED: AppCDS archive | 591 | 616 583 587 591 607 | 1.92 / 2.33 |
| jvm-aot-old | pre-t98u (no AppCDS), same jar | 1121 | 1178 1110 1122 1121 1108 | 2.33 / 2.46 |
| jvm-src | unchanged | 1246 | 1221 1246 1240 1280 1262 | 2.46 / 2.51 |
| cljs | CHANGED: pointer file, no per-run stamp | 173 | 208 173 161 208 157 | 2.63 / 2.63 |
| cljs-old | pre-t98u (stamp every run), same artifact | 243 | 250 266 232 243 241 | 2.63 / 2.63 |
| jolt | CHANGED: pointer file, no per-run stamp | 625 | 604 631 625 607 635 | 2.63 / 2.74 |
| jolt-old | pre-t98u (stamp every run), same artifact | 842 | 835 850 843 822 842 | 2.74 / 2.76 |
| jank (-O3) | CHANGED: pointer file, no per-run stamp | 265 | 278 265 263 259 283 | 2.76 / 2.76 |
| jank-old (-O3) | pre-t98u (stamp every run), same artifact | 362 | 347 353 362 390 413 | 2.76 / 2.76 |
| glj | unchanged | 5445 | 5547 5445 5334 5688 5269 | 2.80 / 2.81 |

glj: two in-run attempts ended at load1 3.06 and 3.80 (medians 5287, 5231);
the row above is the glj-only rerun that stayed under 3.
