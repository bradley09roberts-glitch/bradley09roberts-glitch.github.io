from pathlib import Path

import pytest

from footy.config import PROJECT_ROOT
from footy.ingest.fd_parse import parse_fd_csv
from footy.reference import DIVISIONS, load_aliases, load_teams


def test_team_keys_unique_and_country_consistent():
    teams = load_teams()
    keys = [t.team_key for t in teams]
    assert len(keys) == len(set(keys))
    countries = {d.country for d in DIVISIONS.values()}
    for t in teams:
        assert t.country in countries
        assert t.team_key.startswith(t.country.lower() + "-")
        assert t.team_key == t.team_key.lower() and " " not in t.team_key


def test_every_alias_points_at_a_team_and_is_unique():
    teams = {t.team_key for t in load_teams()}
    aliases = load_aliases()
    pairs = [(a.source, a.alias) for a in aliases]
    assert len(pairs) == len(set(pairs))
    assert all(a.team_key in teams for a in aliases)


def test_every_team_has_a_football_data_alias():
    fd = {a.team_key for a in load_aliases() if a.source == "football-data"}
    assert fd == {t.team_key for t in load_teams()}


RAW = PROJECT_ROOT / "data" / "raw" / "football-data"


@pytest.mark.skipif(not RAW.is_dir(), reason="needs the local raw store (run footy fd-update)")
def test_all_names_in_downloaded_files_are_mapped_to_one_country():
    by_alias = {a.alias: a.team_key for a in load_aliases() if a.source == "football-data"}
    country = {t.team_key: t.country for t in load_teams()}
    unmapped, wrong_country = set(), set()
    for path in sorted(RAW.glob("*/*/r*.csv")):
        division = Path(path).parent.name
        for m in parse_fd_csv(path.read_bytes(), division, with_odds=False).matches:
            for name in (m.home_name, m.away_name):
                key = by_alias.get(name)
                if key is None:
                    unmapped.add(name)
                elif country[key] != DIVISIONS[division].country:
                    wrong_country.add((name, division))
    assert not unmapped and not wrong_country
