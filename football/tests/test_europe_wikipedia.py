"""Parser tests for the 2026/27 UEFA Wikipedia pages.

The two `.wiki` fixtures are verbatim excerpts of English Wikipedia (CC BY-SA 4.0), labelled
with page and revision inside the files. Everything else here is synthetic.
"""

import csv
import json
from datetime import UTC, date, datetime
from pathlib import Path

import httpx
from sqlalchemy.orm import Session

from footy.europe.build_2627 import build_all
from footy.europe.wiki_fetch import USER_AGENT, WikiClient, WikiPage, fetch_wiki_page
from footy.europe.wikipedia_2627 import (
    CSV_COLUMNS,
    PAGES,
    PageSpec,
    goal_minutes,
    kickoff_from_box,
    parse_page,
    regulation_goals,
    stadium_country,
)
from footy.ingest.raw_store import store_raw

FIXTURES = Path(__file__).parent / "fixtures" / "wikipedia"
UCL = PageSpec("UCL", "league", WikiPage("ucl/2627/league-phase", "UCL league phase (excerpt)"))
UCLQ = PageSpec("UCLQ", "qualifying", WikiPage("ucl/2627/qualifying", "UCL qualifying (excerpt)"))
RETRIEVED = datetime(2026, 10, 21, 0, 1, tzinfo=UTC)


def excerpt(name: str) -> str:
    return (FIXTURES / name).read_text(encoding="utf-8")


def league_rows():
    parsed = parse_page(
        UCL,
        excerpt("ucl_2627_league_phase_excerpt.wiki"),
        revid=1381234244,
        retrieved_at=RETRIEVED,
    )
    return parsed, {(r.home_name, r.away_name): r for r in parsed.rows}


def test_league_excerpt_played_and_scheduled_rows():
    parsed, rows = league_rows()
    assert len(parsed.rows) == 3

    played = rows[("Liverpool", "Villarreal")]
    assert played.status == "played"
    assert (played.home_goals, played.away_goals) == (4, 3)  # written with U+2212 minus
    assert played.stage == "League MD3"
    assert played.match_date == date(2026, 10, 20)
    assert played.kickoff_utc == datetime(2026, 10, 20, 19, 0, tzinfo=UTC)  # 21:00 CEST
    assert (played.home_country, played.away_country) == ("ENG", "ESP")
    assert not (played.extra_time or played.penalties or played.neutral)
    assert played.uefa_match_id == "2049590"
    assert "Matchday 3" in played.source_ref and "rev 1381234244" in played.source_ref

    tonight = rows[("Bayern Munich", "Arsenal")]
    assert tonight.status == "scheduled"
    assert tonight.home_goals is None and tonight.away_goals is None
    assert tonight.kickoff_utc == datetime(2026, 10, 21, 19, 0, tzinfo=UTC)
    assert tonight.home_country == "DEU"


def test_league_excerpt_neutral_venue_and_local_time_check():
    parsed, rows = league_rows()
    shakhtar = rows[("Shakhtar Donetsk", "AEK Athens")]
    assert shakhtar.neutral  # Stamford Bridge, London, England
    assert shakhtar.home_country == "UKR"
    assert shakhtar.stage == "League MD2"
    # The box shows the London time as "20:00 UTC+0", but London was on BST (UTC+1) on
    # 14 Oct 2026: the CET-based kick-off is kept and the inconsistency is reported.
    assert shakhtar.kickoff_utc == datetime(2026, 10, 14, 19, 0, tzinfo=UTC)
    codes = [i.code for i in parsed.issues]
    assert "venue_time_mismatch" in codes
    # An excerpt is not a whole league phase, so the structural counts fire; nothing else.
    structural = {"team_count", "fixture_count", "fixtures_per_team"}
    assert set(codes) - structural == {"venue_time_mismatch"}


def test_qualifying_excerpt_extra_time_regulation_score_and_summary():
    parsed = parse_page(
        UCLQ, excerpt("ucl_2627_qualifying_excerpt.wiki"), revid=1380434887, retrieved_at=RETRIEVED
    )
    assert parsed.issues == []  # both legs agree with the page's own summary table
    first, second = parsed.rows
    assert (first.home_name, first.away_name, first.stage) == ("Vardar", "KuPS", "Q1 leg 1")
    assert (first.home_goals, first.away_goals, first.extra_time) == (0, 2, False)

    assert (second.home_name, second.away_name, second.stage) == ("KuPS", "Vardar", "Q1 leg 2")
    assert second.final_score == (2, 3)  # after extra time, as Wikipedia lists it
    assert (second.home_goals, second.away_goals) == (0, 2)  # 59' and 90+1' only
    assert second.extra_time and not second.penalties
    # 17:00 CEST, shown as 18:00 UTC+3 in Kuopio.
    assert second.kickoff_utc == datetime(2026, 7, 14, 15, 0, tzinfo=UTC)
    assert second.match_date == date(2026, 7, 14)


def test_goal_minutes_handles_missing_empty_type():
    # Synthetic: {{goal}} normally alternates minute and type, but pages drop empty types.
    assert goal_minutes("*A {{goal|80||85|pen.}}") == [(80, 0), (85, 0)]
    assert goal_minutes("*B {{goal|72|120+5|pen}}") == [(72, 0), (120, 5)]
    assert regulation_goals("*C {{goal|45+2}} *D {{goal|90+4}} *E {{goal|91}}") == 2


def test_kickoff_conversion_follows_cet_cest_change():
    # Synthetic: the clocks go back on 25 Oct 2026.
    before, _ = kickoff_from_box(date(2026, 10, 21), "18:45")
    after, _ = kickoff_from_box(date(2026, 11, 4), "18:45")
    assert before == datetime(2026, 10, 21, 16, 45, tzinfo=UTC)
    assert after == datetime(2026, 11, 4, 17, 45, tzinfo=UTC)
    _, mismatch = kickoff_from_box(
        date(2026, 10, 21), "21:00 {{small|(23:00 [[UTC+04:00|UTC+4]])}}"
    )
    assert mismatch is None
    _, mismatch = kickoff_from_box(
        date(2026, 10, 21), "21:00 {{small|(23:00 [[UTC+03:00|UTC+3]])}}"
    )
    assert mismatch is not None


def test_stadium_country_and_legacy_flag_codes():
    assert (
        stadium_country("[[Stamford Bridge (stadium)|Stamford Bridge]], [[London]], England")
        == "ENG"
    )
    assert stadium_country("[[Anfield]], [[Liverpool]]") is None
    synthetic = (
        "Times are [[Central European Summer Time|CEST]].\n===Matchday 1===\n"
        "{{#invoke:Football box|main\n|date = {{Start date|2026|9|16|df=y}}\n|time = 18:45\n"
        "|team1 = [[NK Celje|Celje]] {{fbaicon|SLO}}\n|score = 1–0\n"
        "|team2 = {{fbaicon|ROM}} [[FCSB]]\n|stadium = [[Stadion Z'dežele]], [[Celje]]\n}}\n"
    )
    parsed = parse_page(UCL, synthetic, revid=1)
    (row,) = parsed.rows
    assert (row.home_country, row.away_country) == ("SVN", "ROU")


def test_page_without_cet_statement_is_refused():
    synthetic = (
        "===Matchday 1===\n{{#invoke:Football box|main\n|date = {{Start date|2026|9|16}}\n}}"
    )
    parsed = parse_page(UCL, synthetic, revid=1)
    assert parsed.rows == []
    assert [i.code for i in parsed.issues] == ["time_basis_unknown"]


def test_wiki_client_honours_retry_after():
    responses = [
        httpx.Response(429, headers={"Retry-After": "14"}),
        httpx.Response(200, json={"parse": {"title": "T", "revid": 7, "wikitext": "x"}}),
    ]
    seen: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        seen.append(request)
        return responses.pop(0)

    sleeps: list[float] = []
    http = httpx.Client(transport=httpx.MockTransport(handler), headers={"User-Agent": USER_AGENT})
    client = WikiClient(min_interval=2.0, client=http, sleep=sleeps.append)
    resp = client.get("https://en.wikipedia.org/w/api.php", params={"action": "parse"})
    assert resp.status_code == 200
    assert max(sleeps) >= 14
    assert seen[0].headers["User-Agent"].startswith("footy-personal-model/0.1")


def test_fetch_store_then_build_csv(migrated_engine, settings):
    """Raw response is stored first; the CSV is built only from the raw store."""
    spec = PAGES[0]
    wikitext = excerpt("ucl_2627_league_phase_excerpt.wiki")
    body = {"parse": {"title": spec.page.title, "revid": 1381234244, "wikitext": wikitext}}

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.params["page"] == spec.page.title
        return httpx.Response(200, json=body)

    http = httpx.Client(transport=httpx.MockTransport(handler))
    client = WikiClient(client=http, sleep=lambda _s: None)
    with Session(migrated_engine) as s:
        res = fetch_wiki_page(s, client, settings.raw_dir, spec.page)
        assert res.outcome == "new_revision" and res.revid == 1381234244
        again = fetch_wiki_page(s, client, settings.raw_dir, spec.page)
        assert again.outcome == "unchanged" and again.raw_file_id == res.raw_file_id
        # A stored UEFA.com page is never read (terms 6.2, D-023): a page that would make
        # Bayern v Arsenal neutral changes nothing.
        ld = {
            "@type": "SportsEvent",
            "url": "https://www.uefa.com/uefachampionsleague/match/2049598/",
            "location": {"address": {"addressCountry": "ITA"}},
        }
        store_raw(
            s,
            settings.raw_dir,
            source="uefa",
            logical_key="ucl/2627/match/2049598",
            content=f'<script type="application/ld+json">{json.dumps(ld)}</script>'.encode(),
            retrieved_at=RETRIEVED,
        )
        built = build_all(s, settings.raw_dir, settings.data_dir)
        s.commit()

    with built.paths["matches"].open(encoding="utf-8", newline="") as f:
        reader = csv.DictReader(f)
        assert reader.fieldnames == CSV_COLUMNS
        out = list(reader)
    assert len(out) == 3
    bayern = next(r for r in out if r["home_name"] == "Bayern Munich")
    assert bayern["kickoff_utc"] == "2026-10-21T19:00:00+00:00"
    assert (bayern["status"], bayern["home_goals"], bayern["neutral"]) == ("scheduled", "", "0")
    assert "UEFA.com" not in bayern["source_ref"]
    assert set(built.paths) == {"matches", "teams", "build_errors"}  # no UEFA cross-check
    assert built.wikipedia.missing_pages == [p.page.logical_key for p in PAGES[1:]]
    # The pages not stored yet are build errors: a marker says the CSV lacks their rows.
    marker = json.loads(built.paths["build_errors"].read_text(encoding="utf-8"))
    missing = [f"missing page: {p.page.logical_key}" for p in PAGES[1:]]
    assert marker["problems"][-len(missing) :] == missing


def test_goal_list_that_disagrees_with_score_is_reported():
    synthetic = (
        "Times are [[Central European Time|CET]].\n===Matchday 4===\n"
        "{{#invoke:Football box|main\n|date = {{Start date|2026|11|3|df=y}}\n|time = 21:00\n"
        "|team1 = [[A FC|A]] {{fbaicon|ENG}}\n|score = 2–0\n|team2 = {{fbaicon|ESP}} [[B CF|B]]\n"
        "|goals1 =\n*X {{goal|12}}\n|goals2 =\n}}\n"
    )
    parsed = parse_page(UCL, synthetic, revid=1)
    (row,) = parsed.rows
    assert (row.home_goals, row.away_goals) == (2, 0)  # the score line is what counts
    assert row.kickoff_utc == datetime(2026, 11, 3, 20, 0, tzinfo=UTC)  # CET after 25 Oct
    assert "goal_list_mismatch" in [i.code for i in parsed.issues]
