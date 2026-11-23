#!/usr/bin/env bash
export PATH=/home/hatch/.local/bbnew-bin:/home/hatch/.local/bin:$PATH
export CLJ_CONFIG=/home/hatch/workspace/eve-env/cljconfig
cd ~/workspace/syntax-formalization/lanes/eve-db-ds
mkdir -p census-raw
clj-kondo --lint src test > census-raw/kondo.txt 2>&1; echo "KONDO-EXIT=$?" 
bb splint > census-raw/splint.txt 2>&1; echo "SPLINT-EXIT=$?"
carve --opts '{:paths ["src" "test"] :report {:format :text}}' > census-raw/carve.txt 2>&1; echo "CARVE-EXIT=$?"
cljfmt check src test > census-raw/cljfmt.txt 2>&1; echo "CLJFMT-EXIT=$?"
echo CENSUS-DONE
