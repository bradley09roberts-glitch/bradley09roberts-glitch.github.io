"""Fit the stats round scorer on Sherdog-scored rounds from 2016-2018 only, so no test-period (2019+) result
shapes the 'rounds won on the stats' features."""
import json, os, sys
import numpy as np
S = sys.argv[1]; MLD = os.environ.get("ML_DIR", f"{S}/ml")
U = json.load(open(f"{S}/work/ufc_fights.json")); A = json.load(open(f"{S}/sherdog/all_fights.json"))
KD, SIGL, SIGA, TOTL, TOTA, TDL, TDA, SUB, REV, CTRL = range(10)
X, y = [], []
for fid, x in A.items():
    if not ("2016-01-01" <= x["date"] < "2019-01-01"): continue
    f = U[fid]; R0 = f["rounds"].get(f["names"][0], []); R1 = f["rounds"].get(f["names"][1], [])
    rd, secs = f.get("rd") or 0, f.get("secs") or 0
    for r in x["rounds"]:
        i = r["n"] - 1
        if r["own"] not in ("0", "1") or i >= len(R0) or i >= len(R1): continue
        if i == rd - 1 and secs - 300 * i < 300: continue        # full rounds only
        a, b = R0[i], R1[i]
        X.append([a[SIGL] - b[SIGL], a[KD] - b[KD], a[TDL] - b[TDL], (a[CTRL] - b[CTRL]) / 60, a[SUB] - b[SUB],
                  (a[TOTL] - a[SIGL]) - (b[TOTL] - b[SIGL]), a[REV] - b[REV]]); y.append(1 if r["own"] == "0" else 0)
X, y = np.array(X, float), np.array(y, float)
w = np.zeros(X.shape[1])
for _ in range(50):
    p = 1 / (1 + np.exp(-(X @ w))); g = X.T @ (y - p) - .5 * w
    H = -(X.T * (p * (1 - p))) @ X - .5 * np.eye(len(w)); w -= np.linalg.solve(H, g)
acc = float(((1 / (1 + np.exp(-(X @ w))) > .5) == (y == 1)).mean())
names = ["sig_strikes", "knockdowns", "takedowns", "control_min", "sub_attempts", "other_strikes", "reversals"]
json.dump({"weights": dict(zip(names, map(float, w))), "n_rounds": int(len(y)), "acc_in_sample": acc, "window": "2016-2018"},
          open(f"{MLD}/round_model_pre2019.json", "w"), indent=1)
print("rounds", len(y), "in-sample acc", round(acc, 3), dict(zip(names, np.round(w, 3))))
