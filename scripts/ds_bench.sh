#!/usr/bin/env bash
# ds_bench.sh — the Eve-vs-stock bench (Eve doc/performance.md
# section 1 shapes) for the new eve.db.ds code, run FROM this repo.
#
# Drives the three in-repo pieces (test/drivers/contract/):
#   contract.ds-bench        Eve arm   (harness/run-jvm.sh, this repo + Eve tree)
#   contract.ds-bench-stock  stock arm (harness/run-bench-stock.sh, isolated deps)
#   contract.ds-bench-table  merge: medians across reps + the doc's tables
# The arms are separate fresh processes (see ds_bench.cljc for why no
# single classpath serves both). BENCH_REPS defaults to 3, the doc's
# medians-of-3 discipline for write cells.
#
# Env:
#   DS_DIR   eve.db.ds checkout   (default: this script's repo root)
#   EVE_DIR  Eve checkout at the pin under measurement (REQUIRED)
#   OUT      results dir          (default: $DS_DIR/perf-results)
#   BENCH_SIZES  (default: "500 1000")
#   BENCH_REPS   (default: 3)
set -u
DS_DIR="${DS_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
EVE_DIR="${EVE_DIR:?EVE_DIR must name the Eve checkout}"
OUT="${OUT:-$DS_DIR/perf-results}"
SIZES="${BENCH_SIZES:-500 1000}"
REPS="${BENCH_REPS:-3}"
export EVE_LOCAL="$EVE_DIR" EVE_ROOT="$EVE_DIR"
mkdir -p "$OUT"
EVE_FILES=""; STOCK_FILES=""
for rep in $(seq 1 "$REPS"); do
  BD="$OUT/bench-domains-rep$rep"
  rm -rf "$BD"
  EF="$OUT/bench-eve-rep$rep.txt"; SF="$OUT/bench-stock-rep$rep.txt"
  echo "DS-BENCH rep $rep/$REPS sizes=[$SIZES] domain=$BD"
  timeout 3600 bash "$DS_DIR/harness/run-jvm.sh" contract.ds-bench "$BD" $SIZES > "$EF" 2>&1
  grep -E '^\{:arm' "$EF" > "$EF.rows" || true
  timeout 3600 bash "$DS_DIR/harness/run-bench-stock.sh" $SIZES > "$SF" 2>&1
  grep -E '^\{:arm' "$SF" > "$SF.rows" || true
  EVE_FILES="${EVE_FILES:+$EVE_FILES,}$EF.rows"
  STOCK_FILES="${STOCK_FILES:+$STOCK_FILES,}$SF.rows"
done
timeout 600 bash "$DS_DIR/harness/run-jvm.sh" contract.ds-bench-table "$EVE_FILES" "$STOCK_FILES" > "$OUT/bench-merged.txt" 2>&1
grep -E '^\*\*@|^\|' "$OUT/bench-merged.txt" > "$OUT/bench-table.md" || true
echo "DS-BENCH-DONE reps=$REPS table=$OUT/bench-table.md merged=$OUT/bench-merged.txt"
