#!/usr/bin/env bash
# run-jvm-aot.sh — the JVM shootout leg as a true uberjar (the
# precompiled setup, restored shape like run-jank.sh / run-jolt.sh).
#
# The Eve + eve.db.ds graph is AOT-compiled ahead of time into ONE
# self-contained jar (build.clj: tools.build, direct linking, the
# runtime-resolved namespaces force-compiled, no sources in the jar)
# and published under the cohost AOT cache by basis stamp —
# ~/.cache/cohost/aot/jvm/<stamp>/one.jar, the stamp computed by
# cohost.aot/basis-stamp itself (harness/aot-stamp.bb) over: sister
# :jvm, the java version as the runtime, the tools.deps-resolved
# classpath as :extra, and the content of every source file under the
# build roots. A timed run is then exactly `java -jar one.jar <domain>`
# — no cache-key computation, no multi-root classpath, no clojure.main.
#
# This replaces harness/run-jvm.sh FOR THE SHOOTOUT ONLY: that runner's
# persistent class cache still carries a per-launch find+md5 over both
# trees and a clojure.main -m launch. run-jvm.sh itself is untouched —
# the test suites keep using it.
#
#   run-jvm-aot.sh ensure         build the artifact if this basis lacks
#                                 it; prints the artifact dir on stdout,
#                                 the build cost as AOT-BUILD on stderr
#   run-jvm-aot.sh run <args...>  ensure, then run the driver
#
# Env: EVE_ROOT (or EVE_LOCAL), COHOST_DIR, DS_DIR (default: repo root
# of this script), BB (fleet bb), CLJ_CONFIG/JAVA_HOME for the
# classpath resolution and the build.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DS_DIR="${DS_DIR:-$(cd "$HERE/.." && pwd)}"
BB="${BB:-$HOME/workspace/tools/bin/bb}"
EVE_ROOT="${EVE_ROOT:-${EVE_LOCAL:-}}"
COHOST_DIR="${COHOST_DIR:-}"
[ -n "$EVE_ROOT" ] || { echo "run-jvm-aot: EVE_ROOT (or EVE_LOCAL) not set" >&2; exit 2; }
[ -n "$COHOST_DIR" ] || { echo "run-jvm-aot: COHOST_DIR not set" >&2; exit 2; }
export PATH="$HOME/.local/bin:$PATH"

cd "$DS_DIR"
ROOTS="$(EVE_ROOT="$EVE_ROOT" BB="$BB" bash harness/host-cp.sh jvm)"
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
$DS_DIR/build.clj
$DS_DIR/harness/run-jvm-aot.sh"

JAVA_VERSION="$(java -version 2>&1 | head -1)"
mapfile -t _SA < <(COHOST_DIR="$COHOST_DIR" "$BB" harness/aot-stamp.bb \
  jvm "$JAVA_VERSION" "cp:$RESOLVED_CP" $FILES)
STAMP="${_SA[0]}"; ARTIFACT="${_SA[1]}"

ensure() {
  if [ -f "$ARTIFACT/one.jar" ]; then
    return 0
  fi
  local t0=$SECONDS
  local tmp="$ARTIFACT.tmp.$$"
  mkdir -p "$tmp" "$(dirname "$ARTIFACT")"
  clojure -Sdeps "{:deps {io.github.clojure/tools.build {:mvn/version \"0.10.5\"}}
                   :paths [\"$DS_DIR\"]}" \
    -M -e "(require 'build) (build/uber {:cp \"$RESOLVED_CP\" :class-dir \"$tmp/classes\" :out \"$tmp/one.jar\" :ds-src \"$DS_DIR/src\"})"
  rm -rf "$tmp/classes"
  mv "$tmp" "$ARTIFACT"
  echo "AOT-BUILD jvm $((SECONDS-t0))s stamp=$STAMP" >&2
}

case "${1:-}" in
  ensure)
    ensure
    echo "$ARTIFACT"
    ;;
  run)
    shift
    ensure
    exec java -jar "$ARTIFACT/one.jar" "$@"
    ;;
  *)
    echo "usage: run-jvm-aot.sh ensure|run <args...>" >&2
    exit 2
    ;;
esac
