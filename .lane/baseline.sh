#!/usr/bin/env bash
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
export CLJ_CONFIG=/home/hatch/workspace/eve-env/cljconfig
export TMPDIR=$HOME/workspace/syntax-formalization/lanes/eve-db-ds/.lane/tmp
cd ~/workspace/syntax-formalization/lanes/eve-db-ds
JD="-J-Djava.io.tmpdir=$TMPDIR"
run() { # name, then command...
  local name=$1; shift
  echo "=== LEG $name START $(date +%H:%M:%S)"
  "$@" > ".lane/baseline/$name.log" 2>&1
  echo "=== LEG $name EXIT=$? $(date +%H:%M:%S)"
}
for r in u2-runner u3-runner u4-runner u5-runner bset-runner chain-autoclose-runner; do
  run "$r" clojure -Sdeps '{:paths ["src" "test"]}' $JD -M -m "eve.db.ds.$r"
done
run ds-suite clojure $JD -M:ds-suite -m contract.ds-suite
echo BASELINE-DONE
