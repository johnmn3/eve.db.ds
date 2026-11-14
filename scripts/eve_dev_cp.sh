#!/usr/bin/env bash
# Print Eve src + hosts.edn roots for a host, using the PUBLIC Eve pin
# from the root deps.edn (SeniorCareMarket/eve, sha below).
# Usage: scripts/eve_dev_cp.sh <jvm|bb|nbb|lpy>
# Optional: EVE_ROOT=/path/to/eve (an Eve dev tree) skips resolution —
# that env override is how dev runs point at a non-release Eve tree.
set -euo pipefail
HOST="${1:?usage: scripts/eve_dev_cp.sh <jvm|bb|nbb|lpy>}"
SHA=383097e063df5eec07b6b6ee64c72e62a3176fb1
URL=https://github.com/SeniorCareMarket/eve.git
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -n "${EVE_ROOT:-}" && -f "${EVE_ROOT}/hosts.edn" && -f "${EVE_ROOT}/bb.edn" ]]; then
  EVE="$EVE_ROOT"
else
  CANDIDATES=(
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
      if git --git-dir="$ROOT/.git" cat-file -t "${SHA}" >/dev/null 2>&1; then
        extract "${SHA}"
      else
        git --git-dir="$ROOT/.git" fetch --no-tags --depth=1 "$URL" \
          "+refs/heads/release/eve-0.9.3.2:refs/dev-eve/${SHA}"
        extract "${SHA}"
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
