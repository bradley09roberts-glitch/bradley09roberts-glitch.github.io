#!/bin/bash
# Development runs that judged each improvement on 2014-2018 only (FIREWALL=1: every 2019+ result hidden).
# Usage: devtools/dev_runs.sh <scratch> <out_root>
set -euo pipefail
S=$1; R=$2; HERE=$(cd "$(dirname "$0")/.." && pwd); export OMP_NUM_THREADS=1 FIREWALL=1
for d in dev dev_s1 dev_base; do mkdir -p "$R/$d"; ML_DIR="$R/$d" python3 -I "$HERE/round_model_pre2019.py" "$S"; done
ML_DIR="$R/dev" python3 -I "$HERE/features.py" "$S" > "$R/dev/features.log"
ML_DIR="$R/dev_s1" SHRINK=1 python3 -I "$HERE/features.py" "$S" > "$R/dev_s1/features.log"
ML_DIR="$R/dev_base" DEBUT_PRIORS=0 RATINGS=0 SHRINK=1 python3 -I "$HERE/features.py" "$S" > "$R/dev_base/features.log"
run() { ML_DIR="$R/$1" python3 -I "$HERE/$2" "$S" "$3" "$4" > "$R/$1/log_$4.txt" 2>&1; echo "finished $4"; }
run dev_base devtools/learn_old.py all base_old &
run dev_base learn.py all base_new &
run dev devtools/learn_old.py all final_old &
run dev learn.py all final_new &
wait
run dev learn.py elo,record,stats,physical,sherdog,stories,style norat_new &
run dev_s1 learn.py all s1_new &
wait
