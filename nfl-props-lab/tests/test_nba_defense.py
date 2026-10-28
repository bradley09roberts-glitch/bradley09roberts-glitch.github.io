"""NBA shot zones and defensive-matchup tables."""

import numpy as np
import pandas as pd
import pytest

from npl.nba import shots

ZONE_CASES = [
    # (x, y, points attempted, zone)   basket at (25, 0); baseline about y = -5.25
    (25, 2, 2, "RA"), (28, 2, 2, "RA"),
    (25, 9, 2, "PAINT"), (32, 12, 2, "PAINT"),
    (25, 17, 2, "MID"), (10, 3, 2, "MID"),
    (25, 30, 2, "MID"),           # a 2 recorded beyond the arc stays a 2 (mid-range)
    (1, 2, 3, "LC3"), (49, 2, 3, "RC3"),
    (5, 18, 3, "LW3"), (45, 18, 3, "RW3"), (25, 26, 3, "TOP3"), (31, 25, 3, "TOP3"),
    (25, 20, 3, "TOP3"),          # a 3 recorded inside the line keeps its 3-point area
]


@pytest.mark.parametrize("x,y,pts,zone", ZONE_CASES)
def test_zone_rules(x, y, pts, zone):
    assert shots.zone_of(np.array([x]), np.array([y]), np.array([pts]))[0] == zone


def test_every_attempt_gets_exactly_one_zone():
    rng = np.random.default_rng(3)
    x, y = rng.uniform(0, 50, 20000), rng.uniform(-5, 45, 20000)
    pts = rng.choice([2, 3], 20000)
    z = shots.zone_of(x, y, pts)
    assert set(z) <= set(shots.ZONES) and (z != "").all()
    assert set(z[pts == 2]) <= set(shots.TWO) and set(z[pts == 3]) <= set(shots.THREE)


lake = pytest.mark.skipif(not (shots.SHOTS_LAKE / "def_shots.parquet").exists(),
                          reason="shot tables not built")


@lake
def test_shot_tables_match_box_scores_and_positions_do_not_double_count():
    from npl.nba import canon

    S = shots.load()
    d = S["def_shots"]
    tg = canon.load()["team_games"].set_index(["game_id", "team"]).fga
    pb = d.groupby(["game_id", "offense"]).fga.sum()
    j = pd.concat([pb.rename("pbp"), tg.rename("box")], axis=1, join="inner")
    assert ((j.pbp - j.box).abs() <= 2).mean() > 0.99
    # each attempt is in one (position, zone) cell: no key appears twice
    assert not d.duplicated(["game_id", "defense", "pos", "zone"]).any()
    assert set(d.pos) <= {"G", "F", "C", "U"}
    assert (d.defense != d.offense).all()
