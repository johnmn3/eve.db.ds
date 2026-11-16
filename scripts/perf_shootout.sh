#!/usr/bin/env bash
# perf_shootout.sh — SHOOTOUT startup numbers for the eve.db.ds code
# in THIS repo (John, 2026-11-11: perf tests run from eve.db.ds).
#
# The operator's shootout definition: cold process, put one datum on
# a disk-backed DataScript DB, cap 10,000 ms — a run over the cap is
# a failure, recorded "10s+". Implemented by the repo's own
# harness/shootout.sh wrapping test/drivers/contract/one.cljc, x5 per
# host, median reported. Hosts: jvm (run-jvm.sh), bb (run-bb.sh),
# nbb (run-nbb.sh), basilisp (run-lpy.sh) — every host's `one` entry
# dispatches to the same contract.one.
#
# Env (set at filing time):
#   DS_DIR     eve.db.ds checkout at the merged tip   (REQUIRED)
#   EVE_DIR    Eve checkout at the merged release tip (REQUIRED)
#   OUT        results dir (default: $DS_DIR/perf-results)
#   HOSTS      default "jvm bb nbb lpy"
#   BB         fleet bb 1.13.226 binary
set -u
OUT="${OUT:-${DS_DIR:-.}/perf-results}"
mkdir -p "$OUT"
CAP_MS=10000
LOAD1=$(cut -d' ' -f1 /proc/loadavg)
echo "PERF-SHOOTOUT load1=$LOAD1 out=$OUT"

verdict () { # median-ms-or-empty -> prints ms or 10s+
  if [ -z "$1" ] || [ "$1" = "none" ]; then echo "10s+";
  elif [ "$1" -gt "$CAP_MS" ]; then echo "10s+ (raw median $1 ms)";
  else echo "$1"; fi
}

if [ -n "${DS_DIR:-}" ] && [ -n "${EVE_DIR:-}" ]; then
  export EVE_LOCAL="$EVE_DIR" EVE_ROOT="$EVE_DIR"
  export BB="${BB:-$HOME/workspace/tools/bin/bb}"
  export SHOOTOUT_WORK="$OUT/shootout-runs"
  mkdir -p "$SHOOTOUT_WORK"
  : > "$OUT/shootout-table.md"
  echo "| host | one-datum cold total, median of 5 (cap 10,000 ms) |" >> "$OUT/shootout-table.md"
  echo "|---|---|" >> "$OUT/shootout-table.md"
  for h in ${HOSTS:-jvm bb nbb lpy}; do
    case "$h" in
      jvm) bash "$DS_DIR/harness/shootout.sh" jvm bash "$DS_DIR/harness/run-jvm.sh" contract.one > "$OUT/shootout-jvm.txt" 2>&1 ;;
      bb)  bash "$DS_DIR/harness/shootout.sh" bb  bash "$DS_DIR/harness/run-bb.sh"  one          > "$OUT/shootout-bb.txt" 2>&1 ;;
      nbb) bash "$DS_DIR/harness/shootout.sh" nbb bash "$DS_DIR/harness/run-nbb.sh" one          > "$OUT/shootout-nbb.txt" 2>&1 ;;
      lpy) bash "$DS_DIR/harness/shootout.sh" lpy bash "$DS_DIR/harness/run-lpy.sh" one          > "$OUT/shootout-lpy.txt" 2>&1 ;;
      *)   echo "UNKNOWN-HOST $h" > "$OUT/shootout-$h.txt" ;;
    esac
    med=$(grep -o 'median-ms=[0-9]*' "$OUT/shootout-$h.txt" 2>/dev/null | tail -1 | cut -d= -f2)
    med=$(verdict "$med")
    echo "| $h | $med |" >> "$OUT/shootout-table.md"
    echo "HOST-DONE host=$h median=$med"
  done
else
  echo "SHOOTOUT-SKIPPED DS_DIR/EVE_DIR not set"
fi
echo "SHOOTOUT-DONE table=$OUT/shootout-table.md load1-end=$(cut -d' ' -f1 /proc/loadavg)"
