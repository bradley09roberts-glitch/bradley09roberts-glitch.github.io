import json, numpy as np, pandas as pd, importlib.util
spec=importlib.util.spec_from_file_location('c','calib.py'); c=importlib.util.module_from_spec(spec); spec.loader.exec_module(c)
fit, test = c.fit, c.test
chosen = [v for v in c.make_variants() if v.name.startswith('V11')][0]
chosen.fit(fit)
params = chosen.params()
p = chosen.predict(test); y = test.y.values
p0 = np.clip(test.p_over.values, c.EPS, 1-c.EPS); pm = np.clip(test.fair_over.values, c.EPS, 1-c.EPS)
L, L0, Lm = c.ll(p,y), c.ll(p0,y), c.ll(pm,y)
B, B0, Bm = (p-y)**2, (p0-y)**2, (pm-y)**2
res = {"chosen": chosen.name, "n_test": int(len(test)), "n_dates": int(test.date.nunique()),
  "logloss": {"fixed": L.mean(), "market": Lm.mean(), "original": L0.mean(),
     "fixed_minus_market": L.mean()-Lm.mean(), "fixed_minus_market_ci": c.boot_diff(L,Lm,test.date),
     "fixed_minus_original": L.mean()-L0.mean(), "fixed_minus_original_ci": c.boot_diff(L,L0,test.date)},
  "brier": {"fixed": B.mean(), "market": Bm.mean(), "original": B0.mean(),
     "fixed_minus_market_ci": c.boot_diff(B,Bm,test.date), "fixed_minus_original_ci": c.boot_diff(B,B0,test.date)},
  "calibration": {"mean_p_fixed": p.mean(), "mean_p_original": p0.mean(), "mean_market": pm.mean(), "actual_over_rate": y.mean(),
     "slope_fixed": c.calib_slope(p,y), "slope_original": c.calib_slope(p0,y), "slope_market": c.calib_slope(pm,y),
     "ece_fixed": c.ece(p,y)[0], "ece_original": c.ece(p0,y)[0], "ece_market": c.ece(pm,y)[0],
     "deciles_fixed": c.ece(p,y)[1]},
}
per = []
for m in c.MARKETS:
    k = test.market.values==m
    per.append({"market":m,"n":int(k.sum()),"over_rate":y[k].mean(),"mean_fixed":p[k].mean(),"mean_mkt":pm[k].mean(),
                "ll_fixed":L[k].mean(),"ll_mkt":Lm[k].mean(),"ll_orig":L0[k].mean(),"slope_fixed":c.calib_slope(p[k],y[k])[1]})
res["per_market"] = per
res["bets_best"] = c.summarize_bets(test, p, "best_over","best_under", test.date)
res["bets_mgm"] = c.summarize_bets(test, p, "mgm_over","mgm_under", test.date)
res["bets_best_original_ref"] = c.summarize_bets(test, p0, "best_over","best_under", test.date)
res["bets_mgm_original_ref"] = c.summarize_bets(test, p0, "mgm_over","mgm_under", test.date)
res["eligible_best"] = int((test.books>=3).sum())
res["eligible_mgm"] = int(((test.books>=3)&test.mgm_over.notna()&test.mgm_under.notna()).sum())
res["params"] = params
res["feature_order"] = ["logit(p_over)","log(line)","(n1_mean-line)/sqrt(line)","logit*log(line)","logit^2"]
# disagreement with the market
res["corr_logit_fixed_vs_market"] = float(np.corrcoef(c.logit(p), c.logit(pm))[0,1])
res["mean_abs_dev_from_market"] = float(np.abs(p-pm).mean())
json.dump(res, open('test_results.json','w'), indent=1, default=float)
print(json.dumps(res, indent=1, default=lambda x: round(float(x),5)))
