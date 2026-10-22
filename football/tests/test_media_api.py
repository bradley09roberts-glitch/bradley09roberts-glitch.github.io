"""GET /api/teams/{team_key}/media and the pictures under /media/teams/."""

import json
import xml.etree.ElementTree as ET

import pytest
from fastapi import APIRouter, FastAPI
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session

from footy.api.media_routes import make_media_router
from footy.media import service
from footy.models import Team

PNG = b"\x89PNG\r\n\x1a\n" + b"\0" * 16
FACTS = {
    "teams": {
        "eng-arsenal": {
            "qid": "Q9617",
            "wikidata_label": "Arsenal F.C.",
            "crest_file": "Arsenal FC.svg",
            "venue": {"qid": "Q8681", "name": "Emirates Stadium", "image_file": "Emirates.jpg"},
            "colours": [
                {"qid": "Q23444", "name": "white", "hex": "#FFFFFF"},
                {"qid": "Q3142", "name": "red", "hex": "#EF0107"},
            ],
        },
        "eng-man-city": {
            "qid": "Q50602",
            "wikidata_label": "Manchester City F.C.",
            "crest_file": None,
            "venue": {"qid": "Q1", "name": "City of Manchester Stadium", "image_file": None},
            "colours": [{"qid": "Q1088", "name": "sky blue", "hex": "#6CABDD"}],
        },
    }
}


@pytest.fixture
def media(client, migrated_engine, settings, tmp_path, monkeypatch):
    with Session(migrated_engine) as s:
        for key, name in [
            ("eng-arsenal", "Arsenal"),
            ("eng-man-city", "Man City"),
            ("eng-nobody", "Nobody Town"),
        ]:
            s.add(Team(team_key=key, name=name, country="ENG"))
        s.commit()
    path = tmp_path / "team_media.json"
    path.write_text(json.dumps(FACTS))
    monkeypatch.setattr(service, "FACTS_PATH", path)
    return service.media_dir(settings)


def add_crest(media_dir, licence="Public domain"):
    team = media_dir / "eng-arsenal"
    team.mkdir(parents=True)
    (team / "crest.png").write_bytes(PNG)
    manifest = {
        "team_key": "eng-arsenal",
        "files": {
            "crest": {
                "file": "crest.png",
                "commons_file": "Arsenal FC.svg",
                "source_url": "https://commons.wikimedia.org/wiki/File:Arsenal_FC.svg",
                "licence": licence,
                "attribution": "Arsenal FC.svg, Public domain, via Wikimedia Commons",
            }
        },
        "refused": [],
    }
    (team / "manifest.json").write_text(json.dumps(manifest))


def test_generated_badge_until_a_free_crest_is_downloaded(client, media):
    body = client.get("/api/teams/eng-arsenal/media").json()
    assert body["crest"]["kind"] == "generated"
    assert body["crest"]["url"] == "/media/teams/eng-arsenal/badge.svg"
    assert "not the club's crest" in body["crest"]["attribution"]
    assert body["crest"]["source_url"] == "https://www.wikidata.org/wiki/Q9617"
    # Red is the badge's background, so it comes first.
    assert body["colours"] == [
        {"name": "red", "hex": "#EF0107"},
        {"name": "white", "hex": "#FFFFFF"},
    ]
    assert body["venue"] == {
        "name": "Emirates Stadium",
        "url": None,  # no photo downloaded yet
        "licence": None,
        "attribution": None,
        "source_url": None,
    }

    add_crest(media)
    body = client.get("/api/teams/eng-arsenal/media").json()
    assert body["crest"] == {
        "url": "/media/teams/eng-arsenal/crest.png",
        "kind": "crest",
        "licence": "Public domain",
        "attribution": "Arsenal FC.svg, Public domain, via Wikimedia Commons",
        "source_url": "https://commons.wikimedia.org/wiki/File:Arsenal_FC.svg",
    }
    resp = client.get(body["crest"]["url"])
    assert resp.status_code == 200
    assert resp.content == PNG
    assert resp.headers["content-type"] == "image/png"


def test_a_crest_with_a_non_free_licence_is_never_shown(client, media):
    add_crest(media, licence="Fair use")
    body = client.get("/api/teams/eng-arsenal/media").json()
    assert body["crest"]["kind"] == "generated"


def test_team_without_facts_gets_a_neutral_badge(client, media):
    body = client.get("/api/teams/eng-nobody/media").json()
    assert body["crest"]["kind"] == "generated"
    assert body["crest"]["source_url"] is None
    assert body["venue"] is None
    assert body["colours"] == []
    assert client.get("/api/teams/eng-unknown/media").status_code == 404


def test_badge_svg(client, media):
    resp = client.get("/media/teams/eng-man-city/badge.svg")
    assert resp.status_code == 200
    assert resp.headers["content-type"].startswith("image/svg+xml")
    assert "default-src 'none'" in resp.headers["content-security-policy"]
    root = ET.fromstring(resp.text)
    assert root.get("data-generated") == "footy"
    assert "#6CABDD" in resp.text
    assert client.get("/media/teams/eng-unknown/badge.svg").status_code == 404


@pytest.mark.parametrize(
    "path",
    [
        "/media/teams/eng-arsenal/manifest.json",
        "/media/teams/eng-arsenal/crest.svg",
        "/media/teams/eng-arsenal/venue.jpg",  # not downloaded
        "/media/teams/eng-arsenal/..%2Fsecret.png",
        "/media/teams/..%2F..%2Fetc/crest.png",
        "/media/teams/ENG-ARSENAL/crest.png",
    ],
)
def test_only_downloaded_pictures_are_served(client, media, path):
    add_crest(media)
    assert client.get(path).status_code == 404


def test_media_route_is_not_shadowed_by_other_team_routes(client, media, settings, migrated_engine):
    # In the real app: whatever else answers /api/teams/..., the media route answers here.
    assert "crest" in client.get("/api/teams/eng-arsenal/media").json()
    assert "crest" not in client.get("/api/teams/eng-arsenal").json()
    # And with a team route registered either side of it, each keeps its own paths.
    for media_first in (True, False):
        app = FastAPI()
        other = APIRouter()

        @other.get("/api/teams/{team_key}")
        def team(team_key: str) -> dict:
            return {"team": team_key}

        routers = [make_media_router(lambda: migrated_engine, settings), other]
        for router in routers if media_first else routers[::-1]:
            app.include_router(router)
        test = TestClient(app)
        assert test.get("/api/teams/eng-arsenal").json() == {"team": "eng-arsenal"}
        assert test.get("/api/teams/eng-arsenal/media").json()["team_key"] == "eng-arsenal"
