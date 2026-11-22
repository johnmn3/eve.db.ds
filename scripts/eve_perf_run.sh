#!/usr/bin/env bash
# eve_perf_run.sh — one runner for every green leg: startup (the shootout)
# and runtime (Eve's perf-all, the ds runtime harness), written to
# results/<date>/table.md with the Eve sha, ds sha, load1 and the exact
# command of every row.
#
# ADDITIVE. It changes nothing the shootout measures: each startup row is
# exactly perf_shootout.sh's invocation for that leg (ensure untimed, then
# harness/shootout.sh: cold process -> one datum, x5, median, cap 10 s);
# the two legs perf_shootout.sh lacks (jvm-src: run-jvm.sh contract.one;
# glj: run-glj.sh contract.one) use the same harness/shootout.sh wrapper.
# Runtime rows run Eve's own scripts/perf-all.clj (its perf-results.edn is
# saved and restored, so the Eve tree is left as found) and this repo's
# ds-harness.perf (perf_runtime.sh's measurement).
#
# Cells changed by t98u (2026-11-22, the operator's approval of the two
# harness changes): `jvm-aot` now runs with an AppCDS archive trained in
# its untimed ensure (java -XX:SharedArchiveFile=one.jsa -jar one.jar),
# and `cljs`, `jolt`, `jank` exec their artifact from the pointer file
# their untimed ensure writes (no per-launch basis stamp). The contract,
# the one-datum act and the median-of-5 protocol are unchanged. Those
# rows are labelled in the table; with COMPARE=1 (default) each changed
# cell is followed by its pre-t98u twin (run-nocds / run-stamped) on the
# same artifact, so old and new sit side by side at the same load, and
# doc/perf/shootout-history.md keeps the earlier runs' cells.
#
# Load discipline: before each leg, wait (up to LOAD_WAIT_S, default 600)
# for load1 <= LOAD_MAX (default 5); a row whose end load1 > LOAD_MAX is
# rerun once and both are recorded.
#
# Env:
#   EVE_DIR     Eve checkout under measurement           (REQUIRED)
#   DS_DIR      eve.db.ds checkout (default: this repo)
#   COHOST_DIR  cohost.cljc checkout (cljs/jolt/jank/jvm AOT legs)
#   BB          bb binary (default: bb on PATH)
#   LEGS        startup legs (default: "jvm-aot jvm-src cljs jolt jank glj")
#   COMPARE     1 adds the pre-t98u twin after each changed leg (default 1)
#   PERF_HOSTS  Eve perf-all hosts (default: "jvm cljs jolt glj"; "" skips)
#   JANK_PERF   1 runs jank's perf suite via run-main (default 1 if jank green)
#   DS_RUNTIME  1 runs ds-harness.perf people-1k on the JVM (default 1)
#   GLJ_EVE     a built glj-eve (default: run-glj.sh builds one)
#   OUT_ROOT    default $DS_DIR/results
set -u
DS_DIR="${DS_DIR:-$(cd "$(dirname "$0")/.." && pwd)}"
EVE_DIR="${EVE_DIR:?EVE_DIR must name the Eve checkout}"
export EVE_LOCAL="$EVE_DIR" EVE_ROOT="$EVE_DIR" COHOST_DIR="${COHOST_DIR:-}"
export BB="${BB:-$(command -v bb)}"
LEGS="${LEGS-jvm-aot jvm-src cljs jolt jank glj}"
PERF_HOSTS="${PERF_HOSTS-jvm cljs jolt glj}"
COMPARE="${COMPARE:-1}"
LOAD_MAX="${LOAD_MAX:-5}"; LOAD_WAIT_S="${LOAD_WAIT_S:-600}"
DATE=$(date +%F)
OUT="${OUT_ROOT:-$DS_DIR/results}/$DATE"
mkdir -p "$OUT/logs"
export SHOOTOUT_WORK="$OUT/shootout-runs"; mkdir -p "$SHOOTOUT_WORK"
EVE_SHA=$(git -C "$EVE_DIR" rev-parse --short=10 HEAD)$(git -C "$EVE_DIR" diff --quiet HEAD -- . ':!doc/hosts/perf-results.edn' || echo "+dirty")
DS_SHA=$(git -C "$DS_DIR" rev-parse --short=10 HEAD)$(git -C "$DS_DIR" diff --quiet HEAD || echo "+dirty")
load1() { cut -d' ' -f1 /proc/loadavg; }
over() { awk -v l="$(load1)" -v m="$LOAD_MAX" 'BEGIN{exit !(l>m)}'; }
wait_load() { local t=0; while over && [ $t -lt "$LOAD_WAIT_S" ]; do sleep 15; t=$((t+15)); done; }
T="$OUT/table.md"
{
  echo "# eve perf run $DATE ($(date '+%H:%M %Z'))"
  echo
  echo "- eve: \`$EVE_SHA\` ($EVE_DIR, $(git -C "$EVE_DIR" rev-parse --abbrev-ref HEAD))"
  echo "- eve.db.ds: \`$DS_SHA\` ($DS_DIR, $(git -C "$DS_DIR" rev-parse --abbrev-ref HEAD))"
  echo "- box: $(nproc) cpu, $(free -g | awk '/Mem/{print $2}') GB RAM, $(uname -r)"
  echo
  echo "## Startup (cold process -> one datum on a disk-backed DB; median of 5; cap 10,000 ms)"
  echo
  echo "| leg | cell | median ms | runs ms | load1 start/end | command |"
  echo "|---|---|---|---|---|---|"
} > "$T"

startup_cmd() { # leg -> "ensure-cmd#timed-cmd"
  local H="$DS_DIR/harness"
  case "$1" in
    jvm-aot) echo "bash $H/run-jvm-aot.sh ensure#bash $H/run-jvm-aot.sh run" ;;
    jvm-aot-old) echo "bash $H/run-jvm-aot.sh ensure#bash $H/run-jvm-aot.sh run-nocds" ;;
    cljs-old|jolt-old|jank-old) echo "bash $H/run-${1%-old}.sh ensure#bash $H/run-${1%-old}.sh run-stamped" ;;
    jvm-src) echo "bash $H/run-jvm.sh contract.one $OUT/jvm-src-warm#bash $H/run-jvm.sh contract.one" ;;
    cljs)    echo "bash $H/run-cljs.sh ensure#bash $H/run-cljs.sh run" ;;
    jolt)    echo "bash $H/run-jolt.sh ensure#bash $H/run-jolt.sh run" ;;
    jank)    echo "bash $H/run-jank.sh ensure#bash $H/run-jank.sh run" ;;
    glj)     echo "test -x \${GLJ_EVE:-$EVE_DIR/target/glj-eve/glj-eve} || bash $EVE_DIR/impls/glojure/build.sh $EVE_DIR/target/glj-eve#bash $H/run-glj.sh contract.one" ;;
    bb)      echo "true#bash $H/run-bb.sh one" ;;
    nbb)     echo "true#bash $H/run-nbb.sh one" ;;
    lpy)     echo "true#bash $H/run-lpy.sh one" ;;
    *)       echo "" ;;
  esac
}

cell_of() { # leg -> the cell's label in the table
  case "$1" in
    jvm-aot) echo "CHANGED t98u: AppCDS archive" ;;
    cljs|jolt|jank) echo "CHANGED t98u: pointer file, no per-run stamp" ;;
    jvm-aot-old) echo "pre-t98u (no AppCDS), same jar" ;;
    *-old) echo "pre-t98u (stamp every run), same artifact" ;;
    *) echo "unchanged" ;;
  esac
}
run_leg() { # leg attempt
  local leg=$1 c ens cmd l0 l1 line med runs
  c=$(startup_cmd "$leg"); [ -z "$c" ] && { echo "| $leg | unknown leg | | | |" >> "$T"; return; }
  ens=${c%%#*}; cmd=${c#*#}
  wait_load; l0=$(load1)
  ( eval "$ens" ) > "$OUT/logs/ensure-$leg.log" 2>&1
  bash "$DS_DIR/harness/shootout.sh" "$leg" $cmd > "$OUT/logs/shootout-$leg.txt" 2>&1
  l1=$(load1)
  line=$(grep '^SHOOTOUT ' "$OUT/logs/shootout-$leg.txt" | tail -1)
  med=$(echo "$line" | grep -o 'median-ms=[^ ]*' | cut -d= -f2)
  runs=$(echo "$line" | sed -n 's/.*runs=\[\([^]]*\)\].*/\1/p')
  [ -z "$med" ] && med=none
  echo "| $leg${2:+ (rerun)} | $(cell_of "$leg") | $med | $runs | $l0 / $l1 | \`$cmd\` |" >> "$T"
  echo "LEG-DONE $leg median=$med load=$l0/$l1"
  if [ -z "${2:-}" ] && over; then run_leg "$leg" rerun; fi
}
for leg in $LEGS; do
  run_leg "$leg"
  if [ "$COMPARE" = 1 ]; then case "$leg" in jvm-aot|cljs|jolt|jank) run_leg "$leg-old" ;; esac; fi
done

if [ -n "$PERF_HOSTS" ]; then
  {
    echo
    echo "## Eve runtime (Eve scripts/perf-all.clj, test/eve/perf_suite.cljc; ns/op)"
    echo
  } >> "$T"
  PR="$EVE_DIR/doc/hosts/perf-results.edn"
  cp "$PR" "$OUT/logs/perf-results.before.edn"
  wait_load; l0=$(load1)
  ( cd "$EVE_DIR" && "$BB" scripts/perf-all.clj $PERF_HOSTS ) > "$OUT/logs/perf-all.log" 2>&1
  l1=$(load1)
  cp "$PR" "$OUT/perf-results.edn"; cp "$OUT/logs/perf-results.before.edn" "$PR"
  "$BB" -e "
(require '[clojure.edn :as edn] '[clojure.string :as str])
(let [m (:hosts (edn/read-string (slurp \"$OUT/perf-results.edn\")))
      hs (map keyword (str/split \"$PERF_HOSTS\" #\" +\"))
      rows (distinct (mapcat (fn [h] (map :perf (get-in m [h :lines]))) hs))
      cell (fn [h w] (or (some (fn [l] (when (= w (:perf l)) (or (:ns-per-op l) (:error l) \"err\"))) (get-in m [h :lines])) \"—\"))]
  (println (str \"| workload | \" (str/join \" | \" (map name hs)) \" |\"))
  (println (str \"|---|\" (str/join \"\" (repeat (count hs) \"---|\"))))
  (doseq [w rows] (println (str \"| \" (name w) \" | \" (str/join \" | \" (map #(cell % w) hs)) \" |\")))
  (println)
  (println (str \"load1 \" \"$l0\" \" / \" \"$l1\" \"; command: \`cd \$EVE_DIR && bb scripts/perf-all.clj $PERF_HOSTS\`; at \" (str/join \", \" (map #(str (name %) \" \" (get-in m [% :at])) hs)))))" >> "$T" 2>>"$OUT/logs/perf-all.log"
fi

if [ "${JANK_PERF:-1}" = 1 ] && echo " $LEGS " | grep -q ' jank '; then
  wait_load; l0=$(load1)
  JCP=$(bash "$DS_DIR/harness/host-cp.sh" jank):$EVE_DIR/test
  JB="${JANK_HOME_BIN:+$JANK_HOME_BIN/}jank"
  JCMD="$JB --module-path <jank-cp>:\$EVE_DIR/test run-main eve.runner.jank-main -- perf"
  ( cd "$EVE_DIR" && if [ -n "${JANK_XDG_CACHE_HOME:-}" ]; then export XDG_CACHE_HOME="$JANK_XDG_CACHE_HOME"; fi; export LC_ALL="${LC_ALL:-en_US.UTF-8}"; timeout 3600 "$JB" --module-path "$JCP" run-main eve.runner.jank-main -- perf ) > "$OUT/logs/perf-jank.log" 2>&1
  {
    echo
    echo "### jank runtime (interpreted perf suite; ns/op)"
    echo
    echo "| workload | ns/op |"; echo "|---|---|"
    grep -o '{:perf [^}]*}' "$OUT/logs/perf-jank.log" | sed -E 's/.*:perf :([^ ,]+).*:ns-per-op ([0-9]+).*/| \1 | \2 |/'
    echo; echo "load1 $l0 / $(load1); command: \`$JCMD\`"
  } >> "$T"
fi

if [ "${DS_RUNTIME:-1}" = 1 ]; then
  # contract.ds-runtime: people-1k on the rewrite API (ds-harness.perf no longer runs at
  # this ds sha — see the driver's docstring). JVM, x5 fresh processes after one warm-up.
  wait_load; l0=$(load1)
  for i in 0 1 2 3 4 5; do
    rm -rf "$OUT/ds-runtime-jvm"
    timeout 1200 bash "$DS_DIR/harness/run-jvm.sh" contract.ds-runtime "$OUT/ds-runtime-jvm" jvm 2>/dev/null | grep ds-runtime
  done > "$OUT/logs/ds-runtime-jvm.edn"
  {
    echo
    echo "## eve.db.ds runtime (contract.ds-runtime people-1k, JVM; median of 5 after a warm-up; ms)"
    echo
    "$BB" -e "
(require '[clojure.edn :as edn] '[clojure.string :as str])
(let [rows (rest (map edn/read-string (str/split-lines (slurp \"$OUT/logs/ds-runtime-jvm.edn\"))))
      ks [:create-ms :build-ms :find-1000-ms :pull-1000-ms :query-7-ms :seq-ms :tx-single-100-ms]
      med (fn [k] (let [v (sort (map k rows))] (nth v (quot (count v) 2))))]
  (println (str \"| \" (str/join \" | \" (map name ks)) \" | keys-exact |\"))
  (println (str \"|\" (str/join (repeat (inc (count ks)) \"---|\"))))
  (println (str \"| \" (str/join \" | \" (map med ks)) \" | \" (every? :keys-exact rows) \" |\")))"
    echo
    echo "load1 $l0 / $(load1); command: \`bash \$DS_DIR/harness/run-jvm.sh contract.ds-runtime <dir> jvm\`"
  } >> "$T"
fi
echo "RUN-DONE table=$T"
