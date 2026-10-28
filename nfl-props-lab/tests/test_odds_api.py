"""Odds adapter on a synthetic payload shaped like The Odds API v4 event-odds response:
parsing, availability classification, game/player linking and storage."""

import pandas as pd
import pytest

from npl import cli, db, rawstore
from npl.sources import odds_api as oa
from npl.util import redact

PAYLOAD = {
    "id": "evt1", "commence_time": "2026-10-27T00:15:00Z",
    "home_team": "Philadelphia Eagles", "away_team": "Dallas Cowboys",
    "bookmakers": [
        {"key": "draftkings", "last_update": "2026-10-26T13:00:00Z", "markets": [
            {"key": "player_reception_yds", "last_update": "2026-10-26T13:00:00Z", "outcomes": [
                {"name": "Over", "description": "CeeDee Lamb", "price": 1.87, "point": 74.5},
                {"name": "Under", "description": "CeeDee Lamb", "price": 1.95, "point": 74.5},
                {"name": "Over", "description": "Nobody Known", "price": 1.9, "point": 9.5}]},
            {"key": "h2h", "outcomes": [{"name": "Dallas Cowboys", "price": 2.6}]}]},
        {"key": "skybet", "markets": [
            {"key": "player_pass_yds", "last_update": "2026-10-26T12:00:00Z", "outcomes": [
                {"name": "Over", "description": "Dak Prescott", "price": 1.9, "point": 249.5}]}]},
    ]}


@pytest.fixture
def lake(tmp_path):
    d = tmp_path / "lake"
    d.mkdir()
    # the same fixture in an earlier season must not duplicate or capture the quotes
    pd.DataFrame({"game_id": ["2025_05_DAL_PHI", "2026_07_DAL_PHI"], "season": [2025, 2026],
                  "week": [5, 7], "home_team": ["PHI", "PHI"], "away_team": ["DAL", "DAL"],
                  "kickoff_utc": ["2025-10-06T00:15:00Z", "2026-10-27T00:15:00Z"]}).to_parquet(
        d / "games.parquet")
    pd.DataFrame({"team_abbr": ["PHI", "DAL"],
                  "team_name": ["Philadelphia Eagles", "Dallas Cowboys"]}).to_parquet(
        d / "teams.parquet")
    pd.DataFrame({"season": 2026, "week": 7, "team": ["DAL", "DAL"],
                  "player_id": ["00-0035000", "00-0033077"],
                  "full_name": ["CeeDee Lamb", "Dak Prescott"]}).to_parquet(
        d / "membership.parquet")
    return d


def test_parse_classifies_uk_only_for_uk_requests():
    us = oa.parse_event_odds(PAYLOAD, observed_at="2026-10-26T14:00:00Z", request_region="us")
    assert len(us) == 4  # h2h ignored
    assert {r["availability"] for r in us} == {"research_reference"}
    uk = oa.parse_event_odds(PAYLOAD, observed_at="2026-10-26T14:00:00Z", request_region="uk")
    by_book = {r["bookmaker"]: r["availability"] for r in uk}
    assert by_book == {"draftkings": "research_reference", "skybet": "verified_uk"}


def test_link_and_store(tmp_path, monkeypatch, lake):
    monkeypatch.setattr(rawstore, "DATA_HOME", tmp_path)
    con = db.connect(tmp_path / "t.sqlite3")
    rows = oa.parse_event_odds(PAYLOAD, observed_at="2026-10-26T14:00:00Z", request_region="us")
    out = oa.link_and_store(con, rows, None, lake_dir=lake)
    assert out["quotes"] == 4 and out["stored"] == 4 and out["game_ids"] == ["2026_07_DAL_PHI"]
    assert out["linked_players"] == 3 and out["unlinked_names"] == ["Nobody Known"]
    q = oa.quotes_frame(con)
    lamb = q[(q.player_name_raw == "CeeDee Lamb") & (q.selection == "over")].iloc[0]
    assert lamb.player_id == "00-0035000" and lamb.line == 74.5 and lamb.market == "rec_yds"
    # storing the same snapshot again adds nothing
    assert oa.link_and_store(con, rows, None, lake_dir=lake)["stored"] == 0


def test_dotenv_loads_without_overriding(tmp_path, monkeypatch):
    f = tmp_path / ".env"
    f.write_text("# comment\nNPL_TEST_A=one\nNPL_TEST_B='two'\n")
    monkeypatch.setenv("NPL_TEST_A", "already")
    monkeypatch.delenv("NPL_TEST_B", raising=False)
    cli.load_dotenv(f)
    import os
    assert os.environ["NPL_TEST_A"] == "already" and os.environ["NPL_TEST_B"] == "two"
    monkeypatch.delenv("NPL_TEST_B")


def test_key_is_redacted_from_urls():
    assert "abc123" not in redact("https://x/v4/sports?apiKey=abc123&regions=uk")


def test_relink_appends_links_and_leaves_quotes_untouched(tmp_path, monkeypatch, lake):
    import json

    monkeypatch.setattr(rawstore, "DATA_HOME", tmp_path)
    con = db.connect(tmp_path / "t.sqlite3")
    raw = rawstore.store(con, source=oa.SOURCE, dataset="event_odds", part="evt1:us:t",
                         url="https://x/v4/sports/s/events/evt1/odds?regions=us",
                         content=json.dumps(PAYLOAD).encode(), filename="event_odds.json")
    rows = oa.parse_event_odds(PAYLOAD, observed_at="2026-10-26T14:00:00Z", request_region="us")
    unlinked = pd.DataFrame(rows).assign(game_id=None, player_id=None)
    assert oa.store_quotes(con, unlinked, raw.id) == 4  # as stored by the old linking bug
    with pytest.raises(Exception, match="append-only"):
        con.execute("UPDATE quotes SET player_id = 'x'")
    out = oa.relink_raw(con, raw.id, lake_dir=lake)
    assert out["matched_rows"] == 4 and out["links_added"] == 4
    q = oa.quotes_frame(con)
    assert len(q) == 4 and q.game_id.eq("2026_07_DAL_PHI").all()
    assert q.player_id.notna().sum() == 3
    raw_rows = con.execute("SELECT COUNT(*) FROM quotes WHERE player_id IS NULL").fetchone()[0]
    assert raw_rows == 4  # the stored rows themselves never change
    assert oa.relink_raw(con, raw.id, lake_dir=lake)["links_added"] == 0


def test_nba_market_keys_parse_with_the_nba_map():
    nba = {"id": "e2", "commence_time": "2026-10-29T02:30:00Z", "home_team": "LA Clippers",
           "away_team": "Los Angeles Lakers", "bookmakers": [{"key": "fanduel", "markets": [
               {"key": "player_points", "last_update": "2026-10-28T19:00:00Z", "outcomes": [
                   {"name": "Over", "description": "Luka Doncic", "price": 1.87, "point": 31.5},
                   {"name": "Under", "description": "Luka Doncic", "price": 1.95,
                    "point": 31.5}]}]}]}
    assert oa.parse_event_odds(nba, observed_at="t", request_region="us") == []  # NFL map
    rows = oa.parse_event_odds(nba, observed_at="t", request_region="us",
                               market_map=oa.NBA_MARKET_MAP)
    assert {r["market"] for r in rows} == {"pts"} and len(rows) == 2
    assert oa.core_markets("nba") == ["player_points", "player_rebounds", "player_assists",
                                      "player_threes", "player_points_rebounds_assists"]
