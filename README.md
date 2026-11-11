# eve.db.ds

**One Datalog codebase. Every host, both planes.**

derived from DataScript (tonsky/datascript), EPL

`eve.db.ds` is the Datalog database ported onto Eve's value substrate
(its upstream is DataScript 1.8.1, by Nikita Prokopov) — the same public
API under the `eve.db.ds.*` namespaces, the upstream test suite as its
conformance bar — maintained here as a
single `.cljc` codebase that spans **both** host sets:

- **Plane A — directly on Eve.** Every Clojure-like Eve supports loads
  this library as-is; host differences are absorbed by Eve's per-seat
  completion layers, never by forking the library. A database is an
  Eve value: it can live in a persistent Eve atom, in a journaled
  domain on disk, shared by other processes — including processes on
  *different host runtimes at once*, all reading back byte-identical
  state (the flagship contract, `test/drivers/contract/ds_flagship.cljc`).
- **Plane B — through cohost.** Every Clojure-like cohost supports
  runs the same library inside a cohost body (stock Clojure `core.clj`
  hosted by the body) over the Eve data backend. Bodies are separate
  processes opening the same Eve domain file; the same database is
  transacted through any body and read back through any other.

One library, written once, as plain `.cljc` — running everywhere both
substrates run. Where it came from and under what license:
[docs/PROVENANCE-DS.md](docs/PROVENANCE-DS.md). The full guide:
[docs/GUIDE.md](docs/GUIDE.md). Measured results, per host per plane:
[RESULTS.md](RESULTS.md).

## Layout

| Path | What |
|---|---|
| `src/` | The library: `eve/db/ds/*` (namespaces `eve.db.ds.*`) and `data_readers.cljc`. Consume this. |
| `lib/` | `me.tonsky/persistent-sorted-set`, the Eve port — DataScript's index substrate. |
| `lib-ds-deps/` | `extend-clj`, the protocol-extension overlay the port builds on. |
| `test/drivers/` | The shipped contract drivers (flagship, one-datum, suites) — the lib's conformance proof. |
| `harness/` | This repo's own drivers: contract check, people-1k perf, shootout runners. |
| `docs/` | Guide, porting notes, provenance, licenses. |

## Consume it

```clojure
;; deps.edn
eve.db.ds/eve.db.ds {:git/url "https://github.com/johnmn3/eve.db.ds"
                     :git/sha "<pin>"}
```

```clojure
(require '[eve.db.ds.core :as d])
(d/db-with (d/empty-db) [{:db/id 1 :name "Ivan"}])
```

Eve itself is a dependency of the library, pinned in
`deps.edn` to the shipped Eve tree
(`SeniorCareMarket/eve` @ 0.9.3.1). Bumping Eve is a one-line pin
change here — never a copy.

## Support matrix

Status as measured in [RESULTS.md](RESULTS.md); a row exists only
where a run backs it, and a failure is recorded with its wall, never
smoothed over.

| Host | Plane A (on Eve) | Plane B (cohost body) |
|---|---|---|
| JVM (Clojure) | see RESULTS | see RESULTS |
| Babashka | see RESULTS | — |
| Basilisk (lpy) | see RESULTS (defect D1) | see RESULTS |
| Jolt | carried from the Eve 0.9.3.1 record | — |
| nbb / ClojureScript | carried from the Eve 0.9.3.1 record | — |
| Glojure | carried from the Eve 0.9.3.1 record | — |
| Jank | carried from the Eve 0.9.3.1 record | — |
| ClojureCLR | carried from the Eve 0.9.3.1 record | — |
| ClojureDart (cljd) | the port is being folded there (separate lane) | — |
| let-go (lg) | Eve seat in progress (separate lane) | — |
| ClojureScript (browser) | persistent writes proven on Node; browser seat not attempted | — |

## The proofs that live here

- **Contract** — `harness/ds_harness/contract_check.cljc`: create →
  transact → query → pull against one journaled disk atom, then a
  fresh-process readback against the same expected values.
- **Shootout** — `harness/shootout.sh`: cold process, one datum on a
  disk-backed DB, cap 10,000 ms, median of 5, shipped form per host.
- **Perf** — `harness/ds_harness/perf.cljc`: people-1k build with all
  seven build keys asserted exact against in-process ground truth,
  then find-datom / pull / datoms-sequence / transact timings.

## Source of this tree

`eve.db.ds` is the home of this library (operator, 2026-11-10: "We'll call it eve.db.ds"). The initial import on `main` (`ea64dfb0`) was the DataScript port tree exactly as staged:
- johnmn3/cohost.cljc, example branch, commit `2cc5e3d5` (the staged port tree, as-is from the hatch box), 89 files.
- That tree is byte-identical to SeniorCareMarket/eve 0.9.3.1's contract-test DataScript set plus the D1 fix (db.cljc :lpy arm, `^Boolean` dropped); Eve is pinned as a git dep at SeniorCareMarket/eve @ `4a921298` (see deps.edn).

The namespaces were renamed to `eve.db.ds.*` (files under `src/eve/db/ds/`) in the commit after the import; there is no compatibility with the old names. `me.tonsky.persistent-sorted-set` and `extend-clj` are separate libraries and keep their names.

Reconciled against, not yet imported (each waits on proof; see RELEASE-COLLATION.md): lane-m/perf-doc-0931-lpyfix `9edd792e`, lane-m/ds-stage2 `df7b1966`, lane-m/compiled-cljd.

License: EPL-1.0 (upstream DataScript); notices preserved in LICENSE and docs/NOTICE-eve-db-ds-epl-1.0.txt.
