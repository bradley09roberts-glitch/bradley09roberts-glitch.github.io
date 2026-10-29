"""Machine-learning challenger M: gradient boosting on point-in-time opportunity features.

Feature rows are the same pre-game inputs the joint model uses (built by
features.build_slate with variant J0, so no injury report and no odds), plus simple
expected-opportunity products and baseline-A recency means. One LightGBM model per
market predicts the conditional mean (Poisson objective for counts, L2 for yards, which
can be negative); the predictive distribution is negative binomial (counts) or a
discretised normal with a fitted mean-SD relation (yards), both fitted on training
residuals only.

Retraining schedule (fixed): refit at weeks 1, 5, 9, 13, 17 and 19 of every evaluated
season on all feature rows strictly before the slate.
"""

from __future__ import annotations

import json
import warnings
from pathlib import Path

import lightgbm as lgb
import numpy as np
import pandas as pd
from scipy import stats as sps

from . import baselines
from .config import CORE_MARKETS, FEATURE_DIR, MARKETS
from .dist import Dist
from .features import ROLE_POS, build_slate

REFIT_WEEKS = (1, 5, 9, 13, 17, 19)
FEATURES = ["role_QB", "role_RB", "role_WR", "role_TE", "is_home", "mu_plays", "dbr",
            "sack_rate", "opp_rec", "opp_catch", "opp_rush", "tshare", "dshare", "catch",
            "p_play_hist", "p_start", "games_hist", "targets_hist", "carries_hist", "scr_rate",
            "own_rec_n", "own_rush_n", "own_ypc", "own_ypr", "exp_att", "exp_tgt", "exp_dr",
            "ew_attempts", "ew_completions", "ew_passing_yards", "ew_carries",
            "ew_rushing_yards", "ew_targets", "ew_receptions", "ew_receiving_yards", "week",
            "rest", "dome", "thin_history", "no_history"]
LGB_PARAMS = {"num_leaves": 15, "learning_rate": 0.05, "n_estimators": 300,
              "min_child_samples": 50, "subsample": 0.8, "subsample_freq": 1,
              "colsample_bytree": 0.8, "reg_lambda": 1.0, "verbose": -1, "random_state": 13}


def slate_rows(lake: dict, season: int, week: int) -> pd.DataFrame:
    games, P, h = build_slate(lake, season, week, variant="J0")
    ew = baselines.ew_means(h)
    tg = lake["team_games"].set_index(["game_id", "team"])
    rows = []
    for g in games:
        for t in (g.home, g.away):
            rest = tg["rest"].get((g.game_id, t.team), np.nan)
            dome = float(str(tg["roof"].get((g.game_id, t.team), "")) in ("dome", "closed"))
            att = t.mu_plays * t.dbr * (1 - t.sack_rate - P.league["scr_rate"])
            for p in t.players:
                r = {"game_id": g.game_id, "season": season, "week": week,
                     "season_type": g.season_type, "player_id": p.player_id, "name": p.name,
                     "position": p.position, "role": p.role, "team": t.team,
                     "opponent": t.opponent, "is_home": float(t.is_home),
                     "mu_plays": t.mu_plays, "dbr": t.dbr, "sack_rate": t.sack_rate,
                     "opp_rec": t.rec_factor, "opp_catch": t.catch_factor,
                     "opp_rush": t.rush_factor, "tshare": p.tshare, "dshare": p.dshare,
                     "catch": p.catch, "p_play_hist": p.p_play, "p_start": p.p_start,
                     "games_hist": p.games_hist, "targets_hist": p.targets_hist,
                     "carries_hist": p.carries_hist, "scr_rate": p.scr_rate,
                     "own_rec_n": len(p.rec_pool), "own_rush_n": len(p.rush_pool),
                     "own_ypc": float(np.mean(p.rec_pool)) if len(p.rec_pool) else np.nan,
                     "own_ypr": float(np.mean(p.rush_pool)) if len(p.rush_pool) else np.nan,
                     "exp_att": att, "exp_tgt": att * p.tshare,
                     "exp_dr": t.mu_plays * (1 - t.dbr) * p.dshare, "rest": rest, "dome": dome,
                     "thin_history": float("thin_history" in p.flags),
                     "no_history": float("no_history" in p.flags)}
                for c in ("attempts", "completions", "passing_yards", "carries",
                          "rushing_yards", "targets", "receptions", "receiving_yards"):
                    r[f"ew_{c}"] = float(ew.loc[p.player_id, c]) if p.player_id in ew.index \
                        else np.nan
                for role in ("QB", "RB", "WR", "TE"):
                    r[f"role_{role}"] = float(p.role == role)
                rows.append(r)
    return pd.DataFrame(rows)


def build_cache(lake: dict, slates: list[tuple[int, int]], force: bool = False) -> None:
    FEATURE_DIR.mkdir(parents=True, exist_ok=True)
    for s, w in slates:
        f = FEATURE_DIR / f"ml_{s}_{w:02d}.parquet"
        if f.exists() and not force:
            continue
        slate_rows(lake, s, w).to_parquet(f, index=False)


def load_cache(slates: list[tuple[int, int]]) -> pd.DataFrame:
    parts = [pd.read_parquet(FEATURE_DIR / f"ml_{s}_{w:02d}.parquet") for s, w in slates
             if (FEATURE_DIR / f"ml_{s}_{w:02d}.parquet").exists()]
    # no cached slate (e.g. a new week): empty, so callers compute the rows directly
    return pd.concat(parts, ignore_index=True) if parts else pd.DataFrame()


def attach_outcomes(x: pd.DataFrame, pg: pd.DataFrame) -> pd.DataFrame:
    cols = ["game_id", "player_id", "participation"] + sorted({m.stat for m in CORE_MARKETS})
    return x.merge(pg[cols], on=["game_id", "player_id"], how="left")


class MarketModel:
    def __init__(self, market):
        self.m = market
        self.model = None
        self.disp = {}

    def fit(self, d: pd.DataFrame):
        roles = {ROLE_POS[p] for p in self.m.positions}
        d = d[d.role.isin(roles) & (d.participation == "played") & d[self.m.stat].notna()]
        y = d[self.m.stat].to_numpy(float)
        obj = "poisson" if self.m.kind == "count" else "regression"
        self.model = lgb.LGBMRegressor(objective=obj, **LGB_PARAMS)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore")
            self.model.fit(d[FEATURES], y)
        mu = self.model.predict(d[FEATURES])
        if self.m.kind == "count":
            # NB size by moments of the residuals: Var = mu + mu^2 / r
            num = np.sum(mu ** 2)
            den = np.sum((y - mu) ** 2 - mu)
            self.disp = {"r": float(np.clip(num / max(den, 1e-6), 0.5, 500))}
        else:
            b = pd.qcut(mu, 20, duplicates="drop")
            g = pd.DataFrame({"mu": mu, "res": y - mu, "b": b}).groupby("b", observed=True)
            m_, s_ = g.mu.mean(), g.res.std()
            ok = (m_ > 1) & (s_ > 0)
            bb, aa = np.polyfit(np.log(m_[ok]), np.log(s_[ok]), 1)
            self.disp = {"a": float(aa), "b": float(bb)}
        self.n_train = len(d)
        return self

    def predict(self, d: pd.DataFrame) -> np.ndarray:
        return self.model.predict(d[FEATURES])

    def dist(self, mu: float) -> Dist:
        if self.m.kind == "count":
            mu = max(mu, 0.02)
            r = self.disp["r"]
            p = r / (r + mu)
            hi = int(sps.nbinom.ppf(0.99995, r, p)) + 1
            k = np.arange(0, hi + 1)
            return Dist.from_pmf(0, sps.nbinom.pmf(k, r, p))
        sd = float(np.exp(self.disp["a"] + self.disp["b"] * np.log(max(mu, 1.0))))
        lo, hi = max(int(np.floor(mu - 5 * sd)), -30), int(np.ceil(mu + 5 * sd))
        k = np.arange(lo, hi + 1)
        pmf = sps.norm.cdf(k + 0.5, mu, sd) - sps.norm.cdf(k - 0.5, mu, sd)
        return Dist.from_pmf(lo, pmf)


def fit_all(train: pd.DataFrame) -> dict:
    return {m.key: MarketModel(m).fit(train) for m in CORE_MARKETS}


def forecasts_for(models: dict, rows: pd.DataFrame, joint_fc: dict) -> list[dict]:
    """M forecasts for the same (game, player, market) rows the joint model produced, so
    that comparisons use identical subsets. joint_fc maps (game, player, market) -> J0 row."""
    out = []
    preds = {k: mm.predict(rows) for k, mm in models.items()}
    idx = {(r.game_id, r.player_id): i for i, r in enumerate(rows.itertuples())}
    for key, f in joint_fc.items():
        g, pid, mk = key
        base = {k: v for k, v in f.items() if k not in ("dist", "mean", "sd", "median") and
                not k.startswith("q")}
        base["model"] = "M"
        i = idx.get((g, pid))
        if f["status"] != "ok" or i is None:
            out.append({**base, "status": f["status"] if f["status"] != "ok" else "no_row"})
            continue
        d = models[mk].dist(float(preds[mk][i]))
        out.append({**base, "status": "ok", "mean": round(d.mean(), 3), "sd": round(d.sd(), 3),
                    "median": d.median(), **{f"q{int(q * 100):02d}": d.quantile(q)
                                              for q in (0.05, 0.10, 0.25, 0.5, 0.75, 0.9, 0.95)},
                    "dist": d.to_json()})
    return out


def importance(models: dict) -> dict:
    return {k: dict(sorted(zip(FEATURES, mm.model.feature_importances_.tolist(), strict=True),
                           key=lambda kv: -kv[1])[:12]) for k, mm in models.items()}


def describe(models: dict) -> str:
    return json.dumps({k: {"n_train": mm.n_train, "disp": mm.disp} for k, mm in models.items()},
                      indent=1)


__all__ = ["slate_rows", "build_cache", "load_cache", "fit_all", "forecasts_for", "MARKETS"]


def run_backtest(slates: list[tuple[int, int]], *, label: str, lake: dict,
                 train_from: int = 2016) -> dict:
    """Walk-forward ML backtest on cached feature rows. Refit on REFIT_WEEKS using every
    cached row strictly before the slate; forecast the slate; score with the same scorer
    as the joint model (backtest.score_rows) so ledgers are directly comparable."""
    import time

    from . import backtest
    from .config import RUNS_DIR
    from .edition import code_version
    from .forecast import MIN_P_PLAY, eligible_markets, summarise
    from .util import iso, sha256_file, sha256_json, utcnow

    code = code_version()
    all_slates = sorted({(int(s), int(w)) for s, w in zip(lake["games"].season,
                                                          lake["games"].week, strict=True)})
    pg = lake["player_games"]
    models, fitted_at, parts, fits = None, None, [], []
    t0 = time.time()
    for season, week in slates:
        if models is None or week in REFIT_WEEKS or fitted_at[0] != season:
            prior = [x for x in all_slates if train_from <= x[0] and x < (season, week)]
            train = attach_outcomes(load_cache(prior), pg)
            models = fit_all(train)
            fitted_at = (season, week)
            fits.append({"slate": [season, week], "train_rows": len(train),
                         "per_market": {k: {"n": m.n_train, "disp": m.disp}
                                        for k, m in models.items()}})
        rows = load_cache([(season, week)])
        preds = {k: m.predict(rows) for k, m in models.items()}
        fc = []
        for i, r in enumerate(rows.itertuples()):
            for mk in eligible_markets(r.role, int(r.carries_hist)):
                base = {"model": "M", "game_id": r.game_id, "player_id": r.player_id,
                        "name": r.name, "position": r.position, "role": r.role,
                        "team": r.team, "opponent": r.opponent, "market": mk,
                        "p_play": r.p_play_hist,
                        "role_inputs": {"games_in_history": int(r.games_hist)}}
                if r.p_play_hist < MIN_P_PLAY:
                    fc.append({**base, "status": "not_projected_to_play"})
                    continue
                d = models[mk].dist(float(preds[mk][i]))
                fc.append({**base, "status": "ok", **summarise(d), "dist": d.to_json()})
        sc = backtest.score_rows(fc, pg)
        sc["season"], sc["week"] = season, week
        sc["season_type"] = rows.season_type.iloc[0] if len(rows) else None
        parts.append(sc)
    led = pd.concat(parts, ignore_index=True)
    run_id = f"{label}-{utcnow().strftime('%Y%m%dT%H%M%SZ')}"
    d = RUNS_DIR / run_id
    d.mkdir(parents=True, exist_ok=True)
    led.to_csv(d / "ledger.csv.gz", index=False)
    man = {"run_id": run_id, "created_at": iso(utcnow()), "label": label,
           "splits": backtest.SPLITS, "slates": slates, "models": ["M"], "n_sims": 0,
           "seed": LGB_PARAMS["random_state"], "model_version": "ml-0.1", "code": code,
           "lgb_params": LGB_PARAMS, "features": FEATURES, "refit_weeks": REFIT_WEEKS,
           "fits": fits, "ledger_sha256": sha256_file(d / "ledger.csv.gz"), "rows": len(led),
           "exclusions": led[~led.scored].groupby(["model", "participation", "status"]).size()
           .rename("n").reset_index().to_dict("records"), "seconds": round(time.time() - t0)}
    man["manifest_hash"] = sha256_json(man)
    (d / "manifest.json").write_text(json.dumps(man, indent=1, default=str))
    return {"run_id": run_id, "dir": str(d), "rows": len(led)}


def merge_runs(dirs: list, label: str) -> Path:
    """Concatenate ledgers of runs over the same slates into one comparable run dir."""
    from .config import RUNS_DIR
    from .util import sha256_file

    leds, mans = [], []
    for x in dirs:
        x = Path(x)
        leds.append(pd.read_csv(x / "ledger.csv.gz", low_memory=False))
        mans.append(json.loads((x / "manifest.json").read_text()))
    d = RUNS_DIR / label
    d.mkdir(parents=True, exist_ok=True)
    led = pd.concat(leds, ignore_index=True)
    led.to_csv(d / "ledger.csv.gz", index=False)
    m0 = dict(mans[0])
    m0.update({"run_id": label, "label": label, "merged_from": [m["run_id"] for m in mans],
               "models": sorted({mm for m in mans for mm in m["models"]}),
               "codes": [m["code"] for m in mans], "rows": len(led),
               "ledger_sha256": sha256_file(d / "ledger.csv.gz"),
               "exclusions": [e for m in mans for e in m["exclusions"]]})
    (d / "manifest.json").write_text(json.dumps(m0, indent=1, default=str))
    return d
