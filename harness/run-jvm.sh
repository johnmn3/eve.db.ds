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
SDEPS="{:deps {eve/eve {:local/root \"$EVE_LOCAL\"}}}"
JOPTS=(--add-opens=java.base/java.lang=ALL-UNNAMED --enable-native-access=ALL-UNNAMED)
if [ "${JVM_AOT:-1}" != 1 ]; then
  exec clojure "${JOPTS[@]/#/-J}" -Sdeps "$SDEPS" -M -m "$NS" "$@"
fi
# Startup: JVM_AOT=1 (the default) runs from a persistent AOT class cache
# of Eve + the vendored libs (lib/, lib-ds-deps/), keyed by every source
# file's path, size and mtime on the classpath plus the entry namespace:
# any edit selects a fresh cache (compiled once, ~6 s), an unchanged tree
# skips Eve's compile. This repo's own namespaces (src/eve/db, harness,
# drivers) still load from source: an AOT-compiled eve.record/defrecord
# breaks (ArityException in map->T-in / ->T-in — the Eve record macro's
# private slab factory under AOT; written up for eve-release), and code
# compiled against the AOT record classes then misses the source-loaded
# ones. JVM_AOT=0 is the plain `clojure -M -m` launch.
CP="$(clojure -Spath -Sdeps "$SDEPS")" || exit 8
KEY=$( { echo "$NS"; IFS=':'; for r in $CP; do
         if [ -d "$r" ]; then find "$r" \( -name '*.clj*' -o -name '*.class' \) -printf '%p %s %T@\n'; else echo "$r"; fi
       done; } | sort | md5sum | cut -c1-16)
CACHE="${JVM_AOT_DIR:-$HERE/../.jvm-aot}/tree-$KEY"
if [ ! -f "$CACHE/.done" ]; then
  rm -rf "$CACHE" && mkdir -p "$CACHE/classes"
  java "${JOPTS[@]}" -cp "$CACHE/classes:$CP" clojure.main \
    -e "(binding [*compile-path* \"$CACHE/classes\"] (compile '$NS))" > "$CACHE/compile.log" 2>&1 \
    || { echo "run-jvm.sh: AOT compile failed, see $CACHE/compile.log" >&2; rm -rf "$CACHE"; exit 8; }
  # keep Eve + vendored libs; this repo's own namespaces load from source
  rm -rf "$CACHE/classes/eve/db" "$CACHE/classes/ds_harness" "$CACHE/classes/contract" \
         "$CACHE"/classes/ds_harness*.class "$CACHE"/classes/contract*.class
  touch "$CACHE/.done"
fi
exec java "${JOPTS[@]}" -cp "$CACHE/classes:$CP" clojure.main -m "$NS" "$@"
