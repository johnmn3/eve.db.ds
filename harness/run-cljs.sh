#!/bin/bash
# eve.db.ds harness — compiled ClojureScript runner (the shootout's
# `cljs` member: Eve's hosts.edn :cljs host on Node, ahead-of-time
# compiled by shadow-cljs — distinct from the SCI-interpreted nbb member).
# Usage: run-cljs.sh <one|smoke> <args...>
# The compile classpath is Eve's own :node-test alias (its shadow-cljs
# pin and the :cljs adapter roots, made absolute) plus this repo's src,
# lib pieces, drivers and harness; the build config is
# harness/cljs/shadow-cljs.edn (entry ds-harness.cljs-main).
# Pre-compilation: the compiled script is a durable on-disk artifact in
# ${CLJS_BUILD_DIR:-<repo>/.cljs-build}/tree-<key>, the key an md5 of
# every source file's path, size and mtime on that classpath; any edit
# selects a fresh dir (one `shadow-cljs release`, ~1-2 min), an
# unchanged tree just runs `node out/ds-cljs.js`. Runs FROM the Eve tree
# with Eve's mmap_cas addon (npm-installed there when missing).
set -u
EVE_ROOT="${EVE_ROOT:?run-cljs.sh: set EVE_ROOT to the Eve tree (release pin checkout)}"
[ -f "$EVE_ROOT/hosts.edn" ] || { echo "run-cljs.sh: EVE_ROOT=$EVE_ROOT has no hosts.edn" >&2; exit 9; }
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
if ! ls "$EVE_ROOT"/build/Release/mmap_cas.node >/dev/null 2>&1; then
  echo "run-cljs.sh: building Eve node addon in $EVE_ROOT (npm install)" >&2
  (cd "$EVE_ROOT" && npm install --no-audit --no-fund) >&2 || exit 8
fi
EVE_CP="$(cd "$EVE_ROOT" && clojure -Spath -A:node-test)" || exit 8
CP=""
IFS=':' read -ra PARTS <<< "$EVE_CP"
for p in "${PARTS[@]}"; do case "$p" in /*) CP="$CP:$p" ;; *) CP="$CP:$EVE_ROOT/$p" ;; esac; done
CP="${CP#:}:$REPO/src:$REPO/lib:$REPO/lib-ds-deps:$REPO/test/drivers:$REPO/harness"
KEY=$( { IFS=':'; for r in $CP; do [ -d "$r" ] && find "$r" \( -name '*.clj*' -o -name '*.js' -o -name '*.edn' \) -not -path '*/node_modules/*' -printf '%p %s %T@\n'; done; printf '%s\n' "$CP"; md5sum "$HERE/cljs/shadow-cljs.edn"; } | sort | md5sum | cut -c1-16)
BDIR="${CLJS_BUILD_DIR:-$REPO/.cljs-build}/tree-$KEY"
OUTJS="$BDIR/out/ds-cljs.js"
if [ ! -f "$BDIR/.done" ]; then
  mkdir -p "$BDIR" && cp "$HERE/cljs/shadow-cljs.edn" "$BDIR/" || exit 8
  JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
  echo "run-cljs.sh: compiling ds-cljs into $BDIR (one-time per source tree)" >&2
  ( cd "$BDIR" && ln -sfn "$EVE_ROOT/node_modules" node_modules && \
    "$JAVA" -cp "$CP" clojure.main -m shadow.cljs.devtools.cli release ds-cljs ) >&2 || exit 8
  [ -f "$OUTJS" ] || { echo "run-cljs.sh: no $OUTJS after build" >&2; exit 8; }
  touch "$BDIR/.done"
fi
cd "$EVE_ROOT" || exit 9
export EVE_MMAP_ADDON="${EVE_MMAP_ADDON:-$EVE_ROOT/build/Release/mmap_cas.node}"
exec node "$OUTJS" "$@"
