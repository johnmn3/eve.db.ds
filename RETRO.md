# Retro — eve.db.ds syntax formalization (Phase 2)

Branch `syntax/formalization-1` on johnmn3/eve.db.ds, based on
main `39fdd41` (Eve pin `665bcf84`). Final state: `bb lint`
exit 0 — verified in the lane and in a fresh clone of the
pushed branch. Unit gate baseline-identical at every batch.

## Census → zero

| Leg       | Census                      | Final                          |
| --------- | --------------------------- | ------------------------------ |
| clj-kondo | 918 (248E / 640W / 30I)     | 0 errors, 0 warnings           |
| Splint    | 316                         | 0 style warnings               |
| Carve     | 154 vars (55 at recount)    | 0 reported, exit 0             |
| cljfmt    | 154 / 157 files unformatted | check green; fix twice = no-op |
| dprint    | 27 md files unformatted     | check green                    |

## What the templates/tools got wrong (for the fleet retro)

1. **Splint's local-config rule keys must be qualified
   SYMBOLS, not keywords.** Splint 1.25's merge-config
   filters local rules by `qualified-symbol?`, so a config
   written with `:lint/...` keys loads without error and
   silently applies nothing — every `:excludes` is inert.
   The fleet template's `.splint.edn` should show the symbol
   form in its examples; two lanes can burn hours on a
   config that "looks right." (Found by reading Splint's
   config.clj/path_matcher.clj in the shipped jar.)
2. **Splint's printed suggestions are not source.** Its
   printer emits `(splint/unquote x)` for `~x`, `%1#` for fn
   literal args, drops type hints and metadata, and
   normalizes reader conditionals away. A span-matching
   driver that pastes suggestions verbatim corrupted five
   sites in this repo (see STATUS batch 8): a macro rewritten
   to call itself (infinite macroexpansion), a `->>` passed
   as a function value, a cond merged across a `#?@` splice
   boundary, a printer artifact pasted into a macro, and a
   fn literal rewritten inside quoted DATA (a pull pattern).
   Fleet guidance: suggestion-driven edits need a
   form-level applier that refuses suggestions containing
   printer artifacts, and every batch needs the repo gate —
   three of the five compiled clean or failed far from the
   edit site.
3. **A committed .clj-kondo/.cache is a liability.** This
   repo's step-1 cache held a partial eve.alpha entry (the
   facade re-exports its vars in a shape kondo records only
   in part); fresh clones then reported false
   unresolved-var warnings (3E / 32W) where the same tree
   with no cache reads 0 / 0. Worse, the stale cache masked
   four real warnings during the campaign (three missing
   clojure.string requires and a dead validation check),
   because the session's accumulated working cache differed
   from the committed one. Disposition taken: untrack +
   gitignore the cache (the Eve and valuescript repos do
   the same). Fleet guidance: never commit the cache; if a
   repo has it tracked, expect verdicts to depend on whose
   cache is warm, and take final readings from a fresh
   clone.
4. **Kondo verdicts are analysis-order sensitive in the
   presence of duplicate namespaces and ns-less scripts.**
   test/ and test/upstream-ds/ define the same namespaces;
   ns-less driver scripts share kondo's implicit `user` ns.
   Both produce findings that appear or vanish depending on
   cache/order (duplicate-require, unresolved-var on a var
   defined in the shadowed copy). The campaign's
   disposition — per-site ignores naming the artifact — is
   the honest one; a fleet note should name the pattern so
   the next lane doesn't chase them as defects.
5. **Carve's bundled kondo (2023.01.20) cannot load modern
   hooks** and warns so; its unused-var report on a
   hook-heavy repo is a starting list, not a verdict. Of
   55 recounted vars here: 4 dead (deleted), 51 kept with
   reasons in .carve/ignore (deftype-generated names,
   entry points, query-data fixtures, public surface).

## Real defects the campaign found

- **parser.cljc rule-vars check was dead code**:
  `(when-not (concat required* free*) …)` never fires —
  concat always returns a truthy seq — so duplicate rule
  variables were never rejected. Revived as
  `(apply distinct? (concat required* free*))`. Kondo's
  "Condition always true" was the discriminator.
- **Three files used clojure.string fully-qualified
  without requiring it** (core, query_v3, util) — a latent
  load-order dependency inherited from upstream style.
  Requires added (util's in its :cljs arm, where the use
  lives).
- pull_parser's `:eles` typo for `:else` in a case
  (batch 4) — same semantics, wrong spelling, fixed.

## Notes for the merge review

- `.splint.edn` carries four named exceptions
  (prefer-method-values per-file, lisp-case for the JS API
  surface file, single-segment-namespace for leaf harness
  namespaces, prefer-clj-math for test/drivers,
  assoc-fn for inline.clj) — each with its reason in the
  header.
- The branch does NOT merge the Nov 23 ds/Eve collation
  that landed on main after this lane's base; it is based
  on `39fdd41` throughout, per orders.
- The pre-commit hook is enabled per clone
  (`git config core.hooksPath .githooks`); it is not
  self-installing.
