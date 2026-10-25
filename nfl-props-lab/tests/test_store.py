"""Raw store idempotency, append-only tables, job failures, retries, redaction."""

import sqlite3

import pytest
import requests

from npl import db, rawstore
from npl.util import FetchError, http_get, redact


@pytest.fixture
def con(tmp_path, monkeypatch):
    monkeypatch.setattr(rawstore, "DATA_HOME", tmp_path)
    return db.connect(tmp_path / "t.sqlite3")


def test_duplicate_import_is_idempotent(con):
    a = rawstore.store(con, source="s", dataset="d", part="2024", url="u", content=b"abc",
                       filename="x.csv")
    b = rawstore.store(con, source="s", dataset="d", part="2024", url="u", content=b"abc",
                       filename="x.csv")
    assert a.changed and not b.changed and a.id == b.id
    assert con.execute("SELECT COUNT(*) FROM raw_files").fetchone()[0] == 1
    assert con.execute("SELECT COUNT(*) FROM raw_observations").fetchone()[0] == 2
    c = rawstore.store(con, source="s", dataset="d", part="2024", url="u", content=b"abcd",
                       filename="x.csv")
    assert c.changed and c.id != a.id
    assert rawstore.latest(con, "s", "d", "2024")["id"] == c.id


def test_as_of_returns_version_known_then(con):
    rawstore.store(con, source="s", dataset="d", part="p", url="u", content=b"v1",
                   filename="f", retrieved_at="2024-01-01T00:00:00Z")
    rawstore.store(con, source="s", dataset="d", part="p", url="u", content=b"v2",
                   filename="f", retrieved_at="2024-02-01T00:00:00Z")
    old = rawstore.latest(con, "s", "d", "p", as_of="2024-01-15T00:00:00Z")
    assert old["retrieved_at"] == "2024-01-01T00:00:00Z"


def test_settlement_and_quote_rows_are_append_only(con):
    con.execute("INSERT INTO settlements(game_id, player_id, market, revision, status, value,"
                " observed_at) VALUES ('g','p','rec_yds',1,'final',55,'2024-01-01T00:00:00Z')")
    con.execute("INSERT INTO quotes(source, bookmaker, bookmaker_region, availability,"
                " provider_event_id, player_name_raw, market, line, selection, decimal_price,"
                " observed_at) VALUES ('t','b','uk','research_reference','e','X','rec_yds',"
                "54.5,'over',1.9,'2024-01-01T00:00:00Z')")
    for t in ("settlements", "quotes"):
        with pytest.raises(sqlite3.DatabaseError, match="append-only"):
            con.execute(f"UPDATE {t} SET observed_at='2025-01-01T00:00:00Z'")
        with pytest.raises(sqlite3.DatabaseError, match="append-only"):
            con.execute(f"DELETE FROM {t}")
    # a correction is a new revision row
    con.execute("INSERT INTO settlements(game_id, player_id, market, revision, status, value,"
                " observed_at) VALUES ('g','p','rec_yds',2,'final',57,'2024-01-02T00:00:00Z')")
    assert con.execute("SELECT COUNT(*) FROM settlements").fetchone()[0] == 2


def test_quote_price_must_exceed_one(con):
    with pytest.raises(sqlite3.IntegrityError):
        con.execute("INSERT INTO quotes(source, bookmaker, bookmaker_region, availability,"
                    " provider_event_id, player_name_raw, market, line, selection,"
                    " decimal_price, observed_at) VALUES ('t','b','uk','verified_uk','e','X',"
                    "'rec_yds',54.5,'over',1.0,'2024-01-01T00:00:00Z')")


def test_raw_file_update_blocked(con):
    rawstore.store(con, source="s", dataset="d", part="p", url="u", content=b"v1", filename="f")
    with pytest.raises(sqlite3.DatabaseError, match="append-only"):
        con.execute("UPDATE raw_files SET sha256='x'")
    with pytest.raises(sqlite3.DatabaseError, match="append-only"):
        con.execute("DELETE FROM raw_files")


def test_synthetic_files_never_returned_as_latest(con):
    rawstore.store(con, source="odds", dataset="d", part="p", url="u", content=b"trial",
                   filename="f", is_synthetic=True)
    assert rawstore.latest(con, "odds", "d", "p") is None


def test_failed_job_is_recorded(con):
    with pytest.raises(ValueError), rawstore.job(con, "x"):
        raise ValueError("boom apiKey=SECRET123")
    row = con.execute("SELECT status, detail FROM job_runs").fetchone()
    assert row["status"] == "failed"
    assert "SECRET123" not in row["detail"]


def test_redact():
    s = redact("https://api.the-odds-api.com/v4/x?apiKey=abc123&regions=uk")
    assert "abc123" not in s and "regions=uk" in s


class _Resp:
    def __init__(self, code, headers=None):
        self.status_code, self.headers, self.url, self.content = code, headers or {}, "u", b""


class _Sess:
    def __init__(self, seq):
        self.seq, self.calls = list(seq), 0

    def get(self, *a, **k):
        self.calls += 1
        x = self.seq.pop(0)
        if isinstance(x, Exception):
            raise x
        return x


def test_retry_bounded_backoff():
    sleeps = []
    s = _Sess([requests.ConnectionError(), _Resp(503), _Resp(429, {"Retry-After": "3"}),
               _Resp(200)])
    r = http_get("https://x/y?apiKey=K", session=s, sleep=sleeps.append, base_delay=1)
    assert r.status_code == 200 and s.calls == 4
    assert sleeps == [1, 2, 3.0]


def test_retry_gives_up_and_does_not_retry_4xx():
    s = _Sess([_Resp(500)] * 5)
    with pytest.raises(FetchError):
        http_get("https://x", session=s, sleep=lambda _: None, max_tries=3)
    assert s.calls == 3
    s = _Sess([_Resp(404)])
    with pytest.raises(FetchError) as e:
        http_get("https://x", session=s, sleep=lambda _: None)
    assert e.value.status == 404 and s.calls == 1
