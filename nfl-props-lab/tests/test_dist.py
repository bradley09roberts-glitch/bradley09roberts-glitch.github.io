import math

import numpy as np
import pytest

from npl.dist import Dist, expected_return, fair_decimal


@pytest.fixture
def d():
    rng = np.random.default_rng(0)
    return Dist.from_samples(rng.poisson(5, 20_000))


def test_half_point_line_sums_to_one(d):
    for line in (0.5, 3.5, 4.5, 10.5):
        p = d.line(line)
        assert p["push"] == 0
        assert math.isclose(p["over"] + p["under"], 1.0, abs_tol=1e-12)


def test_integer_line_has_push_and_sums_to_one(d):
    p = d.line(5)
    assert p["push"] > 0
    assert math.isclose(p["over"] + p["under"] + p["push"], 1.0, abs_tol=1e-12)
    assert math.isclose(p["push"], d.p_eq(5))
    assert math.isclose(p["over"], d.p_ge(6))


def test_milestone_is_greater_or_equal(d):
    # "5+" means X >= 5, which equals Over 4.5 and is strictly larger than P(X > 5)
    assert math.isclose(d.milestone(5), d.line(4.5)["over"])
    assert d.milestone(5) > d.line(5)["over"]


def test_alternate_lines_monotone(d):
    lines = np.arange(-5.5, 30.5, 0.5)
    overs = [d.line(x)["over"] for x in lines]
    assert all(a >= b - 1e-12 for a, b in zip(overs, overs[1:], strict=False))


def test_tails():
    d = Dist.from_samples(np.array([3, 4, 5]))
    assert d.p_ge(-100) == 1.0
    assert d.p_ge(100) == 0.0
    assert d.line(2.5)["over"] == 1.0
    assert d.line(5.5)["over"] == 0.0


def test_negative_yards_supported():
    d = Dist.from_samples(np.array([-7, -2, 0, 0, 3, 12]))
    assert d.lo == -7
    assert math.isclose(d.p_ge(0), 4 / 6)
    assert d.quantile(0.0) == -7
    assert math.isclose(d.line(-0.5)["over"], 4 / 6)


def test_crps_hand_calculated():
    # Point mass at 2, outcome 4: F = 1 for k >= 2, H = 1 for k >= 4 -> (1-0)^2 at k=2,3
    d = Dist(2, (1,))
    assert d.crps(4) == 2.0
    assert d.crps(2) == 0.0
    # Two-point 0/1 with p=0.5, outcome 1: k=0: (0.5-0)^2 = 0.25; k=1: (1-1)^2 = 0
    assert math.isclose(Dist(0, (1, 1)).crps(1), 0.25)


def test_json_roundtrip(d):
    assert Dist.from_json(d.to_json()) == d


def test_fair_price_and_ev_with_push():
    # p_win 0.4, push 0.2, loss 0.4 -> break-even 2.0 (win 1 unit vs lose 1 unit)
    assert math.isclose(fair_decimal(0.4, 0.2), 2.0)
    assert math.isclose(expected_return(0.4, 2.0, 0.2), 0.0, abs_tol=1e-12)
    # no-push: fair = 1/p
    assert math.isclose(fair_decimal(0.25), 4.0)
    assert math.isclose(expected_return(0.5, 2.1), 0.05)


def test_mc_error(d):
    se = d.mc_se(0.5)
    assert math.isclose(se, math.sqrt(0.25 / d.n))


def test_linear_pool_is_valid_and_monotone():
    from npl.forecast import mix_dist

    rng = np.random.default_rng(3)
    a = Dist.from_samples(rng.poisson(4, 5000) - 2)       # support may go negative
    b = Dist.from_samples(rng.negative_binomial(3, 0.4, 5000))
    for w in (0.0, 0.25, 0.5, 1.0):
        m = mix_dist(a, b, w)
        assert math.isclose(m.pmf.sum(), 1.0, abs_tol=1e-9)
        overs = [m.line(x)["over"] for x in np.arange(-3.5, 20.5, 0.5)]
        assert all(x >= y - 1e-12 for x, y in zip(overs, overs[1:], strict=False))
        # pooled tail probability is the weighted average of the parts (to rounding)
        assert math.isclose(m.p_ge(5), w * a.p_ge(5) + (1 - w) * b.p_ge(5), abs_tol=2e-6)
    assert math.isclose(mix_dist(a, b, 1.0).mean(), a.mean(), abs_tol=1e-3)
