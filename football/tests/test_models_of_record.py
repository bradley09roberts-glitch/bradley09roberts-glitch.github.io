"""Models of record with effective-from times (footy.models_of_record, D-026).

SYNTHETIC: made-up model names and times, in temporary files; the real reference file is only
read, never written.
"""

import json
from datetime import UTC, date, datetime, timedelta

import pytest

from footy import cli, models_of_record
from footy.api import routes
from footy.models_of_record import Period

SWITCH = datetime(2026, 10, 21, 4, 30, tzinfo=UTC)


def write(path, **groups):
    path.write_text(json.dumps({"_comment": "SYNTHETIC", **groups}))
    return path


def test_plain_forms_are_one_period_from_the_start(tmp_path):
    path = write(tmp_path / "p.json", domestic="a", uefa=["b", "c", "b"])
    assert models_of_record.load(path) == {
        "domestic": [Period(None, ("a",))],
        "uefa": [Period(None, ("b", "c"))],
    }
    assert routes.primary_models(path) == {"domestic": ["a"], "uefa": ["b", "c"]}


def test_the_period_is_the_latest_starting_by_the_kickoff(tmp_path):
    path = write(
        tmp_path / "p.json",
        uefa=[
            {"from_utc": None, "models": ["old"]},
            {"from_utc": "2026-10-21T04:30:00Z", "models": ["new", "old"], "note": "x"},
        ],
    )
    (first, second) = models_of_record.load(path)["uefa"]
    assert first == Period(None, ("old",)) and second.from_utc == SWITCH
    assert second.note == "x"
    at = models_of_record.models_at
    assert at([first, second], datetime(2026, 10, 21, 4, 29, 59, tzinfo=UTC)) == ["old"]
    assert at([first, second], SWITCH) == ["new", "old"]  # a period starts at its from_utc
    assert routes.primary_models(path, at=datetime(2026, 10, 20, tzinfo=UTC)) == {"uefa": ["old"]}
    # Before a first period with a start time, nothing is the model of record.
    late = [Period(SWITCH, ("new",))]
    assert at(late, datetime(2026, 10, 20, tzinfo=UTC)) == []
    q = write(tmp_path / "q.json", uefa=[late[0].as_json()])
    assert routes.primary_models(q, at=SWITCH) == {"uefa": ["new"]}
    assert routes.primary_models(q, at=datetime(2026, 1, 1, tzinfo=UTC)) == {}


def test_fixture_time_is_the_kickoff_or_midnight_uk():
    naive = datetime(2026, 10, 24, 14, 0)  # noqa: DTZ001 (stored naive UTC, D-005)
    assert models_of_record.fixture_time(naive, date(2026, 10, 24)) == datetime(
        2026, 10, 24, 14, tzinfo=UTC
    )
    # No kickoff: 00:00 UK, which is 23:00 UTC the day before in summer time, 00:00 in winter.
    assert models_of_record.fixture_time(None, date(2026, 10, 24)) == datetime(
        2026, 10, 23, 23, tzinfo=UTC
    )
    assert models_of_record.fixture_time(None, date(2026, 10, 26)) == datetime(
        2026, 10, 26, 0, tzinfo=UTC
    )


@pytest.mark.parametrize(
    "bad",
    [
        ["a", 3],
        [],
        [""],
        "",
        " a",
        None,
        {"a": 1},
        [{"from_utc": None, "models": ["a"]}, "b"],  # periods mixed with names
        [{"from_utc": None}],  # no models
        [{"models": ["a"]}],  # no from_utc
        [{"from_utc": None, "models": []}],
        [{"from_utc": None, "models": ["a"], "form_utc": None}],  # unknown key
        [{"from_utc": "2026-10-21 04:30", "models": ["a"]}],  # no UTC offset
        [{"from_utc": "next Tuesday", "models": ["a"]}],
        [{"from_utc": 1761021000, "models": ["a"]}],
        [{"from_utc": None, "models": ["a"], "note": 5}],
        [  # only the first period may start from null
            {"from_utc": "2026-10-21T04:30:00Z", "models": ["a"]},
            {"from_utc": None, "models": ["b"]},
        ],
        [  # out of order
            {"from_utc": "2026-10-22T00:00:00Z", "models": ["a"]},
            {"from_utc": "2026-10-21T00:00:00Z", "models": ["b"]},
        ],
        [  # the same start twice
            {"from_utc": "2026-10-21T00:00:00Z", "models": ["a"]},
            {"from_utc": "2026-10-21T01:00:00+01:00", "models": ["b"]},
        ],
    ],
)
def test_malformed_values_are_refused(tmp_path, bad):
    path = write(tmp_path / "p.json", domestic=bad)
    with pytest.raises(ValueError, match="domestic"):
        models_of_record.load(path)
    with pytest.raises(ValueError, match="domestic"):
        routes.primary_models(path)


def test_missing_file_and_non_object(tmp_path):
    assert models_of_record.load(tmp_path / "missing.json") == {}
    (tmp_path / "list.json").write_text("[]")
    with pytest.raises(ValueError):
        models_of_record.load(tmp_path / "list.json")


def test_real_file_keeps_todays_meaning():
    """The converted reference file: UEFA is pan_euro_mix until 21 Oct 2026, 04:30 UTC, then
    pan_euro_mix_mxg; domestic is dixon_coles throughout."""
    periods = routes.model_periods()
    assert set(periods) == {"domestic", "uefa"}
    at = models_of_record.models_at
    assert at(periods["uefa"], datetime(2026, 10, 21, 4, 29, tzinfo=UTC)) == ["pan_euro_mix"]
    assert at(periods["uefa"], SWITCH) == ["pan_euro_mix_mxg"]
    for when in (datetime(2018, 8, 1, tzinfo=UTC), datetime(2027, 5, 1, tzinfo=UTC)):
        assert at(periods["domestic"], when) == ["dixon_coles"]


# --- appending a period ------------------------------------------------------------------------

NOW = datetime(2026, 10, 21, 12, 0, 0, 250000, tzinfo=UTC)


def test_append_converts_the_plain_form_and_keeps_its_meaning(tmp_path):
    path = write(tmp_path / "p.json", domestic="dixon_coles", uefa="u")
    start = models_of_record.parse_from("now", NOW)
    assert start == datetime(2026, 10, 21, 12, 0, 1, tzinfo=UTC)  # rounded up, never before now
    models_of_record.append_period(
        path, "domestic", ["tilt", "dixon_coles"], start, note="D-0xx", now=NOW
    )
    data = json.loads(path.read_text())
    assert data["_comment"] == "SYNTHETIC" and data["uefa"] == "u"  # the rest is untouched
    assert data["domestic"] == [
        {"from_utc": None, "models": ["dixon_coles"]},
        {"from_utc": "2026-10-21T12:00:01Z", "models": ["tilt", "dixon_coles"], "note": "D-0xx"},
    ]
    periods = models_of_record.load(path)["domestic"]
    assert models_of_record.models_at(periods, NOW) == ["dixon_coles"]
    assert models_of_record.models_at(periods, start) == ["tilt", "dixon_coles"]


@pytest.mark.parametrize(
    ("group", "models", "start", "message"),
    [
        ("domestic", ["x"], datetime(2026, 10, 21, 11, 59, tzinfo=UTC), "before now"),
        ("domestic", ["x"], datetime(2026, 10, 21, 13, 0, tzinfo=UTC), "not after the last"),
        ("domestic", ["x"], datetime(2026, 10, 22, 13, 0), "offset"),  # noqa: DTZ001
        ("domestic", [], datetime(2026, 10, 25, tzinfo=UTC), "non-empty"),
        ("domestic", ["x", ""], datetime(2026, 10, 25, tzinfo=UTC), "non-empty"),
        ("cups", ["x"], datetime(2026, 10, 25, tzinfo=UTC), "unknown group"),
    ],
)
def test_append_refusals_leave_the_file_untouched(tmp_path, group, models, start, message):
    path = write(
        tmp_path / "p.json",
        domestic=[
            {"from_utc": None, "models": ["a"]},
            {"from_utc": "2026-10-21T13:00:00Z", "models": ["b"]},  # already scheduled
        ],
    )
    before = path.read_text()
    with pytest.raises(ValueError, match=message):
        models_of_record.append_period(path, group, models, start, now=NOW)
    assert path.read_text() == before


def test_append_refuses_a_malformed_file(tmp_path):
    path = write(tmp_path / "p.json", domestic=[])
    with pytest.raises(ValueError, match="domestic"):
        models_of_record.append_period(
            path, "domestic", ["x"], datetime(2026, 10, 25, tzinfo=UTC), now=NOW
        )
    assert json.loads(path.read_text())["domestic"] == []


def test_cli_appends_from_now_and_refuses_the_past(tmp_path, capsys):
    path = write(tmp_path / "p.json", domestic="dixon_coles")
    args = ["set-model-of-record", "--group", "domestic", "--models", "tilt_x, dixon_coles"]
    t0 = datetime.now(UTC)
    assert cli.main([*args, "--from", "now", "--file", str(path)]) == 0
    out = capsys.readouterr().out
    assert "from the start: dixon_coles" in out and "tilt_x, dixon_coles" in out
    (first, second) = models_of_record.load(path)["domestic"]
    assert first == Period(None, ("dixon_coles",)) and second.models == ("tilt_x", "dixon_coles")
    assert t0 <= second.from_utc <= datetime.now(UTC) + timedelta(seconds=1)
    before = path.read_text()
    assert cli.main([*args, "--from", "2026-01-01T00:00:00Z", "--file", str(path)]) == 1
    assert cli.main([*args, "--from", "tomorrow", "--file", str(path)]) == 1
    assert "Refused" in capsys.readouterr().out
    assert path.read_text() == before


def test_appending_never_changes_the_period_of_an_earlier_kickoff():
    """Independent check (verifier), SYNTHETIC: for random schedules of periods, appending a
    period from time T leaves the models of every kickoff before T unchanged, and gives every
    kickoff at or after T the new period."""
    import random

    rng = random.Random(20261021)
    base = datetime(2026, 8, 1, tzinfo=UTC)
    for _ in range(300):
        starts = sorted(rng.sample(range(1, 2000), rng.randint(0, 5)))
        periods = [Period(None, ("m0",))] + [
            Period(base + timedelta(hours=h), (f"m{i + 1}",)) for i, h in enumerate(starts)
        ]
        switch = base + timedelta(hours=(starts[-1] if starts else 0) + rng.randint(1, 500))
        extended = [*periods, Period(switch, ("new", "m0"))]
        for _ in range(50):
            k = base + timedelta(minutes=rng.randint(-600, 3000 * 60))
            got = models_of_record.models_at(extended, k)
            if k < switch:
                assert got == models_of_record.models_at(periods, k)
            else:
                assert got == ["new", "m0"]
