"""Paper-tracking record (D-044). RESEARCH ONLY: the record exists to judge the tracking rules
honestly, not to place bets.

Every prop a rule flags is recorded once, the first time it is flagged, in the SQLite table
tracked_props: later runs on the same slate do not add it again, and its line and price stay
as first seen (that is when a bet would have been placed). `report()` settles each one from
the box scores (a player who did not play is void; whole-number lines are never tracked, but a
push is graded as one if it happens; a missing stat leaves the prop open) and scores it two
ways:

  profit  flat 1 unit at the recorded price; ROI with a 95% interval that resamples whole
          slates, because props on the same night are correlated
  CLV     closing-line value: the recorded price x the margin-free consensus probability of
          the same side at the same line in the last stored snapshot before tip-off, minus 1.
          Missing when no later snapshot was stored, or fewer than two books still quote that
          line then. The snapshot's lead time is shown: a "close" taken hours before tip-off
          is only a later price, not the closing one.

Why CLV first: at about evens, a true 2% edge needs roughly 14,000 independent bets (more with
same-night correlation) before profit alone shows it with 80% power, while closing-line value
varies far less from bet to bet and a real 1-2% shows within a few hundred bets
(reports/AUDIT_2026-11.md). Both are reported; neither is a promise of anything.
"""

from __future__ import annotations

import sqlite3

import numpy as np
import pandas as pd

from .config import NEW_YORK

COLS = ["rule", "sport", "edition_id", "flagged_at", "game_id", "player_id", "player", "team",
        "opponent", "market", "line", "side", "price", "book", "p_rule", "market_p", "ev",
        "tip_utc", "line_seen"]


def record(con: sqlite3.Connection, rows: list[dict], *, rule: str, sport: str, edition_id: str,
           flagged_at: str) -> int:
    """Append flagged props; a prop already on the record for this rule is left as it was."""
    n = 0
    with con:
        for r in rows:
            if r.get("player_id") is None or r.get("price") is None:
                continue
            v = {"rule": rule, "sport": sport, "edition_id": edition_id, "flagged_at": flagged_at,
                 **{k: r.get(k) for k in COLS[4:]}}
            cur = con.execute(f"INSERT OR IGNORE INTO tracked_props({', '.join(COLS)}) VALUES "
                              f"({', '.join('?' * len(COLS))})", [v[k] for k in COLS])
            n += cur.rowcount
    return n


def load(con: sqlite3.Connection, rule: str | None = None) -> pd.DataFrame:
    q = "SELECT * FROM tracked_props" + (" WHERE rule = ?" if rule else "") + " ORDER BY id"
    return pd.read_sql_query(q, con, params=(rule,) if rule else ())


def settle(t: pd.DataFrame, pg: pd.DataFrame) -> pd.DataFrame:
    """status win / loss / void / open and the actual value, from canonical box scores."""
    out = t.copy()
    out["actual"] = np.nan
    out["status"] = "open"
    if out.empty:
        return out
    b = pg.assign(pra=pg.pts + pg.reb + pg.ast)
    have = set(b.game_id.astype(str))
    key = b.assign(game_id=b.game_id.astype(str), player_id=b.player_id.astype(str)).set_index(
        ["game_id", "player_id"])
    key = key[~key.index.duplicated(keep="last")]
    for i, r in out.iterrows():
        g, p = str(r.game_id), str(r.player_id)
        if g not in have:
            continue
        if (g, p) not in key.index or key.loc[(g, p), "participation"] != "played":
            out.at[i, "status"] = "void"
            continue
        a = key.loc[(g, p), r.market]
        if pd.isna(a):
            continue  # played but the stat is missing: left open, never graded
        a = float(a)
        out.at[i, "actual"] = a
        out.at[i, "status"] = ("push" if a == r.line else
                               "win" if (a > r.line) == (r.side == "over") else "loss")
    return out


def _fair_at(x: pd.DataFrame, line: float) -> tuple[float | None, int]:
    """Mean margin-free over probability across books quoting both sides of `line`."""
    y = x[x.line == line].pivot_table(index="bookmaker", columns="selection",
                                      values="decimal_price", aggfunc="last")
    if not {"over", "under"} <= set(y.columns):
        return None, 0
    y = y.dropna()
    if len(y) < 2:
        return None, len(y)
    return float(((1 / y.over) / (1 / y.over + 1 / y.under)).mean()), len(y)


def clv(t: pd.DataFrame, q: pd.DataFrame) -> pd.DataFrame:
    """Closing-line value of each tracked prop from the stored live quotes."""
    out = t.copy()
    for c in ("close_at", "close_lead_min", "close_books", "fair_close", "clv"):
        out[c] = np.nan if c != "close_at" else None
    if out.empty or q.empty:
        return out
    q = q[q.player_id.notna()].assign(obs=pd.to_datetime(q.observed_at, utc=True, format="ISO8601"))
    for i, r in out.iterrows():
        tip = pd.Timestamp(r.tip_utc)
        tip = tip.tz_localize("UTC") if tip.tzinfo is None else tip
        seen = pd.Timestamp(r.line_seen) if r.line_seen else None
        seen = seen.tz_localize("UTC") if seen is not None and seen.tzinfo is None else seen
        x = q[(q.game_id.astype(str) == str(r.game_id)) & (q.player_id.astype(str) == str(r.player_id))
              & (q.market == r.market) & (q.obs < tip)]
        if x.empty:
            continue
        last = x.obs.max()
        if seen is not None and last <= seen:
            continue  # nothing stored after the flag
        fair, nb = _fair_at(x[x.obs == last], float(r.line))
        out.at[i, "close_at"] = last.isoformat()
        out.at[i, "close_lead_min"] = round((tip - last).total_seconds() / 60, 1)
        out.at[i, "close_books"] = nb
        if fair is None:
            continue
        p = fair if r.side == "over" else 1 - fair
        out.at[i, "fair_close"] = round(p, 4)
        out.at[i, "clv"] = round(float(r.price) * p - 1, 4)
    return out


def _boot(v: np.ndarray, blocks: np.ndarray, n: int = 2000, seed: int = 0) -> tuple[float, float]:
    """95% interval for a mean, resampling whole slates."""
    if len(v) == 0:
        return (np.nan, np.nan)
    u, inv = np.unique(blocks, return_inverse=True)
    s, c = np.bincount(inv, v), np.bincount(inv)
    idx = np.random.default_rng(seed).integers(0, len(u), size=(n, len(u)))
    m = s[idx].sum(1) / c[idx].sum(1)
    return float(np.percentile(m, 2.5)), float(np.percentile(m, 97.5))


def summary(t: pd.DataFrame) -> dict:
    if t.empty:
        return {"flagged": 0}
    slate = pd.to_datetime(t.tip_utc, utc=True).dt.tz_convert(NEW_YORK).dt.date.astype(str)
    done = t.status.isin(["win", "loss"])
    pnl = np.where(t.status == "win", t.price - 1, -1.0)[done.to_numpy()]
    out = {"flagged": len(t), "settled": int(done.sum()), "won": int((t.status == "win").sum()),
           "lost": int((t.status == "loss").sum()),
           "void": int(t.status.isin(["void", "push"]).sum()),
           "open": int((t.status == "open").sum()), "slates": int(slate.nunique())}
    if done.any():
        lo, hi = _boot(pnl, slate[done].to_numpy())
        out |= {"profit_units": round(float(pnl.sum()), 2), "roi": round(float(pnl.mean()), 4),
                "roi_ci95": [round(lo, 4), round(hi, 4)],
                "expected_wins": round(float(t.loc[done, "market_p"].sum()), 1)}
    has = t.clv.notna()
    if has.any():
        lo, hi = _boot(t.loc[has, "clv"].to_numpy(float), slate[has].to_numpy())
        out |= {"clv_bets": int(has.sum()), "clv_mean": round(float(t.loc[has, "clv"].mean()), 4),
                "clv_ci95": [round(lo, 4), round(hi, 4)],
                "clv_positive_share": round(float((t.loc[has, "clv"] > 0).mean()), 3),
                "close_lead_min_median": float(t.loc[has, "close_lead_min"].median())}
    return out


def report(con: sqlite3.Connection, pg: pd.DataFrame | None = None, q: pd.DataFrame | None = None
           ) -> dict:
    from .nba import canon
    from .sources import odds_api

    t = load(con)
    if t.empty:
        return {"rules": {}, "rows": []}
    pg = canon.load()["player_games"] if pg is None else pg
    q = odds_api.quotes_frame(con) if q is None else q
    t = clv(settle(t, pg), q)
    return {"rules": {r: summary(x) for r, x in t.groupby("rule", sort=False)},
            "rows": t.to_dict("records")}


def table(rep: dict, last: int = 30) -> str:
    if not rep["rules"]:
        return "Paper record: nothing tracked yet."
    out = []
    for rule, s in rep["rules"].items():
        line = (f"{rule}: {s['flagged']} tracked, {s.get('won', 0)}-{s.get('lost', 0)} "
                f"({s.get('void', 0)} void, {s.get('open', 0)} open) over {s.get('slates', 0)} slates")
        if "roi" in s:
            line += (f"; profit {s['profit_units']:+.2f} units, ROI {s['roi']:+.1%} "
                     f"[{s['roi_ci95'][0]:+.1%}, {s['roi_ci95'][1]:+.1%}]; wins {s['won']} v "
                     f"{s['expected_wins']} the market expected")
        if "clv_mean" in s:
            line += (f"; CLV {s['clv_mean']:+.2%} [{s['clv_ci95'][0]:+.2%}, {s['clv_ci95'][1]:+.2%}]"
                     f" on {s['clv_bets']} bets, close a median {s['close_lead_min_median']:.0f} min"
                     " before tip")
        out.append(line)
    d = pd.DataFrame(rep["rows"]).tail(last)
    cols = ["rule", "player", "market", "side", "line", "price", "book", "status", "actual",
            "fair_close", "clv"]
    out.append(d[cols].to_string(index=False))
    out.append("RESEARCH ONLY: US prices, not open to UK customers. Profit needs thousands of bets "
               "to mean anything; closing-line value is the earlier test.")
    return "\n".join(out)
