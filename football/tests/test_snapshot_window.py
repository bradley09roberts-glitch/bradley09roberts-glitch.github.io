"""The snapshot's rolling window of match pages, its past shards and its size warnings
(audit 22 Oct 2026: H2, H3).

SYNTHETIC TEST DATA: fictional teams (fdfixtures), made-up probabilities and results, a
made-up build of the site. Nothing here is a real price, prediction or result.
"""

import json
from datetime import UTC, datetime, timedelta
from urllib.parse import quote

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy import snapshot
from footy.api import routes
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.publish import FixtureIn, publish, settle
from footy.snapshot import (
    FULL_PAGE_DAYS,
    HOST_LIMITS,
    MB,
    PART_REF,
    WARN_FRACTION,
    canonical_key,
    run_export,
    size_warnings,
)

NOW = datetime.now(UTC).replace(microsecond=0)
OLD_DAY = (NOW - timedelta(days=FULL_PAGE_DAYS + 6)).date()
NEW_DAY = (NOW + timedelta(days=2)).date()

BUILT_INDEX = """<!doctype html>
<html lang="en-GB">
  <head>
    <script type="module" crossorigin src="./assets/index-AbCd1234.js"></script>
  </head>
  <body><div id="root"></div></body>
</html>
"""


def kickoff(day):
    return datetime(day.year, day.month, day.day, 14, 0, tzinfo=UTC)


def pub(engine, tmp_path, day, model, home, away, lam=(1.4, 1.1)):
    mp = markets_from_grid(poisson_grid([lam[0]], [lam[1]]), [lam[0]], [lam[1]])
    fx = FixtureIn(
        "E0",
        "2627",
        day,
        kickoff(day),
        home,
        away,
        features={
            "home_matches_in_window": 40,
            "away_matches_in_window": 38,
            "model_params": {"synthetic": "x" * 400, "model": model},  # shared across records
        },
        missing_fields=["lineups", "injuries", "odds"],
    )
    (pid,) = publish(
        engine,
        [fx],
        mp,
        model_name=model,
        model_version="v-test",
        training_cutoff_utc=kickoff(day) - timedelta(days=2),
        data_freshness={"latest_result_by_division": {"E0": "2026-10-19"}},
        now=kickoff(day) - timedelta(days=1),
        records_dir=tmp_path / "records",
    )
    return pid


@pytest.fixture
def world(settings, migrated_engine, tmp_path, monkeypatch):
    path = tmp_path / "primary_models.json"
    path.write_text(json.dumps({"_comment": "SYNTHETIC", "domestic": ["rec_m"]}))
    monkeypatch.setattr(routes, "PRIMARY_MODELS_PATH", path)
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    old = ("eng-testtown", "eng-sampleton")
    new = ("eng-sampleton", "eng-mockford")
    ids = {
        "old_rec": pub(migrated_engine, tmp_path, OLD_DAY, "rec_m", *old),
        "old_shadow": pub(migrated_engine, tmp_path, OLD_DAY, "shadow_m", *old, lam=(1.0, 1.5)),
        "new_rec": pub(migrated_engine, tmp_path, NEW_DAY, "rec_m", *new),
        "new_shadow": pub(migrated_engine, tmp_path, NEW_DAY, "shadow_m", *new, lam=(1.0, 1.5)),
    }
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
                "last_raw_file_id) VALUES ('E0', '2627', :d, :h, :a, 2, 1, 'H', 1, 1, 1)"
            ),
            {"d": OLD_DAY.isoformat(), "h": old[0], "a": old[1]},
        )
    settle(migrated_engine, now=kickoff(OLD_DAY) + timedelta(hours=3))
    dist = tmp_path / "dist-snapshot"
    (dist / "assets").mkdir(parents=True)
    (dist / "index.html").write_text(BUILT_INDEX)
    (dist / "assets" / "index-AbCd1234.js").write_text("console.log('SYNTHETIC bundle');\n")
    env_file = tmp_path / "synthetic.env"
    env_file.write_text("FOOTY_DISPLAY_TZ=Europe/London\n")
    refs = {
        "old": f"E0:2627:{OLD_DAY.isoformat()}:{old[0]}:{old[1]}",
        "new": f"E0:2627:{NEW_DAY.isoformat()}:{new[0]}:{new[1]}",
    }
    return {"ids": ids, "refs": refs, "dist": dist, "env_file": env_file}


def read_entries(out):
    main = json.loads((out / "snapshot.json").read_text())
    docs = {"snapshot.json": main}
    for sh in main["shards"]:
        docs[sh["file"]] = json.loads((out / sh["file"]).read_text())
    return {k: (name, doc) for name, doc in docs.items() for k in doc["entries"]}


def models_key(ref):
    return canonical_key(f"/api/fixtures/{quote(ref, safe='')}/models")


def test_old_fixtures_keep_only_their_record_of_record_pages(settings, world, tmp_path):
    out = tmp_path / "site"
    result = run_export(settings, out, dist=world["dist"], env_file=world["env_file"])
    entries = read_entries(out)
    ids, refs = world["ids"], world["refs"]
    # Inside the window: every model's page and the every-model list.
    for pid in (ids["new_rec"], ids["new_shadow"]):
        assert f"/api/predictions/{pid}" in entries
    assert models_key(refs["new"]) in entries
    # Before it: the record of record only, though its editions list links the shadow.
    assert f"/api/predictions/{ids['old_rec']}" in entries
    assert f"/api/predictions/{ids['old_shadow']}" not in entries
    assert models_key(refs["old"]) not in entries
    # The Past page's lists are in their own shard; the default list has the old fixture once.
    past_key = "/api/past?limit=500&record_type=genuine_prematch"
    name, doc = entries[past_key]
    assert name.startswith("snapshot-past-")
    body = doc["bodies"][doc["entries"][past_key][1]]
    assert [c["id"] for c in body["matches"]] == [ids["old_rec"]]
    report = result.report()
    assert report["projection"]["full_page_days"] == FULL_PAGE_DAYS
    assert isinstance(report["warnings"], list)
    assert any("FULL_PAGE_DAYS" in line for line in report["not_covered"])


def test_model_parameters_are_shared_once_per_file(settings, world, tmp_path):
    out = tmp_path / "site"
    run_export(settings, out, dist=world["dist"], env_file=world["env_file"])
    entries = read_entries(out)
    key = f"/api/predictions/{world['ids']['new_shadow']}"
    _, doc = entries[key]
    page = doc["bodies"][doc["entries"][key][1]]
    ref = page["features"]["model_params"]
    assert list(ref) == [PART_REF]
    assert doc["parts"][ref[PART_REF]]["model"] == "shadow_m"
    assert page["features"]["home_matches_in_window"] == 40  # the rest stays in place


def test_size_warnings_within_a_fifth_of_a_limit():
    near = int(HOST_LIMITS["total_bytes"] * (1 - WARN_FRACTION / 2))
    warned = size_warnings([("snapshot.json", near - 11 * MB), ("snapshot-teams-1.json", 11 * MB)])
    assert any("bytes in all" in w for w in warned)
    assert any("snapshot-teams-1.json" in w for w in warned)  # over 80% of a shard's 12 MB
    assert size_warnings([("snapshot.json", 1 * MB)]) == []
    files = [(f"snapshot-x-{n}.json", 10) for n in range(int(HOST_LIMITS["files"] * 0.9))]
    assert any("files" in w for w in size_warnings(files))


def test_a_site_near_the_limit_is_warned_about(settings, world, tmp_path, monkeypatch):
    """A site within a fifth of the host's limit is still written, with a warning and a
    projection that says the limit is near."""
    first = run_export(settings, tmp_path / "a", dist=world["dist"], env_file=world["env_file"])
    assert not any("bytes in all" in w for w in first.report()["warnings"])
    total = first.report()["total_bytes"]
    monkeypatch.setitem(snapshot.HOST_LIMITS, "total_bytes", int(total / 0.9))
    out = tmp_path / "b"
    report = run_export(settings, out, dist=world["dist"], env_file=world["env_file"]).report()
    assert (out / "snapshot.json").exists()
    assert any("bytes in all" in w for w in report["warnings"])
    assert report["projection"]["weeks_to_warning"] == 0.0
