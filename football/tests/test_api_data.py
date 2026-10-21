from datetime import date

from fdfixtures import REFERENCE, ROWS, csv_bytes, replace_field
from footy.ingest.fd_update import run_fd_update


def load(engine, settings, rows=None):
    settings.fd_inbox_dir.mkdir(parents=True, exist_ok=True)
    (settings.fd_inbox_dir / "2526_E0.csv").write_bytes(csv_bytes(rows))
    run_fd_update(
        engine, settings, today=date(2025, 12, 10), allow_download=False, reference=REFERENCE
    )


def test_health_reports_imported_data(client, migrated_engine, settings):
    load(migrated_engine, settings)
    body = client.get("/api/health").json()
    assert body["data_imported"] is True
    assert body["data"]["matches"] == 3 and body["data"]["divisions"] == 1
    assert body["data"]["latest_match_date"] == "2025-12-03"
    assert body["data"]["last_refresh"]["status"] == "ok"


def test_coverage_endpoint(client, migrated_engine, settings):
    load(migrated_engine, settings)
    (row,) = client.get("/api/coverage").json()["rows"]
    assert (row["division"], row["season"], row["matches"], row["teams"]) == ("E0", "2526", 3, 3)
    assert row["expected_matches"] == 6
    assert row["pct_shots"] == 100.0 and row["pct_cards"] == 0.0
    assert row["odds::B365 pre 1X2"] == 100.0 and row["odds::BFE close 1X2"] == 0.0


def test_issues_and_refresh_history_show_problems(client, migrated_engine, settings):
    load(migrated_engine, settings, [replace_field(ROWS[0], "FTR", "D"), *ROWS[1:]])
    issues = client.get("/api/import-issues").json()
    assert issues["summary"] == [{"severity": "error", "code": "result_mismatch", "count": 1}]
    assert issues["issues"][0]["file"] == "2526/E0" and issues["issues"][0]["row"] == 1
    runs = client.get("/api/refresh-history").json()["runs"]
    assert runs[0]["job"] == "fd_update" and runs[0]["detail"]["import"]["rows_rejected"] == 1


def test_import_issues_withhold_rejected_price_cells(client, migrated_engine, settings):
    """SYNTHETIC rows with made-up faulty prices: the column and the reason are served, the
    raw cell never is (D-013, D-024)."""
    rows = [
        replace_field(ROWS[0], "B365H", "0.913"),
        replace_field(ROWS[1], "PC>2.5", "-0.47"),
        *ROWS[2:],
    ]
    load(migrated_engine, settings, rows)
    raw = client.get("/api/import-issues")
    body = raw.json()
    assert {"severity": "error", "code": "invalid_price", "count": 2} in body["summary"]
    details = sorted(i["detail"] for i in body["issues"] if i["code"] == "invalid_price")
    assert details == [
        "B365H: not a valid price (value withheld)",
        "PC>2.5: not a valid price (value withheld)",
    ]
    assert "0.913" not in raw.text and "-0.47" not in raw.text
    limited = client.get("/api/import-issues?per_code=1").json()
    assert all("withheld" in i["detail"] for i in limited["issues"] if i["code"] == "invalid_price")


def test_public_issue_detail_leaves_other_reasons_alone():
    from footy.api.main import public_issue_detail

    assert public_issue_detail("invalid_price", "AvgC<2.5='0.42'") == (
        "AvgC<2.5: not a valid price (value withheld)"
    )
    assert public_issue_detail("invalid_price", "garbled") == "not a valid price (value withheld)"
    assert public_issue_detail("invalid_price", None) is None
    assert public_issue_detail("no_result", "FTHG='' FTAG=''") == "FTHG='' FTAG=''"
