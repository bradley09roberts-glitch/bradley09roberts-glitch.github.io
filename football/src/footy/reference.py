"""Reference data: divisions in scope, and the team mapping tables in reference/*.csv."""

import csv
from dataclasses import dataclass
from pathlib import Path

REFERENCE_DIR = Path(__file__).parent / "reference"


@dataclass(frozen=True)
class DivisionInfo:
    code: str  # football-data code
    name: str
    country: str  # ISO 3166-1 alpha-3, except the home nations (ENG, SCO)
    tier: int
    # core: the ten M1 divisions (results, stats, prices).
    # europe: extra domestic leagues, for rating clubs in UEFA competitions (results and
    # prices; statistics only in the main layout).
    scope: str = "core"
    # main: one file per season at mmz4281/{season}/{code}.csv (the E0 layout).
    # extra: one file holding every season at new/{code}.csv (Country, League, Season, ...).
    layout: str = "main"
    # Most times the same home team may host the same away team in one season: `matches.meeting`
    # runs 1..max_meetings (D-022). 1 = a double round robin; split and multi-round formats
    # need more. Set from the raw files (the largest count any pairing reaches in any season,
    # 21 Oct 2026); a later meeting beyond it is held back as `repeat_pairing`.
    max_meetings: int = 1


def _europe(
    code: str, name: str, country: str, layout: str = "main", max_meetings: int = 1
) -> DivisionInfo:
    return DivisionInfo(
        code, name, country, 1, scope="europe", layout=layout, max_meetings=max_meetings
    )


DIVISIONS: dict[str, DivisionInfo] = {
    d.code: d
    for d in [
        DivisionInfo("E0", "Premier League", "ENG", 1),
        DivisionInfo("SP1", "La Liga", "ESP", 1),
        DivisionInfo("I1", "Serie A", "ITA", 1),
        DivisionInfo("D1", "Bundesliga", "DEU", 1),
        DivisionInfo("F1", "Ligue 1", "FRA", 1),
        DivisionInfo("E1", "Championship", "ENG", 2),
        DivisionInfo("SP2", "Segunda División", "ESP", 2),
        DivisionInfo("I2", "Serie B", "ITA", 2),
        DivisionInfo("D2", "2. Bundesliga", "DEU", 2),
        DivisionInfo("F2", "Ligue 2", "FRA", 2),
        # Main-layout European leagues (same CSV columns as E0).
        _europe("SC0", "Scottish Premiership", "SCO", max_meetings=3),
        _europe("N1", "Eredivisie", "NLD"),
        _europe("B1", "Belgian Pro League", "BEL", max_meetings=2),
        _europe("P1", "Primeira Liga", "PRT"),
        _europe("T1", "Süper Lig", "TUR"),
        _europe("G1", "Super League Greece", "GRC", max_meetings=2),
        # football-data's "extra leagues" (European ones only), listed on all_new_data.php
        # on 2026-10-21. Codes are football-data's (SWZ is Switzerland, ISO CHE).
        _europe("AUT", "Austrian Bundesliga", "AUT", "extra", 3),
        _europe("DNK", "Danish Superliga", "DNK", "extra", 2),
        _europe("SWZ", "Swiss Super League", "CHE", "extra", 3),
        _europe("NOR", "Eliteserien", "NOR", "extra"),
        _europe("SWE", "Allsvenskan", "SWE", "extra"),
        _europe("POL", "Ekstraklasa", "POL", "extra", 2),
        _europe("ROU", "Liga I", "ROU", "extra", 3),
        _europe("RUS", "Russian Premier League", "RUS", "extra"),
        _europe("FIN", "Veikkausliiga", "FIN", "extra", 3),
        _europe("IRL", "League of Ireland Premier Division", "IRL", "extra", 2),
    ]
}
CORE_DIVISIONS = [c for c, d in DIVISIONS.items() if d.scope == "core"]
EUROPE_DIVISIONS = [c for c, d in DIVISIONS.items() if d.scope == "europe"]
# The five core top divisions (until fd-5 the only ones whose prices were parsed; D-016,
# D-022).
TOP_DIVISIONS = [c for c, d in DIVISIONS.items() if d.scope == "core" and d.tier == 1]
MAIN_LAYOUT_DIVISIONS = [c for c, d in DIVISIONS.items() if d.layout == "main"]
EXTRA_LAYOUT_DIVISIONS = [c for c, d in DIVISIONS.items() if d.layout == "extra"]


@dataclass(frozen=True)
class TeamRow:
    team_key: str
    name: str
    country: str


@dataclass(frozen=True)
class AliasRow:
    source: str
    alias: str
    team_key: str


def load_teams(path: Path = REFERENCE_DIR / "teams.csv") -> list[TeamRow]:
    with path.open(newline="", encoding="utf-8") as f:
        return [TeamRow(r["team_key"], r["name"], r["country"]) for r in csv.DictReader(f)]


def load_aliases(path: Path = REFERENCE_DIR / "team_aliases.csv") -> list[AliasRow]:
    with path.open(newline="", encoding="utf-8") as f:
        return [AliasRow(r["source"], r["alias"], r["team_key"]) for r in csv.DictReader(f)]
