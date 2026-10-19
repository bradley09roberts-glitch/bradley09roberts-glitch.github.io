from fastapi.testclient import TestClient

from footy.api.main import create_app
from footy.db import head_revision, make_engine


def test_health_ok_after_migration(client):
    body = client.get("/api/health").json()
    assert body["status"] == "ok"
    assert body["database"] == "ok"
    assert body["schema_revision"] == body["schema_head"] == head_revision()
    assert body["display_timezone"] == "Europe/London"


def test_health_reports_no_data_and_no_provider(client):
    body = client.get("/api/health").json()
    assert body["data_imported"] is False
    assert body["live_provider"] == "none"
    assert body["live_provider_configured"] is False


def test_health_degraded_when_unmigrated(settings, tmp_path):
    engine = make_engine(f"sqlite:///{tmp_path / 'fresh.sqlite3'}")
    body = TestClient(create_app(settings=settings, engine=engine)).get("/api/health").json()
    assert body["status"] == "degraded"
    assert body["schema_revision"] is None
