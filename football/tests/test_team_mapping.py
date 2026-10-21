import re
from collections import Counter
from pathlib import Path

import pytest

from footy.config import PROJECT_ROOT
from footy.ingest.fd_extra_parse import parse_fd_extra_csv
from footy.ingest.fd_parse import parse_fd_csv
from footy.reference import DIVISIONS, load_aliases, load_teams

FD_COUNTRIES = {d.country for d in DIVISIONS.values()}


def test_team_keys_unique_and_country_consistent():
    teams = load_teams()
    keys = [t.team_key for t in teams]
    assert len(keys) == len(set(keys))
    for t in teams:
        assert re.fullmatch(r"[A-Z]{3}", t.country)
        assert t.team_key.startswith(t.country.lower() + "-")
        assert t.team_key == t.team_key.lower() and " " not in t.team_key


def test_every_alias_points_at_a_team_and_is_unique():
    teams = {t.team_key for t in load_teams()}
    aliases = load_aliases()
    pairs = [(a.source, a.alias) for a in aliases]
    assert len(pairs) == len(set(pairs))
    assert all(a.team_key in teams for a in aliases)


def test_every_team_in_a_football_data_country_has_a_football_data_alias():
    """Teams from other sources (e.g. UEFA competitions) may come from countries without a
    football-data division; every team of a football-data country is in a division's files,
    except the reviewed UEFA-only clubs (a lower-division cup winner, D-023)."""
    country = {t.team_key: t.country for t in load_teams()}
    fd = {a.team_key for a in load_aliases() if a.source == "football-data"}
    uefa_only = {"prt-torreense"}
    assert all(country[k] in FD_COUNTRIES for k in fd)
    assert fd == {k for k, c in country.items() if c in FD_COUNTRIES} - uefa_only


def test_reviewed_same_club_variants_share_a_key():
    """Spelling variants and renames found in the near-duplicate review (D-022)."""
    by_alias = {a.alias: a.team_key for a in load_aliases() if a.source == "football-data"}
    merged = {
        ("Waasland-Beveren", "Beveren"): "bel-beveren",
        ("Erzurum BB", "Erzurumspor"): "tur-erzurumspor",
        ("Ham-Kam", "HamKam"): "nor-hamkam",
        ("Gornik Z.", "Gornik Zabrze"): "pol-gornik-zabrze",
        ("Din. Bucuresti", "Dinamo Bucuresti"): "rou-dinamo-bucuresti",
        ("U Craiova", "Univ. Craiova"): "rou-univ-craiova",
        ("Viitorul Constanta", "Farul Constanta"): "rou-farul-constanta",
    }
    for names, key in merged.items():
        assert {by_alias[n] for n in names} == {key}
    # Similar names that are different clubs stay apart.
    for a, b in [
        ("U Craiova 1948", "Univ. Craiova"),
        ("Lausanne", "Lausanne Ouchy"),
        ("Wisla", "Wisla Plock"),
        ("Zaglebie", "Zaglebie Sosnowiec"),
        ("AVS", "Aves"),
        ("HIFK", "HJK"),
        ("Dundee", "Dundee United"),
    ]:
        assert by_alias[a] != by_alias[b]
    per_key = Counter(by_alias.values())
    assert sorted(k for k, n in per_key.items() if n > 1) == sorted(merged.values())


RAW = PROJECT_ROOT / "data" / "raw" / "football-data"
RAW_EXTRA = PROJECT_ROOT / "data" / "raw" / "football-data-extra" / "new"


@pytest.mark.skipif(not RAW.is_dir(), reason="needs the local raw store (run footy fd-update)")
def test_all_names_in_downloaded_files_are_mapped_to_one_country():
    by_alias = {a.alias: a.team_key for a in load_aliases() if a.source == "football-data"}
    country = {t.team_key: t.country for t in load_teams()}
    unmapped, wrong_country = set(), set()

    def check(division, matches):
        for m in matches:
            for name in (m.home_name, m.away_name):
                key = by_alias.get(name)
                if key is None:
                    unmapped.add(name)
                elif country[key] != DIVISIONS[division].country:
                    wrong_country.add((name, division))

    for path in sorted(RAW.glob("*/*/r*.csv")):
        division = Path(path).parent.name
        check(division, parse_fd_csv(path.read_bytes(), division, with_odds=False).matches)
    for path in sorted(RAW_EXTRA.glob("*/r*.csv")) if RAW_EXTRA.is_dir() else []:
        division = Path(path).parent.name
        parsed = parse_fd_extra_csv(path.read_bytes(), division)
        check(division, [m for ms in parsed.seasons.values() for m in ms])
    assert not unmapped and not wrong_country
