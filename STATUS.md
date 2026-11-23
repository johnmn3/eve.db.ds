# STATUS — eve.db.ds syntax formalization (Phase 2 lane)

Branch: `syntax/formalization-1` on johnmn3/eve.db.ds
Base: origin/main `39fdd41e0eaf5622b0ffd202662d5ccb366829e6`
(verified via `git ls-remote` at lane start, Nov 22 2026).
Authorization: John's order Nov 22 2026 ~6:04 PM ET (fleet rollout
to eve.db.ds, eve, cohost, valuescript).

## Steps

- [x] 1. Fleet templates installed (byte-identical) + kondo cache
- [x] 2. Census banked in CENSUS.md (before any fixing)
- [x] 3. Baseline test run banked (before formatting)
- [ ] 4. Format pass (own commit) + gate re-run
- [ ] 5. Fix campaign, rule-class by rule-class, gate after every batch
- [ ] 6. Gates on: bb lint exit 0 (5 legs), pre-commit hook, fmt idempotent
- [ ] 7. RETRO.md + push

## Named config exception

`.clj-kondo/config.edn` carries the Eve `:lint-as` pair
(eve.alpha/deftype, eve.deftype/deftype → clojure.core/deftype),
same as narcis Phase 1 (RETRO item 1): Eve ships no clj-kondo
exports, and this repo builds its core types with `eve/deftype`.
Reason written in the config file's header.

## Log

- Nov 22 ~6:15 PM ET: clone + branch; templates copied
  (.cljfmt.edn, .splint.edn, AGENTS.md byte-identical, verified by
  diff); bb.edn created (repo had none) with the fleet tasks over
  this repo's real paths (src, test) + the pinned splint task;
  kondo cache generation launched over the real classpath
  (`clojure -Spath`, Eve git pin from deps.edn).
- Nov 22 ~6:33 PM ET: FLEET TEMPLATE CORRECTION applied —
  .cljfmt.edn re-copied from the corrected template
  (:sort-ns-references? false; sorting :require lists reorders
  namespace load order — semantics, proven break in the media
  lane). Re-copy diff-verified byte-identical. The census numbers
  stand (the flag affects the format pass, not the check counts).
- Nov 22 ~6:35 PM ET: census complete — kondo 918 (248E/640W/30I),
  Splint 316 (gate task adapted to name paths: no-arg splint
  checked only 73/157 files here), Carve 154, cljfmt 154/157 files
  unformatted. Banked in CENSUS.md + census-raw/.
  Media RETRO items 3–5 read before kondo-config work: deftask
  :lint-as pattern, defcfn hook pattern (not used here — no FFI),
  and the two .cljc false-positive classes (unused-* miscounting
  #? references; dual-pass phantom unresolved symbols) that this
  portable codebase will meet at scale.
- Nov 22 (prior lane, then quota-blocked): kondo analyze-call
  hooks designed + miniature-proven for this repo's own macros
  (parser/deftrecord, db/defcomp, util/cond+, util/if+); baseline
  runners u2/u3/u4/bset/chain-autoclose green; u5 incomplete and
  ds-suite single-JVM run OOM'd (not a verdict).
- Nov 23 ~12:40 AM ET (successor lane): state re-verified against
  the tree (commits a58eb05, a244fb8; uncommitted hooks + bb.edn
  splint-path fix present as reported). Kondo-teaching commit
  landed (45bf05a): hooks + config + bb.edn fix. Full recount with
  hooks in (census-raw2/): **kondo 918 → 738 (140E/568W/30I)**.
  Remaining errors: unresolved-symbol 121 (23 = thrown-msg?,
  PROVEN a clojure.test assert-expr: test_macros.clj
  `(defmethod t/assert-expr 'thrown-msg? ...)` — not a var),
  type-mismatch 9, invalid-arity 5, private-call 4,
  unexpected-recur 1 (was 16 pre-hooks).
- Nov 23 ~12:46 AM ET: **u5 baseline re-run SOLO at -J-Xmx2g with
  its documented deps line: GREEN — U5-RESULT {:test 6, :pass 32,
  :fail 0, :error 0}** (.lane/baseline2/u5.log). CORRECTION to the
  prior lane's u5 observation: test-persistent-cross-process does
  NOT fail at this pin on a clean run. The first run's failure
  signature — child process dying with "a fault occurred in an
  unsafe memory access operation" at eve.region/bb-store-i32!
  (the SIGBUS shape of writing memory-mapped region files on a
  full filesystem), then the reopened DB reading :datoms [] — was
  measured during the home-wide disk-quota exhaustion that blocked
  this lane. Discriminator: same pin, same documented command,
  quota freed → 32/32 pass. Provenance: tree 39fdd41 + lane
  commits through 45bf05a (no src changes), JDK 21.0.13.1,
  Clojure CLI 1.12.6.1673, clj-kondo 2026.10.26.
- Nov 23: ds-suite baseline re-run via the driver's exact
  classpath (clojure -A:ds-suite -Spath) at -Xmx4g with the
  driver's java flags — IN FLIGHT (.lane/baseline2/ds-suite.log).
- Nov 23 ~1:00 AM ET: ds-suite baseline, split-run record
  (.lane/baseline2/): the single-JVM -Xmx4g run completed 27 of 38
  namespaces before the JVM was killed externally mid-run (no
  error in log; box under heavy sibling-lane load, memory tight).
  Those 27: tests=110 pass=608 fail=12 error=35. Fails/errors
  concentrated in test.core (4F), test.conn (6F/1E), test.db
  (1F/2E), test.entity (1F/1E), test.filter (17E), test.pull-api
  (4E), test.query (10E) — pre-existing at the pin; the unit
  runners (u2/u3/u4/u5/bset/chain-autoclose) are the green gate,
  ds-suite is the no-regression anchor. A second JVM completed
  datafy (1 test, 1 error) and issues (9 tests, 35 pass, 1 fail,
  36 errors) before also being killed during query-rules.
- Nov 23 ~1:20 AM ET: remaining 9 namespaces grouped 3-per-JVM
  (.lane/run-ds-rest.sh) — all three groups failed at load:
  "Could not locate eve/host on classpath". Root cause measured:
  the Eve gitlibs checkout for pin 665bcf84 had been removed from
  ~/.gitlibs (only other shas remained) between the successful
  runs and the group runs. Classpath re-resolved
  (clojure -A:ds-suite -Spath), which re-checked-out Eve at the
  pinned sha into ~/.gitlibs.
- Nov 23: **LANE BLOCKED on a runtime execution-risk notice**
  attached to that classpath re-resolution result (the fresh Eve
  checkout). Per the notice, the successor lane does not run,
  load, or test against that checkout without the user's explicit
  go-ahead. Remaining work when unblocked: re-run groups gA/gB/gC
  (9 namespaces: pull-parser, query-rules, query-v3, serialize,
  storage, transact, tuples, upsert, validation) to complete the
  ds-suite baseline union (currently 29/38 namespaces banked:
  tests=120 pass=643 fail=13 error=72 across the split runs),
  then step 4 format pass, step 5 fix campaign, steps 6–7.
  STATUS.md edits after 45bf05a are uncommitted (no git commands
  run after the notice).
- Nov 23 ~8:20 AM ET (third lane): OPERATOR AUTHORIZATION received
  (John, in reply to the coordinator's direct question about this
  exact checkout): "Authorize processing the Eve gitlibs checkout
  — resume the ds lane". The Eve gitlibs checkout at sha 665bcf84
  is processed under that authorization, including re-triggers of
  the same notice on the same checkout (one re-trigger noted on a
  classpath read this session; proceeded under the authorization).
  Inherited uncommitted STATUS edits verified against the tree and
  committed (8c42c91).
- Nov 23 ~8:35 AM ET: **ds-suite baseline COMPLETE — 38/38
  namespaces attempted** (.lane/baseline3/, driver's exact
  classpath/flags, -Xmx3g per group). New groups: gA tests=4
  pass=21 fail=0 error=1 (query-rules 3/17 green, query-v3 1/4
  green; pull-parser FAILS TO LOAD pre-existing: "No such var:
  db/call-in-parse-domain" at pull_parser.cljc:30); gB tests=24
  pass=115 fail=15 error=8 (serialize 5T/18P/14F/5E, transact
  19T/97P/1F/2E; storage FAILS TO LOAD pre-existing, as banked);
  gC tests=32 pass=232 fail=8 error=0 (tuples 24T/155P/8F, upsert
  6/59 green, validation 2/18 green). **Baseline union (38 ns):
  tests=180 pass=1011 fail=36 error=81, 36 loaded, 2 load-failures
  (pull-parser, storage) — all pre-existing at the pin.** The
  service restart killed the first gC attempt mid-run; gC was
  re-run clean. Step 3 done: unit runners green (u2/u3/u4/u5/
  bset/chain-autoclose) + this union is the no-regression anchor.
