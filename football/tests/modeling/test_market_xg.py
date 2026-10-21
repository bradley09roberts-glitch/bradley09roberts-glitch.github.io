"""Market-implied grids: solving back from 1X2 and O/U 2.5 probabilities recovers the grid."""

import numpy as np

from footy.modeling.grid import GOALS, markets_from_grid, poisson_grid
from footy.modeling.market_xg import implied_grid


def test_implied_grid_recovers_known_rates():
    lh, la, rho = np.array([1.2, 2.1, 0.8, 1.6]), np.array([1.0, 0.7, 1.5, 1.6]), -0.07
    g = poisson_grid(lh, la, rho)
    m = markets_from_grid(g)
    target = np.stack([m.p1x2[:, 0], m.p1x2[:, 1], m.p_over[2.5]], axis=1)
    x, g2, ok = implied_grid(target)
    assert ok.all()
    np.testing.assert_allclose(np.exp(x[:, 0]), lh, rtol=1e-4)
    np.testing.assert_allclose(np.exp(x[:, 1]), la, rtol=1e-4)
    np.testing.assert_allclose(x[:, 2], rho, atol=1e-4)
    np.testing.assert_allclose(
        (g2 * GOALS[None, :, None]).sum((1, 2)), (g * GOALS[None, :, None]).sum((1, 2)), rtol=1e-6
    )
