"""Market-anchored NBA prop models: shared features, fitting and evaluation.

Model form: logit P(over) = logit(fair_over) [offset, coefficient fixed at 1]
                           + X @ beta
with an L2 penalty on beta (except per-market intercepts) so that regularisation
shrinks the fit back toward the market's own price, not toward 0.5.
The market's price is an INPUT to every variant.
"""
import numpy as np
import pandas as pd
from scipy.optimize import minimize

REPO = "/home/user/bradley09roberts-glitch.github.io/nfl-props-lab"
FIT_PATH = f"{REPO}/data/runs/n10/n10_2025_rows.parquet"
TEST_PATH = f"{REPO}/data/runs/n10/n10_2026_rows.parquet"
MARKETS = ["ast", "fg3m", "pra", "pts", "reb"]
EPS = 1e-6


def logit(p):
    p = np.clip(np.asarray(p, float), EPS, 1 - EPS)
    return np.log(p / (1 - p))


def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-z))


def load(path):
    d = pd.read_parquet(path)
    d = d.reset_index(drop=True)
    d["y"] = (d["actual"] > d["line"]).astype(float)
    d["month"] = d["date"].dt.to_period("M").astype(str)
    return d


def dispersion(fit):
    """Per-market variance-to-mean ratio of actual around n1_mean (fit data only)."""
    out = {}
    for m, g in fit.groupby("market"):
        mu = np.maximum(g["n1_mean"].values, 0.25)
        out[m] = float(np.mean((g["actual"].values - mu) ** 2 / mu))
    return out


def base_features(d, phi):
    """Pre-game features only (no outcome information)."""
    f = pd.DataFrame(index=d.index)
    f["lm"] = logit(d["fair_over"])               # market logit (offset)
    f["lp"] = logit(d["p_over"])                  # N1 model logit
    f["dev"] = f["lp"] - f["lm"]                  # model's deviation from market
    f["absdev"] = f["dev"].abs()
    ph = d["market"].map(phi).values
    sd = np.sqrt(ph * np.maximum(d["line"].values, 0.5))
    f["z_n1"] = (d["n1_mean"].values - d["line"].values) / sd
    s = d["S"].fillna(d["line"]).values
    f["z_S"] = (s - d["line"].values) / sd
    f["logline"] = np.log(d["line"].values)
    f["books"] = d["books"].values.astype(float)
    f["margin"] = d["margin"].values
    f["market"] = d["market"].values
    return f


# ---------------------------------------------------------------- design
def design(f, spec, stats=None):
    """Build design matrix for a spec. Returns X, names, penalised mask, stats.

    spec keys:
      per_market_int : per-market intercepts (unpenalised) else one global intercept
      lm_slope       : 'none' | 'global' | 'market'  (adjust slope on market logit)
      dev            : 'none' | 'global' | 'market'  (weight on model deviation)
      extra          : list of feature names added globally
      extra_pm       : list of feature names added per market
      inter          : list of (a, b) products added globally
    stats: standardisation (mean, sd) for penalised continuous columns; computed on fit.
    """
    cols, names, pen = [], [], []
    mk = f["market"].values
    if spec.get("per_market_int", False):
        for m in MARKETS:
            cols.append((mk == m).astype(float)); names.append(f"int_{m}"); pen.append(False)
    else:
        cols.append(np.ones(len(f))); names.append("int"); pen.append(False)

    def add(vals, name, penal=True):
        cols.append(np.asarray(vals, float)); names.append(name); pen.append(penal)

    def add_scope(feat, scope):
        v = f[feat].values if isinstance(feat, str) else feat
        nm = feat if isinstance(feat, str) else "x"
        if scope == "global":
            add(v, nm)
        elif scope == "market":
            for m in MARKETS:
                add(v * (mk == m), f"{nm}_{m}")

    if spec.get("lm_slope", "none") != "none":
        add_scope("lm", spec["lm_slope"])
    if spec.get("dev", "none") != "none":
        add_scope("dev", spec["dev"])
    for e in spec.get("extra", []):
        add(f[e].values, e)
    for e in spec.get("extra_pm", []):
        for m in MARKETS:
            add(f[e].values * (mk == m), f"{e}_{m}")
    for a, b in spec.get("inter", []):
        add(f[a].values * f[b].values, f"{a}*{b}")

    X = np.column_stack(cols)
    pen = np.array(pen)
    # centre & scale penalised columns using fit stats (keep zeros for masked market cols)
    if stats is None:
        mu = np.zeros(X.shape[1]); sdv = np.ones(X.shape[1])
        for j in range(X.shape[1]):
            if pen[j]:
                nz = X[:, j] != 0
                sdv[j] = X[nz, j].std() if nz.sum() > 1 and X[nz, j].std() > 0 else 1.0
        stats = (mu, sdv)
    mu, sdv = stats
    X = (X - mu) / sdv
    return X, names, pen, stats


def fit_offset_logit(X, y, offset, pen, lam, w0=None, iters=50):
    """Minimise mean log loss + lam * ||beta_pen||^2 with an offset (damped Newton)."""
    n, k = X.shape
    b = np.zeros(k) if w0 is None else w0.copy()
    P = 2 * lam * np.diag(pen.astype(float))

    def f(b):
        z = offset + X @ b
        return np.mean(np.logaddexp(0, z) - y * z) + lam * np.sum(b[pen] ** 2)

    cur = f(b)
    for _ in range(iters):
        z = offset + X @ b
        p = sigmoid(z)
        g = X.T @ (p - y) / n + 2 * lam * b * pen
        W = p * (1 - p)
        H = (X * W[:, None]).T @ X / n + P + 1e-10 * np.eye(k)
        step = np.linalg.solve(H, g)
        t = 1.0
        while True:
            nb = b - t * step
            nv = f(nb)
            if nv <= cur + 1e-15 or t < 1e-6:
                break
            t *= 0.5
        b, prev, cur = nb, cur, nv
        if prev - cur < 1e-12 and np.max(np.abs(g)) < 1e-8:
            break
    return b


def predict(X, offset, b):
    return sigmoid(offset + X @ b)


# ---------------------------------------------------------------- metrics
def logloss_vec(p, y):
    p = np.clip(p, EPS, 1 - EPS)
    return -(y * np.log(p) + (1 - y) * np.log(1 - p))


def logloss(p, y):
    return float(np.mean(logloss_vec(p, y)))


def brier(p, y):
    return float(np.mean((p - y) ** 2))


def calib_slope(p, y):
    """Logistic recalibration: y ~ a + s*logit(p). Returns (a, s)."""
    z = logit(p)
    X = np.column_stack([np.ones_like(z), z])
    b = fit_offset_logit(X, y, np.zeros_like(z), np.array([False, False]), 0.0)
    return float(b[0]), float(b[1])


def bets(d, p, price="best", thr=0.02, min_books=3):
    """Pre-declared rule. Returns DataFrame of bets with profit per 1 unit."""
    if price == "best":
        po, pu = d["best_over"].values, d["best_under"].values
        ok = d["books"].values >= min_books
    else:
        po, pu = d["mgm_over"].values, d["mgm_under"].values
        ok = (d["books"].values >= min_books) & np.isfinite(po) & np.isfinite(pu)
    ev_o = p * po - 1
    ev_u = (1 - p) * pu - 1
    take_over = ev_o >= ev_u
    ev = np.where(take_over, ev_o, ev_u)
    pr = np.where(take_over, po, pu)
    sel = ok & (ev >= thr)
    y = d["y"].values
    win = np.where(take_over, y == 1, y == 0)
    out = pd.DataFrame({
        "date": d["date"].values[sel],
        "week": d["week"].values[sel] if "week" in d else d["date"].values[sel],
        "win": win[sel].astype(float),
        "price": pr[sel],
        "ev": ev[sel],
        "over": take_over[sel],
    })
    out["profit"] = np.where(out["win"] == 1, out["price"] - 1, -1.0)
    return out


def boot_roi(b, cluster="date", nboot=2000, seed=0):
    if len(b) == 0:
        return dict(bets=0, win_rate=float("nan"), roi=float("nan"), roi_ci=[float("nan")] * 2)
    rng = np.random.default_rng(seed)
    g = b.groupby(cluster).agg(p=("profit", "sum"), n=("profit", "size"))
    P, N = g["p"].values, g["n"].values
    k = len(g)
    rois = np.empty(nboot)
    for i in range(nboot):
        idx = rng.integers(0, k, k)
        rois[i] = P[idx].sum() / N[idx].sum()
    return dict(bets=int(len(b)), win_rate=float(b["win"].mean()),
                roi=float(b["profit"].sum() / len(b)),
                roi_ci=[float(np.quantile(rois, 0.025)), float(np.quantile(rois, 0.975))],
                avg_price=float(b["price"].mean()), share_over=float(b["over"].mean()))


def boot_diff(lossA, lossB, clusters, nboot=2000, seed=0):
    """CI for mean(lossA - lossB), resampling clusters."""
    rng = np.random.default_rng(seed)
    df = pd.DataFrame({"c": clusters, "d": lossA - lossB})
    g = df.groupby("c")["d"].agg(["sum", "size"])
    S, N = g["sum"].values, g["size"].values
    k = len(g)
    v = np.empty(nboot)
    for i in range(nboot):
        idx = rng.integers(0, k, k)
        v[i] = S[idx].sum() / N[idx].sum()
    return float(df["d"].mean()), [float(np.quantile(v, 0.025)), float(np.quantile(v, 0.975))]
