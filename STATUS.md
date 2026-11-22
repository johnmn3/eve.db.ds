# STATUS — eve.db.ds syntax formalization (Phase 2 lane)

Branch: `syntax/formalization-1` on johnmn3/eve.db.ds
Base: origin/main `39fdd41e0eaf5622b0ffd202662d5ccb366829e6`
(verified via `git ls-remote` at lane start, Nov 22 2026).
Authorization: John's order Nov 22 2026 ~6:04 PM ET (fleet rollout
to eve.db.ds, eve, cohost, valuescript).

## Steps

- [x] 1. Fleet templates installed (byte-identical) + kondo cache
- [x] 2. Census banked in CENSUS.md (before any fixing)
- [ ] 3. Baseline test run banked (before formatting)
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
