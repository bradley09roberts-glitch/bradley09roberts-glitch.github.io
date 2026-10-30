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


def test_market_line_ignores_lines_a_book_no_longer_offers():
    from npl.nba import appdata

    def q(t, book, line, over, under):
        return [{"game_id": "g", "player_id": "p", "market": "pra", "bookmaker": book,
                 "line": line, "selection": s, "decimal_price": d, "observed_at": t,
                 "availability": "research_reference"}
                for s, d in (("over", over), ("under", under))]
    early, late = "2026-10-28T12:27:49Z", "2026-10-28T21:40:02Z"
    rows = (q(early, "dk", 39.5, 1.9, 1.9) + q(early, "fd", 39.5, 1.9, 1.9) +
            q(early, "mgm", 39.5, 1.9, 1.9) + q(late, "dk", 38.5, 1.92, 1.83) +
            q(late, "fd", 38.5, 1.96, 1.82))
    m = appdata.consensus_lines(pd.DataFrame(rows))[("p", "pra")]
    assert m["line"] == 38.5 and m["books"] == 2  # mgm stopped quoting: not counted
    assert m["best_under"] == 1.83 and m["observed_at"] == late


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
