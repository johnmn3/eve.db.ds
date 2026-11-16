#!/bin/bash
# The shootout (the operator's definition): cold process, put one
# datum on a disk-backed DataScript DB, cap 10,000 ms — a run over
# the cap is a failure, recorded "10s+". Runs happen back to back,
# each in a fresh domain, wall-clock per run; the table reports the
# median of the runs and the protocol verdict at the cap. Raw per-run
# times are printed whatever they are — the box and its load are part
# of the row's label, and an over-cap completion time is recorded
# beside its "10s+" verdict, never hidden by it.
# Usage: shootout.sh <label> <command...>   (the command gets the
# fresh domain prefix appended as its last argument)
# Env: SHOOTOUT_RUNS (default 5) — runs per label (the median is over
# them); SHOOTOUT_PREP — an untimed command run before each timed run,
# with the same fresh domain prefix appended (the smoke `reopen` case
# writes the durable DB it then reopens).
set -u
LABEL="$1"; shift
CAP_MS=10000
RUNS="${SHOOTOUT_RUNS:-5}"
WORK="${SHOOTOUT_WORK:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.shootout-runs}"
mkdir -p "$WORK"
times=()
mains=()
for i in $(seq 1 "$RUNS"); do
  D="$WORK/$LABEL-$i"
  rm -f "$D".root "$D".slab* "$D".rmap "$D".journal 2>/dev/null
  if [ -n "${SHOOTOUT_PREP:-}" ]; then
    timeout 1200 $SHOOTOUT_PREP "$D" > "$WORK/$LABEL-$i.prep.out" 2>&1
  fi
  start=$(date +%s%N)
  timeout 1200 "$@" "$D" > "$WORK/$LABEL-$i.out" 2> "$WORK/$LABEL-$i.err"
  rc=$?
  end=$(date +%s%N)
  ms=$(( (end - start) / 1000000 ))
  ok=$(grep -c 'ONE-DONE' "$WORK/$LABEL-$i.out" || true)
  main=$(grep -o ':main [0-9]*' "$WORK/$LABEL-$i.out" | tail -1 | cut -d' ' -f2)
  echo "run $i: ${ms} ms rc=$rc one-done=$ok${main:+ in-process-main=$main}"
  if [ "$ok" -lt 1 ]; then
    tail_txt=$(cat "$WORK/$LABEL-$i.err" "$WORK/$LABEL-$i.out" | sed 's/\x1b\[[0-9;]*m//g' | grep -v -i -e reflection -e '^warning')
    why=$(printf '%s\n' "$tail_txt" | grep -i -m1 -e 'caused by' -e 'not found' -e 'could not' -e 'unable to')
    [ -n "$why" ] || why=$(printf '%s\n' "$tail_txt" | grep -i -m1 -e error -e exception -e fatal)
    echo "  fail-tail: $(printf '%s' "$why" | sed 's/^ *//' | cut -c1-240)"
    fails=$(( ${fails:-0} + 1 ))
  fi
  if [ "$ok" -ge 1 ]; then times+=("$ms"); [ -n "$main" ] && mains+=("$main"); else times+=("FAIL"); fi
  # two failed runs end the row (the same failure would repeat)
  [ "${fails:-0}" -ge 2 ] && { echo "  stopped after 2 failed runs"; break; }
done
printf '%s\n' "${times[@]}" | sort -n > "$WORK/$LABEL-times.txt"
median=$(printf '%s\n' "${times[@]}" | grep -E '^[0-9]+$' | sort -n | awk '{a[NR]=$1} END {if (NR) print a[int((NR+1)/2)]; else print "none"}')
main_median=$(printf '%s\n' "${mains[@]:-}" | grep -E '^[0-9]+$' | sort -n | awk '{a[NR]=$1} END {if (NR) print a[int((NR+1)/2)]; else print "none"}')
echo "SHOOTOUT label=$LABEL runs=[${times[*]}] median-ms=$median main-median-ms=$main_median cap-ms=$CAP_MS load=$(cat /proc/loadavg | cut -d' ' -f1-3)"
