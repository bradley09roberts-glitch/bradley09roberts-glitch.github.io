"""The interface every model implements."""

from typing import Protocol

import pandas as pd

from footy.modeling.grid import MarketProbs


class Model(Protocol):
    name: str
    # Markets the model predicts: subset of {"1X2", "OU", "BTTS"}. Score-grid models predict
    # all three from one grid; market-only baselines may predict fewer.
    markets: frozenset[str]

    def fit(self, history: pd.DataFrame, cutoff: pd.Timestamp) -> None:
        """Fit on `history`, which holds only matches final before `cutoff` (UTC)."""

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        """Probabilities for `fixtures` (one row each, same columns as history, results unused)."""

    def params(self) -> dict:
        """Hyper-parameters for the run manifest."""


ALL_MARKETS = frozenset({"1X2", "OU", "BTTS"})
