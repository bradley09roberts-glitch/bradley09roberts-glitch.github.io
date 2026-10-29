"""Provisional NBA box scores from ESPN game feeds."""

import pandas as pd

from npl.nba import espn

KEYS = ["minutes", "points", "fieldGoalsMade-fieldGoalsAttempted",
        "threePointFieldGoalsMade-threePointFieldGoalsAttempted",
        "freeThrowsMade-freeThrowsAttempted", "rebounds", "assists", "turnovers", "steals",
        "blocks", "offensiveRebounds", "defensiveRebounds", "fouls", "plusMinus"]


def _athlete(i, name, stats, starter=True, dnp=False, pos="G"):
    return {"athlete": {"id": str(i), "displayName": name, "position": {"abbreviation": pos}},
            "starter": starter, "didNotPlay": dnp, "reason": "COACH'S DECISION" if dnp else "",
            "ejected": False, "stats": stats}


def summary(completed=True):
    return {"header": {"id": "401909882", "season": {"year": 2027, "type": 2},
                       "competitions": [{"date": "2026-10-28T23:00Z",
                                         "status": {"type": {"completed": completed}},
                                         "competitors": [
                                             {"homeAway": "home", "score": "121",
                                              "team": {"abbreviation": "CLE"}},
                                             {"homeAway": "away", "score": "111",
                                              "team": {"abbreviation": "WSH"}}]}]},
            "boxscore": {"players": [
                {"team": {"abbreviation": "CLE"}, "statistics": [{"keys": KEYS, "athletes": [
                    _athlete(3908809, "Donovan Mitchell",
                             ["31", "19", "7-15", "2-5", "3-4", "5", "7", "2", "1", "0", "1",
                              "4", "2", "+8"]),
                    _athlete(1, "Bench Guy", [], starter=False, dnp=True)]}]},
                {"team": {"abbreviation": "WSH"}, "statistics": [{"keys": KEYS, "athletes": [
                    _athlete(6583, "Anthony Davis",
                             ["34", "18", "8-18", "2-5", "0-0", "5", "3", "0", "1", "2", "1",
                              "4", "2", "-11"], pos="F")]}]}]}}


def test_feed_becomes_sportsdataverse_layout():
    r = espn.raw_rows(summary()).set_index("athlete_display_name")
    m = r.loc["Donovan Mitchell"]
    assert m.athlete_id == 3908809 and m.team_abbreviation == "CLE"
    assert m.opponent_team_abbreviation == "WSH" and m.home_away == "home"
    assert (m.minutes, m.points, m.rebounds, m.assists) == (31, 19, 5, 7)
    assert (m.field_goals_made, m.field_goals_attempted) == (7, 15)
    assert (m.three_point_field_goals_made, m.three_point_field_goals_attempted) == (2, 5)
    assert m.team_score == 121 and m.season == 2027 and m.season_type == 2
    assert m.game_date == "2026-10-28"  # 19:00 in New York
    assert r.loc["Bench Guy"].did_not_play and pd.isna(r.loc["Bench Guy"].minutes)
    assert (r.box_source == "espn_provisional").all()


def test_unfinished_games_give_no_rows():
    assert espn.raw_rows(summary(completed=False)).empty


def test_missing_games_skips_published_and_unfinished(monkeypatch):
    now = pd.Timestamp("2026-10-29T09:00Z")
    monkeypatch.setattr(espn, "utcnow", lambda: now.to_pydatetime())
    sched = pd.DataFrame({"game_id": [1.0, 2.0, 3.0],
                          "date": ["2026-10-28T23:00Z", "2026-10-28T23:30Z",
                                   "2026-10-29T08:00Z"]})
    assert espn.missing_games(sched, have={"1"}) == ["2"]  # 3 started an hour ago
