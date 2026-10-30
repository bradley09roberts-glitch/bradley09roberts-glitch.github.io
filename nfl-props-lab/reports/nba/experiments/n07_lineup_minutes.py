"""n07: lineup-aware minutes (model.lineup_*).

For every candidate row (players seen for the team in its previous ten games): f_tot / f_pos =
expected minutes of teammates missing from this game (all, and in his position group); missing
= did not play (not listed, or no minutes). Variants: "all" (absences known before tip),
"missed_before" (pessimistic: a first missed game is a surprise) and "expected" (as live editions:
tonight's missing minutes from 1 - P(plays); history from observed absences). History = the same quantities averaged over the games behind
his min_mu, for the same team only. Only players listed for the team at some point this season
count (players who left in the summer remain candidates for a while). The shift in expected minutes is a pooled least-squares fit of (actual - min_mu) on
the differences, fitted on the 2018-2023 fit seasons; the minutes residual distributions are
refitted on the shifted forecasts (same seasons). Judged on every 2024-2025 player-game against
frozen nba-0.1 (N1 CRPS, all five markets, week-block intervals); 2026 (test) not used.
"""
import json
import sys

import numpy as np
import pandas as pd

from npl.nba import backtest as B
from npl.nba import canon
from npl.nba import model as M


def week_ci(week, diff, n=1000, seed=7):
    d = pd.DataFrame({"w": week, "d": diff}).groupby("w").d.agg(["sum", "count"])
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(d), (n, len(d)))
    b = d["sum"].to_numpy()[idx].sum(1) / d["count"].to_numpy()[idx].sum(1)
    return [float(np.percentile(b, 2.5)), float(np.percentile(b, 97.5))]


def main(out_path, known="all"):
    p = M.load_params("nba-0.1")  # the baseline: frozen nba-0.1, no lineup adjustment
    x, lg = B.frame(p)
    x = x.reset_index(drop=True)
    x["row"] = np.arange(len(x))
    x["active"] = M.season_active(x, canon.load()["player_games"])  # listed for the team this season
    absent = x.participation != "played"  # any candidate who did not play (as M.observed_absent)
    if known == "missed_before":  # pessimistic: a first missed game counts as a surprise
        absent = absent & (x.missed_streak.fillna(0) >= 1)
    absent = absent.astype(float)
    if known == "expected":  # as live: 1 - P(plays) from the availability model (fit seasons)
        clf = B.fit_availability(x)
        absent = pd.Series(1 - clf.predict_proba(M.avail_matrix(x))[:, 1], index=x.index)
    x["f_tot"], x["f_pos"] = M.absent_minutes(x, absent.to_numpy())
    h = x[x.min_mu.notna()].copy()
    h["f_tot"], h["f_pos"] = M.absent_minutes(h, M.observed_absent(h))  # history: observed
    if known == "missed_before":
        h["f_tot"], h["f_pos"] = M.absent_minutes(h, absent[x.min_mu.notna()].to_numpy())
    st = M.lineup_states(h, p)
    x = M.lineup_features(x, st).sort_values("row").reset_index(drop=True)
    ok = (x.participation == "played") & x.n_played.notna() & ~x.box_incomplete & x.min_mu.notna()
    fit = x[ok & x.season.isin(B.FIT_SEASONS)]
    Z = np.column_stack([fit[f].to_numpy(float) for f in M.LINEUP_FEATURES])
    y = (fit.minutes_y - fit.min_mu).to_numpy(float)
    beta, *_ = np.linalg.lstsq(Z, y, rcond=None)
    coef = dict(zip(M.LINEUP_FEATURES, beta.tolist(), strict=True))
    res = {"coef": coef, "fit_rows": int(len(fit))}
    x2 = x.copy()
    x2["min_mu"] = np.clip(x.min_mu + M.lineup_shift(x, coef), 1, 46).where(x.min_mu.notna())
    p2 = M.Params(**{**p.__dict__, "min_resid": M.fit_minutes_residuals(x2[ok & x2.season.isin(B.FIT_SEASONS)])})
    for split, seasons in (("fit_2018_2023", tuple(B.FIT_SEASONS)), ("validation_2024_2025", B.VALIDATION)):
        sel = (ok & x.season.isin(seasons)).to_numpy()
        e0, e1 = x[sel].reset_index(drop=True), x2[sel].reset_index(drop=True)
        out = {"rows": int(sel.sum()),
               "minutes_mae": {"base": float((e0.minutes_y - e0.min_mu).abs().mean()),
                               "lineup": float((e1.minutes_y - e1.min_mu).abs().mean())},
               "minutes_bias_full_roster_vs_short": {}}
        for name, e in (("base", e0), ("lineup", e1)):
            short = e.f_tot >= 15
            out["minutes_bias_full_roster_vs_short"][name] = [float((e.minutes_y - e.min_mu)[~short].mean()),
                                                              float((e.minutes_y - e.min_mu)[short].mean())]
        if split.startswith("validation"):
            week = e0.date.dt.strftime("%G-%V").to_numpy()
            pm0, pm1 = M.n1_pmfs(e0, p, lg), M.n1_pmfs(e1, p2, lg)
            out["crps"] = {}
            for s in M.MARKETS:
                c0 = M.crps_rows(pm0[s], M.actual(e0, s))
                c1 = M.crps_rows(pm1[s], M.actual(e1, s))
                moved = (e1.min_mu - e0.min_mu).abs().to_numpy() >= 1.5
                out["crps"][s] = {"base": float(c0.mean()), "change_pct": float((c1 - c0).mean() / c0.mean() * 100),
                                  "ci_pct": [v / c0.mean() * 100 for v in week_ci(week, c1 - c0)],
                                  "moved_rows": int(moved.sum()),
                                  "moved_change_pct": float((c1 - c0)[moved].mean() / c0[moved].mean() * 100)}
        res[split] = out
        json.dump(res, open(out_path, "w"), indent=1)
    json.dump({**res, "min_resid": p2.min_resid}, open(out_path.replace(".json", "_full.json"), "w"))
    print(json.dumps(res, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], *(sys.argv[2:3]))
