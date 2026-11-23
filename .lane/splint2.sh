#!/usr/bin/env bash
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
export CLJ_CONFIG=/home/hatch/workspace/eve-env/cljconfig
cd ~/workspace/syntax-formalization/lanes/eve-db-ds
bb splint src test > census-raw/splint.txt 2>&1
echo "SPLINT2-EXIT=$?"
echo SPLINT2-DONE
