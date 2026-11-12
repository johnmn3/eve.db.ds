# Remaining host branches — final state (2026-11-11, last-92 unit)

**Final count: 73 host branches (`scripts/one_eve_counts.py`:
host-branches=73, plain-type-sites=0). All 73 are GLUE. No branch that
touches data, memory, threading, or HTTP remains in eve.db.ds.**
(Corrected from an interim 63: the defrecord-updatable deletion that
produced 63 was reverted — see "Removed in this unit" below.)

This file replaces the 262-state classification wholesale. Every
remaining branch was re-derived at the close-out tree (the old
classification was stale and was not trusted). The full row table
with per-row reasons is in `docs/ONE-EVE-AUDIT.md`, section
"Final close-out (2026-11-11, last-92 unit)". Summary by kind:

- **Loader / require / require-macros wiring** — host EDN reader
  libspecs (core, query), JVM-only files (eve.db.ds.pprint,
  eve.db.ds.inline), bb's eve.host-completion preload, cljs
  :require-macros self-refers (db, parser, query, pull-parser,
  eve-record), the cljs eve.record require (pull-parser).
- **Refer wiring for cljerl** — `#?(:clje [eve.host :refer [...]])`
  in conn, db, query, pull-api, pull-parser, serialize, util:
  clojerl's clojure.core lacks the transient/volatile family; Eve's
  BEAM host supplies them. Other hosts get the same names from
  clojure.core / SCI completion.
- **Class-name / import wiring** — query.cljc's parser-type
  `(:import ...)` (1) and the 16 `(instance? #?(...))` type-name
  branches: eve.deftype classes are named via `:import` on class
  hosts (JVM/CLR) and via the `dp/` alias on SCI hosts. A fold to the
  alias form was attempted in this unit and reverted on measurement
  (`No such var: dp/BindIgnore` on JVM) — see the audit close-out.
- **Type-hint wiring** — serialize's Datom `:import` and per-host
  hint splices; db's patch-tag macro spellings.
- **Print / reader wiring** — Datom and Relation print-methods,
  data-reader registration (core cljs/lpy arms), Datom's
  goog/exportSymbol.
- **Compiler wiring** — `set! *warn-on-reflection*` (db, query),
  `set! *unchecked-math*` around query's sum-rel, db's
  `:refer-clojure :exclude` spelling.
- **Macro wiring (compile-time only)** — db's cljs-env? / if-cljs /
  defn+ family: declared-stub emission and env probing for the seat
  compilers; the fns they define are single portable bodies.
- **Exception type wiring** — db's cljs exception-class aliases and
  the catch-class name splice in cmp-or-incomparable.
- **Protocol-form wiring** — storage's defprotocol option form
  (basilisk cannot read `:extend-via-metadata`).
- **Resolution wiring** — pull-parser's and query's symbol-resolution
  branches: `requiring-resolve` / `resolve` exist on clj/cljr(/lpy);
  other hosts resolve through the built-ins table.

## Eve-gap rows (kept seams — capability missing in Eve, do not fake)

1. `built_ins.cljc:3` — host math library require for the single
   `math/sqrt` call (stddev aggregate). Missing in Eve: a portable
   platform sqrt with IEEE semantics.
2. `core.cljc:9`, `query.cljc:7` — host EDN reader load for
   `read-string` with `*data-readers*`. Missing in Eve: an EDN reader
   honoring data-readers.
3. `db.cljc` (`#?(:clje (extend-type DB clojerl.IAssociative …))`) —
   Missing in Eve: eve.deftype's cljerl backend emitting
   `clojerl.IAssociative` from the Associative protocol group.

## Removed in this unit (88 → 73)

- db.cljc `defrecord-updatable` macro cluster (10) — deleted mid-unit
  as "dead code" on a src/-only usage grep, then RESTORED verbatim:
  the upstream test tree uses it (`test/db.cljc` HashBeef) and its
  absence broke suite loading on JVM and bb. It is tested public
  surface; its 10 branches are GLUE (macro-layer host twins), counted
  in the final 73.
- util.cljc squuid host branches (4) — routed through `eve.uuid`.
- db.cljc combine-cmp / case-tree folds (6) and `:inline` metadata
  branches (3) — one portable form each.
- db.cljc unused `goog.array` require arm (1); conn.cljc `eve.deftype`
  refer made unconditional (1).

Gates at the close-out tree (default deps): contract gates
only-core / lru-portable / transact-realized / serialize-portable PASS
on JVM and bb; the 12-namespace suite matches its pre-unit baseline on
both hosts. Detail: `~/workspace/ds-lanes-pickup/last92-REPORT.md`.
