## Step 5 batch 8 — Splint campaign (this lane, Nov 23)

SPLINT LEG GREEN: 316 at census -> 0 style warnings
(bb splint src test exits 0). Recount before the campaign:
305 findings over ~35 rules (census-raw4/splint.txt). Two
span-matching drivers applied Splint's own suggestions for the
mechanical rules (~180 applications); the rest by hand with
per-site verdicts. Closures of note:

- def-fn in core.cljc x6 converted to defn PRESERVING the
  ^{:tag/:arglists/:doc} metadata (Splint's printed suggestion
  drops the meta); js.cljs x2 hand-written with real arg names
  (Splint prints %1# gensyms, not source).
- identical-branches cond merges applied only where the
  branches are textually identical IN SOURCE: query's t2 if was
  NOT merged — its branches differ by a ^objects hint Splint's
  print drops (per-site disable with the reason at the site).
  db's maybe-wrap-multival three [vs] clauses merged stepwise.
- prefer-method-values (38): every suggestion emits a literal
  CLASS placeholder — not applicable anywhere; excepted per
  file in .splint.edn (6 files) with the reason in the header.
- lisp-case (15): all in js.cljs, which IS DataScript's
  published JS API surface (snake_case is the API) — file
  exception in .splint.edn. single-segment-namespace x3 leaf
  harness namespaces — file exceptions.
- prefer-clj-math: built_ins' per-pass sqrt function value
  carries a per-site disable (clojure.math cannot serve the
  cljs arm); test/drivers/** excepted in .splint.edn (bench
  programs call host Math by design; native_sqrt asserts
  bit-exact Math/sqrt — the interop is its subject).
- predicate x2: is-filtered is public API pinned by
  api_surface_test — per-site disables, no rename.
- conventional-aliases x3: cp -> p renames applied in the
  datafy files (alias + uses).
- catch-throwable (one_uberjar): probe main reports failures
  as data — per-site disable with reason.
- flagship_main let-do: Splint's print flattened a reader-
  conditional body into (do (run) nil); the real body needs
  its do — per-site disable for the misread.
- not-empty?/not-nil?/when-not-empty? x10: (seq x)/(some? x)
  conversions applied in boolean positions.

MEASURED TOOL FACT (banked for the fleet retro): Splint
1.25's local-config rule keys must be qualified SYMBOLS, not
keywords — merge-config filters local rules by
qualified-symbol?, so :lint/... keys are silently dropped and
:excludes never apply. The .splint.edn header records it.

DRIVER CORRUPTIONS FOUND BY THE GATE AND REPAIRED (the
span-matching driver pasted Splint's printed suggestions,
which are not always valid source):

1. inline.clj: lint/assoc-fn rewrote the body of the `update`
   MACRO ITSELF to call `update` — infinite macroexpansion,
   StackOverflowError compiling every consumer of db.cljc.
   File reverted; lint/assoc-fn excepted for inline.clj in
   .splint.edn with the reason (the assoc/get bodies are the
   implementation of update, not a recreation).
2. parser.cljc:29: a suggestion containing (splint/unquote
   tagname) — Splint's printer form for ~tagname — pasted into
   the deftrecord macro. Restored (new ~tagname ...); the
   re-firing style/new-object carries a per-site disable (the
   class is an unquoted macro parameter; Foo. cannot be
   written).
3. query.cljc limit-context: assoc-fn's suggestion passed
   the ->> macro as update's fn value (compiler error:
   Can't take value of a macro). Restored assoc/->> form;
   per-site disable with the reason.
4. db.cljc value-compare: identical-branches matched across
   a #?@ splice boundary and mangled the cond. Repaired by
   hand: one splice carrying the merged clj clause
   ((or (instance? Number x) (instance? Comparable x))
   (compare x y)) plus the unchanged cljs arm — per-pass
   semantics identical.
   A tree-wide grep for splint/unquote and %N# artifacts found
   no other pasted-printer sites.

## Correction to batches 6+7 (Nov 23)

The "KONDO LEG GREEN ... cold-cache" claim was measured with
a warm classpath cache in .clj-kondo/.cache. With the cache
removed, four warnings stood: three files used
clojure.string fully-qualified without requiring it (core,
query_v3, util — pre-existing upstream style, latent
load-order dependency) and parser's rule-vars distinctness
check was dead code — (when-not (concat required* free*)
...) never fires because concat always returns a truthy seq
(kondo: Condition always true). All four are fixed in this
batch: the requires added (util's in its :cljs arm, where
the use lives), and the check revived as
(apply distinct? (concat required* free*)). Cold-cache
clj-kondo --lint src test now reads errors: 0, warnings: 0.
