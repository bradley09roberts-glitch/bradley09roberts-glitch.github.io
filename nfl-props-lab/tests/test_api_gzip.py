"""The server compresses responses of about 1 KB or more for clients that accept gzip, sends
smaller ones and clients that do not as they are, and leaves an already-gzipped download alone.
The data calls are replaced by stand-ins; every figure below is made up."""

import gzip

import pytest

from npl import api
from npl.nba import appdata as nba

# the test client's own notice about its HTTP library, not about this code
pytestmark = pytest.mark.filterwarnings(
    "ignore:Using `httpx` with `starlette.testclient` is deprecated")


@pytest.fixture
def client():
    from fastapi.testclient import TestClient

    return TestClient(api.app)


def _payload(n):
    return {"chance": 0.25, "legs": 2, "warnings": [f"Leg {i}: made up" for i in range(n)]}


def test_large_responses_are_gzipped_for_clients_that_accept_it(client, monkeypatch):
    monkeypatch.setattr(nba, "parlay", lambda spec, price=None: _payload(200))
    r = client.get("/api/nba/parlay", params={"legs": "a:pts:over"},
                   headers={"Accept-Encoding": "gzip"})
    assert r.status_code == 200 and r.headers["content-encoding"] == "gzip"
    assert r.json() == _payload(200)  # the client unpacks it to the same body
    assert int(r.headers["content-length"]) < len(r.content)  # sent smaller than it is
    plain = client.get("/api/nba/parlay", params={"legs": "a:pts:over"},
                       headers={"Accept-Encoding": "identity"})
    assert "content-encoding" not in plain.headers and plain.json() == _payload(200)


def test_small_responses_are_sent_as_they_are(client, monkeypatch):
    monkeypatch.setattr(nba, "parlay", lambda spec, price=None: _payload(1))
    r = client.get("/api/nba/parlay", params={"legs": "a:pts:over"},
                   headers={"Accept-Encoding": "gzip"})
    assert len(r.content) < api.GZIP_MIN_BYTES and "content-encoding" not in r.headers


def test_a_gzipped_ledger_is_not_gzipped_again(client, monkeypatch, tmp_path):
    run = tmp_path / "backtests" / "r1"
    run.mkdir(parents=True)
    raw = gzip.compress(b"season,week,player\n" + b"2025,1,someone\n" * 500)
    (run / "ledger.csv.gz").write_bytes(raw)
    monkeypatch.setattr(api, "REPORTS_DIR", tmp_path)
    r = client.get("/api/export/ledger/r1.csv.gz", headers={"Accept-Encoding": "gzip"})
    assert r.status_code == 200 and r.headers["content-type"] == "application/gzip"
    assert "content-encoding" not in r.headers and r.content == raw
