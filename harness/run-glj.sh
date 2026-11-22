#!/bin/bash
# eve.db.ds harness — glojure runner (Eve's glj-eve: glojure + eve's Go
# natives, built by $EVE/impls/glojure/build.sh).
# Usage: run-glj.sh <namespace> <args...>   (calls <namespace>/-main with args)
# Env: EVE_ROOT (or EVE_LOCAL) names the Eve tree; GLJ_EVE names a built
# glj-eve (default $EVE/target/glj-eve/glj-eve, built here if missing —
# the build is cached by its own source stamp). The classpath (Eve's :glj
# hosts.edn row + this repo's roots) is cached by hosts.edn's mtime, so
# the timed path is exactly one glj-eve process.
set -u
NS="$1"; shift
EVE="${EVE_ROOT:-${EVE_LOCAL:-}}"
if [ -z "$EVE" ] || [ ! -f "$EVE/hosts.edn" ]; then
  echo "run-glj.sh: set EVE_ROOT or EVE_LOCAL to the Eve tree" >&2; exit 9
fi
HERE="$(cd "$(dirname "$0")" && pwd)"; DS="$(cd "$HERE/.." && pwd)"
GLJ_EVE="${GLJ_EVE:-$EVE/target/glj-eve/glj-eve}"
if [ ! -x "$GLJ_EVE" ]; then
  bash "$EVE/impls/glojure/build.sh" "$(dirname "$GLJ_EVE")" >&2 || exit 8
fi
KEY=$(echo "$EVE $DS $(stat -c %Y "$EVE/hosts.edn")" | md5sum | cut -c1-16)
CPFILE="$DS/.glj-cp/cp-$KEY"
if [ ! -s "$CPFILE" ]; then
  R=$(EVE_ROOT="$EVE" bash "$HERE/host-cp.sh" glj) || exit 8
  mkdir -p "$(dirname "$CPFILE")"
  printf '%s' "$R:$DS/src:$DS/lib:$DS/lib-ds-deps:$DS/test/drivers:$DS/harness" > "$CPFILE"
fi
export GLJ_CLASSPATH="$(cat "$CPFILE")"
ARGS=""; for a in "$@"; do ARGS="$ARGS \"$a\""; done
exec "$GLJ_EVE" -e "(require '$NS) ($NS/-main$ARGS)"
