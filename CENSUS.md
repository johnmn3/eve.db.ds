# CENSUS — eve.db.ds at base 39fdd41 (before any fixing)

Measured Nov 22 2026 on branch syntax/formalization-1 after step 1
(templates installed), tools: clj-kondo 2026.10.26, Splint 1.25.0
(bb 1.13.230), Carve 0.3.5, cljfmt 0.16.6. Raw outputs in
census-raw/ (kondo.txt, kondo.json, splint.txt, carve.txt,
cljfmt.txt). Scope: `src test` = 157 .clj/.cljc/.cljs files (kondo
counted 160 incl. deps.edn and data_readers). The tree has three
scopes: src (22 files, the library), test-other (drivers, unit
tests, runners), test/upstream-ds (the vendored renamed DataScript
1.8.1 test suite — this repo's own acceptance suite, run by
contract.ds-suite).

## clj-kondo — 918 findings: 248 errors / 640 warnings / 30 info

By scope: src 443, upstream-ds 283, test-other 192.

| count  | rule                                                                                           |
| ------ | ---------------------------------------------------------------------------------------------- |
| 208    | unresolved-symbol                                                                              |
| 177    | unused-binding                                                                                 |
| 134    | unresolved-var                                                                                 |
| 128    | unused-referred-var                                                                            |
| 96     | unused-namespace                                                                               |
| 20     | aliased-referred-var (info)                                                                    |
| 18     | unresolved-namespace                                                                           |
| 17     | unused-import                                                                                  |
| 16     | unexpected-recur (error)                                                                       |
| 14     | non-arg-vec-return-type-hint                                                                   |
| 11     | invalid-arity (error)                                                                          |
| 11     | unused-value                                                                                   |
| 9      | type-mismatch                                                                                  |
| 8      | unused-private-var                                                                             |
| 8      | unknown-map-key                                                                                |
| 7      | redefined-var                                                                                  |
| 7      | missing-test-assertion                                                                         |
| 5      | missing-protocol-method                                                                        |
| 5      | redundant-str-call (info)                                                                      |
| 4      | private-call (error)                                                                           |
| 3      | redundant-let                                                                                  |
| 2      | unresolved-protocol-method                                                                     |
| 2      | unused-excluded-var                                                                            |
| 2      | redundant-nested-call                                                                          |
| 1 each | cond-else, redundant-do, uninitialized-var, deps.edn, duplicate-require, is-message-not-string |

Worst files: src/eve/db/ds/db.cljc 211, parser.cljc 56,
pull_api.cljc 41, query_v3.cljc 34, query.cljc 29.

Known shape notes (to be re-derived per class in the fix campaign,
not assumed): this is a portable .cljc codebase — the media lane's
RETRO items 4–5 (kondo unused-* miscounts #? reader-conditional
references; dual clj/cljs passes create phantom unresolved symbols
in single-language .cljc files) predict a large false-positive
share in unused-binding / unused-referred-var / unresolved-symbol.
The unexpected-recur and invalid-arity errors need per-site
verdicts: real defect or macro-model gap.

## Splint — 316 warnings, 157 files

Coverage note: `bb splint` with no paths checked only 73 files
(170 warnings) on this repo — Splint's default path discovery
misses test/upstream-ds and parts of test/. The gate task therefore
names the paths (`bb splint src test`), the same adaptation the
kondo/cljfmt tasks already make. Census of record is the explicit
run: src 133, test 183 (upstream-ds 71 of them).

By rule (305 of 316 parsed to rule ids; raw file banked):
44 style/is-eq-order, 38 lint/prefer-method-values, 30 style/eq-nil,
26 style/plus-one, 21 style/eq-zero, 15 naming/lisp-case,
11 lint/identical-branches, 11 style/prefer-clj-math,
11 lint/body-unquote-splicing, 9 lint/assoc-fn, 8 style/def-fn,
8 lint/fn-wrapper, 6 lint/if-nil-else, 6 lint/redundant-call,
5 style/pos-checks, 5 lint/into-literal, 5 lint/redundant-str-call,
4 lint/if-else-nil, 4 style/single-key-in, 3 style/prefer-condp,
3 naming/conventional-aliases, 3 lint/if-not-both,
3 naming/single-segment-namespace, 3 style/redundant-let,
2 naming/predicate, 2 style/neg-checks, 2 style/prefer-for-with-literals,
2 style/eq-true, 2 style/eq-false, 1 each: lint/if-same-truthy,
style/apply-str-interpose, style/new-object, style/when-not-call,
style/cond-else, style/not-some-pred, style/prefer-clj-string,
style/useless-do, style/apply-str, style/let-do,
lint/catch-throwable, style/redundant-nested-call,
style/multiple-arity-order.

## Carve — 154 vars reported (exit 1)

By scope: src 15, test-other 43, upstream-ds 96. Carve 0.3.5
analyzes with its own bundled clj-kondo 2023.01.20 pod (media RETRO
item 7) — its verdicts are computed by a different analyzer than
the gate's kondo leg; each var gets a per-var verdict in the fix
campaign (delete / keep in .carve/ignore with reason). Many
upstream-ds and driver vars are entry points reached by name
(runners, harness mains), not by static calls.

## cljfmt — 154 of 157 files formatted incorrectly

Exit 1, "154 file(s) formatted incorrectly". The 3 clean files are
already fleet-format. (Census taken with the v1 template; the
format pass runs under the corrected v2 template with
:sort-ns-references? false — see STATUS.)
