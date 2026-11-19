#!/usr/bin/env bash
# alpha_gate.sh — the alpha contract gate (DS-IN-EVE-PLAN v1 §4,
# carried into v2): the only Eve namespace a consumer may touch is
# eve.alpha. Fails on any eve.* require or fully-qualified eve.*
# call outside eve.alpha in the Clojure sources of src/, test/ and
# scripts/.
#
# Mechanical rule: scan *.clj/*.cljc/*.cljs under src/, test/,
# scripts/ for tokens `eve.<segment>`; a token is a violation unless
# the segment is `alpha` (eve.alpha itself) or `db` (eve.db.ds.* —
# this library's own namespaces, not Eve's). The scan is textual:
# a hit in a comment or string is reported too, and the owning unit
# judges it — the gate's job is that no hit passes silently.
#
# Exit 1 with the violation list, or print ALPHA-GATE OK.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

find "$ROOT/src" "$ROOT/test" "$ROOT/scripts" -type f \
     \( -name '*.clj' -o -name '*.cljc' -o -name '*.cljs' \) -print0 \
  | sort -z > "$WORK/files.list"

violations=0
scanned=0
while IFS= read -r -d '' f; do
  scanned=$((scanned+1))
  # one line per offending token occurrence: file:line:token
  hits=$(grep -nEo '\beve\.[A-Za-z0-9_-]+' "$f" \
         | grep -vE ':(eve\.(alpha|db))$' || true)
  if [ -n "$hits" ]; then
    while IFS= read -r h; do
      ln=${h%%:*}; tok=${h##*:}
      echo "ALPHA-VIOLATION ${f#$ROOT/}:$ln $tok"
      violations=$((violations+1))
    done <<< "$hits"
  fi
done < "$WORK/files.list"

echo "ALPHA-GATE scanned=$scanned files violations=$violations"
if [ "$violations" -gt 0 ]; then
  echo "ALPHA-GATE: FAIL"
  exit 1
fi
echo "ALPHA-GATE: OK"
