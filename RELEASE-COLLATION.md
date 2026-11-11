# RELEASE-COLLATION — eve.db.ds release/0.1

## Translation rule (standing)

Incoming fixes come from trees that still carry the library's former name (call it OLD). Before a fix is collated here it is translated, mechanically and scripted: namespaces, requires, fully-qualified symbols and keyword namespaces `OLD.*` -> `eve.db.ds.*`; reader tags `OLD/X` -> `eve.db.ds/X`; source paths (`lib-ds/OLD/` -> `src/eve/db/ds/`); the `OLD-on-eve` dependency coordinate -> `eve.db.ds`. Not translated: `me.tonsky.persistent-sorted-set` and `extend-clj` (separate libraries). No compatibility with the old names (operator ruling). After translating, a case-sensitive grep for OLD over the touched files must come back empty (the README attribution line is the repo's only allowed hit). The translated fix then runs `harness/tools/check-dup-features.py` and the paren-balance check before it is proven.

One line per PROVEN DS-port fix: fix | source commit | proof. A fix is listed only after its proof ran; nothing waiting on proof is here.

| fix | source commit | proof |
|---|---|---|
| F-DS-EVE-WRITERS: `eve.db.ds.db` requires eve.atom/map/set/vec/list (writers must be registered by requiring `eve.db.ds.core` alone) | main `193ceb6c74bca787186ef753b2a191cfaf632123` | `contract.only-core` (requires only eve.db.ds.core, db-with + q): before = ExceptionInfo ':map writer not registered' (codec.cljc:714); after = `ONLY-CORE-OK #{["Ivan"] ["Petr"]} 5`, exit 0; JVM, Clojure 1.12.0, Eve 73a6411 |

## Waiting on proof (not in this branch)

- lpy collation + duplicate-`:lpy`-arm fix + `tx-id?` hint fix: atom.broker lane-m/perf-doc-0931-lpyfix `9edd792e` — proof = a vm lpy one-datum run with 5/5 ONE-DONE (vm DISPATCH #54).
- F-BTSET-PLAIN-CMP (BTSet `IPlain` keeps its comparator): lane-m/ds-stage2 `df7b1966`, pin probe `stage2.probe-plain-cmp` — proof = grok DISPATCH #425 (PLAIN-CMP-DONE with 0 fail/err).
- compiled-cljd folds 43/44 (`:cljd` arms, query/core): lane-m/compiled-cljd — proof = grok #424.
