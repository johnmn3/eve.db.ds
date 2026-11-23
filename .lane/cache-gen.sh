#!/usr/bin/env bash
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
export CLJ_CONFIG=/home/hatch/workspace/eve-env/cljconfig
cd ~/workspace/syntax-formalization/lanes/eve-db-ds
CP=$(clojure -Spath 2>.lane/spath.err)
echo "SPATH-EXIT=$? CPLEN=${#CP}" 
echo "$CP" > .lane/classpath.txt
clj-kondo --lint "$CP" > .lane/cache-gen.log 2>&1
echo "KONDO-CACHE-EXIT=$?"
ls .clj-kondo/.cache/v1 2>/dev/null
echo CACHE-GEN-DONE
