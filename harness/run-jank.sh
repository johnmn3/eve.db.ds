#!/usr/bin/env bash
# run-jank.sh — the restored jank shootout leg (the precompiled setup).
#
# The Eve + eve.db.ds graph is AOT-compiled ahead of time into ONE
# binary (`jank compile` of the contract.one-aot entry — the
# compiled-jank lane's shape: eve.host-completion first, exit through
# eve.process/exit!) and published under the cohost AOT cache by basis
# stamp — ~/.cache/cohost/aot/jank/<stamp>/one, the stamp computed by
# cohost.aot/basis-stamp itself (harness/aot-stamp.bb) over: sister
# :jank, `jank print-binary-version` as the runtime, the
# tools.deps-resolved classpath as :extra, and the content of every
# source file under the build roots. A timed run is then just
# `<artifact>/one <domain>`.
#
#   run-jank.sh ensure         build the artifact if this basis lacks
#                              it; prints the artifact dir on stdout,
#                              the build cost as AOT-BUILD on stderr
#   run-jank.sh run <args...>  ensure, then run the driver
#
# Env: EVE_ROOT (or EVE_LOCAL), COHOST_DIR, DS_DIR (default: repo root
# of this script), BB (fleet bb), CLJ_CONFIG/JAVA_HOME for the
# classpath resolution. LC_ALL must name a UTF-8 locale (jank aborts
# without one); en_US.UTF-8 is the fleet's.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DS_DIR="${DS_DIR:-$(cd "$HERE/.." && pwd)}"
BB="${BB:-$HOME/workspace/tools/bin/bb}"
EVE_ROOT="${EVE_ROOT:-${EVE_LOCAL:-}}"
COHOST_DIR="${COHOST_DIR:-}"
[ -n "$EVE_ROOT" ] || { echo "run-jank: EVE_ROOT (or EVE_LOCAL) not set" >&2; exit 2; }
[ -n "$COHOST_DIR" ] || { echo "run-jank: COHOST_DIR not set" >&2; exit 2; }
# JANK_HOME_BIN (optional): a jank install to prefer over ~/.local/bin (a box with a stale shared jank).
export PATH="${JANK_HOME_BIN:+$JANK_HOME_BIN:}$HOME/.local/bin:$PATH"
export LC_ALL="${LC_ALL:-en_US.UTF-8}"

cd "$DS_DIR"
ROOTS="$(EVE_ROOT="$EVE_ROOT" BB="$BB" bash harness/host-cp.sh jank)"
DS_ROOTS="$DS_DIR/src:$DS_DIR/lib:$DS_DIR/lib-ds-deps:$DS_DIR/test/drivers"
CP="$ROOTS:$DS_ROOTS"

PATHS_EDN="$(python3 -c "
ps = '''$CP'''.split(':')
print('[' + ' '.join('\"%s\"' % p for p in ps) + ']')")"
RESOLVED_CP="$(clojure -Sdeps "{:paths $PATHS_EDN}" -Spath 2>/dev/null | tail -1)"

ROOTS_SP="${ROOTS//:/ }"
FILES="$(find $ROOTS_SP $DS_DIR/src $DS_DIR/lib $DS_DIR/lib-ds-deps $DS_DIR/test/drivers \
           -type f \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' -o -name '*.jank' \) | sort)"
FILES="$FILES
$DS_DIR/deps.edn
$DS_DIR/harness/run-jank.sh"

JANK_VERSION="$(jank print-binary-version 2>/dev/null | head -1)"
mapfile -t _SA < <(COHOST_DIR="$COHOST_DIR" "$BB" harness/aot-stamp.bb \
  jank "$JANK_VERSION" "cp:$RESOLVED_CP" $FILES)
STAMP="${_SA[0]}"; ARTIFACT="${_SA[1]}"

ensure() {
  if [ -f "$ARTIFACT/one" ]; then
    return 0
  fi
  local t0=$SECONDS
  local tmp="$ARTIFACT.tmp.$$"
  mkdir -p "$tmp" "$(dirname "$ARTIFACT")"
  jank --module-path "$RESOLVED_CP" compile contract.one-aot \
    -O3 --target-dir "$tmp" --name one
  mv "$tmp" "$ARTIFACT"
  echo "AOT-BUILD jank $((SECONDS-t0))s stamp=$STAMP" >&2
}

case "${1:-}" in
  ensure)
    ensure
    echo "$ARTIFACT"
    ;;
  run)
    shift
    ensure
    exec "$ARTIFACT/one" "$@"
    ;;
  *)
    echo "usage: run-jank.sh ensure|run <args...>" >&2
    exit 2
    ;;
esac
