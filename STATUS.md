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
- [x] 4. Format pass (own commit) + gate re-run
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
- Nov 23 ~9:00 AM ET (third lane): step 4 FORMAT PASS applied —
  fleet cljfmt fix over src+test, 154 files reformatted (the 3
  already-clean files untouched). Gate after format: unit runners
  u2 (11/79), u3, u4 (6/48), bset (5/816), chain-autoclose all
  exit 0; u5 green via its DOCUMENTED deps line (6/32) — the
  generic -Sdeps paths line cannot run u5 (no transit on that
  classpath; measured, harness fact not a format break).
  cljfmt check clean after fix. Environment note: bb/cljfmt on
  this box need GITLIBS=/home/hatch/.gitlibs (exec runs as root,
  whose default gitlibs is empty; without it bb tries to re-fetch
  the Eve git dep and fails).

## Step 5 batch 2 — ns pruning + src unused-* (this lane, Nov 23)

- thrown-msg?: all 23 sites are the clojure.test/cljs.test
  assert-expr DISPATCH symbol (defmethod at test_macros.clj:22),
  never a var — closed by symbol-exact config exclusion
  `:unresolved-symbol {:exclude [thrown-msg?]}` with the reason in
  the config header (same shape as Eve's sio__/offset__ exclusion).
- ns pruning (58 files): unused-referred-var / unused-namespace /
  unused-import entries removed from ns forms, driven by the JSON
  recount (langs-aware). Recount: 685 -> 421 total.
- FOUR dual-pass false positives found and RESTORED with per-site
  `#_{:clj-kondo/ignore [:unused-namespace]}` + written reasons —
  uses live only inside reader-conditional arms the other pass
  cannot see: api_surface_test (str, d), persistence (edn, str),
  ds_suite (ds-db, str), upstream test/cljs.cljc (str, t). Same
  family: core's edn require (cljs arm only), query's str require
  (clj print-method arm), u2/u3/u4 supports' clojure.test (clj
  arms) — per-site ignores with reasons.
- src fixes: bset dropped unused :refer [$]; core dropped
  FilteredDB from the cljs refer + clj import and the unused UUID
  import (squuid delegates to util); pull_api dropped the unused
  lru require; query dropped the unused impl.entity require;
  query_v3 dropped the unused dq require, RulesVar (comment-only)
  from refer+import, and the fq-only IReduceInit/Counted and
  unused Datom imports.
- Gate after batch (.lane/gate5b/): u2 11/79, u3 42/248, u4 6/48,
  bset 5/816, chain 5/36, u5 docline 6/32 — all baseline-identical.
- Remaining (kondo6): 421 — unused-binding 177, unresolved-symbol
  51, non-arg-vec-return-type-hint 35, redefined-var 27,
  unresolved-var 19, unresolved-namespace 18, unused-value 11,
  unused-private-var 8, unknown-map-key 8, missing-test-assertion
  7, type-mismatch 6, missing-protocol-method 5, invalid-arity 5,
  private-call 4, redundant-let 3, unresolved-protocol-method 2,
  singles. Splint + Carve campaigns still owed.

## Step 5 batch 3 — redefined-var + type hints (this lane, Nov 23)

- redefined-var (27) closed: conn's `filter`, core's
  `future-call`, db's `array?` and the laws test's Box/->Box are
  public definitions shadowing clojure.core — closed by adding
  the names to each ns's :refer-clojure :exclude. The 21
  eve.db.ds.db vars are upstream's cljs-stub idiom (empty
  #?(:cljs defn) stub early + real defn+ later): kondo 2026.10.26
  does NOT honor :exclude for redefined-var (measured), so each
  site carries a per-site ignore with the reason; stacked
  discards do not compose (measured), so the 8 sites needing two
  ignores carry one merged discard.
- non-arg-vec-return-type-hint (35) closed: hints moved to arg
  vectors for plain defn/defn- sites and for defn+ sites with
  class tags (^DB, ^Datom). For defn+ sites with lowercase tags
  (^number, ^boolean — 19) the name position is LOAD-BEARING:
  defn+'s patch-tag translates the tag per pass, and moving it
  to the arg vector broke CLJ compilation ("Unable to resolve
  classname: number", gate5c) — those sites carry per-site
  ignores with the measured reason. The five spliced-name sites
  in db.cljc (seqable?, tuple-ref-slot?, resolved-eid?, tempid?,
  tuple-has-tempids?) now splice [name ^Type [args]] per pass.
- find-datom (core) carries ^Datom on every arity's arg vector.
- Gate (.lane/gate5d/): u2 11/79, u3 42/248, u4 6/48, bset
  5/816, chain 5/36, u5 docline 6/32 — baseline-identical.
  Recount: 421 -> 359.

## Step 5 batch 4 — small classes (this lane, Nov 23)

Recount 359 -> 308. Closed: cond-else (pull_parser's `:eles` was
a typo for `:else` — same semantics, now honest), redundant-do,
redundant-let x3 (merged binding vectors), uninitialized-var
(*if+-syms* nil root; always bound by if+ before any read),
deps.edn (cheshire qualified), unused-value (query_v3 fold
branch rewritten to assoc! form; bset-stress into-via-doseq now
a reduce; transact "docstrings" that deftest does not consume
became comments), unused-private-var x8 (lookup-range, base-tx,
disj-all!, per-worker deleted as dead; riter-reduce moved into
its :clj arm — JVM parity only; rand-bits into its :cljs arm;
cljs-env? into a :clj arm; test-vars-for per-site ignore — used
from :lpy/:cljs arms), private-call x4 (cljs.core/pr-writer,
the IPrintWithWriter entry, per-site ignores), unknown-map-key
x8 (tuples: kondo infers the conn-handle shape for vars holding
real DB values; per-deftest ignores), storage.clj's 20 findings
(the file tests the deleted eve.db.ds.storage, cannot load,
retained as upstream record — three per-deftest ignores with
the reason in a file header note), duplicate-require (two
ns-less probe scripts share kondo's implicit user ns; per-site
ignore). :config-in was tried for storage and does NOT govern
in kondo 2026.10.26 (measured here, matches the cohost lane's
note) — removed. Gate (.lane/gate5e/): all unit runners
baseline-identical incl. u5 docline 6/32.

## Step 5 batch 5 — unresolved family + protocol/type (this lane, Nov 23)

Recount 308 -> 213, zero errors. Real fixes: cond+ hook now
models :let inside (and ...) tests (if+ semantics — bindings
scope over the rest of the test and the clause body); hooks
added for db/defrecord-updatable, core/sci-fn (body is SCI data
— quoted-fn model) and core/db-fn (plain fn model);
clojure.pprint/print-length-loop taught via :lint-as loop;
entity.cljc gained its missing [eve.alpha] require; upstream
issues/pull_api/serialize gained missing requires
(clojure.data, clojure.pprint, clojure.edn, cljs.reader);
query_v3's scratch (comment ...) block deleted (it referenced
unrequired t/perf namespaces and was the only user of the
eve.db.ds.core require, which went with it); check-bound call
sites pass real sets. Per-site/per-form ignores with measured
reasons: the driver family's fictitious cljs pass (~25 forms),
duplicate-ns artifacts (serialize/transact — the upstream tree
defines the same namespaces), pull_parser's baseline-dead call
sites, storage's helper, partial protocol implementations
(pull frames, SingletonRelation, Entity), Eve-deftype protocol
groups (Datom, bset Iter/ReverseIter), pr-writer-style
type-mismatch inference FPs (rseq on a slice Iter; @ of a plain
value in the dead pull_parser test). :config-in measured inert
for these classes in kondo 2026.10.26 and is not used.
Gate (.lane/gate5f/): all unit runners baseline-identical.

## Step 5 batches 6+7 — unused-binding + info classes (this lane, Nov 23)

KONDO LEG GREEN: 918 -> 0 errors, 0 warnings (bb lint's kondo
leg exits 0 cold-cache). unused-binding (183) closed by
lang-aware renames: a two-stage driver renamed a binding to
_<name> only where the name occurs nowhere else in its scope
(method/arity/let form, strings+comments stripped) — textual
proof no reader-conditional arm in any pass uses it; 179
renames. The four residuals by hand: Datom containsKey self arg
(a :keyword had masked the proof), two dbfn_bench compute fns
whose n is genuinely ignored, and resolve-sym (per-site ignore
— arg used in the :clj arm only, shared signature). Info
classes closed: aliased-referred-var (issues now t/is
throughout), redundant-str-call x5, redundant-nested-call x2,
is-message-not-string (real string message), unused-excluded-var
(entity's vestigial keys/get exclude removed). Gates
(.lane/gate5g/ + spot): all unit runners baseline-identical.
Remaining for bb lint: Splint (316 at census) and Carve (154),
dprint leg, pre-commit hook.

## Step 5 batch 8 — Splint campaign (this lane, Nov 23)

SPLINT LEG GREEN: 316 at census -> 0 style warnings
(bb splint src test exits 0). Recount before the campaign:
305 findings over ~35 rules (census-raw4/splint.txt). Two
span-matching drivers applied Splint's own suggestions for the
mechanical rules (~180 applications); the rest by hand with
per-site verdicts. Closures of note:

- def-fn in core.cljc x6 converted to defn PRESERVING the
  ^{:tag/:arglists/:doc} metadata (Splint's printed suggestion
  drops the meta); js.cljs x2 hand-written with real arg names
  (Splint prints %1# gensyms, not source).
- identical-branches cond merges applied only where the
  branches are textually identical IN SOURCE: query's t2 if was
  NOT merged — its branches differ by a ^objects hint Splint's
  print drops (per-site disable with the reason at the site).
  db's maybe-wrap-multival three [vs] clauses merged stepwise.
- prefer-method-values (38): every suggestion emits a literal
  CLASS placeholder — not applicable anywhere; excepted per
  file in .splint.edn (6 files) with the reason in the header.
- lisp-case (15): all in js.cljs, which IS DataScript's
  published JS API surface (snake_case is the API) — file
  exception in .splint.edn. single-segment-namespace x3 leaf
  harness namespaces — file exceptions.
- prefer-clj-math: built_ins' per-pass sqrt function value
  carries a per-site disable (clojure.math cannot serve the
  cljs arm); test/drivers/** excepted in .splint.edn (bench
  programs call host Math by design; native_sqrt asserts
  bit-exact Math/sqrt — the interop is its subject).
- predicate x2: is-filtered is public API pinned by
  api_surface_test — per-site disables, no rename.
- conventional-aliases x3: cp -> p renames applied in the
  datafy files (alias + uses).
- catch-throwable (one_uberjar): probe main reports failures
  as data — per-site disable with reason.
- flagship_main let-do: Splint's print flattened a reader-
  conditional body into (do (run) nil); the real body needs
  its do — per-site disable for the misread.
- not-empty?/not-nil?/when-not-empty? x10: (seq x)/(some? x)
  conversions applied in boolean positions.

MEASURED TOOL FACT (banked for the fleet retro): Splint
1.25's local-config rule keys must be qualified SYMBOLS, not
keywords — merge-config filters local rules by
qualified-symbol?, so :lint/... keys are silently dropped and
:excludes never apply. The .splint.edn header records it.

DRIVER CORRUPTIONS FOUND BY THE GATE AND REPAIRED (the
span-matching driver pasted Splint's printed suggestions,
which are not always valid source):
1. inline.clj: lint/assoc-fn rewrote the body of the `update`
   MACRO ITSELF to call `update` — infinite macroexpansion,
   StackOverflowError compiling every consumer of db.cljc.
   File reverted; lint/assoc-fn excepted for inline.clj in
   .splint.edn with the reason (the assoc/get bodies are the
   implementation of update, not a recreation).
2. parser.cljc:29: a suggestion containing (splint/unquote
   tagname) — Splint's printer form for ~tagname — pasted into
   the deftrecord macro. Restored (new ~tagname ...); the
   re-firing style/new-object carries a per-site disable (the
   class is an unquoted macro parameter; Foo. cannot be
   written).
3. query.cljc limit-context: assoc-fn's suggestion passed
   the ->> macro as update's fn value (compiler error:
   Can't take value of a macro). Restored assoc/->> form;
   per-site disable with the reason.
4. db.cljc value-compare: identical-branches matched across
   a #?@ splice boundary and mangled the cond. Repaired by
   hand: one splice carrying the merged clj clause
   ((or (instance? Number x) (instance? Comparable x))
   (compare x y)) plus the unchanged cljs arm — per-pass
   semantics identical.
5. test/upsert.cljc: fn-wrapper applied inside a QUOTED pull
   pattern — the :xform fn literal #(:db/id %) is DATA; the
   suggestion replaced it with the keyword :db/id, which
   resolve-xform then tried to requiring-resolve (27 u3
   errors). Pattern restored; per-site disable with the
   reason at the site. Found by bisect (src halves clean,
   test/eve half guilty), not by the grep audit.
A tree-wide grep for splint/unquote and %N# artifacts found
no other pasted-printer sites.

## Correction to batches 6+7 (Nov 23)

The "KONDO LEG GREEN ... cold-cache" claim was measured with
a warm classpath cache in .clj-kondo/.cache. With the cache
removed, four warnings stood: three files used
clojure.string fully-qualified without requiring it (core,
query_v3, util — pre-existing upstream style, latent
load-order dependency) and parser's rule-vars distinctness
check was dead code — (when-not (concat required* free*)
...) never fires because concat always returns a truthy seq
(kondo: Condition always true). All four are fixed in this
batch: the requires added (util's in its :cljs arm, where
the use lives), and the check revived as
(apply distinct? (concat required* free*)). Cold-cache
clj-kondo --lint src test now reads errors: 0, warnings: 0.

Gate (.lane/gate6d/ + u5 via its documented deps line): all
unit runners baseline-identical — u2 11/79, u3 42/248,
u4 6/48, u5 6/32, bset 5/816, chain-autoclose 5/36. The
gate6/gate6b runs before the repairs are invalid (driver
corruptions above; one also lost to a service restart
mid-u4) and are not banked as evidence.
Remaining for bb lint: Carve (55 vars at recount), dprint
leg, pre-commit hook.

## Step 5 batch 9 — Carve campaign + kondo cache correction (Nov 23)

CARVE LEG GREEN: 154 at census, 55 at recount -> 0 reported
(carve exits 0). Of the 55: 4 deleted as genuinely dead
(query_v3/subarr — its only mention is a commented-out port
block; query_v3/println-context — debug helper, zero refs;
query_v3/singleton-rel — alias def, zero refs, the
->SingletonRelation constructor remains; util/zip — zero
refs). The other 51 are keep-listed in .carve/ignore in six
reasoned groups: Eve-deftype-generated vars (33 + the
unfiltered-db accessor reclassed as public surface),
cljs exception aliases (def Exception js/Error idiom),
public library surface (bset lookup/est-count, query's
normalize-pattern-clause, db's unfiltered-db), util/if+
(used inside util's own macro machinery), entry points
reached by name (browser runner, node mains, run-regressions!,
test-node), and the two sample-query-fn fixtures (referenced
by qualified symbol inside query DATA). Carve's bundled
kondo 2023.01.20 cannot load this repo's hooks (it warns so),
so its call graph is blind to those shapes; the fleet kondo
2026.10.26 with hooks is the analysis of record.
Gate (.lane/gate7/ + u5 documented-deps re-run): all unit
runners baseline-identical.

KONDO CACHE CORRECTION (measured, fresh clone): the step-1
commit tracked .clj-kondo/.cache. Its eve.alpha entry is
partial — Eve's facade re-exports its vars in a shape kondo
records only in part — so a fresh clone of the pushed branch
reported 3 errors / 32 warnings (false unresolved-var on
eve/* calls + degraded type inference in query_v3), while
the same tree with no cache reads 0/0. The session's green
readings used the accumulated working cache. Fix: the cache
is untracked and gitignored, the same disposition as the
Eve and valuescript repos, with the reason written in
.gitignore. Fresh-clone bb lint verification follows in
step 6.
