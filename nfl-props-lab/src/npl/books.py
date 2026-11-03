"""Bookmaker prices: which are takeable, and the consensus line and fair price (audit, D-044).

The provider's "us" region mixes state-regulated sportsbooks with offshore and sweepstakes
books. Offshore books' margin-free prices are informative (BetOnline's are among the most
accurate), so they stay in the fair price, but a "best price" is only taken from a regulated
book: offshore quotes, ladder lines and price pairs that imply an arbitrage inflated earlier
research returns (audit 2026-11). None of these books is open to UK customers.

Consensus, per player and market, from one snapshot (stage-2 audit; reproduces the earlier
rows exactly wherever the two definitions coincide):
  1. exact duplicate rows are dropped; a book quoting two prices for the same line and side
     is left out of that prop;
  2. only two-way prices count; a book's margin-free over chance is (1/over) / (1/over + 1/under);
  3. each book's main line is its most balanced line (closest to 50%, then lower margin, then
     the lower line). Books that hang a ladder of lines (BetRivers, Bovada) vote once;
  4. the consensus line is the most common main line, one vote per book; a tie goes to the
     line whose voters are more balanced, then to the lower line. (The old rule took the mode
     over quote rows, so ladders and duplicate rows outvoted other books and ties went low.)
  5. the fair price is the mean over every book with a two-way price at that line, offshore
     included (limiting it to main-line voters made log loss slightly worse in all three
     seasons tested, so it was not adopted);
  6. best prices come from regulated books' main lines at that line; BetMGM is priced only
     when its own main line is the consensus line; `arb` marks a pair of best prices that
     sum to less than 1 (an arbitrage, usually one stale price).

Boards (D-046): the stores keep each row once (quotes are unique on the provider's per
bookmaker-market last_update and the price; game_lines likewise, without the time we observed
them), so a later pull adds rows only for bookmaker-markets that changed. Which books a pull saw
is in the pull log (odds_pulls): every bookmaker-market of every stored response with its
last_update, and every market the pull asked for. pull_board rebuilds the board as of a
reference time from it: for each event and market, the latest pull at or before the reference
that asked for that market; each bookmaker-market in that pull, with the stored rows whose
quote_time equals its last_update. A book missing from that pull had withdrawn the market and is
left out; a book whose market did not change stays, however old its rows. The board's time is
the pull's observed_at, so a pull in which nothing changed still dates the board.
Rows of an event and market that no logged pull asked for (stored before the log existed and
not backfilled, or test rows) fall back to complete_board: for each (game, market, bookmaker),
that book's rows from its own latest observation at or before the reference, ignoring
observations more than 12 hours older than the reference (this cannot tell an unchanged book
from one that withdrew). The rules above then apply to the rebuilt board as to a single snapshot.
"""

from __future__ import annotations

import pandas as pd

OFFSHORE = frozenset({"betonlineag", "betus", "bovada", "lowvig", "mybookieag", "betanysports",
                      "fliff", "rebet"})
STALE_AFTER = pd.Timedelta(hours=12)  # a book's rows older than this before the reference drop out


def regulated(book: str) -> bool:
    return book not in OFFSHORE


def complete_board(q: pd.DataFrame, keys: tuple[str, ...] = ("game_id", "market"), ref=None,
                   max_age: pd.Timedelta = STALE_AFTER) -> pd.DataFrame:
    """The rows of the board as of a reference time, from a deduplicated store (see above): for
    each (keys..., bookmaker), that bookmaker's rows from its own latest `observed_at` at or
    before the reference, unless that observation is more than `max_age` older than the
    reference. ref: None = each `keys` group's own latest observation; a time = the same
    reference for every row. Rows with a missing key are left out."""
    if q.empty:
        return q
    k = list(keys)
    obs = pd.to_datetime(q.observed_at, utc=True, format="ISO8601")
    if ref is None:
        r = obs.groupby([q[c] for c in k]).transform("max")
    else:
        r = pd.Timestamp(ref)
        r = r.tz_localize("UTC") if r.tzinfo is None else r.tz_convert("UTC")
    ok = (obs <= r) & (obs >= r - max_age)
    x, o = q[ok], obs[ok]
    if x.empty:
        return x
    last = o.groupby([x[c] for c in k] + [x.bookmaker]).transform("max")
    return x[o == last]


def _ts(s: pd.Series) -> pd.Series:
    return pd.to_datetime(s, utc=True, format="ISO8601")


def pull_board(q: pd.DataFrame, pulls: pd.DataFrame | None,
               keys: tuple[str, ...] = ("game_id", "market"), ref=None,
               event: str = "provider_event_id") -> tuple[pd.DataFrame, pd.DataFrame]:
    """The board as of a reference time from the pull log (see above), and when it was seen.

    q: stored rows (quotes, or game_lines with event="event_id") carrying `event`, bookmaker,
    market, quote_time and observed_at; `keys` must include market. pulls: odds_pulls rows
    (event_id, market, bookmaker, last_update, observed_at, raw_id), or None. ref: None = each
    event and market's latest pull; a time = the latest pull at or before it (filter the pulls
    first for a strict cut-off such as tip-off). An event and market that no pull asked for
    falls back to complete_board.

    Returns (rows, times): the board's rows with `board_at` (the pull's observed_at; on the
    fallback the newest observation on the rebuilt board) and one row per `keys` group with its
    board_at, also for a group whose board is empty (every book withdrew). Where one game has
    several events, the one seen latest is kept."""
    k = list(keys)
    q = q.dropna(subset=k)
    none = pd.DataFrame(columns=k + ["board_at"])
    if q.empty:
        return q.assign(board_at=pd.Series(dtype=object)), none
    asked = pd.Series(False, index=q.index)
    p = None
    if pulls is not None and len(pulls) and event in q and "quote_time" in q:
        p = pulls[pulls.event_id.isin(set(q[event].dropna().astype(str)))]
        pairs = set(zip(p.event_id, p.market, strict=True))
        asked = pd.Series([(str(e), m) in pairs for e, m in zip(q[event], q.market, strict=True)],
                          index=q.index)
    rows, times = [], []
    if asked.any():
        b, t = _logged_board(q[asked], p, k, None if ref is None else _utc(ref), event)
        rows.append(b)
        times.append(t)
    if (~asked).any():
        c = complete_board(q[~asked], keys, ref)
        if len(c):
            o = _ts(c.observed_at)
            top = c.loc[o.groupby([c[x] for x in k]).idxmax().to_numpy(), k + ["observed_at"]]
            t = top.rename(columns={"observed_at": "board_at"})
            rows.append(c.merge(t, on=k))
            times.append(t)
    if not times:
        return q.iloc[:0].assign(board_at=pd.Series(dtype=object)), none
    t = pd.concat(times, ignore_index=True)
    t = t.assign(_t=_ts(t.board_at)).sort_values("_t", kind="stable").drop_duplicates(
        k, keep="last")[k + ["board_at"]].reset_index(drop=True)
    b = pd.concat(rows, ignore_index=True).merge(t, on=k + ["board_at"])
    return b, t


def _utc(ref) -> pd.Timestamp:
    r = pd.Timestamp(ref)
    return r.tz_localize("UTC") if r.tzinfo is None else r.tz_convert("UTC")


def _logged_board(q: pd.DataFrame, p: pd.DataFrame, k: list[str], ref, event: str
                  ) -> tuple[pd.DataFrame, pd.DataFrame]:
    """pull_board for rows whose event and market a logged pull asked for."""
    p = p.assign(_t=_ts(p.observed_at), event_id=p.event_id.astype(str))
    if ref is not None:
        p = p[p._t <= ref]
    # the latest pull per event and market: the newest observation, then the later response
    last = p.sort_values(["_t", "raw_id"], kind="stable").drop_duplicates(
        ["event_id", "market"], keep="last")[["event_id", "market", "raw_id", "observed_at", "_t"]]
    seen = p[p.bookmaker.notna()].merge(last[["event_id", "market", "raw_id"]],
                                        on=["event_id", "market", "raw_id"])
    seen = seen[["event_id", "market", "bookmaker", "last_update"]].drop_duplicates().merge(
        last[["event_id", "market", "observed_at", "_t"]], on=["event_id", "market"]).rename(
        columns={"event_id": event, "last_update": "quote_time", "observed_at": "board_at"})
    x = q.assign(**{event: q[event].astype(str)})
    b = x.merge(seen, on=[event, "market", "bookmaker", "quote_time"])
    b = b[_ts(b.observed_at) <= b._t].drop(columns="_t")  # stored by the time of that pull
    # each keys group's board time, from the chosen pull of its event (the latest of several)
    t = x[[event, *k]].drop_duplicates().merge(
        last.rename(columns={"event_id": event, "observed_at": "board_at"}), on=[event, "market"])
    t = t.sort_values("_t", kind="stable").drop_duplicates(k, keep="last")
    b = b.merge(t[[event, *k]], on=[event, *k])
    return b, t[k + ["board_at"]]


def pairs(q: pd.DataFrame, keys: tuple[str, ...] = ("player_id", "market")) -> pd.DataFrame:
    """Two-way prices per (prop, bookmaker, line) from one snapshot's quote rows."""
    p = list(keys) + ["bookmaker"]
    k = p + ["line", "selection"]
    r = q.assign(line=q.line.astype(float).round(1)).drop_duplicates(k + ["decimal_price"])
    clash = r.duplicated(k, keep=False)
    if clash.any():  # two prices for one line and side: the book is left out of that prop
        bad = r.loc[clash, p].drop_duplicates()
        r = r.merge(bad.assign(_bad=1), how="left", on=p)
        r = r[r._bad.isna()].drop(columns="_bad")
    w = r.pivot_table(index=k[:-1], columns="selection", values="decimal_price", aggfunc="first")
    if not {"over", "under"} <= set(w.columns):
        return pd.DataFrame(columns=k[:-1] + ["over", "under", "fair", "margin"])
    w = w[["over", "under"]].dropna().reset_index()
    w.columns.name = None
    w["fair"] = (1 / w.over) / (1 / w.over + 1 / w.under)
    w["margin"] = 1 / w.over + 1 / w.under - 1
    return w


def consensus_frame(w: pd.DataFrame, keys: tuple[str, ...] = ("player_id", "market")
                    ) -> pd.DataFrame:
    """Consensus line, fair price and best prices for every prop in `w` (rules above)."""
    k = list(keys)
    cols = k + ["line", "fair_over", "books", "margin", "mgm_line", "mgm_over", "mgm_under",
                "best_over", "best_over_book", "best_under", "best_under_book", "arb"]
    if w.empty:
        return pd.DataFrame(columns=cols)
    w = w.assign(bal=(w.fair - 0.5).abs())
    main = w.sort_values(k + ["bookmaker", "bal", "margin", "line"]).drop_duplicates(
        k + ["bookmaker"])
    v = main.groupby(k + ["line"]).agg(votes=("bookmaker", "size"), vbal=("bal", "mean"))
    v = v.reset_index().sort_values(k + ["votes", "vbal", "line"],
                                    ascending=[True] * len(k) + [False, True, True])
    cl = v.drop_duplicates(k)[k + ["line"]]
    pr = w.merge(cl, on=k + ["line"]).groupby(k).agg(
        line=("line", "first"), fair_over=("fair", "mean"), books=("fair", "size"),
        margin=("margin", "mean")).reset_index()
    reg = main.merge(cl, on=k + ["line"])
    reg = reg[reg.bookmaker.map(regulated)]
    for side in ("over", "under"):
        b = reg.sort_values(k + [side, "bookmaker"], ascending=[True] * len(k) + [False, True])
        b = b.drop_duplicates(k)[k + [side, "bookmaker"]]
        pr = pr.merge(b.rename(columns={side: f"best_{side}", "bookmaker": f"best_{side}_book"}),
                      on=k, how="left")
    mg = main[main.bookmaker == "betmgm"][k + ["line", "over", "under"]]
    pr = pr.merge(mg.rename(columns={"line": "mgm_line", "over": "_mo", "under": "_mu"}), on=k,
                  how="left")
    own = pr.mgm_line == pr.line
    pr["mgm_over"], pr["mgm_under"] = pr._mo.where(own), pr._mu.where(own)
    pr["arb"] = (1 / pr.best_over + 1 / pr.best_under).lt(1)
    pr["fair_over"], pr["margin"] = pr.fair_over.round(4), pr.margin.round(4)
    return pr[cols]


def _clean(v):
    if isinstance(v, float) and v != v:
        return None
    return v.item() if hasattr(v, "item") else v


def records(pr: pd.DataFrame, keys: tuple[str, ...] = ("player_id", "market")) -> dict:
    """{(player_id, market): consensus dict} with None for anything missing."""
    k = list(keys)
    out = {}
    for r in pr.to_dict("records"):
        d = {c: _clean(v) for c, v in r.items() if c not in k}
        d["books"], d["arb"] = int(d["books"]), bool(d["arb"])
        out[tuple(r[c] for c in k)] = d
    return out


def consensus(w: pd.DataFrame) -> dict | None:
    """The consensus for one prop's two-way prices."""
    r = records(consensus_frame(w.assign(player_id="_", market="_")))
    return r.get(("_", "_"))
