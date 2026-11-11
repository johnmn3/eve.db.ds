# RELEASE-COLLATION — eve.db.ds release/0.1

One line per PROVEN DS-port fix: fix | source commit | proof. A fix is listed only after its proof ran; nothing waiting on proof is here.

| fix | source commit | proof |
|---|---|---|
| (none yet — release/0.1 starts equal to main, the 2cc5e3d5 import) | | |

## Waiting on proof (not in this branch)

- lpy collation + duplicate-`:lpy`-arm fix + `tx-id?` hint fix: atom.broker lane-m/perf-doc-0931-lpyfix `9edd792e` — proof = a vm lpy one-datum run with 5/5 ONE-DONE (vm DISPATCH #54).
- F-BTSET-PLAIN-CMP (BTSet `IPlain` keeps its comparator): lane-m/ds-stage2 `df7b1966`, pin probe `stage2.probe-plain-cmp` — proof = grok DISPATCH #425 (PLAIN-CMP-DONE with 0 fail/err).
- compiled-cljd folds 43/44 (`:cljd` arms, query/core): lane-m/compiled-cljd — proof = grok #424.
