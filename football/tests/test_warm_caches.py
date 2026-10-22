"""The served app builds the team-stats index as it starts (footy.api.main.warm_caches); apps
made for tests and the snapshot export leave it to the first request.

SYNTHETIC TEST DATA: the fictional clubs of test_teams.
"""

import time

from fastapi.testclient import TestClient

from footy import teams
from footy.api.main import create_app, warm_caches
from footy.db import make_engine
from test_teams import build_world, write_primary


def cached(engine) -> bool:
    return teams._CACHE.get(engine) is not None


def test_the_served_app_builds_the_index_at_start_up(
    settings, migrated_engine, tmp_path, monkeypatch
):
    write_primary(tmp_path, monkeypatch)
    build_world(migrated_engine, tmp_path)
    teams._CACHE.pop(migrated_engine, None)
    with TestClient(create_app(settings=settings, engine=migrated_engine)):
        assert not cached(migrated_engine)  # tests: lazy, no thread started
    with TestClient(create_app(settings=settings, engine=migrated_engine, warm_index=True)) as c:
        deadline = time.monotonic() + 10
        while not cached(migrated_engine) and time.monotonic() < deadline:
            time.sleep(0.02)
        assert cached(migrated_engine)
        built = teams._CACHE[migrated_engine]
        assert c.get("/api/teams").json()["count"] == 6
        assert teams._CACHE[migrated_engine] is built  # the first request used it


def test_warm_up_on_a_database_that_is_not_ready_never_raises(tmp_path):
    engine = make_engine(f"sqlite:///{tmp_path / 'empty.sqlite3'}")  # no tables
    out = warm_caches(lambda: engine)
    assert out["status"] == "skipped" and "error" in out
