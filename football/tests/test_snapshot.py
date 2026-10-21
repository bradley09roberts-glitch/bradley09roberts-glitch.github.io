"""The static snapshot exporter (footy.snapshot, `footy export-snapshot`).

SYNTHETIC TEST DATA: fictional teams (fdfixtures), made-up probabilities, a made-up backtest
ledger and report, a made-up "secret" and a planted price-like field. Nothing here is a real
price, prediction, result or credential.
"""

import itertools
import json
import sqlite3
from datetime import UTC, datetime, timedelta

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
    FRONTEND_FIXTURE_DAYS,
    FRONTEND_PAST_RECORD_TYPES,
    FRONTEND_UEFA_CODES,
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
    return {"ids": {a, b, tilt}, "settled": a, "dist": dist, "env_file": env_file}


def export(settings, world, out):
    return run_export(settings, out, dist=world["dist"], env_file=world["env_file"])


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
    entries = snap["entries"]
    assert snap["format"] == FORMAT
    assert datetime.fromisoformat(snap["taken_at_utc"]).utcoffset() == timedelta(0)

    expected = {"/api/health", "/api/coverage", "/api/refresh-history?limit=200"}
    expected |= {"/api/import-issues", "/api/performance", "/api/backtests"}
    expected |= {"/api/reports/model_selection", "/api/reports/benchmark"}
    expected |= {f"/api/fixtures?days={d}" for d in FRONTEND_FIXTURE_DAYS}
    expected |= {"/api/past?limit=1&record_type=genuine_prematch"}
    for rt in FRONTEND_PAST_RECORD_TYPES:
        expected |= {f"/api/past?limit=500&record_type={rt}"}
        expected |= {f"/api/past?competition={c}&limit=500&record_type={rt}" for c in ("E0", "UCL")}
    expected |= {f"/api/predictions/{i}" for i in world["ids"]}
    dom = "/api/backtests/synthetic_dc__validation__2324-2425/summary"
    for lg in (None, "E0", "E1"):
        for ss in (None, "2324", "2425"):
            expected.add(canonical_key(dom, {"league": lg, "season": ss}))
    euro = "/api/backtests/synthetic_euro__test__2425/summary"
    for lg in (None, *FRONTEND_UEFA_CODES):
        for ss in (None, "2425"):
            expected.add(canonical_key(euro, {"league": lg, "season": ss}))
    assert expected <= set(entries)
    assert result.report()["entries"] == len(entries)

    # Bodies are the API's own responses, stored once each.
    client = TestClient(create_app(settings=settings, engine=migrated_engine))

    def body(key):
        status, i = entries[key]
        return status, snap["bodies"][i]

    for pid in world["ids"]:
        assert body(f"/api/predictions/{pid}") == (
            200,
            client.get(f"/api/predictions/{pid}").json(),
        )
    assert body(dom + "?league=E1&season=2425") == (
        200,
        client.get(dom, params={"league": "E1", "season": "2425"}).json(),
    )
    past = body("/api/past?limit=500&record_type=genuine_prematch")[1]
    assert world["settled"] in {m["id"] for m in past["matches"]}
    assert body("/api/reports/benchmark") == (404, {"detail": "no such report"})
    # A server error is kept as its status (the site shows only "error 500" for it).
    broken = "/api/backtests/synthetic_broken__validation__2324/summary"
    assert body(broken + "?league=E0") == (500, None)
    assert broken in result.report()["not_ok"]
    assert len(snap["bodies"]) < len(entries)  # the empty past lists, for one, are shared

    # No price-like field, though the record was built from one (the API withholds it).
    assert find_prices(snap) == []
    text_ = (out / "snapshot.json").read_text()
    assert "planted_price" not in text_ and "B365H" not in text_


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
    assert names == {
        "index.html",
        "snapshot.json",
        "assets/index-AbCd1234.js",
        "assets/index-EfGh5678.css",
    }
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


def test_single_page_refuses_a_bundle_that_closes_its_own_script(tmp_path):
    from footy.snapshot import single_page

    site = tmp_path / "site"
    (site / "assets").mkdir(parents=True)
    (site / "index.html").write_text('<script type="module" src="./assets/a.js"></script>')
    (site / "assets" / "a.js").write_text("document.write('</script>')")
    with pytest.raises(ValueError, match="inlined safely"):
        single_page(site)
