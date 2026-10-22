"""Per-team statistics across competitions (docs/teams.md).

Every match a club has played, from the club's side, in one log:

- domestic league matches (`matches`, football-data): score, half-time score, shots, shots on
  target, corners, fouls, yellow and red cards, referee, and football-data's own expected-goals
  figure where it has one;
- UEFA matches (`euro_matches`, openfootball and Wikipedia, D-023): score, stage, extra-time and
  penalties flags, neutral venue. These sources carry no match statistics, so those are null.

A profile summarises the log by competition type, competition, season and venue, with form,
streaks, upcoming fixtures and a descriptive strength series.

Rules:

- Everything is read from the imported data and the saved prediction records; nothing is
  estimated. A statistic the source doesn't have is null, and every average says how many
  matches it covers.
- Matches with a `model_exclusion` (awarded, cancelled, bare results, play-offs against outside
  clubs; D-018, D-022, D-023) are in the log, flagged, and left out of every summary, form
  string, streak and the strength series.
- Every stored match is its own row: repeat meetings in one season (D-022, `matches.meeting`)
  are never merged.
- Scores are regulation time (D-023): a UEFA match that went to extra time or penalties counts
  with its score after 90 minutes, with its flags set. That is a draw for a final, but not
  always for a second leg (it went on because the aggregate was level).
- Bookmakers' prices are never read (D-013, D-024). A pre-match record shows its probabilities,
  never the prices behind a model + market record.

`competition_type` is the one mapping from a competition code to "league", "europe" or "cup".
No domestic cup is held yet; a cup is added by adding its source to MATCH_SOURCES and its
codes to CUP_COMPETITIONS (docs/teams.md).
"""

import csv
import json
import threading
import time
import unicodedata
import weakref
from collections import Counter
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from datetime import UTC, date, datetime, timedelta
from operator import itemgetter
from pathlib import Path
from zoneinfo import ZoneInfo

from sqlalchemy import or_, select, text
from sqlalchemy.orm import Session

from footy.api.routes import RecordsOfRecord, basis_of, iso_utc
from footy.models import Prediction, PredictionSettlement, Team, TeamAlias
from footy.models_of_record import Period
from footy.reference import DIVISIONS
from footy.seasons import season_label

UK = ZoneInfo("Europe/London")

# ---------------------------------------------------------------------------------------------
# Competitions

COMPETITION_TYPES = ("league", "europe", "cup")

# The six UEFA codes in `euro_matches` (footy.modeling.euro.EURO_COMPS).
EURO_COMPETITIONS = {
    "UCL": "Champions League",
    "UEL": "Europa League",
    "UECL": "Conference League",
    "UCLQ": "Champions League qualifying",
    "UELQ": "Europa League qualifying",
    "UECLQ": "Conference League qualifying",
}
# Domestic cups (FA Cup, Copa del Rey, ...). None is held yet; a cup source adds its codes and
# names here, e.g. {"FAC": "FA Cup"}, and its table to MATCH_SOURCES.
CUP_COMPETITIONS: dict[str, str] = {}


def competition_type(code: str) -> str | None:
    """A competition code's type: "league", "europe" or "cup" (None for an unknown code).

    The one place a competition's type is decided: football-data division codes (DIVISIONS)
    are leagues, the six UEFA codes (main competitions and qualifying) are Europe, and
    CUP_COMPETITIONS are cups."""
    if code in DIVISIONS:
        return "league"
    if code in EURO_COMPETITIONS:
        return "europe"
    if code in CUP_COMPETITIONS:
        return "cup"
    return None


def competition_name(code: str) -> str:
    if code in DIVISIONS:
        return DIVISIONS[code].name
    return EURO_COMPETITIONS.get(code) or CUP_COMPETITIONS.get(code) or code


# ---------------------------------------------------------------------------------------------
# The match log

# Match statistics: log name, home column, away column (football-data's `matches`). `fd_xg` is
# football-data's own expected-goals figure (undocumented columns, 2026/27 onwards), not ours;
# no model uses it.
STATS = (
    ("shots", "home_shots", "away_shots"),
    ("shots_on_target", "home_sot", "away_sot"),
    ("corners", "home_corners", "away_corners"),
    ("fouls", "home_fouls", "away_fouls"),
    ("yellow_cards", "home_yellow", "away_yellow"),
    ("red_cards", "home_red", "away_red"),
    ("fd_xg", "fd_home_xg", "fd_away_xg"),
)
STAT_NAMES = tuple(s[0] for s in STATS)
# Per-match averages: goals, half-time goals, then every statistic.
AVERAGED = ("goals", "half_time_goals", *STAT_NAMES)

# Every log row has exactly these keys, plus "elo" and "prediction" (objects or null).
LOG_FIELDS = (
    "ref",  # "<table>:<id>", unique per stored match
    "source",  # football-data | openfootball | wikipedia
    "date",  # UK date for league matches, the venue's local date for UEFA matches
    "kickoff_utc",
    "competition",
    "competition_name",
    "competition_type",
    "season",
    "season_label",
    "stage",  # UEFA only, e.g. "League MD3"
    "meeting",  # league only: 1, 2, 3 for repeat meetings in one season (D-022)
    "opponent",
    "opponent_name",
    "venue",  # H | A | N (neutral)
    "listed_home",  # the team is the home side as the source lists it (matters on neutral ground)
    "status",  # played | awarded | cancelled
    "goals_for",
    "goals_against",
    "result",  # W | D | L, regulation time
    "ht_goals_for",
    "ht_goals_against",
    "ht_result",
    "extra_time",  # UEFA only
    "penalties",  # UEFA only
    "neutral",
    *(f"{s}_{side}" for s in STAT_NAMES for side in ("for", "against")),
    "referee",
    "excluded",  # the match's model_exclusion: flagged and left out of every summary
)
UNPLAYED = ("scheduled", "postponed")
FIXTURE_FIELDS = (
    "ref",
    "source",
    "date",
    "kickoff_utc",
    "competition",
    "competition_name",
    "competition_type",
    "season",
    "season_label",
    "stage",
    "opponent",
    "opponent_name",
    "venue",
    "status",
    "awaiting_result",  # its kickoff (or its date, if no kickoff is known) has passed
    "prediction",
)
# Upcoming fixtures are listed from this many days back, so a match whose result hasn't been
# loaded yet still shows (as awaiting its result).
UPCOMING_LOOKBACK_DAYS = 7

NOTES = {
    "scores": "Regulation time (90 minutes plus stoppage time). Extra time and penalties are "
    "flags only.",
    "statistics": "League statistics are football-data's. The UEFA sources (openfootball, "
    "Wikipedia) carry none, so UEFA rows have null statistics.",
    "fd_xg": "football-data's own expected-goals figure (undocumented columns, 2026/27 "
    "onwards), not this project's; no model uses it.",
    "excluded": "Matches with a model exclusion (awarded, cancelled, bare results, play-offs "
    "against outside clubs) are listed and flagged, but left out of every summary, form "
    "string, streak and the strength series.",
    "prediction": "Our genuine pre-match record of record (D-026): the first edition of the "
    "first listed model of record with a record for the fixture. 'correct' appears once it "
    "is settled.",
}


def _iso(value: object) -> str | None:
    """A kickoff read with plain SQL (text on SQLite, a datetime elsewhere) as ISO 8601 UTC."""
    if value is None:
        return None
    if isinstance(value, datetime):
        return iso_utc(value)
    try:
        return iso_utc(datetime.fromisoformat(str(value).strip()))
    except ValueError:
        return None


def _day(value: object) -> str:
    return value.isoformat() if isinstance(value, date) else str(value)[:10]


def _label(season: str) -> str:
    try:
        return season_label(season)
    except ValueError:
        return season


def _goals(hg: int | None, ag: int | None, home: bool, prefix: str = "") -> dict:
    if hg is None or ag is None:
        return {f"{prefix}goals_for": None, f"{prefix}goals_against": None, f"{prefix}result": None}
    gf, ga = (int(hg), int(ag)) if home else (int(ag), int(hg))
    return {
        f"{prefix}goals_for": gf,
        f"{prefix}goals_against": ga,
        f"{prefix}result": "W" if gf > ga else "D" if gf == ga else "L",
    }


def _base(m, team: str, names: dict[str, str], *, ref: str, source: str, neutral: bool) -> dict:
    home = m["home_team"] == team
    opp = m["away_team"] if home else m["home_team"]
    comp = m["competition"]
    row = dict.fromkeys(LOG_FIELDS)
    row.update(
        ref=ref,
        source=source,
        date=_day(m["match_date"]),
        kickoff_utc=_iso(m["kickoff_utc"]),
        competition=comp,
        competition_name=competition_name(comp),
        competition_type=competition_type(comp),
        season=m["season"],
        season_label=_label(m["season"]),
        opponent=opp,
        opponent_name=names.get(opp, opp),
        venue="N" if neutral else "H" if home else "A",
        listed_home=home,
        neutral=neutral,
        status="played",
        excluded=m["model_exclusion"],
    )
    row.update(_goals(m["home_goals"], m["away_goals"], home))
    row["elo"] = None
    row["prediction"] = None
    return row


def _league_row(m, team: str, names: dict[str, str]) -> dict:
    row = _base(m, team, names, ref=f"matches:{m['id']}", source="football-data", neutral=False)
    home = row["listed_home"]
    row.update(meeting=m["meeting"], referee=m["referee"])
    row.update(_goals(m["ht_home_goals"], m["ht_away_goals"], home, prefix="ht_"))
    for name, hcol, acol in STATS:
        row[f"{name}_for"], row[f"{name}_against"] = (
            (m[hcol], m[acol]) if home else (m[acol], m[hcol])
        )
    return row


def _euro_row(m, team: str, names: dict[str, str]) -> dict:
    row = _base(
        m,
        team,
        names,
        ref=f"euro_matches:{m['id']}",
        source=m["source"],
        neutral=bool(m["neutral"]),
    )
    row.update(
        stage=m["stage"],
        status=m["status"],
        extra_time=bool(m["extra_time"]),
        penalties=bool(m["penalties"]),
    )
    return row


# The light columns of every row, in this order and date order, for the cached index.
INDEX_SQL = (
    "SELECT id, {comp} AS competition, season, match_date, home_team, away_team, home_goals, "
    "away_goals, model_exclusion, {neutral} AS neutral, {status} AS status, "
    "'{table}' AS source_table FROM {table} ORDER BY match_date, id"
)


@dataclass(frozen=True)
class MatchSource:
    """One table of matches in the log.

    `team_sql` reads one team's rows (bound as :t) with at least the columns `to_row` reads;
    `index_sql` reads every row's INDEX_SQL columns; `to_row` turns a team_sql row into the team's
    log row (a row whose status is in UNPLAYED is an upcoming fixture). Sources are listed in
    the order the strength series takes same-day matches."""

    table: str
    team_sql: str
    index_sql: str
    to_row: Callable[..., dict]


MATCH_SOURCES = (
    MatchSource(
        "matches",
        "SELECT id, division AS competition, season, match_date, kickoff_utc, meeting, "
        "home_team, away_team, home_goals, away_goals, ht_home_goals, ht_away_goals, "
        + ", ".join(c for _, h, a in STATS for c in (h, a))
        + ", referee, model_exclusion FROM matches WHERE home_team = :t OR away_team = :t",
        INDEX_SQL.format(comp="division", neutral="0", status="'played'", table="matches"),
        _league_row,
    ),
    MatchSource(
        "euro_matches",
        "SELECT id, competition, season, stage, match_date, kickoff_utc, home_team, away_team, "
        "home_goals, away_goals, extra_time, penalties, neutral, status, source, "
        "model_exclusion FROM euro_matches WHERE home_team = :t OR away_team = :t",
        INDEX_SQL.format(
            comp="competition", neutral="neutral", status="status", table="euro_matches"
        ),
        _euro_row,
    ),
)


@dataclass
class TeamLog:
    team: str
    rows: list[dict]  # matches with a result or a final status, oldest first
    fixtures: list[dict]  # not yet played, oldest first


def team_names(s: Session) -> dict[str, str]:
    return dict(s.execute(select(Team.team_key, Team.name)).all())


def team_log(s: Session, team: str, names: dict[str, str] | None = None) -> TeamLog:
    """Every stored match of `team`, from its side. Reads only that team's rows."""
    names = names if names is not None else team_names(s)
    rows, fixtures = [], []
    for rank, src in enumerate(MATCH_SOURCES):
        for m in s.execute(text(src.team_sql), {"t": team}).mappings():
            r = src.to_row(m, team, names)
            item = ((r["date"], rank, int(m["id"])), r)
            (fixtures if r["status"] in UNPLAYED else rows).append(item)
    return TeamLog(
        team,
        [r for _, r in sorted(rows, key=lambda x: x[0])],
        [r for _, r in sorted(fixtures, key=lambda x: x[0])],
    )


def counted(rows: Iterable[dict]) -> list[dict]:
    """The rows every summary counts: a result, and no model exclusion."""
    return [r for r in rows if r["excluded"] is None and r["result"] is not None]


# ---------------------------------------------------------------------------------------------
# Pre-match records (D-026)

SUMMARY_MARKETS = ("1X2", "OU_2.5", "BTTS", "expected_goals")


def team_records(
    s: Session, team: str, periods: dict[str, list[Period]]
) -> list[tuple[Prediction, PredictionSettlement | None]]:
    """The record of record of every fixture `team` has one for: the first edition of the
    first listed model of record with a genuine pre-match record (as /api/performance counts
    it), with its settlement if it has one."""
    ror = RecordsOfRecord(s, periods)
    preds = s.scalars(
        select(Prediction)
        .where(Prediction.record_type == "genuine_prematch")
        .where(or_(Prediction.home_team == team, Prediction.away_team == team))
        .order_by(Prediction.id)
    ).all()
    first: dict[str, Prediction] = {}
    for p in preds:
        if p.fixture_ref not in first and p.model_name == ror.model_for(
            p.fixture_ref, p.competition
        ):
            first[p.fixture_ref] = p
    st: dict[int, PredictionSettlement] = {}
    if first:
        ids = [p.id for p in first.values()]
        for x in s.scalars(
            select(PredictionSettlement)
            .where(PredictionSettlement.prediction_id.in_(ids))
            .order_by(PredictionSettlement.id)
        ):
            st[x.prediction_id] = x  # the latest settlement
    return [(p, st.get(p.id)) for p in first.values()]


def _for_team(outcome: str | None, home: bool) -> str | None:
    """A 1X2 outcome (H, D, A) as W, D or L for the team."""
    if outcome not in ("H", "D", "A"):
        return None
    if outcome == "D":
        return "D"
    return "W" if (outcome == "H") == home else "L"


def record_summary(p: Prediction, st: PredictionSettlement | None, team: str) -> dict:
    """What a log row shows of a saved record: its model, basis, the probabilities of the main
    markets (never prices), the 1X2 pick, and once settled, whether each pick was right."""
    probs = json.loads(p.probabilities)
    picks = json.loads(p.selections)
    home = p.home_team == team
    x = probs.get("1X2")
    pick = (picks.get("1X2") or {}).get("pick")
    settled = st is not None and st.status == "settled"
    return {
        "id": p.id,
        "model_name": p.model_name,
        "basis": basis_of(json.loads(p.features) if p.features else {}),
        "edition": p.edition,
        "created_at_utc": iso_utc(p.created_at_utc),
        "probabilities": {k: probs[k] for k in SUMMARY_MARKETS if k in probs},
        "team": None
        if not x
        else {
            "win": x["H" if home else "A"],
            "draw": x["D"],
            "loss": x["A" if home else "H"],
        },
        "pick": pick,
        "pick_for_team": _for_team(pick, home),
        "settled": settled,
        "correct": {m: bool(o["correct"]) for m, o in json.loads(st.outcomes).items()}
        if settled
        else None,
    }


def _sides(r: dict, team: str) -> tuple[str, str]:
    return (team, r["opponent"]) if r["listed_home"] else (r["opponent"], team)


def find_match(rows: list[dict], team: str, p: Prediction) -> dict | None:
    """The log row a record is for, by the rules settlement uses (footy.publish): a league
    pairing that meets once a season (max_meetings 1) is its season's row whatever the date,
    since a postponed match keeps its record; where it can meet again (D-022) only the meeting
    on the record's date; anything else, the match on the record's date."""
    day = p.match_date.isoformat()
    same = [
        r
        for r in rows
        if r["competition"] == p.competition
        and r["season"] == p.season
        and _sides(r, team) == (p.home_team, p.away_team)
    ]
    if competition_type(p.competition) == "league":
        division = DIVISIONS.get(p.competition)
        if division is None or division.max_meetings == 1:
            return min(same, key=lambda r: r["meeting"] or 1, default=None)
    return next((r for r in same if r["date"] == day), None)


def _fixture_from_record(p: Prediction, team: str, names: dict[str, str]) -> dict:
    """An upcoming fixture known only from our published record (domestic fixtures are not
    stored until their result is imported)."""
    home = p.home_team == team
    opp = p.away_team if home else p.home_team
    return {
        **dict.fromkeys(FIXTURE_FIELDS),
        "ref": f"prediction:{p.id}",
        "source": "pre-match record",
        "date": p.match_date.isoformat(),
        "kickoff_utc": iso_utc(p.kickoff_utc),
        "competition": p.competition,
        "competition_name": competition_name(p.competition),
        "competition_type": competition_type(p.competition),
        "season": p.season,
        "season_label": _label(p.season),
        "opponent": opp,
        "opponent_name": names.get(opp, opp),
        "venue": "H" if home else "A",
        "status": "scheduled",
    }


def attach_records(
    log: TeamLog,
    records: list[tuple[Prediction, PredictionSettlement | None]],
    names: dict[str, str],
) -> list[dict]:
    """Put each record on its match or fixture in the log. Returns fixtures known only from a
    record (no stored match yet), with the record on them."""
    extra = []
    for p, st in records:
        summary = record_summary(p, st, log.team)
        row = find_match(log.rows, log.team, p) or find_match(log.fixtures, log.team, p)
        if row is not None:
            row["prediction"] = summary
        else:
            fx = _fixture_from_record(p, log.team, names)
            fx["prediction"] = summary
            extra.append(fx)
    return extra


def upcoming(log: TeamLog, extra: list[dict], now: datetime) -> list[dict]:
    """Fixtures from UPCOMING_LOOKBACK_DAYS ago onwards, oldest first; `awaiting_result` once
    the kickoff (or, without one, the end of the match day in the UK) has passed."""
    since = (now.astimezone(UK).date() - timedelta(days=UPCOMING_LOOKBACK_DAYS)).isoformat()
    out = []
    for f in [*log.fixtures, *extra]:
        if f["date"] < since:
            continue
        fx = {k: f.get(k) for k in FIXTURE_FIELDS}
        if fx["kickoff_utc"]:
            started = datetime.fromisoformat(fx["kickoff_utc"])
        else:
            day = date.fromisoformat(fx["date"]) + timedelta(days=1)
            started = datetime(day.year, day.month, day.day, tzinfo=UK)
        fx["awaiting_result"] = started <= now
        out.append(fx)
    return sorted(out, key=lambda f: (f["date"], f["kickoff_utc"] or "", f["ref"]))


# ---------------------------------------------------------------------------------------------
# Summaries

POINTS = {"W": 3, "D": 1, "L": 0}


def _stat_keys(name: str) -> tuple[str, str]:
    if name == "goals":
        return "goals_for", "goals_against"
    if name == "half_time_goals":
        return "ht_goals_for", "ht_goals_against"
    return f"{name}_for", f"{name}_against"


def per_match(rows: list[dict]) -> dict:
    """Average for and against per match of each statistic, over the counted rows that have
    it; `matches` says how many that is."""
    out = {}
    for name in AVERAGED:
        kf, ka = _stat_keys(name)
        pairs = [(r[kf], r[ka]) for r in rows if r[kf] is not None and r[ka] is not None]
        n = len(pairs)
        out[name] = {
            "for": round(sum(f for f, _ in pairs) / n, 3) if n else None,
            "against": round(sum(a for _, a in pairs) / n, 3) if n else None,
            "matches": n,
        }
    return out


def _rate(k: int, n: int) -> dict:
    return {"matches": k, "rate": round(k / n, 4) if n else None}


def _wdl(rows: list[dict]) -> dict:
    return {
        "won": sum(r["result"] == "W" for r in rows),
        "drawn": sum(r["result"] == "D" for r in rows),
        "lost": sum(r["result"] == "L" for r in rows),
    }


def summarise(rows: list[dict]) -> dict:
    """P W D L, goals, league points per game, per-match averages, rates and half-time
    turnarounds over `rows`, counting only rows with a result and no model exclusion."""
    played = counted(rows)
    n = len(played)
    gf = sum(r["goals_for"] for r in played)
    ga = sum(r["goals_against"] for r in played)
    league = [r for r in played if r["competition_type"] == "league"]
    points = sum(POINTS[r["result"]] for r in league)
    ht = [r for r in played if r["ht_goals_for"] is not None]
    behind = [r for r in ht if r["ht_goals_for"] < r["ht_goals_against"]]
    ahead = [r for r in ht if r["ht_goals_for"] > r["ht_goals_against"]]
    b, a = _wdl(behind), _wdl(ahead)
    return {
        "played": n,
        **_wdl(played),
        "goals_for": gf,
        "goals_against": ga,
        "goal_difference": gf - ga,
        "left_out": len(rows) - n,  # flagged matches (model exclusion) or without a score
        "league_matches": len(league),
        "league_points": points if league else None,
        "points_per_game": round(points / len(league), 3) if league else None,
        "per_match": per_match(played),
        "rates": {
            "clean_sheets": _rate(sum(r["goals_against"] == 0 for r in played), n),
            "failed_to_score": _rate(sum(r["goals_for"] == 0 for r in played), n),
            "both_teams_scored": _rate(
                sum(r["goals_for"] > 0 and r["goals_against"] > 0 for r in played), n
            ),
            "over_2_5_goals": _rate(
                sum(r["goals_for"] + r["goals_against"] > 2 for r in played), n
            ),
        },
        "half_time": {
            "matches_with_half_time_score": len(ht),
            "behind_at_half_time": {
                "matches": len(behind),
                **b,
                "points_won": 3 * b["won"] + b["drawn"],
            },
            "ahead_at_half_time": {
                "matches": len(ahead),
                **a,
                "points_dropped": 2 * a["drawn"] + 3 * a["lost"],
            },
        },
    }


def form_string(rows: list[dict], n: int) -> str:
    """The last `n` counted results, oldest first (the latest is on the right)."""
    return "".join(r["result"] for r in counted(rows)[-n:])


STREAKS: dict[str, Callable[[dict], bool]] = {
    "wins": lambda r: r["result"] == "W",
    "unbeaten": lambda r: r["result"] != "L",
    "without_a_win": lambda r: r["result"] != "W",
    "losses": lambda r: r["result"] == "L",
    "scored": lambda r: r["goals_for"] > 0,
    "clean_sheets": lambda r: r["goals_against"] == 0,
    "failed_to_score": lambda r: r["goals_for"] == 0,
}


def streaks(rows: list[dict]) -> dict:
    """Current runs, counted back from the latest counted match: length and first date."""
    played = counted(rows)
    out = {}
    for name, holds in STREAKS.items():
        k = 0
        for r in reversed(played):
            if not holds(r):
                break
            k += 1
        out[name] = {"matches": k, "since": played[-k]["date"] if k else None}
    return out


# ---------------------------------------------------------------------------------------------
# Cached index: the strength series and the teams list
#
# Built from every match (about 70,000 rows), so it is cached in-process per engine and rebuilt
# when the data fingerprint changes. Per-team logs are never cached: they read only that team's
# rows and are fresh on every request.


def data_fingerprint(s: Session) -> tuple:
    """Cheap fingerprint of what the index reads: row counts and highest ids of `matches`, the
    import log (a re-parse adds a row there) and the correction log (a changed value adds one),
    and the content of `euro_matches` (small, and updated in place by euro-update)."""
    head = s.execute(
        text(
            "SELECT (SELECT COUNT(*) FROM matches), (SELECT MAX(id) FROM matches), "
            "(SELECT COUNT(*) FROM raw_imports), (SELECT MAX(imported_at) FROM raw_imports), "
            "(SELECT COUNT(*) FROM data_corrections), (SELECT MAX(id) FROM data_corrections)"
        )
    ).one()
    cur = s.connection().connection.cursor()  # plain tuples, hashed as they come
    try:
        cur.execute(
            "SELECT id, competition, season, match_date, home_team, away_team, home_goals, "
            "away_goals, model_exclusion, neutral, status FROM euro_matches ORDER BY id"
        )
        euro = tuple(map(tuple, cur.fetchall()))
    finally:
        cur.close()
    return (*(str(v) for v in head), len(euro), hash(euro))


# The descriptive strength series uses the update rule and default settings of the pan-European
# Elo (footy.modeling.euro.PanEuropeanElo): World Football Elo with a goal-difference
# multiplier, K by competition type (a cup takes the league K), home advantage except on
# neutral ground, and a starting level by where a club is first seen. The settings are copied,
# not imported: importing footy.modeling.euro loads scipy, about a second on a page request.
# tests/test_teams.py checks that they agree and that the ratings match PanEuropeanElo's.
ELO_K = {"league": 20.0, "europe": 30.0, "cup": 20.0}
ELO_HOME = 65.0
ELO_START = {"core_top": 1500.0, "europe_top": 1450.0, "tier2": 1400.0, "uefa_only": 1400.0}
ELO_ACTIVE_DAYS = 365  # ranked: a match in the year before the latest match held


def elo_start(competition: str) -> float:
    """A new club's rating, by the competition it is first seen in (PanEuropeanElo._start)."""
    d = DIVISIONS.get(competition)
    if d is None:
        return ELO_START["uefa_only"]
    if d.tier == 2:
        return ELO_START["tier2"]
    return ELO_START["core_top"] if d.scope == "core" else ELO_START["europe_top"]


@dataclass
class TeamsIndex:
    fingerprint: tuple
    built_at_utc: datetime
    build_ms: float
    latest_date: str | None  # the latest counted match held
    latest_season: dict[str, str]  # competition -> latest season key held
    # competition -> its current season: the latest held for a league or cup; for every UEFA
    # competition, the latest UEFA season held (qualifying included), so a competition the
    # newest season hasn't reached yet isn't "this season"
    current_season: dict[str, str]
    rating: dict[str, float]  # each team's current rating
    # team -> [(date, competition, rating after, rating before, opponent's before, table, id)]
    series: dict[str, list[tuple]]
    rank: dict[str, int]  # among teams active in the last ELO_ACTIVE_DAYS
    entries: dict[str, dict]  # team -> its /api/teams entry, without name and country


_CACHE: "weakref.WeakKeyDictionary[object, TeamsIndex]" = weakref.WeakKeyDictionary()
_CACHE_LOCK = threading.Lock()


def teams_index(s: Session) -> TeamsIndex:
    """The cached index for this session's database, rebuilt if the data changed."""
    engine = s.get_bind()
    fp = data_fingerprint(s)
    with _CACHE_LOCK:
        idx = _CACHE.get(engine)
        if idx is None or idx.fingerprint != fp:
            idx = build_index(s, fp)
            _CACHE[engine] = idx
    return idx


def build_index(s: Session, fingerprint: tuple = ()) -> TeamsIndex:
    """One pass over every match in date order for the strength series and each team's
    counted results, then one for the competitions each team is in this season. Plain tuples
    and inline code: it reads about 70,000 rows."""
    t0 = time.perf_counter()
    rows: list[tuple] = []
    cur = s.connection().connection.cursor()  # plain tuples: much faster than result rows
    try:
        for src in MATCH_SOURCES:
            cur.execute(src.index_sql)
            rows += cur.fetchall()
    finally:
        cur.close()
    # Each source comes in (date, id) order; a stable sort on the date merges them, keeping
    # same-day matches in source order, as PanEuropeanElo orders them.
    rows.sort(key=itemgetter(3))

    ctype = {c: competition_type(c) for c in {r[1] for r in rows}}
    k_comp = {c: ELO_K.get(t or "league", ELO_K["league"]) for c, t in ctype.items()}
    start = {c: elo_start(c) for c in ctype}
    flip = {"W": "L", "D": "D", "L": "W"}
    rating: dict[str, float] = {}
    series: dict[str, list[tuple]] = {}
    results: dict[str, list[tuple]] = {}  # team -> [(date, W/D/L, type, competition, season)]
    latest_season: dict[str, str] = {}
    latest_date = None
    for mid, comp, season, day, h, a, hg, ag, excl, neutral, status, table in rows:
        if season > latest_season.get(comp, ""):
            latest_season[comp] = season
        if status != "played" or hg is None or ag is None or excl is not None:
            continue
        if not isinstance(day, str):  # a date object outside SQLite
            day = day.isoformat()
        res = "W" if hg > ag else "D" if hg == ag else "L"
        latest_date = day
        # The pan-European Elo update (footy.modeling.euro.PanEuropeanElo.fit).
        rh = rating.get(h, start[comp])
        ra = rating.get(a, start[comp])
        dr = rh - ra + (0.0 if neutral else ELO_HOME)
        we = 1 / (10 ** (-dr / 400) + 1)
        gd = abs(hg - ag)
        g = 1.0 if gd <= 1 else 1.5 if gd == 2 else (11 + gd) / 8
        w = 1.0 if res == "W" else 0.5 if res == "D" else 0.0
        delta = k_comp[comp] * g * (w - we)
        rating[h], rating[a] = rh + delta, ra - delta
        ct = ctype[comp]
        series.setdefault(h, []).append((day, comp, rh + delta, rh, ra, table, mid))
        series.setdefault(a, []).append((day, comp, ra - delta, ra, rh, table, mid))
        results.setdefault(h, []).append((day, res, ct, comp, season))
        results.setdefault(a, []).append((day, flip[res], ct, comp, season))
    uefa = max((sn for c, sn in latest_season.items() if ctype[c] == "europe"), default=None)
    current_season = {c: uefa if ctype[c] == "europe" else sn for c, sn in latest_season.items()}
    # Competitions in whose current season each team has a match or fixture.
    current: dict[str, set[str]] = {}
    for r in rows:
        if current_season[r[1]] == r[2]:
            current.setdefault(r[4], set()).add(r[1])
            current.setdefault(r[5], set()).add(r[1])

    active_since = (
        (date.fromisoformat(latest_date) - timedelta(days=ELO_ACTIVE_DAYS)).isoformat()
        if latest_date
        else ""
    )
    active = sorted((t for t, x in series.items() if x[-1][0] >= active_since), key=rating.get)
    rank = {t: i + 1 for i, t in enumerate(reversed(active))}
    entries = {
        t: _entry(
            results.get(t, []), current.get(t, set()), current_season, rating.get(t), rank.get(t)
        )
        for t in {r[4] for r in rows} | {r[5] for r in rows}
    }
    return TeamsIndex(
        fingerprint=fingerprint,
        built_at_utc=datetime.now(UTC),
        build_ms=round((time.perf_counter() - t0) * 1000, 1),
        latest_date=latest_date,
        latest_season=latest_season,
        current_season=current_season,
        rating=rating,
        series=series,
        rank=rank,
        entries=entries,
    )


def _division(results: list[tuple], current_season: dict[str, str]) -> dict | None:
    """The league of the team's latest league season among its counted matches (most
    matches; then the higher tier)."""
    counts: dict[str, int] = {}
    season = None
    for _, _, ct, comp, sn in reversed(results):  # newest first; seasons only go back
        if ct != "league":
            continue
        if season is None:
            season = sn
        elif sn != season:
            break
        counts[comp] = counts.get(comp, 0) + 1
    if season is None:
        return None
    code = min(counts, key=lambda c: (-counts[c], DIVISIONS[c].tier, c))
    return {
        "code": code,
        "name": competition_name(code),
        "tier": DIVISIONS[code].tier,
        "season": season,
        "season_label": _label(season),
        # False once the league has a later season without the team (e.g. relegated to a
        # division not held).
        "current": current_season.get(code) == season,
    }


def _entry(
    results: list[tuple],
    current: set[str],
    current_season: dict[str, str],
    rating: float | None,
    rank: int | None,
) -> dict:
    """A team's /api/teams entry, without its name and country (read fresh per request)."""
    order = {t: i for i, t in enumerate(COMPETITION_TYPES)}
    by_type = Counter(map(itemgetter(2), results))
    return {
        "division": _division(results, current_season),
        "competitions_this_season": [
            {
                "code": c,
                "name": competition_name(c),
                "type": competition_type(c),
                "season": current_season[c],
                "season_label": _label(current_season[c]),
            }
            for c in sorted(current, key=lambda c: (order.get(competition_type(c) or "", 9), c))
        ],
        "form_last_5": "".join(r[1] for r in results[-5:]),
        "matches": len(results),
        "matches_by_type": {t: by_type.get(t, 0) for t in COMPETITION_TYPES},
        "last_match_date": results[-1][0] if results else None,
        "elo": None if rating is None else round(rating, 1),
        "elo_rank": rank,
    }


def attach_elo(log: TeamLog, idx: TeamsIndex) -> None:
    """Each counted row's strength before and after, and the opponent's before."""
    by_ref = {f"{x[5]}:{x[6]}": x for x in idx.series.get(log.team, [])}
    for r in log.rows:
        x = by_ref.get(r["ref"])
        if x is not None:
            r["elo"] = {
                "before": round(x[3], 1),
                "after": round(x[2], 1),
                "opponent_before": round(x[4], 1),
            }


def strength(idx: TeamsIndex, team: str, only_type: str | None = None) -> dict:
    series = idx.series.get(team, [])
    if only_type:
        series = [x for x in series if competition_type(x[1]) == only_type]
    peak = max(series, key=itemgetter(2), default=None)
    return {
        "description": "Descriptive only, not a model of record: a goal-based Elo over every "
        "league and UEFA match held, in date order, with the pan-European Elo's update rule "
        "and settings (footy.modeling.euro.PanEuropeanElo). Each point is the rating after a "
        "counted match.",
        "settings": {"k": ELO_K, "home_advantage": ELO_HOME, "start": ELO_START},
        "current": None if team not in idx.rating else round(idx.rating[team], 1),
        "rank": idx.rank.get(team),
        "ranked_teams": len(idx.rank),
        "rank_basis": f"teams with a counted match in the {ELO_ACTIVE_DAYS} days before the "
        f"latest match held ({idx.latest_date})",
        "peak": None if peak is None else {"rating": round(peak[2], 1), "date": peak[0]},
        "series": [{"date": x[0], "competition": x[1], "rating": round(x[2], 1)} for x in series],
    }


# ---------------------------------------------------------------------------------------------
# Profile, list, match page


def _fold(s: str) -> str:
    """Lower case without accents, for search: "Atlético" matches "atletico"."""
    return "".join(
        ch for ch in unicodedata.normalize("NFKD", s.casefold()) if not unicodedata.combining(ch)
    )


def find_teams(s: Session, query: str) -> list[str]:
    """Team keys for a key or a name: an exact key, else an exact name or alias (ignoring case
    and accents), else every team whose name or key contains the query."""
    if s.get(Team, query) is not None:
        return [query]
    q = _fold(query.strip())
    teams = s.execute(select(Team.team_key, Team.name)).all()
    exact = sorted({k for k, n in teams if _fold(n) == q})
    if not exact:
        aliases = s.execute(select(TeamAlias.alias, TeamAlias.team_key)).all()
        exact = sorted({k for a, k in aliases if _fold(a) == q})
    if exact:
        return exact
    return sorted(k for k, n in teams if q in _fold(n) or q in k)


def _group(rows: list[dict], key: str) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {}
    for r in rows:
        out.setdefault(r[key], []).append(r)
    return out


def _by_venue(rows: list[dict]) -> dict:
    return {v: summarise([r for r in rows if r["venue"] == v]) for v in ("H", "A", "N")}


def team_profile(
    s: Session,
    team: str,
    *,
    periods: dict[str, list[Period]],
    now: datetime | None = None,
    only_type: str | None = None,
) -> dict | None:
    """Everything a team page shows (None for an unknown team). `only_type` restricts it to
    one competition type, as `footy team-stats --type` does."""
    now = now or datetime.now(UTC)
    t = s.get(Team, team)
    if t is None:
        return None
    names = team_names(s)
    idx = teams_index(s)
    log = team_log(s, team, names)
    attach_elo(log, idx)
    extra = attach_records(log, team_records(s, team, periods), names)
    fixtures = upcoming(log, extra, now)
    rows = log.rows
    types = COMPETITION_TYPES
    if only_type:
        rows = [r for r in rows if r["competition_type"] == only_type]
        fixtures = [f for f in fixtures if f["competition_type"] == only_type]
        types = (only_type,)
    played = counted(rows)
    typed = {ct: [r for r in rows if r["competition_type"] == ct] for ct in types}
    entry = idx.entries.get(team, {})
    order = {c: i for i, c in enumerate(COMPETITION_TYPES)}
    comps = _group(rows, "competition")
    seasons = _group(rows, "season")
    return {
        "generated_at_utc": iso_utc(now, "seconds"),
        "team": {
            "key": t.team_key,
            "name": t.name,
            "country": t.country,
            "division": entry.get("division"),
            "competitions_this_season": entry.get("competitions_this_season", []),
        },
        "only_type": only_type,
        "data": {"latest_match_held": idx.latest_date},
        "notes": NOTES,
        "matches": {
            "logged": len(rows),
            "counted": len(played),
            "left_out": len(rows) - len(played),
            "first_date": rows[0]["date"] if rows else None,
            "last_date": rows[-1]["date"] if rows else None,
        },
        "summary": summarise(rows),
        "by_type": {ct: {**summarise(rs), "by_venue": _by_venue(rs)} for ct, rs in typed.items()},
        "by_competition": [
            {
                "competition": c,
                "name": competition_name(c),
                "type": competition_type(c),
                "seasons": sorted({r["season"] for r in rs}, reverse=True),
                "summary": summarise(rs),
            }
            for c, rs in sorted(
                comps.items(),
                key=lambda kv: (order.get(competition_type(kv[0]) or "", 9), -len(kv[1]), kv[0]),
            )
        ],
        "by_season": [
            {
                "season": k,
                "label": _label(k),
                "competitions": sorted({r["competition"] for r in rs}),
                "summary": summarise(rs),
                "by_type": {
                    ct: summarise([r for r in rs if r["competition_type"] == ct])
                    for ct in types
                    if any(r["competition_type"] == ct for r in rs)
                },
            }
            for k, rs in sorted(seasons.items(), reverse=True)
        ],
        "by_venue": _by_venue(rows),
        "form": {
            "order": "oldest first; the latest result is on the right",
            "last_5": form_string(rows, 5),
            "last_10": form_string(rows, 10),
            "by_type": {
                ct: {"last_5": form_string(rs, 5), "last_10": form_string(rs, 10)}
                for ct, rs in typed.items()
            },
        },
        "streaks": {
            "all": streaks(rows),
            "by_type": {ct: streaks(rs) for ct, rs in typed.items()},
        },
        "upcoming": fixtures,
        "strength": strength(idx, team, only_type),
    }


def team_matches(
    s: Session,
    team: str,
    *,
    periods: dict[str, list[Period]],
    type_: str | None = None,
    competition: str | None = None,
    season: str | None = None,
) -> list[dict] | None:
    """The team's match log, newest first, filtered (None for an unknown team)."""
    if s.get(Team, team) is None:
        return None
    names = team_names(s)
    log = team_log(s, team, names)
    attach_elo(log, teams_index(s))
    attach_records(log, team_records(s, team, periods), names)
    rows = [
        r
        for r in log.rows
        if (type_ is None or r["competition_type"] == type_)
        and (competition is None or r["competition"] == competition)
        and (season is None or r["season"] == season)
    ]
    return rows[::-1]


def teams_list(
    s: Session,
    competitions: Iterable[str] | None = None,
    q: str | None = None,
    idx: TeamsIndex | None = None,
) -> list[dict]:
    """Every team with a stored match or fixture, by name. `competitions` keeps teams with a
    match or fixture in the current season of any of them (TeamsIndex.current_season); `q`
    matches the name or key (ignoring case and accents). `idx` is the index to use (default:
    the cached one)."""
    idx = idx or teams_index(s)
    meta = {k: (n, c) for k, n, c in s.execute(select(Team.team_key, Team.name, Team.country))}
    wanted = {c for c in competitions or () if c}
    needle = _fold(q.strip()) if q and q.strip() else None
    out = []
    for key, e in idx.entries.items():
        name, country = meta.get(key, (key, None))
        if wanted and not wanted & {c["code"] for c in e["competitions_this_season"]}:
            continue
        if needle and needle not in _fold(name) and needle not in key:
            continue
        out.append({"key": key, "name": name, "country": country, **e})
    return sorted(out, key=lambda x: (_fold(x["name"]), x["key"]))


H2H_MEETINGS = 6
MATCH_PAGE_WINDOW = 10


def _side_stats(r: dict, mine: bool) -> dict:
    """One side's numbers from a log row: `mine` for the log's own team, else its opponent."""
    side = "for" if mine else "against"
    return {
        "goals": r[f"goals_{side}"],
        "half_time_goals": r[f"ht_goals_{side}"],
        **{name: r[f"{name}_{side}"] for name in STAT_NAMES},
    }


def match_page(s: Session, p: Prediction, names: dict[str, str]) -> dict:
    """The "teams" block of a match page: each side's form and per-match averages before the
    match, by competition type; the last meetings in any competition; and once the match is
    played and has statistics, both sides' statistics."""
    home_log = team_log(s, p.home_team, names)
    away_log = team_log(s, p.away_team, names)
    row = find_match(home_log.rows, p.home_team, p)
    # The record's date, or its match's if that was played earlier (a league match brought
    # forward keeps its record, as in settlement): the match itself is never "before" it.
    before = p.match_date.isoformat()
    if row is not None:
        before = min(before, row["date"])

    def side(log: TeamLog) -> dict:
        prior = [r for r in counted(log.rows) if r["date"] < before]
        typed = {ct: [r for r in prior if r["competition_type"] == ct] for ct in COMPETITION_TYPES}
        typed = {ct: rs for ct, rs in typed.items() if rs}
        return {
            "team": log.team,
            "name": names.get(log.team, log.team),
            "form": {
                "last_10": form_string(prior, MATCH_PAGE_WINDOW),
                "by_type": {ct: form_string(rs, MATCH_PAGE_WINDOW) for ct, rs in typed.items()},
            },
            "per_match": {
                ct: {
                    "matches": len(rs[-MATCH_PAGE_WINDOW:]),
                    "averages": per_match(rs[-MATCH_PAGE_WINDOW:]),
                }
                for ct, rs in {"all": prior, **typed}.items()
            },
        }

    meetings = [
        r
        for r in home_log.rows
        if r["opponent"] == p.away_team and r["date"] < before and r["result"] is not None
    ][-H2H_MEETINGS:][::-1]
    h2h = []
    for r in meetings:
        home_team, away_team = _sides(r, p.home_team)
        listed = r["listed_home"]
        h2h.append(
            {
                "ref": r["ref"],
                "date": r["date"],
                "competition": r["competition"],
                "competition_name": r["competition_name"],
                "competition_type": r["competition_type"],
                "season": r["season"],
                "stage": r["stage"],
                "home_team": home_team,
                "home_name": names.get(home_team, home_team),
                "away_team": away_team,
                "away_name": names.get(away_team, away_team),
                "home_goals": r["goals_for"] if listed else r["goals_against"],
                "away_goals": r["goals_against"] if listed else r["goals_for"],
                "neutral": r["neutral"],
                "extra_time": r["extra_time"],
                "penalties": r["penalties"],
                "result_for_home_side": r["result"],  # W/D/L for this fixture's home team
                "excluded": r["excluded"],
            }
        )
    tally = counted(meetings)
    stats = None
    if row is not None and row["result"] is not None:
        mine, theirs = _side_stats(row, True), _side_stats(row, False)
        if any(v is not None for k, v in mine.items() if k != "goals"):
            listed = row["listed_home"]
            stats = {
                "ref": row["ref"],
                "source": row["source"],
                "date": row["date"],
                "referee": row["referee"],
                "excluded": row["excluded"],
                "home": mine if listed else theirs,
                "away": theirs if listed else mine,
            }
    return {
        "as_of": before,
        "note": "Form, averages and meetings are from matches before this one's date; "
        f"averages cover each side's last {MATCH_PAGE_WINDOW} counted matches of each type.",
        "home": side(home_log),
        "away": side(away_log),
        "head_to_head": {
            "meetings": h2h,
            "home_side_won": sum(r["result"] == "W" for r in tally),
            "drawn": sum(r["result"] == "D" for r in tally),
            "away_side_won": sum(r["result"] == "L" for r in tally),
        },
        "match_stats": stats,
    }


# ---------------------------------------------------------------------------------------------
# CSV export and the CLI summary

CSV_EXTRA = (
    "elo_before",
    "elo_after",
    "opponent_elo_before",
    "prediction_id",
    "prediction_model",
    "prediction_basis",
    "p_win",
    "p_draw",
    "p_loss",
    "pick_for_team",
    "prediction_settled",
    "prediction_correct_1x2",
)


def csv_row(r: dict) -> dict:
    out = {k: r[k] for k in LOG_FIELDS}
    e, p = r.get("elo") or {}, r.get("prediction") or {}
    t = p.get("team") or {}
    out.update(
        elo_before=e.get("before"),
        elo_after=e.get("after"),
        opponent_elo_before=e.get("opponent_before"),
        prediction_id=p.get("id"),
        prediction_model=p.get("model_name"),
        prediction_basis=p.get("basis"),
        p_win=t.get("win"),
        p_draw=t.get("draw"),
        p_loss=t.get("loss"),
        pick_for_team=p.get("pick_for_team"),
        prediction_settled=p.get("settled"),
        prediction_correct_1x2=(p.get("correct") or {}).get("1X2"),
    )
    return out


def write_csv(rows: list[dict], path: Path) -> int:
    """The match log as CSV, one row per match, oldest first."""
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=[*LOG_FIELDS, *CSV_EXTRA])
        w.writeheader()
        for r in rows:
            w.writerow(csv_row(r))
    return len(rows)


def _words(key: str) -> str:
    return key.replace("_2_5_", " 2.5 ").replace("fd_xg", "football-data xG").replace("_", " ")


def _pct(x: dict) -> str:
    return "-" if x["rate"] is None else f"{x['rate']:.0%}"


def _avg(x: dict) -> str:
    if x["for"] is None:
        return "-"
    return f"{x['for']:.2f}-{x['against']:.2f} ({x['matches']})"


def _line(label: str, sm: dict, form: str = "") -> str:
    ppg = "" if sm["points_per_game"] is None else f"{sm['points_per_game']:.2f}"
    return (
        f"  {label:<26}{sm['played']:>4}{sm['won']:>4}{sm['drawn']:>4}{sm['lost']:>4}"
        f"{sm['goals_for']:>5}{sm['goals_against']:>5}{sm['goal_difference']:>+6}{ppg:>6}  {form}"
    )


def _uk(kickoff: str | None, day: str) -> str:
    if not kickoff:
        return f"{day}  --:--"
    return datetime.fromisoformat(kickoff).astimezone(UK).strftime("%Y-%m-%d  %H:%M")


def profile_text(pr: dict) -> str:
    """The profile as plain text for the terminal (times in UK time)."""
    t = pr["team"]
    d = t["division"]
    out = [
        f"{t['name']} ({t['key']}, {t['country']})"
        + (
            f" - {d['name']} {d['season_label']}{'' if d['current'] else ' (latest held)'}"
            if d
            else ""
        )
    ]
    if pr["only_type"]:
        out.append(f"Only {pr['only_type']} matches.")
    comps = ", ".join(f"{c['name']} {c['season_label']}" for c in t["competitions_this_season"])
    out.append(f"This season: {comps or '-'}")
    st = pr["strength"]
    if st["current"] is not None:
        rank = f", rank {st['rank']} of {st['ranked_teams']} active" if st["rank"] else ""
        out.append(f"Strength (descriptive Elo, all competitions): {st['current']:.0f}{rank}")
    m = pr["matches"]
    out.append(
        f"Matches: {m['counted']} counted, {m['left_out']} flagged and left out "
        f"({m['first_date']} to {m['last_date']}); latest held {pr['data']['latest_match_held']}"
    )
    header = (
        f"  {'':<26}{'P':>4}{'W':>4}{'D':>4}{'L':>4}{'GF':>5}{'GA':>5}{'GD':>6}{'PPG':>6}  Form"
    )
    out += ["", header, _line("All competitions", pr["summary"], pr["form"]["last_10"])]
    for ct, sm in pr["by_type"].items():
        if sm["played"]:
            out.append(_line(ct.capitalize(), sm, pr["form"]["by_type"][ct]["last_10"]))
    for v, label in (("H", "Home"), ("A", "Away"), ("N", "Neutral")):
        if pr["by_venue"][v]["played"]:
            out.append(_line(label, pr["by_venue"][v]))
    out += ["", header.replace("  Form", "")]
    for c in pr["by_competition"]:
        out.append(_line(c["name"][:26], c["summary"]))
    out += ["", header.replace("  Form", "")]
    for sn in pr["by_season"][:2]:
        out.append(_line(f"Season {sn['label']}", sn["summary"]))
        for ct, sm in sn["by_type"].items():
            out.append(_line(f"  {ct}", sm))
    s_ = pr["summary"]
    pm = s_["per_match"]
    out += [
        "",
        "Per match, for-against (matches with the figure):",
        "  " + "; ".join(f"{_words(k)} {_avg(pm[k])}" for k in AVERAGED[:5]),
        "  " + "; ".join(f"{_words(k)} {_avg(pm[k])}" for k in AVERAGED[5:]),
        "Rates: " + ", ".join(f"{_words(k)} {_pct(v)}" for k, v in s_["rates"].items()),
    ]
    ht = s_["half_time"]
    b, a = ht["behind_at_half_time"], ht["ahead_at_half_time"]
    out.append(
        f"Half time ({ht['matches_with_half_time_score']} matches with a half-time score): "
        f"behind {b['matches']} times, {b['points_won']} points won; "
        f"ahead {a['matches']} times, {a['points_dropped']} points dropped"
    )
    runs = [f"{_words(k)} {v['matches']}" for k, v in pr["streaks"]["all"].items() if v["matches"]]
    out.append("Current runs: " + (", ".join(runs) or "-"))
    if pr["upcoming"]:
        out += ["", "Upcoming (UK time):"]
        for f in pr["upcoming"]:
            p = f["prediction"]
            ours = ""
            if p and p["team"]:
                tm = p["team"]
                ours = (
                    f"  ours: W {tm['win']:.0%} D {tm['draw']:.0%} L {tm['loss']:.0%}"
                    f" ({p['model_name']})"
                )
            wait = "  awaiting result" if f["awaiting_result"] else ""
            out.append(
                f"  {_uk(f['kickoff_utc'], f['date'])}  {f['competition']:<5} {f['venue']}  "
                f"{f['opponent_name']}{ours}{wait}"
            )
    return "\n".join(out)
