#!/usr/bin/env bash
# run-cljs.sh — the restored cljs shootout leg (the cohost AOT setup).
#
# The whole Eve + eve.db.ds graph is Closure-compiled ahead of time
# into ONE bundle and published under the cohost AOT cache by basis
# stamp — ~/.cache/cohost/aot/cljs/<stamp>/main.js, the stamp computed
# by cohost.aot/basis-stamp itself (harness/aot-stamp.bb) over: sister
# :cljs, node's version as the runtime, the resolved classpath as
# :extra, and the content of every source file under the build roots
# plus the build configuration. A timed run is then just
# `EVE_MMAP_ADDON=<eve addon> node <artifact>/main.js <domain>` — no
# ClojureScript compilation or source loading at run time.
#
#   run-cljs.sh ensure         build the artifact if this basis lacks
#                              it (and Eve's node addon if unbuilt);
#                              prints the artifact dir on stdout, the
#                              build cost as AOT-BUILD on stderr
#   run-cljs.sh run <args...>  exec the artifact named by the pointer file
#                              (.cljs-aot-artifact, written by ensure) —
#                              no stamp computation on the timed path;
#                              without a live pointer, ensure first
#   run-cljs.sh run-stamped <args...>  the pre-pointer path (stamp,
#                              then the artifact), for comparison cells
#
# Env: EVE_ROOT (or EVE_LOCAL), COHOST_DIR, DS_DIR (default: repo root
# of this script), BB (fleet bb), CLJ_CONFIG/JAVA_HOME for the build's
# clojure invocation.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DS_DIR="${DS_DIR:-$(cd "$HERE/.." && pwd)}"
BB="${BB:-$HOME/workspace/tools/bin/bb}"
EVE_ROOT="${EVE_ROOT:-${EVE_LOCAL:-}}"
COHOST_DIR="${COHOST_DIR:-}"
[ -n "$EVE_ROOT" ] || { echo "run-cljs: EVE_ROOT (or EVE_LOCAL) not set" >&2; exit 2; }
[ -n "$COHOST_DIR" ] || { echo "run-cljs: COHOST_DIR not set" >&2; exit 2; }
SHADOW_VERSION="3.3.6"

cd "$DS_DIR"
POINTER="${CLJS_AOT_POINTER:-$DS_DIR/.cljs-aot-artifact}"

# The basis computation (host roots, tools.deps classpath resolution,
# the stamp's file list) — ensure's work, and run-stamped's / run's
# fallback only. The timed `run` path skips it via the pointer file.
compute_artifact() {
  ROOTS="$(EVE_ROOT="$EVE_ROOT" BB="$BB" bash harness/host-cp.sh cljs)"
  DS_ROOTS="$DS_DIR/src:$DS_DIR/lib:$DS_DIR/lib-ds-deps:$DS_DIR/test/drivers"
  CP="$ROOTS:$DS_ROOTS"

  # Resolved classpath (jars included) — the :extra of the basis, and
  # the -Sdeps paths for the shadow build.
  PATHS_EDN="$(python3 -c "
import sys
ps = '''$CP'''.split(':')
print('[' + ' '.join('\"%s\"' % p for p in ps) + ']')")"
  RESOLVED_CP="$(clojure -Sdeps "{:paths $PATHS_EDN}" -Spath 2>/dev/null | tail -1)"

  # The basis file set: every source under the build roots + the build
  # configuration that pins the compiler.
  ROOTS_SP="${ROOTS//:/ }"
  FILES="$(find $ROOTS_SP $DS_DIR/src $DS_DIR/lib $DS_DIR/lib-ds-deps $DS_DIR/test/drivers \
             -type f \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' \) | sort)"
  FILES="$FILES
  $DS_DIR/shadow-cljs.edn
  $DS_DIR/deps.edn
  $DS_DIR/harness/run-cljs.sh"

  mapfile -t _SA < <(COHOST_DIR="$COHOST_DIR" "$BB" harness/aot-stamp.bb \
    cljs "$(node --version)" "shadow-cljs $SHADOW_VERSION cp:$RESOLVED_CP" $FILES)
  STAMP="${_SA[0]}"; ARTIFACT="${_SA[1]}"
}

ADDON="$EVE_ROOT/build/Release/mmap_cas.node"

ensure() {
  compute_artifact
  # A failed ensure must not leave a pointer to another basis's artifact.
  rm -f "$POINTER"
  if [ ! -f "$ADDON" ]; then
    local t0=$SECONDS
    (cd "$EVE_ROOT" && npm install --no-audit --no-fund >/dev/null 2>&1)
    echo "AOT-BUILD eve-node-addon $((SECONDS-t0))s" >&2
  fi
  if [ -f "$ARTIFACT/main.js" ]; then
    printf '%s' "$ARTIFACT" > "$POINTER"
    return 0
  fi
  local t0=$SECONDS
  local tmp="$ARTIFACT.tmp.$$"
  mkdir -p "$tmp" "$(dirname "$ARTIFACT")"
  clojure -Sdeps "{:paths $PATHS_EDN :deps {thheller/shadow-cljs {:mvn/version \"$SHADOW_VERSION\"}}}" \
    -M -m shadow.cljs.devtools.cli release one-node
  cp "$DS_DIR/target/one-node/main.js" "$tmp/main.js"
  mv "$tmp" "$ARTIFACT"
  echo "AOT-BUILD cljs $((SECONDS-t0))s stamp=$STAMP" >&2
  printf '%s' "$ARTIFACT" > "$POINTER"
}

case "${1:-}" in
  ensure)
    ensure
    echo "$ARTIFACT"
    ;;
  run)
    # The timed path: exec the artifact the untimed ensure validated for
    # this basis and recorded in the pointer — no stamp, classpath or
    # file-list computation. Without a (live) pointer, fall back to ensure.
    shift
    if [ -f "$POINTER" ]; then
      A="$(cat "$POINTER")"
      if [ -f "$A/main.js" ]; then
        EVE_MMAP_ADDON="$ADDON" exec node "$A/main.js" "$@"
      fi
    fi
    ensure
    A="$ARTIFACT"
    EVE_MMAP_ADDON="$ADDON" exec node "$A/main.js" "$@"
    ;;
  run-stamped)
    # The pre-pointer timed path, kept for comparison cells: the full
    # basis stamp on every launch, then the artifact.
    shift
    ensure
    EVE_MMAP_ADDON="$ADDON" exec node "$ARTIFACT/main.js" "$@"
    ;;
  *)
    echo "usage: run-cljs.sh ensure|run|run-stamped <args...>" >&2
    exit 2
    ;;
esac
