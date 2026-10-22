"""The short media summary sent with every team the other endpoints name
(footy.media.service.media_summary): fixtures and past cards, the match page's team objects and
recent results, the teams list, the profile and the match log.

SYNTHETIC TEST DATA: the fictional clubs of test_teams, made-up Wikidata facts and a made-up
crest (a few bytes of PNG header). Nothing here is a real club's colours, crest or result.
"""

import json
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from footy.media import service
from footy.media.badge import short_name
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.models import Team
from footy.publish import FixtureIn, publish
from test_teams import build_world, write_primary
from test_teams_api import PREDICTION_KEYS

PNG = b"\x89PNG\r\n\x1a\n" + b"\0" * 16
FACTS = {
    "teams": {
        "eng-testtown": {
            "qid": "Q1",
            "crest_file": "Testtown crest.svg",
            "venue": {"name": "Test Park", "image_file": None},
            # White and claret: the claret (more saturated) is the badge's background.
            "colours": [
                {"name": "white", "hex": "#FFFFFF"},
                {"name": "claret", "hex": "#7A263A"},
                {"name": "no hex", "hex": None},
            ],
        },
        "eng-sampleton": {"qid": "Q2", "crest_file": None, "venue": None, "colours": []},
    }
}


@pytest.fixture
def world(migrated_engine, settings, tmp_path, monkeypatch):
    write_primary(tmp_path, monkeypatch)
    path = tmp_path / "team_media.json"
    path.write_text(json.dumps(FACTS))
    monkeypatch.setattr(service, "FACTS_PATH", path)
    w = build_world(migrated_engine, tmp_path)
    # A fixture two days ahead of the real clock, so /api/fixtures lists it.
    day = (datetime.now(UTC) + timedelta(days=2)).date()
    kickoff = datetime(day.year, day.month, day.day, 15, tzinfo=UTC)
    mp = markets_from_grid(poisson_grid([1.3], [1.2]), [1.3], [1.2])
    (w["upcoming"],) = publish(
        migrated_engine,
        [FixtureIn("E0", "2627", day, kickoff, "eng-testtown", "eng-mockford")],
        mp,
        model_name="base_m",
        model_version="v-test",
        training_cutoff_utc=datetime.now(UTC) - timedelta(hours=1),
        data_freshness={},
        now=datetime.now(UTC),
        records_dir=tmp_path / "records",
    )
    return w


def add_crest(settings, team="eng-testtown", licence="CC BY-SA 4.0", commons_file=None):
    folder = service.media_dir(settings) / team
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "crest.png").write_bytes(PNG)
    entry = {
        "file": "crest.png",
        "commons_file": commons_file or "Testtown crest.svg",
        "source_url": "https://commons.wikimedia.org/wiki/File:Testtown_crest.svg",
        "licence": licence,
        "attribution": "Testtown crest.svg by A. Synthetic, CC BY-SA 4.0, via Wikimedia Commons",
    }
    (folder / "manifest.json").write_text(json.dumps({"team_key": team, "files": {"crest": entry}}))


TESTTOWN = {"crest_url": None, "badge": {"colours": ["#7A263A", "#FFFFFF"], "short": "TES"}}
SAMPLETON = {"crest_url": None, "badge": {"colours": [], "short": "SAM"}}


def test_cards_carry_both_teams_media(client, world, settings):
    cards = [
        c for comp in client.get("/api/fixtures").json()["competitions"] for c in comp["fixtures"]
    ]
    card = next(c for c in cards if c["id"] == world["upcoming"])
    assert card["home_media"] == TESTTOWN
    # Mockford has no facts: no colours are made up, the website draws its neutral badge.
    assert card["away_media"] == {"crest_url": None, "badge": {"colours": [], "short": "MOC"}}
    keys = list(card)
    assert keys.index("home_media") == keys.index("away_name") + 1
    past = client.get("/api/past").json()["matches"]
    assert past and all({"home_media", "away_media"} <= set(c) for c in past)
    m6 = next(c for c in past if c["id"] == world["pred"]["m6"])
    assert (m6["home_media"], m6["away_media"]) == (SAMPLETON, TESTTOWN)

    # Once a freely licensed crest is downloaded, its URL is sent, and it is served.
    add_crest(settings)
    card = next(
        c
        for comp in client.get("/api/fixtures").json()["competitions"]
        for c in comp["fixtures"]
        if c["id"] == world["upcoming"]
    )
    assert card["home_media"]["crest_url"] == "/media/teams/eng-testtown/crest.png"
    assert client.get(card["home_media"]["crest_url"]).content == PNG


def test_match_page_team_objects_and_recent_results(client, world):
    d = client.get(f"/api/predictions/{world['pred']['m6']}").json()
    assert set(d) == PREDICTION_KEYS | {"teams"}  # no new top-level key
    home, away = d["teams"]["home"], d["teams"]["away"]
    assert list(home)[:3] == ["team", "name", "media"]
    assert (home["media"], away["media"]) == (SAMPLETON, TESTTOWN)
    recent = d["recent"]["away"]  # Testtown's results before the match
    names = {"eng-sampleton": "Sampleton", "eng-mockford": "Mockford", "nld-voorbeeld": "Voorbeeld"}
    assert {r["opponent_team"] for r in recent} == set(names)
    for r in recent:
        assert r["opponent"] == names[r["opponent_team"]]
        assert r["opponent_media"]["badge"]["short"] == short_name(r["opponent"])
        if r["opponent_team"] == "eng-sampleton":
            assert r["opponent_media"] == SAMPLETON


def test_teams_list_profile_and_match_log(client, world):
    body = client.get("/api/teams").json()
    assert set(body["media"]) == {t["key"] for t in body["teams"]}
    assert body["media"]["eng-testtown"] == TESTTOWN
    filtered = client.get("/api/teams?competition=SC0").json()
    assert set(filtered["media"]) == {"sco-fakeburgh", "sco-mockhill"}

    p = client.get("/api/teams/eng-testtown").json()
    assert p["team"]["media"] == TESTTOWN and list(p["team"])[:3] == ["key", "name", "media"]
    assert p["upcoming"] and all("opponent_media" in f for f in p["upcoming"])
    assert {f["opponent"] for f in p["upcoming"]} >= {"nld-voorbeeld"}

    log = client.get("/api/teams/eng-testtown/matches?limit=3").json()
    assert set(log["media"]) == {"eng-testtown", *(m["opponent"] for m in log["matches"])}
    assert log["media"]["eng-testtown"] == TESTTOWN


def test_crest_url_only_for_a_free_crest_of_the_file_the_facts_name(settings, world):
    assert service.crest_url("eng-testtown", settings) is None
    add_crest(settings, licence="Fair use")
    assert service.crest_url("eng-testtown", settings) is None
    add_crest(settings, commons_file="An older crest.svg")
    assert service.crest_url("eng-testtown", settings) is None
    add_crest(settings)
    assert service.crest_url("eng-testtown", settings) == "/media/teams/eng-testtown/crest.png"
    # An unreadable manifest shows no crest rather than failing the page.
    (service.media_dir(settings) / "eng-testtown" / "manifest.json").write_text("{not json")
    assert service.crest_url("eng-testtown", settings) is None
    assert service.crest_url("../eng-testtown", settings) is None


def test_a_team_folder_linked_outside_the_media_folder_gives_no_crest(settings, world, tmp_path):
    outside = tmp_path / "outside"
    add_crest(settings)
    folder = service.media_dir(settings) / "eng-testtown"
    folder.rename(outside)
    folder.symlink_to(outside, target_is_directory=True)
    assert service.crest_url("eng-testtown", settings) is None


def test_summaries_are_cheap_on_a_long_list(settings, migrated_engine, world):
    """One stat call per team without a manifest: 2,000 summaries well under a second."""
    import time

    with Session(migrated_engine) as s:
        names = dict(s.execute(select(Team.team_key, Team.name)).all())
    many = {f"{k}-{i}": n for i in range(300) for k, n in names.items()}
    started = time.perf_counter()
    out = service.media_summaries(many, settings)
    assert len(out) == len(many) and time.perf_counter() - started < 1.0


def test_short_names_come_from_one_place():
    # The generated badge and the API's badge.short are the same function.
    assert short_name("RB Leipzig") == "RBL"
    assert short_name("PSV Eindhoven") == "PSV"
    assert short_name("AZ Alkmaar") == "AZA"
    assert short_name("CSKA Sofia") == "CSK"
    assert short_name("QPR") == "QPR"
    assert short_name("Iberia 1999") == "IBE"
    assert short_name("Schalke 04") == "S04"
    assert short_name("AC Milan") == "MIL"
    assert short_name("Inter Club d'Escaldes") == "IES"
