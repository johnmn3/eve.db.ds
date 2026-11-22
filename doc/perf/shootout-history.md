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

