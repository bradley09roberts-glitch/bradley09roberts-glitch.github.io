"""Chronological NBA backtests: every game uses only games that tipped off before it.

Splits (D-019): fit 2018-2023 (2016-2017 warm-up), validation 2024-2025, test 2026 (run
once, frozen), prospective 2027. Distributions are scored conditional on playing (books
void props for players who do not play); P(plays) is scored separately.
"""

from __future__ import annotations

import json
import subprocess

import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression

from ..config import DATA_HOME, PROJECT_ROOT, REPORTS_DIR
from ..util import iso, utcnow
from . import canon
from . import model as M

FIT_SEASONS = range(2018, 2024)
VALIDATION = (2024, 2025)
TEST = (2026,)
RUNS = DATA_HOME / "nba" / "runs"


def frame(p: M.Params | None = None) -> tuple[pd.DataFrame, dict]:
    p = p or M.Params()
    L = canon.load()
    pg, tg, games = L["player_games"], L["team_games"], L["games"]
    x = M.assemble(M.candidates(pg, games), M.player_states(pg, p), M.team_states(tg, p))
    y = pg[["game_id", "player_id", *M.BASE_STATS, "minutes"]].rename(
        columns={s: f"{s}_y" for s in [*M.BASE_STATS, "minutes"]})
    x = x.merge(y, on=["game_id", "player_id"], how="left")
    x = M.availability_features(x, pg, games)
    # incomplete box scores: team minutes far below regulation
    bad = set(tg.loc[tg.minutes < 235, "game_id"])
    x["box_incomplete"] = x.game_id.isin(bad)
    return x, M.league_rates(pg, tg, FIT_SEASONS)


def fit_availability(x: pd.DataFrame) -> LogisticRegression:
    tr = x[x.season.isin(FIT_SEASONS)]
    clf = LogisticRegression(C=1.0, max_iter=2000)
    clf.fit(M.avail_matrix(tr), (tr.participation == "played").to_numpy())
    return clf


def _week_ci(d: pd.DataFrame, col: str, n_boot: int = 2000, seed: int = 7) -> tuple:
    w = d.groupby("week")[col].agg(["sum", "count"])
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(w), (n_boot, len(w)))
    s, c = w["sum"].to_numpy(), w["count"].to_numpy()
    boot = s[idx].sum(1) / c[idx].sum(1)
    return float(np.percentile(boot, 2.5)), float(np.percentile(boot, 97.5))


def git_commit() -> dict:
    def g(*a):
        return subprocess.run(["git", *a], cwd=PROJECT_ROOT, capture_output=True, text=True,
                              check=False).stdout.strip()
    return {"git_commit": g("rev-parse", "HEAD"),
            "dirty": bool(g("status", "--porcelain", "--", "src"))}


def run(seasons: tuple[int, ...], label: str, p: M.Params | None = None,
        x: pd.DataFrame | None = None, lg: dict | None = None) -> dict:
    p = p or M.Params()
    if x is None:
        x, lg = frame(p)
    clf = fit_availability(x)
    ev_all = x[x.season.isin(seasons)].copy()
    ev_all["p_play"] = clf.predict_proba(M.avail_matrix(ev_all))[:, 1]
    ev_all["p_play_naive"] = ev_all.frac_played_10.clip(0.02, 0.98)
    ev_all["week"] = ev_all.date.dt.strftime("%G-%V")
    y = (ev_all.participation == "played").to_numpy()
    part = {}
    for k in ("p_play", "p_play_naive"):
        q = ev_all[k].clip(1e-4, 1 - 1e-4).to_numpy()
        part[k] = {"brier": float(((q - y) ** 2).mean()),
                   "log_loss": float(-(y * np.log(q) + (1 - y) * np.log(1 - q)).mean())}
    bins = pd.cut(ev_all.p_play, [0, .1, .3, .5, .7, .9, .97, 1])
    part["reliability"] = ev_all.assign(y=y).groupby(bins, observed=True).agg(
        n=("y", "size"), mean_p=("p_play", "mean"), freq=("y", "mean")).reset_index(
        drop=True).to_dict("records")

    ev = ev_all[(ev_all.participation == "played") & ev_all.n_played.notna() &
                ~ev_all.box_incomplete].reset_index(drop=True)
    pms = {"N1": M.n1_pmfs(ev, p, lg), "A": M.baseline_pmfs(ev, p, "A"),
           "C": M.baseline_pmfs(ev, p, "C")}
    rng = np.random.default_rng(11)
    rows, summ = [], {}
    for s in M.MARKETS:
        yv = M.actual(ev, s)
        sc = {}
        for name, pm in pms.items():
            c = M.crps_rows(pm[s], yv)
            pit = M.pit_rows(pm[s], yv, rng)
            mean = (pm[s] * np.arange(pm[s].shape[1])).sum(1)
            sc[name] = c
            rows.append(pd.DataFrame({"game_id": ev.game_id, "player_id": ev.player_id,
                                      "date": ev.date, "market": s, "model": name,
                                      "crps": c, "pit": pit, "mean": mean, "actual": yv}))
        d = pd.DataFrame({"week": ev.week, **{k: v for k, v in sc.items()}})
        res = {"n": len(ev)}
        for name in pms:
            res[name] = float(d[name].mean())
        for name in ("N1", "C"):
            d["diff"] = d[name] - d["A"]
            lo, hi = _week_ci(d, "diff")
            res[f"{name}_minus_A"] = float(d["diff"].mean())
            res[f"{name}_minus_A_ci"] = [lo, hi]
        summ[s] = res
    led = pd.concat(rows, ignore_index=True)
    pit = led.groupby(["market", "model"]).pit.apply(
        lambda v: np.histogram(v, bins=10, range=(0, 1))[0] / len(v)).apply(list)
    bias = led.assign(err=led["mean"] - led.actual).groupby(["market", "model"]).err.mean()
    out = {"label": label, "seasons": list(seasons), "created_at": iso(utcnow()),
           "params": {k: v for k, v in p.__dict__.items()}, "code": git_commit(),
           "rows_scored": len(ev), "rows_candidates": len(ev_all),
           "excluded_no_history": int(((ev_all.participation == "played") &
                                       ev_all.n_played.isna()).sum()),
           "excluded_incomplete_box": int(((ev_all.participation == "played") &
                                           ev_all.box_incomplete).sum()),
           "participation": part, "markets": summ,
           "pit_deciles": {f"{m}|{k}": v for (m, k), v in pit.items()},
           "bias": {f"{m}|{k}": float(v) for (m, k), v in bias.items()}}
    d = REPORTS_DIR / "nba" / "backtests" / label
    d.mkdir(parents=True, exist_ok=True)
    (d / "summary.json").write_text(json.dumps(out, indent=1, default=float))
    RUNS.mkdir(parents=True, exist_ok=True)
    led.to_parquet(RUNS / f"{label}.parquet", index=False)
    return out
