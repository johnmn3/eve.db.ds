#!/usr/bin/env bash
# ds_matrix.sh — one cell of the README host table: one fresh process of
# one host running one family of the repo's own perf shapes.
#
#   ds_matrix.sh <host> bench   <fresh-dir> [:shape] [size...]
#                                   five write shapes, or the one named
#                                   (:batched-build ...; contract.ds-bench)
#   ds_matrix.sh <host> runtime <fresh-dir>             people-1k reads
#                                                       (contract.ds-runtime)
#
# The JVM runs contract.ds-bench / contract.ds-runtime directly through
# harness/run-jvm.sh (the commands scripts/ds_bench.sh and the runtime
# table already use). Every other host enters contract.ds-matrix, which
# calls the same shape code after the host's start-up. Rows print as EDN
# ({:arm :eve ...} per write shape, {:ds-runtime ...} for the reads);
# medians across fresh processes are the caller's job.
#
# Env: EVE_ROOT (Eve checkout, REQUIRED), DS_DIR (default: this repo),
#      BB (default: bb on PATH), NBB, BASILISP, GLJ_EVE, JOLT, CLJS_JS
#      (a built contract.ds-matrix-cljs node script; see below),
#      CELL_TIMEOUT (seconds per process, default 180).
#
# cljs: build the node script once per tree pair with
#   ds_matrix.sh cljs-build <out.js>
# (shadow-cljs 3.3.6 :one-node with :main contract.ds-matrix-cljs/main,
# :optimizations :simple — :advanced renames the eve Datom's fields that
# the query engine binds by name, so query values come back nil there).
set -u
HOST="${1:?host}"; MODE="${2:?mode}"; shift 2
HERE="$(cd "$(dirname "$0")" && pwd)"
DS="${DS_DIR:-$(cd "$HERE/.." && pwd)}"
EVE="${EVE_ROOT:?EVE_ROOT must name the Eve checkout}"
export EVE_ROOT="$EVE" EVE_LOCAL="$EVE"
BB="${BB:-bb}"
DSCP="$DS/src:$DS/lib:$DS/lib-ds-deps:$DS/test/drivers"
NS=contract.ds-matrix
cp_for () { EVE_ROOT="$EVE" BB="$BB" bash "$DS/harness/host-cp.sh" "$1"; }

# Every perf process is capped (the README table's rule): CELL_TIMEOUT
# seconds, 180 by default, then SIGTERM and SIGKILL 10 s later. A shape
# that cannot finish under the cap is reported "> 3 min (timed out)",
# never run longer to get a number. (cljs-build compiles; it is no cell.)
if [ "$MODE" != cljs-build ] && [ -z "${DS_MATRIX_CAPPED:-}" ]; then
  export DS_MATRIX_CAPPED=1
  exec timeout -k 10 "${CELL_TIMEOUT:-180}" bash "$0" "$HOST" "$MODE" "$@"
fi

if [ "$MODE" = cljs-build ]; then
  OUTJS="${1:?out.js}"
  ROOTS="$(cp_for cljs)"
  PATHS_EDN="$(python3 -c "
ps = '''$ROOTS:$DSCP'''.split(':')
print('[' + ' '.join('\"%s\"' % p for p in ps) + ']')")"
  cd "$DS" && exec clojure -Sdeps "{:paths $PATHS_EDN :deps {thheller/shadow-cljs {:mvn/version \"3.3.6\"}}}" \
    -M -m shadow.cljs.devtools.cli release one-node \
    --config-merge "{:main contract.ds-matrix-cljs/main :output-to \"$OUTJS\" :compiler-options {:optimizations :simple}}"
fi

DIR="${1:?fresh dir}"; shift
case "$DIR" in /*) ;; *) DIR="$PWD/$DIR" ;; esac   # run-jvm.sh and nbb change directory
case "$HOST" in
  jvm)
    if [ "$MODE" = bench ]; then exec bash "$DS/harness/run-jvm.sh" contract.ds-bench "$DIR" "$@"
    else exec bash "$DS/harness/run-jvm.sh" contract.ds-runtime "$DIR" jvm; fi ;;
  browser)
    # Chromium, persistent conns on Eve's OPFS tier (scripts/ds_matrix_browser.clj; build the page once
    # with  bb scripts/ds_matrix_browser.clj build <eve> <out>  and set BROWSER_PAGE=<out>). The fresh
    # dir names a path in the page's own fresh OPFS (a fresh Chromium profile per process).
    exec "$BB" "$DS/scripts/ds_matrix_browser.clj" run "${BROWSER_PAGE:?BROWSER_PAGE: build the page first}" "$MODE" "/ds/$(basename "$DIR")" "$@" ;;
  bb)
    CP="$(cd "$EVE" && "$BB" classpath bb)" || exit 8
    exec "$BB" -cp "${CP#:}:$DSCP" -m $NS "$MODE" "$DIR" bb "$@" ;;
  nbb)
    CP="$(cp_for nbb)" || exit 8
    cd "$EVE" && exec "${NBB:-nbb}" --classpath "$CP:$DSCP" -m $NS "$MODE" "$DIR" nbb "$@" ;;
  lpy)
    CP="$(cp_for lpy)" || exit 8
    export BASILISP_DO_NOT_CACHE_NAMESPACES=true PYTHONDONTWRITEBYTECODE=1
    P=(); IFS=':' read -ra PARTS <<< "$CP:$DSCP"
    for (( i=${#PARTS[@]}-1; i>=0; i-- )); do P+=(-p "${PARTS[i]}"); done
    EXPR="(require (quote eve.host-completion)) (require (quote $NS)) (apply (resolve (quote $NS/-main)) *command-line-args*)"
    cd "$DS" && exec "${BASILISP:-basilisp}" run "${P[@]}" -c "$EXPR" -- "$MODE" "$DIR" lpy "$@" ;;
  jolt)
    CP="$(cp_for jolt)" || exit 8
    # an empty cwd: jolt reads a deps.edn in its working directory
    W="${TMPDIR:-/tmp}/ds-matrix-jolt-cwd"; mkdir -p "$W"
    cd "$W" && exec "${JOLT:-jolt}" -Scp "$CP:$DSCP" -m $NS "$MODE" "$DIR" jolt "$@" ;;
  glj)
    exec bash "$DS/harness/run-glj.sh" $NS "$MODE" "$DIR" glj "$@" ;;
  jank)
    # interpreted (jank run-main, JIT); LC_ALL must name a UTF-8 locale
    CP="$(cp_for jank)" || exit 8
    export LC_ALL="${LC_ALL:-en_US.UTF-8}"
    cd "$DS" && exec "${JANK:-jank}" --module-path "$CP:$DSCP" run-main $NS -- "$MODE" "$DIR" jank "$@" ;;
  clje)
    CP="$(cp_for clje)" || exit 8
    P=(); IFS=':' read -ra PARTS <<< "$CP:$DSCP"
    for r in "${PARTS[@]}"; do P+=(-pa "$r"); done
    cd "$EVE" && exec "${CLOJERL:-clojerl}" "${P[@]}" -m $NS "$MODE" "$DIR" clje "$@" ;;
  cljr)
    CP="$(cp_for cljr)" || exit 8
    export CLOJURE_LOAD_PATH="$CP:$DSCP"
    cd "$EVE" && exec "${CLJR:-cljr}" -m $NS "$MODE" "$DIR" cljr "$@" ;;
  cljs)
    : "${CLJS_JS:?CLJS_JS must name the built node script (ds_matrix.sh cljs-build)}"
    EVE_MMAP_ADDON="$EVE/build/Release/mmap_cas.node" exec node "$CLJS_JS" "$MODE" "$DIR" cljs "$@" ;;
  *) echo "ds_matrix.sh: no runner for host $HOST" >&2; exit 2 ;;
esac
