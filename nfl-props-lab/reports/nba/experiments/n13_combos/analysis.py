"""n13: an honest test of the two-stat combo forecasts (pr, pa, ra) and whether a calibration can be
fitted for them without any past combo lines.

We hold no bookmaker lines for P+R, P+A or R+A. The idea tested here: a "proxy" combo line = the
sum of the component main lines for the same player-game (pr = pts line + reb line, and so on).
Before using it for the combos, the idea is checked on PRA, where both a real line and the three
component lines exist.

Data: the n10 rows (The Odds API historical snapshots about 3 h before tip, US books, main lines;
consensus line = n10_hist_lines.consensus, the construction calibration_v4 for nba-0.3 was fitted on)
for 2024-25 (season key 2025, FIT) and the 2025-26 sample (2026, TEST, scored once). Model: nba-0.3
N1 walk-forward pmfs from pmfs.py (inputs strictly before each game). Outcomes: box scores.
Calibration form exactly as calibration_v4: logit p_cal = a + b * logit(p_model), logit clip 1e-4,
unpenalised maximum likelihood, per market.

PRE-DECLARED CRITERION (fixed before any proxy result was looked at). The proxy "reproduces PRA's
real-line calibration reasonably" only if all three hold:
  (1) |b_proxy - b_real| <= 0.10 for PRA (both fitted on 2024-25, same player-games);
  (2) on the 2025-26 REAL PRA lines, log loss(proxy-fitted) - log loss(real-fitted) <= 0.001 nats;
  (3) and the upper end of its date-block 95% interval is below 0.002.
Only then may pr / pa / ra proxy coefficients be offered for calibration.json. No writer exists: this
script only records the decision (n13_summary.json decision.write_calibration_combo_proxy), and any
future write must check that flag first (tests/test_n13_combos.py asserts it is false and that
calibration.json holds no proxy block).

Two-stat proxies are whole numbers (two half-point lines add to an integer) while real lines are
half points. Primary: both neighbours, L - 0.5 and L + 0.5, each with weight 1/2 (no information
added). Sensitivity: one neighbour, chosen by the sign of the parts' summed market lean
(sum of fair_over - 0.5; ties take both). PRA's proxy (three half-points) is already a half point,
so this rounding step cannot be validated on PRA; the README says so.

Outputs: data/runs/n13/n13_rows_{season}.parquet (per-prop, prices: git-ignored) and
reports/nba/experiments/n13_combos/n13_summary.json (aggregates only).
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import numpy as np
import pandas as pd

from npl.config import PROJECT_ROOT

REPO = str(PROJECT_ROOT)
HERE = Path(__file__).resolve().parent
OUT = f"{REPO}/data/runs/n13"
Q = f"{REPO}/data/runs/n12/data/q_{{}}.parquet"   # n12_audit/extract.py: read-only copy of hist_quotes
ORIG = ("pts", "reb", "ast", "fg3m", "pra")
COMBOS = ("pr", "pa", "ra")
PARTS = {"pra": ("pts", "reb", "ast"), "pr": ("pts", "reb"), "pa": ("pts", "ast"), "ra": ("reb", "ast")}
EPS = 1e-4
LN2 = float(np.log(2))
B_BOOT = 2000
CRIT = {"max_abs_b_diff": 0.10, "max_ll_diff": 0.001, "max_ll_diff_ci_upper": 0.002}

_spec = importlib.util.spec_from_file_location("n10", f"{REPO}/reports/nba/experiments/n10_hist_lines.py")
n10 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(n10)


# ----------------------------------------------------------------------------- helpers
def logit(p):
    p = np.clip(np.asarray(p, float), EPS, 1 - EPS)
    return np.log(p / (1 - p))


def sig(z):
    return 1 / (1 + np.exp(-z))


def ll_vec(p, y):
    p = np.clip(np.asarray(p, float), EPS, 1 - EPS)
    return -(y * np.log(p) + (1 - y) * np.log(1 - p))


def wmean(v, w):
    return float(np.sum(v * w) / np.sum(w))


def fit_ab(p, y, w=None):
    """Weighted ML logistic of y on logit(p) (Newton); returns (a, b)."""
    z = logit(p)
    w = np.ones_like(z) if w is None else np.asarray(w, float)
    X = np.column_stack([np.ones_like(z), z])
    beta = np.zeros(2)
    for _ in range(100):
        q = sig(X @ beta)
        g = X.T @ (w * (q - y))
        H = (X * (w * q * (1 - q))[:, None]).T @ X
        step = np.linalg.solve(H, g)
        beta -= step
        if np.max(np.abs(step)) < 1e-12:
            break
    return float(beta[0]), float(beta[1])


def apply_ab(ab, p):
    return sig(ab[0] + ab[1] * logit(p))


def date_boot(dates, v, w=None, seed=13):
    """Date-block bootstrap 95% interval of the weighted mean of v."""
    w = np.ones(len(v)) if w is None else np.asarray(w, float)
    d = pd.DataFrame({"d": dates, "s": v * w, "w": w}).groupby("d")[["s", "w"]].sum()
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(d), (B_BOOT, len(d)))
    b = d.s.to_numpy()[idx].sum(1) / d.w.to_numpy()[idx].sum(1)
    return [round(float(np.percentile(b, 2.5)), 5), round(float(np.percentile(b, 97.5)), 5)]


class N1:
    def __init__(self, season):
        z = np.load(f"{OUT}/data/n1_{season}.npz", allow_pickle=True)
        self.z = z
        self.ix = pd.Series(np.arange(len(z["game_id"])),
                            index=pd.MultiIndex.from_arrays([z["game_id"], z["player_id"]]))
        self.sf = {m: z[f"sf_{m}"] for m in (*ORIG, *COMBOS)}
        self.y = {m: z[f"y_{m}"] for m in (*ORIG, *COMBOS)}
        self.date = z["date"]

    def rows(self, gid, pid):
        i = self.ix.reindex(pd.MultiIndex.from_arrays([gid, pid])).to_numpy()
        return i

    def p_over(self, m, i, line):
        sf = self.sf[m]
        k = np.floor(np.asarray(line, float)).astype(int) + 1
        return np.where(k <= sf.shape[1] - 1, sf[i, np.clip(k, 0, sf.shape[1] - 1)], 0.0)


def props(season):
    q = pd.read_parquet(Q.format(season))
    q["game_id"] = q.game_id.astype(str)
    q["player_id"] = q.player_id.astype(str).where(q.player_id.notna())
    c = n10.consensus(q)  # n10 construction (row-mode line, books with both sides at it)
    c["game_id"] = c.game_id.astype(str)
    c["player_id"] = c.player_id.astype(str)
    return c


# ----------------------------------------------------------------------------- build rows
def build(season, n1):
    c = props(season)
    i = n1.rows(c.game_id, c.player_id)
    c = c[~np.isnan(i)].copy()
    c["i"] = n1.rows(c.game_id, c.player_id).astype(int)
    c["date"] = n1.date[c.i.to_numpy()]
    # real rows (the five original markets, as n10 / calibration_v4)
    raw = np.full(len(c), np.nan)
    yv = np.full(len(c), np.nan)
    for m in ORIG:
        s = (c.market == m).to_numpy()
        raw[s] = n1.p_over(m, c.i.to_numpy()[s], c.line.to_numpy()[s])
        yv[s] = n1.y[m][c.i.to_numpy()[s]]
    c["raw"], c["actual"] = raw, yv
    real = c[c.actual != c.line].copy()
    real["y"] = (real.actual > real.line).astype(float)
    real["w"] = 1.0
    # wide: one row per player-game with each market's consensus line and fair
    wide = c.pivot_table(index=["game_id", "player_id", "i", "date"], columns="market",
                         values=["line", "fair_over"], aggfunc="first")
    wide.columns = [f"{a}_{b}" for a, b in wide.columns]
    wide = wide.reset_index()
    return real, wide


def proxy_rows(wide, n1, m, mode):
    """Proxy rows for market m (pra or a combo) from player-games with every part's line."""
    parts = PARTS[m]
    need = [f"line_{s}" for s in parts]
    w = wide.dropna(subset=need).copy()
    w["proxy"] = w[need].sum(1)
    w["lean"] = sum(w[f"fair_over_{s}"] - 0.5 for s in parts)
    if m == "pra":            # three half-points: already a half point
        out = w.assign(line=w.proxy, w=1.0)
    elif mode == "both":
        out = pd.concat([w.assign(line=w.proxy - 0.5, w=0.5), w.assign(line=w.proxy + 0.5, w=0.5)])
    else:                     # tilt: the side the parts' market lean points to; ties take both
        up = w[w.lean > 0].assign(line=lambda v: v.proxy + 0.5, w=1.0)
        dn = w[w.lean < 0].assign(line=lambda v: v.proxy - 0.5, w=1.0)
        tie = w[w.lean == 0]
        out = pd.concat([up, dn, tie.assign(line=tie.proxy - 0.5, w=0.5), tie.assign(line=tie.proxy + 0.5, w=0.5)])
    out = out.reset_index(drop=True)
    ii = out.i.to_numpy()
    out["raw"] = n1.p_over(m, ii, out.line.to_numpy())
    out["actual"] = n1.y[m][ii]
    out["y"] = (out.actual > out.line).astype(float)
    out["market"] = m
    assert (out.actual != out.line).all()  # half points: no pushes
    return out


def scores(d, ab=None, extra=None):
    """Weighted test-set log losses and calibration figures."""
    y, w, raw = d.y.to_numpy(), d.w.to_numpy(), d.raw.to_numpy()
    out = {"n_rows": int(len(d)), "n_player_games": int(d.drop_duplicates(["game_id", "player_id"]).shape[0]),
           "dates": int(d.date.nunique()), "over_rate": round(wmean(y, w), 4),
           "mean_raw": round(wmean(raw, w), 4),
           "log_loss": {"coin_flip": round(LN2, 5), "raw_model": round(wmean(ll_vec(raw, y), w), 5)}}
    if ab is not None:
        pc = apply_ab(ab, raw)
        L, L0 = ll_vec(pc, y), ll_vec(raw, y)
        out["mean_cal"] = round(wmean(pc, w), 4)
        out["log_loss"]["calibrated"] = round(wmean(L, w), 5)
        out["cal_minus_coin"] = round(wmean(L - LN2, w), 5)
        out["cal_minus_coin_ci"] = date_boot(d.date.to_numpy(), L - LN2, w)
        out["cal_minus_raw"] = round(wmean(L - L0, w), 5)
        out["cal_minus_raw_ci"] = date_boot(d.date.to_numpy(), L - L0, w)
        out["slope_of_calibrated_on_test"] = round(fit_ab(pc, y, w)[1], 3)
        out["sd_calibrated_chance"] = round(float(np.sqrt(wmean((pc - wmean(pc, w)) ** 2, w))), 4)
    if "fair_over" in d:
        out["log_loss"]["market"] = round(wmean(ll_vec(d.fair_over.to_numpy(), y), w), 5)
    out["raw_slope_on_test"] = round(fit_ab(raw, y, w)[1], 3)
    return out


def cf(ab):
    return {"a": round(ab[0], 6), "b": round(ab[1], 6)}


# ----------------------------------------------------------------------------- PIT / dispersion
def pit_block(n1, season):
    z = n1.z
    dates = z["date"]
    out = {}
    for m in (*ORIG, *COMBOS):
        pit, y, mu, var = z[f"pit_{m}"], z[f"y_{m}"], z[f"mean_{m}"], z[f"var_{m}"]
        r2 = (y - mu) ** 2
        h = np.histogram(pit, bins=10, range=(0, 1))[0] / len(pit)
        out[m] = {"n": int(len(pit)), "pit_var": round(float(pit.var()), 4),
                  "pit_lowest_tenth": round(float((pit < 0.1).mean()), 4),
                  "pit_highest_tenth": round(float((pit > 0.9).mean()), 4),
                  "central_80_coverage": round(float(((pit >= 0.1) & (pit <= 0.9)).mean()), 4),
                  "pit_deciles": [round(float(v), 4) for v in h],
                  "mean_bias": round(float((y - mu).mean()), 3),
                  "var_ratio_obs_over_model": round(float(r2.mean() / var.mean()), 4),
                  "var_ratio_ci": [round(v, 4) for v in _ratio_ci(dates, r2, var)],
                  "outcomes_above_max_k": int(z[f"trunc_{m}"].sum())}
    # beyond-minutes correlation: model covariance of the parts comes only from shared minutes
    pairs = {}
    for c in COMBOS:
        a, b = PARTS[c]
        ra, rb = z[f"y_{a}"] - z[f"mean_{a}"], z[f"y_{b}"] - z[f"mean_{b}"]
        cov_model = (z[f"var_{c}"] - z[f"var_{a}"] - z[f"var_{b}"]) / 2
        emp = ra * rb
        sd = np.sqrt(z[f"var_{a}"].mean() * z[f"var_{b}"].mean())
        ex = emp - cov_model
        pairs[c] = {"parts": [a, b], "emp_cov": round(float(emp.mean()), 4),
                    "model_cov_minutes_only": round(float(cov_model.mean()), 4),
                    "excess_cov": round(float(ex.mean()), 4),
                    "excess_cov_ci": date_boot(dates, ex),
                    "emp_corr_of_residuals": round(float(np.corrcoef(ra, rb)[0, 1]), 4),
                    "model_corr_minutes_only": round(float(cov_model.mean() / sd), 4),
                    "excess_as_share_of_combo_model_var": round(float(2 * ex.mean() / z[f"var_{c}"].mean()), 4)}
    return {"season": season, "markets": out, "pairs": pairs}


def _ratio_ci(dates, num, den, seed=13):
    d = pd.DataFrame({"d": dates, "n": num, "v": den}).groupby("d")[["n", "v"]].sum()
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(d), (B_BOOT, len(d)))
    b = d.n.to_numpy()[idx].sum(1) / d.v.to_numpy()[idx].sum(1)
    return float(np.percentile(b, 2.5)), float(np.percentile(b, 97.5))


# ----------------------------------------------------------------------------- main
def main():
    n1 = {s: N1(s) for s in (2025, 2026)}
    real, wide = {}, {}
    for s in (2025, 2026):
        real[s], wide[s] = build(s, n1[s])
    res = {"experiment": "n13 combos (pr, pa, ra): proxy lines, calibration and dispersion",
           "model": "nba-0.3 N1, walk-forward (inputs strictly before each game)",
           "lines": "n10 consensus (row-mode line, books with both sides at it), about 3 h before tip, US books",
           "fit": "2024-25 (season key 2025)", "test": "2025-26 sample (season key 2026, every fourth date)",
           "form": "logit p_cal = a + b * logit(p_model), per market, clip 1e-4 (calibration_v4's)",
           "criterion": CRIT}
    for k, fn in (("pmfs_check", "pmfs_check.json"), ("head_check", "head_check.json")):
        if Path(f"{OUT}/{fn}").exists():
            h = json.load(open(f"{OUT}/{fn}"))
            h.pop("npl_path", None)
            res[k] = h

    # A. reproduce calibration_v4 (nba-0.3) on the real lines: checks the machinery
    stored = json.load(open(f"{REPO}/reports/nba/model/calibration.json"))["by_model"]["nba-0.3"]["calibration_v4"]
    fitv4 = {m: fit_ab(g.raw.to_numpy(), g.y.to_numpy()) for m, g in real[2025].groupby("market")}
    te = real[2026]
    pc = np.empty(len(te))
    for m in ORIG:
        s = (te.market == m).to_numpy()
        pc[s] = apply_ab(fitv4[m], te.raw.to_numpy()[s])
    res["A_reproduce_calibration_v4"] = {
        "fit_props": int(len(real[2025])), "test_props": int(len(te)),
        "refit": {m: cf(v) for m, v in sorted(fitv4.items())},
        "stored": {m: {"a": round(v["a"], 6), "b": round(v["b"], 6)} for m, v in sorted(stored["coef"].items())},
        "max_abs_coef_diff": round(max(abs(fitv4[m][k] - stored["coef"][m][kk])
                                       for m in ORIG for k, kk in ((0, "a"), (1, "b"))), 8),
        "test_log_loss_refit": round(float(ll_vec(pc, te.y.to_numpy()).mean()), 5),
        "test_log_loss_stored": stored["test_2025_26_log_loss"]}

    # B. PRA: proxy v real
    B_ = {}
    for s in (2025, 2026):
        w = wide[s].dropna(subset=["line_pts", "line_reb", "line_ast", "line_pra"])
        diff = (w.line_pra - (w.line_pts + w.line_reb + w.line_ast)).to_numpy()
        vc = pd.Series(diff).round(1).value_counts()
        B_[f"line_diff_{s}"] = {
            "player_games_with_all_four_lines": int(len(w)),
            "player_games_with_three_part_lines": int(wide[s].dropna(subset=["line_pts", "line_reb", "line_ast"]).shape[0]),
            "player_games_with_pra_line": int(wide[s].line_pra.notna().sum()),
            "mean": round(float(diff.mean()), 3), "sd": round(float(diff.std()), 3),
            "median": float(np.median(diff)),
            "share_equal": round(float((np.abs(diff) < 0.25).mean()), 4),
            "share_within_0_5": round(float((np.abs(diff) <= 0.5).mean()), 4),
            "share_within_1_0": round(float((np.abs(diff) <= 1.0).mean()), 4),
            "share_within_2_0": round(float((np.abs(diff) <= 2.0).mean()), 4),
            "quantiles_5_25_50_75_95": [float(v) for v in np.quantile(diff, [.05, .25, .5, .75, .95])],
            "counts_minus3_to_plus3": {str(k): int(vc.get(float(k), 0)) for k in range(-3, 4)},
            "note": "real PRA consensus line minus (points + rebounds + assists consensus lines); "
                    "all lines are half points, so the difference is a whole number and 'within 0.5' means equal"}
    # same player-games for both fits: those with all four lines
    keys = {}
    for s in (2025, 2026):
        w = wide[s].dropna(subset=["line_pts", "line_reb", "line_ast", "line_pra"])
        keys[s] = set(zip(w.game_id, w.player_id, strict=True))

    def in_keys(d, s):
        return d[[k in keys[s] for k in zip(d.game_id, d.player_id, strict=True)]]

    pra_real = {s: in_keys(real[s][real[s].market == "pra"], s) for s in (2025, 2026)}
    pra_prox = {s: in_keys(proxy_rows(wide[s], n1[s], "pra", "both"), s) for s in (2025, 2026)}
    ab_real = fit_ab(pra_real[2025].raw.to_numpy(), pra_real[2025].y.to_numpy())
    ab_prox = fit_ab(pra_prox[2025].raw.to_numpy(), pra_prox[2025].y.to_numpy())
    ab_v4 = (stored["coef"]["pra"]["a"], stored["coef"]["pra"]["b"])
    t = pra_real[2026]
    y = t.y.to_numpy()
    L_real, L_prox = ll_vec(apply_ab(ab_real, t.raw), y), ll_vec(apply_ab(ab_prox, t.raw), y)
    L_v4, L_raw = ll_vec(apply_ab(ab_v4, t.raw), y), ll_vec(t.raw, y)
    L_mkt = ll_vec(t.fair_over.to_numpy(), y)
    tp = pra_prox[2026]
    yp = tp.y.to_numpy()
    d_ll = L_prox - L_real
    B_["fit_2024_25"] = {"player_games": int(len(pra_real[2025])),
                         "real_line_fit": cf(ab_real), "proxy_line_fit": cf(ab_prox),
                         "calibration_v4_pra_all_real_rows": cf(ab_v4),
                         "over_rate_at_real_lines": round(float(pra_real[2025].y.mean()), 4),
                         "over_rate_at_proxy_lines": round(float(pra_prox[2025].y.mean()), 4),
                         "raw_mean_at_real_lines": round(float(pra_real[2025].raw.mean()), 4),
                         "raw_mean_at_proxy_lines": round(float(pra_prox[2025].raw.mean()), 4)}
    B_["test_2025_26_on_real_pra_lines"] = {
        "props": int(len(t)), "dates": int(t.date.nunique()),
        "log_loss": {"coin_flip": round(LN2, 5), "market": round(float(L_mkt.mean()), 5),
                     "raw_model": round(float(L_raw.mean()), 5),
                     "real_line_fit": round(float(L_real.mean()), 5),
                     "proxy_line_fit": round(float(L_prox.mean()), 5),
                     "calibration_v4": round(float(L_v4.mean()), 5)},
        "proxy_minus_real_fit": round(float(d_ll.mean()), 5),
        "proxy_minus_real_fit_ci": date_boot(t.date.to_numpy(), d_ll),
        "real_fit_minus_coin": round(float((L_real - LN2).mean()), 5),
        "real_fit_minus_coin_ci": date_boot(t.date.to_numpy(), L_real - LN2),
        "over_rate": round(float(y.mean()), 4)}
    B_["test_2025_26_on_proxy_pra_lines"] = {
        "props": int(len(tp)),
        "log_loss": {"coin_flip": round(LN2, 5), "raw_model": round(float(ll_vec(tp.raw, yp).mean()), 5),
                     "real_line_fit": round(float(ll_vec(apply_ab(ab_real, tp.raw), yp).mean()), 5),
                     "proxy_line_fit": round(float(ll_vec(apply_ab(ab_prox, tp.raw), yp).mean()), 5)},
        "over_rate": round(float(yp.mean()), 4)}
    c1 = abs(ab_prox[1] - ab_real[1]) <= CRIT["max_abs_b_diff"]
    c2 = float(d_ll.mean()) <= CRIT["max_ll_diff"]
    c3 = B_["test_2025_26_on_real_pra_lines"]["proxy_minus_real_fit_ci"][1] < CRIT["max_ll_diff_ci_upper"]
    B_["criterion"] = {"b_close": bool(c1), "ll_close": bool(c2), "ll_ci_upper_ok": bool(c3),
                       "proxy_validates": bool(c1 and c2 and c3)}
    res["B_pra_proxy_validation"] = B_

    # C. the combos on proxy lines (fit 2024-25, test 2025-26)
    C_ = {}
    for mode in ("both", "tilt"):
        C_[mode] = {}
        for m in COMBOS:
            f = proxy_rows(wide[2025], n1[2025], m, mode)
            tt = proxy_rows(wide[2026], n1[2026], m, mode)
            ab = fit_ab(f.raw.to_numpy(), f.y.to_numpy(), f.w.to_numpy())
            C_[mode][m] = {"coef": cf(ab), "fit_player_games": int(f.drop_duplicates(["game_id", "player_id"]).shape[0]),
                           "fit_over_rate": round(wmean(f.y.to_numpy(), f.w.to_numpy()), 4),
                           "test": scores(tt, ab)}
            if mode == "both":
                f.to_parquet(f"{OUT}/n13_proxy_{m}_2025.parquet")
                tt.to_parquet(f"{OUT}/n13_proxy_{m}_2026.parquet")
    # PRA on proxy lines and the single markets at real lines, same table, for context
    C_["context_pra_proxy"] = {"coef": cf(ab_prox), "test": scores(pra_prox[2026], ab_prox)}
    C_["context_real_lines_calibration_v4_b"] = {m: round(stored["coef"][m]["b"], 4) for m in ORIG}
    res["C_combos_on_proxy_lines"] = C_

    # E. post hoc (added after B and C were seen; no decision rests on it): how a line that is not
    # the market's own inflates the fitted slope. The five markets' REAL lines moved 1 point down and
    # 1 point up (both rows kept, half points stay half points), fitted on 2024-25 like calibration_v4
    E_ = {}
    r25 = real[2025]
    for m in ORIG:
        r = r25[r25.market == m]
        v = pd.concat([r.assign(line=r.line - 1), r.assign(line=r.line + 1)]).reset_index(drop=True)
        v["raw"] = n1[2025].p_over(m, v.i.to_numpy(), v.line.to_numpy())
        v["y"] = (v.actual > v.line).astype(float)
        E_[m] = {"b_at_real_lines": round(fit_ab(r.raw.to_numpy(), r.y.to_numpy())[1], 4),
                 "b_at_real_lines_moved_1_both_ways": round(fit_ab(v.raw.to_numpy(), v.y.to_numpy())[1], 4)}
    pra_both1 = pd.concat([pra_prox[2025].assign(line=pra_prox[2025].line - 1),
                           pra_prox[2025].assign(line=pra_prox[2025].line + 1)]).reset_index(drop=True)
    pra_both1["raw"] = n1[2025].p_over("pra", pra_both1.i.to_numpy(), pra_both1.line.to_numpy())
    pra_both1["y"] = (pra_both1.actual > pra_both1.line).astype(float)
    E_["pra_proxy_moved_1_both_ways_b"] = round(fit_ab(pra_both1.raw.to_numpy(), pra_both1.y.to_numpy())[1], 4)
    E_["note"] = ("post hoc. A line away from the market's own lets the model 'see' that the line is off "
                  "centre, which any forecaster gets right, so the fitted slope rises. The combos' "
                  "both-neighbours proxy (L - 0.5 and L + 0.5) has the same flaw at half the distance.")
    res["E_post_hoc_line_offset_inflates_slope"] = E_

    # D. PIT and dispersion (all played player-games in the stored-line games)
    res["D_pit_dispersion"] = {"test_2025_26": pit_block(n1[2026], 2026), "fit_2024_25": pit_block(n1[2025], 2025),
                               "note": "randomised PIT of the N1 pmf conditional on playing; uniform has variance "
                                       "0.0833 and 10% in each tenth. var_ratio = mean squared error / mean model "
                                       "variance: above 1 the pmf is too narrow, below 1 too wide."}
    ok = res["B_pra_proxy_validation"]["criterion"]["proxy_validates"]
    res["decision"] = {
        "write_calibration_combo_proxy": bool(ok),
        "reason": ("the pre-declared PRA check passed" if ok else
                   "the pre-declared PRA check failed (interval of the log-loss cost on real PRA lines reaches "
                   "above 0.002), so nothing is written to calibration.json; the combos keep no calibrated "
                   "chance (p_cal_market null, p_cal_basis null)"),
        "section_C_status": "descriptive only, not adopted"}
    for s in (2025, 2026):
        real[s].to_parquet(f"{OUT}/n13_real_{s}.parquet")
    json.dump(res, open(HERE / "n13_summary.json", "w"), indent=1)
    print(json.dumps(res, indent=1))


if __name__ == "__main__":
    main()
