"""D-046: no automated access to ESPN, NBA.com or NFL.com (util.BLOCKED_HOSTS, redirects
included). Box scores and injury reports come from SportsDataverse only; photos and logos are
never downloaded, and the thumbnails cached before then are still used."""

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
    ("https://www.espn.co.uk/nba/", True), ("https://www.espnfc.com/x", True),
    ("https://www.espncricinfo.com/x", True), ("https://www.espn.com.au/nba/", True),
    ("https://www.espn.in/x", True), ("https://www.espn.com.br/nba/", True),
    ("https://www.espndeportes.com/x", True), ("http://ESPN.CO.UK./x", True),
    ("https://stats.nba.com/stats/boxscore", True),
    ("https://ak-static.cms.nba.com/referee/injury/Injury-Report.pdf", True),
    ("https://cdn.nba.net/x.json", True), ("https://www.wnba.com/x", True),
    ("https://static.www.nfl.com/image/private/f_auto,q_auto/league/x", True),
    ("https://www.nfl.com/injuries/", True),
    ("https://github.com/sportsdataverse/sportsdataverse-data/releases/download/x", False),
    ("https://api.the-odds-api.com/v4/sports", False), ("https://notespn.com/x", False),
    ("https://espn.co.uk.example.org/x", False), ("https://notnfl.com/x", False)])
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


class _Resp:
    def __init__(self, url, code=200, location=None, history=()):
        self.url, self.status_code, self.content = url, code, b"ok"
        self.headers = {"Location": location} if location else {}
        self.history = list(history)


class _Redirecting:
    """A session answering from a fixed map url -> response, recording what was asked."""

    def __init__(self, answers):
        self.answers, self.asked = answers, []

    def get(self, url, **kw):
        self.asked.append((url, kw.get("allow_redirects")))
        return self.answers[url]


def test_http_get_never_follows_a_redirect_to_a_blocked_host():
    s = _Redirecting({"https://example.org/photo": _Resp(
        "https://example.org/photo", 302, "https://a.espncdn.com/i/headshots/x.png")})
    with pytest.raises(FetchError, match="redirects to https://a.espncdn.com"):
        http_get("https://example.org/photo", session=s, max_tries=1)
    assert s.asked == [("https://example.org/photo", False)]  # the ESPN hop is never requested


def test_http_get_follows_allowed_redirects_by_hand():
    s = _Redirecting({
        "https://github.com/x/releases/download/t/f.parquet": _Resp(
            "https://github.com/x/releases/download/t/f.parquet", 302,
            "https://objects.githubusercontent.com/f.parquet?sig=1"),
        "https://objects.githubusercontent.com/f.parquet?sig=1": _Resp(
            "https://objects.githubusercontent.com/f.parquet?sig=1", 307, "/g.parquet"),
        "https://objects.githubusercontent.com/g.parquet": _Resp(
            "https://objects.githubusercontent.com/g.parquet")})
    r = http_get("https://github.com/x/releases/download/t/f.parquet", session=s)
    assert r.content == b"ok" and [h.status_code for h in r.history] == [302, 307]
    assert [a for _, a in s.asked] == [False] * 3  # redirects are never left to the session
    assert s.asked[-1][0] == "https://objects.githubusercontent.com/g.parquet"


@pytest.mark.parametrize("resp", [
    _Resp("https://www.nfl.com/players/x/"),  # a session that followed a redirect itself
    _Resp("https://example.org/x", history=[_Resp("https://cdn.nba.net/x", 301)])])
def test_http_get_refuses_a_response_that_passed_through_a_blocked_host(resp):
    with pytest.raises(FetchError, match="blocked host"):
        http_get("https://example.org/x", session=_Redirecting({"https://example.org/x": resp}))


def test_no_blocked_address_in_the_code():
    hits = [f"{p.relative_to(SRC)}: {m.group(0)}" for p in SRC.rglob("*.py")
            for m in re.finditer(r"https?://[\w.-]*(espn|nba\.com|nba\.net|wnba\.com|nfl\.com)"
                                 r"[^\s\"']*", p.read_text())]
    assert hits == []


@pytest.mark.parametrize("sport", ["nba", "nfl"])
def test_player_photos_use_the_cache_only(tmp_path, monkeypatch, sport):
    """NBA photos came from ESPN, NFL photos from NFL.com (static.www.nfl.com): neither is
    downloaded any more (the offline fixture fails any request)."""
    monkeypatch.setattr(headshots, "DIR", tmp_path / "headshots")
    d = tmp_path / "headshots" / sport
    d.mkdir(parents=True)
    (d / "1.webp").write_bytes(b"cached")
    out = headshots.fetch(sport, {"1", "2"})
    assert out == {"cached": 1, "new": 0, "missing": 1}
    assert set(headshots.data_uris(sport, {"1", "2"})) == {"1"}  # player 2 shows initials
    assert sorted(f.name for f in d.iterdir()) == ["1.webp"]  # nothing written either


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
