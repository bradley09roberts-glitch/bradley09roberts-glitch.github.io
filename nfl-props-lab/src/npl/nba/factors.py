"""Factor breakdown for NBA prop cards (D-047): four tabs of research context (matchup, form,
game script, role), each with a lean, a one-line headline built from our own numbers, its key
figures and how its rule did when tested. Context only: nothing here feeds a forecast, and
nothing is a bet or a recommendation.

Each lean is a rule written down before a backtest of 3 Nov 2026 (R2) looked at any outcome,
applied here exactly as tested, to data the app already holds (SportsDataverse box scores, the
play-by-play shares in npl.nba.extras and our stored game lines):

  form         his last 4 played games before tonight (any season or type): 3 or 4 above
               tonight's consensus line -> over; 3 or 4 below -> under; needs 4 games (rule T4)
  matchup      what tonight's opponent has given up a game this season (games on earlier dates)
               to all players of his position group (G, F, C) combined, in this stat, ranked
               across teams with a game (1 = most): top 10 -> over; bottom 10 -> under; no lean
               until the opponent has played 5 games (MU)
  game_script  his team is the underdog by 7 points or more -> under (GS). The backtest took the
               spread from SportsDataverse play-by-play (a near-close line); cards use the median
               spread of regulated books in our latest pre-game pull. His fourth-quarter share of
               points is shown as a figure only (its own rule, Q4, showed no effect either)
  role         a teammate who averaged 25+ minutes in 5+ games for the team this season missed
               the team's previous game and has not played for another team since -> over (RA);
               his minutes in his last 3 games this season at or below 0.8 x his season average
               (5+ games) -> under (RM). One of the two alone gives the lean; both or neither
               give none, as in the backtest's combination

A rule that cannot be applied (no line, too few games, no game lines) is "unknown"; the backtest
counted those as no lean, the same as "neutral". `overall` counts the leans; when 3 or more of
the 4 point the same way (the combination the backtest tested as a stand-in for another app's
top label) it says how that did. None of the rules made money at the best regulated price in
either season, and none measurably improved on the market's own forecast in the test season;
neither did three or more agreeing. Each factor also carries `tested`: one line for each season
of the backtest (fit 2024-25, test the 2025-26 sample) with its figures, the return counted on
the props with a regulated price. A factor's reliability label follows from its lean
(RELIABILITY) and the overall line ends with one of two fixed sentences (OVERALL_TESTED); the
props payload carries both tables once, beside `tested` (tests_payload), so the offline copy can
keep them once too (npl.snapshot).

Combo markets (pr, pa, ra, added 4 Nov 2026): the rules are applied to them the same way (the
matchup rule on the combo's own allowed totals), but none was ever tested on them, since we hold no
past bookmaker lines for those markets: their factors carry `tested` None and the reason as their
label (UNTESTED), and the overall line ends with UNTESTED_OVERALL (`untested`).

Cut-off (cutoffs): a card's box-score inputs count only games that started strictly before the
earlier of the edition's publication and his own game's start. An edition published after some
games had started (the 2 Nov 2026 final, 01:13 UTC, after 9 of 15) therefore never counts those
games, even once their box scores arrive. Season-to-date figures also use league games on dates
before the slate's US date only (no All-Star or other exhibition games), as npl.nba.extras does.
Displayed figures are rounded half up.
"""

from __future__ import annotations

from decimal import Decimal

import numpy as np
import pandas as pd

from . import extras as X

FACTORS = ("matchup", "form", "game_script", "role")
OVER, UNDER, NEUTRAL, UNKNOWN = "over", "under", "neutral", "unknown"
LEANS = (OVER, UNDER, NEUTRAL, UNKNOWN)
MARKETS = ("pts", "reb", "ast", "fg3m", "pra", "pr", "pa", "ra")
# the markets the rules were backtested on (R2); the combos were not (no past lines stored)
TESTED_MARKETS = ("pts", "reb", "ast", "fg3m", "pra")
COMBOS = ("pr", "pa", "ra")
GROUPS = ("G", "F", "C")

FORM_GAMES, FORM_SIDE = 4, 3  # T4: 3 or 4 of his last 4 on one side of tonight's line
TOP = 10  # MU: the 10 teams that give up most (over) or least (under)
MIN_GAMES = 5  # MU: opponent's games; RA: a regular's games for the team; RM: his games
UNDERDOG = 7.0  # GS: underdog by this many points or more
REGULAR_MIN = 25.0  # RA: a regular's minutes a game for the team this season
DROP = 0.8  # RM: last 3 games' minutes at or below this share of his season average
LAST = 3  # RM: games in his recent minutes
AGREE = 3  # factors pointing the same way for the tested combination (CB)

STAT_WORDS = {"pts": "points", "reb": "rebounds", "ast": "assists", "fg3m": "3-pointers",
              "pra": "PRA", "pr": "points + rebounds", "pa": "points + assists",
              "ra": "rebounds + assists"}
GROUP_WORDS = {"G": "guards", "F": "forwards", "C": "centres"}

# ------------------------------------------------------------------ backtest R2, 3 Nov 2026
# The rules above, frozen before any outcome was seen and not tuned, on the stored real lines
# (one snapshot about 3 h before tip): fit 2024-25, 71,922 props on 164 dates; test the 2025-26
# sample, 18,751 props on 42 dates (markets points, rebounds, assists, threes, PRA). "won" is
# the favoured side's hit rate on the test season, "market" the market's margin-free chance of
# that side; ROI flat 1 unit at the best regulated price with a 95% date-block interval (10,000
# resamples), on the props that had a regulated price ("roi_props", 94-99% of them; the others
# were priced only by books outside the regulated set, npl.books.OFFSHORE); "beyond the
# market" a logistic of the outcome on logit(fair) plus the
# factor, fitted on 2024-25 and scored on 2025-26 (log loss per prop); Holm over the 8 rules.
# Aggregates only, from the run's summary (r2_results.json); the per-prop frame holds prices and
# stays under data/ (git-ignored). DECISIONS D-047.
# Each entry: the test season's figures at the top level (props, won, market, roi_props, roi,
# roi_ci), the fit season's under "fit", and for a rule that always takes one side the share of
# all props on that side that won ("all", by season), which a "no effect" label is read against.
# the form label (T4, T5): each beat the fair chance in 2025-26 but not in 2024-25, and lost money
# at the best regulated price in both seasons
FORM_RELIABILITY = ("Beat the fair chance in one season, not the other; lost money at the best "
                    "regulated price in both. No edge.")
TESTS = {
    # Fit 44,754 props: won 51.3% v market 51.7% (base rate 50.1%), ROI -5.0% [-6.0, -3.9].
    # Test 11,731: 52.7% v 51.6% (+1.2 pts [+0.1, +2.2]), ROI -2.4% [-4.4, -0.4]. Fit
    # coefficient -0.022 [-0.045, +0.001]; test log loss +0.00019 [+0.00005, +0.00033] (worse);
    # Holm p 1.0.
    "T4": {"factor": "form", "verdict": "priced in", "props": 11731, "won": 0.527,
           "market": 0.516, "roi_props": 11392, "roi": -0.024, "roi_ci": [-0.044, -0.004],
           "fit": {"props": 44754, "won": 0.513, "market": 0.517, "roi_props": 44018,
                   "roi": -0.050, "roi_ci": [-0.060, -0.039]},
           "reliability": FORM_RELIABILITY},
    # 4 of last 5 (shown nowhere; the form tab uses T4). Fit 27,055: 52.0% v 52.3%, ROI -4.9%
    # [-6.1, -3.6]. Test 7,213: 53.9% v 52.1% (+1.8 pts [+0.5, +3.1]), ROI -1.2% [-3.7, +1.3];
    # log loss +0.00017 [+0.00006, +0.00027] (worse); Holm p 1.0. 2025-26 only: re-check on
    # 2026-27 data, not an edge.
    "T5": {"factor": "form", "verdict": "priced in", "props": 7213, "won": 0.539,
           "market": 0.521, "roi_props": 6986, "roi": -0.012, "roi_ci": [-0.037, 0.013],
           "fit": {"props": 27055, "won": 0.520, "market": 0.523, "roi_props": 26549,
                   "roi": -0.049, "roi_ci": [-0.061, -0.036]},
           "reliability": FORM_RELIABILITY},
    # Fit 44,986: 50.8% v 50.1% (+0.75 pts [+0.14, +1.36]), ROI -3.0% [-4.2, -1.8]. Test
    # 11,708: 50.4% v 50.1%, ROI -3.6% [-5.8, -1.4]. Fit coefficient +0.030 [+0.005, +0.056];
    # test log loss +0.00001 [-0.00020, +0.00023]; Holm p 1.0. Real effect on output (+0.06 SD
    # in both seasons), but the line already moves 57% (2024-25) and 73% (2025-26) of it.
    "MU": {"factor": "matchup", "verdict": "priced in", "props": 11708, "won": 0.504,
           "market": 0.501, "roi_props": 11370, "roi": -0.036, "roi_ci": [-0.058, -0.014],
           "fit": {"props": 44986, "won": 0.508, "market": 0.501, "roi_props": 44256,
                   "roi": -0.030, "roi_ci": [-0.042, -0.018]},
           "reliability": "Real effect, but mostly already in the line. No edge on 2 seasons "
                          "of real lines."},
    # Fit 14,240: under won 51.2% v 50.7% (all unders 51.2%), ROI -3.9% [-6.1, -1.4]. Test
    # 4,018: 51.0% v 50.7% (all unders 51.7%), ROI -4.1% [-8.4, +0.2]; log loss -0.00001
    # [-0.00004, +0.00002]; Holm p 1.0. No effect on output.
    "GS": {"factor": "game_script", "verdict": "no value", "props": 4018, "won": 0.510,
           "market": 0.507, "roi_props": 3867, "roi": -0.041, "roi_ci": [-0.084, 0.002],
           "fit": {"props": 14240, "won": 0.512, "market": 0.507, "roi_props": 13816,
                   "roi": -0.039, "roi_ci": [-0.061, -0.014], "all": 0.512},
           "all": 0.517, "side": "under",
           "reliability": "Tested on 2 seasons of real lines: no effect."},
    # Fit 42,432: 51.2% v 50.7% (all unders 51.2%), ROI -3.4% [-4.7, -2.2]. Test 10,398: 51.5% v
    # 50.7% (all unders 51.7%), ROI -3.2% [-5.9, -0.2]; log loss -0.00001 [-0.00005, +0.00003];
    # Holm p 1.0. No effect on output.
    "Q4": {"factor": "game_script", "verdict": "no value", "props": 10398, "won": 0.515,
           "market": 0.507, "roi_props": 10123, "roi": -0.032, "roi_ci": [-0.059, -0.002],
           "fit": {"props": 42432, "won": 0.512, "market": 0.507, "roi_props": 42014,
                   "roi": -0.034, "roi_ci": [-0.047, -0.022], "all": 0.512},
           "all": 0.517, "side": "under",
           "reliability": "Tested on 2 seasons of real lines: no effect."},
    # Fit 43,020 (60% of props; long absences keep counting): over won 48.9% v 49.3%, ROI -5.2%
    # [-6.6, -3.8]. Test 12,158: 48.0% v 49.3% (-1.4 pts [-2.8, +0.1]), ROI -6.8% [-9.5,
    # -3.9]; log loss +0.00004 [-0.00003, +0.00010] (worse); Holm p 1.0. Output does rise (+0.09
    # and +0.06 SD), but the line rises about 87% as much.
    "RA": {"factor": "role", "verdict": "priced in", "props": 12158, "won": 0.480,
           "market": 0.493, "roi_props": 11782, "roi": -0.068, "roi_ci": [-0.095, -0.039],
           "fit": {"props": 43020, "won": 0.489, "market": 0.493, "roi_props": 42077,
                   "roi": -0.052, "roi_ci": [-0.066, -0.038], "all": 0.488},
           "all": 0.483, "side": "over",
           "reliability": "Real effect, but bookmakers already move the line for it. No edge."},
    # Fit 3,187: under won 49.0% v 50.5%, ROI -7.8% [-13.1, -2.8]. Test 936: 50.0% v 50.7%, ROI
    # -5.9% [-16.7, +5.3]; log loss -0.00003 [-0.00026, +0.00019]; Holm p 1.0. Players tend to
    # bounce back; no effect on output.
    "RM": {"factor": "role", "verdict": "no value", "props": 936, "won": 0.500,
           "market": 0.507, "roi_props": 881, "roi": -0.059, "roi_ci": [-0.167, 0.053],
           "fit": {"props": 3187, "won": 0.490, "market": 0.505, "roi_props": 3129,
                   "roi": -0.078, "roi_ci": [-0.131, -0.028], "all": 0.512},
           "all": 0.517, "side": "under",
           "reliability": "Tested on 2 seasons of real lines: no effect."},
    # 3+ of T4, MU, GS and role pointing the same way. Fit 5,730 (8%): 52.4% v 51.4%, ROI -2.6%
    # [-5.3, +0.1]. Test 1,592: 51.2% v 51.4% (-0.2 pts [-2.1, +1.7]), ROI -4.1% [-7.9, -0.2];
    # fit coefficient +0.045 [-0.011, +0.101]; log loss +0.00002 [-0.00006, +0.00009]; Holm p
    # 1.0. PRA only, 2025-26: 343 props, 50.7% v 50.1%, ROI -1.9% [-10.7, +7.1]. All seven rules
    # in one model: test log loss +0.00015 [-0.00020, +0.00049], worse than the market alone.
    "CB": {"factor": "overall", "verdict": "priced in", "props": 1592, "won": 0.512,
           "market": 0.514, "roi_props": 1537, "roi": -0.041, "roi_ci": [-0.079, -0.002],
           "fit": {"props": 5730, "won": 0.524, "market": 0.514, "roi_props": 5603,
                   "roi": -0.026, "roi_ci": [-0.053, 0.001]},
           "reliability": "Factors agreeing is not an edge: tested on 2 seasons of real "
                          "lines."},
}
RULES = {"T4": "3 or 4 of his last 4 games on one side of tonight's line",
         "T5": "4 or 5 of his last 5 games on one side of tonight's line",
         "MU": "opponent in the top 10 (over) or bottom 10 (under) for what it gives up to his "
               "position group, once it has played 5 games",
         "GS": "his team the underdog by 7 points or more (under)",
         "Q4": "his share of points in fourth quarters below the league's (under)",
         "RA": "a 25-minute regular missed the team's last game (over)",
         "RM": "his last 3 games' minutes 20% or more below his season average (under)",
         "CB": "3 or more of the four factors pointing the same way"}
# the role tab when neither of its two rules decides it (both labels above, in one line)
ROLE_RELIABILITY = ("Tested on 2 seasons of real lines: an absent teammate is already in the "
                    "line and a minutes drop shows no effect. No edge.")
# {factor: {lean: its reliability label}}: the label each factor carries (the role tab's depends
# on which of its rules set the lean). The props payload keeps the table once (tests_payload).
RELIABILITY = {
    "matchup": dict.fromkeys(LEANS, TESTS["MU"]["reliability"]),
    "form": dict.fromkeys(LEANS, TESTS["T4"]["reliability"]),
    "game_script": dict.fromkeys(LEANS, TESTS["GS"]["reliability"]),
    "role": {OVER: TESTS["RA"]["reliability"], UNDER: TESTS["RM"]["reliability"],
             NEUTRAL: ROLE_RELIABILITY, UNKNOWN: ROLE_RELIABILITY},
}
# what the tests showed, in the words the cards use: no rule made money at the best regulated
# price in either season (16 of 16 returns below zero) and none lowered the test season's log
# loss beyond chance when added to the market's own margin-free chance (Holm p 1.0 for each)
NO_EDGE = ("Tested: none of these rules made money at the best regulated price in either season, "
           "or measurably improved on the market's own forecast in the test season.")
SUMMARY = ("Tested on 2 seasons of stored real lines: none of these rules made money at the best "
           "regulated price in either season, and none measurably improved on the market's own "
           "forecast in the test season; neither did three or more agreeing. Recent form beat "
           "the fair chance in one season, not the other; the opponent's figures and absent "
           "teammates are real effects the line already carries; blow-out risk, fourth-quarter "
           "share and minutes drops showed no effect.")
NOTES = {
    "form": "His last 4 games before tonight, oldest first, against tonight's consensus line (a "
            "game counts only if it started before this edition was published).",
    "matchup": "What tonight's opponent has given up a game this season to all players of his "
               "position group combined. Places are counted from the most allowed, or from the "
               "fewest when the lean is under.",
    "game_script": "Spread: his team's, the median of regulated US books in our latest pre-game "
                   "pull (+ = underdog). Fourth-quarter share: his regular-season points in "
                   "fourth quarters this season, v the league's.",
    "role": "Regulars: teammates averaging 25+ minutes in 5+ games for the team this season. "
            "Minutes: his last 3 games this season v his season average.",
}


def tests_payload() -> dict:
    """The props payload's `factor_tests` block: each rule's figures (test season at the top
    level, fit season under "fit") and label, and what every card's factors repeat, kept here
    once (the offline copy keeps only this copy, npl.snapshot): `tested`, the one-line results
    per factor; `reliability`, each factor's label by lean; `overall`, the overall line's
    closing sentence when 3 or more factors agree ("agree") and otherwise ("other")."""
    return {"date": "2026-11-03", "fit": "2024-25: 71,922 props on 164 dates",
            "test": "2025-26 sample: 18,751 props on 42 dates", "summary": SUMMARY,
            "notes": NOTES,
            "rules": {k: {**v, "rule": RULES[k]} for k, v in TESTS.items()},
            "tested": {k: dict(v) for k, v in TESTED.items()},
            "reliability": {k: dict(v) for k, v in RELIABILITY.items()},
            "overall": dict(OVERALL_TESTED),
            # what combo cards carry instead of test figures (kept once here, as above)
            "untested": {"markets": list(COMBOS), "reliability": UNTESTED,
                         "overall": UNTESTED_OVERALL}}


# ------------------------------------------------------------------ formatting
def _num(x: float) -> str:
    """A figure as written on the card: half up to 2 decimals, no trailing zeros."""
    s = f"{X.half_up(x, 2):.2f}".rstrip("0").rstrip(".")
    return "0" if s in ("", "-0") else s


def _one(x: float) -> float:
    return X.half_up(x, 1)


def _signed(x: float) -> str:
    s = _num(x)
    return s if s.startswith("-") or s == "0" else f"+{s}"


def ordinal(n: int) -> str:
    n = int(n)
    suffix = "th" if 10 <= n % 100 <= 20 else {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return f"{n}{suffix}"


def _games(n: int) -> str:
    return f"{n} game" if n == 1 else f"{n} games"


def _names(names: list[str]) -> str:
    return names[0] if len(names) == 1 else ", ".join(names[:-1]) + " and " + names[-1]


def _pc(x: float, sign: bool = False) -> str:
    """A share as a percentage to 1 decimal, half up as written (0.5155 -> 51.6%), with a minus
    sign (−) below zero and, when `sign`, a plus above it."""
    v = X.half_up(float(Decimal(repr(float(x))) * 100), 1)
    return ("−" if v < 0 else "+" if sign and v > 0 else "") + f"{abs(v):.1f}%"


# ------------------------------------------------------------------ tested: both seasons
SEASON_NAMES = {"fit": "2024-25", "test": "2025-26 sample"}


def _season(rule: str, part: str) -> dict:
    return TESTS[rule]["fit"] if part == "fit" else TESTS[rule]


def _ret(s: dict, first: bool = True) -> str:
    """The flat return and its 95% interval, on the props that had a regulated price (the
    others, priced only by books outside the regulated set, are not counted); `first` names
    what the count and the interval are, a later return in the same line says it shorter."""
    lo, hi = s["roi_ci"]
    n = f"{s['roi_props']:,}"
    return (f"return {_pc(s['roi'], True)} on the {n} with a regulated price (95% interval "
            f"{_pc(lo, True)} to {_pc(hi, True)})" if first else
            f"return {_pc(s['roi'], True)} on the {n} with one ({_pc(lo, True)} to "
            f"{_pc(hi, True)})")


def _rule_line(rule: str, part: str) -> str:
    """One season of one rule in a line: props, how often the side it pointed to won against
    the fair (margin-free) chance, with all props on that side for a one-sided rule, and the
    flat return at the best regulated price, on the props that had one, with its 95%
    interval."""
    s, side = _season(rule, part), TESTS[rule].get("side")
    who = f"the {side}" if side else "the side it pointed to"
    base = f" (all {side}s {_pc(s['all'])})" if side and "all" in s else ""
    return (f"{SEASON_NAMES[part]}: on {s['props']:,} props {who} won {_pc(s['won'])} v a fair "
            f"chance of {_pc(s['market'])}{base}; {_ret(s)}, at the best one.")


def _role_line(part: str) -> str:
    """One season of the role tab's two rules (RA over, RM under) in a line."""
    a, m = _season("RA", part), _season("RM", part)
    return (f"{SEASON_NAMES[part]}: with a regular out, the over won {_pc(a['won'])} v a fair "
            f"chance of {_pc(a['market'])} on {a['props']:,} props, {_ret(a)}; with his minutes "
            f"down, the under won {_pc(m['won'])} v {_pc(m['market'])} on {m['props']:,}, "
            f"{_ret(m, first=False)}; both at the best regulated price.")


# {factor: {"fit": the 2024-25 line, "test": the 2025-26 sample's}}: the rule behind each tab (the
# game script tab's lean is GS; Q4 is a figure only), the role tab's two rules in one line
TESTED = {**{k: {p: _rule_line(r, p) for p in SEASON_NAMES}
             for k, r in (("matchup", "MU"), ("form", "T4"), ("game_script", "GS"))},
          "role": {p: _role_line(p) for p in SEASON_NAMES}}
# the overall line's closing sentence: when 3 or more factors agree (rule CB) and otherwise
_CB = TESTS["CB"]
OVERALL_TESTED = {
    "agree": (f"Tested: when {AGREE} or more agreed, that side won {_pc(_CB['won'])} v a fair "
              f"chance of {_pc(_CB['market'])} in the test season and lost money at the best "
              f"regulated price in both seasons. No edge."),
    "other": NO_EDGE}
# combo markets (pr, pa, ra): the same facts and rule conditions, but no rule was ever tested on
# them (we hold no past bookmaker lines for these markets), so no test figures are shown: `tested`
# is None and the label says why
UNTESTED = ("Not tested for this market: we hold no past bookmaker lines for it, so this rule "
            "has never been checked against prices here.")
UNTESTED_OVERALL = ("Not tested for this market: we hold no past bookmaker lines for it, so "
                    "nothing here has been checked against prices.")


def factor(name: str, lean: str, headline: str, evidence: dict) -> dict:
    """One factor's block: its lean, headline and key figures, with the reliability label for
    that lean (RELIABILITY) and a copy of its `tested` lines."""
    assert lean in LEANS
    return {"lean": lean, "headline": headline, "evidence": evidence,
            "reliability": RELIABILITY[name][lean], "tested": dict(TESTED[name])}


# ------------------------------------------------------------------ form (T4)
def form(values: list, line: float | None) -> dict:
    """Rule T4 from his played games before tonight, oldest first, and tonight's consensus
    line (None: no bookmaker line)."""
    if line is None:
        return factor("form", UNKNOWN, "No bookmaker line stored, so nothing to count against",
                      {})
    raw = [float(v) for v in list(values)[-FORM_GAMES:]]
    last = [None if np.isnan(v) else int(v) if v.is_integer() else v for v in raw]
    ev = {"line": float(line), "last": last}
    if len(last) < FORM_GAMES:
        return factor("form", UNKNOWN, f"Only {_games(len(last))} before tonight: the count "
                                       f"needs {FORM_GAMES}", ev)
    if None in last:  # as the backtest: a missing stat in the last 4 gives no count
        return factor("form", UNKNOWN, "A stat is missing from his last 4 games", ev)
    over, under = sum(v > line for v in last), sum(v < line for v in last)
    ev |= {"over": over, "under": under}
    vals, ln = ", ".join(_num(v) for v in last), _num(line)
    if over >= FORM_SIDE:
        return factor("form", OVER, f"Over {ln} in {over} of his last {FORM_GAMES} ({vals})", ev)
    if under >= FORM_SIDE:
        return factor("form", UNDER, f"Under {ln} in {under} of his last {FORM_GAMES} ({vals})",
                      ev)
    level = FORM_GAMES - over - under
    if level:
        return factor("form", NEUTRAL, f"{over} over, {under} under and {level} on {ln} in his "
                                       f"last {FORM_GAMES} ({vals})", ev)
    return factor("form", NEUTRAL, f"Split {over}-{under} against {ln} in his last {FORM_GAMES} "
                                   f"({vals})", ev)


# ------------------------------------------------------------------ matchup (MU)
def matchup_table(p: pd.DataFrame, tg: pd.DataFrame) -> pd.DataFrame:
    """Rule MU's table for one season before a slate. p: played player-games (game_id, opp,
    pos and the stats, pra included); tg: that season's team-games before the slate (game_id,
    team), league games only. One row per (team, pos, market) for every team with a game:
    games, allowed (a game, all opposing players of that position group combined; a game in
    which none played counts 0), rank_most (1 = gives up most, ties share the better place: the
    rule's own order), rank (1 = gives up least, ties likewise) and of (teams ranked)."""
    cols = ["games", "allowed", "rank_most", "rank", "of"]
    if tg.empty:
        return pd.DataFrame(columns=cols, index=pd.MultiIndex.from_tuples(
            [], names=["team", "pos", "market"]))
    n = tg.groupby("team").game_id.nunique()
    x = p[p.game_id.isin(set(tg.game_id))]
    x = x.assign(**{c: _values(x, c) for c in SUMS if c not in x})  # summed markets, if absent
    tot = x.groupby(["opp", "pos"])[list(MARKETS)].sum()
    idx = pd.MultiIndex.from_product([n.index, GROUPS], names=["team", "pos"])
    tot = tot.rename_axis(index=["team", "pos"]).reindex(idx, fill_value=0.0)
    allowed = tot.div(n, axis=0, level="team")
    allowed.columns.name = "market"
    d = allowed.stack().rename("allowed").reset_index()
    g = d.groupby(["pos", "market"]).allowed
    d["rank_most"] = g.rank(ascending=False, method="min").astype(int)
    d["rank"] = g.rank(ascending=True, method="min").astype(int)
    d["of"] = g.transform("size").astype(int)
    d["games"] = d.team.map(n).astype(int)
    return d.set_index(["team", "pos", "market"])[cols]


def matchup(table: pd.DataFrame, opp: str, pos: str, market: str) -> dict:
    """Rule MU for one card from matchup_table. Its evidence: allowed (a game), rank_most (its
    place counted from the most allowed, ties sharing the better place: the rule's own order),
    rank (counted from the fewest, ties likewise), of (teams ranked) and games."""
    stat, group = STAT_WORDS[market], GROUP_WORDS.get(pos)
    if group is None:
        return factor("matchup", UNKNOWN, "No position group (G, F or C) for him", {})
    key = (opp, pos, market)
    if key not in table.index:
        return factor("matchup", UNKNOWN, f"{opp} have not played yet this season", {})
    r = table.loc[key]
    games, most, of = int(r.games), int(r.rank_most), int(r.of)
    ev = {"allowed": _one(r.allowed), "rank_most": most, "rank": int(r["rank"]), "of": of,
          "games": games}
    if games < MIN_GAMES:
        return factor("matchup", UNKNOWN, f"{opp} have played {_games(games)}: too few for the rule "
                                          f"(needs {MIN_GAMES})", ev)
    per = f"{_num(ev['allowed'])} a game between them ({_games(games)})"
    if most <= TOP:
        return factor("matchup", OVER, f"{opp} give up the {ordinal(most)}-most {stat} to "
                                       f"{group}: {per}", ev)
    if most > of - TOP:
        return factor("matchup", UNDER, f"{opp} give up the {ordinal(ev['rank'])}-fewest {stat} "
                                        f"to {group}: {per}", ev)
    return factor("matchup", NEUTRAL, f"{opp} are mid-table for {stat} given up to {group} "
                                      f"({ordinal(most)}-most of {of})", ev)


# ------------------------------------------------------------------ game script (GS)
def game_script(team: str, spread: float | None, total: float | None = None,
                points: float | None = None, q4: float | None = None,
                q4_league: float | None = None) -> dict:
    """Rule GS for one card: `spread` is his team's (positive = underdog; None: no game lines
    stored). Total, implied team points and fourth-quarter shares are shown as figures."""
    ev = {k: v for k, v in (("spread", spread), ("total", total), ("points", points),
                            ("q4", q4), ("q4_league", q4_league)) if v is not None}
    if spread is None:
        return factor("game_script", UNKNOWN, "No game lines stored for this game", ev)
    head = f"{team} {_signed(spread)}" + (f" (total {_num(total)})" if total is not None else "")
    if spread >= UNDERDOG:
        return factor("game_script", UNDER, f"{head}: underdogs by {_num(UNDERDOG)} or more", ev)
    side = ("favourites" if spread < 0 else "no favourite" if spread == 0 else
            f"underdogs by less than {_num(UNDERDOG)}")
    return factor("game_script", NEUTRAL, f"{head}: {side}", ev)


# ------------------------------------------------------------------ role (RA, RM)
def regulars_out(p: pd.DataFrame) -> dict:
    """Rule RA's absentees per team from one season's played player-games before a slate (any
    season type; game_id, tip_utc, team, player_id, name, minutes): {team: {"ready": a player
    has 5+ games for the team (so a regular can be told), "absent": [{player_id, name, games,
    minutes}], most minutes first}}. Absent = 25+ minutes a game in 5+ games for the team, did
    not play in the team's latest game, and his latest game was for this team (not traded)."""
    if p.empty:
        return {}
    p = p.sort_values(["tip_utc", "game_id"], kind="mergesort")
    last_team = p.groupby("player_id").team.last()
    st = p.groupby(["team", "player_id"]).agg(games=("game_id", "size"),
                                              minutes=("minutes", "mean"), name=("name", "last"))
    out = {}
    for team, x in p.groupby("team", sort=False):
        played = set(x.player_id[x.game_id == x.game_id.iloc[-1]])
        s = st.loc[team]
        reg = s[(s.games >= MIN_GAMES) & (s.minutes >= REGULAR_MIN)]
        absent = [{"player_id": pid, "name": r["name"], "games": int(r["games"]),
                   "minutes": _one(r["minutes"])}
                  for pid, r in reg.iterrows() if pid not in played and last_team[pid] == team]
        out[team] = {"ready": bool((s.games >= MIN_GAMES).any()),
                     "absent": sorted(absent, key=lambda a: (-a["minutes"], a["name"]))}
    return out


def minutes_trend(minutes: list) -> dict:
    """Rule RM from his played games this season before tonight, oldest first: games, his
    last-3 and season averages and whether the drop rule holds (None before 5 games)."""
    m = np.asarray(list(minutes), dtype=float)
    if len(m) < MIN_GAMES:
        return {"games": len(m), "last3": None, "season": None, "drop": None}
    last3, season = float(m[-LAST:].mean()), float(m.mean())
    return {"games": len(m), "last3": last3, "season": season, "drop": bool(last3 <= DROP * season)}


def role(team: str, pid: str, out: dict | None, trend: dict) -> dict:
    """Rule RA and RM combined for one card: `out` is his team's regulars_out entry (None: no
    games for the team this season), `trend` his minutes_trend."""
    out = out or {"ready": False, "absent": []}
    absent = [a for a in out["absent"] if a["player_id"] != pid]
    ra, rm = bool(absent), trend["drop"] is True
    ev = {"absent": [a["name"] for a in absent], "games": trend["games"]}
    if trend["drop"] is not None:
        ev |= {"last3": _one(trend["last3"]), "season": _one(trend["season"])}
    mins = (f"{_num(ev['last3'])} a game in his last {LAST} v {_num(ev['season'])} this season"
            if "last3" in ev else None)
    if ra and not rm:
        a = absent[0]
        who = (f"{a['name']} ({_num(a['minutes'])} min a game)" if len(absent) == 1 else
               _names([x["name"] for x in absent]) if len(absent) <= 3 else
               f"{len(absent)} regulars, led by {a['name']},")
        return factor("role", OVER, f"{who} missed {team}'s last game", ev)
    if rm and not ra:
        return factor("role", UNDER, f"His minutes are down: {mins}", ev)
    if ra and rm:
        return factor("role", NEUTRAL, f"A regular missed {team}'s last game, but his minutes "
                                       f"are down ({_num(ev['last3'])} v {_num(ev['season'])})",
                      ev)
    if mins is not None:
        return factor("role", NEUTRAL, f"No regular missed {team}'s last game; minutes "
                                       f"{_num(ev['last3'])} (last {LAST}) v "
                                       f"{_num(ev['season'])} (season)", ev)
    if out["ready"]:
        return factor("role", NEUTRAL, f"No regular missed {team}'s last game; his minutes need "
                                       f"{MIN_GAMES} games ({trend['games']} so far)", ev)
    return factor("role", UNKNOWN, f"Only {_games(trend['games'])} this season: these checks "
                                   f"need {MIN_GAMES} games", ev)


# ------------------------------------------------------------------ overall
def _counts_text(c: dict) -> str:
    sides = sorted((s for s in (OVER, UNDER) if c[s]), key=lambda s: -c[s])  # over first on ties
    rest = [f"{c[k]} {k}" for k in (NEUTRAL, UNKNOWN) if c[k]]
    if not sides:
        if c[UNKNOWN] == len(FACTORS):
            return "Too little data to check any factor."
        return f"No factor leans either way ({', '.join(rest)})."
    first = sides[0]
    lead = f"{c[first]} factor leans {first}" if c[first] == 1 else f"{c[first]} factors lean {first}"
    return ", ".join([lead, *(f"{c[s]} {s}" for s in sides[1:]), *rest]) + "."


def overall(leans: dict) -> dict:
    """Counts of the four leans, the side 3 or more agree on (None if fewer) and one line of
    text: the counts, then how such factors did when tested (OVERALL_TESTED, "agree" when 3 or
    more agree, else "other")."""
    c = {k: sum(1 for f in FACTORS if leans.get(f) == k) for k in LEANS}
    agree = OVER if c[OVER] >= AGREE else UNDER if c[UNDER] >= AGREE else None
    tested = OVERALL_TESTED["agree" if agree else "other"]
    return {**c, "agree": agree, "text": f"{_counts_text(c)} {tested}"}


def breakdown(parts: dict) -> dict:
    """The card's `factors` block from its four factors."""
    return {**{f: parts[f] for f in FACTORS},
            "overall": overall({f: parts[f]["lean"] for f in FACTORS})}


def untested(b: dict) -> dict:
    """A combo card's factors block: the same leans, headlines and figures, with no test figures
    (`tested` None) and the reason as each factor's label; the overall line keeps its counts
    and ends with UNTESTED_OVERALL instead of a tested sentence."""
    out = {f: {**b[f], "tested": None, "reliability": UNTESTED} for f in FACTORS}
    o = b["overall"]
    c = {k: o[k] for k in LEANS}
    return {**out, "overall": {**o, "untested": True,
                               "text": f"{_counts_text(c)} {UNTESTED_OVERALL}"}}


# ------------------------------------------------------------------ assembly
def _values(lg: pd.DataFrame, market: str) -> pd.Series:
    parts = SUMS.get(market)
    if parts is None:
        return lg[market]
    out = lg[parts[0]]
    for q in parts[1:]:
        out = out + lg[q]
    return out


# summed markets and their parts, added left to right (npl.nba.model.COMBO_PARTS)
SUMS = {"pra": ("pts", "reb", "ast"), "pr": ("pts", "reb"), "pa": ("pts", "ast"),
        "ra": ("reb", "ast")}


def utc(ts) -> pd.Timestamp:
    """A time as a UTC timestamp (a naive one is read as UTC)."""
    t = pd.Timestamp(ts)
    return t.tz_localize("UTC") if t.tzinfo is None else t.tz_convert("UTC")


def cutoffs(e: dict) -> dict:
    """{game_id: cut-off} for an edition's games: the earlier of its publication and the game's
    start (tip_utc). A card's box-score inputs (his log, the season's player-games) count only
    games that started strictly before his game's cut-off, so an edition published after some
    games had started never counts them, even once their box scores are stored."""
    pub = utc(e["published_at"])
    return {g["game_id"]: min(pub, utc(g["tip_utc"])) for g in e.get("games", [])}


def slate_cutoff(e: dict) -> pd.Timestamp:
    """The slate's earliest cut-off: before publication and before any of tonight's games had
    started. Tables ranked across the league (the matchup and role rules' season, the cards'
    opponent ranks) use it, so no team's game tonight enters another team's rank either."""
    return min(cutoffs(e).values(), default=utc(e["published_at"]))


def cut_log(lg: pd.DataFrame | None, before: pd.Timestamp, game_id: str | None = None):
    """A player's log cut at `before` (his games that started strictly before it), without his
    game tonight (`game_id`) whatever its stored start; None stays None."""
    if lg is None:
        return None
    keep = lg.tip_utc < before
    if game_id is not None:
        keep &= lg.game_id != game_id
    return lg[keep]


def season_rows(L: dict, cur: int, cutoff: pd.Timestamp, before: pd.Timestamp | None = None
                ) -> tuple[pd.DataFrame, pd.DataFrame, set]:
    """(played player-games, team-games) of season `cur` on dates before `cutoff` that started
    strictly before `before` (when given), league games only, and the games that are not league
    games (exhibitions, every season; usually none)."""
    games = L["games"]
    other = set(games.game_id) - X.league_games(games)
    pg, tg = L["player_games"], L["team_games"]
    p = pg[(pg.season == cur) & (pg.participation == "played") & (pg.date < cutoff)]
    t = tg[(tg.season == cur) & (tg.date < cutoff)]
    if before is not None:
        p, t = p[p.tip_utc < before], t[t.tip_utc < before]
    t = t[["game_id", "team"]]
    if other:
        p, t = p[~p.game_id.isin(other)], t[~t.game_id.isin(other)]
    return p.assign(pra=p.pts + p.reb + p.ast, pr=p.pts + p.reb, pa=p.pts + p.ast,
                    ra=p.reb + p.ast), t, other


def build(e: dict, fc: list, logs: dict, mkt: dict, ex: dict, L: dict) -> dict:
    """{(player_id, market): factors block} for an edition's headline forecasts `fc`. logs: each
    player's played games (the cards' logs), cut again here at his game's cut-off (cutoffs);
    mkt: tonight's consensus per (player_id, market); ex: the props extras block (game lines,
    fourth-quarter shares). The season's player-games behind the matchup and role rules are
    games on earlier dates that started before the slate's earliest cut-off: on earlier dates,
    every game started before any of tonight's, so that is each card's own cut-off as well."""
    cur = X.season_of(e["date_et"])
    cuts, pub = cutoffs(e), utc(e["published_at"])
    p, tg, other = season_rows(L, cur, pd.Timestamp(e["date_et"]), slate_cutoff(e))
    table = matchup_table(p, tg)
    out = regulars_out(p)
    mins = {pid: g.minutes.tolist() for pid, g in
            p.sort_values(["tip_utc", "game_id"], kind="mergesort").groupby("player_id")}
    games = {g["game_id"]: g for g in e["games"]}
    lines = ex.get("game_lines") or {}
    league = ((ex.get("league") or {}).get(str(cur)) or {}).get("q4")
    players = ex.get("players") or {}
    league_logs: dict = {}  # each player's log at his cut-off, without exhibition games, cut once
    res = {}
    for f in fc:
        pid, mk, team = f["player_id"], f["market"], f["team"]
        if pid not in league_logs:
            lg = cut_log(logs.get(pid), cuts.get(f["game_id"], pub), f["game_id"])
            if lg is not None and other:
                lg = lg[~lg.game_id.isin(other)]
            league_logs[pid] = lg
        lg = league_logs[pid]
        vals = [] if lg is None or not len(lg) else _values(lg, mk).tolist()
        gl, home = lines.get(f["game_id"]) or {}, games[f["game_id"]]["home"] == team
        hs = gl.get("home_spread")
        q4 = ((((players.get(pid) or {}).get("script") or {}).get("q4") or {}).get(str(cur))
              or {}).get("q4")
        res[(pid, mk)] = (untested if mk in COMBOS else (lambda b: b))(breakdown({
            "form": form(vals, (mkt.get((pid, mk)) or {}).get("line")),
            "matchup": matchup(table, f["opponent"], f["position"], mk),
            "game_script": game_script(
                team, None if hs is None else (hs if home else -hs) + 0.0, gl.get("total"),
                gl.get("home_points" if home else "away_points"), q4,
                league if q4 is not None else None),
            "role": role(team, pid, out.get(team), minutes_trend(mins.get(pid, []))),
        }))
    return res
