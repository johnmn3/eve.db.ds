# STATUS — eve.db.ds syntax formalization (Phase 2 lane)

Branch: `syntax/formalization-1` on johnmn3/eve.db.ds
Base: origin/main `39fdd41e0eaf5622b0ffd202662d5ccb366829e6`
(verified via `git ls-remote` at lane start, Nov 22 2026).
Authorization: John's order Nov 22 2026 ~6:04 PM ET (fleet rollout
to eve.db.ds, eve, cohost, valuescript).

## Steps

- [ ] 1. Fleet templates installed (byte-identical) + kondo cache
- [ ] 2. Census banked in CENSUS.md (before any fixing)
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
