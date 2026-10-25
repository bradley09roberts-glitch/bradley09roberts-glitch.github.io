"""Checks on the real lake (skipped when data/lake has not been built)."""

import numpy as np
import pandas as pd
import pytest

from npl.config import LAKE_DIR, REPORTS_DIR

pytestmark = pytest.mark.skipif(not (LAKE_DIR / "player_games.parquet").exists(),
                                reason="lake not built (run npl ingest && npl build)")


@pytest.fixture(scope="module")
def lake():
    return {k: pd.read_parquet(LAKE_DIR / f"{k}.parquet")
            for k in ("games", "team_games", "player_games", "plays")}


def test_all_32_teams_present_in_2026(lake):
    g = lake["games"]
    teams = set(g[g.season == 2026].home_team) | set(g[g.season == 2026].away_team)
    assert len(teams) == 32


def test_every_game_has_two_team_rows(lake):
    tg = lake["team_games"]
    assert (tg.groupby("game_id").size() == 2).all()


def test_no_preseason_in_nflverse(lake):
    assert set(lake["games"].season_type) <= {"REG", "POST"}


def test_stat_definitions_reconcile_with_pbp_including_overtime(lake):
    """Team totals from player stats equal play-by-play counts, overtime included:
    attempts = completions + incompletions + spikes; carries = designed runs + scrambles +
    kneels. Tolerance: 99.5% of team-games exact (stat corrections/laterals aside)."""
    pg, tg = lake["player_games"], lake["team_games"]
    s = pg[(pg.participation == "played") & pg.season.between(2016, 2025)].groupby(
        ["game_id", "team"])[["attempts", "carries", "completions"]].sum()
    t = tg.set_index(["game_id", "team"]).join(s, how="inner")
    t = t[t.season.between(2016, 2025)]
    assert ((t.attempts == t.pass_att + t.spike).mean()) > 0.995
    assert ((t.carries == t.designed_rush + t.scramble + t.kneel).mean()) > 0.995
    assert ((t.completions == t.completion).mean()) > 0.995
    ot = lake["plays"][lake["plays"].qtr >= 5]
    assert len(ot) > 0  # overtime plays are present and counted in the team totals


def test_negative_yardage_exists_and_is_kept(lake):
    p = lake["plays"]
    assert (p[p.kind == "rush"].rushing_yards < 0).mean() > 0.05
    assert (p[p.kind == "completion"].receiving_yards < 0).any()


def test_participation_labels_cover_2016_2025(lake):
    pg = lake["player_games"]
    s = pg[pg.season.between(2016, 2025) & pg.skill]
    assert (s.participation == "active_unverified").mean() < 0.01
    # genuine zeros exist: played with zero targets
    wr = s[(s.position == "WR") & (s.participation == "played")]
    assert (wr.targets == 0).any()
    # and non-participants have no stat values
    assert s.loc[s.participation != "played", "targets"].isna().all()


def test_committed_backtest_summaries_parse():
    for p in (REPORTS_DIR / "backtests").glob("*/summary.json"):
        import json

        s = json.loads(p.read_text())
        for mk in s["markets"].values():
            for m in mk["models"].values():
                assert np.isfinite(m["crps"])
                assert abs(sum(m["pit_deciles"]) - 1) < 0.01


def test_committed_headline_ledgers_reproduce_their_summaries():
    """Anyone holding a committed ledger can recompute the headline table."""
    import json

    from npl import evaluate

    for p in (REPORTS_DIR / "backtests").glob("*/ledger.csv.gz"):
        s = json.loads((p.parent / "summary.json").read_text())
        led = evaluate.load_ledger(p)
        out = evaluate.compare(led, s["models"], s["baseline"])
        for mk, r in s["markets"].items():
            assert out["markets"][mk]["n_common"] == r["n_common"]
            for m, x in r["models"].items():
                assert abs(out["markets"][mk]["models"][m]["crps"] - x["crps"]) < 1e-3


@pytest.mark.skipif(not list((LAKE_DIR.parent.parent / "records" / "editions").glob("*/*/*.json")),
                    reason="no edition published")
def test_scenario_rules_and_redistribution():
    from npl import appdata

    e = appdata.latest_edition()
    gid = next(g["game_id"] for g in e["games"] if g["published_before_kickoff"])
    assert "error" in appdata.scenario(gid, ["not-a-player"])
    cards = appdata.game(gid)["cards"]
    team = cards[0]["team"]
    qbs = sorted({c["player_id"] for c in cards if c["team"] == team and c["position"] == "QB"})
    assert "no quarterback" in appdata.scenario(gid, qbs)["error"]
    wr = max((c for c in cards if c["team"] == team and c["market"] == "receptions"),
             key=lambda c: c["mean"])
    r = appdata.scenario(gid, [wr["player_id"]])
    mates = [p for p in r["players"] if p["team"] == team and not p["removed"]
             and p["targets"]["base"]]
    assert all(p["targets"]["scenario"] >= p["targets"]["base"] - 0.15 for p in mates)
    assert sum(p["targets"]["scenario"] - p["targets"]["base"] for p in mates) > 1.0
    assert r["label"].startswith("Exploratory")
