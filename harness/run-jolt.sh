#!/usr/bin/env bash
# run-jolt.sh — the restored jolt shootout leg (the precompiled setup).
#
# The Eve + eve.db.ds graph is compiled ahead of time into ONE
# standalone binary (`jolt build`, the compiled-jolt lane's shape) and
# published under the cohost AOT cache by basis stamp —
# ~/.cache/cohost/aot/jolt/<stamp>/one-jolt, the stamp computed by
# cohost.aot/basis-stamp itself (harness/aot-stamp.bb) over: sister
# :jolt, the jolt version as the runtime, the tools.deps-resolved
# classpath as :extra, and the content of every source file under the
# build roots. A timed run is then just `<artifact>/one-jolt <domain>`
# — no library load or compilation at run time.
#
#   run-jolt.sh ensure         build the artifact if this basis lacks
#                              it; prints the artifact dir on stdout,
#                              the build cost as AOT-BUILD on stderr
#   run-jolt.sh run <args...>  ensure, then run the driver
#
# Env: EVE_ROOT (or EVE_LOCAL), COHOST_DIR, DS_DIR (default: repo root
# of this script), BB (fleet bb), CLJ_CONFIG/JAVA_HOME for the
# classpath resolution.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DS_DIR="${DS_DIR:-$(cd "$HERE/.." && pwd)}"
BB="${BB:-$HOME/workspace/tools/bin/bb}"
EVE_ROOT="${EVE_ROOT:-${EVE_LOCAL:-}}"
COHOST_DIR="${COHOST_DIR:-}"
[ -n "$EVE_ROOT" ] || { echo "run-jolt: EVE_ROOT (or EVE_LOCAL) not set" >&2; exit 2; }
[ -n "$COHOST_DIR" ] || { echo "run-jolt: COHOST_DIR not set" >&2; exit 2; }
export PATH="$HOME/.local/bin:$PATH"

cd "$DS_DIR"
ROOTS="$(EVE_ROOT="$EVE_ROOT" BB="$BB" bash harness/host-cp.sh jolt)"
DS_ROOTS="$DS_DIR/src:$DS_DIR/lib:$DS_DIR/lib-ds-deps:$DS_DIR/test/drivers"
CP="$ROOTS:$DS_ROOTS"

PATHS_EDN="$(python3 -c "
ps = '''$CP'''.split(':')
print('[' + ' '.join('\"%s\"' % p for p in ps) + ']')")"
RESOLVED_CP="$(clojure -Sdeps "{:paths $PATHS_EDN}" -Spath 2>/dev/null | tail -1)"

ROOTS_SP="${ROOTS//:/ }"
FILES="$(find $ROOTS_SP $DS_DIR/src $DS_DIR/lib $DS_DIR/lib-ds-deps $DS_DIR/test/drivers \
           -type f \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' \) | sort)"
FILES="$FILES
$DS_DIR/deps.edn
$DS_DIR/harness/run-jolt.sh"

JOLT_VERSION="$(jolt --version 2>/dev/null | head -1)"
mapfile -t _SA < <(COHOST_DIR="$COHOST_DIR" "$BB" harness/aot-stamp.bb \
  jolt "$JOLT_VERSION" "cp:$RESOLVED_CP" $FILES)
STAMP="${_SA[0]}"; ARTIFACT="${_SA[1]}"

ensure() {
  if [ -f "$ARTIFACT/one-jolt" ]; then
    return 0
  fi
  local t0=$SECONDS
  local tmp="$ARTIFACT.tmp.$$"
  mkdir -p "$tmp" "$(dirname "$ARTIFACT")"
  # The compiled-jolt lane's recipe: jolt build runs from a project
  # root whose deps.edn declares the paths (a -Scp override leaves
  # jolt.host/build-binary unbound), with the --dev codegen path —
  # the recorded fast configuration.
  local stage="$tmp/stage"
  mkdir -p "$stage"
  python3 - "$stage/deps.edn" <<PY
import sys
paths = '''$CP'''.split(':')
edn = '{:paths [' + ' '.join('"%s"' % p for p in paths) + ']'
edn += ' :deps {org.babashka/sci {:mvn/version "0.15.59"}}}\n'
open(sys.argv[1], 'w').write(edn)
PY
  (cd "$stage" && jolt build -m contract.one -o "$tmp/one-jolt" --dev)
  mv "$tmp" "$ARTIFACT"
  echo "AOT-BUILD jolt $((SECONDS-t0))s stamp=$STAMP" >&2
}

case "${1:-}" in
  ensure)
    ensure
    echo "$ARTIFACT"
    ;;
  run)
    shift
    ensure
    exec "$ARTIFACT/one-jolt" "$@"
    ;;
  *)
    echo "usage: run-jolt.sh ensure|run <args...>" >&2
    exit 2
    ;;
esac
