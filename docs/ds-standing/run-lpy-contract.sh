#!/bin/bash
# ds-standing lane — basilisp (lpy) contract closure leg at the eve.db.ds pin.
# Eve: SeniorCareMarket/eve @ 45935b9570bc286abc2a5789bf504c204778f899
#      (the pin named in this repo's deps.edn)
# DS:  this clone (johnmn3/eve.db.ds, branch lane/ds-standing)
# Shape: harness/run-lpy.sh contract write <dom>, then a FRESH PROCESS
# contract read <dom>. Verdict = both CONTRACT-*-OK lines + equal seals,
# or the exact failure text. This is a correctness leg, not a timing row.
set -u
avail_of() { df -Pk "$1" | awk 'NR==2{print $4}'; }
AV=$(avail_of "$HOME")
if [ -z "${AV:-}" ] || ! [ "$AV" -gt 0 ] 2>/dev/null; then
  echo "DISK-GUARD-FAIL-CLOSED AV='${AV:-}'"; exit 1
fi
NEED_KB=4194304; HALF_KB=$((AV/2))
echo "DISK-AVAIL-KB $AV DISK-HALF-KB $HALF_KB DISK-NEED-KB $NEED_KB"
if [ "$NEED_KB" -gt "$HALF_KB" ]; then echo "DISK-REFUSE need>half"; exit 3; fi
W="$HOME/ds-standing-lpy"; rm -rf "$W"; mkdir -p "$W"; cd "$W" || exit 9
echo "BASILISK-PATH $(command -v basilisk || echo MISSING)"
basilisk version 2>/dev/null | head -1 || true
echo "BB-PATH $(command -v bb || echo MISSING)"
bb --version 2>/dev/null || true
git clone -q https://github.com/SeniorCareMarket/eve.git eve || { echo "EVE-CLONE-FAIL"; exit 2; }
cd eve && git checkout -q 45935b9570bc286abc2a5789bf504c204778f899 || { echo "EVE-PIN-CHECKOUT-FAIL"; exit 2; }
echo "EVE-HEAD $(git rev-parse HEAD)"
cd "$W" || exit 9
DS_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
echo "DS-DIR $DS_DIR"
export EVE_ROOT="$W/eve"
export BASILISK="$(command -v basilisk)"
cd "$DS_DIR" || exit 9
timeout 1500 bash harness/run-lpy.sh contract write "$W/dom" > "$W/write.log" 2>&1; RC1=$?
echo "LPY-CONTRACT-WRITE-RC $RC1"
cat "$W/write.log"
timeout 1500 bash harness/run-lpy.sh contract read "$W/dom" > "$W/read.log" 2>&1; RC2=$?
echo "LPY-CONTRACT-READ-RC $RC2"
cat "$W/read.log"
echo "DOMAIN-FILES:"; ls "$W/dom" 2>/dev/null | head -20
echo "LPY-CONTRACT-DONE write=$RC1 read=$RC2"
exit 0
