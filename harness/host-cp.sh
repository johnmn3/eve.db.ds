#!/usr/bin/env bash
# host-cp.sh — print the Eve classpath roots for a hosts.edn row:
# Eve's src first, then the row's adapter roots, absolute, colon-joined.
# Usage: host-cp.sh <jvm|cljs|browser|bb|nbb|jolt|jank|...>
# Env: EVE_ROOT (or EVE_LOCAL) names the Eve tree; BB names a bb that
# can read edn (fleet bb 1.13.x; perf_shootout.sh exports it).
set -u
ROW="${1:?usage: host-cp.sh <hosts.edn row>}"
EVE="${EVE_ROOT:-${EVE_LOCAL:-}}"
if [ -z "$EVE" ] || [ ! -f "$EVE/hosts.edn" ]; then
  echo "host-cp.sh: set EVE_ROOT or EVE_LOCAL to the Eve tree" >&2
  exit 9
fi
EVE="$(cd "$EVE" && pwd)"
BB_BIN="${BB:-bb}"
exec "$BB_BIN" -e "
(require '[clojure.edn :as edn] '[clojure.string :as str])
(let [rows (edn/read-string (slurp \"$EVE/hosts.edn\"))
      roots (get rows (keyword \"$ROW\"))]
  (when-not roots (binding [*out* *err*] (println \"host-cp.sh: no row\" (keyword \"$ROW\") \"in hosts.edn\")) (System/exit 9))
  (println (str/join \":\" (map (fn [r] (str \"$EVE/\" r)) (into [\"src\"] roots)))))"