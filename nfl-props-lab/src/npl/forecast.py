"""Run every model on one slate and summarise forecasts per player x market."""

from __future__ import annotations

import hashlib

import numpy as np
import pandas as pd

from . import baselines, sim
from .config import CORE_MARKETS, MARKETS
from .dist import Dist
from .features import HP, ROLE_POS, GameIn, Params, build_slate

MODEL_VERSION = "0.3.0"
MODELS = {
    "X1": "Stacked J1 + M, same frozen weights (availability-informed; live headline)",
    "X": "Stacked: per-market linear pool of J0 and M (weights fixed on validation)",
    "J1": "Joint simulation + official injury-report status (availability-informed)",
    "J0": "Joint simulation, participation from history only (cutoff-faithful)",
    "A": "Baseline A: recency-weighted game log (NB / normal)",
    "C": "Baseline C: role-tier climatology (empirical distribution)",
    "M": "Gradient boosting mean + NB / normal distribution (ML challenger)",
}
QUANTILES = (0.05, 0.10, 0.25, 0.5, 0.75, 0.90, 0.95)
MIN_P_PLAY = 0.05


def game_seed(seed: int, game_id: str, model: str) -> int:
    return int(hashlib.sha256(f"{seed}|{game_id}|{model}".encode()).hexdigest()[:8], 16)


def eligible_markets(role: str, carries_hist: int) -> list[str]:
    out = []
    for m in CORE_MARKETS:
        if role not in {ROLE_POS[p] for p in m.positions}:
            continue
        if role == "WR" and m.key in ("rush_yds", "rush_att") and carries_hist < 5:
            continue
        out.append(m.key)
    return out


def summarise(d: Dist) -> dict:
    return {"mean": round(d.mean(), 3), "sd": round(d.sd(), 3), "median": d.median(),
            **{f"q{int(q * 100):02d}": d.quantile(q) for q in QUANTILES}}


def _role_record(p, t, g) -> dict:
    return {"player_id": p.player_id, "name": p.name, "position": p.position, "role": p.role,
            "team": t.team, "opponent": t.opponent, "is_home": t.is_home, "game_id": g.game_id,
            "role_inputs": {"target_share": round(p.tshare, 4),
                            "designed_run_share": round(p.dshare, 4),
                            "catch_rate": round(p.catch, 4), "p_start": round(p.p_start, 3),
                            "scramble_rate": round(p.scr_rate, 4),
                            "games_in_history": p.games_hist,
                            "targets_in_history": p.targets_hist,
                            "carries_in_history": p.carries_hist,
                            "own_catch_pool": int(len(p.rec_pool)),
                            "own_carry_pool": int(len(p.rush_pool))},
            "injury_report": p.injury_status, "flags": list(p.flags)}


def run_joint(games: list[GameIn], P: Params, model: str, n_sims: int, seed: int) -> tuple[list, list]:
    fc, teams = [], []
    for g in games:
        out = sim.simulate_game(g, P, n_sims, game_seed(seed, g.game_id, model))
        for side, t in (("home", g.home), ("away", g.away)):
            sims, tt = out[side]
            teams.append({"model": model, "game_id": g.game_id, "team": t.team,
                          "opponent": t.opponent, "inputs": {
                              "mu_plays": round(t.mu_plays, 2), "dropback_rate": round(t.dbr, 4),
                              "sack_rate": round(t.sack_rate, 4),
                              "opp_rec_yards_factor": round(t.rec_factor, 4),
                              "opp_catch_factor": round(t.catch_factor, 4),
                              "opp_rush_yards_factor": round(t.rush_factor, 4)},
                          "sim": {k: summarise(Dist.from_samples(v)) for k, v in tt.items()}})
            for s in sims:
                p = s.player
                p_play = float(s.played.mean())
                rec = _role_record(p, t, g)
                for mk in eligible_markets(p.role, p.carries_hist):
                    m = MARKETS[mk]
                    base = {**rec, "model": model, "market": mk, "p_play": round(p_play, 4),
                            "n_draws": int(n_sims), "n_draws_played": int(s.played.sum())}
                    if p_play < MIN_P_PLAY or s.played.sum() < 50:
                        fc.append({**base, "status": "not_projected_to_play"})
                        continue
                    d = Dist.from_samples(s.stats[m.stat][s.played])
                    fc.append({**base, "status": "ok", **summarise(d), "dist": d.to_json()})
    return fc, teams


def run_baselines(games: list[GameIn], h, joint_fc: list) -> list:
    """Baselines A and C for the same players and markets as the joint model."""
    fit_a = baselines.fit_a(h)
    ew = baselines.ew_means(h)
    rm = baselines.role_means(h)
    fit_c = baselines.fit_c(h)
    rows = [f for f in joint_fc if f["model"] == "J0"] or joint_fc
    pids = sorted({f["player_id"] for f in rows})
    roles = {f["player_id"]: f["role"] for f in rows}
    pa = baselines.predict_a(h, pids, roles, fit_a, ew, rm)
    pc = baselines.predict_c(h, pids, roles, fit_c)
    out = []
    for f in rows:
        for model, pred in (("A", pa), ("C", pc)):
            base = {k: v for k, v in f.items() if k not in ("dist", "mean", "sd", "median") and
                    not k.startswith("q")}
            base["model"] = model
            d = pred.get((f["player_id"], f["market"]))
            if f["status"] != "ok" or d is None:
                out.append({**base, "status": f["status"] if f["status"] != "ok" else
                            "no_baseline"})
                continue
            out.append({**base, "status": "ok", **summarise(d), "dist": d.to_json(),
                        "n_draws": None, "n_draws_played": None})
    return out


def mix_dist(a: Dist, b: Dist, w: float) -> Dist:
    """Linear pool w*a + (1-w)*b on the union of supports (a valid single distribution, so
    line probabilities stay monotone and sum to one)."""
    lo = min(a.lo, b.lo)
    hi = max(a.lo + len(a.counts), b.lo + len(b.counts))
    pa, pb = np.zeros(hi - lo), np.zeros(hi - lo)
    pa[a.lo - lo:a.lo - lo + len(a.counts)] = a.pmf
    pb[b.lo - lo:b.lo - lo + len(b.counts)] = b.pmf
    p = w * pa + (1 - w) * pb
    nz = np.nonzero(p > 5e-7)[0]
    return Dist.from_pmf(lo + int(nz[0]), p[nz[0]:nz[-1] + 1], scale=1_000_000)


def ml_rows_for(lake: dict, season: int, week: int, ml_models: dict, base_rows: list) -> list:
    """Model M forecasts for exactly the (game, player, market) rows of base_rows (J0)."""
    from . import ml

    rows = ml.load_cache([(season, week)])
    if not len(rows):
        rows = ml.slate_rows(lake, season, week)
    preds = {k: m.predict(rows) for k, m in ml_models.items()}
    idx = {(r.game_id, r.player_id): i for i, r in enumerate(rows.itertuples())}
    out = []
    for f in base_rows:
        base = {k: v for k, v in f.items() if k not in ("dist", "mean", "sd", "median",
                                                        "n_draws", "n_draws_played")
                and not k.startswith("q")}
        base["model"] = "M"
        i = idx.get((f["game_id"], f["player_id"]))
        if f["status"] != "ok" or i is None:
            out.append({**base, "status": f["status"] if f["status"] != "ok" else "no_row"})
            continue
        d = ml_models[f["market"]].dist(float(preds[f["market"]][i]))
        out.append({**base, "status": "ok", **summarise(d), "dist": d.to_json(),
                    "n_draws": None, "n_draws_played": None})
    return out


def mixture_rows(a_rows: list, b_rows: list, weights: dict, name: str) -> list:
    """weights: market -> weight on a (J0). Rows aligned by (game, player, market)."""
    bmap = {(f["game_id"], f["player_id"], f["market"]): f for f in b_rows}
    out = []
    for f in a_rows:
        g = bmap.get((f["game_id"], f["player_id"], f["market"]))
        base = {k: v for k, v in f.items() if k not in ("dist", "mean", "sd", "median")
                and not k.startswith("q")}
        base["model"] = name
        w = weights.get(f["market"]) if isinstance(weights, dict) else weights
        if f["status"] != "ok" or g is None or g["status"] != "ok" or w is None:
            out.append({**base, "status": f["status"] if f["status"] != "ok" else "no_pair"})
            continue
        d = mix_dist(Dist.from_json(f["dist"]), Dist.from_json(g["dist"]), w)
        out.append({**base, "status": "ok", "mix_weight_J0": w, **summarise(d),
                    "dist": d.to_json()})
    return out


# Per-market weight on J0 in the stacked model X (rest on M). FROZEN on 25 Oct 2026 from
# validation experiment 5 (2022-2023, 44 slates): the CRPS-minimising weight in
# {0, 0.25, 0.5, 0.75, 1} per market. Never re-tuned on the test seasons.
X_WEIGHTS: dict = {"pass_att": 0.5, "pass_cmp": 0.5, "pass_yds": 0.5, "rec_yds": 0.75,
                   "receptions": 0.0, "rush_att": 0.25, "rush_yds": 0.75}


def run_slate(lake: dict, season: int, week: int, *, n_sims: int = 10_000, seed: int = 20261025,
              injuries_asof: pd.DataFrame | None = None, models=("J1", "J0", "A", "C"),
              ml_models: dict | None = None, mix_grid: tuple = ()) -> dict:
    games0, P, h = build_slate(lake, season, week, variant="J0")
    fc, teams = run_joint(games0, P, "J0", n_sims, seed)
    games1 = None
    if "J1" in models:
        games1, _, _ = build_slate(lake, season, week, variant="J1", P=P, h=h,
                                   injuries_asof=injuries_asof)
        f1, t1 = run_joint(games1, P, "J1", n_sims, seed)
        fc += f1
        teams += t1
    if "A" in models or "C" in models:
        fc += run_baselines(games0, h, fc)
    if ml_models is not None:
        j0 = [f for f in fc if f["model"] == "J0"]
        m_rows = ml_rows_for(lake, season, week, ml_models, j0)
        fc += m_rows
        for w in mix_grid:  # validation grid: one pooled model per weight
            fc += mixture_rows(j0, m_rows, w, f"X{int(round(w * 100)):02d}")
        if "X" in models and X_WEIGHTS:
            fc += mixture_rows(j0, m_rows, X_WEIGHTS, "X")
        if "X1" in models and X_WEIGHTS:
            j1 = [f for f in fc if f["model"] == "J1"]
            fc += mixture_rows(j1, m_rows, X_WEIGHTS, "X1")
    params = {"league": P.league, "alpha_target": P.alpha_target, "alpha_carry": P.alpha_carry,
              "q_qb_exit": P.q_qb_exit, "kneel_mean": P.kneel_mean,
              "resid_cov": np.round(P.resid_cov, 6).tolist(), "hp": HP,
              "history_rows": {"player_games": len(h.pg), "team_games": len(h.tg),
                               "plays": len(h.plays)}}
    return {"games": games0, "forecasts": fc, "teams": teams, "params": params}
