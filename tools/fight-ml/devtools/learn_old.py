"""Learners that see every UFC fight once, in order, and learn from each card's results before the next.

  elo        Elo ratings only (the rating's own formula, no fitting).
  online     Online logistic regression: after each card, every fight's outcome pays a reward equal to the log score of
             the probability it gave the winner (confident and right earns most, confident and wrong is punished most).
             Updated by gradient steps (Adagrad), never refitted on the past.
  rl         Reinforcement learning as asked: a policy picks a fighter (sampling, so it explores), gets +1 if the pick
             won and -1 if not, and updates by policy gradient (REINFORCE). Averaged over 5 seeds.
  rl_greedy  The same, but always picking the fighter it rates higher (no exploration).
  gbm        Gradient-boosted trees, refitted at the start of each year on every earlier fight.
  blend      Average log-odds of online and gbm.
Hyperparameters are chosen on 2014-2018 only; 2019 onward is never used for choices.
Usage: learn.py <scratch> [groups,comma,separated|all] [tag]  ->  ml/preds_<tag>.npz
"""
import json, math, sys, os
import numpy as np
from collections import defaultdict
S = sys.argv[1]
MLD = os.environ.get("ML_DIR", f"{S}/ml")   # output folder (a copy can work in its own)
GROUPS_ON = sys.argv[2] if len(sys.argv) > 2 else "all"
TAG = sys.argv[3] if len(sys.argv) > 3 else "all"
Z = np.load(f"{MLD}/features.npz"); M = json.load(open(f"{MLD}/features_meta.json"))
X, Y = Z["X"], Z["Y"]; names = M["names"]; meta = M["meta"]
dates = np.array([m[1] for m in meta])
if GROUPS_ON != "all":
    keep = [names.index(k) for g in GROUPS_ON.split(",") for k in M["groups"][g]]
    X = X[:, keep]; names = [names[i] for i in keep]
n, k = X.shape
lab = Y >= 0
VAL = (dates >= "2014-01-01") & (dates < "2019-01-01")
TEST = dates >= "2019-01-01"
# cards: indices grouped by date, in order
order_days = sorted(set(dates))
idx_by_day = defaultdict(list)
for i, d in enumerate(dates): idx_by_day[d].append(i)

def sig(z): return 1 / (1 + np.exp(-np.clip(z, -30, 30)))
def ll(p, y): p = np.clip(p, 1e-6, 1 - 1e-6); return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))

# ---------- running scale for linear learners (past rows only)
class Scaler:
    def __init__(self, k): self.s2 = np.zeros(k); self.c = np.zeros(k)
    def z(self, x):
        rms = np.sqrt(self.s2 / np.maximum(self.c, 1)); rms[rms == 0] = 1
        out = np.where(np.isnan(x), 0.0, x / rms)
        return np.clip(out, -5, 5)
    def update(self, x):
        ok = ~np.isnan(x); self.s2[ok] += x[ok] ** 2; self.c[ok] += 1

def run_elo():
    i = names.index("elo") if "elo" in names else None
    p = np.full(n, np.nan)
    if i is None: return p
    d = X[:, i]
    p = 1 / (1 + 10 ** (-np.nan_to_num(d) / 400))
    return p

def run_online(lr, l2, seed=0):
    w = np.zeros(k); g2 = np.full(k, 1e-8); sc = Scaler(k); p = np.full(n, np.nan)
    for d in order_days:
        ids = idx_by_day[d]
        Zs = np.array([sc.z(X[i]) for i in ids])
        p[ids] = sig(Zs @ w)
        for j, i in enumerate(ids):
            sc.update(X[i])
            if Y[i] < 0: continue
            grad = (p[i] - Y[i]) * Zs[j] + l2 * w      # gradient of the log-score penalty
            g2 += grad ** 2; w -= lr * grad / np.sqrt(g2)
    return p, w

def run_rl(lr, seed, greedy=False):
    rng = np.random.default_rng(seed)
    w = np.zeros(k); sc = Scaler(k); p = np.full(n, np.nan); reward = np.zeros(n)
    for d in order_days:
        ids = idx_by_day[d]
        Zs = np.array([sc.z(X[i]) for i in ids])
        pi = sig(Zs @ w); p[ids] = pi
        for j, i in enumerate(ids):
            sc.update(X[i])
            if Y[i] < 0: continue
            a = (1.0 if pi[j] >= .5 else 0.0) if greedy else (1.0 if rng.random() < pi[j] else 0.0)
            r = 1.0 if a == Y[i] else -1.0                  # +1 when the pick wins, -1 when it loses
            reward[i] = r
            w += lr * r * (a - pi[j]) * Zs[j]               # REINFORCE: reward times the gradient of log pi(a)
    return p, reward

def run_gbm(params, start_year=2005):
    from sklearn.ensemble import HistGradientBoostingClassifier
    p = np.full(n, np.nan)
    years = sorted(set(d[:4] for d in dates if d[:4] >= str(start_year)))
    for yv in years:
        tr = lab & (dates < f"{yv}-01-01"); te = np.array([d.startswith(yv) for d in dates])
        if tr.sum() < 500 or te.sum() == 0: continue
        cols = np.where((~np.isnan(X[tr])).sum(0) >= 30)[0]     # features with data in this year's training set
        Xt = np.vstack([X[tr][:, cols], -X[tr][:, cols]]); Yt = np.concatenate([Y[tr], 1 - Y[tr]])
        m = HistGradientBoostingClassifier(random_state=0, early_stopping=False, **params).fit(Xt, Yt)
        pa = m.predict_proba(X[te][:, cols])[:, 1]; pb = m.predict_proba(-X[te][:, cols])[:, 1]
        p[te] = (pa + 1 - pb) / 2                         # symmetric: P(A) = 1 - P(B)
    return p

def score(p, mask):
    m = mask & lab & ~np.isnan(p)
    y = Y[m]; q = p[m]
    acc = float(np.mean(np.where(q == .5, .5, (q > .5) == (y == 1))))
    return {"n": int(m.sum()), "acc": round(acc, 4), "ll": round(ll(q, y), 4), "brier": round(float(np.mean((q - y) ** 2)), 4)}

out = {}
if __name__ == "__main__":
    res = {}
    # ---- tune on 2014-2018
    best = None
    for lr in (0.005, 0.01, 0.02, 0.05):
        for l2 in (0.0, 0.001, 0.01):
            p, _ = run_online(lr, l2)
            s = score(p, VAL)
            if best is None or s["ll"] < best[0]["ll"]: best = (s, lr, l2)
    _, LR, L2 = best
    p_online, w_online = run_online(LR, L2)
    res["online"] = {"lr": LR, "l2": L2}
    bestr = None
    for lr in (0.002, 0.005, 0.01, 0.02, 0.05):
        ps = [run_rl(lr, s)[0] for s in range(2)]
        s = score(np.mean(ps, 0), VAL)
        if bestr is None or s["acc"] > bestr[0]["acc"] or (s["acc"] == bestr[0]["acc"] and s["ll"] < bestr[0]["ll"]): bestr = (s, lr)
    RLR = bestr[1]
    rl_runs = [run_rl(RLR, s) for s in range(5)]
    p_rl = np.mean([r[0] for r in rl_runs], 0)
    rl_reward = np.mean([r[1] for r in rl_runs], 0)
    p_rlg, _ = run_rl(RLR, 0, greedy=True)
    res["rl"] = {"lr": RLR, "seeds": 5}
    bestg = None
    grid = [dict(learning_rate=lr_, max_iter=it, max_leaf_nodes=lv, min_samples_leaf=40, l2_regularization=1.0)
            for lr_ in (0.03, 0.06) for it in (150, 300) for lv in (7, 15)]
    for prm in grid:
        pg = run_gbm(prm, start_year=2014)
        s = score(pg, VAL)
        if bestg is None or s["ll"] < bestg[0]["ll"]: bestg = (s, prm)
    GP = bestg[1]
    p_gbm = run_gbm(GP)
    res["gbm"] = GP
    p_elo = run_elo()
    lo = lambda q: np.log(np.clip(q, 1e-6, 1 - 1e-6) / (1 - np.clip(q, 1e-6, 1 - 1e-6)))
    p_blend = sig((lo(p_online) + lo(np.where(np.isnan(p_gbm), p_online, p_gbm))) / 2)
    preds = {"elo": p_elo, "online": p_online, "rl": p_rl, "rl_greedy": p_rlg, "gbm": p_gbm, "blend": p_blend}
    for nm, p in preds.items():
        res[nm + "_val"] = score(p, VAL); res[nm + "_test"] = score(p, TEST)
    np.savez_compressed(f"{MLD}/preds_{TAG}.npz", **preds, rl_reward=rl_reward, w_online=w_online)
    json.dump({"names": names, "res": res}, open(f"{MLD}/learn_{TAG}.json", "w"), indent=1)
    print(TAG, json.dumps({k: v for k, v in res.items()}, indent=None))
