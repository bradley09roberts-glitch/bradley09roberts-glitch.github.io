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
    "J1": "Joint simulation + official injury-report status (availability-informed)",
    "J0": "Joint simulation, participation from history only (cutoff-faithful)",
    "A": "Baseline A: recency-weighted game log (NB / normal)",
    "C": "Baseline C: role-tier climatology (empirical distribution)",
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


def run_slate(lake: dict, season: int, week: int, *, n_sims: int = 10_000, seed: int = 20261025,
              injuries_asof: pd.DataFrame | None = None, models=("J1", "J0", "A", "C")) -> dict:
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
    params = {"league": P.league, "alpha_target": P.alpha_target, "alpha_carry": P.alpha_carry,
              "q_qb_exit": P.q_qb_exit, "kneel_mean": P.kneel_mean,
              "resid_cov": np.round(P.resid_cov, 6).tolist(), "hp": HP,
              "history_rows": {"player_games": len(h.pg), "team_games": len(h.tg),
                               "plays": len(h.plays)}}
    return {"games": games0, "forecasts": fc, "teams": teams, "params": params}
