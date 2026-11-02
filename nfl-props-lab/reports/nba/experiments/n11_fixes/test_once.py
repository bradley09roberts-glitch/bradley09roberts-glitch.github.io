"""ONE evaluation of the frozen chosen variant on the TEST season (NBA 2025-26 sample)."""
import json
import sys

import numpy as np
import pandas as pd

D = "/tmp/claude-0/-home-user-bradley09roberts-glitch-github-io/2b6195b5-f4ca-5a10-bf25-00fd6dd2c82a/scratchpad/fixes/market_anchored"
sys.path.insert(0, D)
from common import *

fz = json.load(open(f"{D}/frozen.json"))
test = load(TEST_PATH)
y = test["y"].values
F = base_features(test, fz["phi"])          # fit-season dispersion (unused by S4)
X, names, pen, _ = design(F, fz["spec"], (np.array(fz["stats_mu"]), np.array(fz["stats_sd"])))
assert names == fz["names"]
p = predict(X, F["lm"].values, np.array(fz["beta_std"]))
pm, pn = test["fair_over"].values, test["p_over"].values

out = {}
out["n"] = int(len(test)); out["dates"] = int(test["date"].nunique())
out["log_loss"] = dict(fixed=logloss(p, y), market=logloss(pm, y), original=logloss(pn, y))
out["brier"] = dict(fixed=brier(p, y), market=brier(pm, y), original=brier(pn, y))
lf, lmk, lo = logloss_vec(p, y), logloss_vec(pm, y), logloss_vec(pn, y)
out["ll_diff_fixed_minus_market"] = boot_diff(lf, lmk, test["date"].values)
out["ll_diff_fixed_minus_original"] = boot_diff(lf, lo, test["date"].values)
out["brier_diff_fixed_minus_market"] = boot_diff((p - y) ** 2, (pm - y) ** 2, test["date"].values)
out["calibration"] = {
    "actual_over_rate": float(y.mean()),
    "mean_pred": dict(fixed=float(p.mean()), market=float(pm.mean()), original=float(pn.mean())),
    "logistic_int_slope": dict(fixed=calib_slope(p, y), market=calib_slope(pm, y), original=calib_slope(pn, y)),
}
# decile table for fixed
q = pd.qcut(p, 10, labels=False, duplicates="drop")
out["deciles_fixed"] = [dict(pred=float(p[q == k].mean()), act=float(y[q == k].mean()), n=int((q == k).sum()))
                        for k in range(int(q.max()) + 1)]
out["per_market_ll"] = {m: dict(fixed=logloss(p[g], y[g]), market=logloss(pm[g], y[g]), original=logloss(pn[g], y[g]))
                        for m in MARKETS for g in [test["market"].values == m]}
# betting: pre-declared rule
out["bets_best_fixed"] = boot_roi(bets(test, p, "best"))
out["bets_mgm_fixed"] = boot_roi(bets(test, p, "mgm"))
# references (same rule, not used for any choice)
out["ref_bets_best_market_only"] = boot_roi(bets(test, pm, "best"))
out["ref_bets_mgm_market_only"] = boot_roi(bets(test, pm, "mgm"))
out["ref_bets_best_original"] = boot_roi(bets(test, pn, "best"))
out["ref_bets_mgm_original"] = boot_roi(bets(test, pn, "mgm"))
out["mgm_eligible_props"] = int(((test.books >= 3) & test.mgm_over.notna() & test.mgm_under.notna()).sum())
out["best_eligible_props"] = int((test.books >= 3).sum())
json.dump(out, open(f"{D}/test_results.json", "w"), indent=1)
print(json.dumps(out, indent=1))
