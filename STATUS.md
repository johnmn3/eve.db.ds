# DataScript standing lane — STATUS

Seat: re-staffed Nov 14 2026 on John's direct order ("Get all those
lanes back up and working"). Branch: `lane/ds-standing` (eve.db.ds).
Worktree: ~/workspace/lanes-restart/datascript/repo (seeded from
eve.db.ds main @ 2c45e69cf0, pulled via the git-data API and verified
against the remote ref, 2026-11-14).

Charter (standing-lanes.md lane 2): **all 12 hosts working high-perf
for large DataScript runtime workloads, in contention with other
runtimes.** One codebase: this repo (lib, contract tests, perf
harness). Eve pin (deps.edn): SeniorCareMarket/eve
release/eve-0.9.3.2 @ 45935b95 (resolves in SCM/eve; tree 6ba6cbac2e).

## Census — what is banked (verified firsthand this seat, 2026-11-14)

Records read: ops cards ds-* (8; ds-js-nth-fix.json is a 0-byte dead
card), STAGE2-STATUS banks 1–42 on lane-m/ds-stage2 @ 8293c25022,
RESULTS-FINAL.md (ds-perf-final), eve.db.ds RESULTS.md +
ONE-EVE-REMAINING.md @ main 2c45e69cf0, Grok mailbox through
RESULT #462.

### Canonical bench driver — LOCATED

`bench/contract/ds_bench.cljc` lives on **lane-m/ds-perf-bench**
@ d309f1fc at `docs/datascript-perf/bench/contract/ds_bench.cljc`;
sha256 = **7ef8a26485767732d3e20cad0555920f042df6e133bacaafb522154ebfaa9b45**,
byte-identical to the RESULTS-FINAL pin (verified by hashing the
fetched file this seat). Its mains (ds_bench_jvm_main.clj,
ds_bench_main.cljc) are beside it. The perf-doc §04 blocker
("driver unlocated") is closed: the driver was never in the release
tree or this repo; it is in the perf-bench lane's docs tree.

### Per-host proof matrix

Proof standard: flagship pattern — build + fresh-process readback
exact, at a named pin. 12 hosts = the bank-23 frontier set.

| Host | State | Evidence of record |
|---|---|---|
| JVM | **PROVEN** | Flagship 5-host pass (Nov 7–8); eve.db.ds plane A PASS (6/6 checks, seals equal); people-1k keys EXACT (eve.db.ds RESULTS) |
| bb | **PROVEN** | Flagship; plane A PASS (6/6, seals equal); stage-2 frontier table banked (STAGE2-BB-PERF, bank 9+) |
| jolt | **PROVEN** | Flagship 5-host pass |
| nbb | **PROVEN** | Flagship 5-host pass |
| glojure (glj) | **PROVEN** | Flagship 5-host pass |
| ClojureCLR | **PROVEN (adopted)** | people-1k COMPLETE-verified on its own lane; adopted bank 23, not re-run. people-20k walled at F-CLR-REGION-2G (Eve CLR region addressing — Eve-side, named) |
| jank | **PROVEN solo (completion-first)** | Grok RESULT #461 (2026-11-11 01:46 ET), collected by this seat 2026-11-14 — never banked into STAGE2-STATUS (record ends bank 42, pre-#461). Completion-first variant: jank write EXIT 0 (`:write-ok :in-process-count 2000`), fresh read EXIT 0 (`:read-count 2000 :read-q34 10`), JVM control pair green. Plain probe (no completion first) fails analysis: `satisfies?` unresolved in eve/atom.cljc — completion-first is the shipped seat pattern (same as bb). Cross-read JVM←jank FAILED: `Assert failed: (db/db? db)` at datascript.core/datoms (banked log xhost-jank-jvmreadjank.out) — open cross-host item, see below |
| cljs | PARTIAL | Compiled-cljs datasets proven by fresh-process readback (demo tree); 0.9.3.1 campaign: people-1k writes + 9/9 cross-host readback (carried row, eve.db.ds RESULTS). Flagship-pattern leg at the current pin NOT run |
| basilisp (lpy) | PARTIAL | people-1k passed on the lpy-standing tree; eve.db.ds defect D1 (db.cljc tx-id? :lpy `.equals` arm) FIXED in-tree (portable `=` body, verified in this tree at db.cljc:1977) — closure run at this repo's pin NOT yet done. Plane-B basilisk body gate was router-HELD Nov 10 (git transport), never delivered |
| clojerl (clje) | NOT PROVEN | No flagship-pattern DS row. Adjacent: ds-clje-lru branch; Eve-side gaps collated into 0.9.3.2 (ONE-EVE-REMAINING post-close-out) |
| ClojureDart (cljd) | NOT PROVEN | Compiled form was in flight on its own lane at bank 23 (Nov 9); no DS proof row since |
| cherry | NOT PROVEN | Bundle links; defect chain closed on its lane; confirming run sat in J's inbox behind the dead SSH path (Nov 9) |

Score: **7 of 12 proven** (6 flagship/adopted + jank solo), 2 partial,
3 unproven.

### Contention (charter stage 3)

- C1 AUDIT-VERDICT PASS, C2 READBACKS PASS (900 = expected, both
  hosts) — bank 33 local route, confirmed on Grok bank 40
  (RESULT #435). Two venues agree.
- C3 attempt 1 FAILED (bank 34): two member defects + a cross-host
  index finding. Seat-8 isolation (bank 35) COMPLETE: the index
  writer is **jolt** (nbb the same class solo); glj exonerated
  (dies at load, never writes); pair-jolt loses ONE increment.
  **C3 attempt 2 NOT RUN** — gated on disposition of the jolt
  index-writer finding (Eve-side slab representation class).

### Cross-host cell

The bank-22 decision rule stands armed. Reopen-xhost logs (vm-b and
cc-vm sets, incl. xhost4) are present at the ds-stage2 tip
(8293c25022, banked by the #461-era runs) but their verdicts were
never collected into a bank — collection owed. The #461
JVM←jank `(db/db? db)` failure is the same family: a DB root
written by one host not recognized by another host's `db/db?`.

### Read / write frontier (priced facts, RESULTS-FINAL @ e727a05837)

- Reads: find-datom 170.53 ms/op (~389× stock), find-datoms
  78.44 (~211×) per 2,000 seeks @1,000-people — the read frontier
  this charter takes after host expansion (find-datom class).
  Later read work exists on lane-m/ds11-jvm-reads (not censused
  in depth this pass).
- Writes: single-swap regression vs (a3) CONVICTED to the DS11
  tally fix (SEPARATION.md, lane-m/ds-write-regression: +17.0 ms
  add-1 / +39.1 add-5 / +54.0 retract per commit, calm); the
  transient-touch-accumulator prototype recovers NOTHING
  (calibration complete). Disposition is Eve-side; this lane
  carries the priced fact, not the fix.

### Closed lanes (not reopened)

ds-perf-final COMPLETE (RESULTS-FINAL); ds-write-regression done
(tally convicted); ds-materialization COMPLETE (claim refuted;
F1 value-filter scatter / F2 bb root deref / F3 unseeked avet
referred); ds-bb-findcoll complete (no host defect; completion HOF
fix proven 9/9); ds-bb-walls stage 1 landed on 0931.

## Next stage — in charter order

1. **Host expansion, unproven first:**
   a. basilisp closure run at this repo's pin (harness/run-lpy.sh;
      Eve @ 45935b95, basilisp 0.5.1 on hatch) — closes D1 with a
      measured row or names the next wall. LOCAL, first.
   b. cljs flagship-pattern leg at this pin (needs a cljs harness
      main for this repo — instrument work, then the leg).
   c. clje / cljd / cherry legs as their Eve-side landings allow;
      each is a venue filing (job agent), not a local run.
2. **Contention C3 attempt 2** — after the jolt index-writer
   finding is dispositioned on the Eve side.
3. **Read frontier** — find-datom class, on the canonical driver
   (now located), numbers comparable to doc/performance.md §1.

Venue state at seat open (Nov 14): J wedged (SSH banner timeouts
since ~09:26 ET), kind-hypatia dark, Grok via job agent only.
Hatch: light legs only, load1 gate < 8.

## Provenance

Tree: eve.db.ds main @ 2c45e69cf0358b31c2d0c9142ae62417ed64c0fc
(API-verified); records at lane-m/ds-stage2 @ 8293c25022,
lane-m/ds-perf-bench @ d309f1fc4a, lane-m/grok-mailbox @ 6abf78196b.
Tools: python3 (API helper), git; runs carry their own provenance
lines as they land.
