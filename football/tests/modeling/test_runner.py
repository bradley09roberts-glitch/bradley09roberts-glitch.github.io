"""The runner's league map, run ids and worker thread limits, on SYNTHETIC matches."""

import os
from concurrent.futures import ProcessPoolExecutor

import pandas as pd
import pytest

import footy.db
from footy.modeling import runner
from footy.modeling.data import SECOND_TIER

from .test_walkforward import synthetic_league

_GETTERS = (
    "scipy_openblas_get_num_threads64_",
    "scipy_openblas_get_num_threads",
    "openblas_get_num_threads64_",
    "openblas_get_num_threads",
)
_SETTERS = (
    "scipy_openblas_set_num_threads64_",
    "scipy_openblas_set_num_threads",
    "openblas_set_num_threads64_",
    "openblas_set_num_threads",
)


def _call(lib, names, *args):
    for name in names:
        fn = getattr(lib, name, None)
        if fn is not None:
            return fn(*args)
    return None


def _blas_threads() -> list[int]:
    return [_call(lib, _GETTERS) for lib in runner.loaded_openblas()]


def _worker_state(_=None) -> tuple[list[int], dict]:
    return _blas_threads(), {v: os.environ.get(v) for v in runner.THREAD_ENV}


def test_e1_has_its_own_division_map_and_second_tier_is_unchanged():
    assert runner.divisions_for("E1") == ["E1", "E0"]
    assert runner.divisions_for("E0") == ["E0", "E1"]
    assert runner.divisions_for("SP1", ("SC0", "SP2")) == ["SP1", "SP2", "SC0"]
    for top in runner.LEAGUES:
        assert runner.divisions_for(top) == [top, SECOND_TIER[top]]
    # Other modules build their tier sets from SECOND_TIER: E1 stays a second tier there.
    assert "E1" not in SECOND_TIER and SECOND_TIER["E0"] == "E1"


def test_unknown_league_fails_before_any_worker_starts(monkeypatch):
    def no_pool(*a, **k):
        raise AssertionError("a pool was started")

    monkeypatch.setattr(runner, "ProcessPoolExecutor", no_pool)
    with pytest.raises(ValueError, match="no division map for league 'X9'"):
        runner.run_models(["baseline_frequency"], ["2324"], leagues=["E1", "X9"])


def test_e1_backtest_loads_e0_as_its_partner(monkeypatch):
    """SYNTHETIC: an E1 league with its own teams, plus E0. Targets are E1 matches only."""
    e0 = synthetic_league(seed=3)
    e1 = synthetic_league(seed=4)
    e1["division"] = "E1"
    e1["id"] += 10_000
    e1["home_team"] = e1.home_team.str.replace("eng-team", "eng-second")
    e1["away_team"] = e1.away_team.str.replace("eng-team", "eng-second")
    both = pd.concat([e0, e1], ignore_index=True).sort_values(["date", "id"])
    loaded = []

    def fake_load(engine, divisions):
        loaded.append(divisions)
        return both[both.division.isin(divisions)].reset_index(drop=True)

    monkeypatch.setattr(footy.db, "make_engine", lambda url: None)
    monkeypatch.setattr(runner, "load_matches", fake_load)
    led = runner._one(("baseline_frequency", "E1", ["2324"], ()))
    assert loaded == [["E1", "E0"]]
    assert set(led.league) == {"E1"} and set(led.season) == {"2324"}
    assert set(led.match_id) <= set(e1.id) and (led.status == "predicted").sum() > 0


def test_run_ids_keep_five_league_paths_and_name_other_leagues():
    five = runner.run_id_for("dixon_coles", "validation", ["2324"], list(runner.LEAGUES))
    assert five == "dixon_coles__validation__2324"
    shuffled = runner.run_id_for("dixon_coles", "validation", ["2324"], sorted(runner.LEAGUES))
    assert shuffled == five
    e1 = runner.run_id_for("dixon_coles", "validation", ["2324"], ["E1"])
    assert e1 == "dixon_coles__validation__2324__E1"
    six = runner.run_id_for("dixon_coles", "test", ["2425", "2526"], [*runner.LEAGUES, "E1"])
    assert six == "dixon_coles__test__2425-2526__D1-E0-E1-F1-I1-SP1"


def test_workers_run_with_one_blas_thread():
    """The initializer limits an OpenBLAS the worker inherited already loaded (a forked
    worker inherits the parent's thread count; the environment alone can't change it)."""
    libs = runner.loaded_openblas()
    before = _blas_threads()
    try:
        for lib in libs:
            _call(lib, _SETTERS, 2)  # make the parent's setting visibly different from 1
        with ProcessPoolExecutor(max_workers=1, initializer=runner.limit_threads) as ex:
            threads, env = ex.submit(_worker_state).result()
    finally:
        for lib, n in zip(libs, before, strict=True):
            if n is not None:
                _call(lib, _SETTERS, n)
    assert env == {"OPENBLAS_NUM_THREADS": "1", "OMP_NUM_THREADS": "1"}
    if not libs:
        pytest.skip("no OpenBLAS loaded in this process (not Linux, or numpy without it)")
    assert threads and all(n == 1 for n in threads)
