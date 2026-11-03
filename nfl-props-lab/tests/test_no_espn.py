"""D-046: no automated access to ESPN (or NBA.com). Box scores and injury reports come from
SportsDataverse only; photos and logos are never downloaded from ESPN's image server, and the
thumbnails cached before then are still used."""

import argparse
import json
import re
from datetime import date
from pathlib import Path

import pandas as pd
import pytest
import requests

from npl import cli, db, headshots
from npl.util import FetchError, blocked_host, http_get

SRC = Path(__file__).resolve().parents[1] / "src" / "npl"


@pytest.fixture(autouse=True)
def offline(monkeypatch):
    def refuse(*a, **k):
        raise AssertionError("a test tried to reach the network")

    monkeypatch.setattr(requests, "get", refuse)
    monkeypatch.setattr(requests.Session, "get", refuse)


@pytest.mark.parametrize("url,blocked", [
    ("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event=1", True),
    ("https://a.espncdn.com/i/teamlogos/nba/500/bos.png", True),
    ("https://www.espn.com/nba/", True), ("https://espn.com", True),
    ("https://stats.nba.com/stats/boxscore", True),
    ("https://ak-static.cms.nba.com/referee/injury/Injury-Report.pdf", True),
    ("https://github.com/sportsdataverse/sportsdataverse-data/releases/download/x", False),
    ("https://static.www.nfl.com/image/private/f_auto,q_auto/league/x", False),
    ("https://api.the-odds-api.com/v4/sports", False), ("https://notespn.com/x", False)])
def test_blocked_hosts(url, blocked):
    assert blocked_host(url) is blocked


def test_http_get_refuses_a_blocked_host_before_any_request():
    class Session:
        calls = 0

        def get(self, *a, **k):
            Session.calls += 1

    with pytest.raises(FetchError, match="blocked host"):
        http_get("https://a.espncdn.com/combiner/i?img=x.png", session=Session())
    assert Session.calls == 0


def test_no_espn_or_nba_com_address_in_the_code():
    hits = [f"{p.relative_to(SRC)}: {m.group(0)}" for p in SRC.rglob("*.py")
            for m in re.finditer(r"https?://[\w.-]*(espn|espncdn|nba\.com)[^\s\"']*",
                                 p.read_text())]
    assert hits == []


def test_nba_photos_use_the_cache_only(tmp_path, monkeypatch):
    monkeypatch.setattr(headshots, "DIR", tmp_path / "headshots")
    d = tmp_path / "headshots" / "nba"
    d.mkdir(parents=True)
    (d / "1.webp").write_bytes(b"cached")
    out = headshots.fetch("nba", {"1", "2"})  # no request at all (offline fixture)
    assert out == {"cached": 1, "new": 0, "missing": 1}
    assert set(headshots.data_uris("nba", {"1", "2"})) == {"1"}  # player 2 shows initials


def test_espn_logo_addresses_are_never_fetched(tmp_path, monkeypatch):
    monkeypatch.setattr(headshots, "DIR", tmp_path / "headshots")
    monkeypatch.setattr(headshots, "logo_urls", lambda sport: {
        "BOS": "https://a.espncdn.com/i/teamlogos/nba/500/bos.png",
        "LAL": "https://a.espncdn.com/i/teamlogos/nba/500/lal.png"})
    d = tmp_path / "logos" / "nba"
    d.mkdir(parents=True)
    for f in ("BOS.webp", "BOS.dark.webp", "_league.webp"):
        (d / f).write_bytes(b"cached")
    assert headshots.fetch_logos("nba") == {"cached": 2, "new": 0, "failed": 0, "not_fetched": 2}
    assert set(headshots.logo_uris("nba")) == {"BOS", "_league"}  # LAL shows its abbreviation


def test_pipeline_modules_no_longer_use_the_espn_feeds():
    from npl.nba import canon, live

    assert not hasattr(live, "espn") and not hasattr(canon, "espn")
    assert not hasattr(canon, "sdv_current_game_ids")
    from npl.nba import espn

    assert not hasattr(espn, "fetch") and not hasattr(espn, "fetch_injuries")
    assert espn.__doc__.startswith("UNUSED since D-046")


def test_injury_snapshot_records_which_snapshot_and_when():
    from npl.nba import live

    inj = pd.DataFrame({"as_of_date": [date(2026, 11, 1), date(2026, 11, 2)]})
    meta = {"dataset": "nba_injuries", "sha256": "abc", "retrieved_at": "2026-11-03T08:00:00Z",
            "upstream_last_modified": "Mon, 02 Nov 2026 18:40:12 GMT"}
    snap = live.injury_snapshot(inj, pd.DataFrame({"as_of_date": ["2026-11-02"]}), meta)
    assert snap["source"] == "sportsdataverse" and snap["as_of_date"] == "2026-11-02"
    assert snap["snapshot_at"] == "2026-11-02T18:40:12Z" and snap["sha256"] == "abc"
    assert live.injury_note(snap) == (
        "Injury reports: SportsDataverse's daily snapshot of 2 Nov 2026 (published 18:40 UTC on "
        "2 Nov). Injury news after that snapshot is not included.")
    # an older day: the file's time belongs to the newest day, so no time is claimed
    old = live.injury_snapshot(inj, pd.DataFrame({"as_of_date": ["2026-11-01"]}), meta)
    assert old["snapshot_at"] is None
    assert live.injury_note(old) == ("Injury reports: SportsDataverse's daily snapshot of 1 Nov "
                                     "2026. Injury news after that snapshot is not included.")
    none = live.injury_snapshot(inj, pd.DataFrame({"as_of_date": []}), meta)
    assert none["as_of_date"] is None and "no SportsDataverse injury snapshot" in \
        live.injury_note(none)


def test_nba_update_is_sportsdataverse_only(tmp_path, monkeypatch, capsys):
    from npl.nba import canon, shots, source

    con = db.connect(tmp_path / "t.sqlite3")
    monkeypatch.setattr(db, "connect", lambda *a, **k: con)
    monkeypatch.setattr(source, "fetch", lambda con, **k: {"new": [], "failed": []})
    monkeypatch.setattr(canon, "build", lambda con: {"counts": {"games": 1}})
    monkeypatch.setattr(shots, "build", lambda con: {"shots": 0})
    cli.cmd_nba(argparse.Namespace(action="update", max_age_hours=0.5, force=False))
    out = json.loads(capsys.readouterr().out)
    assert out == {"new": [], "failed": [], "build": {"games": 1}, "shots": {"shots": 0}}
