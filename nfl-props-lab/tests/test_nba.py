"""NBA model pieces: distributions, scoring, point-in-time features and candidates."""

import numpy as np
import pandas as pd
import pytest

from npl.dist import Dist
from npl.nba import model as M


def test_nb_rows_are_distributions_with_right_mean():
    pm = M.nb_pmf_matrix(np.array([0.5, 4.0, 20.0]), 12.0, 80)
    assert np.allclose(pm.sum(1), 1)
    means = (pm * np.arange(81)).sum(1)
    assert np.allclose(means, [0.5, 4.0, 20.0], atol=0.02)


def test_vectorised_crps_matches_dist_crps():
    rng = np.random.default_rng(1)
    pm = M.nb_pmf_matrix(np.array([3.0, 11.0]), 5.0, 40)
    y = np.array([0, 17])
    v = M.crps_rows(pm, y)
    for i in range(2):
        d = Dist.from_pmf(0, pm[i], scale=10**9)
        assert v[i] == pytest.approx(d.crps(y[i]), abs=1e-4)
    assert rng is not None


def test_minutes_weights_and_pra_consistency():
    w = M.minutes_weights(np.array([30.0, 2.0]), np.array([5.0, 5.0]))
    assert np.allclose(w.sum(1), 1)
    x = pd.DataFrame({"min_mu": [30.0], "min_sd": [5.0], "pos": ["G"], "pace_ew": [100.0],
                      "opp_pace": [100.0], "lg_pace": [100.0], "opp_n": [10],
                      "min_sum": [300.0], "pts_sum": [180.0], "reb_sum": [50.0],
                      "ast_sum": [60.0], "fg3m_sum": [20.0],
                      **{f"def_{s}": [1.0] for s in M.BASE_STATS},
                      **{f"lg_{s}": [1.0] for s in M.BASE_STATS}})
    lg = {"pos_rate": {"G": {"pts": .5, "reb": .15, "ast": .15, "fg3m": .05},
                       "F": {"pts": .45, "reb": .2, "ast": .1, "fg3m": .04}},
          "per_poss": {s: 1.0 for s in M.BASE_STATS}, "pace48": 100.0}
    pm = M.n1_pmfs(x, M.Params(), lg)
    mean = {s: float((pm[s] * np.arange(pm[s].shape[1])).sum()) for s in M.MARKETS}
    assert mean["pra"] == pytest.approx(mean["pts"] + mean["reb"] + mean["ast"], rel=0.03)
    for s in M.MARKETS:
        assert pm[s].sum() == pytest.approx(1)


def _box(rows):
    df = pd.DataFrame(rows, columns=["game_id", "tip", "team", "opp", "player_id", "minutes",
                                     "pts"])
    df["tip_utc"] = pd.to_datetime(df.tip, utc=True)
    df["date"] = df.tip_utc.dt.tz_convert(None).dt.normalize()
    df["participation"] = np.where(df.minutes > 0, "played", "dnp")
    df["name"] = df.player_id
    df["pos"] = "G"
    df["season"] = 2026
    df["season_type"] = "REG"
    df["starter"] = True
    for s in ("reb", "ast", "fg3m"):
        df[s] = 1.0
    return df


def test_candidates_mark_absent_players_and_features_are_point_in_time():
    pg = _box([
        ("g1", "2025-11-01T00:00Z", "AAA", "BBB", "p1", 30, 20),
        ("g1", "2025-11-01T00:00Z", "AAA", "BBB", "p2", 20, 8),
        ("g2", "2025-11-03T00:00Z", "AAA", "CCC", "p1", 34, 30),
        # g3: p2 not listed (ruled out before the game) -> absent
        ("g3", "2025-11-05T00:00Z", "AAA", "BBB", "p1", 36, 40),
    ])
    games = pd.DataFrame({"game_id": ["g1", "g2", "g3"], "home": "AAA",
                          "away": ["BBB", "CCC", "BBB"], "season": 2026, "season_type": "REG",
                          "date": pg.drop_duplicates("game_id").date.to_numpy(),
                          "tip_utc": pg.drop_duplicates("game_id").tip_utc.to_numpy()})
    c = M.candidates(pg, games)
    g3 = c[(c.game_id == "g3") & (c.team == "AAA")].set_index("player_id")
    assert g3.loc["p2", "participation"] == "absent"
    assert g3.loc["p1", "participation"] == "played"
    ps = M.player_states(pg, M.Params())
    x = M.assemble(c, ps, pd.DataFrame(columns=["team", "tip_utc", "pace_ew", "lg_pace",
                                                "n_team", *[f"def_{s}" for s in M.BASE_STATS],
                                                *[f"lg_{s}" for s in M.BASE_STATS]]).astype(
        {"tip_utc": "datetime64[ns, UTC]", "team": "str"}))
    r = x[(x.game_id == "g3") & (x.player_id == "p1")].iloc[0]
    assert r.n_played == 2  # g1 and g2 only, never the game being forecast
    assert r.pts_l10 == pytest.approx(25.0)


def test_latest_edition_is_by_publish_time_not_label(tmp_path, monkeypatch):
    from npl.nba import live

    d = tmp_path / "2026-10-28"
    d.mkdir()
    for name in ("NBA-2026-10-28-preview-20261028T1117Z", "NBA-2026-10-28-early-20261028T1227Z",
                 "NBA-2026-10-28-final-20261028T1905Z"):
        (d / f"{name}.json").write_text("{}")
    monkeypatch.setattr(live, "NBA_EDITIONS", tmp_path)
    assert live.latest_edition_path().stem.endswith("final-20261028T1905Z")
    assert [p.stem.split("-")[4] for p in live.edition_files()] == ["preview", "early", "final"]


def test_market_line_uses_each_books_own_latest_rows():
    """Rows no logged pull covers (the fallback; tests/test_pull_log.py has the pull log): dk and
    fd moved 39.5 -> 38.5, so their earlier line no longer counts. BetMGM has no later rows (the
    store keeps an unchanged market's rows once), so its 39.5 still votes, until its rows are
    more than 12 hours older than the latest observation (books.complete_board)."""
    from npl.nba import appdata

    def q(t, book, line, over, under):
        return [{"game_id": "g", "player_id": "p", "market": "pra", "bookmaker": book,
                 "line": line, "selection": s, "decimal_price": d, "observed_at": t,
                 "availability": "research_reference"}
                for s, d in (("over", over), ("under", under))]
    early, late = "2026-10-28T12:27:49Z", "2026-10-28T21:40:02Z"
    moved = q(late, "dk", 38.5, 1.92, 1.83) + q(late, "fd", 38.5, 1.96, 1.82)
    rows = (q(early, "dk", 39.5, 1.9, 1.9) + q(early, "fd", 39.5, 1.9, 1.9) +
            q(early, "betmgm", 39.5, 1.9, 1.9) + moved)
    m = appdata.consensus_lines(pd.DataFrame(rows))[("p", "pra")]
    assert m["line"] == 38.5 and m["books"] == 2  # two votes for 38.5, BetMGM's for 39.5
    assert m["mgm_line"] == 39.5 and m["mgm_over"] is None  # not priced off its own main line
    assert m["best_under"] == 1.83 and m["observed_at"] == late
    stale = q("2026-10-28T08:40:01Z", "betmgm", 39.5, 1.9, 1.9)  # 13 hours before the latest
    m = appdata.consensus_lines(pd.DataFrame(stale + moved))[("p", "pra")]
    assert m["line"] == 38.5 and m["books"] == 2 and m["mgm_line"] is None


def test_slate_counts_every_injured_player_of_the_edition(monkeypatch):
    """Out (OUT, OFS) and questionable (any other entry: a game-time decision) per team, for
    both teams of every game, once per player, from all of the edition's players."""
    from npl.nba import appdata

    def f(pid, team, opp, gid, fantasy=None, model="N1", market="pts", p_play=0.9):
        inj = None if fantasy is None else {"status": "Out", "fantasy": fantasy}
        return {"game_id": gid, "player_id": pid, "team": team, "opponent": opp,
                "model": model, "market": market, "injury": inj, "p_play": p_play}

    e = {"edition_id": "e", "date_et": "2026-11-02", "published_at": "2026-11-02T20:00:00Z",
         "kind": "final", "headline_model": "N1", "code": {"model_version": "nba-0.3"},
         "games": [{"game_id": "g1", "home": "CLE", "away": "OKC",
                    "tip_utc": "2026-11-03T00:00:00Z", "published_before_tip": True},
                   {"game_id": "g2", "home": "MIA", "away": "BOS",
                    "tip_utc": "2026-11-03T00:30:00Z", "published_before_tip": True}],
         "forecasts": [f("1", "CLE", "OKC", "g1", "OUT", p_play=0.0),
                       f("1", "CLE", "OKC", "g1", "OUT", market="reb", p_play=0.0),
                       f("1", "CLE", "OKC", "g1", "OUT", model="A", p_play=0.0),
                       f("2", "CLE", "OKC", "g1", "OFS", p_play=0.0),
                       f("3", "CLE", "OKC", "g1", "GTD", p_play=0.55),
                       f("4", "OKC", "CLE", "g1"),
                       f("5", "MIA", "BOS", "g2", "GTD", p_play=0.5)]}
    assert appdata.injured_counts(e) == {
        "CLE": {"out": 2, "questionable": 1}, "OKC": {"out": 0, "questionable": 0},
        "MIA": {"out": 0, "questionable": 1}, "BOS": {"out": 0, "questionable": 0}}
    monkeypatch.setattr(appdata, "latest", lambda: e)
    games = appdata.slate()["games"]
    assert games[0]["injured"] == {"CLE": {"out": 2, "questionable": 1},
                                   "OKC": {"out": 0, "questionable": 0}}
    assert games[1]["injured"] == {"MIA": {"out": 0, "questionable": 1},
                                   "BOS": {"out": 0, "questionable": 0}}
    assert games[0]["published_before_tip"] is True


def _absence_case(ms=0):
    import pandas as pd
    return pd.DataFrame({
        "game_id": ["g", "g", "g", "h"], "team": ["MIN", "MIN", "MIN", "LAC"],
        "min_mu": [30.0, 20.0, 10.0, 25.0], "missed_streak": [ms, 0, 0, 0],
        "min_sum": [100.0] * 4, "pts_sum": [80.0, 50.0, 30.0, 40.0], "reb_sum": [20.0] * 4,
        "ast_sum": [10.0] * 4, "fg3m_sum": [5.0] * 4})


def test_absence_adjust_hands_minutes_and_usage_to_teammates():
    import numpy as np
    import pytest

    from npl.nba import model as M
    x = _absence_case()
    absent = np.array([True, False, False, False])
    out = M.absence_adjust(x, absent, np.ones(4), 1.0, 1.0, M.Params())
    assert out.min_mu.tolist() == pytest.approx([30.0, 40.0, 20.0, 25.0])   # 30 freed, 2:1
    share = 24 / (24 + 10 + 3)                                               # his share of points
    assert out.rmult_pts.tolist() == pytest.approx([1.0, 1 + share, 1 + share, 1.0])


def test_absence_adjust_counts_only_what_recent_form_has_not_absorbed():
    import numpy as np
    import pytest

    from npl.nba import model as M
    x = _absence_case(ms=3)                                                  # missed the last 3
    out = M.absence_adjust(x, np.array([True, False, False, False]), np.ones(4), 1.0, 0.0, M.Params())
    assert out.min_mu.tolist() == pytest.approx([30.0, 30.0, 15.0, 25.0])   # half of 30 freed
    assert (out.rmult_pts == 1.0).all()


def test_absent_minutes_counts_teammates_only_and_by_position():
    import numpy as np
    import pandas as pd

    from npl.nba import model as M
    x = pd.DataFrame({"game_id": ["g"] * 4, "team": ["MIN"] * 4, "pos": ["G", "G", "F", "C"],
                      "min_mu": [34.0, 20.0, 30.0, 25.0]})
    tot, pos = M.absent_minutes(x, np.array([1.0, 0.0, 0.5, 0.0]))
    assert tot.tolist() == [15.0, 49.0, 34.0, 49.0]   # 34 (out) + 15 (half of 30), minus own
    assert pos.tolist() == [0.0, 34.0, 0.0, 0.0]


def test_lineup_shift_two_sided():
    import pandas as pd

    from npl.nba import model as M
    coef = {"d_tot": 0.1, "d_tot_r": 0.0, "d_tot_r2": 0.0, "d_pos": 0.0, "d_pos_r": 0.0}
    x = pd.DataFrame({"d_tot": [20.0, -20.0, 0.0], "d_tot_r": 0, "d_tot_r2": 0, "d_pos": 0, "d_pos_r": 0})
    # more teammate minutes missing than usual -> more minutes; teammates back -> fewer
    assert M.lineup_shift(x, coef).tolist() == [2.0, -2.0, 0.0]


def test_frozen_params_are_nba_0_2_with_lineup():
    import json

    from npl.nba import model as M
    d = json.loads(M.frozen_path().read_text())
    assert d["model_version"] == M.MODEL_VERSION == "nba-0.2"
    assert set(M.load_params().lineup) == set(M.LINEUP_FEATURES)


def test_lineup_cal_factor_by_team_games_played():
    import numpy as np

    from npl.nba import model as M
    cal = {"0-5": 0.7, "5-10": 0.9, "10-999": 1.0}
    assert M.lineup_cal_factor(cal, np.array([0, 4, 5, 12])).tolist() == [0.7, 0.7, 0.9, 1.0]
    assert M.lineup_cal_factor(None, np.array([3])).tolist() == [1.0]


def test_simple_team_rest_and_projection():
    import pandas as pd
    import pytest

    from npl.nba import simple as SM
    games = pd.DataFrame({"game_id": ["a", "b", "c"], "home": ["BOS", "BOS", "NY"], "away": ["NY", "MIA", "BOS"],
                          "season": 2027, "date": pd.to_datetime(["2026-10-20", "2026-10-21", "2026-10-25"]),
                          "tip_utc": pd.to_datetime(["2026-10-20T23:00Z", "2026-10-21T23:00Z", "2026-10-25T23:00Z"])})
    r = SM.team_rest(games).set_index(["game_id", "team"]).rest
    assert r[("a", "BOS")] == 3 and r[("b", "BOS")] == 0 and r[("c", "BOS")] == 3  # opener, b2b, 3 days off
    f = pd.DataFrame({"rest": [0], "min_trend": [1.1], **{f"avg_{s}": [10.0] for s in SM.MARKETS},
                      **{f"opp_factor_{s}": [1.2] for s in SM.MARKETS}})
    rest = {s: {0: 0.9, 1: 1.0, 2: 1.0, 3: 1.0} for s in SM.MARKETS}
    assert SM.project(f, rest).pts[0] == pytest.approx(10 * 1.2 * 0.9 * 1.1)


def _one_player():
    x = pd.DataFrame({"min_mu": [30.4], "min_sd": [5.0], "pos": ["G"], "pace_ew": [100.0],
                      "opp_pace": [100.0], "lg_pace": [100.0], "opp_n": [10],
                      "min_sum": [300.0], "pts_sum": [180.0], "reb_sum": [50.0],
                      "ast_sum": [60.0], "fg3m_sum": [20.0],
                      **{f"def_{s}": [1.0] for s in M.BASE_STATS},
                      **{f"lg_{s}": [1.0] for s in M.BASE_STATS}})
    lg = {"pos_rate": {"G": {"pts": .5, "reb": .15, "ast": .15, "fg3m": .05},
                       "F": {"pts": .45, "reb": .2, "ast": .1, "fg3m": .04}},
          "per_poss": {s: 1.0 for s in M.BASE_STATS}, "pace48": 100.0}
    return x, lg


def test_nba_0_3_changes_nothing_under_nba_0_1_params():
    p1 = M.load_params("nba-0.1")
    x, lg = _one_player()
    a = M.n1_pmfs(x, p1, lg)
    b = M.n1_pmfs(x.assign(has_line=True), p1, lg)
    for s in M.MARKETS:
        assert np.array_equal(a[s], b[s])


def test_nba_0_3_line_factor_nb1_points_and_fractional_minutes():
    p3 = M.load_params("nba-0.3")
    assert p3.min_frac and p3.pts_nb1_phi and p3.line_fac["fg3m"] > 1
    x, lg = _one_player()
    no, yes = M.n1_pmfs(x.assign(has_line=False), p3, lg), M.n1_pmfs(x.assign(has_line=True), p3, lg)
    k = np.arange(no["pts"].shape[1])
    for s in M.BASE_STATS:  # a posted line lifts the mean by the fitted factor
        kk = np.arange(no[s].shape[1])
        assert (yes[s] * kk).sum() / (no[s] * kk).sum() == pytest.approx(p3.line_fac[s], rel=2e-3)
    # points ~ NB1: variance phi x mean for a fixed mean
    pm = M.nb1_pmf_matrix(np.array([22.0]), p3.pts_nb1_phi, 120)[0]
    mu = (pm * np.arange(121)).sum()
    assert mu == pytest.approx(22.0, rel=1e-6)
    assert (pm * (np.arange(121) - mu) ** 2).sum() == pytest.approx(22.0 * p3.pts_nb1_phi, rel=1e-4)
    assert (no["pts"] * k).sum() > 0
    # fractional centre: at a whole number it equals the rounded table
    resid = p3.min_resid
    w_int = M.minutes_weights_empirical(np.array([30.0]), resid, frac=True)
    assert np.allclose(w_int, M.minutes_weights_empirical(np.array([30.0]), resid, frac=False))
