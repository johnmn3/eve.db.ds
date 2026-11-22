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
#   run-jank.sh run <args...>  exec the artifact named by the pointer file
#                              (.jank-aot-artifact, written by ensure) —
#                              no stamp computation on the timed path;
#                              without a live pointer, ensure first
#   run-jank.sh run-stamped <args...>  the pre-pointer path (stamp,
#                              then the artifact), for comparison cells
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
# JANK_XDG_CACHE_HOME (optional): the cache root for that jank (its PCH/module cache), for a box
# whose older jank shares the binary-version hash and would hand the new one an incompatible PCH.
if [ -n "${JANK_XDG_CACHE_HOME:-}" ]; then export XDG_CACHE_HOME="$JANK_XDG_CACHE_HOME"; fi

cd "$DS_DIR"
POINTER="${JANK_AOT_POINTER:-$DS_DIR/.jank-aot-artifact}"

# The basis computation (host roots, tools.deps classpath resolution,
# the stamp's file list) — ensure's work, and run-stamped's / run's
# fallback only. The timed `run` path skips it via the pointer file.
compute_artifact() {
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
}

ensure() {
  compute_artifact
  # A failed ensure must not leave a pointer to another basis's artifact.
  rm -f "$POINTER"
  if [ -f "$ARTIFACT/one" ]; then
    printf '%s' "$ARTIFACT" > "$POINTER"
    return 0
  fi
  local t0=$SECONDS
  local tmp="$ARTIFACT.tmp.$$"
  mkdir -p "$tmp" "$(dirname "$ARTIFACT")"
  jank --module-path "$RESOLVED_CP" compile contract.one-aot \
    -O3 --target-dir "$tmp" --name one
  mv "$tmp" "$ARTIFACT"
  echo "AOT-BUILD jank $((SECONDS-t0))s stamp=$STAMP" >&2
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
      if [ -f "$A/one" ]; then
        exec "$A/one" "$@"
      fi
    fi
    ensure
    A="$ARTIFACT"
    exec "$A/one" "$@"
    ;;
  run-stamped)
    # The pre-pointer timed path, kept for comparison cells: the full
    # basis stamp on every launch, then the artifact.
    shift
    ensure
    exec "$ARTIFACT/one" "$@"
    ;;
  *)
    echo "usage: run-jank.sh ensure|run|run-stamped <args...>" >&2
    exit 2
    ;;
esac
