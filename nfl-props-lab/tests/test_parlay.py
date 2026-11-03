"""Parlay arithmetic (npl.parlay.fair_combo): the independence product, fair odds, expected value
at an offered price, and the same-game, same-player and thin-market flags. Every chance and
price below is made up."""

import math

import pytest

from npl import parlay as P


def _leg(chance, game="g1", player="p1", **kw):
    return {"chance": chance, "game_id": game, "player_id": player, **kw}


def test_independent_legs_multiply_and_give_fair_odds():
    r = P.fair_combo([_leg(0.5, "g1", "a"), _leg(0.6, "g2", "b"), _leg(0.55, "g3", "c")])
    assert r["legs"] == 3
    assert r["chance"] == pytest.approx(0.5 * 0.6 * 0.55)
    assert r["fair_odds"] == pytest.approx(1 / 0.165)  # 6.06
    assert r["independent"] is True and r["same_game"] == [] and r["same_player"] == []
    assert r["warnings"] == [] and r["thin"] == []
    assert r["price"] is None and r["ev"] is None and r["breakeven"] is None  # no price given


def test_expected_value_and_breakeven_at_an_offered_price():
    legs = [_leg(0.516, "g1", "a"), _leg(0.48, "g2", "b")]
    r = P.fair_combo(legs, price=4.2)
    chance = 0.516 * 0.48
    assert r["chance"] == pytest.approx(chance)
    assert r["ev"] == pytest.approx(chance * 4.2 - 1)  # EV = chance x decimal price - 1
    assert r["breakeven"] == pytest.approx(1 / 4.2)
    assert r["ev"] == pytest.approx(0.040256)  # 0.24768 x 4.2 = 1.040256
    assert r["fair_odds"] == pytest.approx(4.0375, abs=1e-4)
    assert P.fair_combo(legs, price=3.5)["ev"] == pytest.approx(0.24768 * 3.5 - 1)  # -13.3%
    # the fair price makes EV exactly zero
    assert P.fair_combo(legs, price=1 / chance)["ev"] == pytest.approx(0.0, abs=1e-12)
    assert P.fair_combo(legs, price=1 / chance + 0.5)["ev"] > 0


def test_one_leg_is_its_own_chance():
    r = P.fair_combo([_leg(0.25)], price=4.0)
    assert r["chance"] == 0.25 and r["fair_odds"] == 4.0 and r["ev"] == pytest.approx(0.0)


def test_same_game_legs_are_flagged_as_not_independent():
    legs = [_leg(0.5, "g1", "a"), _leg(0.5, "g1", "b"), _leg(0.5, "g2", "c"),
            _leg(0.5, "g1", "d")]
    r = P.fair_combo(legs)
    assert r["independent"] is False
    assert r["same_game"] == [[0, 1, 3]]
    assert r["same_player"] == []
    assert r["warnings"] == [f"Legs 1 and 2: {P.SAME_GAME}", f"Legs 1 and 4: {P.SAME_GAME}",
                             f"Legs 2 and 4: {P.SAME_GAME}"]
    assert P.SAME_GAME == "same game, chances not independent"
    assert r["chance"] == pytest.approx(0.0625)  # still the product, flagged as unreliable


def test_same_player_legs_are_flagged_too():
    legs = [_leg(0.55, "g1", "jg", market="pra", side="under"),
            _leg(0.5, "g1", "jg", market="pts", side="under"), _leg(0.6, "g2", "sc")]
    r = P.fair_combo(legs, price=3.5)
    assert r["same_game"] == [[0, 1]] and r["same_player"] == [[0, 1]]
    assert f"Legs 1 and 2: {P.SAME_PLAYER}" in r["warnings"]
    assert f"Legs 1 and 2: {P.SAME_GAME}" in r["warnings"]
    assert r["independent"] is False


def test_thin_markets_are_flagged():
    r = P.fair_combo([_leg(0.5, "g1", "a", books=2), _leg(0.5, "g2", "b", books=5),
                      _leg(0.5, "g3", "c")])
    assert r["thin"] == [0] and r["warnings"] == [f"Leg 1: {P.THIN}"]
    assert r["independent"] is True  # thin is not a correlation flag


def test_legs_without_ids_are_never_grouped():
    r = P.fair_combo([{"chance": 0.5}, {"chance": 0.5}])
    assert r["independent"] is True and r["same_game"] == [] and r["chance"] == 0.25


@pytest.mark.parametrize("legs", [[], [_leg(0.0)], [_leg(1.0)], [_leg(None)], [_leg(1.2)],
                                  [_leg(True)], [{"game_id": "g1"}]])
def test_bad_legs_are_refused(legs):
    with pytest.raises(ValueError):
        P.fair_combo(legs)


@pytest.mark.parametrize("price", [1.0, 0.5, -2.0])
def test_bad_prices_are_refused(price):
    with pytest.raises(ValueError):
        P.fair_combo([_leg(0.5)], price=price)


def test_many_legs_stay_exact():
    legs = [_leg(0.5, f"g{i}", f"p{i}") for i in range(10)]
    r = P.fair_combo(legs)
    assert r["chance"] == pytest.approx(2 ** -10) and r["fair_odds"] == pytest.approx(1024)
    assert math.isclose(r["chance"] * r["fair_odds"], 1.0)
