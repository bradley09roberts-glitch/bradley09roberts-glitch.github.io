"""Crest file, home venue (with its photo) and official colours of every mapped club, from
the Wikidata Query Service, saved to reference/team_media.json (Wikidata is CC0).

- Crest: P154 (logo image), a Wikimedia Commons file name.
- Venue: P115 (home venue), with the venue's English label and its P18 (image) file.
- Colours: P6364 (official colour), with each colour's English label and P465 (sRGB hex).

Deprecated statements are ignored. Of several logos or venues, a current one (no end time,
P582) is taken, preferred rank first, then the latest start time (P580), then by name, so
the choice is deterministic. A logo or venue whose every statement has ended is left out.
"""

import json
from collections import defaultdict
from datetime import UTC, datetime
from pathlib import Path

from footy.media.badge import valid_hex
from footy.media.mapping import MappingRow
from footy.media.wdqs import WdqsClient, qid_values
from footy.reference import REFERENCE_DIR

MEDIA_JSON = REFERENCE_DIR / "team_media.json"
WIKIDATA_ITEM_URL = "https://www.wikidata.org/wiki/{qid}"
PREFERRED = "http://wikiba.se/ontology#PreferredRank"


def statements_query(qids, prop: str, extra: str = "", extra_vars: str = "") -> str:
    return f"""SELECT ?item ?value ?rank ?start ?end {extra_vars} WHERE {{
  VALUES ?item {{ {qid_values(qids)} }}
  ?item p:{prop} ?st .
  ?st ps:{prop} ?value ; wikibase:rank ?rank .
  FILTER(?rank != wikibase:DeprecatedRank)
  OPTIONAL {{ ?st pq:P580 ?start }}
  OPTIONAL {{ ?st pq:P582 ?end }}{extra}
}}"""


def logo_query(qids) -> str:
    return statements_query(qids, "P154")


def venue_query(qids) -> str:
    extra = """
  OPTIONAL { ?value rdfs:label ?name FILTER(LANG(?name) = "en") }
  OPTIONAL { ?value wdt:P18 ?image }"""
    return statements_query(qids, "P115", extra, "?name ?image")


def colour_query(qids) -> str:
    extra = """
  OPTIONAL { ?value rdfs:label ?name FILTER(LANG(?name) = "en") }
  OPTIONAL { ?value wdt:P465 ?hex }"""
    return statements_query(qids, "P6364", extra, "?name ?hex")


def _current(rows: list[dict]) -> list[dict]:
    """Statements still in force, best first (see the module docstring)."""
    live = sorted((r for r in rows if not r.get("end")), key=lambda r: r["value"])
    live.sort(key=lambda r: r.get("start") or "", reverse=True)  # latest first, none last
    live.sort(key=lambda r: r["rank"] != PREFERRED)  # stable: preferred rank first
    return live


def fetch_facts(
    client: WdqsClient, mapping: dict[str, MappingRow], batch: int = 150
) -> dict[str, dict]:
    """{team_key: facts} for every mapped team (see `team_entry`)."""
    by_qid: dict[str, list[str]] = defaultdict(list)
    for key, row in mapping.items():
        if row.qid:
            by_qid[row.qid].append(key)
    qids = sorted(by_qid, key=lambda q: int(q[1:]))
    logos: dict[str, list[dict]] = defaultdict(list)
    venues: dict[str, list[dict]] = defaultdict(list)
    colours: dict[str, list[dict]] = defaultdict(list)
    for i in range(0, len(qids), batch):
        part = qids[i : i + batch]
        for r in client.query(logo_query(part)):
            logos[r["item"]].append(r)
        for r in client.query(venue_query(part)):
            venues[r["item"]].append(r)
        for r in client.query(colour_query(part)):
            colours[r["item"]].append(r)
    out = {}
    for qid in qids:
        entry = team_entry(qid, logos[qid], venues[qid], colours[qid])
        for key in by_qid[qid]:
            out[key] = {"qid": qid, "wikidata_label": mapping[key].wikidata_label, **entry}
    return out


def team_entry(qid: str, logos: list[dict], venues: list[dict], colours: list[dict]) -> dict:
    crest = _current(logos)
    venue_rows = _current(venues)
    venue = None
    if venue_rows:
        best = venue_rows[0]["value"]
        same = [r for r in venue_rows if r["value"] == best]
        images = sorted({r["image"] for r in same if r.get("image")})
        names = sorted({r["name"] for r in same if r.get("name")})
        venue = {
            "qid": best,
            "name": names[0] if names else None,
            "image_file": images[0] if images else None,
        }
    seen: dict[str, dict] = {}
    for r in _current(colours):
        c = seen.setdefault(r["value"], {"qid": r["value"], "name": r.get("name"), "hex": None})
        hexes = sorted(h for h in [valid_hex(r.get("hex"))] if h)
        if hexes and (c["hex"] is None or hexes[0] < c["hex"]):
            c["hex"] = hexes[0]
    return {
        "crest_file": crest[0]["value"] if crest else None,
        "venue": venue,
        "colours": sorted(seen.values(), key=lambda c: (c["name"] or "", c["qid"])),
    }


def write_facts(facts: dict[str, dict], path: Path = MEDIA_JSON) -> None:
    doc = {
        "source": "Wikidata Query Service (https://query.wikidata.org/); Wikidata is CC0",
        "retrieved": datetime.now(UTC).date().isoformat(),
        "properties": {
            "crest_file": "P154 logo image (a Wikimedia Commons file)",
            "venue": "P115 home venue: its English label and P18 image (a Commons file)",
            "colours": "P6364 official colour, with the colour's P465 sRGB hex",
        },
        "teams": {key: facts[key] for key in sorted(facts)},
    }
    path.write_text(json.dumps(doc, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def read_facts(path: Path = MEDIA_JSON) -> dict[str, dict]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8")).get("teams", {})


def summary(facts: dict[str, dict]) -> dict:
    return {
        "teams": len(facts),
        "with_crest_file": sum(1 for f in facts.values() if f["crest_file"]),
        "with_venue": sum(1 for f in facts.values() if f["venue"]),
        "with_venue_photo": sum(1 for f in facts.values() if (f["venue"] or {}).get("image_file")),
        "with_colours": sum(1 for f in facts.values() if f["colours"]),
        "with_colour_hex": sum(1 for f in facts.values() if any(c["hex"] for c in f["colours"])),
    }
