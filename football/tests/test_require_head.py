"""Unattended jobs refuse an out-of-date database instead of migrating it."""

import pytest

from footy import cli
from footy.db import SchemaNotCurrent, make_engine, require_head


def test_require_head_passes_on_a_migrated_database(migrated_engine):
    require_head(migrated_engine)


def test_require_head_refuses_an_unmigrated_database(tmp_path):
    engine = make_engine(f"sqlite:///{tmp_path / 'old.sqlite3'}")
    with pytest.raises(SchemaNotCurrent, match="footy migrate"):
        require_head(engine)


def test_capture_odds_refuses_without_migrating(tmp_path, monkeypatch, capsys):
    from footy import config, db

    url = f"sqlite:///{tmp_path / 'never-migrated.sqlite3'}"
    monkeypatch.setenv("FOOTY_DATABASE_URL", url)
    monkeypatch.setenv("FOOTY_DATA_DIR", str(tmp_path / "data"))
    config.get_settings.cache_clear()
    db.get_engine.cache_clear()
    try:
        assert cli.main(["capture-odds"]) == 1
        assert cli.main(["fd-fixtures"]) == 1
        assert "Refused" in capsys.readouterr().out
        assert db.current_revision(make_engine(url)) is None  # nothing was migrated
    finally:
        config.get_settings.cache_clear()
        db.get_engine.cache_clear()
