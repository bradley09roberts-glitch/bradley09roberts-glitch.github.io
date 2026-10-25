"""Money backtest and settlement: ONLY real recorded prices.

Kept separate from probability evaluation. With no recorded quotes the result is the
explicit state `ROI unavailable` - a price is never invented from fair odds.

Settlement of one single (stake s, decimal price d):
  win  -> net profit s*(d-1) (exchange: minus commission on net winnings)
  loss -> -s
  push -> 0 (integer line hit exactly; stake returned)
  void -> 0 (bookmaker rule, e.g. player did not take part)
  pending -> not settled (game not final, or participation unverified)

Quote eligibility (no look-ahead): observed_at <= decision time, and the bookmaker's
quote_time >= decision time - freshness. Among eligible quotes the rule is either a fixed
bookmaker or the best price available AT the decision time among allowed books; never the
best price seen later.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field

import numpy as np
import pandas as pd

from .dist import Dist, expected_return, fair_decimal


# ------------------------------------------------------------------ settlement rules
@dataclass(frozen=True)
class RuleProfile:
    name: str
    void_if_not_played: bool = True       # player must take part or bets are void
    overtime_counts: bool = True          # NFL props normally include overtime
    requires_start: bool = False          # some books void QB props unless he starts
    settle_on_first_result: bool = True   # later stat corrections do not resettle
    commission: float = 0.0               # exchanges: fraction of net winnings
    verified: bool = False                # True only after reading the book's rules
    note: str = "Default profile; the bookmaker's own rules have not been reviewed."


RULES = {
    "default": RuleProfile("default"),
    "betfair_ex_uk": RuleProfile("betfair_ex_uk", commission=0.05,
                                 note="Exchange: 5% assumed commission; rules not reviewed."),
    "smarkets": RuleProfile("smarkets", commission=0.02,
                            note="Exchange: 2% assumed commission; rules not reviewed."),
    "matchbook": RuleProfile("matchbook", commission=0.02,
                             note="Exchange: 2% assumed commission; rules not reviewed."),
}


def rule_for(bookmaker: str) -> RuleProfile:
    return RULES.get(bookmaker, RULES["default"])


def outcome(selection: str, line: float, value: float) -> str:
    if selection == "over":
        return "win" if value > line else ("push" if value == line else "loss")
    if selection == "under":
        return "win" if value < line else ("push" if value == line else "loss")
    if selection == "yes":  # milestone k+: value >= line
        return "win" if value >= line else "loss"
    raise ValueError(selection)


def settle(selection: str, line: float, price: float, stake: float, participation: str,
           value: float | None, rule: RuleProfile, started: bool | None = None) -> dict:
    if participation in ("not_final", "active_unverified", "unknown", "absent") or (
            participation == "played" and (value is None or pd.isna(value))):
        return {"status": "pending", "profit": None, "returned": None}
    if participation != "played":
        if rule.void_if_not_played:
            return {"status": "void", "profit": 0.0, "returned": stake}
        # A book whose bets stand on non-participants needs its exact rule encoded first.
        return {"status": "pending", "profit": None, "returned": None,
                "note": "non-participant rule not encoded for this bookmaker"}
    if rule.requires_start and started is False:
        return {"status": "void", "profit": 0.0, "returned": stake}
    res = outcome(selection, line, float(value))
    if res == "win":
        gross = stake * (price - 1.0)
        profit = gross * (1 - rule.commission)
        return {"status": "win", "profit": profit, "returned": stake + profit}
    if res == "push":
        return {"status": "push", "profit": 0.0, "returned": stake}
    return {"status": "loss", "profit": -stake, "returned": 0.0}


# ------------------------------------------------------------------ probabilities
def line_probs(d: Dist, selection: str, line: float) -> tuple[float, float]:
    """(p_win, p_push) for a selection on the saved distribution."""
    if selection == "yes":
        return d.p_ge(line), 0.0
    p = d.line(line)
    return (p["over"] if selection == "over" else p["under"]), p["push"]


def demargin(over_price: float, under_price: float) -> dict:
    """Implied probabilities and multiplicative margin removal for a complete pair."""
    io, iu = 1 / over_price, 1 / under_price
    book = io + iu
    return {"implied_over": io, "implied_under": iu, "overround": book - 1,
            "fair_over": io / book, "fair_under": iu / book}


# ------------------------------------------------------------------ strategy
@dataclass(frozen=True)
class Strategy:
    id: str
    min_ev: float = 0.03            # on the margin-adjusted probability
    prob_margin: float = 0.02       # subtract from p before EV: model-uncertainty haircut
    min_prob: float = 0.10
    max_prob: float = 0.90
    min_odds: float = 1.40
    max_odds: float = 5.0
    freshness_minutes: int = 180
    availability: tuple = ("verified_uk",)
    bookmakers: tuple | None = None  # None = best price at decision among allowed books
    markets: tuple | None = None
    stake: float = 1.0
    one_per_player_market: bool = True
    note: str = ""

    def manifest(self) -> dict:
        return asdict(self)


UNFILTERED = Strategy("unfiltered-positive-ev", min_ev=0.0, prob_margin=0.0, min_prob=0.0,
                      max_prob=1.0, min_odds=1.01, max_odds=1000,
                      note="every eligible priced selection with positive model EV")


def eligible_quotes(q: pd.DataFrame, decision_at: pd.Timestamp, s: Strategy) -> pd.DataFrame:
    if q.empty:
        return q
    if (q.availability == "synthetic_test").any() and "synthetic_test" not in s.availability:
        q = q[q.availability != "synthetic_test"]
    q = q[q.availability.isin(s.availability) & q.player_id.notna() & q.game_id.notna()]
    obs = pd.to_datetime(q.observed_at, utc=True)
    qt = pd.to_datetime(q.quote_time.fillna(q.observed_at), utc=True)
    fresh = qt >= decision_at - pd.Timedelta(minutes=s.freshness_minutes)
    q = q[(obs <= decision_at) & fresh]
    if s.bookmakers:
        q = q[q.bookmaker.isin(s.bookmakers)]
    # latest observation per bookmaker/selection as of the decision
    q = q.sort_values("observed_at").groupby(
        ["bookmaker", "game_id", "player_id", "market", "line", "selection"]).tail(1)
    return q


def select_bets(forecasts: pd.DataFrame, quotes: pd.DataFrame, s: Strategy) -> pd.DataFrame:
    """forecasts: rows with game_id, player_id, market, dist (Dist), decision_at, kickoff.
    Returns one row per candidate with eligibility reason (selected or why not)."""
    rows = []
    for f in forecasts.itertuples():
        dq = eligible_quotes(quotes, f.decision_at, s)
        dq = dq[(dq.game_id == f.game_id) & (dq.player_id == f.player_id) &
                (dq.market == f.market)]
        if s.markets and f.market not in s.markets:
            continue
        if dq.empty:
            rows.append({"game_id": f.game_id, "player_id": f.player_id, "market": f.market,
                         "selected": False, "reason": "no eligible quote"})
            continue
        for qq in dq.itertuples():
            p_win, p_push = line_probs(f.dist, qq.selection, qq.line)
            p_adj = max(p_win - s.prob_margin, 0.0)
            ev = expected_return(p_adj, qq.decimal_price, p_push)
            ev_raw = expected_return(p_win, qq.decimal_price, p_push)
            reason = "selected"
            if not (s.min_prob <= p_win <= s.max_prob):
                reason = "probability outside band"
            elif not (s.min_odds <= qq.decimal_price <= s.max_odds):
                reason = "odds outside band"
            elif ev < s.min_ev:
                reason = "EV below threshold"
            rows.append({"game_id": f.game_id, "player_id": f.player_id, "market": f.market,
                         "kickoff": f.kickoff, "decision_at": f.decision_at,
                         "bookmaker": qq.bookmaker, "availability": qq.availability,
                         "line": qq.line, "selection": qq.selection,
                         "decimal_price": qq.decimal_price, "quote_time": qq.quote_time,
                         "observed_at": qq.observed_at, "quote_id": getattr(qq, "id", None),
                         "p_win": p_win, "p_push": p_push, "p_adj": p_adj,
                         "fair_decimal": fair_decimal(p_win, p_push), "ev": ev,
                         "ev_raw": ev_raw, "selected": reason == "selected",
                         "reason": reason})
    out = pd.DataFrame(rows)
    if out.empty or not s.one_per_player_market:
        return out
    sel = out[out.selected].sort_values("ev", ascending=False)
    keep = sel.drop_duplicates(["game_id", "player_id", "market"]).index
    out.loc[out.selected & ~out.index.isin(keep), ["selected", "reason"]] = [
        False, "lower EV duplicate for same player-market"]
    return out


# ------------------------------------------------------------------ bankroll simulation
@dataclass
class Bankroll:
    start: float = 100.0
    currency: str = "GBP"
    stake: float = 1.0           # GBP per 1-unit bet (illustrative)
    settle_after_hours: float = 4.0
    log: list = field(default_factory=list)


def run_bankroll(bets: pd.DataFrame, bank: Bankroll) -> tuple[pd.DataFrame, dict]:
    """Chronological placement and settlement with reserved stakes. A bet is placed only
    if available funds (balance minus open stakes) cover it; returns are credited when the
    game is assumed settled (kickoff + settle_after_hours)."""
    if bets.empty:
        return bets, {"start": bank.start, "end": bank.start, "skipped_insufficient": 0}
    b = bets.copy()
    b["place_at"] = pd.to_datetime(b.decision_at, utc=True)
    b["settle_at"] = pd.to_datetime(b.kickoff, utc=True) + pd.Timedelta(hours=bank.settle_after_hours)
    events = [(t, 1, i) for i, t in b.settle_at.items()] + [(t, 0, i) for i, t in b.place_at.items()]
    events.sort(key=lambda e: (e[0], e[1]))  # placements before settlements at equal times
    balance, open_ = bank.start, {}
    placed, skipped, curve = set(), 0, []
    for t, kind, i in events:
        stake = bank.stake * float(b.at[i, "stake_units"])
        if kind == 0:
            if balance - sum(open_.values()) + 1e-9 >= stake:
                open_[i] = stake
                placed.add(i)
            else:
                skipped += 1
        elif i in open_:
            st = b.at[i, "status"]
            stake = open_.pop(i)
            if st == "pending":
                open_[i] = stake  # stays reserved
                continue
            balance += float(b.at[i, "profit_units"]) * bank.stake
            curve.append({"t": t, "balance": balance})
    b["placed"] = b.index.isin(placed)
    return b, {"start": bank.start, "end": balance, "currency": bank.currency,
               "stake_gbp": bank.stake, "skipped_insufficient": skipped,
               "open_stakes": sum(open_.values()), "curve": curve}


# ------------------------------------------------------------------ metrics
def drawdown(profits: np.ndarray, start: float = 0.0) -> tuple[float, float]:
    """Maximum drawdown (from per-bet profits in order) in units and as a fraction of the
    running peak of start + cumulative profit."""
    path = start + np.concatenate([[0.0], np.cumsum(profits)])
    peak = np.maximum.accumulate(path)
    dd = peak - path
    i = int(np.argmax(dd))
    frac = dd[i] / peak[i] if peak[i] > 0 else np.nan
    return float(dd.max()), float(frac)


def longest_losing_run(statuses: list[str]) -> int:
    best = cur = 0
    for s in statuses:
        if s == "loss":
            cur += 1
            best = max(best, cur)
        elif s == "win":
            cur = 0
    return best


def block_bootstrap_yield(df: pd.DataFrame, block: str = "week_key", n: int = 2000,
                          seed: int = 0) -> tuple[float, float]:
    """95% interval for yield resampling whole weeks (props in a week are correlated)."""
    s = df[df.status.isin(["win", "loss", "push", "void"])]
    if s.empty:
        return (np.nan, np.nan)
    g = s.groupby(block).agg(p=("profit_units", "sum"), k=("stake_units", "sum"))
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(g), size=(n, len(g)))
    p, k = g.p.to_numpy()[idx].sum(1), g.k.to_numpy()[idx].sum(1)
    y = p / np.where(k == 0, np.nan, k)
    return float(np.nanpercentile(y, 2.5)), float(np.nanpercentile(y, 97.5))


def report(settled: pd.DataFrame, n_eligible_forecasts: int, bank_summary: dict | None = None
           ) -> dict:
    """Headline money metrics with explicit denominators. Empty input -> ROI unavailable."""
    if settled is None or settled.empty:
        return {"state": "ROI unavailable", "reason": "no bets with real recorded prices",
                "eligible_forecasts": n_eligible_forecasts, "bets": 0}
    s = settled.sort_values("decision_at")
    counts = s.status.value_counts().to_dict()
    done = s[s.status.isin(["win", "loss", "push", "void"])]
    staked = done.stake_units.sum()
    profit = done.profit_units.sum()
    dd_u, dd_f = drawdown(done.profit_units.to_numpy())
    lo, hi = block_bootstrap_yield(s)
    out = {
        "state": "ok", "bets": len(s), "counts": counts,
        "denominators": {"eligible_forecasts": n_eligible_forecasts,
                         "forecasts_with_eligible_quote": int(s[["game_id", "player_id",
                                                                 "market"]].drop_duplicates()
                                                              .shape[0]),
                         "settled_bets": len(done)},
        "avg_decimal_odds": float(s.decimal_price.mean()),
        "staked_units": float(staked), "profit_units": float(profit),
        "yield": float(profit / staked) if staked else None,
        "yield_95ci_week_blocks": [lo, hi],
        "max_drawdown_units": dd_u, "max_drawdown_frac_of_peak_cum": dd_f,
        "longest_losing_run": longest_losing_run(s.status.tolist()),
        "by_week": done.groupby("week_key").profit_units.sum().round(3).to_dict(),
        "by_market": done.groupby("market").profit_units.sum().round(3).to_dict(),
        "exposure_by_player": s.groupby("player_id").stake_units.sum().nlargest(10).to_dict(),
        "exposure_by_game": s.groupby("game_id").stake_units.sum().nlargest(10).to_dict(),
        "odds_coverage": (s[["game_id", "player_id", "market"]].drop_duplicates().shape[0]
                          / n_eligible_forecasts) if n_eligible_forecasts else None,
    }
    if bank_summary:
        out["bankroll"] = {k: v for k, v in bank_summary.items() if k != "curve"}
        st = bank_summary["start"]
        out["bankroll"]["return"] = (bank_summary["end"] - st) / st if st else None
    return out


def settle_frame(bets: pd.DataFrame, pg: pd.DataFrame) -> pd.DataFrame:
    """Attach outcomes and settle each selected bet under its bookmaker's rule profile."""
    o = pg.set_index(["game_id", "player_id"])
    from .config import MARKETS

    out = []
    for b in bets.itertuples():
        key = (b.game_id, b.player_id)
        part = o["participation"].get(key, "absent") if key in o.index else "absent"
        stat = MARKETS[b.market].stat
        val = o[stat].get(key) if key in o.index else None
        r = settle(b.selection, b.line, b.decimal_price, b.stake_units, part, val,
                   rule_for(b.bookmaker))
        out.append({**b._asdict(), "participation": part, "actual": val, **{
            "status": r["status"], "profit_units": r["profit"], "rule": rule_for(b.bookmaker).name}})
    return pd.DataFrame(out).drop(columns=["Index"], errors="ignore")
