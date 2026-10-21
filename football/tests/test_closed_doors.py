"""Shape checks for reference/closed_doors.csv (COVID-19 closed-doors and limited-crowd periods).

The rows are hand-curated from the sources in docs/research/closed-doors.md; these tests only
guard the file's structure, so that anything that tags matches by date can rely on it.
"""

import csv
from collections import defaultdict
from datetime import date

from footy.reference import CORE_DIVISIONS, REFERENCE_DIR

PATH = REFERENCE_DIR / "closed_doors.csv"
COLUMNS = ["division", "start_date", "end_date", "crowd_status", "cap_note", "source_url"]


def _rows() -> list[dict[str, str]]:
    with PATH.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == COLUMNS
        return list(reader)


def test_rows_are_well_formed():
    rows = _rows()
    assert rows
    for r in rows:
        assert r["division"] in CORE_DIVISIONS
        start, end = date.fromisoformat(r["start_date"]), date.fromisoformat(r["end_date"])
        assert start <= end
        # Scope is 2019/20 and 2020/21 only.
        assert date(2020, 2, 1) <= start and end <= date(2021, 6, 30)
        assert r["crowd_status"] in {"none", "limited"}
        assert r["cap_note"].strip()
        urls = r["source_url"].split(" ")
        assert urls and all(u.startswith("https://") for u in urls)


def test_periods_do_not_overlap_within_a_division():
    by_division: dict[str, list[tuple[date, date]]] = defaultdict(list)
    for r in _rows():
        by_division[r["division"]].append(
            (date.fromisoformat(r["start_date"]), date.fromisoformat(r["end_date"]))
        )
    for periods in by_division.values():
        periods.sort()
        for (_, prev_end), (next_start, _) in zip(periods, periods[1:], strict=False):
            assert prev_end < next_start


def test_known_boundaries():
    """Spot values taken from the sources (see docs/research/closed-doors.md)."""
    rows = {(r["division"], r["start_date"]): r for r in _rows()}
    # Premier League restart, 17 June 2020, behind closed doors.
    assert rows[("E0", "2020-06-17")]["crowd_status"] == "none"
    # England tiers from 2 December 2020 (up to 2,000 in tier 2).
    assert rows[("E0", "2020-12-02")]["crowd_status"] == "limited"
    # Bundesliga's first ghost game, Gladbach v Köln on 11 March 2020.
    assert rows[("D1", "2020-03-11")]["end_date"] == "2020-03-11"
    # Ligue 1 was never played behind closed doors in 2019/20.
    assert not [r for r in _rows() if r["division"] == "F1" and r["start_date"] < "2020-08-01"]


def test_missing_file_warns_once_and_flags_nothing(monkeypatch, tmp_path, caplog):
    """SYNTHETIC one-row frame. With the file, the Premier League restart is closed doors and
    nothing is logged; without it, no match is flagged and one warning says so."""
    import logging

    import pandas as pd

    from footy.modeling import closed_doors as cd

    df = pd.DataFrame({"division": ["E0"], "date": pd.to_datetime(["2020-06-20"])})
    logger = "footy.modeling.closed_doors"
    cd.periods.cache_clear()
    try:
        with caplog.at_level(logging.WARNING, logger=logger):
            assert cd.closed_doors_mask(df).all()
        assert not [r for r in caplog.records if r.name == logger]

        monkeypatch.setattr(cd, "PATH", tmp_path / "closed_doors.csv")
        monkeypatch.setattr(cd, "_warned_missing", False)
        cd.periods.cache_clear()
        with caplog.at_level(logging.WARNING, logger=logger):
            assert not cd.closed_doors_mask(df).any()
            assert not cd.closed_doors_mask(df, ("none", "limited")).any()
            assert cd.periods() == {}
        warnings = [r for r in caplog.records if r.name == logger]
        assert len(warnings) == 1
        assert warnings[0].levelno == logging.WARNING
        assert "closed_doors.csv is missing" in warnings[0].getMessage()
    finally:
        cd.periods.cache_clear()  # later tests read the real file again
