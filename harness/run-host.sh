#!/bin/bash
# eve.db.ds harness — generic runner for the hosts entered through
# ds-harness.host-main (plane A: directly on Eve):
#   jank, jolt, clje (clojerl), lg (let-go), glj (glojure).
# Usage: run-host.sh <host> <one|smoke> <args...>
# The classpath is Eve's hosts.edn roots for <host> (`bb classpath <host>`
# in EVE_ROOT), then this repo's lib pieces, the shipped drivers and the
# harness. Binaries: JANK, JOLT, CLOJERL env (else on PATH); LG and GLJ
# default to EVE_ROOT's target/lg-eve/lg-eve and target/glj-eve/glj-eve.
set -u
H="$1"; shift
EVE_ROOT="${EVE_ROOT:-${EVE_LOCAL:-}}"
[ -n "$EVE_ROOT" ] && [ -f "$EVE_ROOT/hosts.edn" ] || { echo "run-host.sh: set EVE_ROOT to the Eve tree (release pin checkout)" >&2; exit 9; }
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
BBBIN="${BB:-bb}"
EVE_CP="$(cd "$EVE_ROOT" && "$BBBIN" classpath "$H")" || exit 8
CP="$EVE_CP:$REPO/src:$REPO/lib:$REPO/lib-ds-deps:$REPO/test/drivers:$REPO/harness"
case "$H" in
  jank)  export LC_ALL=${JANK_LOCALE:-C.utf8}
         exec "${JANK:-jank}" --module-path "$CP" run-main ds-harness.host-main -- "$@" ;;
  jolt)  exec "${JOLT:-jolt}" -Scp "$CP" -m ds-harness.host-main "$@" ;;
  clje)  PA=(); IFS=':' read -ra P <<< "$CP"; for p in "${P[@]}"; do PA+=(-pa "$p"); done
         exec "${CLOJERL:-clojerl}" "${PA[@]}" -m ds-harness.host-main "$@" ;;
  lg|glj)
         ARGS=""; for x in "$@"; do ARGS="$ARGS \"$x\""; done
         EXPR="(require 'ds-harness.host-main) (ds-harness.host-main/-main$ARGS)"
         if [ "$H" = lg ]; then
           # let-go with Eve's Go package: `bb lg-eve` in EVE_ROOT builds it
           exec "${LG:-$EVE_ROOT/target/lg-eve/lg-eve}" -source-paths "$CP" -e "$EXPR"
         else
           # glojure with Eve's Go packages: `bb glj-eve` in EVE_ROOT builds it
           export GLJ_CLASSPATH="$CP"
           exec "${GLJ:-$EVE_ROOT/target/glj-eve/glj-eve}" -e "$EXPR"
         fi ;;
  *)     echo "run-host.sh: unknown host $H" >&2; exit 9 ;;
esac
