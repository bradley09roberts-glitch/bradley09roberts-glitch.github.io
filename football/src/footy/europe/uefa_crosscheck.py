"""Second-source check of the Wikipedia fixtures against UEFA.com match pages.

UEFA.com's robots.txt allows its match pages (`/<competition>/match/<id>/`). Each page carries a
schema.org `SportsEvent` in JSON-LD: the scheduled start in UTC, both teams (UEFA's short names,
e.g. "Man Utd"), the event status, the stadium and its country. Wikipedia's football boxes link
to the same UEFA match id, so the two are paired by id, not by name.

Pages are fetched with `footy.europe.wiki_fetch.fetch_and_store` (source "uefa", logical key
`<comp>/2627/match/<id>`) and only parsed from the raw store.
"""

import json
import re
import unicodedata
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from sqlalchemy.orm import Session

from footy.europe.wikipedia_2627 import FIFA_TO_ISO, EuroRow, write_csv
from footy.ingest.raw_store import latest_revision, read_raw

SOURCE = "uefa"
COMPETITION_PATHS = {
    "UCL": "uefachampionsleague",
    "UEL": "uefaeuropaleague",
    "UECL": "uefaconferenceleague",
}
CHECK_COLUMNS = [
    "competition",
    "stage",
    "uefa_match_id",
    "wiki_home",
    "wiki_away",
    "uefa_home",
    "uefa_away",
    "teams_agree",
    "wiki_kickoff_utc",
    "uefa_kickoff_utc",
    "kickoff_agrees",
    "wiki_status",
    "uefa_status",
    "uefa_venue",
    "uefa_venue_country",
    "wiki_neutral",
    "uefa_neutral",
    "note",
    "wiki_ref",
    "uefa_raw_file_id",
]

_LD_JSON = re.compile(r'<script type="application/ld\+json">(.*?)</script>', re.S)


def logical_key(competition: str, match_id: str) -> str:
    return f"{competition.lower()}/2627/match/{match_id}"


def match_url(competition: str, match_id: str) -> str:
    return f"https://www.uefa.com/{COMPETITION_PATHS[competition]}/match/{match_id}/"


@dataclass(frozen=True)
class UefaEvent:
    match_id: str
    start_utc: datetime | None
    home: str
    away: str
    status: str | None  # schema.org eventStatus, e.g. "EventScheduled"
    venue: str | None
    venue_country: str | None  # UEFA's three-letter code


def _sports_events(data) -> list[dict]:
    found: list[dict] = []
    stack = [data]
    while stack:
        item = stack.pop()
        if isinstance(item, list):
            stack.extend(item)
        elif isinstance(item, dict):
            if item.get("@type") == "SportsEvent":
                found.append(item)
            stack.extend(v for v in item.values() if isinstance(v, (dict, list)))
    return found


def parse_uefa_match_page(html: str, match_id: str) -> UefaEvent | None:
    """The page's own SportsEvent (the one whose URL carries `match_id`), if any."""
    for block in _LD_JSON.finditer(html):
        try:
            data = json.loads(block.group(1))
        except ValueError:
            continue
        for event in _sports_events(data):
            if f"/match/{match_id}/" not in str(event.get("url", event.get("@id", ""))):
                continue
            start = event.get("startDate")
            location = event.get("location") or {}
            address = location.get("address") or {}
            status = event.get("eventStatus")
            return UefaEvent(
                match_id=match_id,
                start_utc=datetime.fromisoformat(start) if start else None,
                home=(event.get("homeTeam") or {}).get("name", ""),
                away=(event.get("awayTeam") or {}).get("name", ""),
                status=status.rsplit("/", 1)[-1] if status else None,
                venue=location.get("name"),
                venue_country=address.get("addressCountry"),
            )
    return None


_STOP = {"fc", "cf", "sc", "ac", "afc", "fk", "sk", "the", "club", "de", "cp", "sv", "bc", "kv"}
_ABBREV = {"utd": "united", "man": "manchester", "atleti": "atletico", "b": "borussia"}


def _words(name: str) -> list[str]:
    text = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().casefold()
    return [_ABBREV.get(w, w) for w in re.findall(r"[a-z0-9]+", text) if w not in _STOP]


def names_agree(a: str, b: str) -> bool:
    """Loose name match between Wikipedia's and UEFA's labels ("Manchester United" and
    "Man Utd", "NEC" and "N.E.C.", "Heart of Midlothian" and "Hearts"). Teams are paired by
    UEFA match id; this only catches a box pointing at the wrong match, so one shared word
    (or a shared four-letter stem) is enough. Translations such as "Red Star Belgrade" and
    "Crvena Zvezda" are reported as differing labels."""
    wa, wb = _words(a), _words(b)
    if not wa or not wb:
        return False
    if set(wa) & set(wb) or "".join(wa) == "".join(wb):
        return True
    return any(len(x) >= 4 and len(y) >= 4 and x[:4] == y[:4] for x in wa for y in wb)


@dataclass
class CrossCheck:
    row: EuroRow
    event: UefaEvent | None
    raw_file_id: int | None
    teams_agree: bool | None
    kickoff_agrees: bool | None
    note: str
    wiki_neutral: bool = False  # as parsed from Wikipedia, before any UEFA evidence
    uefa_neutral: bool | None = None  # UEFA's venue country differs from the home team's

    def csv_row(self) -> dict[str, str]:
        e, r = self.event, self.row

        def flag(v: bool | None) -> str:
            return "" if v is None else str(int(v))

        return {
            "competition": r.competition,
            "stage": r.stage,
            "uefa_match_id": r.uefa_match_id or "",
            "wiki_home": r.home_name,
            "wiki_away": r.away_name,
            "uefa_home": e.home if e else "",
            "uefa_away": e.away if e else "",
            "teams_agree": flag(self.teams_agree),
            "wiki_kickoff_utc": r.kickoff_utc.isoformat() if r.kickoff_utc else "",
            "uefa_kickoff_utc": e.start_utc.isoformat() if e and e.start_utc else "",
            "kickoff_agrees": flag(self.kickoff_agrees),
            "wiki_status": r.status,
            "uefa_status": (e.status or "") if e else "",
            "uefa_venue": (e.venue or "") if e else "",
            "uefa_venue_country": (e.venue_country or "") if e else "",
            "wiki_neutral": flag(self.wiki_neutral),
            "uefa_neutral": flag(self.uefa_neutral),
            "note": self.note,
            "wiki_ref": r.source_ref,
            "uefa_raw_file_id": "" if self.raw_file_id is None else str(self.raw_file_id),
        }


def compare(row: EuroRow, event: UefaEvent | None, raw_file_id: int | None) -> CrossCheck:
    if event is None:
        return CrossCheck(
            row, None, raw_file_id, None, None, "no UEFA SportsEvent found", row.neutral
        )
    teams = names_agree(row.home_name, event.home) and names_agree(row.away_name, event.away)
    kickoff = None
    if row.kickoff_utc is not None and event.start_utc is not None:
        kickoff = row.kickoff_utc == event.start_utc
    uefa_neutral = None
    if event.venue_country:
        venue = FIFA_TO_ISO.get(event.venue_country, event.venue_country)
        uefa_neutral = venue != row.home_country
    notes = []
    if not teams:
        notes.append("team labels differ")
    if kickoff is False:
        notes.append("kick-off differs")
    if kickoff is None:
        notes.append("kick-off missing in one source")
    if uefa_neutral is not None and uefa_neutral != row.neutral:
        notes.append(f"venue country {event.venue_country} per UEFA")
    if row.status == "scheduled" and event.status not in (None, "EventScheduled"):
        notes.append(f"UEFA status {event.status}")
    return CrossCheck(
        row, event, raw_file_id, teams, kickoff, "; ".join(notes), row.neutral, uefa_neutral
    )


def apply_uefa_venues(checks: list[CrossCheck]) -> int:
    """Mark a match neutral when UEFA places it outside the home team's country but the
    Wikipedia stadium line didn't name the country (e.g. Union SG v Hapoel Be'er Sheva moved to
    Düsseldorf). Returns how many rows changed."""
    changed = 0
    for c in checks:
        if c.uefa_neutral and not c.row.neutral:
            c.row.neutral = True
            c.row.source_ref += f"; neutral: venue country {c.event.venue_country} per UEFA.com"
            changed += 1
    return changed


def crosscheck_rows(
    session: Session,
    raw_dir: Path,
    rows: list[EuroRow],
    *,
    out_csv: Path | None = None,
) -> list[CrossCheck]:
    """Compare every row that has a stored UEFA match page; rows without one are skipped."""
    checks: list[CrossCheck] = []
    for row in rows:
        if not row.uefa_match_id or row.competition not in COMPETITION_PATHS:
            continue
        raw = latest_revision(session, SOURCE, logical_key(row.competition, row.uefa_match_id))
        if raw is None:
            continue
        html = read_raw(raw_dir, raw).decode("utf-8", errors="replace")
        checks.append(compare(row, parse_uefa_match_page(html, row.uefa_match_id), raw.id))
    if out_csv is not None:
        write_csv(out_csv, CHECK_COLUMNS, (c.csv_row() for c in checks))
    return checks
