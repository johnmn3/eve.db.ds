# RELEASE-COLLATION — eve.db.ds release/0.1

## Translation rule (standing)

Every incoming fix comes from trees that still use the old names. Before it is collated here it is translated, mechanically and scripted: `datascript.*` -> `eve.db.ds.*` (namespaces, requires, fully-qualified symbols, keyword namespaces such as `:datascript.db/cmp-eavt`), `datascript/X` reader tags -> `eve.db.ds/X`, `datascript/<file>` source paths -> `eve/db/ds/<file>` (lib-ds/datascript/ -> src/eve/db/ds/), `datascript-on-eve` coordinates -> `eve.db.ds`. Not translated: `me.tonsky.persistent-sorted-set` and `extend-clj` (separate libraries). There is no compatibility with the old names (operator ruling). After translating, `grep datascript` over the touched files must show only the README attribution line. The translated fix runs check-dup-features.py and the paren-balance check before it is proven.

One line per PROVEN DS-port fix: fix | source commit | proof. A fix is listed only after its proof ran; nothing waiting on proof is here.

| fix | source commit | proof |
|---|---|---|
| (none yet — release/0.1 starts equal to main, the 2cc5e3d5 import) | | |

## Waiting on proof (not in this branch)

- lpy collation + duplicate-`:lpy`-arm fix + `tx-id?` hint fix: atom.broker lane-m/perf-doc-0931-lpyfix `9edd792e` — proof = a vm lpy one-datum run with 5/5 ONE-DONE (vm DISPATCH #54).
- F-BTSET-PLAIN-CMP (BTSet `IPlain` keeps its comparator): lane-m/ds-stage2 `df7b1966`, pin probe `stage2.probe-plain-cmp` — proof = grok DISPATCH #425 (PLAIN-CMP-DONE with 0 fail/err).
- compiled-cljd folds 43/44 (`:cljd` arms, query/core): lane-m/compiled-cljd — proof = grok #424.
