"""UEFA loader safety (audit 22 Oct 2026: H5, M3, M14, M16). Every page, CSV and HTTP response
here is SYNTHETIC; club names are real only so that the committed alias table resolves them."""

import csv
import importlib.util
import json
from datetime import UTC, date, datetime, timedelta
from functools import partial

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from footy import cli
from footy.europe import build_2627, euro_load
from footy.europe.build_2627 import build_all, fetch_wikipedia
from footy.europe.euro_load import mark_interrupted_runs, run_euro_update
from footy.europe.wiki_fetch import WikiClient, WikiPage, fetch_wiki_page
from footy.europe.wikipedia_2627 import (
    CSV_COLUMNS,
    PAGES,
    apply_venue_overrides,
    build_uefa_2627,
    parse_page,
)
from footy.ingest.raw_store import store_raw
from footy.models import DataCorrection, EuroMatch, FetchLog, JobRun

QSPEC = PAGES[3]  # UCLQ, "ucl/2627/qualifying"
UELSPEC = PAGES[1]  # UEL league phase
CEST = "Times are [[Central European Summer Time|CEST]].\n"
RETRIEVED = datetime(2026, 7, 25, tzinfo=UTC)


def box(day, home, home_code, away, away_code, score="", extra="", month=7):
    return (
        "{{#invoke:football box|main\n"
        f"|date = {{{{Start date|2026|{month}|{day}|df=y}}}}\n|time = 19:00\n"
        f"|team1 = {home} {{{{fbaicon|{home_code}}}}}\n|score = {score}\n"
        f"|team2 = {{{{fbaicon|{away_code}}}}} {away}\n{extra}}}}}\n"
    )


def goals(*minutes):
    return "".join(f"*X {{{{goal|{m}}}}}\n" for m in minutes)


def qualifying(*boxes, summary=""):
    return CEST + "==First qualifying round==\n" + summary + "===Matches===\n" + "".join(boxes)


def store_page(s, raw_dir, spec, wikitext, revid, retrieved=RETRIEVED):
    body = {"parse": {"title": spec.page.title, "revid": revid, "wikitext": wikitext}}
    return store_raw(
        s,
        raw_dir,
        source="wikipedia",
        logical_key=spec.page.logical_key,
        content=json.dumps(body).encode(),
        retrieved_at=retrieved,
        suffix=".json",
    ).raw


def euro_rows(engine):
    with Session(engine) as s:
        return {(m.home_team, m.away_team): m for m in s.scalars(select(EuroMatch))}


def last_run(engine, job="euro_update"):
    with Session(engine) as s:
        return s.scalars(select(JobRun).where(JobRun.job == job).order_by(JobRun.id.desc())).first()


# --- H5: a box the parser can't read never deletes its stored row ------------------------


def test_unparsable_box_does_not_delete_its_row(settings, migrated_engine, monkeypatch, tmp_path):
    monkeypatch.setattr(build_2627, "build_uefa_2627", partial(build_uefa_2627, specs=(QSPEC,)))
    played = box(8, "Víkingur Reykjavík", "ISL", "Hapoel Be'er Sheva", "ISR", "2–1")
    page = qualifying(box(7, "Vardar", "MKD", "KuPS", "FIN", "0–2"), played)
    with Session(migrated_engine) as s:
        store_page(s, settings.raw_dir, QSPEC, page, revid=1)
        s.commit()
    no_clone = tmp_path / "no-clone"
    run_euro_update(migrated_engine, settings, openfootball_dir=no_clone)
    assert len(euro_rows(migrated_engine)) == 2
    assert last_run(migrated_engine).status == "ok"

    # A new revision whose box for a played match can't be read ("Report" in the score).
    with Session(migrated_engine) as s:
        store_page(s, settings.raw_dir, QSPEC, page.replace("|score = 2–1", "|score = Report"), 2)
        s.commit()
    detail = run_euro_update(migrated_engine, settings, openfootball_dir=no_clone)
    rows = euro_rows(migrated_engine)
    kept = rows[("isl-vikingur-reykjavik", "isr-hapoel-beer-sheva")]
    assert (kept.status, kept.home_goals, kept.away_goals) == ("played", 2, 1)
    assert detail["wikipedia"]["deleted"] == 0 and detail["wikipedia"]["kept_rows"] == 1
    assert detail["wikipedia"]["rebuild_errors"][0].startswith("unparsed_score: Report")
    assert last_run(migrated_engine).status == "partial"


def test_awarded_walkover_abandoned_replay_and_postponed():
    page = qualifying(
        box(7, "A", "ENG", "B", "ESP", "3–0 (awd.)"),
        box(7, "C", "ENG", "D", "ESP", "{{abbr|w/o|Walkover}}"),
        box(8, "E", "ENG", "F", "ESP", "1–0 (abandoned)"),
        box(9, "E", "ENG", "F", "ESP", "2–0", "|goals1 =\n" + goals(10, 20)),
        box(15, "F", "ESP", "E", "ENG", "1–1", "|goals1 =\n" + goals(5) + "|goals2 =\n" + goals(6)),
        box(15, "G", "ENG", "H", "ESP", "P–P"),
    )
    parsed = parse_page(QSPEC, page, revid=1, retrieved_at=RETRIEVED)
    got = [(r.home_name, r.match_date.day, r.status, r.stage, r.home_goals, r.away_goals)
           for r in parsed.rows]  # fmt: skip
    assert got == [
        ("A", 7, "awarded", "Q1 leg 1", 3, 0),
        ("C", 7, "awarded", "Q1 leg 1", None, None),
        ("E", 8, "postponed", "Q1 leg 1", None, None),  # the abandoned match is not a leg
        ("E", 9, "played", "Q1 leg 1", 2, 0),  # its replay is the first leg
        ("F", 15, "played", "Q1 leg 2", 1, 1),
        ("G", 15, "postponed", "Q1 leg 1", None, None),
    ]
    assert not [i for i in parsed.issues if i.severity == "error"]


def test_status_rows_load_with_exclusions(tmp_path):
    path = tmp_path / "uefa_2627.csv"
    base = {c: "" for c in CSV_COLUMNS} | {
        "competition": "UCLQ",
        "season": "2627",
        "stage": "Q1 leg 1",
        "home_name": "Vardar",
        "home_country": "MKD",
        "away_name": "KuPS",
        "away_country": "FIN",
        "extra_time": "0",
        "penalties": "0",
        "neutral": "0",
        "source": "wikipedia",
        "source_ref": "page (rev 1, line 1)",
    }
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        w.writeheader()
        w.writerow(base | {"match_date": "2026-07-07", "status": "awarded", "home_goals": "3",
                           "away_goals": "0"})  # fmt: skip
        w.writerow(base | {"match_date": "2026-07-08", "status": "postponed"})
    aliases = {"Vardar (MKD)": "mkd-vardar", "KuPS (FIN)": "fin-kups"}
    rows, issues = euro_load.wikipedia_rows(path, aliases, {})
    assert [(r.status, r.home_goals, r.model_exclusion) for r in rows] == [
        ("awarded", 3, "awarded"),
        ("postponed", None, None),
    ]


def _csv_row(**kw):
    row = {c: "" for c in CSV_COLUMNS} | {
        "competition": "UCL",
        "season": "2627",
        "stage": "League MD3",
        "match_date": "2026-10-21",
        "kickoff_utc": "2026-10-21T19:00:00+00:00",
        "home_name": "Bayern Munich",
        "home_country": "DEU",
        "away_name": "Arsenal",
        "away_country": "ENG",
        "extra_time": "0",
        "penalties": "0",
        "neutral": "0",
        "status": "played",
        "home_goals": "2",
        "away_goals": "1",
        "source": "wikipedia",
        "source_ref": "Synthetic page#Matchday 3 (rev 5, UEFA match 1)",
    }
    return row | kw


def _write(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        w.writeheader()
        w.writerows(rows)


def test_result_rows_are_deleted_only_with_a_correction(settings, migrated_engine, tmp_path):
    with Session(migrated_engine) as s:
        raw = store_page(s, settings.raw_dir, PAGES[0], "x", revid=5)
        raw_id = raw.id
        s.commit()
    csv_path = settings.data_dir / "europe" / "uefa_2627.csv"
    unlinked = _csv_row(home_name="Como", home_country="ITA", away_name="Manchester United",
                        source_ref="Synthetic page#Matchday 3 (rev 999, UEFA match 2)")  # fmt: skip
    _write(csv_path, [_csv_row(), unlinked])
    run = partial(
        run_euro_update,
        migrated_engine,
        settings,
        openfootball_dir=tmp_path / "no-clone",
        wiki_csv=csv_path,
        rebuild_2627=False,
    )
    run()
    assert len(euro_rows(migrated_engine)) == 2
    # Both played rows disappear from the source (another row keeps the group in the load).
    _write(csv_path, [_csv_row(home_name="Real Madrid", home_country="ESP", away_name="RB Leipzig",
                               away_country="DEU", status="scheduled", home_goals="",
                               away_goals="")])  # fmt: skip
    detail = run()["wikipedia"]
    rows = euro_rows(migrated_engine)
    assert ("deu-bayern-munich", "eng-arsenal") not in rows
    with Session(migrated_engine) as s:
        (c,) = s.scalars(select(DataCorrection)).all()
    assert (c.entity, c.field, c.new_value) == ("euro_match", "deleted", None)
    assert "deu-bayern-munich v eng-arsenal played 2-1" in c.old_value
    assert c.old_raw_file_id == c.new_raw_file_id == raw_id
    # A result row with no raw revision to name can't be recorded, so it stays (an error).
    assert ("ita-como", "eng-man-united") in rows
    assert detail["deleted"] == 1 and detail["kept_rows"] == 1
    assert detail["issues"]["result_row_kept"] == 1
    assert last_run(migrated_engine).status == "partial"


# --- M3: failed fetches are logged with their body, stop the sweep, and show ---------------


def _client(handler, seen):
    def record(request):
        seen.append(request)
        return handler(request)

    http = httpx.Client(transport=httpx.MockTransport(record))
    return WikiClient(client=http, sleep=lambda _s: None)


BLOCK_PAGE = ("<html><body>Forbidden: this client is blocked. " + "x" * 2000).encode()


def test_refused_fetch_is_logged_stops_the_sweep_and_marks_the_job(
    settings, migrated_engine, monkeypatch, tmp_path
):
    seen: list = []
    client = _client(lambda r: httpx.Response(403, content=BLOCK_PAGE), seen)
    monkeypatch.setattr(euro_load, "_wiki_client", lambda: client)
    csv_path = settings.data_dir / "europe" / "uefa_2627.csv"
    _write(csv_path, [_csv_row()])
    detail = run_euro_update(
        migrated_engine,
        settings,
        openfootball_dir=tmp_path / "no-clone",
        wiki_csv=csv_path,
        rebuild_2627=False,
        fetch_wikipedia=True,
    )
    assert len(seen) == 1  # the first refusal stops every later request of the job
    assert detail["wikipedia_gap"]["fetch"]["error"] == 1
    assert detail["wikipedia"]["fetch_wikipedia"] == {"skipped": len(PAGES)}
    assert detail["wikipedia"]["fetch_errors"]
    run = last_run(migrated_engine)
    assert run.status == "partial"
    with Session(migrated_engine) as s:
        (log,) = s.scalars(select(FetchLog)).all()
    assert log.http_status == 403 and log.job_run_id == run.id
    assert log.error.startswith("HTTP 403: <html><body>Forbidden: this client is blocked.")
    assert len(log.error) == len("HTTP 403: ") + 500


def test_rate_limit_is_waited_out_once_at_most(settings, migrated_engine):
    page = WikiPage("ucl/2627/league-phase", "T")
    ok = httpx.Response(200, json={"parse": {"title": "T", "revid": 3, "wikitext": "x"}})
    with Session(migrated_engine) as s:
        seen: list = []
        long_wait = _client(
            lambda r: httpx.Response(429, headers={"Retry-After": "600"}, content=b"slow down"),
            seen,
        )
        res = fetch_wiki_page(s, long_wait, settings.raw_dir, page)
        assert len(seen) == 1 and res.http_status == 429  # no retry loop
        assert res.error == "HTTP 429 (Retry-After 600): slow down"
        assert long_wait.refused == "ucl/2627/league-phase: HTTP 429"

        seen = []
        replies = [httpx.Response(429, headers={"Retry-After": "5"}), ok]
        short_wait = _client(lambda r: replies.pop(0), seen)
        res = fetch_wiki_page(s, short_wait, settings.raw_dir, page)
        assert len(seen) == 2 and res.outcome == "new_revision"
        assert res.error == "after retrying HTTP 429 (Retry-After 5)"
        assert short_wait.refused is None
        # A sweep with a client that was refused asks nothing.
        out = fetch_wikipedia(migrated_engine, settings.raw_dir, client=long_wait)
        assert {r.outcome for r in out} == {"skipped"}


def test_euro_update_exits_1_when_fetches_fail(tmp_path, monkeypatch, capsys):
    from footy import config, db

    url = f"sqlite:///{tmp_path / 'cli.sqlite3'}"
    db.upgrade_to_head(url)
    monkeypatch.setenv("FOOTY_DATABASE_URL", url)
    monkeypatch.setenv("FOOTY_DATA_DIR", str(tmp_path / "data"))
    monkeypatch.setenv("FOOTY_OPENFOOTBALL_DIR", str(tmp_path / "no-clone"))
    seen: list = []
    client = _client(lambda r: httpx.Response(403, content=b"blocked"), seen)
    monkeypatch.setattr(euro_load, "_wiki_client", lambda: client)
    _write(tmp_path / "data" / "europe" / "uefa_2627.csv", [_csv_row()])
    config.get_settings.cache_clear()
    db.get_engine.cache_clear()
    try:
        assert cli.main(["euro-update", "--fetch-wikipedia", "--no-rebuild"]) == 1
        assert '"fetch_errors"' in capsys.readouterr().out
    finally:
        config.get_settings.cache_clear()
        db.get_engine.cache_clear()


def test_stale_running_jobs_are_marked_interrupted(migrated_engine):
    now = datetime(2026, 10, 22, 18, 0, tzinfo=UTC)
    with Session(migrated_engine) as s:
        s.add(JobRun(job="predict", started_at=now - timedelta(hours=9), status="running"))
        s.add(JobRun(job="settle", started_at=now - timedelta(minutes=5), status="running"))
        s.commit()
    assert mark_interrupted_runs(migrated_engine, now=now) == [1]
    with Session(migrated_engine) as s:
        old, recent = s.scalars(select(JobRun).order_by(JobRun.id)).all()
    assert old.status == "interrupted" and "killed" in old.error
    assert recent.status == "running"


# --- M14: a score after extra time is never stored as the 90-minute score ------------------


def test_extra_time_without_a_marker_is_recognised():
    late_goal = qualifying(
        box(7, "A", "ENG", "B", "ESP", "1–0", "|goals1 =\n" + goals(10)),
        box(14, "B", "ESP", "A", "ENG", "2–0", "|goals1 =\n" + goals(30, 100)),
    )
    parsed = parse_page(QSPEC, late_goal, revid=1, retrieved_at=RETRIEVED)
    second = parsed.rows[1]
    assert second.extra_time and second.final_score == (2, 0)
    assert (second.home_goals, second.away_goals) == (1, 0)  # level on aggregate after 90
    assert "extra_time_inferred" in [i.code for i in parsed.issues]

    shoot_out = qualifying(
        box(7, "A", "ENG", "B", "ESP", "0–0"),
        box(
            14,
            "B",
            "ESP",
            "A",
            "ENG",
            "2–2",
            "|penaltyscore = 4–3\n|goals1 =\n" + goals(20, 95) + "|goals2 =\n" + goals(50, 110),
        ),
    )
    second = parse_page(QSPEC, shoot_out, revid=1, retrieved_at=RETRIEVED).rows[1]
    assert second.extra_time and second.penalties
    assert (second.home_goals, second.away_goals) == (1, 1)


def test_summary_aet_marker_and_inconsistent_ties_hold_the_score():
    summary = "===Summary===\n{{#invoke:Sports series|main\n|A|ENG|4–3|B|ESP|1–1|3–2 {{aet}}\n}}\n"
    page = qualifying(
        box(7, "A", "ENG", "B", "ESP", "1–1"),
        box(14, "B", "ESP", "A", "ENG", "2–3"),  # no aet, no goal list
        summary=summary,
    )
    parsed = parse_page(QSPEC, page, revid=1, retrieved_at=RETRIEVED)
    second = parsed.rows[1]
    assert second.extra_time and (second.home_goals, second.away_goals) == (None, None)
    codes = [(i.severity, i.code) for i in parsed.issues]
    assert ("error", "regulation_score_unknown") in codes

    level_without_extra_time = qualifying(
        box(7, "A", "ENG", "B", "ESP", "1–0"), box(14, "B", "ESP", "A", "ENG", "1–0")
    )
    parsed = parse_page(QSPEC, level_without_extra_time, revid=1, retrieved_at=RETRIEVED)
    assert [(r.status, r.home_goals) for r in parsed.rows] == [("played", None)] * 2
    assert [i.code for i in parsed.issues if i.severity == "error"] == ["tie_inconsistent"]


def test_league_phase_goal_after_90_is_not_extra_time():
    page = (
        CEST
        + "===Matchday 4===\n"
        + box(
            4,
            "A",
            "ENG",
            "B",
            "ESP",
            "2–1",
            "|goals1 =\n" + goals(10, 93) + "|goals2 =\n" + goals(50),
            month=11,
        )  # fmt: skip
    )
    (row,) = parse_page(PAGES[0], page, revid=1).rows
    assert not row.extra_time and (row.home_goals, row.away_goals) == (2, 1)


# --- M16: UEFA.com content sets nothing ----------------------------------------------------


def test_neutral_venue_comes_from_the_committed_override_not_uefa(settings, migrated_engine):
    assert importlib.util.find_spec("footy.europe.uefa_crosscheck") is None
    page = (
        CEST
        + "===Matchday 3===\n"
        + box(
            22,
            "[[Royale Union Saint-Gilloise|Union Saint-Gilloise]]",
            "BEL",
            "[[Hapoel Be'er Sheva F.C.|Hapoel Be'er Sheva]]",
            "ISR",
            extra="|stadium = [[Merkur Spiel-Arena]]\n"
            "|report = [https://www.uefa.com/uefaeuropaleague/match/2050097/ Report]\n",
            month=10,
        )
    )
    with Session(migrated_engine) as s:
        store_page(
            s, settings.raw_dir, UELSPEC, page, 7, retrieved=datetime(2026, 10, 21, tzinfo=UTC)
        )
        # A stored UEFA.com page for the same match is not read.
        store_raw(
            s,
            settings.raw_dir,
            source="uefa",
            logical_key="uel/2627/match/2050097",
            content=b'<script type="application/ld+json">{"@type": "SportsEvent"}</script>',
            retrieved_at=RETRIEVED,
        )
        built = build_all(s, settings.raw_dir, settings.data_dir)
    (row,) = built.wikipedia.rows
    assert row.neutral and row.match_date == date(2026, 10, 22)
    assert "German Wikipedia" in row.source_ref and "oldid 271428890" in row.source_ref
    assert "UEFA.com" not in row.source_ref
    assert "(rev 7, UEFA match 2050097)" in row.source_ref  # the revision link still parses
    assert euro_load._revid(row.source_ref) == 7


def test_unmatched_venue_override_is_reported():
    issues = apply_venue_overrides([])
    assert [i.code for i in issues] == ["venue_override_unmatched"]
