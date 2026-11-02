"""Post-hoc calibration family for the NBA N1 model (no market price as input).

Selection on 2024-25 only (leave-one-month-out CV), single evaluation on 2025-26.
"""
import json

import numpy as np
import pandas as pd
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LogisticRegression

REPO = "/home/user/bradley09roberts-glitch.github.io/nfl-props-lab"
OUT = "/tmp/claude-0/-home-user-bradley09roberts-glitch-github-io/2b6195b5-f4ca-5a10-bf25-00fd6dd2c82a/scratchpad/fixes/calibration"
EPS = 1e-4
rng_seed = 20261102


def logit(p):
    p = np.clip(p, EPS, 1 - EPS)
    return np.log(p / (1 - p))


def sigmoid(x):
    return 1 / (1 + np.exp(-x))


def ll(p, y):
    p = np.clip(p, EPS, 1 - EPS)
    return -(y * np.log(p) + (1 - y) * np.log(1 - p))


def load(path):
    d = pd.read_parquet(path)
    d = d[d.actual != d.line].copy()
    d["y"] = (d.actual > d.line).astype(float)
    d["lz"] = logit(d.p_over.values)
    d["logline"] = np.log(d.line.values)
    d["gap_raw"] = d.n1_mean - d.line
    d["gap_sc"] = (d.n1_mean - d.line) / np.sqrt(np.maximum(d.line, 0.5))
    d["month"] = d.date.dt.to_period("M").astype(str)
    return d.reset_index(drop=True)


fit = load(f"{REPO}/data/runs/n10/n10_2025_rows.parquet")
test = load(f"{REPO}/data/runs/n10/n10_2026_rows.parquet")
MARKETS = sorted(fit.market.unique())


# ---------------- variant definitions ----------------
def lr_fit(X, y, offset_only=False):
    m = LogisticRegression(C=np.inf, max_iter=5000, solver="lbfgs")
    m.fit(X, y)
    return m


class Identity:
    name = "V0 identity (raw N1)"

    def fit(self, d):
        return self

    def predict(self, d):
        return np.clip(d.p_over.values, EPS, 1 - EPS)


class InterceptShift:
    """logit p' = logit p + c (slope fixed at 1)."""

    def __init__(self, per_market):
        self.per_market = per_market
        self.name = f"{'V2' if per_market else 'V1'} intercept shift ({'per market' if per_market else 'global'})"

    def _solve(self, lz, y):
        # Newton on c
        c = 0.0
        for _ in range(50):
            p = sigmoid(lz + c)
            g = np.sum(p - y)
            h = np.sum(p * (1 - p))
            step = g / h
            c -= step
            if abs(step) < 1e-10:
                break
        return c

    def fit(self, d):
        if self.per_market:
            self.c = {m: self._solve(g.lz.values, g.y.values) for m, g in d.groupby("market")}
        else:
            self.c = {"_": self._solve(d.lz.values, d.y.values)}
        return self

    def predict(self, d):
        if self.per_market:
            c = d.market.map(self.c).values
        else:
            c = self.c["_"]
        return sigmoid(d.lz.values + c)


class Logistic:
    """logit p' = a + b*logit p + extra features (per market or global)."""

    def __init__(self, name, feats, per_market=True, interact_logline=False, quad=False):
        self.quad = quad
        self.name = name
        self.feats = feats
        self.per_market = per_market
        self.interact = interact_logline

    def _X(self, d):
        cols = [d.lz.values] + [d[f].values for f in self.feats]
        if self.interact:
            cols.append(d.lz.values * d.logline.values)
        if getattr(self, "quad", False):
            cols.append(d.lz.values ** 2)
        return np.column_stack(cols)

    def fit(self, d):
        self.models = {}
        if self.per_market:
            for m, g in d.groupby("market"):
                self.models[m] = lr_fit(self._X(g), g.y.values)
        else:
            self.models["_"] = lr_fit(self._X(d), d.y.values)
        return self

    def predict(self, d):
        out = np.empty(len(d))
        if self.per_market:
            for m in d.market.unique():
                idx = np.where(d.market.values == m)[0]
                out[idx] = self.models[m].predict_proba(self._X(d.iloc[idx]))[:, 1]
        else:
            out = self.models["_"].predict_proba(self._X(d))[:, 1]
        return np.clip(out, EPS, 1 - EPS)

    def params(self):
        res = {}
        for k, m in self.models.items():
            res[k] = {"intercept": float(m.intercept_[0]), "coefs": [float(x) for x in m.coef_[0]]}
        return res


class Isotonic:
    def __init__(self, per_market):
        self.per_market = per_market
        self.name = f"{'V9' if per_market else 'V10'} isotonic ({'per market' if per_market else 'global'})"

    def fit(self, d):
        self.models = {}
        groups = d.groupby("market") if self.per_market else [("_", d)]
        for m, g in groups:
            ir = IsotonicRegression(y_min=EPS, y_max=1 - EPS, out_of_bounds="clip")
            ir.fit(g.p_over.values, g.y.values)
            self.models[m] = ir
        return self

    def predict(self, d):
        out = np.empty(len(d))
        if self.per_market:
            for m in d.market.unique():
                idx = np.where(d.market.values == m)[0]
                out[idx] = self.models[m].predict(d.p_over.values[idx])
        else:
            out = self.models["_"].predict(d.p_over.values)
        return np.clip(out, EPS, 1 - EPS)


def make_variants():
    return [
        Identity(),
        InterceptShift(False),
        InterceptShift(True),
        Logistic("V3 logistic a+b*logit (global)", [], per_market=False),
        Logistic("V4 logistic a+b*logit (per market)", [], per_market=True),
        Logistic("V5 per-market logistic + log(line)", ["logline"]),
        Logistic("V6 per-market logistic + (n1_mean-line)", ["gap_raw"]),
        Logistic("V7 per-market logistic + (n1_mean-line)/sqrt(line)", ["gap_sc"]),
        Logistic("V8 per-market logistic + log(line) + (n1_mean-line)/sqrt(line) + logit*log(line)",
                 ["logline", "gap_sc"], interact_logline=True),
        Isotonic(True),
        Isotonic(False),
        Logistic("V11 = V8 + logit^2", ["logline", "gap_sc"], interact_logline=True, quad=True),
    ]


# ---------------- betting rule ----------------
def bet_rule(d, p, over_col, under_col, min_books=3, thr=0.02):
    ok = (d.books.values >= min_books) & d[over_col].notna().values & d[under_col].notna().values
    po, pu = d[over_col].values, d[under_col].values
    ev_o = p * po - 1
    ev_u = (1 - p) * pu - 1
    take_over = ev_o >= ev_u
    ev = np.where(take_over, ev_o, ev_u)
    bet = ok & (ev >= thr)
    win = np.where(take_over, d.y.values == 1, d.y.values == 0)
    price = np.where(take_over, po, pu)
    profit = np.where(win, price - 1, -1.0)
    return bet, win, profit, take_over


def summarize_bets(d, p, over_col, under_col, boot_groups, n_boot=2000, seed=rng_seed):
    bet, win, profit, take_over = bet_rule(d, p, over_col, under_col)
    nb = int(bet.sum())
    if nb == 0:
        return {"bets": 0, "roi": 0.0, "win_rate": None, "roi_ci": [None, None]}
    roi = float(profit[bet].sum() / nb)
    wr = float(win[bet].mean())
    # cluster bootstrap over groups
    g = boot_groups.values
    ug = np.unique(g)
    gi = {u: i for i, u in enumerate(ug)}
    gidx = np.array([gi[x] for x in g])
    prof_g = np.bincount(gidx, weights=np.where(bet, profit, 0.0), minlength=len(ug))
    bets_g = np.bincount(gidx, weights=bet.astype(float), minlength=len(ug))
    rng = np.random.default_rng(seed)
    rois = []
    for _ in range(n_boot):
        s = rng.integers(0, len(ug), len(ug))
        b = bets_g[s].sum()
        if b > 0:
            rois.append(prof_g[s].sum() / b)
    lo, hi = np.percentile(rois, [2.5, 97.5])
    return {"bets": nb, "roi": roi, "win_rate": wr, "roi_ci": [float(lo), float(hi)],
            "over_share": float(take_over[bet].mean()),
            "avg_price": float(np.where(take_over, d[over_col].values, d[under_col].values)[bet].mean())}


def boot_diff(a, b, groups, n_boot=2000, seed=rng_seed):
    """CI for mean(a)-mean(b) with group (date) cluster bootstrap."""
    g = groups.values
    ug = np.unique(g)
    gi = {u: i for i, u in enumerate(ug)}
    gidx = np.array([gi[x] for x in g])
    sa = np.bincount(gidx, weights=a, minlength=len(ug))
    sb = np.bincount(gidx, weights=b, minlength=len(ug))
    n = np.bincount(gidx, minlength=len(ug)).astype(float)
    rng = np.random.default_rng(seed)
    out = []
    for _ in range(n_boot):
        s = rng.integers(0, len(ug), len(ug))
        out.append((sa[s].sum() - sb[s].sum()) / n[s].sum())
    return [float(x) for x in np.percentile(out, [2.5, 97.5])]


def calib_slope(p, y):
    m = LogisticRegression(C=np.inf, max_iter=5000).fit(logit(p).reshape(-1, 1), y)
    return float(m.intercept_[0]), float(m.coef_[0][0])


def ece(p, y, bins=10):
    q = np.quantile(p, np.linspace(0, 1, bins + 1))
    idx = np.clip(np.searchsorted(q, p, side="right") - 1, 0, bins - 1)
    e = 0.0
    rows = []
    for b in range(bins):
        mk = idx == b
        if mk.sum() == 0:
            continue
        e += mk.mean() * abs(p[mk].mean() - y[mk].mean())
        rows.append((float(p[mk].mean()), float(y[mk].mean()), int(mk.sum())))
    return float(e), rows


# ---------------- stage 1: CV on 2024-25 ----------------
def cv_stage():
    months = sorted(fit.month.unique())
    variants = make_variants()
    results = []
    oof_store = {}
    for v in variants:
        oof = np.empty(len(fit))
        fold_ll = {}
        for mo in months:
            tr = fit[fit.month != mo]
            te_idx = np.where(fit.month.values == mo)[0]
            vv = type(v).__new__(type(v))
            vv.__dict__ = dict(v.__dict__)
            vv.fit(tr)
            oof[te_idx] = vv.predict(fit.iloc[te_idx])
            fold_ll[mo] = float(ll(oof[te_idx], fit.y.values[te_idx]).mean())
        L = ll(oof, fit.y.values)
        Lm = ll(fit.fair_over.values, fit.y.values)
        bets = summarize_bets(fit, oof, "best_over", "best_under", fit.date, n_boot=500)
        res = {
            "variant": v.name,
            "oof_logloss": float(L.mean()),
            "oof_brier": float(((oof - fit.y.values) ** 2).mean()),
            "oof_mean_p": float(oof.mean()),
            "oof_calib_slope": calib_slope(oof, fit.y.values)[1],
            "months_beating_identity": None,
            "fold_ll": fold_ll,
            "oof_minus_market": float(L.mean() - Lm.mean()),
            "fit_oof_bets": bets["bets"], "fit_oof_roi": bets["roi"],
        }
        results.append(res)
        oof_store[v.name] = oof
    # forward-chaining check (train on months < m, test on m) for months >= 2024-12
    for v, res in zip(variants, results):
        fwd = []
        for mo in months[2:]:
            tr = fit[fit.month < mo]
            te = fit[fit.month == mo]
            vv = type(v).__new__(type(v))
            vv.__dict__ = dict(v.__dict__)
            vv.fit(tr)
            fwd.append((len(te), ll(vv.predict(te), te.y.values).sum()))
        res["fwd_chain_logloss"] = float(sum(x[1] for x in fwd) / sum(x[0] for x in fwd))
    return variants, results, oof_store


if __name__ == "__main__":
    variants, results, oof = cv_stage()
    print(f"fit rows {len(fit)}, market LL on fit {ll(fit.fair_over.values, fit.y.values).mean():.4f}")
    df = pd.DataFrame(results)
    pd.set_option("display.width", 250)
    pd.set_option("display.max_columns", 20)
    print(df[["variant", "oof_logloss", "fwd_chain_logloss", "oof_brier", "oof_mean_p", "oof_calib_slope",
              "oof_minus_market", "fit_oof_bets", "fit_oof_roi"]].to_string(index=False))
    # fold-level comparison
    fl = pd.DataFrame({r["variant"][:3]: r["fold_ll"] for r in results})
    fl["market"] = fit.groupby("month").apply(lambda g: ll(g.fair_over.values, g.y.values).mean())
    print(fl.round(4).to_string())
    names = [r["variant"] for r in results]
    L8 = ll(oof[names[8]], fit.y.values); L4 = ll(oof[names[4]], fit.y.values); L11 = ll(oof[names[11]], fit.y.values)
    print("V8-V4 OOF diff", (L8 - L4).mean(), boot_diff(L8, L4, fit.date))
    print("V11-V8 OOF diff", (L11 - L8).mean(), boot_diff(L11, L8, fit.date))
    Lm = ll(fit.fair_over.values, fit.y.values)
    print("V8-market OOF diff", (L8 - Lm).mean(), boot_diff(L8, Lm, fit.date))
    pd.to_pickle(oof, f"{OUT}/oof.pkl")
    with open(f"{OUT}/cv_results.json", "w") as f:
        json.dump(results, f, indent=1)
