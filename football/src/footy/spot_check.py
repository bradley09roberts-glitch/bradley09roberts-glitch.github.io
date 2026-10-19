"""M1 acceptance: compare a seeded random sample of imported matches with their raw source rows.

The raw row is re-read with Python's csv.DictReader, separately from the importer's parser,
and every stored field and price is compared with it. The report shows facts (date, teams,
score) and counts of compared values; it never reprints football-data's prices (D-013).
"""

import csv
import io
import random
from datetime import datetime
from zoneinfo import ZoneInfo

from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from footy.config import Settings
from footy.ingest.fd_parse import decode
from footy.ingest.raw_store import read_raw
from footy.models import FdOdds, Match, RawFile, Team, TeamAlias
from footy.reference import DIVISIONS
from footy.seasons import season_label

UK = ZoneInfo("Europe/London")
INT_FIELDS = {
    "home_goals": "FTHG",
    "away_goals": "FTAG",
    "ht_home_goals": "HTHG",
    "ht_away_goals": "HTAG",
    "home_shots": "HS",
    "away_shots": "AS",
    "home_sot": "HST",
    "away_sot": "AST",
    "home_corners": "HC",
    "away_corners": "AC",
    "home_fouls": "HF",
    "away_fouls": "AF",
    "home_yellow": "HY",
    "away_yellow": "AY",
    "home_red": "HR",
    "away_red": "AR",
}
PER_TOP_DIVISION = 3
PER_SECOND_TIER = 1


def sample_matches(session: Session, seed: int, n: int) -> list[Match]:
    rng = random.Random(seed)
    picked: list[Match] = []
    for code, d in DIVISIONS.items():
        ids = sorted(session.scalars(select(Match.id).where(Match.division == code)))
        k = PER_TOP_DIVISION if d.tier == 1 else PER_SECOND_TIER
        picked += [session.get(Match, i) for i in rng.sample(ids, min(k, len(ids)))]
    rng.shuffle(picked)
    return picked[:n]


def check_match(session: Session, settings: Settings, m: Match) -> dict:
    raw = session.get(RawFile, m.last_raw_file_id)
    text, _ = decode(read_raw(settings.raw_dir, raw))
    # source_row numbers every line after the header, blank ones included (csv.reader keeps
    # them; DictReader would not), so locate the row the same way.
    reader = csv.reader(io.StringIO(text))
    header = [h.strip() for h in next(reader)]
    values = list(reader)[m.source_row - 1]
    src = {h: (values[i].strip() if i < len(values) else "") for i, h in enumerate(header)}

    mismatches: list[str] = []
    compared = 0
    alias = {
        a.team_key: a.alias
        for a in session.scalars(select(TeamAlias).where(TeamAlias.source == "football-data"))
    }

    def cmp(label, ours, theirs):
        nonlocal compared
        compared += 1
        if ours != theirs:
            mismatches.append(f"{label}: db={ours!r} source={theirs!r}")

    cmp("HomeTeam", alias[m.home_team], src["HomeTeam"])
    cmp("AwayTeam", alias[m.away_team], src["AwayTeam"])
    cmp("Date", m.match_date, _date(src["Date"]))
    cmp("FTR", m.result, src.get("FTR"))
    if src.get("Time"):
        cmp("Time", m.kickoff_local, src["Time"])
        local = m.kickoff_utc.astimezone(UK).strftime("%H:%M") if m.kickoff_utc else None
        cmp("kickoff_utc→UK", local, src["Time"])
    for field, col in INT_FIELDS.items():
        if col in src:
            cmp(col, getattr(m, field), int(src[col]) if src[col] != "" else None)

    prices = list(session.scalars(select(FdOdds).where(FdOdds.match_id == m.id)))
    n_prices = 0
    for o in prices:
        n_prices += 1
        cmp(o.source_column, o.price, float(src[o.source_column]))
    return {
        "match": m,
        "raw": raw,
        "compared": compared,
        "prices": n_prices,
        "mismatches": mismatches,
    }


def run_spot_check(engine: Engine, settings: Settings, n: int, seed: int) -> tuple[bool, str]:
    with Session(engine) as s:
        teams = {t.team_key: t.name for t in s.scalars(select(Team))}
        results = [check_match(s, settings, m) for m in sample_matches(s, seed, n)]
        lines = [
            "# M1 spot-check: imported matches vs source rows",
            "",
            f"_Run {datetime.now(UK):%Y-%m-%d %H:%M} UK time by `uv run footy spot-check "
            f"-n {n} --seed {seed}`. Sample: {PER_TOP_DIVISION} matches per top division and "
            f"{PER_SECOND_TIER} per second tier, seeded._",
            "",
            "Each row was re-read from the stored raw CSV revision with Python's `csv` module,",
            "independently of the importer, and every stored field and price compared with it.",
            "",
            "| # | Division | Season | Date | Kick-off (UK) | Match | Score | Values compared"
            " | Of which prices | Mismatches |",
            "| ---: | --- | --- | --- | --- | --- | --- | ---: | ---: | ---: |",
        ]
        total_mismatch = 0
        for i, r in enumerate(results, 1):
            m = r["match"]
            total_mismatch += len(r["mismatches"])
            lines.append(
                f"| {i} | {m.division} | {season_label(m.season)} | {m.match_date} "
                f"| {m.kickoff_local or '–'} | {teams[m.home_team]} v {teams[m.away_team]} "
                f"| {m.home_goals}–{m.away_goals} | {r['compared']} | {r['prices']} "
                f"| {len(r['mismatches'])} |"
            )
        lines.append("")
        total = sum(r["compared"] for r in results)
        lines.append(
            f"**Result: {total} values compared across {len(results)} matches, "
            f"{total_mismatch} mismatches.**"
        )
        for r in results:
            for mm in r["mismatches"]:
                lines.append(f"- {r['match'].id}: {mm}")
        lines.append("")
    return total_mismatch == 0, "\n".join(lines)


def _date(s: str):
    for fmt in ("%d/%m/%Y", "%d/%m/%y"):
        try:
            return datetime.strptime(s, fmt).date()  # noqa: DTZ007 (date only)
        except ValueError:
            pass
    return s
