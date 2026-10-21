"""Schedule-to-database team mapping is verified meeting by meeting (SYNTHETIC rows)."""

from datetime import date

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.fixtures import ScheduledMatch, map_teams
from footy.ingest.fd_import import sync_reference

PAIRS = [
    ("eng-testtown", "eng-sampleton"),
    ("eng-sampleton", "eng-mockford"),
    ("eng-mockford", "eng-testtown"),
]


def load(engine, rows):
    with Session(engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    with engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-25', 'p')"
            )
        )
        for i, (home, away, meeting, day, hg, ag) in enumerate(rows, start=1):
            conn.execute(
                text(
                    "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                    "meeting, home_goals, away_goals, result, source_row, first_raw_file_id, "
                    "last_raw_file_id) VALUES ('SC0', '2627', :day, :h, :a, :m, :hg, :ag, 'H', "
                    ":i, 1, 1)"
                ),
                {"day": str(day), "h": home, "a": away, "m": meeting, "hg": hg, "ag": ag, "i": i},
            )


NAMES = {"eng-testtown": "Testtown FC", "eng-sampleton": "Sampleton", "eng-mockford": "Mockford"}


def sched(home, away, day, hg, ag):
    return ScheduledMatch(day, None, NAMES[home], NAMES[away], hg, ag, 0)


def test_each_meeting_is_checked_against_its_own_score(migrated_engine):
    first = [(h, a, 1, date(2026, 8, 1 + i), 1, 0) for i, (h, a) in enumerate(PAIRS)]
    load(migrated_engine, [*first, (*PAIRS[0], 2, date(2026, 12, 5), 2, 2)])
    schedule = [sched(h, a, d, hg, ag) for h, a, _, d, hg, ag in first]
    schedule.append(sched(*PAIRS[0], date(2026, 12, 5), 2, 2))
    mapping, check = map_teams(migrated_engine, "SC0", "2627", schedule)
    assert mapping["Testtown FC"] == "eng-testtown" and check["checked_results"] == 4

    schedule[-1] = sched(*PAIRS[0], date(2026, 12, 5), 1, 0)  # meeting 1's score, not 2's
    with pytest.raises(ValueError, match="mismatches"):
        map_teams(migrated_engine, "SC0", "2627", schedule)
