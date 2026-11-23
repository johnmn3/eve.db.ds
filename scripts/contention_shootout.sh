#!/usr/bin/env bash
# contention_shootout.sh — every runtime of the perf shootout bashing ONE
# persistent Eve domain at once (test/drivers/contract/ds_shootout.cljc):
# a shared disk atom, a mailbox atom per writer and an eve.db.ds
# persistent conn, all under one fresh directory prefix. Then every
# runtime reads the final state back, and scripts/contention_shootout_check.clj
# proves it: every transaction present exactly once, no lost swap!,
# the :db/cas counter equal to the increments that landed, every
# writer saw the others' writes while it ran, every reader read the
# same final DB. Throughput: each runtime alone, then all together,
# per-op ns/op from the same mixed workload.
#
#   scripts/contention_shootout.sh            solo legs, then the together leg
#   PHASES=together scripts/contention_shootout.sh
#
# Env: EVE_DIR (Eve tree, REQUIRED) · DS_DIR (default: this repo) ·
# RUNTIMES (default "jvm jolt cljs jank bb"; glj when GLJ_EVE names
# glj-eve) · PHASES ("solo together") · DUR_MS (each writer's run after
# the barrier, default 30000) · OUT (default $DS_DIR/contention-results/
# <stamp>) · SCRATCH (store root, default /tmp/eve-ds-shootout; each leg
# gets a fresh <root>/<leg> prefix, and <prefix> plus <prefix>.* are
# removed before and after — an Eve store keeps sibling files) ·
# KEEP=1 keeps the stores · WORKLOAD_FLAGS (comma list: nocas drops the :db/cas op; nodurable opens the domain unjournaled — a control) · CLJS_JS (a prebuilt bundle; default: built
# into $OUT with shadow-cljs 3.3.6, :advanced as in the cljs shootout
# leg) · JANK_HOME_BIN / JANK_XDG_CACHE_HOME as in run-jank.sh.
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
DS="$(cd "${DS_DIR:-$HERE/..}" && pwd)"
EVE="$(cd "${EVE_DIR:?EVE_DIR must name the Eve tree}" && pwd)"
RUNTIMES="${RUNTIMES:-jvm jolt cljs jank bb}"
PHASES="${PHASES:-solo together}"
DUR_MS="${DUR_MS:-30000}"
OUT="${OUT:-$DS/contention-results/$(date +%Y%m%d-%H%M%S)}"
SCRATCH="${SCRATCH:-/tmp/eve-ds-shootout}"
BB="${BB:-bb}"
NS=contract.ds-shootout
mkdir -p "$OUT" "$SCRATCH"
export EVE_ROOT="$EVE" EVE_LOCAL="$EVE"
log() { echo "[$(TZ=America/New_York date '+%H:%M:%S ET')] $*" | tee -a "$OUT/run.log" >&2; }

# disk: refuse when the store root's filesystem is short (stores grow to ~100 MB a leg)
free_kb=$(df -Pk "$SCRATCH" | awk 'NR==2{print $4}'); free_in=$(df -Pi "$SCRATCH" | awk 'NR==2{print $4}')
if [ "$free_kb" -lt 2000000 ] || [ "$free_in" -lt 60000 ]; then
  log "DISK-HOLD free ${free_kb} KB, ${free_in} inodes under $SCRATCH"; exit 3
fi

DSCP="$DS/src:$DS/lib:$DS/lib-ds-deps:$DS/test/drivers"
JOLT_CWD="$(mktemp -d)"   # jolt resolves a deps.edn in its cwd; run from an empty one
trap 'rm -rf "$JOLT_CWD"' EXIT
for rt in $RUNTIMES; do case "$rt" in
  jolt) JCP="$(bash "$DS/harness/host-cp.sh" jolt):$DSCP" ;;
  bb) BBCP="$(cd "$EVE" && "$BB" classpath bb)"; BBCP="${BBCP#:}:$DSCP" ;;
  jank) KCP="$(bash "$DS/harness/host-cp.sh" jank):$DSCP"
        export PATH="${JANK_HOME_BIN:+$JANK_HOME_BIN:}$PATH" LC_ALL="${LC_ALL:-en_US.UTF-8}"
        [ -n "${JANK_XDG_CACHE_HOME:-}" ] && export XDG_CACHE_HOME="$JANK_XDG_CACHE_HOME" ;;
  cljs) if [ -z "${CLJS_JS:-}" ]; then
          CLJS_JS="$OUT/ds-shootout.js"
          ROOTS="$(bash "$DS/harness/host-cp.sh" cljs)"
          PATHS_EDN="[$(echo "$ROOTS:$DSCP" | tr ':' '\n' | sed 's/.*/"&"/' | tr '\n' ' ')]"
          log "building the cljs bundle ($CLJS_JS)"
          (cd "$DS" && clojure -Sdeps "{:paths $PATHS_EDN :deps {thheller/shadow-cljs {:mvn/version \"3.3.6\"}}}" \
             -M -m shadow.cljs.devtools.cli release one-node \
             --config-merge "{:main contract.ds-shootout-cljs/main :output-to \"$CLJS_JS\"}") > "$OUT/cljs-build.log" 2>&1 \
            || { log "cljs build failed ($OUT/cljs-build.log)"; exit 2; }
        fi ;;
esac; done

run_rt() { # rt args... — one process of the driver on runtime rt
  local rt="$1"; shift
  case "$rt" in
    jvm) bash "$DS/harness/run-jvm.sh" "$NS" "$@" ;;
    jolt) (cd "$JOLT_CWD" && jolt -Scp "$JCP" -m "$NS" "$@") ;;
    cljs) EVE_MMAP_ADDON="$EVE/build/Release/mmap_cas.node" node "$CLJS_JS" "$@" ;;
    jank) (cd "$DS" && jank --module-path "$KCP" run-main "$NS" -- "$@") ;;
    bb) "$BB" -cp "$BBCP" -m "$NS" "$@" ;;
    glj) bash "$DS/harness/run-glj.sh" "$NS" "$@" ;;
    *) echo "{:fatal \"unknown runtime $rt\"}" ;;
  esac
}

edn_line() { grep -E "^\{.*:mode :$1" | tail -1; }

leg() { # leg-name rt... — a fresh store, every rt writing at once, every rt reading back
  local name="$1"; shift
  local rts=("$@") n=$# dir="$SCRATCH/$name" d="$OUT/$name" k=0
  # eve.db.ds names a persistent conn's atom by (hash "<dir>/ds"), and jank's
  # hash is unsigned where the others' is signed: a store path whose hash is
  # negative elsewhere opens a DIFFERENT conn atom on jank. Pick a prefix whose
  # hash is non-negative so every runtime opens the same conn (the bug itself
  # is reported, not worked around in the library).
  while [ "$("$BB" -e "(prn (neg? (hash \"$dir/ds\")))")" = true ]; do k=$((k + 1)); dir="$SCRATCH/$name-$k"; done
  rm -rf "$dir" "$dir".*; mkdir -p "$dir" "$d"
  log "leg $name: writers [${rts[*]}] ${DUR_MS} ms; store $dir"
  run_rt "${rts[0]}" init "$dir" ${WORKLOAD_FLAGS:-} > "$d/init.log" 2>&1
  edn_line init < "$d/init.log" > "$d/init.edn" || { log "init failed ($d/init.log)"; return 1; }
  local pids=(); k=0
  for rt in "${rts[@]}"; do
    k=$((k + 1))
    ( run_rt "$rt" worker "$dir" "$rt" "p$k" "$n" "$DUR_MS" ${WORKLOAD_FLAGS:-} > "$d/worker-$rt-p$k.log" 2>&1 ) &
    pids+=($!)
  done
  for p in "${pids[@]}"; do wait "$p"; done
  for f in "$d"/worker-*.log; do edn_line worker < "$f" > "${f%.log}.edn"; done
  log "leg $name: writers done; reading back from [${READERS:-${rts[*]}}]"
  for rt in ${READERS:-${rts[@]}}; do
    run_rt "$rt" readback "$dir" "$rt" ${WORKLOAD_FLAGS:-} > "$d/readback-$rt.log" 2>&1
    edn_line readback < "$d/readback-$rt.log" > "$d/readback-$rt.edn"
  done
  du -sh "$dir" > "$d/store-size.txt" 2>/dev/null
  [ "${KEEP:-0}" = 1 ] || rm -rf "$dir" "$dir".*
}

log "contention shootout: eve $EVE ($(git -C "$EVE" rev-parse --short HEAD 2>/dev/null)) ds $DS ($(git -C "$DS" rev-parse --short HEAD 2>/dev/null)) runtimes [$RUNTIMES] load1 $(cut -d' ' -f1 /proc/loadavg)"
for ph in $PHASES; do
  case "$ph" in
    solo) for rt in $RUNTIMES; do leg "solo-$rt" "$rt"; done ;;
    together) READERS="$RUNTIMES" leg together $RUNTIMES ;;
  esac
done
log "load1 end $(cut -d' ' -f1 /proc/loadavg)"
"$BB" "$HERE/contention_shootout_check.clj" "$OUT" | tee "$OUT/verdict.txt"
