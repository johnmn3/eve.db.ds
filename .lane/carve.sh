#!/usr/bin/env bash
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
export CLJ_CONFIG=/home/hatch/workspace/eve-env/cljconfig
cd ~/workspace/syntax-formalization/lanes/eve-db-ds
carve --opts '{:paths ["src" "test"] :report {:format :text}}' > census-raw/carve.txt 2>&1
echo "CARVE-EXIT=$?"
echo CARVE-DONE
