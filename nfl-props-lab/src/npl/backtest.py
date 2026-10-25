"""Walk-forward (chronological) backtest.

Split boundaries are fixed in SPLITS before any evaluation was inspected (decided from
coverage on 2026-10-25, see DECISIONS.md D-004). For each slate (season, week) every
model is fitted on History.before(season, week) only, the whole slate is forecast, and
only then are its outcomes joined. Parameters are re-estimated every week (weekly
retraining schedule).

Rows are scored only where the player actually took part (participation == 'played'),
matching the usual bookmaker rule that a non-participant's prop is void; every excluded
row is counted by reason in the run manifest.
"""

from __future__ import annotations

import json
import time

import numpy as np
import pandas as pd

from . import forecast
from .config import MARKETS, RUNS_DIR
from .dist import Dist
from .features import load_lake
from .util import iso, sha256_file, sha256_json, utcnow

SPLITS = {
    "train": (2012, 2021),       # feature history 2012+, model fitting 2016+
    "validation": (2022, 2023),  # model selection, calibration, strategy thresholds
    "test": (2024, 2025),        # untouched until the frozen final run
    "prospective_from": (2026, 7),
}
MILESTONES = {"pass_yds": [200, 250, 300], "pass_att": [30, 35], "pass_cmp": [20, 25],
              "rush_yds": [25, 50, 75, 100], "rush_att": [10, 15, 20],
              "receptions": [3, 4, 5, 6], "rec_yds": [25, 50, 75, 100]}


def score_rows(fc: list[dict], pg: pd.DataFrame) -> pd.DataFrame:
    """Join forecasts to outcomes and score each eligible row."""
    out = pg.set_index(["game_id", "player_id"])
    rows = []
    for f in fc:
        key = (f["game_id"], f["player_id"])
        part = out["participation"].get(key, "absent") if key in out.index else "absent"
        r = {k: f.get(k) for k in ("model", "game_id", "player_id", "name", "position", "role",
                                   "team", "opponent", "market", "status", "p_play", "mean",
                                   "median", "q05", "q10", "q25", "q75", "q90", "q95")}
        r["participation"] = part
        r["games_in_history"] = f.get("role_inputs", {}).get("games_in_history")
        r["injury_report"] = f.get("injury_report")
        stat = MARKETS[f["market"]].stat
        y = out[stat].get(key) if key in out.index else np.nan
        r["actual"] = y
        if f["status"] == "ok" and part == "played" and pd.notna(y):
            d = Dist.from_json(f["dist"])
            r["crps"] = d.crps(y)
            r["log_score"] = d.log_score(int(y))
            r["pit_lo"] = 1 - d.p_ge(y)  # P(X < y)
            r["pit_hi"] = 1 - d.p_ge(y + 1)  # P(X <= y)
            r["in80"] = d.quantile(0.10) <= y <= d.quantile(0.90)
            r["in90"] = d.quantile(0.05) <= y <= d.quantile(0.95)
            r["in50"] = d.quantile(0.25) <= y <= d.quantile(0.75)
            for k in MILESTONES[f["market"]]:
                r[f"p_ge_{k}"] = d.p_ge(k)
                r[f"y_ge_{k}"] = float(y >= k)
            r["scored"] = True
        else:
            r["scored"] = False
        rows.append(r)
    return pd.DataFrame(rows)


def run(seasons_weeks: list[tuple[int, int]], *, label: str, models=("J0", "J1", "A", "C"),
        n_sims: int = 4000, seed: int = 7, lake: dict | None = None, with_ml: bool = False,
        mix_grid: tuple = ()) -> dict:
    lake = lake or load_lake()
    code = _code()  # captured at start: the code that actually ran
    run_id = f"{label}-{utcnow().strftime('%Y%m%dT%H%M%SZ')}"
    d = RUNS_DIR / run_id
    d.mkdir(parents=True, exist_ok=True)
    parts, t0 = [], time.time()
    ml_state = {"models": None, "season": None, "fits": []}
    for season, week in seasons_weeks:
        mlm = _ml_models_for(lake, season, week, ml_state) if with_ml else None
        res = forecast.run_slate(lake, season, week, n_sims=n_sims, seed=seed, models=models,
                                 ml_models=mlm, mix_grid=mix_grid)
        sc = score_rows(res["forecasts"], lake["player_games"])
        sc["season"], sc["week"] = season, week
        sc["season_type"] = res["games"][0].season_type if res["games"] else None
        parts.append(sc)
    led = pd.concat(parts, ignore_index=True)
    led_path = d / "ledger.csv.gz"
    led.to_csv(led_path, index=False)
    manifest = {
        "run_id": run_id, "created_at": iso(utcnow()), "label": label, "splits": SPLITS,
        "slates": seasons_weeks, "models": list(models), "n_sims": n_sims, "seed": seed,
        "model_version": forecast.MODEL_VERSION, "code": code, "with_ml": with_ml,
        "mix_grid": list(mix_grid), "x_weights": forecast.X_WEIGHTS,
        "ml_fits": ml_state["fits"],
        "lake_manifest_sha256": sha256_file(RUNS_DIR.parent / "lake" / "build_manifest.json"),
        "ledger_sha256": sha256_file(led_path), "rows": len(led),
        "exclusions": led[~led.scored].groupby(["model", "participation", "status"]).size()
        .rename("n").reset_index().to_dict("records"),
        "seconds": round(time.time() - t0, 1),
    }
    manifest["manifest_hash"] = sha256_json(manifest)
    (d / "manifest.json").write_text(json.dumps(manifest, indent=1, default=str))
    return {"run_id": run_id, "dir": str(d), "rows": len(led)}


def _ml_models_for(lake: dict, season: int, week: int, state: dict):
    """Refit M on the fixed schedule (ml.REFIT_WEEKS, and at each new season) using cached
    feature rows strictly before the slate."""
    from . import ml

    if state["models"] is None or week in ml.REFIT_WEEKS or state["season"] != season:
        g = lake["games"]
        prior = sorted({(int(s), int(w)) for s, w in zip(g.season, g.week, strict=True)
                        if 2016 <= s and (s, w) < (season, week)})
        train = ml.attach_outcomes(ml.load_cache(prior), lake["player_games"])
        state["models"] = ml.fit_all(train)
        state["season"] = season
        state["fits"].append({"slate": [season, week], "train_rows": len(train)})
    return state["models"]


def _code():
    from .edition import code_version

    return code_version()


def summary(led: pd.DataFrame, by=("model", "market")) -> pd.DataFrame:
    s = led[led.scored]
    g = s.groupby(list(by))
    out = g.agg(n=("crps", "size"), crps=("crps", "mean"), log_score=("log_score", "mean"),
                mae=("mean", lambda x: np.nan), bias=("actual", lambda x: np.nan),
                cov50=("in50", "mean"), cov80=("in80", "mean"), cov90=("in90", "mean"))
    s = s.assign(err=s["mean"] - s["actual"], aerr=(s["median"] - s["actual"]).abs())
    g2 = s.groupby(list(by))
    out["mae"] = g2["aerr"].mean()
    out["bias"] = g2["err"].mean()
    out["rmse"] = np.sqrt(g2["err"].apply(lambda e: (e ** 2).mean()))
    return out.reset_index()
