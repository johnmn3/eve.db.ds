#!/bin/bash
# eve.db.ds harness — JVM runner (plane A: directly on Eve).
# Usage: run-jvm.sh <namespace> <args...>
# Env: EVE_LOCAL, or EVE_ROOT, names the Eve tree that overrides the
# lib's Eve git pin (a release pin checkout); the repo's deps.edn
# keeps the git pin. There is NO default tree — the previous default
# named a gone seat checkout, so an unset env now fails with guidance
# instead of silently measuring a stale or missing tree.
set -u
NS="$1"; shift
EVE_LOCAL="${EVE_LOCAL:-${EVE_ROOT:-}}"
if [ -z "$EVE_LOCAL" ] || [ ! -f "$EVE_LOCAL/hosts.edn" ]; then
  echo "run-jvm.sh: set EVE_LOCAL or EVE_ROOT to the Eve tree (release pin checkout)" >&2
  exit 9
fi
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE/../test" || exit 9
export CLJ_CONFIG="${CLJ_CONFIG:-$HOME/workspace/eve-env/cljconfig}"
exec clojure \
  -J--add-opens=java.base/java.lang=ALL-UNNAMED \
  -J--enable-native-access=ALL-UNNAMED \
  -Sdeps "{:deps {eve/eve {:local/root \"$EVE_LOCAL\"}}}" \
  -M -m "$NS" "$@"
