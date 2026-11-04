#!/bin/bash
# One driver, so the evaluation always matches the features and predictions it reads (evaluate.py also checks hashes).
# Usage: run_all.sh <scratch> <out_dir>     (FIREWALL=1 hides 2019+ results, for development runs)
set -euo pipefail
S=$1; OUT=$2; HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"; export ML_DIR="$OUT"; export OMP_NUM_THREADS=1
python3 -I "$HERE/round_model_pre2019.py" "$S"
python3 -I "$HERE/features.py" "$S"
python3 -I "$HERE/learn.py" "$S" all all > "$OUT/learn_all.log"
# ablations: what each data source adds (each re-tuned on 2014-2018 like the full model)
python3 -I "$HERE/learn.py" "$S" elo,ratings,record,stats,physical,style no_sherdog_stories > "$OUT/learn_ab1.log" &
python3 -I "$HERE/learn.py" "$S" elo,ratings,record,stats,physical,stories,style no_sherdog > "$OUT/learn_ab2.log" &
python3 -I "$HERE/learn.py" "$S" elo,record,stats,physical,sherdog,stories,style no_ratings > "$OUT/learn_ab3.log" &
python3 -I "$HERE/learn.py" "$S" elo no_stats_elo_only > "$OUT/learn_ab4.log" &
wait
[ "${FIREWALL:-0}" = "1" ] && exit 0
python3 -I "$HERE/evaluate.py" "$S" all > "$OUT/evaluate.log"
echo done
