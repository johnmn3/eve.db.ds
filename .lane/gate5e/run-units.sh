#!/usr/bin/env bash
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
export CLJ_CONFIG=/home/hatch/workspace/eve-env/cljconfig
export GITLIBS=/home/hatch/.gitlibs
export TMPDIR=$HOME/workspace/syntax-formalization/lanes/eve-db-ds/.lane/tmp
cd ~/workspace/syntax-formalization/lanes/eve-db-ds
JD="-J-Djava.io.tmpdir=$TMPDIR"
for r in u2-runner u3-runner u4-runner u5-runner bset-runner chain-autoclose-runner; do
  echo "=== LEG $r START $(date +%H:%M:%S)"
  clojure -Sdeps '{:paths ["src" "test"]}' $JD -M -m "eve.db.ds.$r" > ".lane/gate5e/$r.log" 2>&1
  echo "=== LEG $r EXIT=$? $(date +%H:%M:%S)"
  tail -2 ".lane/gate5e/$r.log"
done
echo UNITS-DONE
