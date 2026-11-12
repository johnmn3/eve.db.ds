#!/bin/bash
# eve.db.ds harness — stock-arm runner for the Eve-vs-stock bench.
# Usage: run-bench-stock.sh [size...]
# Runs contract.ds-bench-stock (upstream DataScript 1.8.1 in a heap
# atom) from its isolated deps context (test/bench-stock/), where the
# real persistent-sorted-set jar is present and eve.db.ds is not.
# See test/drivers/contract/ds_bench.cljc for why the arms split.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/../test/bench-stock" || exit 9
export CLJ_CONFIG="${CLJ_CONFIG:-$HOME/workspace/eve-env/cljconfig}"
exec clojure -M -m contract.ds-bench-stock "$@"
