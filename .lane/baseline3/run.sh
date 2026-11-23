#!/bin/bash
# Baseline completion: remaining ds-suite namespaces in small groups,
# one JVM per group (driver's exact classpath + flags, -Xmx3g).
cd ~/workspace/syntax-formalization/lanes/eve-db-ds || exit 1
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
CP=$(cat .lane/baseline3/ds-suite.cp)
run_group() {
  local name="$1"; shift
  echo "=== GROUP $name: $* ==="
  java -Xmx3g -Djava.io.tmpdir=$PWD/.lane/tmp \
    --enable-native-access=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED \
    -cp "$CP" clojure.main -m contract.ds-suite "$@" \
    > ".lane/baseline3/ds-suite-$name.log" 2>&1
  echo "group $name exit=$?"
  grep "DS-SUITE-SUMMARY" ".lane/baseline3/ds-suite-$name.log" || echo "GROUP $name NO SUMMARY"
}
run_group gA eve.db.ds.test.pull-parser eve.db.ds.test.query-rules eve.db.ds.test.query-v3
run_group gB eve.db.ds.test.serialize eve.db.ds.test.storage eve.db.ds.test.transact
run_group gC eve.db.ds.test.tuples eve.db.ds.test.upsert eve.db.ds.test.validation
echo ALL-GROUPS-DONE
