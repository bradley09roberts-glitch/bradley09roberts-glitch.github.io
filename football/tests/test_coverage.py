"""Expected match counts in the coverage report, with repeat meetings (SYNTHETIC rows)."""

import itertools

import pandas as pd

from footy.coverage import expected_matches
from footy.ingest.fd_extra_parse import PLAYOFF_EXCLUSION


def season(pairs, exclusion=None):
    """One row per (home, away, meeting)."""
    return pd.DataFrame(
        {
            "home_team": [h for h, _, _ in pairs],
            "away_team": [a for _, a, _ in pairs],
            "meeting": [m for _, _, m in pairs],
            "model_exclusion": [exclusion(h, a) if exclusion else None for h, a, _ in pairs],
        }
    )


CLUBS = ["a", "b", "c", "d"]
ROUND_ROBIN = [(h, a, 1) for h, a in itertools.permutations(CLUBS, 2)]


def test_double_round_robin_is_teams_times_teams_minus_one():
    assert expected_matches(season(ROUND_ROBIN)) == 4 * 3
    # In progress: still the full count, so the shortfall shows.
    assert expected_matches(season(ROUND_ROBIN[:5])) == 4 * 3


def test_further_meetings_are_added_once_played():
    split = ROUND_ROBIN + [("a", "b", 2), ("b", "a", 2), ("a", "b", 3)]
    assert expected_matches(season(split)) == 12 + 3
    # A season that stopped early: unplayed first meetings still count.
    assert expected_matches(season(split[2:])) == 12 + 3


def test_playoff_visitors_are_not_counted_as_clubs():
    playoff = ROUND_ROBIN + [("v", "d", 1), ("d", "v", 1)]
    g = season(playoff, lambda h, a: PLAYOFF_EXCLUSION if "v" in (h, a) else None)
    assert expected_matches(g) == 12 + 2  # not 5 x 4
