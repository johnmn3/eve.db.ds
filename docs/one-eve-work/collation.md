# #56 six-worker collation

Base: `22ea73167aae5e61588a21a562c97ac26f30dd60`.

All six worker tips are incorporated by non-fast-forward merges in order:
`06895c0b95`, `7b0b3ed975`, `13016661c2`, `ce98c10fa0`,
`5312877d0a`, `bcf71f8853`. Their per-row logs remain in this directory.

## Integration decisions

- No textual merge conflicts. The DB front/back changes merge automatically;
  w1's deletion of the separate `:clje defcomp` twin wins, leaving a single
  portable `defcomp` definition. No reinstated twin.
- `diff-sorted` now calls the public `cmp-or-incomparable` helper introduced
  by w1, removing w2's duplicate reader-conditional catch site.
- **Supersedes w4's Newton sqrt entry:** Eve at the development pin
  `ec22aba4c0ddf4f2ff58f26b04d654d53f247d61` has no floating-point sqrt in
  `eve.platform` or `eve.host`. Use one native `math/sqrt` call instead,
  with namespace-only glue: `clojure.math` on JVM/bb, `cljs.math` on nbb,
  Python `math` on basilisp. These delegate to native square root rather
  than an iterated approximation. The sole new host branch is require/import
  wiring for that alias; there is no host-specific numerical body.
- Existing pending design-call rows remain untouched.

## Counts

`python3 scripts/one_eve_counts.py 22ea73167a`:

```
host-branches=263
plain-type-sites=0
```

Collated tree after integration:

```
host-branches=106
plain-type-sites=0
```

The six workers' raw merge has 106 sites. Sharing the comparison catch removes
one; exact native-math namespace glue adds one. Net 157 host branches removed
from this batch's base. This is an inventory reduction, not a claim that every
remaining branch has cleared the final one-Eve audit.

## Exact square-root regression

`contract.native-sqrt` checks all eight IEEE-754 bytes, not formatted decimal
strings or an epsilon comparison. JVM `Math/sqrt` independently verifies the
literal expected bytes in the same driver.

- `sqrt(2.0)`: `0x3ff6a09e667f3bcd`.
- Variance of `[1.0 2.0 4.0 8.0]`: exactly `7.1875`.
- Its standard deviation: `0x400572969b8b5cd8`.

## Gate protocol

All runs use the shared `flock /workspace/ds-wt/one-eve-workers/test.lock`;
`df -Pk /workspace` is checked first. Logs for the final source tree:
`/tmp/collated-final-gates/all.log`. Only quick contracts and sqrt regression
are run here. The coordinator files the full four-host DataScript suite;
no grok job was posted during collation.

Final tree gate results (all PASS):

| Host | only-core | lru-portable | transact-realized | native-sqrt bits |
|---|---|---|---|---|
| JVM | PASS | PASS | PASS | PASS |
| bb | PASS | PASS | PASS | PASS |
| nbb | PASS | PASS | PASS | PASS |
| basilisp | PASS | PASS | PASS | PASS |

No failed test invocation during collation. The final run exits 0.
