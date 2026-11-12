#!/bin/bash
# eve.db.ds harness — Basilisp runner (plane A: directly on Eve).
# Usage: run-lpy.sh <perf|one> <args...>
# The classpath is Eve's :lpy roots via scripts/eve_dev_cp.sh with
# EVE_ROOT naming the Eve tree — NEVER eve_dev_cp.sh's :dev pin —
# then this repo's lib pieces, the shipped drivers and the harness.
# Entry is the scripts/ds_suite.py lpy pattern: host-completion first,
# then ds-harness.lpy-main applied to *command-line-args*. BASILISK
# env names the basilisp binary.
set -u
EVE_ROOT="${EVE_ROOT:?run-lpy.sh: set EVE_ROOT to the Eve tree (release pin checkout)}"
[ -f "$EVE_ROOT/hosts.edn" ] || { echo "run-lpy.sh: EVE_ROOT=$EVE_ROOT has no hosts.edn" >&2; exit 9; }
BASILISK="${BASILISK:-$HOME/.local/bin/basilisp}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
export PATH="$HOME/workspace/tools/bin:$PATH"   # fleet bb: eve_dev_cp.sh expands hosts.edn with it
EVE_CP="$(EVE_ROOT="$EVE_ROOT" bash "$REPO/scripts/eve_dev_cp.sh" lpy)" || exit 8
CP="$EVE_CP:$REPO/src:$REPO/lib:$REPO/lib-ds-deps:$REPO/test/drivers:$REPO/harness"
export BASILISP_DO_NOT_CACHE_NAMESPACES=true
export PYTHONDONTWRITEBYTECODE=1
export PYTHONPYCACHEPREFIX="${PYTHONPYCACHEPREFIX:-$REPO/.lpycache}"
# ds_suite.py passes the classpath paths to basilisp in reverse order.
PATHS=()
IFS=':' read -ra PARTS <<< "$CP"
for (( i=${#PARTS[@]}-1; i>=0; i-- )); do PATHS+=(-p "${PARTS[i]}"); done
EXPR='(require (quote eve.host-completion)) (require (quote ds-harness.lpy-main)) (apply (resolve (quote ds-harness.lpy-main/-main)) *command-line-args*)'
cd "$REPO" || exit 9
exec "$BASILISK" run "${PATHS[@]}" -c "$EXPR" -- "$@"
