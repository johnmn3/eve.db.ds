#!/bin/bash
# eve.db.ds harness — Babashka runner (plane A: directly on Eve).
# Usage: run-bb.sh <contract|perf|one> <args...>
# Entry is ds-harness.bb-main, which loads Eve's bb completion before
# the port's namespaces (the shipped bb seat pattern). The classpath
# is composed the way Eve's own bb tasks do: Eve's src + hosts.edn :bb
# adapter roots (absolute), then this repo's lib pieces, the shipped
# drivers and the harness. BB env names the bb binary (fleet
# 1.13.226 — an older bb on the PATH changes the result class).
set -u
# EVE_LOCAL, or EVE_ROOT, names the Eve tree (a release pin checkout).
# There is NO default tree — the previous default named a gone seat
# checkout, so an unset env now fails with guidance instead of
# silently measuring a stale or missing tree.
EVE_LOCAL="${EVE_LOCAL:-${EVE_ROOT:-}}"
if [ -z "$EVE_LOCAL" ] || [ ! -f "$EVE_LOCAL/hosts.edn" ]; then
  echo "run-bb.sh: set EVE_LOCAL or EVE_ROOT to the Eve tree (release pin checkout)" >&2
  exit 9
fi
# BB names the babashka binary. It must be a dynamically linked build: a
# static bb cannot dlopen libatomic.so.1 (babashka.ffi), and Eve's regions
# then fall back to lock-only CAS with a warning. Default: the dynamic bb on
# this box, else bb on PATH.
DYN_BB=/workspace/coord-campaign/contract-test/bin/bb
BB="${BB:-$( [ -x "$DYN_BB" ] && echo "$DYN_BB" || command -v bb )}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
# Eve's own bb classpath (src + hosts.edn :bb roots), not a copied root
# list: a copy goes stale when the Eve tree adds a root (the private
# release line's :bb adds adapters/crypto/portable/src; the copy lacked it).
CP="$(cd "$EVE_LOCAL" && "$BB" classpath bb)" || exit 8
CP="$CP:$REPO/src:$REPO/lib:$REPO/lib-ds-deps:$REPO/test/drivers:$REPO/harness"
exec "$BB" -cp "${CP#:}" -m ds-harness.bb-main "$@"
