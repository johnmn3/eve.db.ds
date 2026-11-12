#!/bin/bash
# eve.db.ds harness — nbb runner (plane A: directly on Eve).
# Usage: run-nbb.sh <perf|one> <args...>
# Entry is ds-harness.nbb-main (process slab first, then dispatch).
# The classpath is Eve's :nbb roots via scripts/eve_dev_cp.sh with
# EVE_ROOT naming the Eve tree — NEVER eve_dev_cp.sh's :dev pin —
# then this repo's lib pieces, the shipped drivers and the harness.
# Eve's node mmap addon must be built in the Eve tree: if
# build/Release/*.node is absent this runner npm-installs there
# (the scripts/ds_suite_grok.sh pattern). nbb runs FROM the Eve tree
# (the one_nbb / ds_suite.py nbb cwd convention). NBB env names the
# nbb binary; when no nbb is installed, the npx shim nbb@1.6.214 is
# used (also the ds_suite_grok.sh pattern).
set -u
EVE_ROOT="${EVE_ROOT:?run-nbb.sh: set EVE_ROOT to the Eve tree (release pin checkout)}"
[ -f "$EVE_ROOT/hosts.edn" ] || { echo "run-nbb.sh: EVE_ROOT=$EVE_ROOT has no hosts.edn" >&2; exit 9; }
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
export PATH="$HOME/workspace/tools/bin:$PATH"   # fleet bb: eve_dev_cp.sh expands hosts.edn with it
if ! ls "$EVE_ROOT"/build/Release/*.node >/dev/null 2>&1; then
  echo "run-nbb.sh: building Eve node addon in $EVE_ROOT (npm install)" >&2
  (cd "$EVE_ROOT" && npm install --no-audit --no-fund) || exit 8
fi
if [ -n "${NBB:-}" ]; then
  NBB_BIN="$NBB"
elif command -v nbb >/dev/null 2>&1; then
  NBB_BIN=nbb
else
  SHIM="${TMPDIR:-/tmp}/ds-harness-shim"
  mkdir -p "$SHIM"
  printf '#!/bin/sh\nexec npx -y nbb@1.6.214 "$@"\n' > "$SHIM/nbb"
  chmod +x "$SHIM/nbb"
  NBB_BIN="$SHIM/nbb"
fi
EVE_CP="$(EVE_ROOT="$EVE_ROOT" bash "$REPO/scripts/eve_dev_cp.sh" nbb)" || exit 8
CP="$EVE_CP:$REPO/src:$REPO/lib:$REPO/lib-ds-deps:$REPO/test/drivers:$REPO/harness"
cd "$EVE_ROOT" || exit 9
exec "$NBB_BIN" --classpath "$CP" -m ds-harness.nbb-main "$@"
