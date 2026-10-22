"""What GET /api/teams/{team_key}/media returns: the club's crest if a freely licensed one has
been downloaded, otherwise our generated badge; the home venue with its photo when one has
been downloaded; and the club's official colours. Each picture comes with what its licence
asks to be shown (licence and its link, author, source, attribution text), and only while it
is of the file reference/team_media.json names now. Nothing here makes a network request.
"""

import csv
import json
import re
from collections.abc import Mapping
from functools import lru_cache
from pathlib import Path

from footy.config import Settings
from footy.media.badge import contrast, kit_shade, pick_colours, short_name, valid_hex
from footy.media.commons import licence_is_free, read_manifest
from footy.media.facts import MEDIA_JSON, WIKIDATA_ITEM_URL
from footy.media.mapping import ALIASES_JSON
from footy.reference import REFERENCE_DIR

MEDIA_URL = "/media/teams"
FACTS_PATH = MEDIA_JSON  # tests point this elsewhere
ALIASES_PATH = ALIASES_JSON  # likewise
# Reviewed by hand: a club's badge letters where the made-up ones read wrong ("MCT" for Man
# City), and the order to draw its Wikidata colours in where the most saturated one isn't the
# club's main colour (Real Madrid's purple, Tottenham's blue: both clubs play in white).
BADGES_PATH = REFERENCE_DIR / "team_badges.csv"
TEAM_KEY = re.compile(r"[a-z0-9][a-z0-9-]{0,63}")
# The only picture files ever served (footy.api.media_routes) or linked to.
PICTURE_FILE = re.compile(r"(crest|venue)\.(png|jpg|gif|webp)")


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


def load_wikidata_aliases(path: Path | None = None) -> dict[str, list[str]]:
    """reference/team_wikidata_aliases.json's teams (team key -> the mapped item's English
    aliases on Wikidata), re-read whenever the file changes; {} without the file."""
    path = path or ALIASES_PATH
    if not path.exists():
        return {}
    return _facts_cached(str(path), path.stat().st_mtime_ns)


@lru_cache(maxsize=4)
def _badges_cached(path: str, mtime_ns: int) -> dict[str, dict]:
    with Path(path).open(encoding="utf-8", newline="") as f:
        return {
            r["team_key"]: {
                "short": (r.get("short") or "").strip().upper() or None,
                "colours": [c.strip() for c in (r.get("colours") or "").split(";") if c.strip()],
            }
            for r in csv.DictReader(f)
        }


def load_badge_overrides(path: Path | None = None) -> dict[str, dict]:
    """reference/team_badges.csv: team key -> {short, colours (Wikidata colour names, in the
    order to draw them)}, re-read whenever the file changes; {} without the file."""
    path = path or BADGES_PATH
    if not path.exists():
        return {}
    return _badges_cached(str(path), path.stat().st_mtime_ns)


def ordered_colours(team_key: str, colours: list[dict]) -> list[dict]:
    """{name, hex, shade} per official colour: `hex` as Wikidata gives it, `shade` the one the
    site draws (badge.kit_shade: a kit shade for Wikidata's pure named colours). The badge's
    background first, then its band, then the rest by name; colours without a hex value last.
    The background and band are the reviewed order in reference/team_badges.csv where a club
    has one, otherwise picked from the shades (badge.pick_colours)."""
    rows = [
        {"name": c.get("name"), "hex": valid_hex(c.get("hex")), "shade": kit_shade(c)}
        for c in colours
    ]
    drawn = [r for r in rows if r["shade"]]
    if not drawn:
        return [{**r, "shade": None} for r in rows]
    order = [n.casefold() for n in load_badge_overrides().get(team_key, {}).get("colours", [])]
    named = [r for r in drawn if (r["name"] or "").casefold() in order]
    if len(named) >= min(2, len(drawn)):
        named.sort(key=lambda r: order.index((r["name"] or "").casefold()))
        background = named[0]["shade"]
        band = named[1]["shade"] if len(named) > 1 else None
    else:
        background, band = pick_colours([r["shade"] for r in drawn], team_key)

    def rank(r: dict) -> tuple:
        h = r["shade"]
        if h is None:
            return (3, 0.0, r["name"] or "")
        place = 0 if h == background else 1 if h == band else 2
        return (place, -contrast(background, h), r["name"] or "")

    return sorted(rows, key=rank)


def _picture(team_dir: Path, entry: dict | None, commons_file: str | None) -> dict | None:
    """A downloaded picture's manifest entry, if its file is there, its licence free, and it
    is of the Commons file the facts name now: a picture left from an earlier crest or venue,
    or from a Wikidata item since corrected, is not shown."""
    if not entry or not commons_file or entry.get("commons_file") != commons_file:
        return None
    if not (team_dir / entry.get("file", "")).is_file():
        return None
    if not licence_is_free(entry.get("licence")):
        return None
    return entry


def _credit(entry: dict | None) -> dict:
    """What must be shown with a picture: CC BY and BY-SA ask for the author, the source and
    the licence with a link to it (all None when there is no picture)."""
    entry = entry or {}
    return {
        "licence": entry.get("licence"),
        "licence_url": entry.get("licence_url"),
        "author": entry.get("author"),
        "attribution": entry.get("attribution"),
        "source_url": entry.get("source_url"),
    }


def team_media(team_key: str, settings: Settings, facts_path: Path | None = None) -> dict:
    facts = load_facts(facts_path).get(team_key) or {}
    team_dir = media_dir(settings) / team_key
    files = read_manifest(team_dir).get("files", {}) if team_dir.is_dir() else {}
    qid = facts.get("qid")
    wikidata_url = WIKIDATA_ITEM_URL.format(qid=qid) if qid else None
    colours = ordered_colours(team_key, facts.get("colours") or [])

    crest_entry = _picture(team_dir, files.get("crest"), facts.get("crest_file"))
    if crest_entry is not None:
        crest = {
            "url": f"{MEDIA_URL}/{team_key}/{crest_entry['file']}",
            "kind": "crest",
            **_credit(crest_entry),
        }
    else:
        has_colours = any(c["hex"] for c in colours)
        crest = {
            "url": f"{MEDIA_URL}/{team_key}/badge.svg",
            "kind": "generated",
            **_credit(None),
            "attribution": (
                "Generated badge, not the club's crest"
                + (": colours from Wikidata (CC0)" if has_colours else "")
            ),
            "source_url": wikidata_url if has_colours else None,
        }

    venue = None
    venue_facts = facts.get("venue") or {}
    photo = _picture(team_dir, files.get("venue"), venue_facts.get("image_file"))
    if venue_facts.get("name") or photo is not None:
        venue = {
            "name": venue_facts.get("name"),
            "url": f"{MEDIA_URL}/{team_key}/{photo['file']}" if photo else None,
            **_credit(photo),
        }
    return {
        "team_key": team_key,
        "wikidata_url": wikidata_url,
        "crest": crest,
        "venue": venue,
        "colours": colours,
    }


# ---------------------------------------------------------------------------------------------
# The short media summary sent with every team the other endpoints name


@lru_cache(maxsize=4096)
def _manifest_cached(path: str, mtime_ns: int) -> dict:
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError):  # unreadable: no picture is shown until it is rewritten
        return {}


def _downloaded(team_dir: Path) -> dict:
    """The team's manifest `files`, re-read only when the manifest changes (one stat call per
    team otherwise, so summaries stay cheap on lists of hundreds of teams)."""
    path = team_dir / "manifest.json"
    try:
        mtime = path.stat().st_mtime_ns
    except OSError:
        return {}
    return _manifest_cached(str(path), mtime).get("files") or {}


def crest_url(team_key: str, settings: Settings, facts: dict | None = None) -> str | None:
    """The URL of the team's downloaded, freely licensed crest, as /media/teams/ serves it; None
    when there is none (never the generated badge: the website draws its own from `badge`)."""
    if not TEAM_KEY.fullmatch(team_key):
        return None
    if facts is None:
        facts = load_facts().get(team_key) or {}
    root = media_dir(settings)
    team_dir = root / team_key
    entry = _picture(team_dir, _downloaded(team_dir).get("crest"), facts.get("crest_file"))
    if entry is None or not PICTURE_FILE.fullmatch(entry.get("file", "")):
        return None
    if root.resolve() not in (team_dir / entry["file"]).resolve().parents:
        return None  # a team folder linked outside data/media/teams is never served
    return f"{MEDIA_URL}/{team_key}/{entry['file']}"


def badge_short(team_key: str, name: str) -> str:
    """The team's badge letters before collisions are resolved: the reviewed ones in
    reference/team_badges.csv, else `short_name`'s."""
    return load_badge_overrides().get(team_key, {}).get("short") or short_name(name)


def media_summary(team_key: str, name: str, settings: Settings, short: str | None = None) -> dict:
    """What every endpoint naming a team sends with it: the downloaded crest's URL (or None)
    and what the website's own badge needs, the club's official colours as the shades drawn
    (the badge's background first, then its band) and the three-letter short name the
    generated badge uses (`short`: the team's letters with collisions resolved,
    footy.teams.TeamsIndex.shorts; else `badge_short`). Colours are Wikidata's (CC0); none are
    made up, so a club without them has `[]`."""
    facts = load_facts().get(team_key) or {}
    colours = ordered_colours(team_key, facts.get("colours") or [])
    return {
        "crest_url": crest_url(team_key, settings, facts),
        "badge": {
            "colours": [c["shade"] for c in colours if c["shade"]],
            "short": short or badge_short(team_key, name),
        },
    }


def media_summaries(
    names: Mapping[str, str], settings: Settings, shorts: Mapping[str, str] | None = None
) -> dict[str, dict]:
    """media_summary for each team key -> name (`shorts`: resolved letters by team key)."""
    shorts = shorts or {}
    return {key: media_summary(key, name, settings, shorts.get(key)) for key, name in names.items()}
