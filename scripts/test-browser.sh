#!/usr/bin/env bash
# Run a DS suite in a real browser (headless Chromium): scripts/test-browser.sh [laws|bset]
#
# Builds the test page with shadow-cljs against Eve's :browser roots at the deps.edn
# pin, serves it cross-origin isolated, drives headless Chromium, and exits 0 only
# when the suite is green. Full record in target/ds-browser/ (build.log,
# chrome-<suite>.log, result-<suite>.edn). Env: DS_CHROME (Chromium binary),
# DS_BROWSER_TIMEOUT_S (default 900), DS_BROWSER_CONSOLE=1 (echo the browser log).
# The driver itself is scripts/test-browser.clj (babashka; modeled on Eve's
# scripts/test-browser.clj). Requires bb 1.13+ on PATH (this fleet: ~/workspace/tools/bin).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export CLJ_CONFIG="${CLJ_CONFIG:-$HOME/workspace/eve-env/cljconfig}"
export JAVA_HOME="${JAVA_HOME:-$HOME/workspace/tools/jdk-21}"
export PATH="$HOME/workspace/tools/bin:$HOME/.local/bin:$PATH"
cd "$ROOT"
exec bb scripts/test-browser.clj "${1:-laws}"
