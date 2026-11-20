# Laws results — deftype/atom probe at the pre-fix Eve pin

Test: `test/eve/deftype_atom_laws_test.cljc` (EVE-FIX-PLAN §4 probe,
one deftest per row E1–E8, eve.alpha only).
Pin: SeniorCareMarket/eve `release/eve-0.9.3.3` @
f4a1c3ae97ee76f2651ce600a857675aa5c227ef (deps.edn).
Host: JVM (OpenJDK 21), this box — a throttled shared cell, so all
numbers are smoke-class. Run 2026-11-19:

```
clojure -M:laws -e "(require 'eve.deftype-atom-laws-test)
                    (clojure.test/run-tests 'eve.deftype-atom-laws-test)"
→ Ran 8 tests containing 16 assertions. 2 failures, 0 errors.
```

## Row results

| # | Row | Result |
|---|---|---|
| E1 | `@a` returns the deftype value | **GREEN** |
| E2 | `add-watch` + `swap!` | **GREEN** (watch fired once, delivered new value reads correctly) |
| E3 | `compare-and-set!` | **RED** — returns `false`, no throw; the atom keeps the old value |
| E4 | `reset!` with a deftype built inside a swap | **GREEN** |
| E5 | `($ …)` value still correct after 1,000 swaps | **GREEN** — and verified flat, see below |
| E6 | `eve/same?` | **PENDING** — `eve/same?` does not exist in eve.alpha at this pin (EVE-FIX F3); the deftest self-reports pending and passes vacuously |
| E7 | old root kept beside new one stays readable | **GREEN** |
| E8 | transact flow never uses `swap!`'s return | **GREEN** |

## The E3 mechanism (measured, not inferred)

`compare-and-set!` cannot take on this atom shape: deref delivers a
fresh copy on every read, so a delivered "old" value never
identity-matches the stored root. Discriminator run (same pin,
same JVM): two derefs of one atom — maps `identical?` false, the
nested Boxes `identical?` false; two `($ …)` reads likewise not
identical, and a `$` read is not identical to a deref. This is
consistent with DS-IN-EVE v2 §2, which already bans
`compare-and-set!` on the conn's atom for this reason.

## Correction to EVE-FIX-PLAN §0 (banked per the defect rule)

§0's table — E1–E6 red at this pin, E1–E4 throwing "flat chunk: a
value held by reference cannot be packed" — **does not reproduce
at its own cited pin on the JVM.** Re-derived firsthand
2026-11-19 at f4a1c3a (fresh JVM runs; tree:
`/root/.gitlibs/libs/eve/eve/f4a1c3ae…`, the checkout tools.deps
resolved for this repo's pin):

- The §4 probe as specified (map holding one deftype, built inside
  `swap!`): E1, E2, E4, E5, E7, E8 green; only E3 red, in the
  altered form above (false, not throw).
- Field content is not the discriminator: Boxes holding a long, a
  string, a host map and a host vector all deref cleanly.
- Shape is not the discriminator: the atom's whole value being the
  deftype, a deftype inside a deftype, a deftype holding a vector
  of deftypes, and a deftype holding an Eve hash-map of deftypes
  all deref cleanly.
- E5's green is real flat delivery, not an unpinned pointer kept
  alive by non-reclamation: repeated `$`/deref reads are never
  `identical?` (above), i.e. each read is a fresh copy.
- The refusal path itself exists in the tree
  (`src/eve/flat/chunk.cljc`:190 — it fires when a chunk body
  begins with the 0x15 by-reference marker). None of the §4 probe
  shapes hit it. What shape does is the Eve seat's question to
  reconcile against their probe; this file is the shared
  instrument (EVE-FIX §4) and it is green on E1/E2/E4/E5 here.
- E6 stands as §0/F3 describe, in the only form checkable today:
  the fn is absent. Verified two ways — `(resolve
  'eve.alpha/same?)` is nil in the laws run (the run's 16
  assertions include E6's single vacuous one, not the two real
  ones), and `grep same? src/eve/alpha.cljc` at the pin tree is 0
  hits.

Provenance: Eve tree f4a1c3ae97ee76f2651ce600a857675aa5c227ef
(release/eve-0.9.3.3 tip), JDK 21 (~/workspace/tools/jdk-21),
clojure CLI ~/.local/bin, CLJ_CONFIG=~/workspace/eve-env/cljconfig,
fresh-staging JVM runs of the committed test file plus three
discriminator probes (field shapes, nesting shapes, identity of
repeated reads), 2026-11-19.

## Not measured by U0

- The browser (ClojureScript) leg of the laws — no browser test
  runner is wired in this repo yet; the plan's U0 gate needs it at
  the FIXED pin, which does not exist yet.
- A disk (`:persistent`) atom — §0 likewise left it unmeasured.
- EVE-FIX F5 (lifetime of values built outside every `swap!`) —
  U3's dependency, not probed here.

## Coordinator addendum — fixed pin 323bfe3 (2026-11-19, ~20:20 ET)

At Eve release/eve-0.9.3.3 tip 323bfe35901f3f892921eecd41fc5e85be4ef8ec
(the pin in deps.edn), after the Eve seat's fixes landed:

- JVM, committed test file as amended below: **8 tests, 17
  assertions, 0 failures, 0 errors.** E6 green against the real
  eve/same? (F3 landed in alpha). E3 passes at this pin.
- E1 amended per the Eve seat's ruling (2026-11-19): a delivered
  deftype value is the flat sister type, not the host class; the
  row now asserts the delivered value answers the type's protocols
  (satisfies? + the protocol fn) instead of instance? of the host
  class. Before the amendment the same run read 16 pass / 1 fail,
  the failure being exactly that instance? line.
- Browser leg at this pin (browser-harness lane, real Chromium):
  8 tests, 16 pass, 1 fail, 0 errors — the fail was the same E1
  instance? line, pre-amendment; host parity with the JVM. The
  amended file's browser run is recorded when it completes.
