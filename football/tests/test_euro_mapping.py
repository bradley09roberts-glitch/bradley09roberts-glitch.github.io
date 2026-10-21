"""Team identity for UEFA competitions: every openfootball and Wikipedia club name maps to one
team in reference/teams.csv (D-023)."""

import csv
import re
from collections import defaultdict

import pytest

from footy.config import PROJECT_ROOT
from footy.europe.openfootball import FILES, LEAGUE_COUNTRY, OF_COUNTRIES, parse_openfootball
from footy.reference import load_aliases, load_teams

UEFA_SOURCES = ("openfootball", "wikipedia")
_SUFFIX = re.compile(r"^(?P<name>.*\S) \((?P<code>[A-Z]{3})\)$")


def _alias_country(source: str, alias: str) -> str:
    """The country (as in teams.csv) an alias says its club is from: openfootball aliases end
    in openfootball's code (FIFA trigrams), Wikipedia aliases in the ISO code of its flag."""
    m = _SUFFIX.match(alias)
    assert m, f"{source} alias without a country code: {alias!r}"
    code = m.group("code")
    iso = OF_COUNTRIES[code] if source == "openfootball" else code
    return LEAGUE_COUNTRY.get(iso, iso)


def _uefa_aliases():
    return [a for a in load_aliases() if a.source in UEFA_SOURCES]


def test_uefa_aliases_unique_per_source_and_point_at_teams():
    teams = {t.team_key for t in load_teams()}
    aliases = _uefa_aliases()
    assert {a.source for a in aliases} == set(UEFA_SOURCES)
    for source in UEFA_SOURCES:
        names = [a.alias for a in aliases if a.source == source]
        assert len(names) == len(set(names)), source
    assert all(a.team_key in teams for a in aliases)


def test_no_team_has_aliases_from_two_countries():
    country = {t.team_key: t.country for t in load_teams()}
    seen: dict[str, set[str]] = defaultdict(set)
    for a in _uefa_aliases():
        seen[a.team_key].add(_alias_country(a.source, a.alias))
    for key, countries in seen.items():
        assert countries == {country[key]}, (key, countries)


def test_new_team_keys_follow_the_scheme():
    for t in load_teams():
        assert re.fullmatch(r"[a-z]{3}-[a-z0-9]+(?:-[a-z0-9]+)*", t.team_key), t.team_key
        assert t.team_key[:3] == t.country.lower()


def _key(source: str, alias: str) -> str:
    return {(a.source, a.alias): a.team_key for a in load_aliases()}[(source, alias)]


def test_clubs_already_known_keep_their_key():
    assert _key("openfootball", "FC Bayern München (GER)") == "deu-bayern-munich"
    assert _key("openfootball", "Bayern München (GER)") == "deu-bayern-munich"
    assert _key("wikipedia", "Bayern Munich (DEU)") == "deu-bayern-munich"
    assert _key("openfootball", "AS Monaco FC (MCO)") == "fra-monaco"
    assert _key("openfootball", "FC Vaduz (LIE)") == "che-vaduz"
    assert _key("openfootball", "İstanbul Başakşehir (TUR)") == "tur-buyuksehyr"
    assert _key("openfootball", "CS Universitatea Craiova (ROU)") == "rou-univ-craiova"
    assert _key("wikipedia", "AGF (DNK)") == "dnk-aarhus"
    assert _key("wikipedia", "NEC (NLD)") == "nld-nijmegen"


@pytest.mark.parametrize(
    ("a", "b"),
    [
        (("openfootball", "Sporting CP (POR)"), ("football-data", "Sp Gijon")),
        (("openfootball", "Sporting CP (POR)"), ("openfootball", "Sporting Braga (POR)")),
        (("openfootball", "Crvena Zvezda (SRB)"), ("openfootball", "RB Salzburg (AUT)")),
        (("openfootball", "Dinamo Zagreb (CRO)"), ("football-data", "Dinamo Bucuresti")),
        (("openfootball", "Dinamo Zagreb (CRO)"), ("openfootball", "Dinamo Kiev (UKR)")),
        (("openfootball", "AEK Athen (GRE)"), ("openfootball", "AEK Larnaca (CYP)")),
        (
            ("openfootball", "Olympiakos Piraeus (GRE)"),
            ("openfootball", "Olimpija Ljubljana (SVN)"),
        ),
        (("openfootball", "Paris Saint-Germain (FRA)"), ("football-data", "Paris FC")),
        (("openfootball", "Inter (ITA)"), ("openfootball", "AC Milan (ITA)")),
        (("wikipedia", "Inter Milan (ITA)"), ("wikipedia", "Milan (ITA)")),
        (("openfootball", "Lazio Roma (ITA)"), ("openfootball", "AS Roma (ITA)")),
        (("openfootball", "CSKA Sofia (BUL)"), ("openfootball", "CSKA 1948 Sofia (BUL)")),
        (("openfootball", "FK Žalgiris (LTU)"), ("openfootball", "Kauno Žalgiris (LTU)")),
        (("openfootball", "Hibernian FC (SCO)"), ("openfootball", "Hibernians FC (MLT)")),
        (("openfootball", "Partizan (SRB)"), ("openfootball", "FK Partizani (ALB)")),
        (("openfootball", "Víkingur (FRO)"), ("openfootball", "Víkingur Reykjavík (ISL)")),
        (("openfootball", "FC Santa Coloma (AND)"), ("openfootball", "UE Santa Coloma (AND)")),
        (("openfootball", "Wisła Kraków (POL)"), ("football-data", "Wisla Plock")),
        (("openfootball", "Universitatea Cluj (ROU)"), ("openfootball", "CFR Cluj (ROU)")),
    ],
)
def test_similar_names_are_different_clubs(a, b):
    assert _key(*a) != _key(*b)


@pytest.mark.parametrize(
    "names",
    [
        [("openfootball", "Crvena Zvezda (SRB)"), ("wikipedia", "Red Star Belgrade (SRB)")],
        [("openfootball", "Dinamo Kiev (UKR)"), ("wikipedia", "Dynamo Kyiv (UKR)")],
        [("openfootball", "Pafos FC (CYP)"), ("openfootball", "Paphos FC (CYP)")],
        [("openfootball", "Drita Gjilan (KOS)"), ("openfootball", "KF Drita (KOS)")],
        [("openfootball", "Qarabağ FK (AZE)"), ("openfootball", "Qarabağ Ağdam FK (AZE)")],
        [("openfootball", "Sporting CP (POR)"), ("wikipedia", "Sporting CP (PRT)")],
        [("openfootball", "SL Benfica (POR)"), ("openfootball", "Sport Lisboa e Benfica (POR)")],
        [("openfootball", "Olympiakos Piraeus (GRE)"), ("wikipedia", "Olympiacos (GRC)")],
    ],
)
def test_spellings_of_one_club_share_a_key(names):
    assert len({_key(*n) for n in names}) == 1


def test_fd_country_teams_without_football_data_alias_are_listed():
    """A team of a football-data country normally comes from a division's files; the only
    exception is a cup winner from a lower division that reached a UEFA competition."""
    fd = {a.team_key for a in load_aliases() if a.source == "football-data"}
    uefa_only = {a.team_key for a in _uefa_aliases()} - fd
    fd_countries = {t.country for t in load_teams() if t.team_key in fd}
    country = {t.team_key: t.country for t in load_teams()}
    assert {k for k in uefa_only if country[k] in fd_countries} == {"prt-torreense"}


RAW = PROJECT_ROOT / "data" / "raw" / "openfootball"
CSV_2627 = PROJECT_ROOT / "data" / "europe" / "uefa_2627.csv"


@pytest.mark.skipif(not RAW.is_dir(), reason="needs the local raw store (run footy euro-update)")
def test_every_name_in_the_stored_openfootball_files_resolves():
    aliases = {a.alias for a in load_aliases() if a.source == "openfootball"}
    unmapped = set()
    for path in sorted(RAW.glob("*/*/r*.txt")):
        season, file_name = path.parent.parent.name, path.parent.name
        assert file_name in FILES
        for m in parse_openfootball(path.read_bytes(), file_name, season).matches:
            unmapped |= {m.home_name, m.away_name} - aliases
    assert not unmapped


@pytest.mark.skipif(not CSV_2627.is_file(), reason="needs data/europe/uefa_2627.csv")
def test_every_name_in_the_2026_27_csv_resolves():
    aliases = {a.alias for a in load_aliases() if a.source == "wikipedia"}
    with CSV_2627.open(newline="", encoding="utf-8") as f:
        names = {
            f"{r[side + '_name']} ({r[side + '_country']})"
            for r in csv.DictReader(f)
            for side in ("home", "away")
        }
    assert names and not names - aliases
