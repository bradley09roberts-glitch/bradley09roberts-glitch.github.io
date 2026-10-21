"""The three baselines from the brief, each fitted only on matches before the cutoff."""

import numpy as np
import pandas as pd
from scipy.optimize import minimize

from footy.modeling.base import ALL_MARKETS
from footy.modeling.grid import TOTAL_LINES, MarketProbs


class FrequencyBaseline:
    """League rates over the previous `days`: home/draw/away, each goal line, BTTS."""

    name = "baseline_frequency"
    markets = ALL_MARKETS

    def __init__(self, days: int = 730):
        self.days = days
        self.rates: dict[str, dict] = {}

    def params(self) -> dict:
        return {"days": self.days}

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        recent = history[history["date"] >= history["date"].max() - pd.Timedelta(days=self.days)]
        self.rates = {}
        for div, g in recent.groupby("division"):
            total = g.home_goals + g.away_goals
            self.rates[div] = {
                "1x2": np.bincount(g.outcome, minlength=3) / len(g),
                "over": {line: float((total > line).mean()) for line in TOTAL_LINES},
                "btts": float(((g.home_goals > 0) & (g.away_goals > 0)).mean()),
            }

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        r = [self.rates[d] for d in fixtures.division]
        return MarketProbs(
            p1x2=np.array([x["1x2"] for x in r]),
            p_over={line: np.array([x["over"][line] for x in r]) for line in TOTAL_LINES},
            p_btts=np.array([x["btts"] for x in r]),
        )


class EloBaseline:
    """Elo per league with home advantage, and an ordered-logit draw model fitted on the
    pre-match rating differences seen in training. Predicts 1X2 only.

    Teams new to a league start at the mean final rating of the teams that left it the
    previous season (the usual stand-in for promoted sides)."""

    name = "baseline_elo"
    markets = frozenset({"1X2"})

    def __init__(self, k: float = 20.0, home: float = 60.0):
        self.k, self.home = k, home
        self.ratings: dict[str, dict[str, float]] = {}
        self.cuts: dict[str, tuple[float, float]] = {}
        self.newcomer: dict[str, float] = {}

    def params(self) -> dict:
        return {"k": self.k, "home": self.home}

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        self.ratings, self.cuts, self.newcomer = {}, {}, {}
        for div, g in history.groupby("division"):
            ratings, diffs, outcomes = self._replay(g.sort_values(["date", "id"]))
            self.ratings[div] = ratings
            self.cuts[div] = _fit_ordered_logit(np.array(diffs), np.array(outcomes))
            # A team not yet seen in this league (promoted, before its first match) starts at
            # the mean of the bottom three of the latest season, i.e. the relegation places.
            last = g[g.season == g.season.max()]
            latest = sorted(ratings[t] for t in set(last.home_team) | set(last.away_team))
            self.newcomer[div] = float(np.mean(latest[:3]))

    def _replay(self, g: pd.DataFrame):
        ratings: dict[str, float] = {}
        prev_teams: set[str] = set()
        season_teams: set[str] = set()
        current = None
        diffs, outcomes = [], []
        for r in g.itertuples():
            if r.season != current:
                if current is not None:
                    prev_teams = season_teams
                season_teams = set(g.loc[g.season == r.season, "home_team"])
                left = [ratings[t] for t in prev_teams - season_teams if t in ratings]
                start = float(np.mean(left)) if left else 1500.0
                for t in season_teams - set(ratings):
                    ratings[t] = start
                current = r.season
            rh, ra = (
                ratings.setdefault(r.home_team, 1500.0),
                ratings.setdefault(r.away_team, 1500.0),
            )
            d = rh + self.home - ra
            exp_h = 1 / (1 + 10 ** (-d / 400))
            score = 1.0 if r.outcome == 0 else 0.5 if r.outcome == 1 else 0.0
            diffs.append(d)
            outcomes.append(r.outcome)
            ratings[r.home_team] = rh + self.k * (score - exp_h)
            ratings[r.away_team] = ra - self.k * (score - exp_h)
        return ratings, diffs, outcomes

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        out = []
        for r in fixtures.itertuples():
            ratings, new = self.ratings[r.division], self.newcomer[r.division]
            d = ratings.get(r.home_team, new) + self.home - ratings.get(r.away_team, new)
            out.append(_ordered_probs(d, *self.cuts[r.division]))
        return MarketProbs(p1x2=np.array(out))


def _ordered_probs(d: float, c1: float, c2: float, scale: float = 400.0) -> np.ndarray:
    """P(away), P(draw), P(home) from an ordered logit on the rating difference, returned in
    home, draw, away order."""
    x = d / scale
    p_away = 1 / (1 + np.exp(-(c1 - x)))
    p_not_home = 1 / (1 + np.exp(-(c2 - x)))
    return np.array([1 - p_not_home, p_not_home - p_away, p_away])


def _fit_ordered_logit(diffs: np.ndarray, outcomes: np.ndarray) -> tuple[float, float]:
    x = diffs / 400.0

    def nll(c):
        c1, c2 = c[0], c[0] + np.exp(c[1])
        p_away = 1 / (1 + np.exp(-(c1 - x)))
        p_not_home = 1 / (1 + np.exp(-(c2 - x)))
        p = np.where(
            outcomes == 0, 1 - p_not_home, np.where(outcomes == 1, p_not_home - p_away, p_away)
        )
        return -np.log(np.clip(p, 1e-12, 1)).sum()

    res = minimize(nll, x0=np.array([-1.0, 0.0]), method="Nelder-Mead")
    return float(res.x[0]), float(res.x[0] + np.exp(res.x[1]))
