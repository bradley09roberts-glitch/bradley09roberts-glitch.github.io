"""The team endpoints (/api/teams...) and the "teams" block of /api/predictions/{id}.

SYNTHETIC TEST DATA: the fictional clubs, results, records and planted prices of test_teams.
"""

import json

import pytest

from footy.snapshot import find_prices
from test_api_records import timestamps
from test_teams import PLANTED_PRICES, build_world, write_primary

# The keys /api/predictions/{id} sent before the "teams" block was added; they must not change.
PREDICTION_KEYS = {
    "id",
    "is_primary",
    "basis",
    "fixture_ref",
    "competition",
    "competition_name",
    "season",
    "match_date",
    "kickoff_utc",
    "home_team",
    "away_team",
    "home_name",
    "away_name",
    "edition",
    "record_type",
    "model_name",
    "model_version",
    "created_at_utc",
    "training_cutoff_utc",
    "probabilities",
    "selections",
    "data_freshness",
    "missing_fields",
    "notes",
    "settlement",
    "features",
    "market",
    "editions",
    "counterpart",
    "recent",
    "explanation",
}


@pytest.fixture
def world(migrated_engine, tmp_path, monkeypatch):
    write_primary(tmp_path, monkeypatch)
    return build_world(migrated_engine, tmp_path)


def keys(body: dict) -> list[str]:
    return [t["key"] for t in body["teams"]]


def test_teams_list(client, world):
    body = client.get("/api/teams").json()
    assert body["count"] == 6 and body["latest_match_held"] == "2026-08-22"
    assert keys(body)[0] == "sco-fakeburgh"
    tt = next(t for t in body["teams"] if t["key"] == "eng-testtown")
    assert set(tt) == {
        "key",
        "name",
        "country",
        "division",
        "competitions_this_season",
        "form_last_5",
        "matches",
        "matches_by_type",
        "last_match_date",
        "elo",
        "elo_rank",
    }
    assert (tt["name"], tt["country"], tt["form_last_5"], tt["matches"]) == (
        "Testtown",
        "ENG",
        "LDDWW",
        7,
    )
    sco = ["sco-fakeburgh", "sco-mockhill"]
    assert keys(client.get("/api/teams?competition=SC0").json()) == sco
    both = ["sco-fakeburgh", "eng-mockford", "sco-mockhill", "eng-sampleton", "eng-testtown"]
    assert keys(client.get("/api/teams?competition=E0,SC0").json()) == both
    assert keys(client.get("/api/teams?competition=E0&competition=SC0").json()) == both
    assert keys(client.get("/api/teams?q=MÖCKHILL").json()) == ["sco-mockhill"]
    assert keys(client.get("/api/teams?competition=UCL&q=voor").json()) == ["nld-voorbeeld"]
    assert client.get("/api/teams?competition=XYZ").json()["count"] == 0


def test_team_profile(client, world):
    r = client.get("/api/teams/eng-testtown")
    assert r.status_code == 200
    p = r.json()
    assert p["team"]["key"] == "eng-testtown" and p["team"]["division"]["code"] == "E0"
    assert p["summary"]["played"] == 7 and p["summary"]["left_out"] == 2
    assert set(p["by_type"]) == {"league", "europe", "cup"}
    assert p["by_type"]["league"]["points_per_game"] == 2.0
    assert p["form"]["last_10"] == "WDLDDWW"
    assert len(p["strength"]["series"]) == 7
    assert p["generated_at_utc"].endswith("+00:00")
    missing = client.get("/api/teams/eng-unknown")
    assert missing.status_code == 404 and missing.json() == {"detail": "no such team"}


def test_team_matches(client, world):
    ids = world["ids"]
    body = client.get("/api/teams/eng-testtown/matches").json()
    assert body["total"] == 9 and body["offset"] == 0 and body["limit"] == 100
    # Newest first; a flagged match is listed with its reason.
    assert body["matches"][0]["ref"] == f"matches:{ids['m6']}"
    assert body["matches"][0]["prediction"]["id"] == world["pred"]["m6"]
    flagged = {m["ref"]: m["excluded"] for m in body["matches"] if m["excluded"]}
    assert flagged == {
        f"matches:{ids['m4']}": "result_without_stats",
        f"euro_matches:{ids['u3']}": "awarded",
    }
    assert "fd_xg" in body["notes"]
    europe = client.get("/api/teams/eng-testtown/matches?type=europe").json()
    assert [m["competition"] for m in europe["matches"]] == ["UCL", "UECL", "UCL"]
    old = client.get("/api/teams/eng-testtown/matches?competition=E0&season=2526").json()
    assert old["total"] == 4 and {m["season"] for m in old["matches"]} == {"2526"}
    page = client.get("/api/teams/eng-testtown/matches?limit=2&offset=1").json()
    assert page["total"] == 9 and [m["ref"] for m in page["matches"]] == [
        m["ref"] for m in body["matches"][1:3]
    ]
    assert client.get("/api/teams/eng-testtown/matches?type=bogus").status_code == 422
    assert client.get("/api/teams/eng-testtown/matches?limit=0").status_code == 422
    assert client.get("/api/teams/eng-unknown/matches").status_code == 404


def test_prediction_detail_gains_a_teams_block(client, world):
    ids, pred = world["ids"], world["pred"]
    d = client.get(f"/api/predictions/{pred['m6']}").json()
    assert set(d) == PREDICTION_KEYS | {"teams"}
    t = d["teams"]
    assert set(t) == {"as_of", "note", "home", "away", "head_to_head", "match_stats"}
    assert t["as_of"] == "2026-08-22"
    assert t["home"]["team"] == "eng-sampleton" and t["away"]["team"] == "eng-testtown"
    assert t["away"]["form"]["by_type"] == {"league": "WDLW", "europe": "DD"}
    assert set(t["away"]["per_match"]) == {"all", "league", "europe"}
    assert t["match_stats"]["ref"] == f"matches:{ids['m6']}"
    # Unplayed: no statistics, and the earlier meetings in any competition.
    u = client.get(f"/api/predictions/{pred['u4']}").json()["teams"]
    assert u["match_stats"] is None and len(u["head_to_head"]["meetings"]) == 3


def test_no_price_and_every_timestamp_in_utc(client, world, tmp_path, monkeypatch):
    # The model + market record is the record of record, so its saved prices sit right behind
    # the records the team endpoints show.
    write_primary(tmp_path, monkeypatch, ("tilt_m", "base_m"))
    shown = client.get("/api/teams/eng-testtown/matches").json()["matches"][0]["prediction"]
    assert shown["basis"] == "model_and_market"
    urls = [
        "/api/teams",
        "/api/teams?competition=E0",
        *(f"/api/teams/{k}" for k in ("eng-testtown", "eng-sampleton", "sco-fakeburgh")),
        *(f"/api/teams/{k}/matches" for k in ("eng-testtown", "eng-sampleton", "nld-voorbeeld")),
        *(f"/api/predictions/{i}" for i in world["pred"].values()),
    ]
    seen = 0
    for url in urls:
        r = client.get(url)
        assert r.status_code == 200, url
        body = r.json()
        assert find_prices({"body": body}) == [], url
        for p in PLANTED_PRICES:
            assert str(p) not in r.text, url
        if url.startswith("/api/teams"):
            assert "B365" not in r.text and "planted_price" not in r.text, url
        for path, value in timestamps(body):
            seen += 1
            assert "T" in value and value.endswith("+00:00"), (url, path, value)
    assert seen > 20
    # The model + market record's own detail still withholds what it always did.
    tilt = client.get(f"/api/predictions/{world['pred']['tilt']}").text
    assert "planted_price" not in tilt and "B365H" not in tilt
    assert json.loads(tilt)["teams"]["home"]["team"] == "eng-sampleton"


def test_teams_list_reads_the_index_once(client, world, monkeypatch):
    """One data fingerprint per request: the list and its `latest_match_held` come from the
    same index build."""
    from footy import teams

    calls = []
    real = teams.data_fingerprint
    monkeypatch.setattr(teams, "data_fingerprint", lambda s: calls.append(1) or real(s))
    body = client.get("/api/teams").json()
    assert body["count"] == 6 and body["latest_match_held"] == "2026-08-22"
    assert len(calls) == 1
