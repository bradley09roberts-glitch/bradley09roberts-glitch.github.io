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
"""

from __future__ import annotations

import pandas as pd

OFFSHORE = frozenset({"betonlineag", "betus", "bovada", "lowvig", "mybookieag", "betanysports",
                      "fliff", "rebet"})


def regulated(book: str) -> bool:
    return book not in OFFSHORE


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
