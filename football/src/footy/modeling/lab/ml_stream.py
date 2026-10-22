"""Out-of-sample member forecasts to learn from (shared code of the `ml_stack` and `ml_online`
model-lab families; it registers no candidate itself).

A learner that corrects or combines models must be trained on what those models said
*before* each match, never on their in-sample fits. `MemberStream` produces exactly that inside
a walk-forward run: every match of the history the runner passes gets the forecasts its member
models made at that match's own decision time (00:00 UK on its D-012 collection day), with
the members fitted only on matches dated before that day.

- **Warm-up.** On the first `update`, the stream walks forward through the last
  `warmup_days` of the history, one decision batch (cutoff) at a time: the members are fitted
  on the history before the batch's cutoff day and predict the batch. The runner passes a
  three-year window, so a one-year warm-up leaves the members at least two years of history.
- **Afterwards.** Each `update(history, cutoff)` records the matches that have become final
  since the previous update, batch by batch in the same way, then fits the members at the new
  cutoff for `predict`. The batch at the previous cutoff reuses the members as they were
  fitted then, so the fixtures the runner asked for get exactly the forecasts made for them.
- **Every division in the history** is recorded, so a league's run also learns from its second
  tier (the runner loads a league with its partner division).
- **Market** (`market=True`). Each member's grid is also tilted onto the pre-closing consensus
  exactly as `MarketTilt` with weights 1 / 1 does (its own code, `market_for` and `predict`),
  so the same availability rules apply: a price is used only if it was quoted by the match's
  collection deadline and before kickoff (live: by the time of the run, from a file published
  on the fixture's own collection day). The consensus actually used is recorded.
- **Features** (`features=True`). ml_gbm's leakage-safe `FeatureScan` gives each match its
  engineered features as at its decision time: the scan's state after the history before the
  cutoff day (rolling goals, shots on target, shots and corners, Elo, online and Kalman
  ratings, rest days, congestion, promotion, the division's level).

A record is made from the history passed to `update` only. Its result is attached as a label
once the match is final, i.e. once it is in that history; no record holds a price quoted
after its own deadline.

`tilt_markets` moves a score grid onto targets for 1X2, O/U 1.5, 2.5, 3.5 and BTTS at once
(the minimum-information change, as `market_blend.tilt` does for three of them), so a learner
that corrects several markets still returns one coherent score grid.
"""

from collections.abc import Callable, Sequence
from dataclasses import dataclass

import numpy as np
import pandas as pd

from footy.ingest.fd_parse import model_cutoff
from footy.modeling.data import SECOND_TIER
from footy.modeling.grid import GOALS, MarketProbs
from footy.modeling.lab.market_blend import MarketTilt, default_prices
from footy.modeling.lab.ml_gbm import FEATURES, FeatureScan
from footy.modeling.walkforward import RESULT_COLUMNS, LeakageError

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
# The five markets of the D-025 composite, read off one grid, plus the grid's expected goals.
MARKET_COLS = ("p_H", "p_D", "p_A", "o15", "o25", "o35", "btts", "lam_h", "lam_a")
CONSENSUS_COLS = ("p_H", "p_D", "p_A", "p_O")
MIN_HISTORY = 3  # as the runner's eligibility: matches per team before the decision

# ---- market rows -------------------------------------------------------------------------


def market_rows(mp: MarketProbs) -> np.ndarray:
    """(n, 9) in MARKET_COLS order: 1X2, over 1.5 / 2.5 / 3.5, BTTS, expected goals (from the
    grid where the model gives none)."""
    g = mp.grid
    lam_h = mp.lam_h if mp.lam_h is not None else (g.sum(axis=2) * GOALS).sum(axis=1)
    lam_a = mp.lam_a if mp.lam_a is not None else (g.sum(axis=1) * GOALS).sum(axis=1)
    return np.column_stack(
        [mp.p1x2, mp.p_over[1.5], mp.p_over[2.5], mp.p_over[3.5], mp.p_btts, lam_h, lam_a]
    )


class _Given:
    """Stands in for MarketTilt's base model: returns the MarketProbs it was handed."""

    name = "given"
    mp: MarketProbs | None = None

    def fit(self, history, cutoff) -> None:
        pass

    def predict(self, fixtures) -> MarketProbs:
        return self.mp

    def params(self) -> dict:
        return {}


class _GridTilt(MarketTilt):
    """MarketTilt (weights 1 / 1) applied to grids handed to it, the consensus for a batch
    looked up once and reused for every member's grid."""

    def __init__(self, prices: Callable[[], pd.DataFrame]):
        super().__init__(_Given, 1.0, 1.0, "member_tilt", prices=prices)
        self.base = _Given()
        self._mk: pd.DataFrame | None = None

    def market_for(self, fixtures: pd.DataFrame) -> pd.DataFrame:
        return self._mk if self._mk is not None else super().market_for(fixtures)

    def tilt_all(
        self, frame: pd.DataFrame, mps: list[MarketProbs]
    ) -> tuple[list[MarketProbs], pd.DataFrame]:
        """Every grid tilted, and the first one's `last_used` (the consensus and whether its
        1X2 and O/U 2.5 were used)."""
        self._mk = None
        self._mk = super().market_for(frame)
        out, used = [], None
        try:
            for mp in mps:
                self.base.mp = mp
                out.append(self.predict(frame))
                used = self.last_used if used is None else used
        finally:
            self._mk = None
        self.last_used = used
        return out, used.reset_index(drop=True)


# ---- the coherent multi-market tilt -------------------------------------------------------

_TOT = GOALS[:, None] + GOALS[None, :]
SHAPE = np.stack(
    [
        GOALS[:, None] > GOALS[None, :],  # home win
        GOALS[:, None] == GOALS[None, :],  # draw
        _TOT > 1.5,
        _TOT > 2.5,
        _TOT > 3.5,
        (GOALS[:, None] >= 1) & (GOALS[None, :] >= 1),  # both teams score
    ]
).astype(float)  # (6, 11, 11)
_TYPES, _CELL_TYPE = np.unique(SHAPE.reshape(6, -1).T, axis=0, return_inverse=True)
_CELL_TYPE = _CELL_TYPE.ravel()
_ONEHOT = np.eye(len(_TYPES))[_CELL_TYPE]  # (121, T): each cell's indicator pattern


def shape_probs(grid: np.ndarray) -> np.ndarray:
    """(n, 6): P(home), P(draw), P(over 1.5), P(over 2.5), P(over 3.5), P(BTTS)."""
    return np.einsum("nij,kij->nk", grid, SHAPE)


def tilt_markets(
    grid: np.ndarray, target: np.ndarray, iters: int = 100, tol: float = 1e-10
) -> tuple[np.ndarray, np.ndarray]:
    """Exponentially tilt each (11, 11) grid so that P(home), P(draw), P(over 1.5 / 2.5 /
    3.5) and P(BTTS) equal `target` (n, 6): the minimum-KL change to the grid that hits them
    all. Every cell is multiplied by exp(theta . f(cell)), f its six indicators, so the cells
    fall into the few patterns of those indicators and the problem is a damped Newton
    iteration on the convex dual over the pattern masses (theta, six numbers per fixture).

    Returns (grids, ok). Where the targets cannot be reached (outside what any distribution on
    the base grid's support can have, or not converged), ok is False and the base grid is
    returned unchanged for that fixture."""
    grid = np.asarray(grid, dtype=float)
    t = np.asarray(target, dtype=float)
    n = len(grid)
    flat = np.clip(grid.reshape(n, -1), 0.0, None)
    mass = flat @ _ONEHOT  # (n, T)
    logm = np.log(np.where(mass > 0, mass, 1.0)) + np.where(mass > 0, 0.0, -np.inf)
    A = _TYPES  # (T, 6)
    ok = np.isfinite(t).all(axis=1) & (t > 0).all(axis=1) & (t < 1).all(axis=1)
    ok &= t[:, 0] + t[:, 1] < 1
    theta = np.zeros((n, 6))

    def dual(th):
        s = logm + th @ A.T
        mx = s.max(axis=1, keepdims=True)
        lse = mx[:, 0] + np.log(np.exp(s - mx).sum(axis=1))
        return lse - (th * t).sum(axis=1), s - lse[:, None]

    obj, logq = dual(theta)
    done = ~ok
    for _ in range(iters):
        q = np.exp(logq)
        m = q @ A
        grad = m - t
        done |= np.abs(grad).max(axis=1) < tol
        act = ~done
        if not act.any():
            break
        H = np.einsum("nt,ti,tj->nij", q[act], A, A) - m[act, :, None] * m[act, None, :]
        step = np.zeros_like(theta)
        step[act] = -np.linalg.solve(H + 1e-12 * np.eye(6), grad[act][:, :, None])[:, :, 0]
        slope = (grad * step).sum(axis=1)
        alpha = np.ones(n)
        with np.errstate(over="ignore", invalid="ignore"):
            new_obj, new_logq = dual(theta + step)
            for _ in range(40):  # backtracking: the dual is convex, so a short enough step helps
                slack = 1e-4 * alpha * slope + 1e-13 * (1.0 + np.abs(obj))
                bad = act & ~(new_obj <= obj + slack)
                if not bad.any():
                    break
                alpha = np.where(bad, alpha / 2, alpha)
                new_obj, new_logq = dual(theta + alpha[:, None] * step)
        upd = act & np.isfinite(new_obj)
        theta[upd] += alpha[upd, None] * step[upd]
        obj[upd], logq[upd] = new_obj[upd], new_logq[upd]
    ok &= np.abs(np.exp(logq) @ A - t).max(axis=1) < 1e-8
    out = grid.copy()
    if ok.any():
        factor = np.exp(theta[ok] @ A.T)[:, _CELL_TYPE].reshape(grid[ok].shape)
        g = grid[ok] * factor
        out[ok] = g / g.sum(axis=(1, 2), keepdims=True)
    return out, ok


# ---- the stream ----------------------------------------------------------------------------


@dataclass
class Block:
    """Member forecasts for a set of matches, one row each. Records of the history carry
    their result (`goals`); a block for fixtures carries the grids instead."""

    ids: np.ndarray
    division: np.ndarray
    date: np.ndarray  # datetime64[ns], the match's UK date
    raw: np.ndarray  # (n, K, 9) each member's markets (MARKET_COLS)
    tilted: np.ndarray | None  # (n, K, 9) after the market tilt (market streams only)
    market: np.ndarray | None  # (n, 4) consensus used (CONSENSUS_COLS), NaN where none was
    xh: np.ndarray | None  # (n, F) engineered features, home side's perspective
    xa: np.ndarray | None  # (n, F) the same from the away side's perspective
    n_home: np.ndarray
    n_away: np.ndarray
    goals: np.ndarray | None = None  # (n, 2) home and away goals (history records)
    grids: np.ndarray | None = None  # (K, n, 11, 11) member grids (fixtures)
    tilted_grids: np.ndarray | None = None  # (K, n, 11, 11) tilted grids (fixtures)
    last_used: pd.DataFrame | None = None  # MarketTilt.last_used of the first member

    def __len__(self) -> int:
        return len(self.ids)

    @property
    def eligible(self) -> np.ndarray:
        return (self.n_home >= MIN_HISTORY) & (self.n_away >= MIN_HISTORY)

    @property
    def priced(self) -> np.ndarray:
        """Both the 1X2 and the O/U 2.5 consensus were used."""
        if self.market is None:
            return np.zeros(len(self), dtype=bool)
        return np.isfinite(self.market).all(axis=1)


def concat_blocks(blocks: Sequence[Block]) -> Block:
    def cat(name):
        parts = [getattr(b, name) for b in blocks]
        return None if parts[0] is None else np.concatenate(parts)

    return Block(
        **{
            k: cat(k)
            for k in (
                "ids",
                "division",
                "date",
                "raw",
                "tilted",
                "market",
                "xh",
                "xa",
                "n_home",
                "n_away",
                "goals",
            )
        }
    )


def _cutoffs(h: pd.DataFrame) -> pd.DataFrame:
    """`cutoff_utc` and `cutoff_date` for frames that lack them (load_matches adds both)."""
    if "cutoff_utc" in h and "cutoff_date" in h:
        return h
    h = h.copy()
    days = h["date"].dt.date
    by_day = {d: model_cutoff(d) for d in days.unique()}
    h["cutoff_utc"] = pd.to_datetime(days.map(by_day), utc=True)
    h["cutoff_date"] = h.cutoff_utc.dt.tz_convert("Europe/London").dt.tz_localize(None)
    h["cutoff_date"] = h["cutoff_date"].dt.normalize()
    return h


class MemberStream:
    """Out-of-sample forecasts of registered member models for every match of the history,
    each made at the match's own decision time (see the module docstring)."""

    def __init__(
        self,
        members: Sequence[str],
        market: bool = False,
        features: bool = False,
        warmup_days: int = 365,
        prices: Callable[[], pd.DataFrame] = default_prices,
        factories: dict[str, Callable] | None = None,
    ):
        self._args = (members, market, features, warmup_days, prices, factories)
        if factories is None:
            from footy.modeling.registry import candidates  # late: the registry imports us

            factories = candidates()
        self.member_names = list(members)
        self.models = [factories[m]() for m in self.member_names]
        self.market, self.features, self.warmup_days = market, features, warmup_days
        self.tilter = _GridTilt(prices) if market else None
        self.scanner = FeatureScan() if features else None
        self.blocks: list[Block] = []
        self._all: Block | None = None
        self.horizon: pd.Timestamp | None = None  # matches dated from here are not recorded yet
        self.fitted_cutoff: pd.Timestamp | None = None
        self.counts = pd.Series(dtype=int)  # matches per team in the history of the last update
        self.fits = 0  # member fits so far (for the run's cost)

    def params(self) -> dict:
        return {
            "members": self.member_names,
            "member_params": {
                n: m.params() for n, m in zip(self.member_names, self.models, strict=True)
            },
            "market": self.market,
            "features": self.features,
            "warmup_days": self.warmup_days,
        }

    # ---- recording -----------------------------------------------------------------------

    def _fit_members(self, hist: pd.DataFrame, cutoff: pd.Timestamp) -> None:
        for m in self.models:
            m.fit(hist, cutoff)
        if self.scanner is not None:
            h = hist[hist.division.isin(TIER1 | TIER2)].sort_values(["date", "id"])
            if not self.scanner.covers(h):
                self.scanner = FeatureScan()
            self.scanner.extend(h)
        self.fitted_cutoff = cutoff
        self.fits += 1

    def _forecast(self, frame: pd.DataFrame, counts: pd.Series, keep_grids: bool) -> Block:
        """The members' forecasts for `frame` (no result columns) as they stand; `counts`:
        matches per team in the history they were fitted on."""
        mps = [m.predict(frame) for m in self.models]
        for n, mp in zip(self.member_names, mps, strict=True):
            if mp.grid is None:
                raise ValueError(f"stream member {n} has no score grid")
        raw = np.stack([market_rows(mp) for mp in mps], axis=1)
        tilted = market = last_used = tgrids = None
        if self.tilter is not None:
            tmps, last_used = self.tilter.tilt_all(frame, mps)
            tilted = np.stack([market_rows(mp) for mp in tmps], axis=1)
            market = last_used[list(CONSENSUS_COLS)].to_numpy(float).copy()
            market[~last_used.used_1x2.to_numpy(bool), :3] = np.nan
            market[~last_used.used_ou.to_numpy(bool), 3] = np.nan
            if keep_grids:
                tgrids = np.stack([mp.grid for mp in tmps])
        xh = xa = None
        if self.scanner is not None:
            xh, xa = self.scanner.fixture_features(frame)
        return Block(
            ids=frame["id"].to_numpy() if "id" in frame else np.arange(len(frame)),
            division=frame.division.to_numpy(),
            date=pd.to_datetime(frame.date).to_numpy(),
            raw=raw,
            tilted=tilted,
            market=market,
            xh=xh,
            xa=xa,
            n_home=frame.home_team.map(counts).fillna(0).to_numpy(int),
            n_away=frame.away_team.map(counts).fillna(0).to_numpy(int),
            grids=np.stack([mp.grid for mp in mps]) if keep_grids else None,
            tilted_grids=tgrids,
            last_used=last_used,
        )

    def update(self, history: pd.DataFrame, cutoff) -> None:
        """Record every match of `history` that became final since the last update (the last
        `warmup_days` on the first), each forecast at its own decision time, then fit the
        members at `cutoff`. `history` holds only matches final before `cutoff`."""
        cutoff = pd.Timestamp(cutoff)
        h = _cutoffs(history).sort_values(["date", "id"])
        cut_day = (
            cutoff.tz_convert("Europe/London").tz_localize(None).normalize()
            if cutoff.tzinfo is not None
            else cutoff.normalize()
        )
        if not h.empty and h["date"].max() >= cut_day:
            raise LeakageError(f"stream history reaches {h['date'].max()} >= {cut_day}")
        if self.fitted_cutoff is not None and cutoff < self.fitted_cutoff:
            self.__init__(*self._args)  # time went backwards: start again
        if self.horizon is None:
            self.horizon = cut_day - pd.Timedelta(days=self.warmup_days)
        new = h[h["date"] >= self.horizon]
        for c, grp in new.groupby("cutoff_utc", sort=True):
            c = pd.Timestamp(c)
            day = grp.cutoff_date.iloc[0]
            hist = h[h["date"] < day]
            if not hist.empty and hist["date"].max() >= day:
                raise LeakageError("a recorded batch's members saw its own day")
            if hist["id"].isin(grp["id"]).any():
                raise LeakageError("a recorded match is in its own training data")
            counts = pd.concat([hist.home_team, hist.away_team]).value_counts()
            n_h = grp.home_team.map(counts).fillna(0)
            n_a = grp.away_team.map(counts).fillna(0)
            if not ((n_h >= MIN_HISTORY) & (n_a >= MIN_HISTORY)).any():
                continue  # as the runner: no member is fitted for a batch nobody can predict
            if c != self.fitted_cutoff:
                self._fit_members(hist, c)
            block = self._forecast(grp.drop(columns=RESULT_COLUMNS), counts, keep_grids=False)
            block.goals = grp[["home_goals", "away_goals"]].to_numpy(float)
            self.blocks.append(block)
            self._all = None
        self.horizon = cut_day
        self._fit_members(h, cutoff)
        self.counts = pd.concat([h.home_team, h.away_team]).value_counts()

    def records(self) -> Block | None:
        """Every record so far, oldest first (None before the first)."""
        if not self.blocks:
            return None
        if self._all is None:
            self._all = concat_blocks(self.blocks)
        return self._all

    def predict(self, fixtures: pd.DataFrame) -> Block:
        """The members' forecasts (and grids) for `fixtures`, as fitted at the last update."""
        return self._forecast(fixtures, self.counts, keep_grids=True)


class MarketFacing:
    """What footy.live expects of a model + market candidate (as of MarketTilt): a settable
    `decision_time` (the time of a live run; backtests use each fixture's deadline),
    `last_used` (the consensus of the last predict and whether it was used) and
    `provenance()`. The class using it has a market `MemberStream` as `self.stream`."""

    last_used: pd.DataFrame | None = None

    @property
    def decision_time(self):
        return self.stream.tilter.decision_time

    @decision_time.setter
    def decision_time(self, value) -> None:
        self.stream.tilter.decision_time = value

    def provenance(self) -> dict | None:
        return self.stream.tilter.provenance()


# ---- design helpers shared by the learners ------------------------------------------------

EPS = 1e-6


def logit(p: np.ndarray) -> np.ndarray:
    p = np.clip(p, EPS, 1 - EPS)
    return np.log(p) - np.log1p(-p)


def feature_index(names: Sequence[str]) -> list[int]:
    return [FEATURES.index(f) for f in names]


def outcomes(goals: np.ndarray) -> dict[str, np.ndarray]:
    """Labels of the five composite markets from (n, 2) goals."""
    hg, ag = goals[:, 0], goals[:, 1]
    tot = hg + ag
    return {
        "1x2": np.where(hg > ag, 0, np.where(hg == ag, 1, 2)),
        "o15": (tot > 1.5).astype(float),
        "o25": (tot > 2.5).astype(float),
        "o35": (tot > 3.5).astype(float),
        "btts": ((hg > 0) & (ag > 0)).astype(float),
    }
