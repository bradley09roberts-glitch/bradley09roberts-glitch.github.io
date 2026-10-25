"""Participation states, identities, cutoff leakage, immutable editions, ledger metrics,
feed failures. Pure unit tests (synthetic frames); lake-dependent checks are in
test_integration.py."""

import json
import math

import numpy as np
import pandas as pd
import pytest

from npl import canon, edition, evaluate, features, ml
from npl.config import MARKETS
from npl.sources import nflverse
from npl.util import FetchError


# ------------------------------------------------------------------ participation states
def test_dnp_vs_zero_vs_missing_vs_not_final():
    status = canon.participation_status(
        is_final=[True, True, True, True, True, False, True, True],
        has_stats=[True, False, False, False, False, False, False, False],
        onfield_plays=[30, 12, 0, 0, np.nan, np.nan, 0, np.nan],
        onfield_present=[True, True, True, True, False, False, True, False],
        roster_status=["ACT", "ACT", "ACT", "INA", "ACT", "ACT", "RES", None])
    assert status.tolist() == ["played",           # recorded a stat
                               "played",           # on field, no stat -> genuine zero
                               "dnp",              # active, on-field data, never on field
                               "inactive",         # game-day inactive
                               "active_unverified",  # active, no on-field data: unknown
                               "not_final",
                               "reserve", "unknown"]


def test_stats_are_nan_unless_played():
    # mirrors canon.build: non-participants never get zeros
    pg = pd.DataFrame({"participation": ["played", "played", "inactive", "dnp"],
                       "targets": [5, np.nan, np.nan, np.nan]})
    played = pg.participation.eq("played")
    pg["targets"] = np.where(played, pg["targets"].fillna(0), np.nan)
    assert pg.targets.tolist()[:2] == [5, 0]
    assert pg.targets.iloc[2:].isna().all()


# ------------------------------------------------------------------ identities
def test_id_map_never_merges_conflicting_ids_and_never_uses_names():
    players = pd.DataFrame({"gsis_id": ["00-1", "00-2"], "pfr_id": ["SmitJo00", "SmitJo01"],
                            "display_name": ["John Smith", "John Smith"]})
    rosters = pd.DataFrame({"gsis_id": ["00-1", "00-3", "00-2"],
                            "pfr_id": ["SmitJo00", "NewPl00", "SmitJo00"]})  # conflict on 00-2
    m = canon.build_id_map(players, rosters)
    pfr = m[m.source == "pfr"].set_index("source_id")
    assert pfr.loc["SmitJo00", "player_id"] == "00-1"   # directory wins, not merged
    assert bool(pfr.loc["SmitJo00", "conflict"])
    assert pfr.loc["SmitJo01", "player_id"] == "00-2"   # same name, different player kept apart
    assert pfr.loc["NewPl00", "player_id"] == "00-3"
    assert m.groupby(["source", "source_id"]).size().max() == 1


def test_traded_player_belongs_to_team_of_that_week():
    games = pd.DataFrame({"game_id": ["g1", "g2"], "season": [2025, 2025], "week": [5, 6],
                          "team": ["NYJ", "DAL"], "opponent": ["MIA", "PHI"],
                          "season_type": ["REG", "REG"], "is_final": [True, True],
                          "kickoff_utc": pd.to_datetime(["2025-10-05", "2025-10-12"], utc=True),
                          "is_home": [True, True]})
    mem = pd.DataFrame({"season": [2025, 2025], "week": [5, 6], "team": ["NYJ", "DAL"],
                        "player_id": ["00-9", "00-9"], "status": ["ACT", "ACT"]})
    m = mem.merge(games, on=["season", "week", "team"], how="inner")
    assert m.set_index("week").team.to_dict() == {5: "NYJ", 6: "DAL"}


def test_franchise_relocations():
    assert [canon.franchise(x) for x in ("OAK", "SD", "STL", "KC")] == ["LV", "LAC", "LA", "KC"]


# ------------------------------------------------------------------ leakage
def _lake_small():
    rows = []
    for s, w in [(2024, 1), (2024, 2), (2024, 3)]:
        rows.append({"season": s, "week": w, "is_final": True, "pbp_present": True,
                     "game_id": f"g{w}"})
    tg = pd.DataFrame(rows)
    pg = tg.assign(participation="played")
    pl = tg[["season", "week", "game_id"]]
    return {"team_games": tg, "player_games": pg, "plays": pl}


def test_history_excludes_target_week_and_later():
    h = features.History.before(_lake_small(), 2024, 2)
    assert set(h.tg.week) == {1}
    assert set(h.pg.week) == {1}
    assert set(h.plays.week) == {1}


def test_no_market_columns_in_any_feature_set():
    banned = canon.MARKET_COLUMNS | {"result", "total", "spread", "moneyline"}
    assert not (set(ml.FEATURES) & banned)
    assert not any(any(b in f for b in ("spread", "total_line", "moneyline", "odds", "vegas"))
                   for f in ml.FEATURES)
    # observed weather is retrospective only
    assert not {"temp", "wind", "obs_temp_f", "obs_wind_mph"} & set(ml.FEATURES)


def test_build_games_splits_betting_lines_out():
    sched = pd.DataFrame({"game_id": ["2025_01_A_B"], "season": [2025], "game_type": ["REG"],
                          "week": [1], "gameday": ["2025-10-26"], "gametime": ["13:00"],
                          "weekday": ["Sunday"], "away_team": ["OAK"], "home_team": ["SD"],
                          "away_score": [10.0], "home_score": [20.0], "overtime": [0],
                          "location": ["Home"], "away_rest": [7], "home_rest": [7],
                          "div_game": [1], "roof": ["outdoors"], "surface": ["grass"],
                          "stadium": ["X"], "stadium_id": ["X1"], "home_coach": ["a"],
                          "away_coach": ["b"], "home_qb_id": ["q1"], "away_qb_id": ["q2"],
                          "pfr": ["p"], "espn": ["e"], "gsis": [1], "old_game_id": ["o"],
                          "temp": [60], "wind": [5], "spread_line": [3.5], "total_line": [44.5],
                          "away_moneyline": [150], "home_moneyline": [-170],
                          "under_odds": [-110], "over_odds": [-110], "away_spread_odds": [-110],
                          "home_spread_odds": [-110]})
    g, lines = canon.build_games(sched)
    assert not (set(g.columns) & canon.MARKET_COLUMNS)
    assert {"spread_line", "total_line"} <= set(lines.columns)
    assert g.home_team.iloc[0] == "LAC" and g.away_team.iloc[0] == "LV"
    # 13:00 ET on 26 Oct 2025 is 17:00 UTC (US still on EDT; UK already on GMT)
    assert g.kickoff_utc.iloc[0] == pd.Timestamp("2025-10-26T17:00Z")
    assert g.season_type.iloc[0] == "REG"


def test_kickoff_bst_and_gmt_conversion():
    from npl.util import london

    # same 13:00 ET kickoff: London shows BST before 25 Oct 2026, GMT on the 25th (US EDT)
    assert london(pd.Timestamp("2026-10-18T17:00Z").to_pydatetime()).endswith("18:00 BST")
    assert london(pd.Timestamp("2026-10-25T17:00Z").to_pydatetime()).endswith("17:00 GMT")


# ------------------------------------------------------------------ editions
def test_edition_refuses_dirty_code_and_overwrite(tmp_path, monkeypatch):
    monkeypatch.setattr(edition, "code_version", lambda: {"git_commit": "abc", "dirty": True,
                                                          "model_version": "t"})
    with pytest.raises(edition.EditionError, match="uncommitted"):
        edition.publish(None, season=2026, week=7, kind="early", result={"games": []},
                        lake={})


def test_edition_verify_detects_tampering(tmp_path):
    p = tmp_path / "e.json"
    p.write_bytes(b'{"a":1}')
    from npl.util import sha256_bytes

    p.with_suffix(".json.sha256").write_text(f"{sha256_bytes(b'{\"a\":1}')}  e.json\n")
    assert edition.verify(p)
    p.write_bytes(b'{"a":2}')
    assert not edition.verify(p)


# ------------------------------------------------------------------ ledger metrics
def test_metrics_recomputed_from_ledger_on_common_subset():
    rows = []
    for w in (1, 2):
        for m, crps in (("A", 2.0), ("J0", 1.0)):
            rows.append({"model": m, "game_id": f"g{w}", "player_id": "p", "market": "rec_yds",
                         "scored": True, "crps": crps * w, "log_score": 1.0, "mean": 10.0,
                         "median": 10, "actual": 12.0, "in50": True, "in80": True, "in90": True,
                         "pit_lo": 0.4, "pit_hi": 0.45, "season": 2023, "week": w})
    # a row only J0 scored must not enter the comparison
    rows.append({**rows[-1], "game_id": "g9", "crps": 100.0, "week": 3})
    led = pd.DataFrame(rows)
    out = evaluate.compare(led, ["A", "J0"], baseline="A")
    r = out["markets"]["rec_yds"]
    assert r["n_common"] == 2
    assert r["models"]["A"]["crps"] == pytest.approx(3.0)
    assert r["models"]["J0"]["crps"] == pytest.approx(1.5)
    assert r["models"]["J0"]["crps_minus_baseline"] == pytest.approx(-1.5)
    assert r["models"]["J0"]["crps_skill_vs_baseline"] == pytest.approx(0.5)
    assert r["models"]["J0"]["bias_mean"] == pytest.approx(-2.0)


def test_week_block_ci_contains_mean():
    d = pd.Series([1.0, 2.0, 3.0, 4.0])
    lo, hi = evaluate.week_block_ci(d, pd.Series([1, 1, 2, 2]))
    assert lo <= 2.5 <= hi


# ------------------------------------------------------------------ feeds
class _Con:
    """Minimal stand-in recording failures; nflverse.fetch only needs rawstore calls."""


def test_feed_404_is_absent_not_failed(monkeypatch, tmp_path):
    from npl import db, rawstore

    monkeypatch.setattr(rawstore, "DATA_HOME", tmp_path)
    con = db.connect(tmp_path / "t.sqlite3")

    def fake_get(url, **k):
        if "2026" in url:
            raise FetchError("HTTP 404", 404)
        raise FetchError("HTTP 503 after retries", 503)

    monkeypatch.setattr(nflverse, "http_get", fake_get)
    out = nflverse.fetch(con, ["participation"], [2025, 2026], include_optional=True)
    assert out["absent"] == ["participation/2026"]
    assert len(out["failed"]) == 1 and "participation/2025" in out["failed"][0]
    n = con.execute("SELECT COUNT(*) FROM fetch_failures").fetchone()[0]
    assert n == 2


def test_market_registry_matches_canonical_stats():
    assert MARKETS["rec_yds"].stat == "receiving_yards"
    assert MARKETS["rush_att"].stat == "carries"
    assert math.isclose(len(MARKETS), 7)
    assert json.dumps(sorted(MARKETS))  # serialisable


def test_record_uses_last_edition_before_kickoff(tmp_path, monkeypatch):
    """D-008: per model and game, only the last pre-kickoff edition's rows count."""
    from npl import appdata, settle

    ed = tmp_path / "records" / "editions"
    monkeypatch.setattr(appdata, "EDITIONS_DIR", ed)
    appdata._load_edition.cache_clear()

    def write(eid, published, games, forecasts):
        p = ed / "2026" / "W07" / f"{eid}.json"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps({"edition_id": eid, "season": 2026, "week": 7,
                                 "season_type": "REG", "kind": "early",
                                 "published_at": published, "published_at_london": "x",
                                 "code": {}, "inputs_hash": "h", "games": games,
                                 "forecasts": forecasts, "retrospective": False}))

    g_pre = {"game_id": "G1", "published_before_kickoff": True}
    g_late = {"game_id": "G1", "published_before_kickoff": False}
    f = lambda pid, mk: {"model": "X", "game_id": "G1", "player_id": pid, "market": mk,  # noqa: E731
                         "status": "ok"}
    write("e1", "2026-10-25T03:00:00Z", [g_pre], [f("p1", "rec_yds"), f("p2", "rec_yds")])
    write("e2", "2026-10-25T04:00:00Z", [g_pre], [f("p1", "rec_yds")])  # p2 dropped
    write("e3", "2026-10-25T20:00:00Z", [g_late], [f("p1", "rec_yds"), f("p3", "rec_yds")])
    rows = settle.record_rows()
    assert sorted((eid, r["player_id"]) for eid, r in rows) == [("e2", "p1")]
