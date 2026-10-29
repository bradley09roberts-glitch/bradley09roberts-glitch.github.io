"""Experiment n04: blowout risk via the pre-game expected margin.

Expected margin (team's view) = EW point differential of team - of opponent (halflife 15
team games, point-in-time) + 2 points at home / -2 away. Two variants against frozen
nba-0.1, scored on identical 2024-2025 validation rows:
  S: shift expected minutes by a fitted function of margin and expected minutes
  R: empirical minutes residuals by (expected-minutes band x margin class) - moves both the
     centre and the spread (short nights for big favourites' starters, garbage time for
     their bench)
Everything is fitted on FIT_SEASONS (2018-2023) only. The 2026 test season is not touched.
"""
import json
import sys

import numpy as np
import pandas as pd

from npl.nba import backtest as B, canon, model as M

HCA = 2.0
CLASSES = (-7.0, 7.0)  # big underdog <= -7 < close < 7 <= big favourite


def margin_states(tg: pd.DataFrame, hl: float = 15.0) -> pd.DataFrame:
    o = tg[["game_id", "team", "score"]].rename(columns={"team": "opp", "score": "opp_score"})
    t = tg.merge(o, on=["game_id", "opp"]).sort_values(["team", "tip_utc"])
    t["margin"] = t.score - t.opp_score
    t["margin_ew"] = t.groupby("team", sort=False).margin.transform(lambda s: s.ewm(halflife=hl).mean())
    return t[["team", "tip_utc", "margin_ew"]]


def add_margin(x: pd.DataFrame, tg: pd.DataFrame) -> pd.DataFrame:
    ms = margin_states(tg)
    x = M._asof(x, ms, "team").rename(columns={"margin_ew": "team_margin_ew"})
    x = M._asof(x, ms.rename(columns={"team": "opp"}), "opp").rename(columns={"margin_ew": "opp_margin_ew"})
    x["exp_margin"] = (x.team_margin_ew - x.opp_margin_ew + np.where(x.is_home.astype(bool), HCA, -HCA)).fillna(0.0)
    return x


def shift_features(m: np.ndarray, mu: np.ndarray) -> np.ndarray:
    a, b = np.maximum(m, 0), np.maximum(-m, 0)
    z = (np.clip(mu, 1, 48) - 20) / 10
    cols = [np.minimum(a, 7), np.maximum(a - 7, 0), np.minimum(b, 7), np.maximum(b - 7, 0)]
    return np.column_stack(cols + [c * z for c in cols])


def fit_shift(tr: pd.DataFrame) -> np.ndarray:
    X = shift_features(tr.exp_margin.to_numpy(), tr.min_mu.to_numpy())
    y = (tr.minutes_y - tr.min_mu).to_numpy()
    return np.linalg.lstsq(X, y, rcond=None)[0]


def mclass(m: np.ndarray) -> np.ndarray:
    return np.digitize(m, CLASSES)  # 0 big underdog, 1 close, 2 big favourite


def weights_by_class(mu: np.ndarray, m: np.ndarray, resid_by_class: dict) -> np.ndarray:
    W = np.zeros((len(mu), len(M.MIN_GRID)))
    cl = mclass(m)
    for c, resid in resid_by_class.items():
        idx = np.where(cl == c)[0]
        if len(idx):
            W[idx] = M.minutes_weights_empirical(np.clip(mu[idx], 1, 48), resid)
    return W


def main():
    p0 = M.load_params()
    L = canon.load()
    x, lg = B.frame(p0)
    x = add_margin(x, L["team_games"])
    played = (x.participation == "played") & x.n_played.notna() & x.min_mu.notna() & ~x.box_incomplete
    tr = x[played & x.season.isin(B.FIT_SEASONS) & (x.season_type == "REG")]
    ev = x[played & x.season.isin(B.VALIDATION)].reset_index(drop=True)
    ev["week"] = ev.date.dt.strftime("%G-%V")
    print("fit rows", len(tr), "validation rows", len(ev), file=sys.stderr)

    # variant S: shifted minutes, residuals refit around the shifted centre
    beta = fit_shift(tr)
    trS = tr.assign(min_mu=tr.min_mu + shift_features(tr.exp_margin.to_numpy(), tr.min_mu.to_numpy()) @ beta)
    pS = M.Params(**{**p0.__dict__, "min_resid": M.fit_minutes_residuals(trS)})
    evS = ev.assign(min_mu=ev.min_mu + shift_features(ev.exp_margin.to_numpy(), ev.min_mu.to_numpy()) @ beta)

    # variant R: residual distributions by margin class
    resid_by_class = {c: M.fit_minutes_residuals(tr[mclass(tr.exp_margin.to_numpy()) == c]) for c in (0, 1, 2)}

    pm0 = M.n1_pmfs(ev, p0, lg)
    pmS = M.n1_pmfs(evS, pS, lg)
    orig = M.minutes_weights_empirical
    mm = ev.exp_margin.to_numpy()
    M.minutes_weights_empirical = lambda mu, resid: (
        weights_by_class(mu, mm, resid_by_class) if resid is p0.min_resid else orig(mu, resid))
    try:
        pmR = M.n1_pmfs(ev, p0, lg)
    finally:
        M.minutes_weights_empirical = orig

    out = {"beta_shift": beta.round(4).tolist(), "fit_rows": len(tr), "validation_rows": len(ev),
           "class_counts_fit": np.bincount(mclass(tr.exp_margin.to_numpy()), minlength=3).tolist(),
           "markets": {}}
    big = np.abs(mm) >= 9
    rng = np.random.default_rng(11)
    for s in M.MARKETS:
        y = M.actual(ev, s)
        c0 = M.crps_rows(pm0[s], y)
        res = {"N1": float(c0.mean())}
        for name, pm in (("S", pmS), ("R", pmR)):
            c = M.crps_rows(pm[s], y)
            d = pd.DataFrame({"week": ev.week, "diff": c - c0})
            lo, hi = B._week_ci(d, "diff")
            dbig = d[big]
            lob, hib = B._week_ci(dbig, "diff")
            mean = (pm[s] * np.arange(pm[s].shape[1])).sum(1)
            pit = M.pit_rows(pm[s], y, rng)
            res[name] = {"crps": float(c.mean()), "diff": float(d["diff"].mean()), "ci": [lo, hi],
                         "rel_pct": float(100 * d["diff"].mean() / c0.mean()),
                         "diff_big_margin": float(dbig["diff"].mean()), "ci_big": [lob, hib],
                         "bias": float((mean - y).mean()), "bias_big": float((mean - y)[big].mean()),
                         "pit_low10": float((pit < 0.1).mean()), "pit_high10": float((pit > 0.9).mean())}
        mean0 = (pm0[s] * np.arange(pm0[s].shape[1])).sum(1)
        pit0 = M.pit_rows(pm0[s], y, rng)
        res["N1_bias"] = float((mean0 - y).mean())
        res["N1_bias_big"] = float((mean0 - y)[big].mean())
        res["N1_pit_low10"] = float((pit0 < 0.1).mean())
        res["N1_pit_high10"] = float((pit0 > 0.9).mean())
        out["markets"][s] = res
    out["share_big_margin"] = float(big.mean())
    json.dump({"beta": beta.tolist(), "resid_by_class": {str(k): v for k, v in resid_by_class.items()},
               "min_resid_S": pS.min_resid}, open(sys.argv[1], "w"))
    print(json.dumps(out, indent=1))


if __name__ == "__main__":
    main()
