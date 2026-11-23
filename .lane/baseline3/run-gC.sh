#!/bin/bash
cd ~/workspace/syntax-formalization/lanes/eve-db-ds || exit 1
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
CP=$(cat .lane/baseline3/ds-suite.cp)
java -Xmx3g -Djava.io.tmpdir=$PWD/.lane/tmp \
  --enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED \
  -cp "$CP" clojure.main -m contract.ds-suite eve.db.ds.test.tuples eve.db.ds.test.upsert eve.db.ds.test.validation \
  > ".lane/baseline3/ds-suite-gC.log" 2>&1
echo "gC exit=$?"
grep "DS-SUITE-SUMMARY" ".lane/baseline3/ds-suite-gC.log" || echo "GROUP gC NO SUMMARY"
echo GC-DONE
