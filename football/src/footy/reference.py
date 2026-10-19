"""Reference data: divisions in scope, and the team mapping tables in reference/*.csv."""

import csv
from dataclasses import dataclass
from pathlib import Path

REFERENCE_DIR = Path(__file__).parent / "reference"


@dataclass(frozen=True)
class DivisionInfo:
    code: str  # football-data code
    name: str
    country: str  # ISO 3166-1 alpha-3, England as ENG
    tier: int


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
    ]
}
TOP_DIVISIONS = [c for c, d in DIVISIONS.items() if d.tier == 1]


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
