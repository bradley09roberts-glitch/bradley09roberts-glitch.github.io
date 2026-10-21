import numpy as np
import pytest

from footy.modeling.grid import (
    MAX_GOALS,
    TOTAL_LINES,
    check_coherent,
    markets_from_grid,
    mix_grids,
    poisson_grid,
    tail_mass,
)
from footy.modeling.metrics import block_bootstrap_ci, logloss_binary, outcome_index, rps


@pytest.mark.parametrize("rho", [-0.2, -0.05, 0.0, 0.1])
def test_grid_is_a_distribution_and_markets_are_coherent(rho):
    lh, la = np.array([0.4, 1.3, 2.9, 6.0]), np.array([0.3, 1.1, 0.8, 5.5])
    g = poisson_grid(lh, la, rho)
    assert g.shape == (4, MAX_GOALS + 1, MAX_GOALS + 1)
    assert (g >= 0).all() and np.allclose(g.sum(axis=(1, 2)), 1)
    mp = markets_from_grid(g, lh, la)
    assert check_coherent(mp) == []
    for a, b in zip(TOTAL_LINES, TOTAL_LINES[1:], strict=False):
        assert (mp.p_over[b] <= mp.p_over[a] + 1e-12).all()


def test_dixon_coles_adjustment_keeps_total_and_moves_low_scores():
    g0, g1 = poisson_grid([1.4], [1.1], 0.0), poisson_grid([1.4], [1.1], -0.1)
    assert np.isclose(g0.sum(), 1) and np.isclose(g1.sum(), 1)
    assert g1[0, 0, 0] > g0[0, 0, 0] and g1[0, 1, 1] > g0[0, 1, 1]
    assert g1[0, 1, 0] < g0[0, 1, 0]


def test_tail_mass_is_reported_not_lost():
    g = poisson_grid([9.0], [0.5], 0.0)
    assert tail_mass(g)[0] > 0.4 and np.isclose(g.sum(), 1)


def test_mixture_of_grids_stays_coherent():
    g = mix_grids([poisson_grid([1.2], [1.0]), poisson_grid([2.0], [0.7], -0.1)], [0.3, 0.7])
    assert check_coherent(markets_from_grid(g)) == []


def test_rps_known_values():
    p = np.array([[1.0, 0, 0], [0, 1.0, 0], [1 / 3, 1 / 3, 1 / 3]])
    assert np.allclose(rps(p, np.array([0, 1, 0])), [0, 0, (4 / 9 + 1 / 9) / 2])
    # a draw is closer to a home win than an away win is
    assert (
        rps(np.array([[0, 1.0, 0]]), np.array([0]))[0]
        < rps(np.array([[0, 0, 1.0]]), np.array([0]))[0]
    )


def test_outcomes_and_logloss():
    assert list(outcome_index([2, 1, 0], [1, 1, 3])) == [0, 1, 2]
    assert np.isclose(logloss_binary([0.5], [1])[0], np.log(2))


def test_block_bootstrap_brackets_the_mean():
    rng = np.random.default_rng(0)
    v = rng.normal(0.2, 0.05, 400)
    lo, hi = block_bootstrap_ci(v, np.repeat(np.arange(40), 10))
    assert lo < v.mean() < hi
