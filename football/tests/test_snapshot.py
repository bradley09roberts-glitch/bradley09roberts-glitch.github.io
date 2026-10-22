"""The static snapshot exporter (footy.snapshot, `footy export-snapshot`).

SYNTHETIC TEST DATA: fictional teams (fdfixtures), made-up probabilities, a made-up backtest
ledger and report, a made-up "secret", a planted price-like field and a made-up crest (a few
bytes of PNG header). Nothing here is a real price, prediction, result, picture or credential.
"""

import itertools
import json
import sqlite3
from datetime import UTC, datetime, timedelta
from unittest.mock import ANY
from urllib.parse import quote

import pandas as pd
import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.api.main import create_app
from footy.config import Settings
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.publish import FixtureIn, publish, settle
from footy.snapshot import (
    FORMAT,
    FRONTEND_PAST_RECORD_TYPES,
    FRONTEND_REPORTS,
    FRONTEND_UEFA_CODES,
    PART_REF,
    SNAPSHOT_FIXTURE_DAYS,
    SnapshotRefused,
    body_only_html,
    canonical_key,
    find_prices,
    read_only_engine,
    run_export,
)

NOW = datetime.now(UTC).replace(microsecond=0)
DAY = (NOW + timedelta(days=2)).date()
KICKOFF = datetime(DAY.year, DAY.month, DAY.day, 14, 0, tzinfo=UTC)
SYNTHETIC_SECRET = "synthetic-secret-0123456789abcdef"  # SYNTHETIC: not a real credential

# What `npm run build:snapshot` writes (base "./"): relative, hashed asset names.
BUILT_INDEX = """<!doctype html>
<html lang="en-GB">
  <head>
    <meta charset="utf-8" />
    <title>Football model</title>
    <script type="module" crossorigin src="./assets/index-AbCd1234.js"></script>
    <link rel="stylesheet" crossorigin href="./assets/index-EfGh5678.css">
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>
"""


def fx(home: str, away: str, features: dict | None = None) -> FixtureIn:
    return FixtureIn(
        "E0",
        "2627",
        DAY,
        KICKOFF,
        home,
        away,
        features=features or {"home_matches_in_window": 40, "away_matches_in_window": 38},
        missing_fields=["lineups", "injuries", "odds"],
    )


def pub(engine, tmp_path, fixtures, model_name):
    n = len(fixtures)
    mp = markets_from_grid(poisson_grid([1.4] * n, [1.1] * n), [1.4] * n, [1.1] * n)
    return publish(
        engine,
        fixtures,
        mp,
        model_name=model_name,
        model_version="v-test",
        training_cutoff_utc=NOW - timedelta(days=1),
        data_freshness={"latest_result_by_division": {"E0": "2026-10-19"}},
        now=NOW,
        records_dir=tmp_path / "records",
    )


def write_run(root, run_id, manifest, rows):
    d = root / run_id
    d.mkdir(parents=True)
    (d / "manifest.json").write_text(json.dumps({"run_id": run_id, **manifest}))
    pd.DataFrame(rows).to_csv(d / "ledger.csv", index=False)


def ledger_rows(leagues, seasons, key="league"):
    """SYNTHETIC ledger: one predicted home win per league and season."""
    rows = []
    for i, (lg, ss) in enumerate(itertools.product(leagues, seasons)):
        rows.append(
            {
                key: lg,
                "season": ss,
                "status": "predicted",
                "batch_cutoff_utc": f"2024-01-0{1 + i % 2}T00:00:00+00:00",
                "home_goals": 2,
                "away_goals": 1,
                "p_H": 0.5,
                "p_D": 0.3,
                "p_A": 0.2,
                "rps": 0.1,
                "ll_1x2": 0.69,
                "brier_1x2": 0.38,
                "correct_1x2": 1,
            }
        )
    return rows


@pytest.fixture
def world(settings, migrated_engine, tmp_path):
    """Two fixtures (one settled), a model + market record with a planted price-like field,
    a domestic and a UEFA backtest run, one saved report and one job run."""
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    a, b = pub(
        migrated_engine,
        tmp_path,
        [fx("eng-testtown", "eng-sampleton"), fx("eng-sampleton", "eng-mockford")],
        "base_m",
    )
    market = {
        "books": ["Avg"],
        "p_H": 0.30,
        "p_D": 0.28,
        "p_A": 0.42,
        "quote_time_utc": "2026-10-20T12:00:00+00:00",
        "provenance": {"source": "SYNTHETIC", "planted_price": 2.38},
        "B365H": 3.3,  # SYNTHETIC price-like field: must never reach the snapshot
    }
    features = {
        "home_matches_in_window": 40,
        "away_matches_in_window": 38,
        "model_params": {"base": "base_m", "w_1x2": 1.0, "w_ou": 1.0, "devig": "shin"},
        "market_prices_used": market,
    }
    (tilt,) = pub(migrated_engine, tmp_path, [fx("eng-testtown", "eng-sampleton", features)], "t")
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-25', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, "
                "last_raw_file_id) VALUES ('E0', '2627', :d, 'eng-testtown', 'eng-sampleton', "
                "2, 1, 'H', 1, 1, 1)"
            ),
            {"d": DAY.isoformat()},
        )
        conn.execute(
            text(
                "INSERT INTO job_runs (job, started_at, finished_at, status, detail, error) "
                "VALUES ('fd_update', '2026-10-20 03:00:00', '2026-10-20 03:01:00', 'ok', "
                "'{\"files\": 1}', NULL)"
            )
        )
    settle(migrated_engine, now=KICKOFF + timedelta(hours=3))

    data = settings.data_dir
    write_run(
        data / "backtests",
        "synthetic_dc__validation__2324-2425",
        {
            "model": "synthetic_dc",
            "split": "validation",
            "seasons": ["2324", "2425"],
            "leagues": ["E0", "E1"],
            "created_at_utc": "2026-10-01T00:00:00+00:00",
        },
        ledger_rows(["E0", "E1"], ["2324", "2425"]),
    )
    write_run(
        data / "backtests_euro",
        "synthetic_euro__test__2425",
        {"model": "synthetic_euro", "split": "test", "seasons": ["2425"], "leagues": None},
        ledger_rows(["UCL"], ["2425"], key="comp"),
    )
    broken = data / "backtests" / "synthetic_broken__validation__2324"  # no ledger: a 500
    broken.mkdir()
    (broken / "manifest.json").write_text(json.dumps({"seasons": ["2324"], "leagues": ["E0"]}))
    (data / "reports").mkdir(parents=True)
    (data / "reports" / "model_selection.json").write_text(
        json.dumps({"_comment": "SYNTHETIC", "selected": "synthetic_dc"})
    )
    dist = tmp_path / "dist-snapshot"
    (dist / "assets").mkdir(parents=True)
    (dist / "index.html").write_text(BUILT_INDEX)
    (dist / "assets" / "index-AbCd1234.js").write_text("console.log('SYNTHETIC bundle');\n")
    (dist / "assets" / "index-EfGh5678.css").write_text("body{margin:0}\n")
    env_file = tmp_path / "synthetic.env"
    env_file.write_text("FOOTY_DISPLAY_TZ=Europe/London\nSYNTHETIC_API_TOKEN=not-in-output-xyz\n")
    refs = {
        f"E0:2627:{DAY.isoformat()}:eng-testtown:eng-sampleton",
        f"E0:2627:{DAY.isoformat()}:eng-sampleton:eng-mockford",
    }
    return {
        "ids": {a, b, tilt},
        "refs": refs,
        "settled": a,
        "dist": dist,
        "env_file": env_file,
    }


def export(settings, world, out):
    return run_export(settings, out, dist=world["dist"], env_file=world["env_file"])


def read_site(out):
    """Every entry the site holds, from snapshot.json and each shard it lists:
    key -> (status, body, file). A key is in exactly one file."""
    main = json.loads((out / "snapshot.json").read_text())
    docs = {"snapshot.json": main}
    for sh in main["shards"]:
        docs[sh["file"]] = json.loads((out / sh["file"]).read_text())
        assert sh["bytes"] == (out / sh["file"]).stat().st_size
        assert sorted(docs[sh["file"]]["entries"]) == sh["keys"]
    found = {}
    for name, doc in docs.items():
        assert doc["format"] == FORMAT and doc["taken_at_utc"] == main["taken_at_utc"]
        for key, (status, i) in doc["entries"].items():
            assert key not in found, key
            found[key] = (status, resolve_parts(doc, doc["bodies"][i]), name)
    return found


def resolve_parts(doc, body):
    """A body with its shared parts put back, as frontend/src/snapshot.ts does: a top-level
    field holding only {"$part": n} is the file's n-th part."""
    parts = doc.get("parts") or []
    if not parts or not isinstance(body, dict):
        return body
    out = dict(body)
    for k, v in body.items():
        if isinstance(v, dict) and list(v) == [PART_REF]:
            out[k] = parts[v[PART_REF]]
    return out


def test_canonical_key_matches_the_frontend():
    # The same strings frontend/src/snapshot.ts canonicalKey() gives for these requests.
    assert canonical_key("/api/health") == "/api/health"
    assert (
        canonical_key("/api/past", {"record_type": "genuine_prematch", "limit": 500})
        == "/api/past?limit=500&record_type=genuine_prematch"
    )
    assert canonical_key("/api/backtests/x/summary", {"league": None, "season": ""}) == (
        "/api/backtests/x/summary"
    )
    assert canonical_key("/api/backtests/a%20b(c)'*~!/summary", {"q": "a b&c"}) == (
        "/api/backtests/a%20b(c)'*~!/summary?q=a%20b%26c"
    )
    assert canonical_key("/api/performance", {"primary_only": False}) == (
        "/api/performance?primary_only=false"
    )


def test_read_only_engine_cannot_write(migrated_engine, db_url):
    engine = read_only_engine(db_url)
    with engine.connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM predictions")).scalar_one() == 0
        with pytest.raises(Exception, match="readonly|read-only|query_only"):
            conn.execute(
                text(
                    "INSERT INTO job_runs (job, started_at, status) "
                    "VALUES ('x', '2026-01-01', 'ok')"
                )
            )
    raw = sqlite3.connect(db_url.removeprefix("sqlite:///"))
    try:
        assert raw.execute("SELECT count(*) FROM job_runs").fetchone() == (0,)
    finally:
        raw.close()


def test_export_covers_every_frontend_request(settings, migrated_engine, world, tmp_path):
    out = tmp_path / "site"
    result = export(settings, world, out)
    snap = json.loads((out / "snapshot.json").read_text())
    entries = read_site(out)
    assert snap["format"] == FORMAT
    assert datetime.fromisoformat(snap["taken_at_utc"]).utcoffset() == timedelta(0)

    expected = {"/api/health", "/api/coverage", "/api/refresh-history?limit=200"}
    expected |= {"/api/import-issues", "/api/performance", "/api/backtests", "/api/hit-rates"}
    expected |= {f"/api/reports/{name}" for name in FRONTEND_REPORTS} | {"/api/ml/summary"}
    # One fixtures window: the page takes every shorter one from it (snapshot.ts).
    expected.add(f"/api/fixtures?days={SNAPSHOT_FIXTURE_DAYS}")
    windows = [k for k in entries if k.startswith("/api/fixtures") and "/models" not in k]
    assert windows == ["/api/fixtures?days=30"]
    expected |= {"/api/past?limit=1&record_type=genuine_prematch"}
    for rt in FRONTEND_PAST_RECORD_TYPES:
        expected |= {f"/api/past?limit=500&record_type={rt}"}
        expected |= {f"/api/past?competition={c}&limit=500&record_type={rt}" for c in ("E0", "UCL")}
    expected |= {f"/api/predictions/{i}" for i in world["ids"]}
    # Every model: each fixture's list (both fixtures have genuine records), the leaderboards
    # and the backtest explorer's models list (no curated ledger here, so no match lists).
    for ref in world["refs"]:
        expected.add(canonical_key(f"/api/fixtures/{quote(ref, safe='')}/models"))
    expected |= {"/api/model-leaderboard?group=domestic", "/api/model-leaderboard?group=uefa"}
    expected.add("/api/backtest-models")
    assert not any(k.startswith("/api/backtest-matches") for k in entries)
    dom = "/api/backtests/synthetic_dc__validation__2324-2425/summary"
    for lg in (None, "E0", "E1"):
        for ss in (None, "2324", "2425"):
            expected.add(canonical_key(dom, {"league": lg, "season": ss}))
    euro = "/api/backtests/synthetic_euro__test__2425/summary"
    for lg in (None, *FRONTEND_UEFA_CODES):
        for ss in (None, "2425"):
            expected.add(canonical_key(euro, {"league": lg, "season": ss}))
    # Team pages: the list, and every team on a card (here all three) with its three pages.
    teams = ("eng-mockford", "eng-sampleton", "eng-testtown")
    expected.add("/api/teams")
    for t in teams:
        expected |= {f"/api/teams/{t}", f"/api/teams/{t}/matches", f"/api/teams/{t}/media"}
    assert expected <= set(entries)
    assert result.report()["entries"] == len(entries)
    assert result.report()["team_pages"] == len(teams)

    # Bodies are the API's own responses, stored once each per file.
    client = TestClient(create_app(settings=settings, engine=migrated_engine))

    def body(key):
        return entries[key][:2]

    for pid in world["ids"]:
        assert body(f"/api/predictions/{pid}") == (
            200,
            client.get(f"/api/predictions/{pid}").json(),
        )
    assert body(dom + "?league=E1&season=2425") == (
        200,
        client.get(dom, params={"league": "E1", "season": "2425"}).json(),
    )
    profile = client.get("/api/teams/eng-testtown").json() | {"generated_at_utc": ANY}
    assert body("/api/teams/eng-testtown") == (200, profile)
    past = body("/api/past?limit=500&record_type=genuine_prematch")[1]
    assert world["settled"] in {m["id"] for m in past["matches"]}
    assert body("/api/reports/benchmark") == (404, {"detail": "no such report"})
    assert body("/api/ml/summary") == (404, {"detail": "no such report"})
    hits = body("/api/hit-rates")
    assert hits == (200, client.get("/api/hit-rates").json() | {"generated_at_utc": ANY})
    assert {m["model"] for m in hits[1]["backtest"]["missing"]} >= {"market_tilt_shots"}
    # A server error is kept as its status (the site shows only "error 500" for it).
    broken = "/api/backtests/synthetic_broken__validation__2324/summary"
    assert body(broken + "?league=E0") == (500, None)
    assert broken in result.report()["not_ok"]
    assert len(snap["bodies"]) < len(snap["entries"])  # the empty past lists, for one, are shared

    # No price-like field in any file, though a record was built from one (the API withholds it).
    for f in [out / "snapshot.json", *(out / sh["file"] for sh in snap["shards"])]:
        assert find_prices(json.loads(f.read_text())) == []
        assert "planted_price" not in f.read_text() and "B365H" not in f.read_text()


def test_first_screen_in_snapshot_json_the_rest_in_shards(settings, world, tmp_path):
    out = tmp_path / "site"
    export(settings, world, out)
    entries = read_site(out)
    where = {key: name for key, (_, _, name) in entries.items()}
    for key in ("/api/health", "/api/performance", "/api/ml/summary", "/api/backtests"):
        assert where[key] == "snapshot.json", key
    # The fixtures, and the Data page's tables, each in a file of their own.
    assert where["/api/fixtures?days=30"] == "snapshot-fixtures-1.json"
    data = ("/api/coverage", "/api/import-issues", "/api/refresh-history?limit=200")
    assert {where[k] for k in data} == {"snapshot-data-1.json"}
    assert {where[f"/api/predictions/{i}"] for i in world["ids"]} == {"snapshot-matches-1.json"}
    lab = {name for key, name in where.items() if key.startswith("/api/backtests/")}
    assert lab == {"snapshot-lab-1.json"}
    # The teams list has a file to itself; each team's pages are never split.
    assert where["/api/teams"] == "snapshot-teams-1.json"
    assert [k for k, n in where.items() if n == "snapshot-teams-1.json"] == ["/api/teams"]
    for t in ("eng-mockford", "eng-sampleton", "eng-testtown"):
        files = {where[k] for k in (f"/api/teams/{t}", f"/api/teams/{t}/matches")}
        assert files == {where[f"/api/teams/{t}/media"]} != {"snapshot-teams-1.json"}


def test_shards_close_at_the_target_size_and_keep_each_team_together(
    settings, world, tmp_path, monkeypatch
):
    from footy import snapshot

    monkeypatch.setattr(snapshot, "SHARD_TARGET_BYTES", 1)  # every group in its own file
    out = tmp_path / "site"
    report = export(settings, world, out).report()
    entries = read_site(out)
    team_files = {
        t: {n for k, (_, _, n) in entries.items() if k.startswith(f"/api/teams/{t}")}
        for t in ("eng-mockford", "eng-sampleton", "eng-testtown")
    }
    assert all(len(files) == 1 for files in team_files.values())
    assert len({f for files in team_files.values() for f in files}) == 3
    shards = {sh["file"] for sh in report["shards"]}
    assert {f"snapshot-teams-{n}.json" for n in range(1, 5)} <= shards
    # One file per fixture (every group alone here): its match pages and its every-model list.
    assert {f"snapshot-matches-{n}.json" for n in range(1, len(world["refs"]) + 1)} <= shards
    for ref in world["refs"]:
        models = entries[canonical_key(f"/api/fixtures/{quote(ref, safe='')}/models")]
        pages = {entries[f"/api/predictions/{m['prediction_id']}"][2] for m in models[1]["models"]}
        assert pages == {models[2]}
    assert report["checks"]["within_limits"] and report["file_count"] == len(report["files"])


def test_page_is_body_content_with_relative_references(settings, world, tmp_path):
    out = tmp_path / "site"
    result = export(settings, world, out)
    page = (out / "index.html").read_text()
    for tag in ("<!doctype", "<html", "<head", "<body", "<title", "<meta"):
        assert tag not in page.lower()
    assert page.splitlines() == [
        '<link rel="stylesheet" href="./assets/index-EfGh5678.css">',
        '<div id="root"></div>',
        '<script type="module" crossorigin src="./assets/index-AbCd1234.js"></script>',
    ]
    names = {f["path"] for f in result.report()["files"]}
    shards = {sh["file"] for sh in result.report()["shards"]}
    assert shards == {
        "snapshot-fixtures-1.json",
        "snapshot-data-1.json",
        "snapshot-matches-1.json",
        "snapshot-lab-1.json",
        "snapshot-teams-1.json",
        "snapshot-teams-2.json",
    }
    assert (
        names
        == {
            "index.html",
            "snapshot.json",
            "assets/index-AbCd1234.js",
            "assets/index-EfGh5678.css",
        }
        | shards
    )
    with pytest.raises(ValueError, match="relative"):
        body_only_html(BUILT_INDEX.replace("./assets/index-AbCd1234.js", "/assets/x.js"))


def test_re_export_replaces_an_earlier_snapshot_only(settings, world, tmp_path):
    out = tmp_path / "site"
    export(settings, world, out)
    (out / "assets" / "index-Old00000.js").write_text("stale")
    (out / "notes.txt").write_text("the owner's file")
    export(settings, world, out)
    assert not (out / "assets" / "index-Old00000.js").exists()
    assert (out / "notes.txt").read_text() == "the owner's file"
    assert not [p for p in tmp_path.iterdir() if p.name.startswith(".site-")]

    other = tmp_path / "other"
    other.mkdir()
    (other / "keep.txt").write_text("x")
    with pytest.raises(FileExistsError):
        export(settings, world, other)
    assert [p.name for p in other.iterdir()] == ["keep.txt"]


def test_reports_public_env_values_and_refuses_secrets(settings, migrated_engine, world, tmp_path):
    out = tmp_path / "site"
    report = export(settings, world, out).report()
    shown = {(v["file"], v["variable"]) for v in report["non_secret_env_values_shown"]}
    assert ("snapshot.json", "FOOTY_DISPLAY_TZ") in shown  # /api/health's display_timezone
    assert "SYNTHETIC_API_TOKEN" not in {name for _, name in shown}
    before = (out / "snapshot.json").read_bytes()

    # A job run whose error quotes the (SYNTHETIC) provider token: the export is refused and
    # the earlier snapshot is left as it was.
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO job_runs (job, started_at, status, error) VALUES ('odds_capture', "
                "'2026-10-20 04:00:00', 'failed', :e)"
            ),
            {"e": f"401 for https://provider.example/v3/odds?api_token={SYNTHETIC_SECRET}"},
        )
    with_token = Settings(
        _env_file=None,
        FOOTY_DATABASE_URL=settings.database_url,
        FOOTY_DATA_DIR=settings.data_dir,
        SPORTMONKS_API_TOKEN=SYNTHETIC_SECRET,
    )
    with pytest.raises(SnapshotRefused, match="SPORTMONKS_API_TOKEN") as err:
        export(with_token, world, out)
    assert SYNTHETIC_SECRET not in str(err.value)
    assert (out / "snapshot.json").read_bytes() == before
    assert not [p for p in tmp_path.iterdir() if p.name.startswith(".site-")]


def test_find_prices_flags_planted_examples():
    snap = {
        "bodies": [
            {
                "B365H": 3.3,
                "x": {"planted_price": 2.38},
                "detail": "B365H='2.10'",
                "fixtures_with_prices": 2,
                "odds_inserted": 10,
                "odds::PS close 1X2": 97.5,
                "missing_fields": ["odds"],
                "sources": {"PS": 1752, "Avg": 1752},
            }
        ]
    }
    assert find_prices(snap) == ["bodies[0].B365H", "bodies[0].x.planted_price", "bodies[0].detail"]


def test_single_page_inlines_the_bundle_and_keeps_snapshot_beside_it(tmp_path):
    """SYNTHETIC exported site: one module script and one stylesheet are inlined, a script tag
    inside a string literal is escaped, and snapshot.json is copied next to the page."""
    from footy.snapshot import write_single_page

    site = tmp_path / "site"
    (site / "assets").mkdir(parents=True)
    (site / "index.html").write_text(
        '<link rel="stylesheet" href="./assets/a.css">\n<div id="root"></div>\n'
        '<script type="module" crossorigin src="./assets/a.js"></script>\n'
    )
    (site / "assets" / "a.css").write_text("body{color:red}")
    (site / "assets" / "a.js").write_text("const t=`<script><\\/script>`;console.log(t)")
    (site / "snapshot.json").write_text('{"format":"footy-api-snapshot/1"}')
    page, snap = write_single_page(site, tmp_path / "out" / "football-model.html")
    html = page.read_text()
    assert html.startswith("<title>Football Model</title>")
    assert "<style>body{color:red}</style>" in html and '<div id="root"></div>' in html
    assert "src=" not in html and "href=" not in html
    assert "\\x3cscript>" in html and html.count("<script") == 1
    assert snap.read_text() == '{"format":"footy-api-snapshot/1"}'


def test_font_stylesheet_is_the_only_outside_reference_kept():
    """The Google Fonts stylesheet (allowed by the artifact host) survives both the body-only
    page and the single page; any other absolute reference is still refused."""
    font = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=X&display=swap">'
    built = BUILT_INDEX.replace("</head>", f"{font}\n</head>")
    page = body_only_html(built)
    assert font in page
    with pytest.raises(ValueError, match="relative"):
        body_only_html(built.replace("https://fonts.googleapis.com/", "https://example.com/"))


def test_single_page_keeps_the_font_stylesheet(tmp_path):
    from footy.snapshot import single_page

    site = tmp_path / "site"
    (site / "assets").mkdir(parents=True)
    font = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=X&display=swap">'
    (site / "index.html").write_text(
        f'{font}\n<link rel="stylesheet" href="./assets/a.css">\n<div id="root"></div>\n'
        '<script type="module" src="./assets/a.js"></script>\n'
    )
    (site / "assets" / "a.css").write_text("body{color:red}")
    (site / "assets" / "a.js").write_text("console.log(1)")
    html = single_page(site)
    assert html.count(font) == 1 and "./assets/" not in html


def test_single_page_refuses_a_bundle_that_closes_its_own_script(tmp_path):
    from footy.snapshot import single_page

    site = tmp_path / "site"
    (site / "assets").mkdir(parents=True)
    (site / "index.html").write_text('<script type="module" src="./assets/a.js"></script>')
    (site / "assets" / "a.js").write_text("document.write('</script>')")
    with pytest.raises(ValueError, match="inlined safely"):
        single_page(site)


# --- Pictures, limits and the checks over every file -------------------------------------------

PNG = b"\x89PNG\r\n\x1a\n" + b"\0" * 16  # SYNTHETIC: a PNG header, not a picture


def add_picture(settings, monkeypatch, tmp_path, team="eng-testtown", kind="crest"):
    """A SYNTHETIC downloaded picture for `team`, with the facts naming its Commons file."""
    from footy.media import service

    name = "crest.png" if kind == "crest" else "venue.jpg"
    facts = {
        "teams": {
            team: {
                "qid": "Q1",
                "crest_file": "Synthetic crest.svg",
                "venue": {"name": "Synthetic Ground", "image_file": "Synthetic ground.jpg"},
                "colours": [{"name": "red", "hex": "#CC0000"}],
            }
        }
    }
    path = tmp_path / "team_media.json"
    path.write_text(json.dumps(facts))
    monkeypatch.setattr(service, "FACTS_PATH", path)
    folder = service.media_dir(settings) / team
    folder.mkdir(parents=True, exist_ok=True)
    (folder / name).write_bytes(PNG)
    manifest = folder / "manifest.json"
    files = json.loads(manifest.read_text())["files"] if manifest.exists() else {}
    files[kind] = {
        "file": name,
        "commons_file": "Synthetic crest.svg" if kind == "crest" else "Synthetic ground.jpg",
        "source_url": f"https://commons.wikimedia.org/wiki/File:Synthetic_{kind}",
        "licence": "CC BY-SA 4.0",
        "licence_url": "https://creativecommons.org/licenses/by-sa/4.0",
        "author": "A. Synthetic",
        "attribution": f"Synthetic {kind} by A. Synthetic, CC BY-SA 4.0, via Wikimedia Commons",
    }
    manifest.write_text(json.dumps({"team_key": team, "files": files}))


def test_downloaded_pictures_are_carried_beside_the_page(settings, world, tmp_path, monkeypatch):
    add_picture(settings, monkeypatch, tmp_path, kind="crest")
    add_picture(settings, monkeypatch, tmp_path, kind="venue")
    out = tmp_path / "site"
    report = export(settings, world, out).report()
    assert report["pictures"] == {
        "carried": ["media/teams/eng-testtown/crest.png", "media/teams/eng-testtown/venue.jpg"],
        "left_out": [],
    }
    assert (out / "media/teams/eng-testtown/crest.png").read_bytes() == PNG
    assert {"media/teams/eng-testtown/crest.png", "media/teams/eng-testtown/venue.jpg"} <= {
        f["path"] for f in report["files"]
    }
    entries = read_site(out)
    media = entries["/api/teams/eng-testtown/media"][1]
    # The URLs point at the copies beside the page; the attribution is kept with them.
    assert media["crest"]["url"] == "media/teams/eng-testtown/crest.png"
    assert media["crest"]["attribution"].startswith("Synthetic crest by A. Synthetic")
    assert media["crest"]["licence_url"] == "https://creativecommons.org/licenses/by-sa/4.0"
    assert media["venue"]["url"] == "media/teams/eng-testtown/venue.jpg"
    assert media["venue"]["author"] == "A. Synthetic"
    # A generated badge is drawn on request, so the snapshot has no file for it: null.
    other = entries["/api/teams/eng-sampleton/media"][1]
    assert other["crest"]["kind"] == "generated" and other["crest"]["url"] is None
    # Cards carry the relative crest URL too, in every file.
    card = entries["/api/past?limit=500&record_type=genuine_prematch"][1]["matches"][0]
    side = "home_media" if card["home_team"] == "eng-testtown" else "away_media"
    assert card[side]["crest_url"] == "media/teams/eng-testtown/crest.png"
    profile = entries["/api/teams/eng-testtown"][1]
    assert profile["team"]["media"]["crest_url"] == "media/teams/eng-testtown/crest.png"
    for _, body, _ in entries.values():
        assert "/media/teams/" not in json.dumps(body)


def test_pictures_beyond_the_file_limit_are_left_out(settings, world, tmp_path, monkeypatch):
    from footy import snapshot

    add_picture(settings, monkeypatch, tmp_path, kind="crest")
    add_picture(settings, monkeypatch, tmp_path, kind="venue")
    out = tmp_path / "site"
    first = export(settings, world, out).report()
    # Room for exactly one picture beside the other files: the crest goes first.
    monkeypatch.setitem(snapshot.HOST_LIMITS, "files", first["file_count"] - 1)
    report = export(settings, world, out).report()
    assert report["pictures"] == {
        "carried": ["media/teams/eng-testtown/crest.png"],
        "left_out": ["media/teams/eng-testtown/venue.jpg"],
    }
    assert not (out / "media/teams/eng-testtown/venue.jpg").exists()  # the earlier copy went
    media = read_site(out)["/api/teams/eng-testtown/media"][1]
    assert media["crest"]["url"] == "media/teams/eng-testtown/crest.png"
    assert media["venue"]["url"] is None and media["venue"]["name"] == "Synthetic Ground"


def test_a_file_over_the_hosts_limits_refuses_the_export(settings, world, tmp_path, monkeypatch):
    from footy import snapshot

    out = tmp_path / "site"
    export(settings, world, out)
    before = (out / "snapshot.json").read_bytes()
    monkeypatch.setitem(snapshot.HOST_LIMITS, "text_file_bytes", 20_000)
    with pytest.raises(SnapshotRefused, match="text file limit"):
        export(settings, world, out)
    monkeypatch.setitem(snapshot.HOST_LIMITS, "text_file_bytes", 16_000_000)
    monkeypatch.setitem(snapshot.HOST_LIMITS, "total_bytes", 1_000)
    with pytest.raises(SnapshotRefused, match="in all"):
        export(settings, world, out)
    assert (out / "snapshot.json").read_bytes() == before
    assert not [p for p in tmp_path.iterdir() if p.name.startswith(".site-")]


def test_a_price_in_a_shard_refuses_the_export(settings, world, tmp_path, monkeypatch):
    """SYNTHETIC: a team profile that (wrongly) carried a price-like field."""
    from footy import teams

    real = teams.team_profile
    monkeypatch.setattr(
        teams, "team_profile", lambda *a, **k: {**real(*a, **k), "odds": {"B365H": 2.5}}
    )
    with pytest.raises(SnapshotRefused, match=r"snapshot-teams-\d+\.json"):
        export(settings, world, tmp_path / "site")
    assert not (tmp_path / "site").exists()


def test_a_secret_in_a_shard_refuses_the_export(settings, world, migrated_engine, tmp_path):
    with migrated_engine.begin() as conn:  # SYNTHETIC: a team name quoting the token
        conn.execute(
            text("UPDATE teams SET name = :n WHERE team_key = 'eng-mockford'"),
            {"n": f"Mockford {SYNTHETIC_SECRET}"},
        )
    with_token = Settings(
        _env_file=None,
        FOOTY_DATABASE_URL=settings.database_url,
        FOOTY_DATA_DIR=settings.data_dir,
        SPORTMONKS_API_TOKEN=SYNTHETIC_SECRET,
    )
    with pytest.raises(SnapshotRefused, match=r"snapshot-teams-\d+\.json") as err:
        export(with_token, world, tmp_path / "site")
    assert SYNTHETIC_SECRET not in str(err.value)


def test_re_export_drops_shards_the_new_snapshot_doesnt_have(settings, world, tmp_path):
    out = tmp_path / "site"
    export(settings, world, out)
    (out / "snapshot-teams-9.json").write_text("{}")  # from an earlier, bigger snapshot
    (out / "media" / "teams" / "eng-gone").mkdir(parents=True)
    (out / "media" / "teams" / "eng-gone" / "crest.png").write_bytes(PNG)
    export(settings, world, out)
    assert not (out / "snapshot-teams-9.json").exists() and not (out / "media").exists()


def test_an_earlier_format_snapshot_folder_is_replaced(settings, world, tmp_path):
    out = tmp_path / "site"
    out.mkdir()
    (out / "snapshot.json").write_text('{"format":"footy-api-snapshot/1","bodies":[]}')
    (out / "index.html").write_text("old")
    export(settings, world, out)
    assert json.loads((out / "snapshot.json").read_text())["format"] == FORMAT


def test_single_page_carries_every_shard_and_picture(settings, world, tmp_path, monkeypatch):
    from footy.snapshot import write_single_page

    add_picture(settings, monkeypatch, tmp_path)
    out = tmp_path / "site"
    result = export(settings, world, out)
    (out / "assets" / "index-AbCd1234.js").write_text("console.log('SYNTHETIC bundle');\n")
    folder = tmp_path / "artifact"
    folder.mkdir()
    (folder / "snapshot.json").write_text('{"format":"footy-api-snapshot/2","bodies":[]}')
    (folder / "snapshot-teams-7.json").write_text("{}")  # left from an earlier single page
    (folder / "owner-notes.txt").write_text("kept")
    written = write_single_page(out, folder / "football-model.html")
    names = sorted(str(p.relative_to(folder)) for p in written)
    shards = sorted(sh["file"] for sh in result.report()["shards"])
    assert names == sorted(
        ["football-model.html", "snapshot.json", "media/teams/eng-testtown/crest.png", *shards]
    )
    for name in names[1:]:
        assert (folder / name).read_bytes() == (out / name).read_bytes()
    assert not (folder / "snapshot-teams-7.json").exists()
    assert (folder / "owner-notes.txt").read_text() == "kept"


def test_single_page_over_the_limits_writes_nothing(settings, world, tmp_path, monkeypatch):
    from footy import snapshot

    out = tmp_path / "site"
    export(settings, world, out)
    monkeypatch.setitem(snapshot.HOST_LIMITS, "files", 2)
    with pytest.raises(SnapshotRefused, match="over the limit of 2"):
        snapshot.write_single_page(out, tmp_path / "artifact" / "page.html")
    assert not (tmp_path / "artifact").exists()


def test_a_shard_over_its_cap_refuses_the_export(settings, world, tmp_path, monkeypatch):
    from footy import snapshot

    monkeypatch.setattr(snapshot, "SHARD_MAX_BYTES", 100)
    with pytest.raises(SnapshotRefused, match=r"snapshot-\w+-\d+\.json is .* shard limit"):
        export(settings, world, tmp_path / "site")


def test_single_page_is_ascii_whatever_charset_it_is_served_with(tmp_path):
    """SYNTHETIC bundle with the kinds of non-ASCII text the real one has: an arrow in a
    template literal, an accent-stripping regular expression, a CSS content string and an
    astral character. Served as Latin-1 they would break the page; escaped they mean the same."""
    from footy.snapshot import single_page

    site = tmp_path / "site"
    (site / "assets").mkdir(parents=True)
    (site / "index.html").write_text(
        '<link rel="stylesheet" href="./assets/a.css">\n<div id="root">Café</div>\n'
        '<script type="module" src="./assets/a.js"></script>\n'
    )
    (site / "assets" / "a.css").write_text('a:before{content:"↓ ";color:red}', encoding="utf-8")
    (site / "assets" / "a.js").write_text(
        "const a=`← back`,b=s=>s.normalize(`NFD`).replace(/[̀-ͯ]/g,``),c='⚽😀';",
        encoding="utf-8",
    )
    html = single_page(site)
    assert html.isascii()
    assert "const a=`\\u2190 back`" in html
    assert ".replace(/[\\u0300-\\u036f]/g,``)" in html
    assert "c='\\u26bd\\ud83d\\ude00'" in html
    assert 'content:"\\2193  "' in html  # the escape eats one space; the text's space stays
    assert '<div id="root">Caf&#xe9;</div>' in html

    (site / "assets" / "a.js").write_text("const d='\\é';", encoding="utf-8")
    with pytest.raises(ValueError, match="ASCII safely"):
        single_page(site)


def test_answers_that_differ_only_in_when_they_were_made_are_stored_once():
    from footy.snapshot import Snapshot

    snap = Snapshot(taken_at_utc="2026-10-22T04:00:00+00:00")
    snap.add("/a", 200, {"generated_at_utc": "2026-10-22T04:00:01+00:00", "x": [1]})
    snap.add("/b", 200, {"generated_at_utc": "2026-10-22T04:00:07+00:00", "x": [1]})
    snap.add("/c", 200, {"generated_at_utc": "2026-10-22T04:00:07+00:00", "x": [2]})
    assert snap.entries["/a"] == snap.entries["/b"] != snap.entries["/c"]
    assert snap.body("/b")["generated_at_utc"] == "2026-10-22T04:00:01+00:00"  # the first kept
    assert len(snap.bodies) == 2


def test_team_and_match_page_shards_are_smaller_than_the_others():
    from footy.snapshot import PAGE_SHARD_TARGET_BYTES, SHARD_TARGET_BYTES, Snapshot

    # Bodies of 0.6 of the page target: a match or team file holds one, a lab file several.
    filler = "x" * int(PAGE_SHARD_TARGET_BYTES * 0.6)
    snap = Snapshot(taken_at_utc="2026-10-22T04:00:00+00:00")
    for family in ("matches", "lab", "teams"):
        for n in range(3):
            key = f"/api/{family}/{n}"
            snap.add(key, 200, {"n": n, "family": family, "filler": filler})
            snap.group(family, [key])
    files = {name: keys for name, keys in snap.shards()}
    assert PAGE_SHARD_TARGET_BYTES < SHARD_TARGET_BYTES
    assert [len(files[f"snapshot-matches-{n}.json"]) for n in (1, 2, 3)] == [1, 1, 1]
    assert [len(files[f"snapshot-teams-{n}.json"]) for n in (1, 2, 3)] == [1, 1, 1]
    assert len(files["snapshot-lab-1.json"]) == 3 and "snapshot-lab-2.json" not in files


def test_a_record_published_while_the_export_runs_is_carried_with_the_team_page_linking_it(
    settings, migrated_engine, world, tmp_path, monkeypatch
):
    """A team page links to its fixtures' and matches' match pages. A record published after
    the match pages were collected but before the team pages (a scheduled job writing while the
    export runs) must still have its match page in the snapshot."""
    from footy import snapshot

    later = FixtureIn(
        "E0",
        "2627",
        DAY + timedelta(days=3),
        KICKOFF + timedelta(days=3),
        "eng-mockford",
        "eng-testtown",
        features={"home_matches_in_window": 40, "away_matches_in_window": 38},
        missing_fields=["lineups", "injuries", "odds"],
    )
    choose_teams = snapshot.snapshot_teams
    published: list[int] = []

    def publish_then_choose(engine, snap):
        if not published:
            published.extend(pub(migrated_engine, tmp_path, [later], "dixon_coles"))
        return choose_teams(engine, snap)

    monkeypatch.setattr(snapshot, "snapshot_teams", publish_then_choose)
    out = tmp_path / "site"
    export(settings, world, out)
    entries = read_site(out)
    (new_id,) = published
    profile = entries["/api/teams/eng-testtown"][1]
    assert new_id in {f["prediction"]["id"] for f in profile["upcoming"] if f["prediction"]}
    linked = {
        r["prediction"]["id"]
        for key, (status, body, _) in entries.items()
        if status == 200 and key.startswith("/api/teams/")
        for r in [*(body.get("upcoming") or []), *(body.get("matches") or [])]
        if isinstance(r, dict) and r.get("prediction")
    }
    assert new_id in linked
    assert {f"/api/predictions/{i}" for i in linked} <= set(entries)


# --- Every model's predictions (docs/model-comparison.md) -----------------------------------


def test_backtest_explorer_is_carried_one_shard_per_division_and_split(
    settings, migrated_engine, world, tmp_path
):
    """With curated ledgers saved (SYNTHETIC, btfixtures), the snapshot carries the explorer's
    models list in snapshot.json and every division and season it covers, under the exact
    query the page asks for, one shard per group, division and split."""
    import btfixtures

    btfixtures.write_curated(settings.data_dir)
    out = tmp_path / "site"
    export(settings, world, out)
    entries = read_site(out)
    client = TestClient(create_app(settings=settings, engine=migrated_engine))
    assert entries["/api/backtest-models"][2] == "snapshot.json"
    keys = {
        "E0 validation": ["/api/backtest-matches?division=E0&limit=1000&season=2324"],
        "E0 test": [
            "/api/backtest-matches?division=E0&limit=1000&season=2425",
            "/api/backtest-matches?division=E0&limit=1000&season=2526",
        ],
        "UCL test": ["/api/backtest-matches?division=UCL&limit=1000&season=2425"],
    }
    found = sorted(k for k in entries if k.startswith("/api/backtest-matches"))
    assert found == sorted(k for ks in keys.values() for k in ks)
    files = {name: {entries[k][2] for k in ks} for name, ks in keys.items()}
    assert all(len(f) == 1 for f in files.values())
    assert len({f for fs in files.values() for f in fs}) == 3
    for name in {f for fs in files.values() for f in fs}:
        assert name.startswith("snapshot-backtest-")
        assert {n for k, (_, _, n) in entries.items() if n == name} == {name}
        held = [k for k, (_, _, n) in entries.items() if n == name]
        assert all(k.startswith("/api/backtest-matches") for k in held)
    key = keys["E0 validation"][0]
    assert entries[key][:2] == (200, client.get(key).json())
    assert entries[key][1]["kind"] == "backtest"
    # The leaderboards are on the first screen, and are genuine records only.
    for group in ("domestic", "uefa"):
        status, body, name = entries[f"/api/model-leaderboard?group={group}"]
        assert (status, name, body["kind"]) == (200, "snapshot.json", "genuine_prematch")


def test_a_division_bigger_than_the_explorer_limit_refuses_the_export(
    settings, world, tmp_path, monkeypatch
):
    import btfixtures
    from footy import snapshot

    btfixtures.write_curated(settings.data_dir)
    monkeypatch.setattr(snapshot, "FRONTEND_BACKTEST_LIMIT", 2)  # E0 2023/24 holds three
    with pytest.raises(RuntimeError, match="FRONTEND_BACKTEST_LIMIT"):
        export(settings, world, tmp_path / "site")


def test_match_pages_share_their_fixtures_parts_within_a_file(settings, world, tmp_path):
    """Fixture A's two records have two match pages repeating the same form and stats,
    recent results and list of records: each is written once, in the file's `parts`, and
    every page reads back as the API's own response."""
    out = tmp_path / "site"
    export(settings, world, out)
    main = json.loads((out / "snapshot.json").read_text())
    entries = read_site(out)
    by_file: dict[str, list[str]] = {}
    for key, (_, _, name) in entries.items():
        if key.startswith("/api/predictions/"):
            by_file.setdefault(name, []).append(key)
    shared = 0
    for name, keys in by_file.items():
        doc = json.loads((out / name).read_text())
        raw = [doc["bodies"][doc["entries"][k][1]] for k in keys]
        refs = [
            v[PART_REF] for b in raw for v in b.values() if isinstance(v, dict) and PART_REF in v
        ]
        assert sorted(set(refs)) == list(range(len(doc.get("parts", []))))
        shared += len(refs) - len(set(refs))
        assert find_prices(doc) == []
    assert shared >= 2  # fixture A's pages share at least teams and editions
    for pid in world["ids"]:
        status, body, _ = entries[f"/api/predictions/{pid}"]
        assert status == 200 and {"teams", "recent", "editions"} <= set(body)
        assert isinstance(body["teams"], dict) and "home" in body["teams"]
        assert isinstance(body["editions"], list)
    assert "parts" not in main or all(main["parts"])


def test_find_prices_reads_shared_parts():
    planted = {"bodies": [{"teams": {PART_REF: 0}}], "parts": [{"B365H": 2.1}]}
    assert find_prices(planted) == ["parts[0].B365H"]
