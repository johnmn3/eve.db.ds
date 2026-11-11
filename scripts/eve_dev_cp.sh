#!/usr/bin/env bash
# Print Eve src + hosts.edn roots for a host, using the :dev pin
# (atom.broker lane-m/eve-db-ds-gaps-eve, eve/ subdir, sha below).
# Usage: scripts/eve_dev_cp.sh <jvm|bb|nbb|lpy>
# Optional: EVE_ROOT=/path/to/eve (a tree at that sha) skips resolution.
set -euo pipefail
HOST="${1:?usage: scripts/eve_dev_cp.sh <jvm|bb|nbb|lpy>}"
SHA=ec22aba4c0ddf4f2ff58f26b04d654d53f247d61
URL=https://github.com/johnmn3/atom.broker.git
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -n "${EVE_ROOT:-}" && -f "${EVE_ROOT}/hosts.edn" && -f "${EVE_ROOT}/bb.edn" ]]; then
  EVE="$EVE_ROOT"
else
  CANDIDATES=(
    "${GITLIBS:-$HOME/.gitlibs}/libs/eve/eve/${SHA}/eve"
    "${GITLIBS:-$HOME/.gitlibs}/libs/eve/eve/${SHA}"
  )
  EVE=""
  for c in "${CANDIDATES[@]}"; do
    if [[ -f "$c/hosts.edn" && -f "$c/bb.edn" ]]; then
      EVE="$c"
      break
    fi
  done
  if [[ -z "$EVE" ]]; then
    CACHE="$ROOT/.cpcache/eve-dev/${SHA}"
    if [[ ! -f "$CACHE/hosts.edn" ]]; then
      mkdir -p "$CACHE"
      extract() { git --git-dir="$ROOT/.git" archive "$1" | tar -x -C "$CACHE"; }
      if git --git-dir="$ROOT/.git" cat-file -t "${SHA}:eve" >/dev/null 2>&1; then
        extract "${SHA}:eve"
      else
        git --git-dir="$ROOT/.git" fetch --no-tags --depth=1 "$URL" \
          "+refs/heads/lane-m/eve-db-ds-gaps-eve:refs/dev-eve/${SHA}"
        extract "${SHA}:eve"
      fi
    fi
    EVE="$CACHE"
  fi
fi

if [[ ! -f "$EVE/hosts.edn" ]]; then
  echo "eve_dev_cp: could not resolve Eve tree at $SHA" >&2
  exit 1
fi

if ! command -v bb >/dev/null 2>&1; then
  echo "eve_dev_cp: bb is required to expand hosts.edn" >&2
  exit 1
fi
CP="$(cd "$EVE" && bb classpath "$HOST")"
# Test-only native-host support is explicit and never part of the public lib.
# bb/nbb both ship real cognitect.transit; basilisp uses the helper's :lpy
# branch and the suite driver counts its Transit-dependent tests as skipped.
if [[ -d "$ROOT/test/host-support/$HOST" ]]; then
  CP="$ROOT/test/host-support/$HOST:$CP"
fi
printf '%s\n' "$CP"
