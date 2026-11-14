#!/usr/bin/env bash
# perf_runtime.sh — RUNTIME numbers for the new eve.db.ds code.
#
# Wraps this repo's OWN harness (harness/ds_harness/perf.cljc,
# "people-1k": build 10x100 swap!s, find-datom x1000, pull x1000,
# datoms-seq, transact 100 single-entity commits; keys asserted exact
# against an in-process ground truth before any timing prints).
# Wrap, don't rewrite: all measurement logic is the repo's.
#
# Hosts: jvm (harness/run-jvm.sh), bb (harness/run-bb.sh),
# nbb (harness/run-nbb.sh), basilisp (harness/run-lpy.sh) — one entry
# per host, all dispatching to the same ds-harness.perf.
#
# Eve-vs-stock: scripts/ds_bench.sh drives the in-repo driver family
# (test/drivers/contract/ds_bench.cljc + ds_bench_stock.cljc +
# ds_bench_table.cljc), reproducing Eve doc/performance.md section 1
# — five write shapes at 500 and 1,000 people, Eve arm = this repo
# on a journaled persistent domain, stock arm = upstream DataScript
# 1.8.1 in a heap atom, arms in separate processes, cells medianed
# across BENCH_REPS fresh processes (default 3, the doc's
# discipline). The stock bb column stays GAPPED (stock on bb needs
# the special native bb build; PLAN gap 2).
#
# Env (set at filing time to the MERGED tips):
#   DS_DIR    eve.db.ds checkout            (default: this script's repo root)
#   EVE_DIR   Eve checkout at the merged release tip (REQUIRED)
#   OUT       results dir                   (default: $DS_DIR/perf-results)
#   HOSTS     subset of: jvm bb nbb lpy     (default: "jvm bb nbb lpy")
#   BB        fleet bb 1.13.226 binary      (default: ~/workspace/tools/bin/bb)
#   BENCH     "off" skips the ds-bench block (default: on)
#   BENCH_SIZES  sizes for ds-bench         (default: "500 1000")
#   PERF_LOAD_GATE  abort if load1 > 8 at start (default: on; "off" overrides)
set -u
DS_DIR="${DS_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
EVE_DIR="${EVE_DIR:?EVE_DIR must name the merged Eve checkout}"
OUT="${OUT:-$DS_DIR/perf-results}"
HOSTS="${HOSTS:-jvm bb nbb lpy}"
export EVE_LOCAL="$EVE_DIR"          # harness/run-jvm.sh + run-bb.sh read EVE_LOCAL/EVE_ROOT
export EVE_ROOT="$EVE_DIR"           # eve_dev_cp.sh honors EVE_ROOT over the :dev pin
export BB="${BB:-$HOME/workspace/tools/bin/bb}"
export PYTHONPYCACHEPREFIX="${PYTHONPYCACHEPREFIX:-$OUT/lpycache}"
mkdir -p "$OUT"
LOAD1=$(cut -d' ' -f1 /proc/loadavg)
echo "PERF-RUNTIME ds=$DS_DIR eve=$EVE_DIR load1=$LOAD1 hosts=[$HOSTS]"
if [ "${PERF_LOAD_GATE:-on}" != "off" ] && awk "BEGIN{exit !($LOAD1 > 8)}"; then
  echo "LOAD-HOLD load1=$LOAD1 > 8 — perf rows on a loaded box are not numbers; aborting"
  exit 3
fi
[ -f "$EVE_DIR/hosts.edn" ] || { echo "EVE-TREE-INVALID $EVE_DIR (no hosts.edn)"; exit 1; }
[ -f "$DS_DIR/harness/ds_harness/perf.cljc" ] || { echo "DS-TREE-INVALID $DS_DIR (no harness/ds_harness/perf.cljc)"; exit 1; }

run_host () { # host -> writes $OUT/runtime-<host>.txt, echoes a table row
  local host="$1" log="$OUT/runtime-$1.txt" row=""
  : > "$log"
  case "$host" in
    jvm)
      timeout 3600 bash "$DS_DIR/harness/run-jvm.sh" ds-harness.perf "$OUT/perf-jvm" jvm >>"$log" 2>&1
      row=$(grep -o '{:label.*}' "$log" | tail -1) ;;
    bb)
      timeout 3600 bash "$DS_DIR/harness/run-bb.sh" perf "$OUT/perf-bb" bb >>"$log" 2>&1
      row=$(grep -o '{:label.*}' "$log" | tail -1) ;;
    nbb)
      timeout 3600 bash "$DS_DIR/harness/run-nbb.sh" perf "$OUT/perf-nbb" nbb >>"$log" 2>&1
      row=$(grep -o '{:label.*}' "$log" | tail -1) ;;
    lpy)
      timeout 3600 bash "$DS_DIR/harness/run-lpy.sh" perf "$OUT/perf-lpy" lpy >>"$log" 2>&1
      row=$(grep -o '{:label.*}' "$log" | tail -1) ;;
    *) echo "UNKNOWN-HOST $host" >>"$log"; row="UNKNOWN" ;;
  esac
  echo "| $host | $row |" >> "$OUT/runtime-table.md"
  echo "LEG-DONE host=$host log=$log"
}

: > "$OUT/runtime-table.md"
echo "| host | ds-harness.perf EDN row (people-1k, keys-exact gate first) |" >> "$OUT/runtime-table.md"
echo "|---|---|" >> "$OUT/runtime-table.md"
for h in $HOSTS; do run_host "$h"; done

# ---------- Eve-vs-stock bench (doc/performance.md section 1 shapes) ----------
if [ "${BENCH:-on}" != "off" ]; then
  DS_DIR="$DS_DIR" EVE_DIR="$EVE_DIR" OUT="$OUT" \
    BENCH_SIZES="${BENCH_SIZES:-500 1000}" BENCH_REPS="${BENCH_REPS:-3}" \
    timeout 10800 bash "$DS_DIR/scripts/ds_bench.sh" > "$OUT/bench-run.txt" 2>&1
  echo "BENCH-DONE log=$OUT/bench-run.txt table=$OUT/bench-table.md (stock bb column GAPPED; PLAN gap 2)"
fi
echo "RUNTIME-DONE table=$OUT/runtime-table.md load1-end=$(cut -d' ' -f1 /proc/loadavg)"
