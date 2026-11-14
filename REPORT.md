# DataScript standing lane — census + stage-1 report (2026-11-14)

Branch: `lane/ds-standing` (this repo). Lane worktree:
`~/workspace/lanes-restart/datascript/`. Full working state: `STATUS.md`
(this repo). Stage-2 predecessor records:
`~/workspace/lanes-restart/datascript/records/` (banks 1–42 from
`lane-m/ds-stage2`).

## Method

Census of every banked DS record the coordinator named (ds-lanes-pickup
records incl. STAGE2-STATUS banks 1–42, this repo's RESULTS.md,
RESULTS-FINAL, ONE-EVE-*, the Grok mailbox, ops cards), then
firsthand verification where the record's own evidence was thin:
the canonical driver was hash-checked against its pin; the jank
RESULT was read from the mailbox blob itself; the D1 fix was
re-derived in this repo's tree.

## Canonical bench driver — LOCATED (perf doc §04's blocker)

`lane-m/ds-perf-bench` @ d309f1fc4a, `docs/datascript-perf/bench/
contract/ds_bench.cljc`, **sha256 7ef8a26485767732d3e20cad0555920f
042df6e133bacaafb522154ebfaa9b45** == the pin in RESULTS-FINAL.md.
Verified copy: `records/ds_bench.cljc`. This repo's
`test/drivers/contract/ds_bench.cljc` hashes 759a6f68… — a
different driver; never substitute it.

## Per-host proof matrix (12 = bank-23 frontier set)

Proof shape (flagship pattern): build + fresh-process readback
exact, at a named pin.

| Host | State | Provenance |
|---|---|---|
| JVM | PROVEN | flagship 5-host pass, Nov 7–8; plane-A PASS 6/6 in this repo's RESULTS |
| bb | PROVEN | flagship pass; plane-A PASS 6/6 |
| jolt | PROVEN | flagship pass |
| nbb | PROVEN | flagship pass |
| glojure | PROVEN | flagship pass |
| ClojureCLR | PROVEN (adopted) | people-1k COMPLETE-verified on its own lane; people-20k walled at F-CLR-REGION-2G (Eve-side) |
| jank | PROVEN (completion-first) | Grok RESULT #461, Nov 11 01:46 ET, collected firsthand this seat: jank2write EXIT 0 `:write-ok :in-process-count 2000`; jank2read EXIT 0 `:read-count 2000 :read-q34 10`; JVM control pair green |
| cljs | PARTIAL | compiled datasets proven by readback (demo tree); 0.9.3.1 campaign people-1k + 9/9 cross-host readback; flagship-pattern leg at the current pin not run |
| basilisp/lpy | PARTIAL → closure filed | people-1k passed on the lpy-standing tree; D1 fix verified in-tree (below); closure leg filed via job agent, HELD by the router (below) |
| clojerl (clje) | NOT PROVEN | — |
| ClojureDart (cljd) | NOT PROVEN | — |
| cherry | NOT PROVEN | — |

## Findings

- **D1 (basilisk tx-id? `.equals`)**: fix verified present in
  this repo's tree (`db.cljc:1977-1980`, portable equality body) —
  re-derived firsthand. Closure = a basilisk run of the fixed lib
  at the repo pin; never done until now. The leg is filed:
  harness `ds-harness.lpy-main` gained the missing `contract`
  mode this seat (mirrors bb-main), job script banked at
  `docs/ds-standing/run-lpy-contract.sh`, Eve pin
  SCM/eve @ 45935b9570bc (this repo's deps.edn). Filing HELD by
  the job-agent router 2026-11-14 10:27 ET ("submission to grok
  failed" — the router's git-submission path, same breakage as
  stage-2 banks 29/31); exactly-once, not re-filed. Local
  alternative blocked by a hatch write-guard window (397/876
  pin-tree files landed 0-byte; git object writes refused —
  environment defect, named in STATUS.md; retry owed).
- **jank open items**: plain-probe `satisfies?` analysis failure
  (banked logs at stage-2 tip); cross-read JVM←jank FAILED
  `(db/db? db)`; the bank-22 cross-host cell's reopen-xhost logs
  sit uncollected at the ds-stage2 tip.
- **Contention**: C1 audit PASS, C2 readbacks PASS (900 =
  expected, both hosts; banks 33/40). C3 attempt 1 FAILED
  (bank 34); seat-8 isolation COMPLETE (bank 35): the index
  writer is **jolt** (nbb same class solo), glojure exonerated.
  **C3 attempt 2 not run** — gated on Eve-side disposition.
- **Read frontier** (RESULTS-FINAL @ e727a05837): find-datom
  170.53 ms/op (~389× stock), find-datoms 78.44 (~211×) per
  2,000 seeks @ 1,000 people. Write regression CONVICTED to the
  DS11 tally fix (+17.0/+39.1/+54.0 ms calm); the
  transient-touch-accumulator recovers nothing (calibration
  complete) — disposition is Eve-side. Later read work on
  `lane-m/ds11-jvm-reads` not yet censused in depth.

## Next stages

1. lpy closure verdict when the held filing runs (or the local
   pin-tree pull retries clean) → matrix row flips or the wall
   is named.
2. cljs flagship-pattern leg at the current pin (cljs harness
   instrument first, miniature-proven).
3. clje / cljd / cherry legs as their Eve-side landings allow.
4. Bank-22 cross-host cell collection + the JVM←jank item.
5. C3 attempt 2 after the Eve side dispositions the jolt
   index-writer finding.
6. Read frontier on the canonical driver, numbers comparable
   to doc/performance.md §1.

Not declared done: 3 hosts unproven, 2 partial, C3 attempt 2
and the read frontier open.
