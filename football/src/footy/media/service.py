"""What GET /api/teams/{team_key}/media returns: the club's crest if a freely licensed one has
been downloaded, otherwise our generated badge; the home venue with its photo when one has
been downloaded; and the club's official colours. Nothing here makes a network request.
"""

import json
from functools import lru_cache
from pathlib import Path

from footy.config import Settings
from footy.media.badge import contrast, pick_colours, valid_hex
from footy.media.commons import licence_is_free, read_manifest
from footy.media.facts import MEDIA_JSON, WIKIDATA_ITEM_URL

MEDIA_URL = "/media/teams"
FACTS_PATH = MEDIA_JSON  # tests point this elsewhere


def media_dir(settings: Settings) -> Path:
    """Where the downloader saves pictures: data/media/teams/<team_key>/."""
    return settings.data_dir / "media" / "teams"


@lru_cache(maxsize=4)
def _facts_cached(path: str, mtime_ns: int) -> dict[str, dict]:
    return json.loads(Path(path).read_text(encoding="utf-8")).get("teams", {})


def load_facts(path: Path | None = None) -> dict[str, dict]:
    """reference/team_media.json's teams, re-read whenever the file changes."""
    path = path or FACTS_PATH
    if not path.exists():
        return {}
    return _facts_cached(str(path), path.stat().st_mtime_ns)


def ordered_colours(team_key: str, colours: list[dict]) -> list[dict]:
    """{name, hex} per official colour, the badge's background first, then its band, then
    the rest by name; colours without a hex value last."""
    with_hex = [c for c in colours if valid_hex(c.get("hex"))]
    if not with_hex:
        return [{"name": c.get("name"), "hex": None} for c in colours]
    background, band = pick_colours([c["hex"] for c in with_hex], team_key)

    def rank(c: dict) -> tuple:
        h = valid_hex(c.get("hex"))
        if h is None:
            return (3, c.get("name") or "")
        return ({background: 0, band: 1}.get(h, 2), -contrast(background, h), c.get("name") or "")

    return [
        {"name": c.get("name"), "hex": valid_hex(c.get("hex"))} for c in sorted(colours, key=rank)
    ]


def _picture(team_dir: Path, entry: dict | None) -> dict | None:
    """A downloaded picture's manifest entry, if its file is there and its licence free."""
    if not entry or not (team_dir / entry.get("file", "")).is_file():
        return None
    if not licence_is_free(entry.get("licence")):
        return None
    return entry


def team_media(team_key: str, settings: Settings, facts_path: Path | None = None) -> dict:
    facts = load_facts(facts_path).get(team_key) or {}
    team_dir = media_dir(settings) / team_key
    files = read_manifest(team_dir).get("files", {}) if team_dir.is_dir() else {}
    qid = facts.get("qid")
    wikidata_url = WIKIDATA_ITEM_URL.format(qid=qid) if qid else None
    colours = ordered_colours(team_key, facts.get("colours") or [])

    crest_entry = _picture(team_dir, files.get("crest"))
    if crest_entry is not None:
        crest = {
            "url": f"{MEDIA_URL}/{team_key}/{crest_entry['file']}",
            "kind": "crest",
            "licence": crest_entry.get("licence"),
            "attribution": crest_entry.get("attribution"),
            "source_url": crest_entry.get("source_url"),
        }
    else:
        has_colours = any(c["hex"] for c in colours)
        crest = {
            "url": f"{MEDIA_URL}/{team_key}/badge.svg",
            "kind": "generated",
            "licence": None,
            "attribution": (
                "Generated badge, not the club's crest"
                + (": colours from Wikidata (CC0)" if has_colours else "")
            ),
            "source_url": wikidata_url if has_colours else None,
        }

    venue = None
    venue_facts = facts.get("venue") or {}
    photo = _picture(team_dir, files.get("venue"))
    if venue_facts.get("name") or photo is not None:
        venue = {
            "name": venue_facts.get("name"),
            "url": f"{MEDIA_URL}/{team_key}/{photo['file']}" if photo else None,
            "licence": photo.get("licence") if photo else None,
            "attribution": photo.get("attribution") if photo else None,
            "source_url": photo.get("source_url") if photo else None,
        }
    return {
        "team_key": team_key,
        "wikidata_url": wikidata_url,
        "crest": crest,
        "venue": venue,
        "colours": colours,
    }
