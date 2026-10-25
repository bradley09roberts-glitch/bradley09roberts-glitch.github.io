"""Joint simulation: hard constraints, reproducibility, availability handling."""

import numpy as np
import pandas as pd
import pytest

from npl import sim
from npl.features import GameIn, Params, PlayerIn, TeamIn


def _player(pid, role, tshare=0.0, dshare=0.0, p_play=0.95, p_start=0.0, pool=None):
    return PlayerIn(player_id=pid, name=pid, position=role, role=role, team="T", p_play=p_play,
                    tshare=tshare, dshare=dshare, catch=0.65,
                    rec_pool=np.array(pool if pool is not None else [5, 8, 12, -3, 25]),
                    rush_pool=np.array([3, -2, 4, 1, 15]), scr_rate=0.05,
                    scr_pool=np.array([6, 9, -1]) if role == "QB" else None, p_start=p_start)


def _team(name, opp):
    return TeamIn(name, opp, True, 63.0, 0.6, 0.065, 0.03, 1.0, 1.0, 1.0, [
        _player(f"{name}qb1", "QB", p_start=0.9, dshare=0.05),
        _player(f"{name}qb2", "QB", p_start=0.1, dshare=0.05),
        _player(f"{name}wr1", "WR", tshare=0.25), _player(f"{name}wr2", "WR", tshare=0.18),
        _player(f"{name}te1", "TE", tshare=0.15, p_play=0.6),
        _player(f"{name}rb1", "RB", tshare=0.08, dshare=0.6),
        _player(f"{name}rb2", "RB", tshare=0.04, dshare=0.3, p_play=0.0),
    ])


@pytest.fixture
def setup():
    P = Params(league={"plays": 63, "dbr": 0.6, "sack_rate": 0.065, "scr_rate": 0.045,
                       "notarget": 0.03},
               resid_cov=np.array([[60, -28, 0.03, 0.1], [-28, 60, 0.1, -0.02],
                                   [0.03, 0.1, 0.0095, -0.0037], [0.1, -0.02, -0.0037, 0.0095]]),
               alpha_target=100, alpha_carry=12, q_qb_exit=0.06, p_qb_cameo=0.05,
               pools={("rec", "WR"): np.array([10, 12, 15, -2, 40]),
                      ("rec", "TE"): np.array([8, 9, 11]), ("rec", "RB"): np.array([5, -4, 7]),
                      ("rush", "RB"): np.array([4, 2, -1, 9]), ("rush", "QB"): np.array([5, 3]),
                      ("rush", "WR"): np.array([6]), ("scr", "QB"): np.array([7, 4]),
                      ("kneel", "QB"): np.array([-1.0])})
    g = GameIn("G1", 2024, 5, "REG", pd.Timestamp("2024-10-06T17:00Z"), _team("H", "A"),
               _team("A", "H"))
    return g, P


def test_constraints(setup):
    g, P = setup
    out = sim.simulate_game(g, P, 5000, seed=3)
    for side in ("home", "away"):
        sims, tt = out[side]
        assert (tt["completions"] <= tt["attempts"]).all()
        tgt = sum(s.stats["targets"] for s in sims)
        rec = sum(s.stats["receptions"] for s in sims)
        assert (tgt <= tt["attempts"]).all()
        assert (rec == tt["completions"]).all()
        for s in sims:
            assert (s.stats["receptions"] <= s.stats["targets"]).all()
            assert (s.stats["completions"] <= s.stats["attempts"]).all()
            assert (s.stats["targets"] >= 0).all()
        # QB attempts and completions sum to team totals unless the only backup is
        # absent; here two QBs exist, so they must reconcile exactly.
        qb_att = sum(s.stats["attempts"] for s in sims if s.player.role == "QB")
        qb_cmp = sum(s.stats["completions"] for s in sims if s.player.role == "QB")
        qb_yds = sum(s.stats["passing_yards"] for s in sims if s.player.role == "QB")
        rec_yds = sum(s.stats["receiving_yards"] for s in sims)
        assert (qb_att == tt["attempts"]).all()
        assert (qb_cmp == tt["completions"]).all()
        assert (qb_yds == rec_yds).all()
        # team carries = designed runs + scrambles + kneels
        car = sum(s.stats["carries"] for s in sims)
        assert (car == tt["designed_runs"] + tt["scrambles"] + tt["kneels"]).all()


def test_unavailable_player_never_plays_and_volume_is_redistributed(setup):
    g, P = setup
    out = sim.simulate_game(g, P, 3000, seed=1)
    sims, _ = out["home"]
    rb2 = next(s for s in sims if s.player.player_id == "Hrb2")
    assert not rb2.played.any()
    assert (rb2.stats["carries"] == 0).all()
    rb1 = next(s for s in sims if s.player.player_id == "Hrb1")
    # rb1's carries are drawn from the full designed-run volume when rb2 is out
    assert rb1.stats["carries"][rb1.played].mean() > 10


def test_negative_yards_possible(setup):
    g, P = setup
    out = sim.simulate_game(g, P, 5000, seed=2)
    rb1 = next(s for s in out["home"][0] if s.player.player_id == "Hrb1")
    assert (rb1.stats["receiving_yards"] < 0).any()


def test_same_seed_reproducible(setup):
    g, P = setup
    a = sim.simulate_game(g, P, 2000, seed=11)
    b = sim.simulate_game(g, P, 2000, seed=11)
    c = sim.simulate_game(g, P, 2000, seed=12)
    sa = a["home"][0][2].stats["receiving_yards"]
    assert (sa == b["home"][0][2].stats["receiving_yards"]).all()
    assert not (sa == c["home"][0][2].stats["receiving_yards"]).all()


def test_backup_qb_only_plays_on_exit_or_cameo(setup):
    g, P = setup
    out = sim.simulate_game(g, P, 20000, seed=5)
    sims, _ = out["home"]
    q1 = next(s for s in sims if s.player.player_id == "Hqb1")
    q2 = next(s for s in sims if s.player.player_id == "Hqb2")
    assert q1.played.mean() > 0.85
    assert q2.played.mean() < 0.3
    # someone threw every pass in every draw
    both = q1.stats["attempts"] + q2.stats["attempts"]
    assert (both == out["home"][1]["attempts"]).all()
