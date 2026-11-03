"""NBA factor breakdown (npl.nba.factors, D-047) on small synthetic frames: each frozen rule
(form T4, matchup MU, game script GS, role RA and RM), the overall count and its wording, each
factor's two-season test lines, the cut-off that keeps tonight's games out of every input, the
assembled card block, the props payload, the snapshot trim and the parlay route. Every player,
line, spread and chance below is made up."""

import copy
import json
import re
import shutil
import subprocess

import pandas as pd
import pytest

from npl import snapshot
from npl.nba import appdata
from npl.nba import factors as F

BANNED = re.compile(r"\b(pick|picks|strong|signal|bet|bets|lock|tip)\b", re.I)


def _texts(block: dict) -> list[str]:
    return [block[f]["headline"] for f in F.FACTORS] + [block["overall"]["text"]]


# --------------------------------------------------------------------------- form (T4)
def test_form_counts_his_last_four_games_against_tonights_line():
    f = F.form([30, 30, 15, 15, 28, 6], 22.5)  # only the last 4 count
    assert f["lean"] == "under"
    assert f["headline"] == "Under 22.5 in 3 of his last 4 (15, 15, 28, 6)"
    assert f["evidence"] == {"line": 22.5, "last": [15, 15, 28, 6], "over": 1, "under": 3}
    assert f["reliability"] == F.TESTS["T4"]["reliability"]
    assert F.form([25, 28, 30, 26], 22.5)["lean"] == "over"
    assert F.form([25, 28, 30, 26], 22.5)["headline"] == "Over 22.5 in 4 of his last 4 (25, 28, 30, 26)"
    assert F.form([25, 28, 10, 26], 22.5)["lean"] == "over"  # 3 of 4


def test_form_split_pushes_and_missing_data():
    split = F.form([25, 10, 30, 12], 22.5)
    assert split["lean"] == "neutral" and split["headline"].startswith("Split 2-2 against 22.5")
    level = F.form([22, 22, 25, 10], 22.0)  # a whole-number line: 2 on the line
    assert level["lean"] == "neutral"
    assert level["evidence"]["over"] == 1 and level["evidence"]["under"] == 1
    assert "1 over, 1 under and 2 on 22" in level["headline"]
    assert F.form([22, 25, 26, 10], 22.0)["lean"] == "neutral"  # 2 over, 1 under, 1 on: no 3
    few = F.form([30, 30, 30], 22.5)
    assert few["lean"] == "unknown" and "needs 4" in few["headline"]
    none = F.form([30, 30, 30, 30], None)
    assert none["lean"] == "unknown" and none["evidence"] == {}
    gap = F.form([30, float("nan"), 30, 30], 22.5)  # as the backtest: no count with a gap
    assert gap["lean"] == "unknown" and gap["evidence"]["last"] == [30, None, 30, 30]


# --------------------------------------------------------------------------- matchup (MU)
def _rr(n_teams: int, rounds: int, start="2026-10-21"):
    """Circle-method round robin: every team plays once a round, one round a day."""
    t = list(range(n_teams))
    games = []
    for r in range(rounds):
        order = [t[0]] + t[1:][r:] + t[1:][:r]
        d = (pd.Timestamp(start) + pd.Timedelta(days=r)).strftime("%Y-%m-%d")
        for i in range(n_teams // 2):
            games.append((f"g{r}_{i}", d, order[i], order[n_teams - 1 - i]))
    return games


ROSTER = [("G", 30.0), ("G", 28.0), ("F", 30.0), ("F", 26.0), ("C", 28.0)]


def _league(sched, *, rosters=None, skip=frozenset(), stat=None, season=2027):
    """A lake (games, player_games, team_games) from a schedule [(gid, date, home, away)].
    rosters: {team: [(pid, pos, minutes or [minutes by team game])]}, default five players per
    team; skip: {(gid, pid)} who did not play; stat(pid, team, opp) -> (pts, reb, ast)."""
    games, pg, tg, n = [], [], [], {}
    for gid, d, h, a in sched:
        tip = pd.Timestamp(d, tz="UTC") + pd.Timedelta(hours=23)
        games.append({"game_id": gid, "season": season, "season_type": "REG",
                      "date": pd.Timestamp(d), "tip_utc": tip, "home": h, "away": a,
                      "home_score": 100.0, "away_score": 98.0, "is_final": True})
        for team, opp, home in ((h, a, True), (a, h, False)):
            k = n[team] = n.get(team, -1) + 1
            roster = (rosters or {}).get(team) or [(f"{team}-{j}", pos, m)
                                                   for j, (pos, m) in enumerate(ROSTER)]
            for pid, pos, mins in roster:
                if (gid, pid) in skip:
                    continue
                pts, reb, ast = stat(pid, team, opp) if stat else (10.0, 5.0, 3.0)
                pg.append({"game_id": gid, "season": season, "season_type": "REG",
                           "date": pd.Timestamp(d), "tip_utc": tip, "player_id": pid,
                           "name": pid.upper(), "team": team, "opp": opp, "is_home": home,
                           "pos": pos, "participation": "played",
                           "minutes": float(mins[k] if isinstance(mins, list) else mins),
                           "pts": float(pts), "reb": float(reb), "ast": float(ast),
                           "fg3m": 1.0})
            tg.append({"game_id": gid, "team": team, "season": season, "date": pd.Timestamp(d),
                       "tip_utc": tip})
    return {"games": pd.DataFrame(games), "player_games": pd.DataFrame(pg),
            "team_games": pd.DataFrame(tg)}


def _table(L, date="2026-11-02", season=2027):
    p, tg, _ = F.season_rows(L, season, pd.Timestamp(date))
    return F.matchup_table(p, tg)


def test_matchup_ranks_thirty_teams_top_and_bottom_ten():
    # team k's opponents score 10 + k points a player: team 29 gives up most, team 0 least
    L = _league(_rr(30, 5), stat=lambda pid, team, opp: (10 + opp, 5, 3))
    t = _table(L)
    r = t.loc[(29, "G", "pts")]
    assert (r.games, r.allowed, r.rank_most, r["rank"], r.of) == (5, 2 * 39.0, 1, 30, 30)
    assert t.loc[(0, "G", "pra")].allowed == 2 * (10 + 0 + 5 + 3)  # two guards a game
    lean = {k: F.matchup(t, k, "G", "pts")["lean"] for k in (29, 20, 19, 10, 9, 0)}
    assert lean == {29: "over", 20: "over", 19: "neutral", 10: "neutral", 9: "under",
                    0: "under"}
    m = F.matchup(t, 20, "G", "pts")
    assert m["headline"] == "20 give up the 10th-most points to guards: 60 a game between them (5 games)"
    assert m["evidence"] == {"allowed": 60.0, "rank": 21, "of": 30, "games": 5}
    u = F.matchup(t, 0, "C", "pra")
    assert u["headline"].startswith("0 give up the 1st-fewest PRA to centres")
    assert "mid-table" in F.matchup(t, 15, "F", "pts")["headline"]
    # every team gives up the same rebounds: ties share the better rank both ways (method
    # "min", as the backtest), so all of them count as giving up the most
    tie = F.matchup(t, 15, "F", "reb")
    assert tie["lean"] == "over" and tie["evidence"]["rank"] == 1
    assert tie["headline"].startswith("15 give up the 1st-most rebounds to forwards")
    assert F.matchup(t, 15, "F", "pts")["reliability"] == F.TESTS["MU"]["reliability"]


def test_matchup_needs_five_opponent_games_and_counts_missing_positions_as_zero():
    sched = [("a", "2026-10-21", "PHX", "GS"), ("b", "2026-10-23", "GS", "LAL"),
             ("c", "2026-10-25", "LAL", "PHX")]
    rosters = {"PHX": [("p1", "G", 30.0), ("p2", "C", 30.0)], "GS": [("s1", "G", 30.0)],
               "LAL": [("l1", "G", 30.0), ("l2", "C", 30.0)]}
    L = _league(sched, rosters=rosters, skip={("c", "p2")},
                stat=lambda pid, team, opp: (20, 10, 5))
    t = _table(L)
    # PHX faced GS (no centre) and LAL (one centre): 35 PRA to centres in 2 games
    assert t.loc[("PHX", "C", "pra")].allowed == 17.5 and t.loc[("PHX", "C", "pra")].games == 2
    # LAL faced GS (no centre) and PHX, whose centre did not play in game c: 0 in 2 games
    assert t.loc[("LAL", "C", "pra")].allowed == 0.0
    m = F.matchup(t, "PHX", "C", "pra")
    assert m["lean"] == "unknown" and m["headline"] == "PHX have played 2 games: too few to rank (needs 5)"
    assert F.matchup(t, "BOS", "G", "pts")["headline"] == "BOS have not played yet this season"
    assert F.matchup(t, "PHX", "X", "pts")["lean"] == "unknown"
    # games on the slate's date or later are not counted
    assert _table(L, "2026-10-23").loc[("PHX", "G", "pts")].games == 1


def test_season_rows_leave_out_exhibition_games():
    sched = _rr(4, 6) + [("asg", "2026-10-30", "STARS", "WORLD")]
    L = _league(sched)
    p, tg, other = F.season_rows(L, 2027, pd.Timestamp("2026-11-02"))
    assert other == {"asg"} and "asg" not in set(p.game_id) and "asg" not in set(tg.game_id)
    assert set(F.matchup_table(p, tg).index.get_level_values(0)) == {0, 1, 2, 3}


# --------------------------------------------------------------------------- game script (GS)
def test_game_script_underdog_by_seven_or_more_leans_under():
    u = F.game_script("PHX", 7.0, 221.5, 107.25)
    assert u["lean"] == "under" and u["headline"] == "PHX +7 (total 221.5): underdogs by 7 or more"
    n = F.game_script("PHX", 2.5, 227.5, 112.5, 0.1591, 0.2404)
    assert n["lean"] == "neutral"
    assert n["headline"] == "PHX +2.5 (total 227.5): underdogs by less than 7"
    assert n["evidence"] == {"spread": 2.5, "total": 227.5, "points": 112.5, "q4": 0.1591,
                             "q4_league": 0.2404}
    assert F.game_script("GS", -6.5, 227.5)["headline"] == "GS -6.5 (total 227.5): favourites"
    assert F.game_script("GS", 0.0)["headline"] == "GS 0: no favourite"
    assert F.game_script("GS", 6.75)["lean"] == "neutral"
    x = F.game_script("PHX", None, q4=0.2)
    assert x["lean"] == "unknown" and x["headline"] == "No game lines stored for this game"
    assert x["reliability"] == F.TESTS["GS"]["reliability"] == "Tested on 2 seasons of real lines: no effect."


# --------------------------------------------------------------------------- role (RA, RM)
def _played(rows):
    """rows: (game_id, day, team, player_id, minutes)."""
    return pd.DataFrame([{"game_id": g, "tip_utc": pd.Timestamp("2026-10-20", tz="UTC") +
                          pd.Timedelta(days=d), "team": t, "player_id": p, "name": p.title(),
                          "minutes": float(m)} for g, d, t, p, m in rows])


def test_regulars_out_follows_the_frozen_rule():
    rows = []
    for k in range(6):  # PHX play 6 games
        g = f"g{k}"
        rows.append((g, k, "PHX", "jg", 30))
        if k < 5:
            rows += [(g, k, "PHX", "mw", 27), (g, k, "PHX", "sub", 24.9)]  # both miss game 6
        if 1 <= k:
            rows.append((g, k, "PHX", "new", 30))  # 5 games, played the last one
        if 2 <= k < 5:
            rows.append((g, k, "PHX", "few", 35))  # only 3 games
        if k < 5:
            rows.append((g, k, "PHX", "gone", 31))  # traded after game 5 ...
    rows.append(("x1", 5, "LAL", "gone", 20))  # ... and played for LAL since
    out = F.regulars_out(_played(rows))
    assert [a["player_id"] for a in out["PHX"]["absent"]] == ["mw"]
    assert out["PHX"]["absent"][0] == {"player_id": "mw", "name": "Mw", "games": 5,
                                       "minutes": 27.0}
    assert out["PHX"]["ready"] is True and out["LAL"]["ready"] is False
    assert F.regulars_out(_played([])) == {}


def test_minutes_trend_drop_rule():
    assert F.minutes_trend([30, 30, 30, 30])["drop"] is None  # needs 5 games
    t = F.minutes_trend([32.5, 32.5, 20, 20, 20])  # season 25.0, last 3 = 0.8 x 25 exactly
    assert (t["games"], t["last3"], t["season"], t["drop"]) == (5, 20.0, 25.0, True)
    assert F.minutes_trend([32.5, 32.5, 20, 20, 20.5])["drop"] is False
    assert F.minutes_trend([40, 40, 40, 40, 40, 10, 40, 40])["drop"] is False  # last 3 count


def test_role_combines_the_two_rules_as_the_backtest():
    out = {"ready": True, "absent": [{"player_id": "mw", "name": "Mark W", "games": 5,
                                      "minutes": 27.1}]}
    steady = F.minutes_trend([30] * 6)
    down = F.minutes_trend([32.5, 32.5, 20, 20, 20])
    early = F.minutes_trend([30] * 4)
    o = F.role("PHX", "jg", out, steady)
    assert o["lean"] == "over" and o["headline"] == "Mark W (27.1 min a game) missed PHX's last game"
    assert o["reliability"] == F.TESTS["RA"]["reliability"]
    assert o["evidence"] == {"absent": ["Mark W"], "games": 6, "last3": 30.0, "season": 30.0}
    assert F.role("PHX", "jg", out, early)["lean"] == "over"  # RA alone, RM not checkable
    u = F.role("PHX", "jg", {"ready": True, "absent": []}, down)
    assert u["lean"] == "under" and u["reliability"] == F.TESTS["RM"]["reliability"]
    assert u["headline"] == "His minutes are down: 20 a game in his last 3 v 25 this season"
    both = F.role("PHX", "jg", out, down)
    assert both["lean"] == "neutral" and both["reliability"] == F.ROLE_RELIABILITY
    assert F.role("PHX", "mw", out, steady)["lean"] == "neutral"  # himself is not a teammate
    assert F.role("PHX", "jg", {"ready": True, "absent": []}, early)["lean"] == "neutral"
    x = F.role("PHX", "jg", {"ready": False, "absent": []}, early)
    assert x["lean"] == "unknown" and x["headline"] == "Only 4 games this season: the role checks need 5"
    assert F.role("PHX", "jg", None, F.minutes_trend([]))["lean"] == "unknown"
    many = {"ready": True, "absent": [{"player_id": c, "name": c, "games": 9, "minutes": 30.0}
                                      for c in ("A", "B", "C", "D")]}
    assert F.role("PHX", "jg", many, steady)["headline"] == "4 regulars, led by A, missed PHX's last game"
    two = {"ready": True, "absent": many["absent"][:2]}
    assert F.role("PHX", "jg", two, steady)["headline"] == "A and B missed PHX's last game"


# --------------------------------------------------------------------------- overall
def test_overall_counts_and_the_honest_line():
    o = F.overall({"matchup": "under", "form": "under", "game_script": "neutral",
                   "role": "neutral"})
    assert o["text"] == "2 factors lean under, 2 neutral. Tested: these factors did not beat the market."
    assert (o["over"], o["under"], o["neutral"], o["unknown"], o["agree"]) == (0, 2, 2, 0, None)
    three = F.overall({"matchup": "over", "form": "over", "game_script": "neutral",
                       "role": "over"})
    assert three["agree"] == "over"
    assert three["text"] == ("3 factors lean over, 1 neutral. Tested: when 3 or more agreed, "
                             "that side won 51.2% v the market's 51.4%. No edge.")
    mixed = F.overall({"matchup": "over", "form": "under", "game_script": "unknown",
                       "role": "unknown"})
    assert mixed["text"].startswith("1 factor leans over, 1 under, 2 unknown.")
    assert F.overall(dict.fromkeys(F.FACTORS, "neutral"))["text"].startswith(
        "No factor leans either way (4 neutral).")
    assert F.overall(dict.fromkeys(F.FACTORS, "unknown"))["text"].startswith(
        "Too little data to check any factor.")
    assert F.overall({"matchup": "under", "form": "under", "game_script": "under",
                      "role": "under"})["agree"] == "under"


def test_reliability_labels_are_the_backtests():
    assert F.TESTS["T4"]["reliability"] == ("Beat the fair chance in one season, not the other; "
                                            "lost money at the best regulated price in both. "
                                            "No edge.")
    # what the form label says holds for the figures shown with it (T4 and T5 alike)
    for k in ("T4", "T5"):
        t = F.TESTS[k]
        assert t["fit"]["won"] < t["fit"]["market"] and t["won"] > t["market"]  # one season only
        assert t["fit"]["roi"] < 0 and t["roi"] < 0  # lost money in both
        assert t["reliability"] == F.TESTS["T4"]["reliability"]
    assert F.TESTS["MU"]["reliability"] == ("Real effect, but mostly already in the line. No edge "
                                            "on 2 seasons of real lines.")
    assert F.TESTS["RA"]["reliability"] == ("Real effect, but bookmakers already move the line "
                                            "for it. No edge.")
    assert F.TESTS["CB"]["reliability"] == ("Factors agreeing is not an edge: tested on 2 seasons "
                                            "of real lines.")
    assert all(v["roi"] < 0 for v in F.TESTS.values())  # every rule lost at regulated prices
    t = F.tests_payload()
    assert set(t["rules"]) == {"T4", "T5", "MU", "GS", "Q4", "RA", "RM", "CB"}
    assert t["rules"]["CB"]["props"] == 1592 and t["rules"]["CB"]["won"] == 0.512
    json.dumps(t)


def test_every_factor_carries_both_seasons_with_the_backtest_figures():
    t = F.TESTED
    assert set(t) == set(F.FACTORS) and all(set(v) == {"fit", "test"} for v in t.values())
    assert t["form"] == {
        "fit": "2024-25: on 44,754 props the side it pointed to won 51.3% v a fair chance of "
               "51.7%; return −5.0% (95% interval −6.0% to −3.9%) at the best regulated price.",
        "test": "2025-26 sample: on 11,731 props the side it pointed to won 52.7% v a fair "
                "chance of 51.6%; return −2.4% (95% interval −4.4% to −0.4%) at the best "
                "regulated price."}
    assert t["game_script"]["test"] == (
        "2025-26 sample: on 4,018 props the under won 51.0% v a fair chance of 50.7% (all unders "
        "51.7%); return −4.1% (95% interval −8.4% to +0.2%) at the best regulated price.")
    assert t["role"]["fit"] == (
        "2024-25: with a regular out, the over won 48.9% v a fair chance of 49.3% on 43,020 "
        "props, return −5.2% (95% interval −6.6% to −3.8%); with his minutes down, the under won "
        "49.0% v 50.5% on 3,187, return −7.8% (−13.1% to −2.8%); both at the best regulated "
        "price.")
    assert t["role"]["test"].startswith("2025-26 sample: with a regular out, the over won 48.0%")
    # each line holds its own season's figures from the table (fit, then the test sample)
    for key, rule in (("matchup", "MU"), ("form", "T4"), ("game_script", "GS")):
        for part, s in (("fit", F.TESTS[rule]["fit"]), ("test", F.TESTS[rule])):
            line = t[key][part]
            assert line.startswith(F.SEASON_NAMES[part] + ": on ")
            for bit in (f"{s['props']:,} props", f"won {F._pc(s['won'])} v a fair chance of "
                        f"{F._pc(s['market'])}", f"return {F._pc(s['roi'], True)} (95% interval "
                        f"{F._pc(s['roi_ci'][0], True)} to {F._pc(s['roi_ci'][1], True)})"):
                assert bit in line, (key, part, bit)
    # every rule's output carries its factor's lines (a copy), and the payload carries them once
    outs = {"form": F.form([30] * 4, 22.5), "matchup": F.matchup(None, "GS", "X", "pts"),
            "game_script": F.game_script("PHX", None),
            "role": F.role("PHX", "jg", None, F.minutes_trend([]))}
    for k, f in outs.items():
        assert f["tested"] == t[k] and f["tested"] is not t[k]
    assert F.tests_payload()["tested"] == t
    assert F.tests_payload()["rules"]["T4"]["fit"]["props"] == 44754


def test_shares_are_written_half_up_with_a_minus_sign():
    assert F._pc(0.5155) == "51.6%" and F._pc(0.5) == "50.0%"
    assert F._pc(-0.0385, True) == "−3.9%" and F._pc(-0.1305, True) == "−13.1%"
    assert F._pc(0.002, True) == "+0.2%" and F._pc(0.002) == "0.2%"
    assert F._pc(-0.0004, True) == "0.0%" and F._pc(0.0, True) == "0.0%"  # never −0.0%


def test_no_text_calls_anything_a_pick_or_a_signal():
    seen = set()
    for vals, line in (([15, 15, 28, 6], 22.5), ([30] * 4, 22.5), ([1, 2], None)):
        seen.add(F.form(vals, line)["headline"])
    for s in (8.0, 2.5, -3.0, None):
        seen.add(F.game_script("PHX", s, 230.0)["headline"])
    for combo in ({"matchup": "over", "form": "over", "game_script": "over", "role": "neutral"},
                  {"matchup": "under", "form": "neutral", "game_script": "unknown",
                   "role": "unknown"}):
        seen.add(F.overall(combo)["text"])
    seen |= {F.SUMMARY, *F.NOTES.values(), *F.RULES.values(), F.ROLE_RELIABILITY,
             *(v["reliability"] for v in F.TESTS.values()),
             *(line for t in F.TESTED.values() for line in t.values())}
    assert seen and not [s for s in seen if BANNED.search(s)]
    assert "favourites" in F.game_script("GS", -1.5)["headline"]  # British English


# --------------------------------------------------------------------------- assembly
TONIGHT = "2026-11-03T03:00:00Z"  # tip-off of tonight's game t1, PHX v GS (US date 2 Nov)


def _scenario(tonight=None, published="2026-11-02T22:00:00Z"):
    """PHX v GS on 2 Nov 2026. PHX have played 6 games: a 27-minute regular (mw) missed the
    last one; jg's last 4 PRA are 15, 15, 28, 6. GS have played 4 games (too few to rank).
    tonight: jg's PRA in tonight's game t1 when its box score is already stored (mw played in
    it), as for an edition published after t1 started once the box scores arrive; published:
    the edition's publication time."""
    sched = [("p1", "2026-10-21", "PHX", "LAL"), ("p2", "2026-10-23", "BOS", "PHX"),
             ("p3", "2026-10-24", "PHX", "GS"), ("p4", "2026-10-26", "LAL", "PHX"),
             ("p5", "2026-10-28", "PHX", "BOS"), ("p6", "2026-10-30", "GS", "PHX"),
             ("q1", "2026-10-22", "GS", "LAL"), ("q2", "2026-10-25", "BOS", "LAL"),
             ("q3", "2026-10-27", "GS", "BOS")]
    if tonight is not None:
        sched.append(("t1", "2026-11-02", "PHX", "GS"))
    rosters = {"PHX": [("jg", "G", 30.0), ("mw", "C", 27.0), ("db", "G", 34.0)],
               "GS": [("sc", "G", 33.0), ("dg", "F", 30.0)],
               "LAL": [("lj", "F", 35.0), ("lg", "G", 30.0)],
               "BOS": [("jt", "F", 36.0), ("jb", "G", 34.0)]}
    pra = {"p1": 40, "p2": 40, "p3": 15, "p4": 15, "p5": 28, "p6": 6}
    if tonight is not None:
        pra["t1"] = tonight
    L = _league(sched, rosters=rosters, skip={("p6", "mw")})
    for k in ("games", "player_games", "team_games"):  # t1 starts at its edition time
        d = L[k]
        d.loc[d.game_id == "t1", "tip_utc"] = pd.Timestamp(TONIGHT)
    pg = L["player_games"]  # jg's PRA game by game
    for gid, v in pra.items():
        i = (pg.game_id == gid) & (pg.player_id == "jg")
        pg.loc[i, ["pts", "reb", "ast"]] = [v - 6.0, 4.0, 2.0]
    e = {"edition_id": "nba-e1", "date_et": "2026-11-02", "headline_model": "N1",
         "published_at": published,
         "games": [{"game_id": "t1", "home": "PHX", "away": "GS", "tip_utc": TONIGHT}],
         "forecasts": [{"game_id": "t1", "player_id": pid, "name": pid.upper(), "team": tm,
                        "opponent": op, "position": pos, "model": "N1", "market": mk}
                       for pid, tm, op, pos in (("jg", "PHX", "GS", "G"), ("sc", "GS", "PHX", "G"))
                       for mk in ("pts", "pra")]}
    logs = {pid: g.sort_values("tip_utc") for pid, g in pg.groupby("player_id")}
    mkt = {("jg", "pra"): {"line": 22.5, "fair_over": 0.484}, ("sc", "pts"): {"line": 9.5}}
    ex = {"game_lines": {"t1": {"home_spread": 2.5, "total": 227.5, "home_points": 112.5,
                                "away_points": 115.0}},
          "league": {"2027": {"q4": 0.2404}},
          "players": {"jg": {"script": {"q4": {"2027": {"q4": 0.1591}}}}}}
    return e, L, logs, mkt, ex


def test_cutoffs_take_the_earlier_of_publication_and_each_games_start():
    e = {"published_at": "2026-11-03T01:13:49Z",
         "games": [{"game_id": "a", "tip_utc": "2026-11-03T00:00:00Z"},
                   {"game_id": "b", "tip_utc": "2026-11-03T02:30:00Z"}]}
    assert F.cutoffs(e) == {"a": pd.Timestamp("2026-11-03T00:00:00Z"),
                            "b": pd.Timestamp("2026-11-03T01:13:49Z")}
    assert F.slate_cutoff(e) == pd.Timestamp("2026-11-03T00:00:00Z")
    assert F.slate_cutoff({"published_at": "2026-11-02T18:00:00Z", "games": []}) == \
        pd.Timestamp("2026-11-02T18:00:00Z")
    # strictly before: a game starting at the cut-off does not count, nor does his game tonight
    # whatever its stored start
    lg = pd.DataFrame({"game_id": ["x", "y", "a"], "tip_utc": pd.to_datetime(
        ["2026-11-01T23:00:00Z", "2026-11-03T00:00:00Z", "2026-11-02T23:30:00Z"], utc=True)})
    assert F.cut_log(lg, F.cutoffs(e)["a"], "a").game_id.tolist() == ["x"]
    assert F.cut_log(lg, F.cutoffs(e)["b"]).game_id.tolist() == ["x", "y", "a"]
    assert F.cut_log(None, F.cutoffs(e)["a"]) is None


def test_an_edition_published_after_a_start_never_counts_that_game(monkeypatch):
    """The 2 Nov final was published at 01:13 UTC, after 9 of its 15 games had started: once
    their box scores were stored, a player's own game that night entered his card log and the
    form count. Every input now stops at the earlier of publication and his game's start."""
    late = "2026-11-03T04:13:00Z"  # 73 minutes after t1 started
    sc = _scenario(tonight=40, published=late)
    e, L, logs, mkt, ex = sc
    pg = L["player_games"]
    mine = pg[(pg.game_id == "t1") & (pg.player_id == "jg")]
    assert len(mine) == 1 and (mine.tip_utc < pd.Timestamp(late)).all()  # stored, and started
    assert F.cutoffs(e) == {"t1": pd.Timestamp(TONIGHT)}
    # the cards' logs as read live from the lake: tonight's game is in nobody's
    monkeypatch.setattr(appdata, "lake", lambda: L)
    got = appdata.game_logs(*appdata.log_cuts(e, e["forecasts"]))
    assert got["jg"].game_id.tolist() == ["p1", "p2", "p3", "p4", "p5", "p6"]
    assert "t1" not in set(got["sc"].game_id)
    assert appdata._team_last_tip("PHX", e) == pd.Timestamp("2026-10-30T23:00:00Z")  # p6
    seen = []
    p = _props_with(monkeypatch, sc, real_logs=True, seen=seen)
    jg = {(x["player_id"], x["market"]): x for x in p["cards"]}[("jg", "pra")]
    assert jg["log"]["values"] == [40, 40, 15, 15, 28, 6]  # not tonight's 40 at the end
    assert jg["factors"]["form"]["lean"] == "under"
    assert jg["factors"]["form"]["headline"] == "Under 22.5 in 3 of his last 4 (15, 15, 28, 6)"
    # the season behind matchup and role: GS still on 4 games, mw still out of PHX's last game
    assert jg["factors"]["matchup"]["evidence"]["games"] == 4
    assert jg["factors"]["role"]["lean"] == "over"
    assert seen == [pd.Timestamp(TONIGHT)]  # opponent ranks from before tonight's games
    # the same edition published before the start gives the same card
    q = _props_with(monkeypatch, _scenario(tonight=40), real_logs=True)
    early = {(x["player_id"], x["market"]): x for x in q["cards"]}[("jg", "pra")]
    assert early["log"] == jg["log"] and early["factors"] == jg["factors"]
    # the rules cut their inputs themselves, even when handed a log that holds tonight's game
    assert "t1" in set(logs["jg"].game_id)
    assert F.build(e, e["forecasts"], logs, mkt, ex, L)[("jg", "pra")]["form"] == \
        jg["factors"]["form"]
    # the old cut (publication only) let it in, and it would have made the count a 2-2 split
    leaky = F.form(F._values(logs["jg"], "pra").tolist(), 22.5)
    assert leaky["lean"] == "neutral" and leaky["evidence"]["last"] == [15, 28, 6, 40]


def test_build_gives_every_card_its_four_factors():
    e, L, logs, mkt, ex = _scenario()
    fx = F.build(e, e["forecasts"], logs, mkt, ex, L)
    assert set(fx) == {("jg", "pts"), ("jg", "pra"), ("sc", "pts"), ("sc", "pra")}
    jg = fx[("jg", "pra")]
    assert list(jg) == ["matchup", "form", "game_script", "role", "overall"]
    assert jg["form"]["headline"] == "Under 22.5 in 3 of his last 4 (15, 15, 28, 6)"
    assert jg["matchup"]["lean"] == "unknown" and "GS have played 4 games" in jg["matchup"]["headline"]
    assert jg["game_script"]["lean"] == "neutral"
    assert jg["game_script"]["headline"] == "PHX +2.5 (total 227.5): underdogs by less than 7"
    assert jg["game_script"]["evidence"]["points"] == 112.5 and jg["game_script"]["evidence"]["q4"] == 0.1591
    assert jg["role"]["lean"] == "over" and jg["role"]["headline"] == "MW (27 min a game) missed PHX's last game"
    assert jg["overall"]["text"] == ("1 factor leans over, 1 under, 1 neutral, 1 unknown. "
                                     "Tested: these factors did not beat the market.")
    assert fx[("jg", "pts")]["form"]["lean"] == "unknown"  # no line for his points
    sc = fx[("sc", "pts")]
    assert sc["game_script"]["headline"] == "GS -2.5 (total 227.5): favourites"
    assert sc["game_script"]["evidence"]["points"] == 115.0
    assert sc["matchup"]["lean"] == "over"  # PHX have 6 games; with 4 teams all are in the top 10
    assert sc["role"]["lean"] == "unknown"  # GS have 4 games: no regular yet, no minutes check
    for b in fx.values():
        assert not [t for t in _texts(b) if BANNED.search(t)]
    json.dumps({f"{k[0]}|{k[1]}": v for k, v in fx.items()})  # plain JSON types


def test_build_without_extras_or_lines_marks_unknowns():
    e, L, logs, _, _ = _scenario()
    fx = F.build(e, e["forecasts"], logs, {}, {}, L)
    jg = fx[("jg", "pra")]
    assert jg["form"]["lean"] == "unknown" and jg["game_script"]["lean"] == "unknown"
    assert jg["game_script"]["evidence"] == {} and jg["role"]["lean"] == "over"


# --------------------------------------------------------------------------- payload, snapshot
def _props_with(monkeypatch, scenario, real_logs=False, seen=None):
    """appdata._props() over a scenario. real_logs: the cards' logs come from appdata.game_logs
    reading the scenario's lake (as live), not from the scenario's own logs; seen collects the
    time opponent_factors is asked for."""
    e, L, logs, mkt, ex = scenario
    e = {**e, "code": {"model_version": "nba-0.3"},
         "forecasts": [{**f, "p_play": 0.95, "minutes_expected": 30.0, "mean": 20.0,
                        "median": 20, "q10": 12, "q90": 28, "dist": {"lo": 0, "pmf": [1.0]},
                        "injury": None} for f in e["forecasts"]]}
    patch = [("market_lines", mkt), ("_simple", {})] + ([] if real_logs else [("game_logs", logs)])
    for name, value in patch:
        monkeypatch.setattr(appdata, name, lambda *a, _v=value, **k: _v)
    monkeypatch.setattr(appdata, "opponent_factors",
                        lambda at: (seen if seen is not None else []).append(at) or {})
    monkeypatch.setattr(appdata, "_extras", lambda e, mkt: (ex, {}))
    monkeypatch.setattr(appdata, "latest", lambda: e)
    monkeypatch.setattr(appdata, "lake", lambda: L)
    if not real_logs:
        monkeypatch.setattr(appdata, "_team_last_tip", lambda team, e: pd.Timestamp(0, tz="UTC"))
    return appdata._props()


def test_props_cards_carry_factors_and_the_payload_their_tests(monkeypatch):
    p = _props_with(monkeypatch, _scenario())
    c = {(x["player_id"], x["market"]): x for x in p["cards"]}
    assert c[("jg", "pra")]["factors"]["form"]["lean"] == "under"
    assert all(x["factors"]["overall"]["text"] for x in p["cards"])
    assert p["factor_tests"]["rules"]["T4"]["reliability"] == F.TESTS["T4"]["reliability"]
    shared = p["factor_tests"]["tested"]
    assert all(x["factors"][k]["tested"] == shared[k] for x in p["cards"] for k in F.FACTORS)
    monkeypatch.setattr(F, "build", lambda *a: 1 / 0)  # a failure never blocks the cards
    q = appdata._props()
    assert len(q["cards"]) == 4 and all(x["factors"] is None for x in q["cards"])


def test_consensus_lines_give_each_sides_fair_chance():
    rows = []
    for book, (o, u) in {"fanduel": (1.95, 1.87), "betmgm": (1.9, 1.9),
                         "draftkings": (2.0, 1.8)}.items():
        for sel, pr in (("over", o), ("under", u)):
            rows.append({"game_id": "g1", "player_id": "p1", "market": "pts", "line": 20.5,
                         "bookmaker": book, "selection": sel, "decimal_price": pr,
                         "observed_at": "2026-11-02T18:00:00+00:00",
                         "availability": "research_reference"})
    m = appdata.consensus_lines(pd.DataFrame(rows))[("p1", "pts")]
    assert m["fair_under"] == pytest.approx(1 - m["fair_over"], abs=1e-12)
    assert round(m["fair_under"], 4) == m["fair_under"]


def test_snapshot_keeps_factor_tabs_on_cards_with_a_line_only():
    fx = {"overall": {"text": "x"}}
    lined = {"market_line": {"line": 22.5}, "factors": fx, "log": {"values": list(range(30))}}
    bare = {"market_line": None, "factors": fx, "log": {"values": [1, 2]}}
    a, b = snapshot._nba_card(lined), snapshot._nba_card(bare)
    assert a["factors"] is fx and a["log"]["values"] == list(range(10, 30))
    assert "factors" not in b and b["log"]["values"] == [1, 2]
    assert "factors" in bare  # the cached card is not changed


def test_snapshot_keeps_the_tested_lines_once():
    shared = F.tests_payload()["tested"]
    fx = {**{k: {"lean": "neutral", "tested": dict(shared[k])} for k in F.FACTORS},
          "overall": {"text": "x"}}
    fx["role"]["tested"] = {"fit": "own", "test": "own"}  # not the payload's copy: kept
    card = {"market_line": {"line": 22.5}, "factors": fx, "log": {"values": [1]}}
    before = copy.deepcopy(card)
    out = snapshot._nba_card(card, shared)
    assert all("tested" not in out["factors"][k] for k in ("matchup", "form", "game_script"))
    assert out["factors"]["role"]["tested"] == {"fit": "own", "test": "own"}
    assert out["factors"]["overall"] == {"text": "x"} and out["factors"]["form"]["lean"] == "neutral"
    assert card == before  # the cached card is not changed


@pytest.mark.skipif(shutil.which("node") is None, reason="node is not installed")
def test_the_offline_page_puts_the_tested_lines_back_on_each_card():
    shared = F.tests_payload()["tested"]
    lined = {"market_line": {"line": 22.5}, "log": {"values": [1]},
             "factors": {**{k: {"lean": "under", "tested": dict(shared[k])} for k in F.FACTORS},
                         "overall": {"text": "x"}}}
    own = copy.deepcopy(lined)
    own["factors"]["form"]["tested"] = {"fit": "own", "test": "own"}
    bare = {"market_line": None, "log": {"values": []}, "factors": None}
    cards = [snapshot._nba_card(c, shared) for c in (lined, own, bare)]
    assert "tested" not in cards[0]["factors"]["form"]
    page = {"data": {"/api/nba/props": {"factor_tests": {"tested": shared}, "cards": cards}}}
    script = snapshot.REHYDRATE.removeprefix("<script>").removesuffix("</script>")
    js = (f"const window = {{ NPL_SNAPSHOT: {json.dumps(page)} }};\n{script}\n"
          "process.stdout.write(JSON.stringify(window.NPL_SNAPSHOT.data['/api/nba/props'].cards));")
    r = subprocess.run([shutil.which("node"), "-e", js], capture_output=True, text=True,
                       timeout=60, check=True)
    got = json.loads(r.stdout)
    assert got[0]["factors"] == lined["factors"]  # every line back, as the server sends it
    assert got[1]["factors"]["form"]["tested"] == {"fit": "own", "test": "own"}
    assert got[1]["factors"]["matchup"]["tested"] == shared["matchup"]
    assert "factors" not in got[2]


def test_parlay_route_resolves_card_legs(monkeypatch):
    sc = _scenario()
    e, L, logs, mkt, ex = sc
    mkt = {**mkt, ("jg", "pra"): {"line": 22.5, "fair_over": 0.484, "fair_under": 0.516,
                                  "books": 7},
           ("sc", "pts"): {"line": 9.5, "fair_over": 0.55, "fair_under": 0.45, "books": 2}}
    p = _props_with(monkeypatch, (e, L, logs, mkt, ex))
    monkeypatch.setattr(appdata, "props", lambda: p)
    r = appdata.parlay("jg:pra:under, sc:pts:over", price=4.0)
    assert r["chance"] == round(0.516 * 0.55, 4) and r["fair_odds"] == round(1 / (0.516 * 0.55), 2)
    assert r["ev"] == round(0.516 * 0.55 * 4.0 - 1, 4)
    assert r["same_game"] == [[0, 1]] and r["independent"] is False and r["thin"] == [1]
    assert [s["side"] for s in r["selections"]] == ["under", "over"]
    assert "error" in appdata.parlay("jg:pts:under")  # no line for his points
    assert "error" in appdata.parlay("jg:pra")
    assert "error" in appdata.parlay("nobody:pts:over")
    assert "error" in appdata.parlay("")


# the test client's own notice about its HTTP library, not about this code
@pytest.mark.filterwarnings("ignore:Using `httpx` with `starlette.testclient` is deprecated")
def test_parlay_http_route(monkeypatch):
    from fastapi.testclient import TestClient

    from npl import api

    seen = []

    def fake(spec, price=None):
        seen.append((spec, price))
        return {"error": "leg 1: bad"} if spec == "bad" else {"chance": 0.25, "legs": 2}

    monkeypatch.setattr(appdata, "parlay", fake)
    client = TestClient(api.app)
    r = client.get("/api/nba/parlay", params={"legs": "a:pts:over,b:reb:under", "price": 4.5})
    assert r.status_code == 200 and r.json() == {"chance": 0.25, "legs": 2}
    assert seen[-1] == ("a:pts:over,b:reb:under", 4.5)
    assert client.get("/api/nba/parlay", params={"legs": "bad"}).status_code == 422
    assert seen[-1] == ("bad", None)
