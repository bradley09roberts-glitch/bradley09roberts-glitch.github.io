"""Per-team statistics across competitions (footy.teams).

SYNTHETIC TEST DATA: fictional clubs, made-up scores, statistics, probabilities and prices.
Nothing here is a real result, a real prediction or a real price.
"""

import json
from datetime import UTC, date, datetime, timedelta

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from footy import teams
from footy.api import routes
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.models import EuroMatch, Match, Prediction
from footy.publish import FixtureIn, publish, settle
from footy.reference import DIVISIONS, AliasRow, TeamRow

NOW = datetime(2026, 10, 21, 12, 0, tzinfo=UTC)

TEAMS = [
    TeamRow("eng-testtown", "Testtown", "ENG"),
    TeamRow("eng-sampleton", "Sampleton", "ENG"),
    TeamRow("eng-mockford", "Mockford", "ENG"),
    TeamRow("sco-fakeburgh", "Fakeburgh", "SCO"),
    TeamRow("sco-mockhill", "Möckhill", "SCO"),
    TeamRow("nld-voorbeeld", "Voorbeeld", "NLD"),
    TeamRow("eng-nomatches", "Nomatches", "ENG"),
]
ALIASES = [AliasRow("football-data", "Test Town FC", "eng-testtown")]

# Prices planted next to the data (fd_odds and a model + market record). They must never
# appear in anything footy.teams returns.
PLANTED_PRICES = (41.97, 29.83)

STAT_COLS = (
    "home_shots",
    "away_shots",
    "home_sot",
    "away_sot",
    "home_corners",
    "away_corners",
    "home_fouls",
    "away_fouls",
    "home_yellow",
    "away_yellow",
    "home_red",
    "away_red",
)


def league(
    division, season, day, home, away, hg, ag, ht=None, stats=None, meeting=1, **extra
) -> Match:
    """A league match. `stats` lists STAT_COLS in order (None: the file had no statistics)."""
    values = dict(zip(STAT_COLS, stats, strict=True)) if stats else {}
    return Match(
        division=division,
        season=season,
        match_date=date.fromisoformat(day),
        home_team=home,
        away_team=away,
        meeting=meeting,
        home_goals=hg,
        away_goals=ag,
        result="H" if hg > ag else "D" if hg == ag else "A",
        ht_home_goals=None if ht is None else ht[0],
        ht_away_goals=None if ht is None else ht[1],
        source_row=1,
        first_raw_file_id=1,
        last_raw_file_id=1,
        **values,
        **extra,
    )


def euro(comp, season, stage, day, home, away, hg, ag, status="played", **extra) -> EuroMatch:
    return EuroMatch(
        competition=comp,
        season=season,
        stage=stage,
        match_date=date.fromisoformat(day),
        home_team=home,
        away_team=away,
        home_goals=hg,
        away_goals=ag,
        status=status,
        source="openfootball",
        **extra,
    )


def pub(engine, tmp_path, fx: FixtureIn, model: str, published: datetime, lam=(1.4, 1.1)):
    mp = markets_from_grid(poisson_grid([lam[0]], [lam[1]]), [lam[0]], [lam[1]])
    (pid,) = publish(
        engine,
        [fx],
        mp,
        model_name=model,
        model_version="v-test",
        training_cutoff_utc=published - timedelta(hours=1),
        data_freshness={},
        now=published,
        records_dir=tmp_path / "records",
    )
    return pid


def at(day: str, hour: int = 14) -> datetime:
    d = date.fromisoformat(day)
    return datetime(d.year, d.month, d.day, hour, tzinfo=UTC)


def write_primary(tmp_path, monkeypatch, domestic=("base_m",)) -> dict:
    """A temporary primary_models.json (the real file is never touched): base_m for leagues,
    uefa_m for UEFA."""
    path = tmp_path / "primary_models.json"
    path.write_text(json.dumps({"domestic": list(domestic), "uefa": ["uefa_m"]}))
    monkeypatch.setattr(routes, "PRIMARY_MODELS_PATH", path)
    return routes.model_periods()


@pytest.fixture
def primary(tmp_path, monkeypatch):
    return write_primary(tmp_path, monkeypatch)


@pytest.fixture
def world(migrated_engine, tmp_path, primary):
    return build_world(migrated_engine, tmp_path)


def build_world(e, tmp_path) -> dict:
    """Testtown: five league matches over two seasons (one more flagged as a bare result),
    three UEFA matches (one a neutral final after extra time and penalties, one awarded) and a
    scheduled UEFA fixture. Fakeburgh and Möckhill: three Scottish meetings in one season, two
    of them the same home and away teams (meetings 1 and 2). Returns the match and record ids."""
    with Session(e) as s:
        sync_reference(s, TEAMS, ALIASES)
        s.commit()
    with e.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-01', 'p')"
            )
        )
    tt, sa, mo = "eng-testtown", "eng-sampleton", "eng-mockford"
    fb, mh, vb = "sco-fakeburgh", "sco-mockhill", "nld-voorbeeld"
    with Session(e) as s:
        m = {
            # Testtown behind at half time, then won.
            "m1": league(
                "E0",
                "2526",
                "2025-08-16",
                tt,
                sa,
                2,
                1,
                (0, 1),
                (12, 8, 5, 3, 6, 2, 10, 12, 1, 2, 0, 1),
                referee="A Referee",
            ),
            "m2": league(
                "E0",
                "2526",
                "2025-08-23",
                mo,
                tt,
                0,
                0,
                (0, 0),
                (9, 11, 2, 4, 3, 5, 14, 9, 3, 1, 0, 0),
            ),
            # Testtown ahead at half time (away), then lost.
            "m3": league(
                "E0",
                "2526",
                "2025-09-13",
                sa,
                tt,
                3,
                1,
                (0, 1),
                (15, 7, 6, 2, 7, 3, 8, 13, 0, 4, 0, 1),
            ),
            # A bare result: flagged, never counted.
            "m4": league(
                "E0", "2526", "2025-12-20", tt, mo, 1, 1, model_exclusion="result_without_stats"
            ),
            "m5": league(
                "E0",
                "2627",
                "2026-08-15",
                tt,
                mo,
                4,
                0,
                (2, 0),
                (18, 4, 9, 1, 8, 1, 11, 10, 2, 2, 0, 0),
                fd_home_xg=2.1,
                fd_away_xg=0.4,
            ),
            "m6": league(
                "E0",
                "2627",
                "2026-08-22",
                sa,
                tt,
                1,
                2,
                (1, 1),
                (10, 14, 3, 6, 4, 6, 12, 11, 2, 1, 0, 0),
                kickoff_utc=at("2026-08-22", 11),
            ),
            # Repeat meetings: Fakeburgh host Möckhill twice in 2025/26 (D-022).
            "s1": league(
                "SC0",
                "2526",
                "2025-09-01",
                fb,
                mh,
                1,
                0,
                (1, 0),
                (5, 5, 2, 2, 3, 3, 9, 9, 1, 1, 0, 0),
            ),
            "s2": league(
                "SC0",
                "2526",
                "2025-11-01",
                mh,
                fb,
                0,
                1,
                (0, 0),
                (6, 6, 2, 2, 3, 3, 9, 9, 1, 1, 0, 0),
            ),
            "s3": league(
                "SC0",
                "2526",
                "2026-01-02",
                fb,
                mh,
                2,
                2,
                (1, 2),
                (7, 8, 4, 5, 2, 9, 13, 8, 3, 0, 1, 0),
                meeting=2,
            ),
            "u1": euro("UCL", "2526", "League MD1", "2025-10-01", tt, vb, 1, 1),
            "u2": euro(
                "UCL",
                "2526",
                "Final",
                "2026-05-30",
                tt,
                vb,
                1,
                1,
                neutral=True,
                extra_time=True,
                penalties=True,
            ),
            "u3": euro(
                "UECL",
                "2526",
                "League MD2",
                "2025-11-06",
                vb,
                tt,
                0,
                3,
                status="awarded",
                model_exclusion="awarded",
            ),
            "u4": euro(
                "UCL",
                "2627",
                "League MD3",
                "2026-10-28",
                vb,
                tt,
                None,
                None,
                status="scheduled",
                kickoff_utc=at("2026-10-28", 20),
            ),
        }
        s.add_all(m.values())
        s.flush()
        ids = {k: v.id for k, v in m.items()}
        for price, sel in zip(PLANTED_PRICES, ("H", "A"), strict=True):
            s.execute(
                text(
                    "INSERT INTO fd_odds (match_id, bookmaker, market_key, selection, phase, "
                    "price, quote_time_basis, source_column, raw_file_id) VALUES "
                    "(:m, 'B365', '1X2', :sel, 'closing', :p, 'kickoff', 'B365C', 1)"
                ),
                {"m": ids["m6"], "sel": sel, "p": price},
            )
        s.commit()

    # Pre-match records. m6: an unlisted model first, then the model of record, then a later
    # edition of it; only the model of record's first edition is the record of record.
    m6 = FixtureIn("E0", "2627", date(2026, 8, 22), at("2026-08-22", 11), sa, tt)
    other = pub(e, tmp_path, m6, "other_m", at("2026-08-20"))
    p_m6 = pub(e, tmp_path, m6, "base_m", at("2026-08-20", 15))
    m6_tilt = FixtureIn(
        "E0",
        "2627",
        date(2026, 8, 22),
        at("2026-08-22", 11),
        sa,
        tt,
        features={
            "model_params": {"base": "base_m", "w_1x2": 1.0, "w_ou": 1.0},
            "market_prices_used": {
                "p_H": 0.4,
                "p_D": 0.3,
                "p_A": 0.3,
                "B365H": PLANTED_PRICES[0],  # SYNTHETIC price-like field
                "provenance": {"planted_price": PLANTED_PRICES[1]},
            },
        },
    )
    tilt = pub(e, tmp_path, m6_tilt, "tilt_m", at("2026-08-20", 16))
    # s3: the second meeting's date only.
    p_s3 = pub(
        e,
        tmp_path,
        FixtureIn("SC0", "2526", date(2026, 1, 2), None, fb, mh),
        "base_m",
        at("2026-01-01"),
    )
    # u4: a scheduled UEFA fixture; n1: a league fixture with no stored match yet.
    p_u4 = pub(
        e,
        tmp_path,
        FixtureIn("UCL", "2627", date(2026, 10, 28), at("2026-10-28", 20), vb, tt),
        "uefa_m",
        at("2026-10-20"),
        lam=(1.0, 1.6),
    )
    p_n1 = pub(
        e,
        tmp_path,
        FixtureIn("E0", "2627", date(2026, 11, 1), at("2026-11-01", 15), tt, sa),
        "base_m",
        at("2026-10-20"),
    )
    assert settle(e, now=at("2026-08-23"))["settled"] >= 3
    return {
        "ids": ids,
        "pred": {"other": other, "m6": p_m6, "tilt": tilt, "s3": p_s3, "u4": p_u4, "n1": p_n1},
    }


def session(engine) -> Session:
    return Session(engine)


def log_of(engine, team):
    with session(engine) as s:
        return teams.team_log(s, team)


def by_ref(rows, ids, key, table="matches"):
    return next(r for r in rows if r["ref"] == f"{table}:{ids[key]}")


# --- Competitions ----------------------------------------------------------------------------


def test_competition_type_is_one_mapping(monkeypatch):
    from footy.modeling.euro import EURO_COMPS

    assert set(teams.EURO_COMPETITIONS) == set(EURO_COMPS)
    assert all(teams.competition_type(c) == "league" for c in DIVISIONS)
    assert all(teams.competition_type(c) == "europe" for c in EURO_COMPS)
    assert teams.competition_type("FAC") is None
    # A cup is added by naming it (and adding its source); nothing else changes.
    monkeypatch.setitem(teams.CUP_COMPETITIONS, "FAC", "FA Cup")
    assert teams.competition_type("FAC") == "cup"
    assert teams.competition_name("FAC") == "FA Cup"
    assert teams.competition_name("UCLQ") == "Champions League qualifying"
    assert teams.competition_name("E0") == "Premier League"


# --- The match log ---------------------------------------------------------------------------


def test_log_rows_are_from_the_teams_side(migrated_engine, world):
    ids = world["ids"]
    log = log_of(migrated_engine, "eng-testtown")
    assert [r["date"] for r in log.rows] == sorted(r["date"] for r in log.rows)
    assert len(log.rows) == 9 and [f["ref"] for f in log.fixtures] == [f"euro_matches:{ids['u4']}"]
    assert all(set(r) == {*teams.LOG_FIELDS, "elo", "prediction"} for r in log.rows)
    home = by_ref(log.rows, ids, "m1")
    assert (home["venue"], home["goals_for"], home["goals_against"], home["result"]) == (
        "H",
        2,
        1,
        "W",
    )
    assert (home["ht_goals_for"], home["ht_goals_against"], home["ht_result"]) == (0, 1, "L")
    assert (home["shots_for"], home["shots_against"], home["red_cards_against"]) == (12, 8, 1)
    assert home["referee"] == "A Referee" and home["competition_type"] == "league"
    assert home["opponent_name"] == "Sampleton" and home["season_label"] == "2025/26"
    away = by_ref(log.rows, ids, "m3")  # Sampleton 3-1 Testtown: everything swaps
    assert (away["venue"], away["goals_for"], away["goals_against"], away["result"]) == (
        "A",
        1,
        3,
        "L",
    )
    assert (away["shots_for"], away["shots_against"]) == (7, 15)
    assert (away["yellow_cards_for"], away["yellow_cards_against"]) == (4, 0)
    assert (away["ht_goals_for"], away["ht_goals_against"]) == (1, 0)
    xg = by_ref(log.rows, ids, "m5")
    assert (xg["fd_xg_for"], xg["fd_xg_against"]) == (2.1, 0.4)
    final = by_ref(log.rows, ids, "u2", "euro_matches")
    assert final["venue"] == "N" and final["neutral"] and final["result"] == "D"
    assert final["extra_time"] and final["penalties"] and final["stage"] == "Final"
    # UEFA sources carry no statistics: null, never zero.
    assert all(
        final[f"{s}_{side}"] is None for s in teams.STAT_NAMES for side in ("for", "against")
    )
    assert final["ht_goals_for"] is None and final["competition_type"] == "europe"
    awarded = by_ref(log.rows, ids, "u3", "euro_matches")
    assert awarded["excluded"] == "awarded" and awarded["status"] == "awarded"
    assert by_ref(log.rows, ids, "m4")["excluded"] == "result_without_stats"


def test_repeat_meetings_stay_separate(migrated_engine, world):
    ids = world["ids"]
    log = log_of(migrated_engine, "sco-fakeburgh")
    assert [r["ref"] for r in log.rows] == [f"matches:{ids[k]}" for k in ("s1", "s2", "s3")]
    first, second = by_ref(log.rows, ids, "s1"), by_ref(log.rows, ids, "s3")
    assert (first["meeting"], second["meeting"]) == (1, 2)
    assert (first["result"], second["result"]) == ("W", "D")
    sm = teams.summarise(log.rows)
    assert (sm["played"], sm["won"], sm["drawn"], sm["lost"]) == (3, 2, 1, 0)
    assert (sm["goals_for"], sm["goals_against"]) == (4, 2)


# --- Summaries -------------------------------------------------------------------------------


def test_summary_counts_only_unflagged_matches(migrated_engine, world):
    rows = log_of(migrated_engine, "eng-testtown").rows
    sm = teams.summarise(rows)
    # Counted: m1 W 2-1, m2 D 0-0, m3 L 1-3, m5 W 4-0, m6 W 2-1, u1 D 1-1, u2 D 1-1.
    assert (sm["played"], sm["won"], sm["drawn"], sm["lost"], sm["left_out"]) == (7, 3, 3, 1, 2)
    assert (sm["goals_for"], sm["goals_against"], sm["goal_difference"]) == (11, 7, 4)
    assert (sm["league_matches"], sm["league_points"], sm["points_per_game"]) == (5, 10, 2.0)
    pm = sm["per_match"]
    assert pm["goals"] == {"for": round(11 / 7, 3), "against": 1.0, "matches": 7}
    # Statistics only from the five league matches that have them.
    assert pm["shots"] == {
        "for": (12 + 11 + 7 + 18 + 14) / 5,
        "against": (8 + 9 + 15 + 4 + 10) / 5,
        "matches": 5,
    }
    assert pm["fd_xg"] == {"for": 2.1, "against": 0.4, "matches": 1}
    r = sm["rates"]
    assert r["clean_sheets"] == {"matches": 2, "rate": round(2 / 7, 4)}
    assert r["failed_to_score"]["matches"] == 1
    assert r["both_teams_scored"]["matches"] == 5
    assert r["over_2_5_goals"]["matches"] == 4
    ht = sm["half_time"]
    assert ht["matches_with_half_time_score"] == 5
    assert ht["behind_at_half_time"] == {
        "matches": 1,
        "won": 1,
        "drawn": 0,
        "lost": 0,
        "points_won": 3,
    }
    assert ht["ahead_at_half_time"] == {
        "matches": 2,
        "won": 1,
        "drawn": 0,
        "lost": 1,
        "points_dropped": 3,
    }


def test_half_time_turnarounds_weigh_draws():
    """Behind at half time: a win is 3 points won, a draw 1. Ahead: a draw drops 2, a loss 3.
    Level at half time is neither."""

    def row(ft: tuple[int, int], ht: tuple[int, int]) -> dict:
        r = dict.fromkeys(teams.LOG_FIELDS)
        r.update(competition_type="league", venue="H")
        r.update(teams._goals(*ft, home=True))
        r.update(teams._goals(*ht, home=True, prefix="ht_"))
        return r

    behind = [row((2, 1), (0, 1)), row((1, 1), (0, 1)), row((1, 1), (0, 2)), row((0, 2), (0, 1))]
    ahead = [row((2, 0), (1, 0)), row((1, 1), (1, 0)), row((1, 2), (1, 0))]
    ht = teams.summarise([*behind, *ahead, row((3, 3), (1, 1))])["half_time"]
    assert ht["matches_with_half_time_score"] == 8
    assert ht["behind_at_half_time"] == {
        "matches": 4,
        "won": 1,
        "drawn": 2,
        "lost": 1,
        "points_won": 5,
    }
    assert ht["ahead_at_half_time"] == {
        "matches": 3,
        "won": 1,
        "drawn": 1,
        "lost": 1,
        "points_dropped": 5,
    }


def test_form_and_streaks(migrated_engine, world):
    rows = log_of(migrated_engine, "eng-testtown").rows
    assert teams.form_string(rows, 10) == "WDLDDWW"
    assert teams.form_string(rows, 5) == "LDDWW"
    lg = [r for r in rows if r["competition_type"] == "league"]
    assert teams.form_string(lg, 10) == "WDLWW"
    st = teams.streaks(rows)
    assert st["wins"] == {"matches": 2, "since": "2026-08-15"}
    assert st["unbeaten"] == {"matches": 4, "since": "2025-10-01"}
    assert st["scored"]["matches"] == 5 and st["losses"]["matches"] == 0
    assert st["without_a_win"] == {"matches": 0, "since": None}


# --- Records of record (D-026) ---------------------------------------------------------------


def test_records_attach_to_their_own_match(migrated_engine, world, primary):
    ids, pred = world["ids"], world["pred"]
    with session(migrated_engine) as s:
        rows = teams.team_matches(s, "eng-testtown", periods=primary)
        m6 = by_ref(rows, ids, "m6")["prediction"]
        # The first edition of the first listed model, not the earlier unlisted one.
        assert m6["id"] == pred["m6"] and m6["model_name"] == "base_m"
        assert m6["basis"] == "model_only" and m6["settled"] is True
        # Testtown were away: the pick (home) is a loss for them, and Testtown won.
        assert m6["pick"] == "H" and m6["pick_for_team"] == "L"
        assert m6["correct"]["1X2"] is False
        x = m6["probabilities"]["1X2"]
        assert m6["team"] == {"win": x["A"], "draw": x["D"], "loss": x["H"]}
        assert set(m6["probabilities"]) <= set(teams.SUMMARY_MARKETS)
        assert all(r["prediction"] is None for r in rows if r["ref"] != f"matches:{ids['m6']}")
        # Repeat meetings: the record dated 2 January belongs to the second meeting only.
        fb = teams.team_matches(s, "sco-fakeburgh", periods=primary)
        assert by_ref(fb, ids, "s3")["prediction"]["id"] == pred["s3"]
        assert by_ref(fb, ids, "s1")["prediction"] is None
        # Seen from the away side, the same record reads the other way round.
        mh = teams.team_matches(s, "sco-mockhill", periods=primary)
        rec = by_ref(mh, ids, "s3")["prediction"]
        assert rec["team"]["win"] == rec["probabilities"]["1X2"]["A"]


def test_upcoming_fixtures_carry_published_records(migrated_engine, world, primary):
    ids, pred = world["ids"], world["pred"]
    with session(migrated_engine) as s:
        pr = teams.team_profile(s, "eng-testtown", periods=primary, now=NOW)
    up = pr["upcoming"]
    assert [f["ref"] for f in up] == [f"euro_matches:{ids['u4']}", f"prediction:{pred['n1']}"]
    assert all(set(f) == set(teams.FIXTURE_FIELDS) for f in up)
    ucl, league_fx = up
    assert ucl["venue"] == "A" and ucl["prediction"]["id"] == pred["u4"]
    assert ucl["prediction"]["model_name"] == "uefa_m" and not ucl["awaiting_result"]
    assert league_fx["source"] == "pre-match record" and league_fx["venue"] == "H"
    assert league_fx["prediction"]["pick_for_team"] in ("W", "D", "L")
    # Once its kickoff has passed with no result stored, it is awaiting its result; a week
    # later it drops off.
    with session(migrated_engine) as s:
        later = teams.team_profile(s, "eng-testtown", periods=primary, now=at("2026-10-29"))
        gone = teams.team_profile(s, "eng-testtown", periods=primary, now=at("2026-11-05"))
    assert later["upcoming"][0]["awaiting_result"] is True
    assert [f["ref"] for f in gone["upcoming"]] == [f"prediction:{pred['n1']}"]


# --- Profile ---------------------------------------------------------------------------------


def test_profile(migrated_engine, world, primary):
    with session(migrated_engine) as s:
        pr = teams.team_profile(s, "eng-testtown", periods=primary, now=NOW)
        assert teams.team_profile(s, "eng-unknown", periods=primary) is None
        empty = teams.team_profile(s, "eng-nomatches", periods=primary, now=NOW)
    t = pr["team"]
    assert (t["name"], t["country"]) == ("Testtown", "ENG")
    assert t["division"] == {
        "code": "E0",
        "name": "Premier League",
        "tier": 1,
        "season": "2627",
        "season_label": "2026/27",
        "current": True,
    }
    # UECL's only season (2025/26) is behind the latest UEFA season held, so it isn't current.
    assert [c["code"] for c in t["competitions_this_season"]] == ["E0", "UCL"]
    assert pr["matches"] == {
        "logged": 9,
        "counted": 7,
        "left_out": 2,
        "first_date": "2025-08-16",
        "last_date": "2026-08-22",
    }
    assert pr["by_type"]["league"]["played"] == 5 and pr["by_type"]["europe"]["played"] == 2
    assert pr["by_type"]["cup"]["played"] == 0
    assert pr["by_type"]["league"]["by_venue"]["H"]["played"] == 2
    assert pr["by_venue"]["N"]["played"] == 1
    comps = {c["competition"]: c for c in pr["by_competition"]}
    assert list(comps) == ["E0", "UCL", "UECL"]
    assert comps["UECL"]["summary"]["played"] == 0 and comps["UECL"]["summary"]["left_out"] == 1
    seasons = {x["season"]: x for x in pr["by_season"]}
    assert list(seasons) == ["2627", "2526"]
    assert seasons["2627"]["summary"]["won"] == 2 and set(seasons["2627"]["by_type"]) == {"league"}
    assert seasons["2526"]["summary"]["played"] == 5
    assert seasons["2526"]["by_type"]["europe"]["played"] == 2
    assert pr["form"]["last_10"] == "WDLDDWW" and pr["form"]["by_type"]["europe"]["last_5"] == "DD"
    assert pr["streaks"]["all"]["wins"]["matches"] == 2
    st = pr["strength"]
    assert st["current"] is not None and len(st["series"]) == 7  # counted matches only
    assert st["rank"] is not None and st["ranked_teams"] == 6
    # A team with no matches still has a profile, with empty summaries.
    assert empty["summary"]["played"] == 0 and empty["team"]["division"] is None
    assert empty["strength"]["current"] is None and empty["form"]["last_5"] == ""


def test_only_type(migrated_engine, world, primary):
    with session(migrated_engine) as s:
        pr = teams.team_profile(s, "eng-testtown", periods=primary, now=NOW, only_type="europe")
    assert list(pr["by_type"]) == ["europe"]
    assert pr["summary"]["played"] == 2 and pr["matches"]["left_out"] == 1
    assert {f["competition_type"] for f in pr["upcoming"]} == {"europe"}
    assert {x["competition"] for x in pr["strength"]["series"]} == {"UCL"}
    assert "Only europe matches." in teams.profile_text(pr)


# --- Strength series -------------------------------------------------------------------------


def test_strength_reuses_the_pan_european_elo(migrated_engine, world):
    """Same settings and the same ratings as footy.modeling.euro.PanEuropeanElo, which is
    fitted on the same matches in the same order."""
    from footy.modeling.euro import PanEuropeanElo, load_combined

    e = PanEuropeanElo()
    assert (e.k_domestic, e.k_euro, e.home) == (
        teams.ELO_K["league"],
        teams.ELO_K["europe"],
        teams.ELO_HOME,
    )
    assert e.START == teams.ELO_START
    for comp in [*DIVISIONS, *teams.EURO_COMPETITIONS, "OTHER"]:
        assert teams.elo_start(comp) == e._start(comp)
    hist = load_combined(migrated_engine)
    e.fit(hist, hist.date.max())
    with session(migrated_engine) as s:
        idx = teams.build_index(s)
    assert set(idx.rating) == set(e.r)
    for team, r in e.r.items():
        assert idx.rating[team] == pytest.approx(r, abs=1e-9)
    # Each point is the rating after a counted match; the log shows before and after.
    with session(migrated_engine) as s:
        log = teams.team_log(s, "eng-testtown")
        teams.attach_elo(log, idx)
    counted = teams.counted(log.rows)
    assert all(r["elo"] is not None for r in counted)
    assert all(r["elo"] is None for r in log.rows if r["excluded"])
    assert counted[0]["elo"]["before"] == teams.ELO_START["core_top"]
    assert counted[-1]["elo"]["after"] == round(idx.rating["eng-testtown"], 1)
    for prev, nxt in zip(counted, counted[1:], strict=False):
        assert nxt["elo"]["before"] == prev["elo"]["after"]


def test_index_is_cached_until_the_data_changes(migrated_engine, world):
    with session(migrated_engine) as s:
        first = teams.teams_index(s)
        assert teams.teams_index(s) is first
    # A new match and an in-place UEFA result each change the fingerprint.
    with Session(migrated_engine) as s:
        s.add(league("E0", "2627", "2026-09-01", "eng-mockford", "eng-sampleton", 2, 0))
        s.commit()
    with session(migrated_engine) as s:
        second = teams.teams_index(s)
    assert second is not first
    assert second.entries["eng-mockford"]["matches"] == first.entries["eng-mockford"]["matches"] + 1
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "UPDATE euro_matches SET home_goals = 0, away_goals = 2, status = 'played' "
                "WHERE id = :i"
            ),
            {"i": world["ids"]["u4"]},
        )
    with session(migrated_engine) as s:
        third = teams.teams_index(s)
    assert third is not second
    assert third.entries["eng-testtown"]["form_last_5"].endswith("W")


# --- Teams list, search ----------------------------------------------------------------------


def test_teams_list_and_search(migrated_engine, world):
    with session(migrated_engine) as s:
        every = teams.teams_list(s)
        # Teams with no stored match or fixture aren't listed.
        assert "eng-nomatches" not in {t["key"] for t in every}
        assert [t["key"] for t in every] == [
            "sco-fakeburgh",
            "eng-mockford",
            "sco-mockhill",
            "eng-sampleton",
            "eng-testtown",
            "nld-voorbeeld",
        ]
        tt = next(t for t in every if t["key"] == "eng-testtown")
        assert tt["form_last_5"] == "LDDWW" and tt["matches"] == 7
        assert tt["matches_by_type"] == {"league": 5, "europe": 2, "cup": 0}
        assert tt["division"]["code"] == "E0" and tt["elo_rank"] is not None
        vb = next(t for t in every if t["key"] == "nld-voorbeeld")
        assert vb["division"] is None
        assert [c["code"] for c in vb["competitions_this_season"]] == ["UCL"]
        sco = teams.teams_list(s, ["SC0"])
        # SC0's only season is its latest held, so it is "this season" for its clubs.
        assert [t["key"] for t in sco] == ["sco-fakeburgh", "sco-mockhill"]
        assert sco[0]["division"]["current"] is True
        assert [t["key"] for t in teams.teams_list(s, q="mockhill")] == ["sco-mockhill"]
        assert [t["key"] for t in teams.teams_list(s, ["UCL"], q="test")] == ["eng-testtown"]
        assert teams.find_teams(s, "eng-testtown") == ["eng-testtown"]
        assert teams.find_teams(s, "TESTTOWN") == ["eng-testtown"]
        assert teams.find_teams(s, "test town fc") == ["eng-testtown"]  # an alias
        assert teams.find_teams(s, "mock") == ["eng-mockford", "sco-mockhill"]
        assert teams.find_teams(s, "nobody") == []


# --- Match page ------------------------------------------------------------------------------


def test_match_page_block(migrated_engine, world):
    ids, pred = world["ids"], world["pred"]
    with session(migrated_engine) as s:
        names = teams.team_names(s)
        p = s.get(Prediction, pred["s3"])
        block = teams.match_page(s, p, names)
        m6 = teams.match_page(s, s.get(Prediction, pred["m6"]), names)
        upcoming = teams.match_page(s, s.get(Prediction, pred["u4"]), names)
    assert block["as_of"] == "2026-01-02"
    # Only matches before this one: meetings 1 (home) and the reverse fixture, newest first.
    h2h = block["head_to_head"]
    assert [m["ref"] for m in h2h["meetings"]] == [f"matches:{ids[k]}" for k in ("s2", "s1")]
    assert h2h["meetings"][0]["home_team"] == "sco-mockhill"
    assert (h2h["meetings"][0]["home_goals"], h2h["meetings"][0]["away_goals"]) == (0, 1)
    assert (h2h["home_side_won"], h2h["drawn"], h2h["away_side_won"]) == (2, 0, 0)
    assert block["home"]["form"]["last_10"] == "WW"
    # The statistics are this meeting's (2-2), not the first meeting's.
    ms = block["match_stats"]
    assert ms["ref"] == f"matches:{ids['s3']}"
    assert ms["home"]["goals"] == 2 and ms["away"]["goals"] == 2
    assert (ms["home"]["shots"], ms["away"]["shots"]) == (7, 8)
    assert (ms["home"]["half_time_goals"], ms["away"]["half_time_goals"]) == (1, 2)
    # Testtown (away at m6): form and averages by type, from before 22 August 2026.
    away = m6["away"]
    assert away["team"] == "eng-testtown"
    assert away["form"] == {"last_10": "WDLDDW", "by_type": {"league": "WDLW", "europe": "DD"}}
    assert away["per_match"]["europe"]["matches"] == 2
    assert away["per_match"]["europe"]["averages"]["shots"]["matches"] == 0
    assert away["per_match"]["league"]["averages"]["shots"]["matches"] == 4
    assert (m6["match_stats"]["home"]["shots"], m6["match_stats"]["away"]["shots"]) == (10, 14)
    # Not played yet: no statistics.
    assert upcoming["match_stats"] is None
    meetings = upcoming["head_to_head"]["meetings"]  # newest first; the awarded one flagged
    assert [m["competition"] for m in meetings] == ["UCL", "UECL", "UCL"]
    assert meetings[0]["neutral"] is True and meetings[0]["penalties"] is True
    assert meetings[1]["excluded"] == "awarded"
    assert upcoming["head_to_head"]["drawn"] == 2


def test_match_page_leaves_out_a_match_brought_forward(migrated_engine, world, tmp_path):
    """A league match played before the date its record was published for (brought forward;
    a pairing that meets once a season keeps its record, as settlement does). Its own result
    must not be part of what the page shows from before it."""
    mo, tt = "eng-mockford", "eng-testtown"
    fx = FixtureIn("E0", "2627", date(2026, 9, 12), at("2026-09-12"), mo, tt)
    pid = pub(migrated_engine, tmp_path, fx, "base_m", at("2026-09-01"))
    with Session(migrated_engine) as s:
        s.add(
            league(
                "E0",
                "2627",
                "2026-09-05",
                mo,
                tt,
                3,
                0,
                (1, 0),
                (16, 6, 7, 1, 8, 2, 10, 12, 1, 3, 0, 1),
            )
        )
        s.commit()
    with session(migrated_engine) as s:
        block = teams.match_page(s, s.get(Prediction, pid), teams.team_names(s))
    assert block["as_of"] == "2026-09-05"
    # Testtown's form before it, unchanged by its 0-3 defeat in this very match.
    assert block["away"]["form"]["last_10"] == "WDLDDWW"
    assert block["home"]["per_match"]["all"]["matches"] == 2  # m2 and m5 (m4 is flagged)
    h2h = block["head_to_head"]
    assert all(m["date"] < "2026-09-05" for m in h2h["meetings"])
    assert (h2h["home_side_won"], h2h["drawn"], h2h["away_side_won"]) == (0, 1, 1)
    # The match itself is still this record's match: its statistics are shown.
    assert (block["match_stats"]["home"]["goals"], block["match_stats"]["home"]["shots"]) == (3, 16)


# --- CSV and CLI -----------------------------------------------------------------------------


def test_csv_and_cli(migrated_engine, world, primary, monkeypatch, tmp_path, capsys):
    from footy import cli, db

    monkeypatch.setattr(db, "get_engine", lambda: migrated_engine)
    out = tmp_path / "log.csv"
    assert cli.main(["team-stats", "Testtown", "--csv", str(out)]) == 0
    printed = capsys.readouterr().out
    assert printed.startswith("Testtown (eng-testtown, ENG) - Premier League 2026/27")
    assert "All competitions" in printed and "Wrote 9 matches" in printed
    lines = out.read_text().splitlines()
    assert len(lines) == 10 and lines[0].split(",")[: len(teams.LOG_FIELDS)] == list(
        teams.LOG_FIELDS
    )
    assert lines[1].startswith(f"matches:{world['ids']['m1']},football-data,2025-08-16")
    assert cli.main(["team-stats", "eng-testtown", "--type", "europe"]) == 0
    assert "Only europe matches." in capsys.readouterr().out
    assert cli.main(["team-stats", "mock"]) == 1
    assert "matches 2 teams" in capsys.readouterr().out
    assert cli.main(["team-stats", "nobody"]) == 1


# --- No prices -------------------------------------------------------------------------------


def test_no_price_reaches_any_output(migrated_engine, world, tmp_path, monkeypatch):
    """With the model + market record as the record of record, so its saved prices sit right
    behind what is shown."""
    from footy.snapshot import find_prices

    primary = write_primary(tmp_path, monkeypatch, ("tilt_m", "base_m"))
    with session(migrated_engine) as s:
        shown = teams.team_matches(s, "eng-testtown", periods=primary)
        assert by_ref(shown, world["ids"], "m6")["prediction"]["basis"] == "model_and_market"
        names = teams.team_names(s)
        outputs = {
            "profile": teams.team_profile(s, "eng-testtown", periods=primary, now=NOW),
            "matches": teams.team_matches(s, "eng-testtown", periods=primary),
            "list": teams.teams_list(s),
            "page": teams.match_page(s, s.get(Prediction, world["pred"]["tilt"]), names),
        }
        rows = teams.team_matches(s, "eng-testtown", periods=primary)
    teams.write_csv(rows, tmp_path / "x.csv")
    blob = json.dumps(outputs) + (tmp_path / "x.csv").read_text()
    assert find_prices({"outputs": outputs}) == []
    for p in PLANTED_PRICES:
        assert str(p) not in blob
    assert "B365" not in blob and "planted_price" not in blob and "market_prices_used" not in blob
