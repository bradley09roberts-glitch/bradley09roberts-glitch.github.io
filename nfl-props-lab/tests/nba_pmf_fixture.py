"""A synthetic slate for the NBA pmf reference (tests/fixtures/nba_pmf_reference.npz): rows that
cover what N1's code paths branch on (fractional, tiny, NaN and near-48 expected minutes, each
position group and an unknown one, missing opponent figures, a posted line or not, absent-teammate
rate multipliers). The reference arrays were written with the code as it stood before the combo
markets (pr, pa, ra) were added, so a test can prove the five original markets did not move."""

from __future__ import annotations

import numpy as np
import pandas as pd

FIVE = ("pts", "reb", "ast", "fg3m", "pra")
STATS = ("pts", "reb", "ast", "fg3m")
REF_VERSIONS = ("nba-0.3", "nba-0.1")


def frame() -> tuple[pd.DataFrame, dict]:
    rng = np.random.default_rng(20261104)
    n = 12
    mu = np.array([30.4, 2.3, 47.6, np.nan, 18.0, 36.75, 24.2, 11.5, 33.0, 28.9, 8.1, 40.2])
    x = pd.DataFrame({
        "min_mu": mu,
        "min_sd": np.round(rng.uniform(2, 9, n), 2),
        "pos": ["G", "F", "C", "G", "X", "F", "C", "G", "F", "C", "G", "F"],
        "opp_pace": np.round(rng.uniform(95, 104, n), 2),
        "lg_pace": 99.5,
        "opp_n": [10, 3, 0, np.nan, 25, 40, 8, 12, 60, 5, 1, 30],
        "min_sum": np.round(rng.uniform(40, 900, n), 1),
        "has_line": [True, False, True, False, True, True, False, True, True, False, False, True],
    })
    for s, r in (("pts", 0.5), ("reb", 0.2), ("ast", 0.12), ("fg3m", 0.05)):
        x[f"{s}_sum"] = np.round(x.min_sum * r * rng.uniform(0.5, 1.6, n), 1)
        x[f"def_{s}"] = np.round(rng.uniform(0.8, 1.2, n), 3)
        x[f"lg_{s}"] = 1.0
        x[f"rmult_{s}"] = np.where(rng.random(n) < 0.3, np.round(rng.uniform(1.0, 1.1, n), 3), 1.0)
        x[f"{s}_gm"] = np.round(x.min_sum * r / 12 * rng.uniform(0.5, 1.5, n), 2)
        x[f"{s}_l10"] = np.round(x[f"{s}_gm"] * rng.uniform(0.8, 1.2, n), 2)
    x.loc[3, "def_pts"] = np.nan  # an opponent without figures
    x["pra_gm"] = x.pts_gm + x.reb_gm + x.ast_gm
    x["pra_l10"] = np.round(x.pts_l10 + x.reb_l10 + x.ast_l10, 2)
    lg = {"pos_rate": {"G": {"pts": .5, "reb": .15, "ast": .15, "fg3m": .05},
                       "F": {"pts": .45, "reb": .2, "ast": .1, "fg3m": .04},
                       "C": {"pts": .48, "reb": .32, "ast": .07, "fg3m": .01}},
          "per_poss": {s: 1.0 for s in STATS}, "pace48": 99.5}
    return x, lg
