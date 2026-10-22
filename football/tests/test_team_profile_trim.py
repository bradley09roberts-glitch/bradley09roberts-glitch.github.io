"""How a team profile stays small (footy.teams): the strength series is thinned before the
latest matches, and the season and venue splits are short summaries.

SYNTHETIC TEST DATA: made-up ratings and dates, and the fictional clubs of test_teams.
"""

from footy import teams
from test_teams import build_world, write_primary


def point(day: str, rating: float, comp: str = "E0") -> tuple:
    """A strength-series entry as the index holds it (date, competition, after, before, ...)."""
    return (day, comp, rating, rating - 1, 1500.0, "matches", 1)


def test_thin_series_keeps_the_latest_matches_and_one_point_a_month_before():
    # 30 months of two matches each, then the latest SERIES_FULL_MATCHES in full.
    early = [
        point(f"{2020 + m // 12}-{m % 12 + 1:02d}-{d:02d}", 1500 + m + d / 100)
        for m in range(30)
        for d in (5, 20)
    ]
    late = [point(f"2023-{7 + i // 28:02d}-{i % 28 + 1:02d}", 1600 + i) for i in range(70)]
    series = early + late
    shown = teams.thin_series(series)
    n = teams.SERIES_FULL_MATCHES
    assert shown[-n:] == series[-n:]
    before = shown[:-n]
    held_before = series[:-n]
    # One per month, each the month's last counted match, in date order.
    months = sorted({x[0][:7] for x in held_before})
    assert [x[0][:7] for x in before] == months
    for x in before:
        assert x == [y for y in held_before if y[0][:7] == x[0][:7]][-1]
    assert teams.thin_series(series[:n]) == series[:n]  # short series are untouched


def test_profile_series_is_thinned_with_full_on_request(
    client, migrated_engine, tmp_path, monkeypatch
):
    write_primary(tmp_path, monkeypatch)
    build_world(migrated_engine, tmp_path)
    monkeypatch.setattr(teams, "SERIES_FULL_MATCHES", 3)
    p = client.get("/api/teams/eng-testtown").json()["strength"]
    full = client.get("/api/teams/eng-testtown?series=full").json()["strength"]
    assert len(full["series"]) == 7 == full["series_points"]["counted_matches"]
    assert full["series_points"]["every_match_from"] is None
    assert p["series"][-3:] == full["series"][-3:]
    assert len(p["series"]) == p["series_points"]["shown"] < 7
    assert p["series_points"]["every_match_from"] == full["series"][-3]["date"]
    assert p["peak"] == full["peak"] and p["current"] == full["current"]
    assert client.get("/api/teams/eng-testtown?series=all").status_code == 422


def test_season_and_venue_splits_are_short_summaries(
    client, migrated_engine, tmp_path, monkeypatch
):
    write_primary(tmp_path, monkeypatch)
    build_world(migrated_engine, tmp_path)
    p = client.get("/api/teams/eng-testtown").json()
    short = set(teams.SHORT_SUMMARY)
    for season in p["by_season"]:
        assert set(season["summary"]) == short
        assert all(set(x) == short for x in season["by_type"].values())
    for t in p["by_type"].values():
        assert all(set(x) == short for x in t["by_venue"].values())
        assert "per_match" in t and "rates" in t  # the type itself stays in full
    assert "per_match" in p["summary"] and "per_match" in p["by_venue"]["H"]
    assert all("per_match" in c["summary"] for c in p["by_competition"])
    # The counts agree with the full summaries of the same matches.
    league = p["by_type"]["league"]
    assert sum(league["by_venue"][v]["played"] for v in "HAN") == league["played"]
    assert sum(s["summary"]["won"] for s in p["by_season"]) == p["summary"]["won"]
