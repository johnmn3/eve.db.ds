# API var list — the parity surface

The public vars of upstream `datascript.core` at DataScript 1.8.1
(tag 1.8.1, commit 34915cf673c02cbc64779fe56e0288d70fea63f8), as
vendored at `src/eve/db/ds/core.cljc`. This is the surface the
DS-in-Eve rewrite must expose (DS-IN-EVE-PLAN v2 §1, §7/U0): every
var below, under the `eve.db.ds.core` namespace, with upstream
semantics — plus the rewrite's additive vars (`keep!`, `release!`,
`close-conn!` per v2 §4 / v1 §5.2), which are not upstream's and so
are not listed here.

Derived mechanically (U0): scan of top-level `(def|defn|defmacro|
defonce|defmulti` forms in `src/eve/db/ds/core.cljc`, skipping
metadata forms between the kind token and the name. Reconciliation:
45 top-level defs in the file = 44 public vars below + 1 private
var (`last-tempid`, line 597, `^:private`, excluded). Line numbers
are lines in the vendored file.

| Var | Kind | Line |
|---|---|---|
| `conn-from-datoms` | def | 451 |
| `conn-from-db` | def | 447 |
| `conn?` | def | 443 |
| `create-conn` | def | 455 |
| `data-readers` | def | 583 |
| `datom` | def | 182 |
| `datom?` | def | 191 |
| `datoms` | defn | 283 |
| `db` | defn | 619 |
| `db-with` | def | 272 |
| `db?` | def | 178 |
| `empty-db` | defn | 151 |
| `entid` | def | 79 |
| `entity` | def | 27 |
| `entity-db` | defn | 85 |
| `filter` | defn | 247 |
| `find-datom` | defn | 360 |
| `from-serializable` | def | 221 |
| `index-range` | defn | 413 |
| `init-db` | defn | 195 |
| `is-filtered` | defn | 242 |
| `listen!` | def | 567 |
| `pull` | def | 106 |
| `pull-many` | def | 120 |
| `q` | def | 134 |
| `reset-conn!` | def | 560 |
| `reset-schema!` | def | 564 |
| `resolve-tempid` | defn | 612 |
| `rseek-datoms` | defn | 405 |
| `schema` | def | 235 |
| `seek-datoms` | defn | 368 |
| `serializable` | def | 207 |
| `settings` | defn | 740 |
| `squuid` | def | 679 |
| `squuid-time-millis` | def | 685 |
| `tempid` | defn | 599 |
| `touch` | def | 91 |
| `transact` | defn | 627 |
| `transact!` | def | 473 |
| `transact-async` | defn | 667 |
| `tx0` | def | 21 |
| `unlisten!` | def | 576 |
| `with` | def | 268 |
| `with-schema` | defn | 276 |

Notes:

- `tx0` (line 21) carries `^:no-doc` upstream. It is a public var
  of the namespace and is listed; whether the rewrite documents it
  is U7's call, not a parity question.
- Upstream `datascript.core` is the only API namespace at 1.8.1;
  there is no separate public `datascript.conn` surface — conn
  vars (`create-conn`, `transact!`, `listen!`, …) live here.
