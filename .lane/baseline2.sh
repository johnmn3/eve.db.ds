#!/usr/bin/env bash
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
export CLJ_CONFIG=/home/hatch/workspace/eve-env/cljconfig
export TMPDIR=$HOME/workspace/syntax-formalization/lanes/eve-db-ds/.lane/tmp
cd ~/workspace/syntax-formalization/lanes/eve-db-ds
echo "=== LEG u5-docline START $(date +%H:%M:%S)"
clojure -Sdeps '{:paths ["src" "test"] :deps {com.cognitect/transit-clj {:mvn/version "1.0.333"} cheshire/cheshire {:mvn/version "5.13.0"} metosin/jsonista {:mvn/version "0.3.13"}}}' -J-Djava.io.tmpdir=$TMPDIR -M -m eve.db.ds.u5-runner > .lane/baseline/u5-docline.log 2>&1
echo "=== LEG u5-docline EXIT=$? $(date +%H:%M:%S)"
CP=$(clojure -A:ds-suite -Spath 2>/dev/null)
echo "=== LEG ds-suite-xmx START $(date +%H:%M:%S)"
java -Xmx4g --enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED -Djava.io.tmpdir=$TMPDIR -cp "$CP" clojure.main -m contract.ds-suite > .lane/baseline/ds-suite-xmx.log 2>&1
echo "=== LEG ds-suite-xmx EXIT=$? $(date +%H:%M:%S)"
echo BASELINE2-DONE
