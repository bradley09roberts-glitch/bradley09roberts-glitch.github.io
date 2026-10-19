import pytest
from fastapi.testclient import TestClient

from footy.api.main import create_app
from footy.config import Settings
from footy.db import make_engine, upgrade_to_head


@pytest.fixture
def db_url(tmp_path):
    return f"sqlite:///{tmp_path / 'test.sqlite3'}"


@pytest.fixture
def settings(tmp_path, db_url, monkeypatch):
    for var in ("SPORTMONKS_API_TOKEN", "API_FOOTBALL_KEY", "FOOTY_LIVE_PROVIDER"):
        monkeypatch.delenv(var, raising=False)
    return Settings(_env_file=None, FOOTY_DATABASE_URL=db_url, FOOTY_DATA_DIR=tmp_path / "data")


@pytest.fixture
def migrated_engine(db_url):
    upgrade_to_head(db_url)
    return make_engine(db_url)


@pytest.fixture
def client(settings, migrated_engine):
    return TestClient(create_app(settings=settings, engine=migrated_engine))
