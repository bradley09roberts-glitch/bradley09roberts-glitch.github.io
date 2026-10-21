"""Divisions in scope: the core ten are unchanged by the European leagues (D-022)."""

import re

from footy.ingest.fd_extra_parse import FD_COUNTRY_NAMES
from footy.reference import (
    CORE_DIVISIONS,
    DIVISIONS,
    EUROPE_DIVISIONS,
    EXTRA_LAYOUT_DIVISIONS,
    MAIN_LAYOUT_DIVISIONS,
    TOP_DIVISIONS,
)


def test_core_divisions_unchanged():
    assert TOP_DIVISIONS == ["E0", "SP1", "I1", "D1", "F1"]
    assert CORE_DIVISIONS == ["E0", "SP1", "I1", "D1", "F1", "E1", "SP2", "I2", "D2", "F2"]
    assert all(DIVISIONS[c].layout == "main" for c in CORE_DIVISIONS)


def test_european_divisions():
    assert set(EUROPE_DIVISIONS) == set(DIVISIONS) - set(CORE_DIVISIONS)
    assert {"SC0", "N1", "B1", "P1", "T1", "G1"} <= set(MAIN_LAYOUT_DIVISIONS)
    assert {"AUT", "DNK", "SWZ", "NOR", "SWE", "POL", "ROU"} <= set(EXTRA_LAYOUT_DIVISIONS)
    assert set(EXTRA_LAYOUT_DIVISIONS) == set(FD_COUNTRY_NAMES)
    for code in EUROPE_DIVISIONS:
        d = DIVISIONS[code]
        assert d.scope == "europe" and d.tier == 1
        assert re.fullmatch(r"[A-Z]{3}", d.country)
    assert DIVISIONS["SC0"].country == "SCO" and DIVISIONS["SWZ"].country == "CHE"
    # One division per country, so a club's country is its league's country.
    countries = [d.country for d in DIVISIONS.values() if d.tier == 1]
    assert len(countries) == len(set(countries))
