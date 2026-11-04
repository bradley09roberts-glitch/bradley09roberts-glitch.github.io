"""Evaluate the learners against the ledger's model (v4) and the de-vigged closing line, gate-style.

Close benchmark: the ledger stores a proportional de-vig of the close (mk, for the ledger's pick). The gate uses Shin, which
for a two-way market equals the additive de-vig: p_add = p_prop + m * (p_prop - 0.5), with m the overround. m = 0.0457 (the
ledger's typical closing overround) reproduces the ledger's own recorded gate close log loss (0.5926 on its 1,111 locked bouts).

The close is never an input to any model here. The one place it is combined with a model ("information test") is a
diagnostic, labelled as such and benchmarked against a close-only recalibration fitted the same way; it can't be bet.
Usage: evaluate.py <scratch> <tag> [tag2 ...]   ->  ML_DIR/eval.json, ML_DIR/predictions_all.csv
"""
import csv, hashlib, json, os, sys
import numpy as np
S = sys.argv[1]; MLD = os.environ.get("ML_DIR", f"{S}/ml"); TAGS = sys.argv[2:] or ["all"]
MARGIN = 0.0457
def sha(fn): return hashlib.sha256(open(fn, "rb").read()).hexdigest()[:16]
M = json.load(open(f"{MLD}/features_meta.json")); meta = M["meta"]
assert not M.get("firewall"), "this features.npz is a firewalled development copy"
FZ = np.load(f"{MLD}/features.npz"); Y = FZ["Y"]; IMP = FZ["IMP"] if "IMP" in FZ.files else None
L = json.load(open(f"{S}/work/ledger.json"))
row_of = {m[0]: i for i, m in enumerate(meta)}
LB = []
for e in L["events"]:
    for b in e["bouts"]:
        if b.get("won") is None or b.get("ok") is None or b["id"] not in row_of: continue
        LB.append((e["date"], b))
LB.sort(key=lambda x: x[0])

def lo(p): p = np.clip(p, 1e-6, 1 - 1e-6); return np.log(p / (1 - p))
def sig(z): return 1 / (1 + np.exp(-z))
def lls(p, y): p = np.clip(p, 1e-12, 1 - 1e-12); return -(y * np.log(p) + (1 - y) * np.log(1 - p))
def acc(p, y): return float(np.mean(np.where(p == .5, .5, (p > .5) == (y == 1))))
def gate(pm, pc, y, n_boot=5000, seed=0):
    gap = lls(pc, y) - lls(pm, y)                      # > 0 = model better
    boots = np.random.default_rng(seed).choice(gap, (n_boot, len(gap))).mean(1)
    lo_, hi_ = np.percentile(boots, [2.5, 97.5])
    return {"n": int(len(y)), "model_ll": round(float(lls(pm, y).mean()), 4), "close_ll": round(float(lls(pc, y).mean()), 4),
            "model_acc": round(acc(pm, y), 4), "close_acc": round(acc(pc, y), 4),
            "ci95": [round(float(lo_), 4), round(float(hi_), 4)], "verdict": "PASS" if lo_ > 0 else "FAIL"}

KEYS = ("elo", "online", "rl", "rl_greedy", "logit", "gbm", "blend")
preds, hashes = {}, {"features.npz": sha(f"{MLD}/features.npz")}
for t in TAGS:
    Z = np.load(f"{MLD}/preds_{t}.npz"); hashes[f"preds_{t}.npz"] = sha(f"{MLD}/preds_{t}.npz")
    assert str(Z["features_sha"]) == hashes["features.npz"], f"preds_{t}.npz was trained on a different features.npz"
    for k in KEYS:
        if k in Z.files: preds[f"{t}:{k}" if len(TAGS) > 1 else k] = Z[k]

# per ledger bout: each learner's chance for the ledger's pick
y = np.array([1.0 if b["ok"] else 0.0 for _, b in LB])
date = np.array([d for d, _ in LB])
v4 = np.array([b["p"] for _, b in LB])
mk_prop = np.array([b["mk"] if (b.get("mk") is not None and b.get("mk_src") == "close") else np.nan for _, b in LB])
mk = np.clip(mk_prop + MARGIN * (mk_prop - 0.5), 1e-4, 1 - 1e-4)        # additive (Shin, two-way) de-vig
P = {}
for nm, p in preds.items():
    P[nm] = np.array([p[row_of[b["id"]]] if meta[row_of[b["id"]]][2] == b["pick"] else 1 - p[row_of[b["id"]]] for _, b in LB])
P["v4 (ledger model)"] = v4
priced = ~np.isnan(mk)

out = {"inputs": hashes, "close_devig": f"additive (Shin for two-way), margin {MARGIN}", "sets": {}, "gate": {}, "info_test": {},
       "blend_v4": {}, "calibration": {}, "segments": {}, "curves": {}, "rl": {}, "fill_in_check": {}}
SETS = {"ledger_2019_26": np.ones(len(y), bool), "locked_2023_26": date >= "2023-01-01", "season_2025_26": date >= "2025-01-01"}
def stats(p, yy): return {"n": int(len(yy)), "acc": round(acc(p, yy), 4), "ll": round(float(lls(p, yy).mean()), 4)}
for sname, m in SETS.items():
    allb = {nm: stats(p[m & ~np.isnan(p)], y[m & ~np.isnan(p)]) for nm, p in P.items()}
    pr = m & priced
    pb = {nm: stats(p[pr & ~np.isnan(p)], y[pr & ~np.isnan(p)]) for nm, p in P.items()}
    pb["close (market)"] = stats(mk[pr], y[pr])
    out["sets"][sname] = {"all_bouts": allb, "priced_bouts": pb}
for sname, m in SETS.items():
    pm = m & priced
    out["gate"][sname] = {nm: gate(p[pm], mk[pm], y[pm]) for nm, p in P.items() if not np.isnan(p[pm]).any()}

def wf_fit(cols, yv, dv, min_n=150):
    """Walk-forward logistic fit on the given log-odds columns, refitted before each date on earlier dates only."""
    outp = np.full(len(yv), np.nan); w = None
    for d in sorted(set(dv)):
        past = dv < d; cur = dv == d
        if past.sum() < min_n: continue
        Xp = np.column_stack([c[past] for c in cols]); w = np.zeros(len(cols))
        for _ in range(50):
            p_ = sig(Xp @ w); g = Xp.T @ (yv[past] - p_) - 0.1 * w
            H = -(Xp.T * (p_ * (1 - p_))) @ Xp - 0.1 * np.eye(len(cols)); w -= np.linalg.solve(H, g)
        outp[cur] = sig(np.column_stack([c[cur] for c in cols]) @ w)
    return outp, (w.tolist() if w is not None else None)

# information test (diagnostic only, not bettable): does a learner add anything to the close? Compare
# close+learner against the close alone, both recalibrated walk-forward the same way on earlier priced bouts.
cl_only, _ = wf_fit([lo(mk[priced])], y[priced], date[priced])
for nm, p in P.items():
    bp, cf = wf_fit([lo(p[priced]), lo(mk[priced])], y[priced], date[priced])
    ok = ~np.isnan(bp) & ~np.isnan(cl_only)
    g = gate(bp[ok], cl_only[ok], y[priced][ok])
    out["info_test"][nm] = {"n": g["n"], "close_plus_learner_ll": g["model_ll"], "close_recalibrated_ll": g["close_ll"], "ci95": g["ci95"],
                            "adds_information": g["verdict"] == "PASS", "weights_learner_close": [round(c, 3) for c in cf] if cf else None}

# does a learner add to v4? walk-forward blend with v4 on every ledger bout (model-only, no odds)
for nm in [k for k in P if not k.startswith("v4")]:
    bp, cf = wf_fit([lo(P[nm]), lo(v4)], y, date)
    ok = ~np.isnan(bp)
    gv = gate(bp[ok], v4[ok], y[ok])
    out["blend_v4"][nm] = {"n": int(ok.sum()), "ll_blend": round(float(lls(bp[ok], y[ok]).mean()), 4), "ll_v4": round(float(lls(v4[ok], y[ok]).mean()), 4), "ci95": gv["ci95"],
                           "acc_blend": round(acc(bp[ok], y[ok]), 4), "acc_v4": round(acc(v4[ok], y[ok]), 4), "weights_learner_v4": [round(c, 3) for c in cf] if cf else None}

# calibration: favourite's stated chance against how often it won
CAL = list(P) + ["close (market)"]
for nm in CAL:
    p = mk if nm == "close (market)" else P[nm]
    ok = ~np.isnan(p); pp, yy = p[ok], y[ok]
    fav = np.maximum(pp, 1 - pp); won = np.where(pp >= .5, yy, 1 - yy)
    rows = []
    for a, b_ in ((.5, .55), (.55, .6), (.6, .65), (.65, .7), (.7, .75), (.75, .8), (.8, .9), (.9, 1.01)):
        s = (fav >= a) & (fav < b_)
        if s.sum(): rows.append({"lo": a, "hi": min(b_, 1), "n": int(s.sum()), "said": round(float(fav[s].mean()), 3), "won": round(float(won[s].mean()), 3)})
    out["calibration"][nm] = rows
divs = np.array([("w:" if b.get("w") else "m:") + (b.get("div") or "?") for _, b in LB])
for nm in [k for k in ("gbm", "online", "logit", "blend") if k in P] + ["v4 (ledger model)"]:
    seg = {}
    for dv in sorted(set(divs)):
        s = (divs == dv) & priced & ~np.isnan(P[nm])
        if s.sum() >= 60:
            seg[dv] = {"n": int(s.sum()), "acc": round(acc(P[nm][s], y[s]), 3), "ll": round(float(lls(P[nm][s], y[s]).mean()), 4),
                       "close_acc": round(acc(mk[s], y[s]), 3), "close_ll": round(float(lls(mk[s], y[s]).mean()), 4)}
    out["segments"][nm] = seg

# learning curves over every UFC fight: rolling accuracy (last 400 fights) and yearly accuracy / log loss
lab = Y >= 0; dates_all = np.array([m[1] for m in meta]); yrs_all = dates_all.astype("U4")
for nm, p in preds.items():
    ok = lab & ~np.isnan(p)
    hit = np.where(p[ok] == .5, .5, (p[ok] > .5) == (Y[ok] == 1)).astype(float)
    dd = dates_all[ok]; yy = yrs_all[ok]
    roll = np.convolve(hit, np.ones(400) / 400, mode="valid")
    step = max(1, len(roll) // 300)
    out["curves"][nm] = {"rolling400": [[dd[i + 399], round(float(roll[i]), 4)] for i in range(0, len(roll), step)],
                         "yearly": {yv: {"n": int((yy == yv).sum()), "acc": round(float(hit[yy == yv].mean()), 4),
                                         "ll": round(float(lls(p[ok][yy == yv], Y[ok][yy == yv]).mean()), 4)}
                                    for yv in sorted(set(yy)) if (yy == yv).sum() >= 50}}

# RL: the rewarded agent's own picks (sampled, so it explores) versus the policy's greedy picks
Zr = np.load(f"{MLD}/preds_{TAGS[0]}.npz")
rw = Zr["rl_reward"]                                    # mean over seeds of +1 / -1 per fight (0 when no result)
own_hit = (rw[lab] + 1) / 2                             # the agent's own sampled-pick hit rate, averaged over seeds
dl = dates_all[lab]
cum_reward = np.cumsum(rw[lab])
out["rl"] = {
    "own_hit_rate_by_year": {yv: {"n": int((dl.astype('U4') == yv).sum()), "hit": round(float(own_hit[dl.astype('U4') == yv].mean()), 4)}
                             for yv in sorted(set(dl.astype('U4'))) if (dl.astype('U4') == yv).sum() >= 50},
    "own_hit_2019_26": round(float(own_hit[dl >= "2019-01-01"].mean()), 4),
    "own_hit_2014_18": round(float(own_hit[(dl >= "2014-01-01") & (dl < "2019-01-01")].mean()), 4),
    "cum_reward": [[dl[i], round(float(cum_reward[i]), 1)] for i in range(0, len(cum_reward), max(1, len(cum_reward) // 300))] + [[dl[-1], round(float(cum_reward[-1]), 1)]],
    "rolling400_reward": [[dl[i], round(float(np.mean(rw[lab][max(0, i - 399):i + 1])), 4)] for i in range(399, len(dl), max(1, len(dl) // 300))],
    "share_extreme_probs_2019_26": round(float(np.mean((preds["rl"][lab & (dates_all >= "2019-01-01")] < .02) | (preds["rl"][lab & (dates_all >= "2019-01-01")] > .98))), 4) if "rl" in preds else None,
}

# regression check for the hindsight leak the audit found: in fights where exactly one fighter's physicals were filled in,
# the filled-in fighter won far less often before 2019 (blanks mark fighters later cut). A model that can't see the
# fill-ins should over-rate them there; one that could see them would match the low win rate.
if IMP is not None:
    one = lab & (IMP.sum(1) == 1)
    for win, (a, b) in {"pre_2014": ("0000", "2014-01-01"), "2014_18": ("2014-01-01", "2019-01-01"), "2019_26": ("2019-01-01", "9999")}.items():
        m = one & (dates_all >= a) & (dates_all < b)
        if not m.sum(): continue
        a_imp = IMP[m, 0] == 1
        won_imp = np.where(a_imp, Y[m], 1 - Y[m])
        rec = {"n": int(m.sum()), "filled_in_fighter_won": round(float(won_imp.mean()), 3)}
        for nm in ("online", "gbm", "blend"):
            if nm in preds:
                q = np.where(a_imp, preds[nm][m], 1 - preds[nm][m]); rec[f"{nm}_said"] = round(float(np.nanmean(q)), 3) if (~np.isnan(q)).any() else None
                rec[f"{nm}_n"] = int((~np.isnan(q)).sum())
        out["fill_in_check"][win] = rec

json.dump(out, open(f"{MLD}/eval.json", "w"), indent=1)
with open(f"{MLD}/predictions_all.csv", "w", newline="") as fh:
    w = csv.writer(fh); w.writerow(["date", "fight_id", "fighter_a", "fighter_b", "a_won"] + list(preds))
    for i, m in enumerate(meta):
        w.writerow([m[1], m[0], m[2], m[3], "" if Y[i] < 0 else int(Y[i])] + ["" if np.isnan(preds[k][i]) else round(float(preds[k][i]), 4) for k in preds])
for sname, v in out["sets"].items():
    print("==", sname)
    for k in v["priced_bouts"]:
        a_ = v["all_bouts"].get(k); p_ = v["priced_bouts"][k]
        print(f"   {k:22s} all: " + (f"n={a_['n']:5d} acc={a_['acc']:.3f} ll={a_['ll']:.4f}" if a_ else " " * 33) + f" | priced: n={p_['n']:5d} acc={p_['acc']:.3f} ll={p_['ll']:.4f}")
for sname, v in out["gate"].items():
    print("== gate", sname); [print(f"   {k:22s} {x['verdict']} n={x['n']} model {x['model_ll']} close {x['close_ll']} ci {x['ci95']}") for k, x in v.items()]
print("== information test (diagnostic; close+learner vs close recalibrated)"); [print(f"   {k:22s} {x['close_plus_learner_ll']} vs {x['close_recalibrated_ll']} ci {x['ci95']} w {x['weights_learner_close']}") for k, x in out["info_test"].items()]
print("== blends with v4"); [print(f"   {k:22s} blend {x['ll_blend']} v4 {x['ll_v4']} acc {x['acc_blend']} v {x['acc_v4']} w {x['weights_learner_v4']}") for k, x in out["blend_v4"].items()]
print("== rl", {k: v for k, v in out["rl"].items() if not isinstance(v, (list, dict))})
print("== fill-in check", out["fill_in_check"])
