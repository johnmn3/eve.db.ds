#!/usr/bin/env bash
# diff_upstream.sh — the DS-IN-EVE-PLAN v2 §3 gate.
#
# Regenerates the pristine renamed upstream baseline (via
# scripts/vendor_upstream_ds.sh --emit-src) and diffs it against the
# working src/ tree. For every VERBATIM and VERBATIM+TRAP-LIST file,
# every changed or deleted baseline line must be a row of
# docs/TRAP-LIST.md for that file, and no line may be inserted (a new
# line has no baseline row). Type-swap (db.cljc) and new-class
# (conn.cljc, serialize.cljc) files are reported, not gated — those
# units replace them wholesale. Files present in src/ but not in the
# baseline are new code, reported only.
#
# Exit 1 if any gated file has an uncovered hunk.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

bash "$ROOT/scripts/vendor_upstream_ds.sh" --emit-src "$WORK/pristine" >/dev/null

# trap rows: "file<TAB>line" pairs out of docs/TRAP-LIST.md
ROWS="$WORK/rows.tsv"
grep -oE '^\| `src/[^`]+` \| [0-9]+' "$ROOT/docs/TRAP-LIST.md" \
  | sed -E 's/^\| `//; s/` \| /\t/' > "$ROWS" || true

row_covered() { # file line
  grep -qF "$1	$2" "$ROWS"
}

# gated classes (v2 §3 + src/UPSTREAM-SRC.txt)
# U6 addendum: core.cljc is REPORT_ONLY, not gated — plan §3 never
# classified it (U3's filing; see docs/TRAP-LIST.md's addendum). It
# is the rewrite's adapter surface (conn resolution, delivery, the
# §4 handle API and additive vars), rebuilt wholesale like the
# type-swap/new classes below, not a verbatim file with trap hunks.
GATED="eve/db/ds/built_ins.cljc eve/db/ds/lru.cljc eve/db/ds/parser.cljc
eve/db/ds/pull_parser.cljc eve/db/ds/util.cljc
eve/db/ds/query_v3.cljc eve/db/ds/inline.clj eve/db/ds/pprint.clj
eve/db/ds/js.cljs eve/db/ds/query.cljc eve/db/ds/pull_api.cljc
eve/db/ds/impl/entity.cljc eve/db/ds/datafy.cljc
eve/db/ds/externs.js data_readers.clj deps.cljs"
REPORT_ONLY="eve/db/ds/db.cljc eve/db/ds/conn.cljc eve/db/ds/serialize.cljc eve/db/ds/core.cljc"

fail=0
check_file() { # relpath gated?
  local rel=$1 gated=$2
  local pristine="$WORK/pristine/$rel" current="$ROOT/src/$rel"
  if [ ! -f "$current" ]; then
    echo "MISSING  src/$rel (in baseline, absent in tree)"
    [ "$gated" = 1 ] && fail=1
    return
  fi
  if cmp -s "$pristine" "$current"; then
    echo "SAME     src/$rel"
    return
  fi
  if [ "$gated" = 0 ]; then
    echo "DIFFERS  src/$rel (report-only class)"
    return
  fi
  # gated: collect changed baseline line numbers from a -U0 diff.
  # awk emits "oldstart oldcount newcount" per hunk header.
  local uncovered=0 ostart ocount ncount i ln
  while read -r ostart ocount ncount; do
    for ((i=0; i<ocount; i++)); do
      ln=$((ostart+i))
      row_covered "src/$rel" "$ln" || { echo "UNCOVERED src/$rel baseline line $ln changed/deleted, no trap row"; uncovered=1; }
    done
    if [ "$ncount" -gt "$ocount" ]; then
      echo "UNCOVERED src/$rel inserts $((ncount-ocount)) line(s) near baseline line $ostart (insertions have no baseline row)"
      uncovered=1
    fi
  done < <(diff -U0 "$pristine" "$current" | awk '
    /^@@ /{ o=$2; n=$3; sub(/^-/,"",o); sub(/^\+/,"",n);
            split(o,op,","); split(n,np,",");
            print op[1], (op[2]==""?1:op[2]), (np[2]==""?1:np[2]) }')
  if [ "$uncovered" = 1 ]; then
    echo "FAIL     src/$rel"
    fail=1
  else
    echo "COVERED  src/$rel (all hunks on trap rows)"
  fi
}

for rel in $GATED; do
  # externs.js lives under eve/db/ds in both trees
  check_file "$rel" 1
done
for rel in $REPORT_ONLY; do
  check_file "$rel" 0
done

# new files in src/ that the baseline does not have: report only
(cd "$ROOT/src" && find . -type f | sed 's|^\./||' | sort) > "$WORK/tree.txt"
(cd "$WORK/pristine" && find . -type f | sed 's|^\./||' | sort) > "$WORK/base.txt"
while IFS= read -r extra; do
  echo "NEW      src/$extra (not in baseline)"
done < <(comm -23 "$WORK/tree.txt" "$WORK/base.txt")

if [ "$fail" = 1 ]; then
  echo "DIFF-UPSTREAM: FAIL"
  exit 1
fi
echo "DIFF-UPSTREAM: OK"
