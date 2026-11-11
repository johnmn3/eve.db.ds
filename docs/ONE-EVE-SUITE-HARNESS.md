# Upstream suite harness

No upstream assertion bodies were edited. `test.core` and `test.cljs` are
adapted as helpers: optional console printing, explicit `clojure.walk`, a
Basilisp thunk fixture, and runtime `thrown-msg?` registration on nbb/Basilisp.

bb 1.13.226 and nbb 1.6.214 already bundle real `cognitect.transit`. The #482
nbb cascade begins at `enable-console-print!`, not Transit. Basilisp has no
Transit implementation on this classpath: its helper omits that require and
throws if accidentally called. The driver counts whole tests `serialize` and
`test-nan` in `eve.db.ds.test.serialize` as SKIP on Basilisp. This reduces
coverage and is not a claim that those tests pass.

`scripts/eve_dev_cp.sh` adds explicit test-host support roots. nbb gets the
missing datafy/nav protocols with metadata defaults. Basilisp gets an empty
`eve.db.ds.test` package anchor to avoid the upstream eager whole-suite
aggregator defeating namespace isolation; the driver adds the real vendored
namespace directories to the package search paths.

Use `scripts/ds_suite.py HOST --timeout SECONDS --out PATH` on a venue. Each
namespace runs in a fresh process; timeout kills its process group and continues
to the next namespace. Every row has pass/fail/error/timeout, exit code, elapsed
seconds, loaded/test/assertion counts and skips. Missing summary is an error.
`--load-only` executes zero test vars and loads all host-applicable namespaces.

`scripts/ds_suite_grok.sh` resolves Eve's :dev pin and builds the addon, then runs
all four legs regardless of prior reds. Limits are 180 seconds per namespace
JVM/bb/nbb and 300 seconds Basilisp. nbb's cwd is the addon tree. Basilisp Lisp
cache reuse is disabled so assertion instrumentation is recompiled per process;
Python bytecode writes are disabled to avoid inode pressure.

Disk checks fail closed below 4 GiB free, if 3 GiB exceeds half of free, or below
1024 free inodes (the venue launcher requires 50,000). Local checks must use the shared test.lock; never run a local
full DataScript suite. Harness-only regression tests:
`python3 -m unittest discover -s test/harness -v`.
