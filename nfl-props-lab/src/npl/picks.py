"""Paper tracking for both sports against the stored US lines. RESEARCH ONLY.

History: the 6-15 and 10-15 point pick rules (D-028, D-030) were tested on a full season of real
NBA lines (n10) and lost about 2% at the best US price; the NFL rule lost on 2023's lines (D-041).
They are retired (D-043). What remains: the frozen market-anchored tracking list (NBA) and the
simple-baseline list, both recorded to build an honest record, neither presented as a bet. The
text below describes the retired rules for reference.

A prop is a pick when the model's side is 6 to 15 points more likely than the market says
(margin removed); for the NBA 10 to 15 points, with 6-10 point gaps listed as leans (D-030).
Disagreements of 15 points or more are left out: on the first two NBA nights the model's side
won about as often as the market implied there (51%, not the 71% claimed), 6-10 point gaps won
51% (market 50%), while 10-15 point gaps won 63%, close to what the model said. Every pick carries an estimate
that trusts the model's gap by a third (market + gap / 3) and the lowest price worth taking
(1 / estimate). Lines are a research reference: US books, not open to UK customers.

NBA rules: plays >= 85%, no injury flag, no teammate newly ruled out, no starter-level
teammate (26+ minutes or 15+ points a game this season) listed Out or Doubtful who has missed
no more than 2 of the team's games (the model's minutes and shots do not reflect his absence
yet; D-032), >= 3 games this season for his team, >= 3 books, half-point line, and the gap still >= 6 points with this season's
minutes in place of the model's. Strict list adds: not on a new team, season average >= 10%
beyond the line on the model's side, the model's side in >= 7 of the last 10 games.
NFL rules: plays >= 85%, no injury flag, >= 3 books, half-point line, the headline model and
both of its parts (joint simulation, gradient boosting) on the same side of the market and the
recent-average baseline on that side too; the gap uses the least confident of the three.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

GAP_MIN, GAP_MAX = 0.06, 0.15
NBA_GAP_MIN = 0.10  # D-030: NBA picks need 10-15 points; 6-10 point gaps are leans
SHRINK = 1 / 3


def estimate(model_low: float, market: float) -> float:
    return market + (model_low - market) * SHRINK


def _finish(rows: list[dict]) -> list[dict]:
    for r in rows:
        r["estimate"] = round(estimate(r["model_low"], r["market_p"]), 4)
        r["min_price"] = round(1 / r["estimate"], 2)
        r["gap"] = round(r["model_low"] - r["market_p"], 4)
    return sorted(rows, key=lambda r: -r["gap"])


def _hits(values: np.ndarray, line: float, side: str) -> tuple[int, int]:
    return int(((values > line) if side == "over" else (values < line)).sum()), len(values)


def stars_out(cards: list[dict], cur: pd.DataFrame, max_missed: int = 2) -> dict[str, list[str]]:
    """Teams with a starter-level player (26+ minutes or 15+ points a game this season) listed Out
    or Doubtful who has missed no more than `max_missed` of the team's games this season: his
    teammates' recent games were mostly played with him, so their forecasts miss his absence."""
    avg = cur.groupby("player_id").agg(m=("minutes", "mean"), p=("pts", "mean"),
                                       gp=("game_id", "nunique"))
    team_games = cur.groupby("team").game_id.nunique()
    out: dict[str, list[str]] = {}
    for c in cards:
        inj = c.get("injury")
        if not (isinstance(inj, dict) and inj.get("status") in ("Out", "Doubtful")):
            continue
        if c["player_id"] not in avg.index or c["name"] in out.get(c["team"], []):
            continue
        a = avg.loc[c["player_id"]]
        if (a.m >= 26 or a.p >= 15) and team_games.get(c["team"], 0) - a.gp <= max_missed:
            out.setdefault(c["team"], []).append(c["name"])
    return out


TRACK_EV = 0.02  # D-043: frozen paper-tracking threshold
TRACK_RULE = "nba-anchored-ev2-mgm@D044"  # rule ids on the paper record (track.py)
TRACK_MODEL = "nba-0.3"  # the rule is recorded only from editions made by this model
SIMPLE_RULE = "nba-simple-gap1@D038"
# D-049: the user stopped the simple rule on 5 Nov 2026 after two slates (165-169, -6.3% ROI,
# closing-line value -4.6% on 224 bets). No new flags are made or recorded; its settled history
# stays on the paper record, and the Simple baseline still shows on cards as a reference.
SIMPLE_STOPPED = "5 Nov 2026 (D-049)"
# Both frozen rules keep exactly the population they were frozen on: these five markets. The
# two-stat combos added on 4 Nov 2026 (pr, pa, ra) never enter either rule (calib.anchored has no
# coefficients for them, and nothing about them has been tested against prices).
TRACKED_MARKETS = ("pts", "reb", "ast", "fg3m", "pra")


def nba() -> dict:
    """Paper-tracking list for the latest NBA edition and the newest stored lines (D-043, D-044).

    The 6-15 / 10-15 point rules are retired: on a full season of real lines they lost (D-041).
    Each eligible prop (plays >= 85%, no injury flag, >= 3 books, half-point line, not started)
    gets the frozen market-anchored probability (calib.anchored: about 95% the market's
    margin-free price, nudged by the model in rebounds, assists and threes) and is tracked when
    the better side's expected value at BetMGM's price is at least 2%. BetMGM only (D-044): the
    fallback to another book's best price lost in both seasons at regulated prices (-8.5% and
    -9.3%), and BetMGM is priced only where its own main line is the consensus line. Tracking
    only: no edge has been shown, and these are US prices a UK customer cannot take.
    """
    from .dist import Dist
    from .nba import appdata as A
    from .nba import calib

    P = A.props()
    if "error" in P:
        return {"error": P["error"]}
    now = pd.Timestamp.now(tz="UTC")
    ver = P["model_version"]  # the stack fitted for the model that made this edition
    rows = []
    for c in P["cards"]:
        if c["market"] not in TRACKED_MARKETS:
            continue  # the frozen rule's markets only (combos are not part of it)
        m = c.get("market_line")
        if (not m or c["p_play"] < 0.85 or c["injury"] or m["books"] < 3
                or float(m["line"]).is_integer() or pd.Timestamp(c["tip_utc"]) <= now):
            continue
        raw = Dist.from_json(c["dist"]).line(m["line"])["over"]
        pa = calib.anchored(c["market"], m["fair_over"], raw, ver)
        pc = calib.calibrated(c["market"], raw, ver)
        if not (m.get("mgm_over") and m.get("mgm_under")):
            continue  # D-044: BetMGM's own main line must be the consensus line
        if ver == TRACK_MODEL and c.get("line_known") is not True:
            continue  # the edition was made before this player's line was pulled: no line factor
        evs = {side: p * m[f"mgm_{side}"] - 1 for side, p in (("over", pa), ("under", 1 - pa))}
        side = max(evs, key=evs.get)
        ev, price, book = evs[side], m[f"mgm_{side}"], "betmgm"
        if ev < TRACK_EV:
            continue
        flip = side == "under"
        rows.append({"sport": "nba", "player": c["name"], "player_id": c["player_id"],
                     "team": c["team"], "opponent": c["opponent"], "game_id": c["game_id"],
                     "tip_utc": c["tip_utc"], "tip_london": c["tip_london"], "market": c["market"],
                     "line": m["line"], "side": side,
                     "p_anchor": round(1 - pa if flip else pa, 4), "p_model_cal": round(1 - pc if flip else pc, 4),
                     "p_rule": round(1 - pa if flip else pa, 4),
                     "market_p": round(1 - m["fair_over"] if flip else m["fair_over"], 4),
                     "price": price, "book": book, "arb": m.get("arb", False),
                     "ev": round(ev, 4), "books": m["books"], "line_seen": m["observed_at"]})
    return {"edition_id": P["edition_id"], "model_version": ver,
            "tracking": sorted(rows, key=lambda r: -r["ev"]),
            "rules": nba.__doc__}  # no simple_gap list: that rule was stopped (D-049)


SIMPLE_GAP = 1.0  # D-038: tracked, not picks


def simple_gap(cards: list[dict]) -> list[dict]:
    """Tracking list for the simple baseline: the side S favours when it beats the US line by at
    least SIMPLE_GAP (eligible props only). Not a pick list: on the first three nights this rule
    won about half its bets (n08). Stopped on 5 Nov 2026 (D-049): kept so the rule stays
    reproducible, but nothing calls it and nothing it returns is recorded."""
    out = []
    for c in cards:
        if c["market"] not in TRACKED_MARKETS:
            continue  # the frozen rule's markets only (combos are not part of it)
        m, s = c.get("market_line"), c.get("simple")
        if (not m or s is None or c["p_play"] < 0.85 or c["injury"] or m["books"] < 3
                or float(m["line"]).is_integer()):
            continue
        d = s - m["line"]
        if abs(d) < SIMPLE_GAP:
            continue
        side = "over" if d > 0 else "under"
        out.append({"player": c["name"], "player_id": c["player_id"], "team": c["team"],
                    "opponent": c["opponent"], "game_id": c["game_id"], "tip_utc": c["tip_utc"],
                    "market": c["market"], "line": m["line"], "side": side, "simple": s,
                    "gap_points": round(abs(d), 1),
                    "market_p": round(m["fair_over"] if side == "over" else 1 - m["fair_over"], 4),
                    "best_price": m.get(f"best_{side}"), "price": m.get(f"best_{side}"),
                    "book": m.get(f"best_{side}_book"), "line_seen": m["observed_at"]})
    return sorted(out, key=lambda r: -r["gap_points"])


def nfl(season: int | None = None, week: int | None = None, game_id: str | None = None) -> dict:
    """NFL picks are retired (D-043): on the 2023 season's real lines the headline model carried
    no information beyond the market once its overconfidence was removed, and every rule lost."""
    from . import appdata as AP

    e = AP.latest_edition(season, week)
    return {"edition_id": e["edition_id"], "tracking": [], "rules": nfl.__doc__,
            "error": "NFL picks retired (D-043): no rule beat the market on 2023's real lines."}


def table(res: dict) -> str:
    if res.get("error"):
        return res["error"]
    cols = ["player", "team", "opponent", "market", "side", "line", "p_anchor", "p_model_cal", "market_p",
            "price", "book", "ev", "simple", "gap_points"]
    out = []
    for key, title in (("tracking", "Paper tracking: market-anchored EV >= 2% (not bets)"),
                       ("simple_gap", "Simple baseline beats the line by 1+ (tracking only)")):
        if key not in res:
            continue
        d = pd.DataFrame(res[key])
        out.append(f"{title}: none" if d.empty else f"{title}:\n" + d[[c for c in cols if c in d]].to_string(index=False))
    return "\n\n".join(out) + ("\nRESEARCH ONLY: no rule has beaten the market on real past lines "
                                 "(D-041, D-043). Nothing here is a bet.")
