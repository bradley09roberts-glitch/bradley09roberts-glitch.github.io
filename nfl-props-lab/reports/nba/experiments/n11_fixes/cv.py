"""Cross-validation by month on the FIT season (NBA 2024-25) only."""
import sys

import numpy as np
import pandas as pd

sys.path.insert(0, "/tmp/claude-0/-home-user-bradley09roberts-glitch-github-io/2b6195b5-f4ca-5a10-bf25-00fd6dd2c82a/scratchpad/fixes/market_anchored")
from common import *

OUT = "/tmp/claude-0/-home-user-bradley09roberts-glitch-github-io/2b6195b5-f4ca-5a10-bf25-00fd6dd2c82a/scratchpad/fixes/market_anchored"
fit = load(FIT_PATH)
y = fit["y"].values
months = fit["month"].values
umonths = sorted(set(months))

EXTRA = ["z_n1", "z_S", "logline", "books", "margin", "absdev"]
SPECS = {
    "S1_pm_int": dict(per_market_int=True),
    "S2_pm_int_slope": dict(per_market_int=True, lm_slope="market"),
    "S3_blend_global": dict(per_market_int=True, lm_slope="market", dev="global"),
    "S4_blend_pm": dict(per_market_int=True, lm_slope="market", dev="market"),
    "S5_blend_pm_feats": dict(per_market_int=True, lm_slope="market", dev="market", extra=EXTRA),
    "S6_blend_pm_feats_inter": dict(per_market_int=True, lm_slope="market", dev="market", extra=EXTRA,
                                    inter=[("dev", "books"), ("dev", "logline"), ("dev", "absdev"),
                                           ("dev", "margin"), ("dev", "z_S")]),
    "S7_full_pm": dict(per_market_int=True, lm_slope="market", dev="market",
                       extra=["logline", "books", "margin", "absdev"], extra_pm=["z_n1", "z_S"],
                       inter=[("dev", "books"), ("dev", "logline"), ("dev", "absdev"),
                              ("dev", "margin"), ("dev", "z_S")]),
    "S8_no_model_terms": dict(per_market_int=True, lm_slope="market",
                              extra=["z_S", "logline", "books", "margin"]),
}
LAMS = [0.0, 1e-5, 1e-4, 1e-3, 1e-2, 3e-2, 1e-1, 3e-1]


def cv_predict(spec, lam, folds):
    """Leave-one-month-out (or other folds) out-of-fold predictions."""
    oof = np.full(len(fit), np.nan)
    for tr, te in folds:
        ftr = fit.iloc[tr]
        phi = dispersion(ftr)
        Ftr = base_features(ftr, phi)
        Fte = base_features(fit.iloc[te], phi)
        Xtr, names, pen, st = design(Ftr, spec)
        Xte, _, _, _ = design(Fte, spec, st)
        b = fit_offset_logit(Xtr, y[tr], Ftr["lm"].values, pen, lam)
        oof[te] = predict(Xte, Fte["lm"].values, b)
    return oof


lomo = [(np.where(months != m)[0], np.where(months == m)[0]) for m in umonths]
fwd = [(np.where(months < m)[0], np.where(months == m)[0]) for m in umonths[2:]]  # train on >=2 prior months

res = []
mk_ll = logloss_vec(fit["fair_over"].values, y)
n1_ll = logloss_vec(fit["p_over"].values, y)
res.append(dict(spec="S0_market", lam=None, cv_ll=float(mk_ll.mean()), d_vs_mkt=0.0, se=0.0))
oofs = {}
nvar = 0
for sname, spec in SPECS.items():
    for lam in LAMS:
        nvar += 1
        oof = cv_predict(spec, lam, lomo)
        l = logloss_vec(oof, y)
        dd = l - mk_ll
        # SE of the mean difference via month-clustered... use date clusters
        g = pd.DataFrame({"c": fit["date"].values, "d": dd}).groupby("c")["d"].agg(["sum", "size"])
        k = len(g); mean = dd.mean()
        se = np.sqrt(np.sum((g["sum"] - mean * g["size"]) ** 2)) / g["size"].sum() * np.sqrt(k / (k - 1))
        res.append(dict(spec=sname, lam=lam, cv_ll=float(l.mean()), d_vs_mkt=float(mean), se=float(se)))
        oofs[(sname, lam)] = oof
        print(f"{sname:28s} lam={lam:<7g} cvLL={l.mean():.5f} d_vs_mkt={mean:+.5f} (se {se:.5f})", flush=True)

R = pd.DataFrame(res)
R.to_csv(f"{OUT}/cv_results.csv", index=False)
best = R.iloc[1:].sort_values("cv_ll").iloc[0]
print("\nVariants (spec x lambda) tried:", nvar)
print("Best:", best.to_dict())
np.save(f"{OUT}/oof_best.npy", oofs[(best["spec"], best["lam"])])
# save all oofs for diagnostics
pd.DataFrame({f"{k[0]}|{k[1]}": v for k, v in oofs.items()}).to_parquet(f"{OUT}/oofs.parquet")
