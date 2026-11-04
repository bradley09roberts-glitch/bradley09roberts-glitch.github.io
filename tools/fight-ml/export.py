"""Turn a finished run (features, predictions, eval.json) into the report's data files.

Usage: export.py <scratch> <run_dir> <out_dir>   ->  out_dir/*.csv
With report_tables.py (improvements.csv, checklist.csv) these are every data file the report page reads.
"""
import csv, json, os, shutil, sys
import numpy as np
S, RUN, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
os.makedirs(OUT, exist_ok=True)
E = json.load(open(f"{RUN}/eval.json"))
LABEL = {"elo": "Elo only", "online": "Online learner", "rl": "RL agent", "rl_greedy": "RL agent, no exploration", "gbm": "Boosted trees", "logit": "Yearly logistic",
         "blend": "ML blend", "v4 (ledger model)": "v4 (ledger)", "close (market)": "Closing line"}
def w(name, cols, rows):
    with open(f"{OUT}/{name}.csv", "w", newline="") as fh:
        wr = csv.writer(fh); wr.writerow(cols); [wr.writerow([r.get(c, "") for c in cols]) for r in rows]
    print(name, len(rows))

SETNAME = {"ledger_2019_26": "2019-26", "locked_2023_26": "2023-26", "season_2025_26": "2025-26"}
rows = []
for s, v in E["sets"].items():
    for sub in ("all_bouts", "priced_bouts"):
        for m, x in v[sub].items():
            rows.append({"set": SETNAME[s], "bouts": "priced" if sub == "priced_bouts" else "all", "model": LABEL.get(m, m), "n": x["n"], "acc": x["acc"], "ll": x["ll"]})
w("results", ["set", "bouts", "model", "n", "acc", "ll"], rows)

rows = []
for s, v in E["gate"].items():
    for m, g in v.items():
        rows.append({"set": SETNAME[s], "model": LABEL.get(m, m), "n": g["n"], "model_ll": g["model_ll"], "close_ll": g["close_ll"],
                     "gap": round(g["close_ll"] - g["model_ll"], 4), "ci_lo": g["ci95"][0], "ci_hi": g["ci95"][1], "verdict": g["verdict"],
                     "model_acc": g["model_acc"], "close_acc": g["close_acc"]})
w("gate", ["set", "model", "n", "model_ll", "close_ll", "gap", "ci_lo", "ci_hi", "verdict", "model_acc", "close_acc"], rows)

rows = []
for m in ("elo", "online", "logit", "gbm", "blend", "rl_greedy", "rl"):
    for d, a in E["curves"][m]["rolling400"]:
        rows.append({"model": LABEL[m], "date": d, "acc400": a})
w("curve", ["model", "date", "acc400"], rows)
rows = []
for m in ("elo", "online", "logit", "gbm", "blend", "rl_greedy", "rl"):
    for yv, x in E["curves"][m]["yearly"].items():
        rows.append({"model": LABEL[m], "year": yv, "n": x["n"], "acc": x["acc"], "ll": x["ll"]})
for yv, x in E["rl"]["own_hit_rate_by_year"].items():
    rows.append({"model": "RL agent's own picks", "year": yv, "n": x["n"], "acc": x["hit"], "ll": ""})
w("yearly", ["model", "year", "n", "acc", "ll"], rows)

w("rl_reward", ["date", "cum_reward"], [{"date": d, "cum_reward": c} for d, c in dict(E["rl"]["cum_reward"]).items()])   # last value per date
w("rl_summary", ["measure", "value"], [
    {"measure": "own_hit_2019_26", "value": E["rl"]["own_hit_2019_26"]},
    {"measure": "own_hit_2014_18", "value": E["rl"]["own_hit_2014_18"]},
    {"measure": "share_extreme_probs_2019_26", "value": E["rl"]["share_extreme_probs_2019_26"]},
    {"measure": "total_reward", "value": E["rl"]["cum_reward"][-1][1]},
    {"measure": "fights_rewarded", "value": int((np.load(f"{RUN}/features.npz")["Y"] >= 0).sum())}])

rows = []
for m in ("blend", "online", "gbm", "rl", "v4 (ledger model)", "close (market)"):
    for b in E["calibration"][m]:
        rows.append({"model": LABEL[m], "bin": f"{int(b['lo'] * 100)}-{int(b['hi'] * 100)}%", "n": b["n"], "said": b["said"], "won": b["won"]})
w("calibration", ["model", "bin", "n", "said", "won"], rows)

rows = []
for m, x in E["info_test"].items():
    rows.append({"model": LABEL.get(m, m), "test": "Adds to the close?", "n": x["n"], "with_ll": x["close_plus_learner_ll"], "without_ll": x["close_recalibrated_ll"],
                 "ci_lo": x["ci95"][0], "ci_hi": x["ci95"][1], "weight": x["weights_learner_close"][0] if x["weights_learner_close"] else ""})
for m, x in E["blend_v4"].items():
    rows.append({"model": LABEL.get(m, m), "test": "Adds to v4?", "n": x["n"], "with_ll": x["ll_blend"], "without_ll": x["ll_v4"], "ci_lo": x["ci95"][0], "ci_hi": x["ci95"][1],
                 "weight": x["weights_learner_v4"][0] if x["weights_learner_v4"] else ""})
w("adds", ["model", "test", "n", "with_ll", "without_ll", "ci_lo", "ci_hi", "weight"], rows)

rows = []
for m, segs in E["segments"].items():
    for dv, x in segs.items():
        rows.append({"model": LABEL[m], "division": dv.split(":", 1)[1] + (" (W)" if dv.startswith("w:") else ""), **{k: x[k] for k in ("n", "acc", "ll", "close_acc", "close_ll")}})
w("divisions", ["model", "division", "n", "acc", "ll", "close_acc", "close_ll"], rows)

# ablations and development comparisons: 2014-2018 (validation) and all UFC fights from 2019 (test)
AB = [("all", "Everything"), ("no_sherdog", "Without Sherdog play-by-play"), ("no_sherdog_stories", "Without Sherdog or fight stories"),
      ("no_ratings", "Without Glicko and margin ratings"), ("no_stats_elo_only", "Elo rating alone")]
rows = []
for tag, lab in AB:
    fn = f"{RUN}/learn_{tag}.json"
    if not os.path.exists(fn): continue
    r = json.load(open(fn))["res"]
    for m in ("blend", "online", "logit", "gbm", "rl"):
        rows.append({"variant": lab, "model": LABEL[m], "val_n": r[m + "_val"]["n"], "val_acc": r[m + "_val"]["acc"], "val_ll": r[m + "_val"]["ll"],
                     "test_n": r[m + "_test"]["n"], "test_acc": r[m + "_test"]["acc"], "test_ll": r[m + "_test"]["ll"]})
w("ablation", ["variant", "model", "val_n", "val_acc", "val_ll", "test_n", "test_acc", "test_ll"], rows)

rows = []
for win, x in E["fill_in_check"].items():
    rows.append({"window": win.replace("_", "-").replace("pre-2014", "before 2014"), "n": x["n"], "won": x["filled_in_fighter_won"],
                 "model_said": x.get("blend_said", "")})
w("fill_in", ["window", "n", "won", "model_said"], rows)

M = E["method"]
w("method", ["measure", "value"], [{"measure": k, "value": v} for k, v in M.items()])
shutil.copyfile(f"{RUN}/predictions_all.csv", f"{OUT}/predictions.csv"); print("predictions copied")
