"""Profit simulation (M5): flat stakes on real historical prices, kept apart from accuracy.

Rules (DECISIONS.md, profit section):
- Singles only. Prices are Bet365 pre-closing from football-data, stamped at the documented
  collection deadline (D-012). No best-price shopping, no closing prices for an earlier
  decision, no prices derived from fair odds. Markets with no historical prices (O/U 1.5,
  3.5, BTTS) are reported as unavailable.
- A fixture flagged `kickoff_before_collection` is never staked on.
- Expected return per unit = p × O − 1. Win profit = stake × (O − 1); loss = −stake; void = 0.
- All bets of one decision batch are placed together: their total stake must fit in the
  bankroll available at that moment; if it doesn't, bets are dropped from the lowest expected
  return upwards and recorded as skipped. Returns come back before the next batch.
"""

import json
from dataclasses import asdict, dataclass

import numpy as np
import pandas as pd
from sqlalchemy import Engine, text

from footy.modeling.metrics import block_bootstrap_ci

MARKETS = {"1X2": {"H": "p_H", "D": "p_D", "A": "p_A"}, "OU_2.5": {"O": "p_over_2.5", "U": None}}
UNAVAILABLE = ["OU_1.5", "OU_3.5", "BTTS"]


@dataclass(frozen=True)
class Strategy:
    name: str
    min_edge: float = 0.0  # expected return per unit must exceed this
    min_prob: float = 0.0
    min_odds: float = 1.01
    max_odds: float = 1000.0
    one_per_fixture: bool = False  # keep only the highest expected return per fixture
    markets: tuple[str, ...] = ("1X2", "OU_2.5")


def load_prices(engine: Engine, match_ids: list[int]) -> pd.DataFrame:
    if not match_ids:
        return pd.DataFrame()
    ids = ",".join(str(int(i)) for i in match_ids)
    with engine.connect() as conn:
        df = pd.read_sql(
            text(
                "SELECT match_id, market_key, selection, phase, price, quote_time_utc "
                f"FROM fd_odds WHERE bookmaker = 'B365' AND match_id IN ({ids})"
            ),
            conn,
        )
        flagged = pd.read_sql(
            text(
                "SELECT m.id AS match_id FROM import_issues i "
                "JOIN raw_files r ON r.id = i.raw_file_id "
                "JOIN matches m ON m.last_raw_file_id = r.id AND m.source_row = i.source_row "
                f"WHERE i.code = 'kickoff_before_collection' AND m.id IN ({ids})"
            ),
            conn,
        )
    df.attrs["no_stake"] = set(flagged.match_id)
    return df


def candidates(ledger: pd.DataFrame, prices: pd.DataFrame) -> pd.DataFrame:
    """Every (fixture, market, selection) with a model probability and a pre-closing price."""
    pred = ledger[ledger.status == "predicted"].copy()
    pre = prices[prices.phase == "pre_closing"].set_index(["match_id", "market_key", "selection"])
    close = prices[prices.phase == "closing"].set_index(["match_id", "market_key", "selection"])
    rows = []
    for r in pred.itertuples(index=False):
        rd = r._asdict()
        hg, ag = rd["home_goals"], rd["away_goals"]
        for market, sels in MARKETS.items():
            for sel, col in sels.items():
                p = (
                    rd.get(col)
                    if col
                    else (1 - rd.get("p_over_2.5") if rd.get("p_over_2.5") is not None else None)
                )
                if p is None or (isinstance(p, float) and np.isnan(p)):
                    continue
                key = (rd["match_id"], market, sel)
                if key not in pre.index:
                    continue
                o = float(pre.loc[key, "price"])
                oc = float(close.loc[key, "price"]) if key in close.index else np.nan
                won = (
                    (sel == "H" and hg > ag)
                    or (sel == "D" and hg == ag)
                    or (sel == "A" and hg < ag)
                    if market == "1X2"
                    else (sel == "O") == (hg + ag > 2.5)
                )
                rows.append(
                    {
                        "match_id": rd["match_id"],
                        "league": rd.get("league"),
                        "season": rd["season"],
                        "date": rd["date"],
                        "batch": rd["batch_cutoff_utc"],
                        "home_team": rd["home_team"],
                        "away_team": rd["away_team"],
                        "market": market,
                        "selection": sel,
                        "p": float(p),
                        "odds": o,
                        "odds_close": oc,
                        "quote_time_utc": str(pre.loc[key, "quote_time_utc"]),
                        "prediction_time_utc": rd["batch_cutoff_utc"],
                        "ev": float(p) * o - 1,
                        "won": bool(won),
                        "source": "football-data",
                        "bookmaker": "B365",
                    }
                )
    out = pd.DataFrame(rows)
    out.attrs["no_stake"] = prices.attrs.get("no_stake", set())
    return out


def select(cands: pd.DataFrame, s: Strategy) -> pd.DataFrame:
    c = cands[
        cands.market.isin(s.markets)
        & (cands.ev > s.min_edge)
        & (cands.p >= s.min_prob)
        & (cands.odds >= s.min_odds)
        & (cands.odds <= s.max_odds)
        & ~cands.match_id.isin(cands.attrs.get("no_stake", set()))
    ]
    if s.one_per_fixture:
        c = c.sort_values("ev", ascending=False).drop_duplicates("match_id")
    return c.sort_values(["batch", "ev"], ascending=[True, False])


def simulate(bets: pd.DataFrame, bankroll_gbp: float = 1000.0, unit_gbp: float = 10.0) -> dict:
    """Flat 1-unit stakes; batch-level fund reservation as described in the module docstring."""
    bank = bankroll_gbp
    placed, skipped = [], 0
    for _, b in bets.groupby("batch", sort=True):
        affordable = int(bank // unit_gbp)
        take, drop = b.iloc[:affordable], b.iloc[affordable:]
        skipped += len(drop)
        pnl_units = np.where(take.won, take.odds - 1, -1.0)
        bank += float(pnl_units.sum() * unit_gbp)
        for (_, row), u in zip(take.iterrows(), pnl_units, strict=True):
            placed.append(
                {
                    **row.to_dict(),
                    "stake_units": 1.0,
                    "profit_units": float(u),
                    "bank_after_batch_gbp": bank,
                }
            )
    ledger = pd.DataFrame(placed)
    return {"ledger": ledger, "skipped_insufficient_funds": skipped, "final_bank_gbp": bank}


def report(sim: dict, bankroll_gbp: float = 1000.0, unit_gbp: float = 10.0) -> dict:
    led = sim["ledger"]
    if led.empty:
        return {"bets": 0}
    profit = led.profit_units.to_numpy()
    equity = np.cumsum(profit)
    peak = np.maximum.accumulate(np.concatenate([[0], equity]))[1:]
    dd_units = float((peak - equity).max())
    losing, longest = 0, 0
    for w in led.won:
        losing = 0 if w else losing + 1
        longest = max(longest, losing)
    lo, hi = block_bootstrap_ci(profit, led.batch.to_numpy())
    led = led.assign(month=pd.to_datetime(led.date).dt.strftime("%Y-%m"))
    monthly = led.groupby("month").profit_units.agg(["count", "sum"]).reset_index()
    seasonal = led.groupby("season").profit_units.agg(["count", "sum"]).reset_index()
    by_team = (
        pd.concat(
            [
                led[["home_team", "profit_units"]].rename(columns={"home_team": "team"}),
                led[["away_team", "profit_units"]].rename(columns={"away_team": "team"}),
            ]
        )
        .groupby("team")
        .profit_units.agg(["count", "sum"])
        .sort_values("count", ascending=False)
        .head(10)
    )
    clv = led[np.isfinite(led.odds_close)]
    n = len(led)
    return {
        "bets": n,
        "wins": int(led.won.sum()),
        "losses": int((~led.won).sum()),
        "voids": 0,
        "hit_rate": float(led.won.mean()),
        "hit_rate_denominator": n,
        "average_odds": float(led.odds.mean()),
        "break_even_rate": float((1 / led.odds).mean()),
        "staked_units": float(n),
        "returns_units": float((led.won * led.odds).sum()),
        "profit_units": float(profit.sum()),
        "yield": float(profit.sum() / n),
        "yield_ci95_per_bet": [lo, hi],
        "bankroll_start_gbp": bankroll_gbp,
        "unit_gbp": unit_gbp,
        "bankroll_end_gbp": sim["final_bank_gbp"],
        "bankroll_return": sim["final_bank_gbp"] / bankroll_gbp - 1,
        "max_drawdown_units": dd_units,
        "max_drawdown_gbp": dd_units * unit_gbp,
        "max_drawdown_pct_of_start": dd_units * unit_gbp / bankroll_gbp,
        "longest_losing_run": longest,
        "skipped_insufficient_funds": sim["skipped_insufficient_funds"],
        "fixtures_bet": int(led.match_id.nunique()),
        "max_bets_per_fixture": int(led.groupby("match_id").size().max()),
        "monthly": monthly.to_dict(orient="records"),
        "seasonal": seasonal.to_dict(orient="records"),
        "by_league": led.groupby("league")
        .profit_units.agg(["count", "sum"])
        .reset_index()
        .to_dict(orient="records"),
        "top_teams_by_exposure": by_team.reset_index().to_dict(orient="records"),
        "equity_curve_units": [float(x) for x in equity],
        "closing_line_value_mean": float((clv.odds / clv.odds_close - 1).mean())
        if len(clv)
        else None,
        "closing_line_value_n": int(len(clv)),
    }


def coverage(ledger: pd.DataFrame, cands: pd.DataFrame) -> dict:
    pred = ledger[ledger.status == "predicted"]
    out = {"eligible_fixtures": int(len(pred))}
    for market in MARKETS:
        have = cands[cands.market == market].match_id.nunique() if len(cands) else 0
        out[market] = {
            "fixtures_with_price": int(have),
            "fixtures_without_price": int(len(pred) - have),
        }
    for m in UNAVAILABLE:
        out[m] = "ROI unavailable: no historical prices for this market (forward capture only)"
    return out


def strategy_dict(s: Strategy) -> dict:
    return asdict(s)


def dumps(x) -> str:
    return json.dumps(x, indent=1, default=lambda o: o.item() if hasattr(o, "item") else str(o))
