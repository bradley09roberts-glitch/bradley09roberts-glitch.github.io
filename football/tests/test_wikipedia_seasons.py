"""2025/26 gap seasons from Wikipedia, and the openfootball cross-check.

The wikitext here is SYNTHETIC, written in the layout of Wikipedia's football boxes (made-up
clubs, dates and scores); it is not copied from any page."""

import json
from datetime import UTC, date, datetime

from sqlalchemy.orm import Session

from footy.europe.of_crosscheck import pair_boxes, wiki_boxes
from footy.europe.openfootball import parse_openfootball
from footy.europe.wiki_fetch import WikiPage
from footy.europe.wikipedia_seasons import (
    GapPage,
    gap_matches,
    knockout_stage,
    league_stage,
)
from footy.ingest.raw_store import store_raw
from footy.models import utcnow


def _box(day: str, time: str, home: str, hc: str, score: str, away: str, ac: str, **kw) -> str:
    y, m, d = day.split("-")
    extra = "".join(f"\n|{k} = {v}" for k, v in kw.items())
    return (
        "{{#invoke:football box|main\n"
        f"|date = {{{{Start date|{y}|{int(m)}|{int(d)}|df=y}}}}\n"
        f"|time = {time}\n"
        f"|team1 = [[{home}]] {{{{fbaicon|{hc}}}}}\n"
        f"|score = {score}\n"
        f"|team2 = {{{{fbaicon|{ac}}}}} [[{away}]]{extra}\n"
        "}}\n"
    )


KNOCKOUT = (
    "Times are [[Central European Time|CET]]/[[Central European Summer Time|CEST]], as listed "
    "by UEFA.\n\n==Round of 16==\n===Matches===\n"
    + _box("2026-03-12", "18:45", "Alpha", "GER", "1–0", "Beta", "AUT",
           goals1="*A {{goal|10}}", stadium="[[Alpha Park]], [[Alphaburg]]")
    + _box("2026-03-19", "21:00 {{small|(21:00 [[UTC+01:00|UTC+1]])}}", "Beta", "AUT", "2–0",
           "Alpha", "GER", aet="y", goals1="*B {{goal|30}}\n*B {{goal|111}}",
           stadium="[[Beta Arena]], [[Vienna]]")
    + "\n==Quarter-finals==\n===Matches===\n"
    + _box("2026-04-09", "21:00", "Gamma", "UKR", "0–0", "Beta", "AUT",
           stadium="[[Arena]], [[Kraków]], Poland")
    + _box("2026-04-16", "21:00", "Beta", "AUT", "1–1", "Gamma", "UKR",
           goals1="*B {{goal|5}}", goals2="*G {{goal|80}}")
)  # fmt: skip


def _store(session, raw_dir, key: str, wikitext: str, revid: int) -> int:
    body = {"parse": {"title": key, "revid": revid, "wikitext": wikitext}}
    raw = store_raw(
        session,
        raw_dir,
        source="wikipedia",
        logical_key=key,
        content=json.dumps(body).encode(),
        retrieved_at=utcnow(),
        suffix=".json",
    ).raw
    return raw.id


def test_stage_headings():
    assert league_stage(("Matches", "Matchday 3")) == "League MD3"
    assert league_stage(("Matches",)) is None
    assert knockout_stage(("Knockout phase play-offs", "Matches")) == "Knockout play-off"
    assert knockout_stage(("Round of 16", "Matches")) == "Round of 16"
    assert knockout_stage(("Quarter-finals", "Matches")) == "Quarter-final"
    assert knockout_stage(("Semi-finals", "Matches")) == "Semi-final"


def test_knockout_page_rows(tmp_path, migrated_engine):
    raw_dir = tmp_path / "raw"
    with Session(migrated_engine) as s:
        raw_id = _store(s, raw_dir, "uel/2526/knockout-phase", KNOCKOUT, 1)
        spec = GapPage("UEL", "2526", "knockout", WikiPage("uel/2526/knockout-phase", "KO"))
        result = gap_matches(s, raw_dir, pages=(spec,))
    m = result.matches
    assert [x.stage for x in m] == [
        "Round of 16 leg 1",
        "Round of 16 leg 2",
        "Quarter-final leg 1",
        "Quarter-final leg 2",
    ]
    assert m[0].home_alias == "Alpha (DEU)" and m[0].away_alias == "Beta (AUT)"
    assert m[0].kickoff_utc == datetime(2026, 3, 12, 17, 45, tzinfo=UTC)  # 18:45 CET
    # After extra time the regulation score comes from the goal minutes: 1-0, then 2-0.
    assert (m[1].home_goals, m[1].away_goals, m[1].extra_time) == (1, 0, True)
    assert m[1].model_exclusion is None
    # The stadium line names another country: Gamma's home match is neutral.
    assert m[2].neutral and not m[3].neutral
    # Level on aggregate after 90 minutes but no extra time: the tie is excluded.
    assert [x.model_exclusion for x in m[2:]] == ["tie_inconsistent"] * 2
    assert (raw_id, "warning", "level_without_extra_time") in {i[:3] for i in result.issues}
    assert result.pages == {"uel/2526/knockout-phase": "rev 1"}


def test_crosscheck_pairs_boxes_with_openfootball_rows():
    of = """\
= UEFA Europa League 2025/26

▪ Round of 16
  Thu Mar 12 2026
    18:45  Alpha FC (GER)          v Beta SK (AUT)            1-0 (1-0)
  Thu Mar 19
    21:00  Beta SK (AUT)           v Alpha FC (GER)           2-0 a.e.t. (1-0, 1-0)
"""
    matches = parse_openfootball(of.encode(), "el.txt", "2526").matches
    boxes = wiki_boxes(KNOCKOUT, "Synthetic")[:2]
    result = pair_boxes(matches, boxes)
    assert len(result.comparisons) == 2 and not result.unmatched_boxes
    for c in result.comparisons:
        assert c.kickoff_agrees and c.regulation_agrees and c.final_agrees and c.flags_agree
    assert result.comparisons[1].box.venue_local_time == "21:00 UTC+01:00"
    assert result.comparisons[0].of.match_date == date(2026, 3, 12)
