"""NBA card extras (npl.nba.extras) on small synthetic frames: usage, defensive rebound %,
fourth-quarter share, the close-game split, implied team points and game-line consensus, the
grid against tonight's opponent, position against an opponent's lines, injury fields, stored
lines and the assembled block. Every line and price below is made up."""

import json

import numpy as np
import pandas as pd
import pytest

from npl import db, snapshot
from npl.nba import extras as X
from npl.sources import odds_api as oa


@pytest.fixture(autouse=True)
def fresh_memo():
    X._MEMO.clear()
    yield
    X._MEMO.clear()


# --------------------------------------------------------------------------- (a) usage
def test_usage_rate_formula():
    # (10 + 0.44 x 5 + 2) x (240 / 5) / (30 x (85 + 0.44 x 20 + 14))
    u = X.usage_rate(10, 5, 2, 30, 85, 20, 14, 240)
    assert u == pytest.approx(14.2 * 48 / (30 * 107.8))
    assert X.usage_rate(1, 1, 1, 0, 80, 20, 10, 240) is None  # no minutes
    assert X.usage_rate(1, 1, 1, 20, 0, 0, 0, 240) is None  # no team possessions


def _vol_rows():
    return pd.DataFrame({
        "player_id": ["p", "p", "p", "q"], "game_id": ["g1", "g2", "g3", "g1"],
        "minutes": [30.0, 20.0, 10.0, 25.0], "fga": [10.0, 6.0, np.nan, 8.0],
        "fta": [5.0, 0.0, 2.0, 2.0], "tov": [2.0, 1.0, 0.0, 3.0],
        "t_minutes": [240.0, 265.0, 240.0, 240.0], "t_fga": [85.0, 90.0, 80.0, 85.0],
        "t_fta": [20.0, 25.0, 22.0, 20.0], "t_tov": [14.0, 12.0, 15.0, 14.0]})


def test_volume_table_sums_over_games_before_dividing():
    t = X.volume_table(_vol_rows())
    p = t.loc["p"]
    assert p.games == 3 and p.minutes == pytest.approx(20.0)  # minutes over all 3 games
    # g3 has no FGA: shots and usage use g1 and g2 only, summed, then divided
    used = (10 + 6) + 0.44 * (5 + 0) + (2 + 1)
    team = (85 + 90) + 0.44 * (20 + 25) + (14 + 12)
    assert p.usage == pytest.approx(used * ((240 + 265) / 5) / ((30 + 20) * team))
    assert p.fga == pytest.approx(8.0) and p.shot_volume == pytest.approx((16 + 0.44 * 5) / 2)
    assert t.loc["q"].usage == pytest.approx(X.usage_rate(8, 2, 3, 25, 85, 20, 14, 240))


def test_volume_blocks_this_season_last5_and_last_season():
    rows = []
    for i in range(7):  # 7 games this season (one in the play-in), 2 last season
        rows.append({"player_id": "p", "game_id": f"c{i}", "season": 2027,
                     "season_type": "PLAYIN" if i == 6 else "REG",
                     "tip_utc": pd.Timestamp("2026-10-20", tz="UTC") + pd.Timedelta(days=i),
                     "minutes": 20.0 + i, "fga": 10.0, "fta": 0.0, "tov": 0.0,
                     "t_minutes": 240.0, "t_fga": 80.0, "t_fta": 0.0, "t_tov": 20.0})
    for i in range(2):
        rows.append({**rows[0], "game_id": f"l{i}", "season": 2026, "minutes": 40.0})
    v = X.volume(pd.DataFrame(rows), {"p"}, 2027)["p"]
    assert v["season"]["games"] == 6 and v["season"]["minutes"] == 22.5  # regular season only
    assert v["last5"]["games"] == 5 and v["last5"]["minutes"] == 24.0  # games 2-6, any type
    assert v["last_season"] == {"games": 2, "minutes": 40.0, "fga": 10.0, "shot_volume": 10.0,
                                "usage": pytest.approx(10 * 48 / (40 * 100), abs=1e-4)}


# --------------------------------------------------------------------------- (c) defence
def test_dreb_pct_formula():
    assert X.dreb_pct(30, 10) == pytest.approx(0.75)
    assert X.dreb_pct(0, 0) is None


def _team_box_rows():
    """Four teams, four games each (two meetings per pair, round robin), plus one exhibition."""
    rows = []
    stats = {"A": (20, 30, 8), "B": (25, 35, 10), "C": (30, 28, 12), "D": (25, 33, 6)}
    pairs = [("A", "B"), ("C", "D"), ("A", "C"), ("B", "D"), ("A", "D"), ("B", "C"),
             ("A", "B"), ("C", "D")]
    for i, (h, a) in enumerate(pairs):
        for t, o in ((h, a), (a, h)):
            ast, dreb, oreb = stats[t]
            rows.append({"game_id": f"g{i}", "team": t, "opp": o, "ast": ast, "dreb": dreb,
                         "oreb": oreb})
    rows += [{"game_id": "x", "team": "STARS", "opp": "WORLD", "ast": 50, "dreb": 40, "oreb": 1},
             {"game_id": "x", "team": "WORLD", "opp": "STARS", "ast": 50, "dreb": 40, "oreb": 1}]
    return pd.DataFrame(rows)


def test_defence_table_assists_allowed_dreb_pct_and_ranks():
    t = X.defence_table(_team_box_rows())
    assert set(t.index) == {"A", "B", "C", "D"} and (t["of"] == 4).all()  # exhibition left out
    # A met B twice, C and D once: allowed (25 + 25 + 30 + 25) / 4 assists
    assert t.loc["A", "ast_allowed"] == pytest.approx(105 / 4)
    # A's DREB% = its defensive rebounds / (those + its opponents' offensive rebounds)
    assert t.loc["A", "dreb_pct"] == pytest.approx(4 * 30 / (4 * 30 + 10 + 10 + 12 + 6))
    # rank 1 = fewest assists allowed / highest DREB%
    assert t.ast_allowed.idxmin() == t.ast_rank.idxmin()
    assert t.loc[t.dreb_pct.idxmax(), "dreb_rank"] == 1
    assert sorted(t.dreb_rank) == [1, 2, 3, 4]


def test_defence_cuts_to_regular_season_before_the_slate():
    tb = _team_box_rows().assign(season_type=2, date=pd.Timestamp("2026-10-25"))
    tb.loc[tb.game_id == "g7", "date"] = pd.Timestamp("2026-11-02")  # tonight's date: left out
    d = X.defence(tb, 2027, pd.Timestamp("2026-11-02"), set(tb.game_id))
    assert d["C"]["games"] == 3 and d["A"]["games"] == 4
    assert X.defence(None, 2027, pd.Timestamp("2026-11-02"), set()) == {}


# --------------------------------------------------------------------------- (b) game script
def _pbp():
    rows = [  # game, type, period, scoring, value, athlete
        ("1", 2, 1, True, 2, 7), ("1", 2, 2, True, 3, 7), ("1", 2, 4, True, 2, 7),
        ("1", 2, 4, True, 1, 7), ("1", 2, 5, True, 2, 7), ("1", 2, 4, False, 0, 7),
        ("1", 2, 3, True, 2, 8), ("1", 2, 4, True, 2, 8), ("1", 2, 1, True, 2, None),
        ("2", 3, 4, True, 3, 7)]  # playoffs: left out
    return pd.DataFrame(rows, columns=["game_id", "season_type", "period_number", "scoring_play",
                                       "score_value", "athlete_id_1"])


def test_fourth_quarter_share_counts_overtime_in_the_total_and_separately():
    pp = X.period_points(_pbp(), 2026)
    # the unattributed basket and the playoff game are left out
    assert set(pp.game_id) == {"1"} and pp.points.sum() == 14
    sh = X.period_shares(pp, ["season", "player_id"])
    me = sh.loc[(2026, "7")]
    assert me.points == 10 and me.q4_points == 3 and me.ot_points == 2
    assert me.q4 == pytest.approx(0.3) and me.ot == pytest.approx(0.2)
    lg = X.period_shares(pp, ["season"]).loc[2026]
    assert lg.q4 == pytest.approx(5 / 14) and lg.ot == pytest.approx(2 / 14)


def test_close_game_split():
    g = pd.DataFrame({"game_id": list("abcde"), "margin": [3, 5, 6, 20, np.nan],
                      "pra": [10.0, 20.0, 30.0, 50.0, 99.0],
                      "minutes": [30.0, 34.0, 20.0, 10.0, 40.0]})
    s = X.close_split(g)
    assert s["close"] == {"games": 2, "pra": 15.0, "minutes": 32.0}  # margin 5 counts as close
    assert s["other"] == {"games": 2, "pra": 40.0, "minutes": 15.0}  # not-final game left out
    assert X.close_split(g.iloc[:2])["other"] == {"games": 0, "pra": None, "minutes": None}


# --------------------------------------------------------------------------- (g) game lines
def test_implied_team_points():
    assert X.implied_points(220.0, -6.0) == (113.0, 107.0)  # home favoured by 6
    assert X.implied_points(221.5, 2.5) == (109.5, 112.0)
    assert X.implied_points(None, -3.0) == (None, None)


def _gl(book, market, side, point, price, obs, avail="research_reference"):
    return {"source": "the_odds_api", "sport": "nba", "event_id": "ev", "game_id": "g1",
            "commence_time": "2026-11-03T03:00:00Z", "home_team": "Home", "away_team": "Away",
            "bookmaker": book, "bookmaker_region": "us", "availability": avail, "market": market,
            "outcome": side, "side": side, "point": point, "price": price, "quote_time": obs,
            "observed_at": obs}


def _board_rows(obs, home_pts, totals, ml_home):
    out = []
    for book, hp, tot, mlh in zip(("fanduel", "draftkings", "betmgm", "bovada"), home_pts,
                                  totals, ml_home, strict=True):
        out += [_gl(book, "spread", "home", hp, 1.91, obs),
                _gl(book, "spread", "away", -hp, 1.91, obs),
                _gl(book, "total", "over", tot, 1.9, obs), _gl(book, "total", "under", tot, 1.9, obs),
                _gl(book, "moneyline", "home", None, mlh, obs),
                _gl(book, "moneyline", "away", None, round(4.6 - mlh, 2), obs)]
    return out


def test_game_line_consensus_median_regulated_best_moneyline_and_move():
    first = _board_rows("2026-11-02T12:00:00Z", [-4.5, -5.0, -4.5, -9.0], [228.5, 229.0, 228.5, 240],
                        [1.55, 1.6, 1.5, 1.3])
    last = _board_rows("2026-11-02T20:00:00Z", [-6.0, -5.5, -6.5, -1.0], [226.5, 226.5, 227.0, 200],
                       [1.45, 1.5, 1.44, 3.0])
    s = X.game_lines(pd.DataFrame(first + last))["g1"]
    # median over the three regulated books; Bovada (offshore) never counts
    assert s["home_spread"] == -6.0 and s["total"] == 226.5
    assert s["books"] == {"spread": 3, "total": 3, "moneyline": 3}
    assert s["moneyline"] == {"home": 1.5, "home_book": "draftkings", "away": 3.16,
                              "away_book": "betmgm"}
    assert (s["home_points"], s["away_points"]) == (116.2, 110.2)  # (226.5 +/- 6) / 2, rounded
    assert s["first"]["home_spread"] == -4.5 and s["first"]["total"] == 228.5
    assert s["move"] == {"home_spread": -1.5, "total": -2.0}
    assert s["seen"] == "2026-11-02T20:00:00Z" and s["first_seen"] == "2026-11-02T12:00:00Z"
    assert s["observations"] == 2 and s["availability"] == "research_reference"
    one = X.game_lines(pd.DataFrame(first))["g1"]
    assert one["move"] == {"home_spread": None, "total": None}  # nothing to compare with
    assert X.game_lines(pd.DataFrame(first), {"g2"}) == {}  # a game never pulled: absent


def test_stored_game_lines_drop_in_play_rows(tmp_path):
    con = db.connect(tmp_path / "t.sqlite3")
    rows = _board_rows("2026-11-02T20:00:00Z", [-6.0, -5.5, -6.5, -1.0], [226.5] * 4,
                       [1.45] * 4)
    rows += [{**r, "observed_at": "2026-11-03T03:30:00Z", "quote_time": "x", "point": 1.0}
             for r in _board_rows("", [1.0] * 4, [190.0] * 4, [2.0] * 4)]  # after tip-off
    oa.store_game_lines(con, pd.DataFrame(rows), None)
    s = X.stored_game_lines(con, {"g1"})["g1"]
    assert s["home_spread"] == -6.0 and s["observations"] == 1
    assert X.stored_game_lines(con, {"g9"}) == {}


# --------------------------------------------------------------------------- (e) grid
def _played(rows):
    df = pd.DataFrame(rows, columns=["player_id", "opp", "game_id", "date", "season_type", "pts",
                                     "reb", "ast", "fg3m"])
    df["date"] = pd.to_datetime(df.date)
    df["tip_utc"] = df.date.dt.tz_localize("UTC") + pd.Timedelta(hours=23)
    df["season"] = np.where(df.date.dt.month >= 8, df.date.dt.year + 1, df.date.dt.year)
    df["team"], df["is_home"], df["minutes"] = "PHX", True, 30.0
    df["pra"] = df.pts + df.reb + df.ast
    df["pos"] = "G"
    return df


def test_grid_against_tonights_opponent_differences_and_stored_lines():
    p = _played([("p", "GS", "g1", "2025-01-10", "REG", 20, 5, 4, 2),
                 ("p", "GS", "g2", "2025-04-30", "POST", 30, 4, 6, 4),
                 ("p", "GS", "g3", "2026-04-17", "PLAYIN", 10, 2, 1, 0),
                 ("p", "LAL", "g4", "2026-01-05", "REG", 50, 9, 9, 9),  # another opponent
                 ("q", "GS", "g1", "2025-01-10", "REG", 8, 8, 8, 0)])
    lines = pd.DataFrame({"game_id": ["g1", "g1", "g4"], "player_id": ["p", "p", "p"],
                          "market": ["pts", "pra", "pts"], "line": [18.5, 27.5, 30.5],
                          "books": [5, 5, 5], "snapshot": ["s"] * 3})
    slate = pd.DataFrame({"player_id": ["p", "q"], "opp": ["GS", "GS"]})
    tonight = {("p", "pts"): 15.5, ("p", "pra"): 22.5}  # no line tonight for reb, ast, threes
    g = X.vs_opp(p, slate, lines, tonight, cap=2)
    me = g["p"]
    assert me["opp"] == "GS" and me["n"] == 3 and len(me["games"]) == 2  # capped, newest first
    assert [r["d"] for r in me["games"]] == ["2026-04-17", "2025-04-30"]
    assert [r["t"] for r in me["games"]] == ["I", "P"]
    assert me["tonight"] == [15.5, None, None, None, 22.5]
    r = me["games"][1]
    assert r["v"] == [30, 4, 6, 4, 40]
    assert r["diff"] == [14.5, None, None, None, 17.5]  # result minus TONIGHT's line
    assert r["line"] is None  # no line stored for that game: never filled
    full = X.vs_opp(p, slate, lines, tonight)["p"]["games"][-1]
    assert full["d"] == "2025-01-10" and full["t"] == "R" and full["s"] == 2025
    assert full["line"] == [18.5, None, None, None, 27.5]  # that night's own stored lines only
    assert full["diff"] == [4.5, None, None, None, 6.5]
    assert g["q"]["games"][0]["diff"] is None  # no line tonight at all


# --------------------------------------------------------------------------- (f) position v lines
def test_position_against_opponent_lines():
    p = _played([("a", "GS", "g1", "2026-10-26", "REG", 10, 2, 2, 1),
                 ("b", "GS", "g1", "2026-10-26", "REG", 20, 3, 5, 3),
                 ("c", "GS", "g2", "2026-10-28", "REG", 30, 4, 6, 5),
                 ("d", "GS", "g2", "2026-10-28", "REG", 9, 9, 9, 9)])  # no line stored
    p.loc[p.player_id == "c", "pos"] = "F"
    lines = pd.DataFrame({"game_id": ["g1", "g1", "g2", "g1"],
                          "player_id": ["a", "b", "c", "a"],
                          "market": ["pra", "pra", "pra", "pts"],
                          "line": [20.5, 25.5, 35.5, 12.5], "books": 4, "snapshot": "s"})
    t = X.pos_v_opp_table(X.lined_results(lines, p))
    g = t["GS|G"]["2027"]
    assert g["player_games"] == 2  # d had no line: not counted
    assert g["markets"]["pra"] == {"n": 2, "result": 21.0, "line": 23.0, "diff": -2.0, "over": 1,
                                   "under": 1}
    assert g["markets"]["pts"] == {"n": 1, "result": 10.0, "line": 12.5, "diff": -2.5, "over": 0,
                                   "under": 1}
    assert t["GS|F"]["2027"]["markets"]["pra"]["diff"] == 4.5
    assert "reb" not in g["markets"]  # no reb line stored: nothing shown, nothing filled


# --------------------------------------------------------------------------- (d) injuries
def test_injury_report_structured_fields_only():
    inj = pd.DataFrame([
        {"as_of_date": "2026-11-01", "athlete_id": 4701232, "status": "Out",
         "detail_type": "Shoulder", "detail_location": "Arm", "detail_detail": "Surgery",
         "detail_side": "Left", "detail_return_date": "2027-02-03",
         "short_comment": "SECRET COMMENT", "long_comment": "SECRET COMMENT"},
        {"as_of_date": "2026-11-01", "athlete_id": 1, "status": "Day-To-Day",
         "detail_type": "Rest", "detail_location": "Other", "detail_detail": "Not Specified",
         "detail_side": "Not Specified", "detail_return_date": None, "short_comment": "x",
         "long_comment": "x"},
        {"as_of_date": "2026-10-31", "athlete_id": 2, "status": "Out", "detail_type": "Knee",
         "detail_location": "Leg", "detail_detail": None, "detail_side": None,
         "detail_return_date": "2026-11-20", "short_comment": "", "long_comment": ""}])
    r = X.injury_reports(inj, "2026-11-01")
    assert set(r) == {"4701232", "1"}  # one day of the snapshot only
    assert r["4701232"] == {"status": "Out", "type": "Shoulder", "location": "Arm",
                            "detail": "Surgery", "side": "Left", "return_date": "2027-02-03",
                            "as_of_date": "2026-11-01", "label": "Shoulder (left), surgery"}
    assert r["1"]["detail"] is None and r["1"]["side"] is None and r["1"]["label"] == "Rest"
    assert "SECRET" not in json.dumps(r)
    entry = {"status": "Out", "fantasy": "OUT"}
    out = X.with_report(entry, r["1"])
    assert out["report"] == r["1"] and "report" not in entry  # the edition is never changed
    assert X.with_report(None, r["1"]) is None


def test_edition_injuries_read_the_snapshot_file_and_day_the_edition_used(tmp_path,
                                                                          monkeypatch):
    monkeypatch.setattr(X, "DATA_HOME", tmp_path)
    con = db.connect(tmp_path / "t.sqlite3")
    base = {"athlete_id": 9, "status": "Out", "detail_type": "Knee", "detail_location": "Leg",
            "detail_detail": "Sprain", "detail_side": "Right", "detail_return_date": "2026-11-20"}
    files = {"used": [{**base, "as_of_date": "2026-11-01"}, {**base, "as_of_date": "2026-10-31",
                                                             "detail_side": "Left"}],
             "newer": [{**base, "as_of_date": "2026-11-02", "detail_detail": "Surgery"}]}
    for i, (name, rows) in enumerate(files.items()):
        pd.DataFrame(rows).to_parquet(tmp_path / f"{name}.parquet")
        con.execute("INSERT INTO raw_files(source, dataset, part, url, retrieved_at, sha256, bytes,"
                    " path) VALUES ('sportsdataverse','nba_injuries','2027','u',?,?,1,?)",
                    (f"2026-11-0{i + 1}T12:00:00Z", name, f"{name}.parquet"))
    e = {"date_et": "2026-11-02", "inputs": {"injuries": {"sha256": "used"},
                                             "injury_snapshot": "2026-11-01"}}
    r = X.edition_injuries(con, e)["9"]
    assert (r["detail"], r["side"], r["as_of_date"]) == ("Sprain", "Right", "2026-11-01")
    old = X.edition_injuries(con, {"date_et": "2026-11-02", "inputs": {}})["9"]  # no record
    assert old["detail"] == "Surgery"  # the newest file and its newest day on or before the slate


# --------------------------------------------------------------------------- stored lines
def _quote(gid, pid, book, line, sel, price, obs):
    return {"game_id": gid, "player_id": pid, "market": "pts", "bookmaker": book, "line": line,
            "selection": sel, "decimal_price": price, "observed_at": obs}


def test_live_lines_take_the_last_collection_before_tip():
    q = []
    for b in ("fanduel", "draftkings", "betmgm"):
        q += [_quote("g1", "p", b, 20.5, "over", 1.9, "2026-10-30T12:00:00Z"),
              _quote("g1", "p", b, 20.5, "under", 1.9, "2026-10-30T12:00:00Z"),
              _quote("g1", "p", b, 22.5, "over", 1.9, "2026-10-30T22:00:00Z"),
              _quote("g1", "p", b, 22.5, "under", 1.9, "2026-10-30T22:00:00Z"),
              _quote("g1", "p", b, 30.5, "over", 1.9, "2026-10-31T03:00:00Z"),  # in play
              _quote("g1", "p", b, 30.5, "under", 1.9, "2026-10-31T03:00:00Z")]
    q += [_quote("g9", "p", "fanduel", 9.5, "over", 1.9, "2026-11-04T12:00:00Z")]  # unplayed
    tips = pd.Series([pd.Timestamp("2026-10-31T02:00:00Z")], index=["g1"])
    lines = X.live_lines(pd.DataFrame(q), tips)
    assert len(lines) == 1
    r = lines.iloc[0]
    assert (r.game_id, r.player_id, r.market, r.line, r.books) == ("g1", "p", "pts", 22.5, 3)
    assert r.snapshot == "2026-10-30T22:00:00Z"


def test_prop_lines_prefer_the_snapshot_nearest_tip(tmp_path, monkeypatch):
    con = db.connect(tmp_path / "t.sqlite3")
    monkeypatch.setattr(X, "HIST_LINES", tmp_path / "hist_lines.parquet")
    games = pd.DataFrame({"game_id": ["g1"], "tip_utc": [pd.Timestamp("2026-10-31T02:00:00Z")]})
    assert X.prop_lines(con, games, quotes=pd.DataFrame(
        columns=["player_id", "game_id", "market", "observed_at"])).empty  # nothing stored
    con.executescript("CREATE TABLE hist_quotes (id INTEGER PRIMARY KEY, sport TEXT, season INT,"
                      " game_id TEXT, provider_event_id TEXT, snapshot TEXT, requested_at TEXT,"
                      " bookmaker TEXT, player_name_raw TEXT, player_id TEXT, link_method TEXT,"
                      " market TEXT, alternate INT, line REAL, selection TEXT,"
                      " decimal_price REAL, quote_time TEXT, raw_file_id INT)")
    for b in ("fanduel", "betmgm"):
        for sel in ("over", "under"):
            con.execute("INSERT INTO hist_quotes(sport, game_id, snapshot, bookmaker, player_id,"
                        " market, alternate, line, selection, decimal_price) VALUES "
                        "('nba','g1','2026-10-30T23:00:00Z',?, 'p','pts',0,19.5,?,1.9)", (b, sel))
    live = pd.DataFrame([_quote("g1", "p", b, 21.5, s, 1.9, "2026-10-31T01:30:00Z")
                         for b in ("fanduel", "draftkings") for s in ("over", "under")])
    lines = X.prop_lines(con, games, quotes=live)
    assert lines.line.tolist() == [21.5]  # the later snapshot wins
    assert (tmp_path / "hist_lines.parquet").exists()  # the historical consensus is cached
    assert X.prop_lines(con, games, quotes=live.iloc[:0]).line.tolist() == [19.5]


# --------------------------------------------------------------------------- assembly
def _league():
    """Four teams; PHX v GS tonight (2026-11-02). Box scores from earlier games only."""
    games, pg, tg = [], [], []
    sched = [("g1", "2026-10-24", "PHX", "GS", 110, 108), ("g2", "2026-10-26", "LAL", "GS", 100, 120),
             ("g3", "2026-10-28", "PHX", "LAL", 99, 90), ("g4", "2026-10-30", "BOS", "PHX", 101, 100),
             ("g5", "2026-10-31", "GS", "BOS", 95, 97), ("g6", "2026-10-31", "LAL", "BOS", 90, 80)]
    roster = {"PHX": [("jg", "G"), ("pc", "C")], "GS": [("sc", "G"), ("dg", "F")],
              "LAL": [("lj", "F"), ("lg", "G")], "BOS": [("bt", "F"), ("bg", "G")]}
    for gid, d, h, a, hs, as_ in sched:
        tip = pd.Timestamp(d, tz="UTC") + pd.Timedelta(hours=23)
        games.append({"game_id": gid, "season": 2027, "season_type": "REG",
                      "date": pd.Timestamp(d), "tip_utc": tip, "home": h, "away": a,
                      "home_score": float(hs), "away_score": float(as_), "is_final": True})
        for team, opp, home in ((h, a, True), (a, h, False)):
            for k, (pid, pos) in enumerate(roster[team]):
                pg.append({"game_id": gid, "season": 2027, "season_type": "REG",
                           "date": pd.Timestamp(d), "tip_utc": tip, "player_id": pid,
                           "name": pid, "team": team, "opp": opp, "is_home": home,
                           "starter": True, "pos": pos, "participation": "played",
                           "minutes": 30.0 - 10 * k, "pts": 20.0 - 5 * k, "reb": 5.0, "ast": 4.0,
                           "fg3m": 2.0, "fga": 15.0 - 5 * k, "fta": 4.0, "tov": 2.0})
            tg.append({"game_id": gid, "team": team, "minutes": 240.0, "fga": 85.0,
                       "fta": 20.0, "tov": 14.0})
    return {"games": pd.DataFrame(games), "player_games": pd.DataFrame(pg),
            "team_games": pd.DataFrame(tg)}


def _edition():
    fc = [{"game_id": "g7", "player_id": pid, "name": pid, "team": t, "opponent": o,
           "position": pos, "model": "N1", "market": m, "injury": None}
          for pid, t, o, pos in (("jg", "PHX", "GS", "G"), ("sc", "GS", "PHX", "G"))
          for m in ("pts", "reb", "ast", "fg3m", "pra")]
    return {"date_et": "2026-11-02", "headline_model": "N1", "forecasts": fc, "inputs": {}}


def test_build_assembles_the_block_and_leaves_missing_data_missing(tmp_path, monkeypatch):
    con = db.connect(tmp_path / "t.sqlite3")  # no play-by-play, team box, lines or game lines
    monkeypatch.setattr(X, "HIST_LINES", tmp_path / "hist_lines.parquet")
    L = _league()
    mkt = {("jg", "pts"): {"line": 18.5}, ("jg", "pra"): {"line": 27.5}}
    ex = X.build(_edition(), mkt, L, con)
    assert ex["stats"] == ["pts", "reb", "ast", "fg3m", "pra"]
    assert ex["seasons"]["current"] == 2027 and ex["seasons"]["last_label"] == "2025-26"
    jg = ex["players"]["jg"]
    assert jg["volume"]["season"]["games"] == 3 and jg["volume"]["season"]["minutes"] == 30.0
    assert jg["volume"]["season"]["usage"] == pytest.approx(
        X.usage_rate(45, 12, 6, 90, 255, 60, 42, 720), abs=1e-4)
    assert jg["volume"]["last_season"] is None  # no games last season: missing, not zero
    assert jg["script"]["q4"] == {}  # no play-by-play stored
    assert jg["script"]["close"]["2027"]["close"]["games"] == 2  # margins 2 and 1
    assert jg["script"]["close"]["2027"]["other"]["games"] == 1  # margin 9
    grid = jg["vs_opp"]
    assert grid["opp"] == "GS" and grid["n"] == 1 and grid["tonight"][0] == 18.5
    assert grid["games"][0]["diff"] == [1.5, None, None, None, 1.5]  # 20 - 18.5, 29 - 27.5
    assert grid["games"][0]["line"] is None
    assert ex["defence"] == {} and ex["game_lines"] == {} and ex["pos_v_opp"] == {}
    assert ex["players"]["sc"]["vs_opp"]["tonight"] == [None] * 5
    json.dumps(ex)  # plain JSON types throughout


def test_player_view_and_snapshot_trim():
    ex = {"stats": list(X.STATS), "players": {"jg": {"volume": 1, "script": 2, "vs_opp": 3},
                                              "zz": {"volume": 0}},
          "defence": {"GS": {"games": 7}, "LAL": {}}, "pos_v_opp": {"GS|G": {"x": 1}, "LAL|F": {}},
          "game_lines": {"g7": {"total": 220.5}, "g8": {}}, "league": {}, "seasons": {},
          "notes": {}}
    card = {"player_id": "jg", "opponent": "GS", "position": "G", "game_id": "g7",
            "team": "PHX", "injury": None}
    v = X.player_view(ex, card)
    assert v["volume"] == 1 and v["defence"] == {"games": 7} and v["pos_v_opp"] == {"x": 1}
    assert v["game_lines"] == {"total": 220.5} and v["opponent"] == "GS"
    assert X.player_view(ex, {**card, "player_id": "nobody"}) == {}
    cut = snapshot._nba_extras(ex, [card])
    assert set(cut["players"]) == {"jg"} and set(cut["defence"]) == {"GS"}
    assert set(cut["pos_v_opp"]) == {"GS|G"} and set(cut["game_lines"]) == {"g7"}
    assert ex["players"].keys() == {"jg", "zz"}  # the cached block is not changed


def test_league_games_leave_out_exhibition_sides():
    L = _league()
    g = pd.concat([L["games"], pd.DataFrame([{**L["games"].iloc[0].to_dict(), "game_id": "asg",
                                              "home": "STARS", "away": "WORLD"}])])
    assert "asg" not in X.league_games(g) and "g1" in X.league_games(g)
