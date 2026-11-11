#!/usr/bin/env bash
# Venue-only full suite. All legs run, even after another host reports red.
set -u
set -o pipefail
DS=$(cd "$(dirname "$0")/.." && pwd)
J=${DS_SUITE_JOB_ROOT:-$(dirname "$DS")}
free=$(df -Pk "$J" | awk 'NR==2 {print $4}')
[[ "$free" =~ ^[0-9]+$ ]] || { echo DISK-PROBE-FAIL; exit 1; }
[ "$free" -ge 4194304 ] && [ $((free/2)) -ge 3145728 ] || { echo DISK-BOUND-HOLD; exit 1; }
inodes=$(df -Pi "$J" | awk 'NR==2 {print $4}')
[[ "$inodes" =~ ^[0-9]+$ ]] && [ "$inodes" -ge 50000 ] || { echo DISK-INODE-HOLD; exit 1; }
echo "HALF_FREE_KB=$((free/2)) NEED_KB=3145728 FREE_INODES=$inodes MIN_FREE_INODES=50000"
export PATH="$J/shim:/workspace/jobs/vol/bin:$PATH"
mkdir -p "$J/shim"
printf '#!/bin/sh\nexec npx -y nbb@1.6.214 "$@"\n' > "$J/shim/nbb"
chmod +x "$J/shim/nbb"
bb --version; java -version; node --version; nbb --version < /dev/null; basilisp version
cd "$DS"
echo DS-HEAD "$(git rev-parse HEAD)"
EVESHA=$(awk -F= '$1 == "SHA" {print $2}' scripts/eve_dev_cp.sh)
echo EVE-DEV-PIN "$EVESHA"
timeout 1500 clojure -A:dev:ds-suite -Spath > "$J/jvm.cp" < /dev/null
rc=$?; echo RESOLVE-RC=$rc
[ "$rc" -eq 0 ] || exit "$rc"
GL="$HOME/.gitlibs/libs/eve/eve/$EVESHA/eve"
[ -f "$GL/hosts.edn" ] || { echo EVE-PIN-RESOLUTION-FAIL; exit 1; }
cp -a "$GL" "$J/eve" || exit 1
export EVE_ROOT="$J/eve"
echo EVE-TREE "gitlibs $EVESHA"
(cd "$EVE_ROOT" && timeout 900 npm install --no-audit --no-fund < /dev/null) || exit 1
ls "$EVE_ROOT"/build/Release/*.node || exit 1
export PYTHONPYCACHEPREFIX="$J/lpycache"
S=""
for host in jvm bb nbb lpy; do
  echo "===$host per-namespace DataScript suite"
  ns_timeout=180
  [ "$host" != lpy ] || ns_timeout=300
  python3 "$DS/scripts/ds_suite.py" "$host" --timeout "$ns_timeout" --out "$J/results/$host" < /dev/null
  rc=$?
  echo "LEG-RC host=$host rc=$rc"
  S="$S $host=$rc"
done
echo "SUITE-GATE-SUMMARY$S"
du -sk "$J"; df -Pk "$J"; df -Pi "$J"
case "$S" in *=[1-9]*) exit 1;; esac
