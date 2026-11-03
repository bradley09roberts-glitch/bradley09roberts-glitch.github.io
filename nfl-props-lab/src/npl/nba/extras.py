"""Research context for NBA prop cards beyond the projection (parity build). Context only:
nothing here feeds a forecast (D-017, D-023, D-039 apply), and every figure comes from data we
hold: SportsDataverse box scores, team box scores, play-by-play and daily injury snapshots
(CC BY 4.0), and the US bookmaker lines we stored (a research reference no UK customer can
take). A missing figure or line stays missing; nothing is filled in.

  volume     minutes a game this regular season and in his last 5 games this season (any
             type); field-goal attempts, shot volume (FGA + 0.44 x FTA) and usage, this season,
             last 5 and last season
  usage      (FGA + 0.44 FTA + TOV) x (team minutes / 5) / (minutes x team (FGA + 0.44 FTA +
             TOV)), each term summed over his games, as a fraction. The team's FGA, FTA and
             minutes are the sums of its player box lines; its TOV is the team box score's total,
             team turnovers (credited to no player) included. A game without that total counts
             for minutes only
  script     share of his regular-season points scored in fourth quarters, of all his points
             (overtime counted in the total and shown separately), v the league's, from
             play-by-play; PRA and minutes in games decided by 5 points or fewer (final score,
             after any overtime) v the rest
  defence    the opponent's assists allowed a game and defensive rebound % = DREB / (DREB +
             opponent OREB) this regular season, from the team box scores; rank 1 = fewest
             assists allowed / highest DREB% (as the app's other ranks: 1 = allows least)
  injury     the snapshot's structured fields (never its comment text)
  vs_opp     every game he played against tonight's opponent (regular season, play-in and
             playoffs), newest first, up to 10: each stat, the stat minus tonight's consensus
             line, and that night's own stored consensus line
  pos_v_opp  players of his position group (G/F/C) in the opponent's games that have a stored
             line: mean result minus line per stat, with the number of player-games
  game lines median spread and total across regulated books, best regulated moneyline per
             side, implied team points ((total -/+ spread) / 2), books, when seen, and the move
             since the first pull; each board the one a pull saw, from the pull log
             (books.pull_board)

Stored lines are the npl.books consensus of the snapshot nearest before tip-off: hist_quotes
(one snapshot 3 hours before tip) for 2024-25 and the 2025-26 sample, quotes (the board the last
pull before tip saw for each game and market, from the pull log) for 2026-27. Per-stat arrays
follow STATS.

Displayed figures are rounded half up (halves away from zero: 2.25 -> 2.3, -2.25 -> -2.3), not
with Python's round(), which rounds the binary value half to even (round(2.25, 1) = 2.2).
"""

from __future__ import annotations

import json
import sqlite3
from decimal import ROUND_HALF_UP, Decimal

import numpy as np
import pandas as pd

from .. import books
from ..config import DATA_HOME
from ..util import iso, utcnow
from . import canon, source
from . import model as M

STATS = ("pts", "reb", "ast", "fg3m", "pra")  # order of every per-stat array in the payload
CLOSE = 5  # a close game is decided by this many points or fewer
GRID_CAP = 10  # games shown against tonight's opponent
STYPE = {"REG": "R", "PLAYIN": "I", "POST": "P"}
KEYS = ["game_id", "player_id", "market"]
LINE_COLS = KEYS + ["line", "books", "snapshot"]
HIST_LINES = canon.NBA_LAKE / "prop_lines_hist.parquet"  # derived from hist_quotes; data/ only
LINES_VERSION = 1
UNSPECIFIED = {"", "nan", "none", "not specified", "unknown"}


def vs_opp_note(shown: int | None = None) -> str:
    """The grid's note with the cap actually used: GRID_CAP games, or `shown` of them in a copy
    that keeps fewer (the offline snapshot)."""
    cap = f"up to {GRID_CAP}" + (f"; {shown} in the offline copy" if shown is not None else "")
    return (f"Every game he played against tonight's opponent, newest first ({cap}). Diff = "
            "result minus tonight's consensus line; line = that night's stored consensus line. "
            "Blank: no line stored.")


NOTES = {
    "volume": "Regular-season averages; last 5 = his last 5 games this season. Shot volume = "
              "FGA + 0.44 x FTA. Usage = share of his team's plays he ends with a shot, free "
              "throws or a turnover while on court, estimated from box scores; the team's plays "
              "include its team turnovers (credited to no player).",
    "script": "Fourth-quarter share = his fourth-quarter points / all his points, regular "
              "season, from play-by-play; overtime is counted in the total and shown "
              "separately. Close games: final margin 5 points or fewer.",
    "defence": "This regular season, from team box scores. DREB% = defensive rebounds / "
               "(defensive rebounds + opponents' offensive rebounds). Rank 1 = fewest assists "
               "allowed or highest DREB%.",
    "injury": "From the SportsDataverse injury snapshot: body part or reason, area, detail, "
              "side and the return date it expects.",
    "vs_opp": vs_opp_note(),
    "pos_v_opp": "Opponents' players in his position group, in this team's games with a stored "
                 "line: average result minus the consensus line. Small samples are noise.",
    "game_lines": "Median spread and total across regulated US books, best moneyline per side; "
                  "implied points = (total -/+ spread) / 2. US prices: research reference only, "
                  "not available to UK customers.",
}

_MEMO: dict = {}


def _memo(name: str, key, fn):
    """The latest result per name until its key changes (key None: no caching)."""
    if key is None:
        return fn()
    hit = _MEMO.get(name)
    if hit is not None and hit[0] == key:
        return hit[1]
    val = fn()
    _MEMO[name] = (key, val)
    return val


def half_up(x: float, n: int = 1) -> float:
    """x to n decimals, halves away from zero, as written (the shortest decimal form of the
    float, so 2.675 -> 2.68 where round() gives 2.67); never -0.0."""
    q = Decimal(repr(float(x))).quantize(Decimal(1).scaleb(-n), rounding=ROUND_HALF_UP)
    return float(q) + 0.0


def _r(x, n: int = 1):
    return None if x is None or pd.isna(x) else half_up(x, n)


def _minus(a: float | None, b: float | None) -> float | None:
    """a - b exactly in decimal (both already rounded for display), so the result is what the
    two shown figures give."""
    return None if a is None or b is None else float(Decimal(repr(a)) - Decimal(repr(b))) + 0.0


def season_of(date_et: str) -> int:
    """Seasons are named by their ending year (2027 = 2026-27); August onwards is the next."""
    d = pd.Timestamp(date_et)
    return d.year + 1 if d.month >= 8 else d.year


def season_label(season: int) -> str:
    return f"{season - 1}-{str(season)[2:]}"


# --------------------------------------------------------------------------- box-score frames
def league_games(games: pd.DataFrame) -> set:
    """Games between teams with a normal schedule that season (at least half the median number
    of games): leaves out All-Star and other exhibition games, which the lake keeps for the
    current season."""
    t = pd.concat([games[["season", "game_id", "home"]].rename(columns={"home": "team"}),
                   games[["season", "game_id", "away"]].rename(columns={"away": "team"})])
    n = t.groupby(["season", "team"]).game_id.nunique()
    ok = set(n[n >= 0.5 * n.groupby(level=0).transform("median")].index)
    keep = [(s, h) in ok and (s, a) in ok
            for s, h, a in zip(games.season, games.home, games.away, strict=True)]
    return set(games.game_id[keep])


def played_frame(L: dict) -> pd.DataFrame:
    """Played player-games between league teams with PRA, his team's minutes, FGA and FTA in
    that game (t_*, summed from the player rows) and the final margin (absolute, NaN when the
    game is not final). The team's turnovers come from the team box score (with_team_tov)."""
    pg, tg, games = L["player_games"], L["team_games"], L["games"]
    p = pg[(pg.participation == "played") & pg.game_id.isin(league_games(games))].copy()
    p["pra"] = p.pts + p.reb + p.ast
    tcols = ["minutes", "fga", "fta"]
    t = tg[["game_id", "team", *tcols]].rename(columns={c: f"t_{c}" for c in tcols})
    p = p.merge(t, on=["game_id", "team"], how="left")
    fin = games[games.is_final.astype(bool)]
    margin = pd.Series((fin.home_score - fin.away_score).abs().to_numpy(), index=fin.game_id)
    p["margin"] = p.game_id.map(margin)
    return p


# --------------------------------------------------------------------------- (a) volume, usage
def usage_rate(fga, fta, tov, minutes, t_fga, t_fta, t_tov, t_minutes) -> float | None:
    """Usage as a fraction from totals over a set of games:
    (FGA + 0.44 FTA + TOV) x (team minutes / 5) / (minutes x team (FGA + 0.44 FTA + TOV))."""
    team = t_fga + 0.44 * t_fta + t_tov
    if pd.isna(minutes) or pd.isna(team) or minutes <= 0 or team <= 0:
        return None
    return float((fga + 0.44 * fta + tov) * (t_minutes / 5) / (minutes * team))


_SUMS = ["fga", "fta", "tov", "minutes", "t_fga", "t_fta", "t_tov", "t_minutes"]


def volume_table(g: pd.DataFrame) -> pd.DataFrame:
    """Per player over the played games in `g`: games and minutes a game, FGA, shot volume
    (FGA + 0.44 FTA) a game and usage. A game missing a box-score value counts for minutes
    only."""
    cols = ["games", "minutes", "fga", "shot_volume", "usage"]
    if g.empty:
        return pd.DataFrame(columns=cols)
    a = g.groupby("player_id").agg(games=("game_id", "size"), minutes=("minutes", "mean"))
    c = g.dropna(subset=_SUMS)
    s = c.groupby("player_id").agg(n=("game_id", "size"), **{k: (k, "sum") for k in _SUMS})
    s["usage"] = [usage_rate(*r) for r in s[_SUMS].itertuples(index=False)]
    s["fga_pg"] = s.fga / s.n
    s["shot_volume"] = (s.fga + 0.44 * s.fta) / s.n
    out = a.join(s[["fga_pg", "shot_volume", "usage"]], how="left")
    return out.rename(columns={"fga_pg": "fga"})[cols]


def _vol_block(t: pd.DataFrame, pid: str) -> dict | None:
    if pid not in t.index:
        return None
    r = t.loc[pid]
    return {"games": int(r.games), "minutes": _r(r.minutes), "fga": _r(r.fga),
            "shot_volume": _r(r.shot_volume), "usage": _r(r.usage, 4)}


def with_team_tov(p: pd.DataFrame, tov: pd.DataFrame | None) -> pd.DataFrame:
    """`p` with t_tov = his team's total turnovers in that game from the team box score (players'
    and team turnovers; team_turnovers); NaN where the team box has no figure."""
    p = p.drop(columns="t_tov", errors="ignore")
    if tov is None or tov.empty:
        return p.assign(t_tov=np.nan)
    return p.merge(tov[["game_id", "team", "t_tov"]], on=["game_id", "team"], how="left")


def volume(p: pd.DataFrame, pids: set, cur: int, tov: pd.DataFrame | None = None) -> dict:
    """{pid: {season, last5, last_season}}; `p` is played_frame cut to games before tonight.
    `tov`: the team box's total turnovers per (game_id, team), for usage; without it `p` must
    already carry t_tov."""
    p = p[p.player_id.isin(pids)]
    if tov is not None or "t_tov" not in p:
        p = with_team_tov(p, tov)
    reg = p[p.season_type == "REG"]
    this = p[p.season == cur].sort_values("tip_utc")
    t_cur = volume_table(reg[reg.season == cur])
    t_l5 = volume_table(this.groupby("player_id").tail(5))
    t_last = volume_table(reg[reg.season == cur - 1])
    return {pid: {"season": _vol_block(t_cur, pid), "last5": _vol_block(t_l5, pid),
                  "last_season": _vol_block(t_last, pid)} for pid in pids}


# --------------------------------------------------------------------------- (b) game script
_PBP_COLS = ["game_id", "season_type", "period_number", "scoring_play", "score_value",
             "athlete_id_1"]


def _pbp_files(con: sqlite3.Connection, seasons) -> tuple:
    rows = con.execute("SELECT part, path FROM raw_files WHERE source=? AND dataset='nba_pbp' "
                       "AND is_synthetic=0 ORDER BY retrieved_at, id", (source.SOURCE,)).fetchall()
    latest = {r[0]: r[1] for r in rows}
    return tuple((s, latest[str(s)]) for s in seasons if str(s) in latest)


def period_points(pbp: pd.DataFrame, season: int) -> pd.DataFrame:
    """Points per (season, game, player, period) from one season's regular-season scoring
    plays (the scorer is athlete 1)."""
    p = pbp[(pbp.season_type == 2) & pbp.scoring_play.fillna(False).astype(bool) &
            (pbp.score_value > 0) & pbp.athlete_id_1.notna()]
    d = pd.DataFrame({"season": season, "game_id": p.game_id.astype("int64").astype(str),
                      "player_id": p.athlete_id_1.astype("int64").astype(str),
                      "period": p.period_number.astype(int), "points": p.score_value.astype(int)})
    return d.groupby(["season", "game_id", "player_id", "period"], as_index=False).points.sum()


def _period_points(files: tuple) -> pd.DataFrame:
    parts = [period_points(pd.read_parquet(DATA_HOME / path, columns=_PBP_COLS), s)
             for s, path in files]
    if not parts:
        return pd.DataFrame(columns=["season", "game_id", "player_id", "period", "points"])
    return pd.concat(parts, ignore_index=True)


def period_shares(pp: pd.DataFrame, by: list[str]) -> pd.DataFrame:
    """Points, fourth-quarter and overtime (periods 5+) points and their shares of all points,
    per `by` (e.g. season and player, or season for the league)."""
    x = pp.assign(q4=pp.points.where(pp.period == 4, 0), ot=pp.points.where(pp.period >= 5, 0))
    a = x.groupby(by).agg(points=("points", "sum"), q4_points=("q4", "sum"),
                          ot_points=("ot", "sum"))
    tot = a.points.where(a.points > 0)
    a["q4"], a["ot"] = a.q4_points / tot, a.ot_points / tot
    return a


def close_table(g: pd.DataFrame, margin: int = CLOSE) -> dict:
    """{(player_id, season): {close: {games, pra, minutes}, other: {...}}}: PRA and minutes a
    game in games decided by `margin` points or fewer (final score, after any overtime) and in
    the rest, with game counts; games with no final score are left out."""
    g = g[g.margin.notna()]
    out: dict = {}
    if g.empty:
        return out
    a = g.assign(kind=(g.margin <= margin).map({True: "close", False: "other"})).groupby(
        ["player_id", "season", "kind"]).agg(games=("game_id", "size"), pra=("pra", "mean"),
                                             minutes=("minutes", "mean"))
    none = {"games": 0, "pra": None, "minutes": None}
    for (pid, s, kind), r in zip(a.index, a.to_dict("records"), strict=True):
        e = out.setdefault((pid, int(s)), {"close": dict(none), "other": dict(none)})
        e[kind] = {"games": int(r["games"]), "pra": _r(r["pra"]), "minutes": _r(r["minutes"])}
    return out


def close_split(g: pd.DataFrame, margin: int = CLOSE) -> dict:
    """The close-game split of one player's games (see close_table)."""
    t = close_table(g.assign(player_id="_", season=0), margin)
    return t.get(("_", 0), {k: {"games": 0, "pra": None, "minutes": None}
                            for k in ("close", "other")})


def script(p: pd.DataFrame, pp: pd.DataFrame, pids: set, cur: int) -> tuple[dict, dict]:
    """({pid: {q4: {season: ...}, close: {season: ...}}}, league shares by season). `p` and
    `pp` are cut to league games before tonight; both use the regular season only."""
    seasons = (cur, cur - 1)
    sh = period_shares(pp[pp.player_id.isin(pids)], ["season", "player_id"])
    lg = period_shares(pp, ["season"])
    league = {str(s): {"q4": _r(lg.q4[s], 4), "ot": _r(lg.ot[s], 4), "points": int(lg.points[s])}
              for s in seasons if s in lg.index}
    q4 = {}
    for (s, pid), r in zip(sh.index, sh.to_dict("records"), strict=True):
        q4.setdefault(pid, {})[str(int(s))] = {"points": int(r["points"]), "q4": _r(r["q4"], 4),
                                               "ot": _r(r["ot"], 4)}
    reg = p[(p.season_type == "REG") & p.player_id.isin(pids) & p.season.isin(seasons)]
    ct = close_table(reg[["player_id", "season", "game_id", "margin", "pra", "minutes"]])
    out = {}
    for pid in pids:
        close = {str(s): ct[(pid, s)] for s in seasons if (pid, s) in ct}
        out[pid] = {"q4": q4.get(pid, {}), "close": close}
    return out, league


# --------------------------------------------------------------------------- (c) defence
def _team_box(con: sqlite3.Connection, season: int) -> pd.DataFrame | None:
    """One season's team box scores (the newest stored file), one row per team and game."""
    r = con.execute("SELECT path FROM raw_files WHERE source=? AND dataset='nba_team_box' AND "
                    "part=? AND is_synthetic=0 ORDER BY retrieved_at DESC, id DESC LIMIT 1",
                    (source.SOURCE, str(season))).fetchone()
    if r is None:
        return None

    def read():
        b = pd.read_parquet(DATA_HOME / r[0], columns=[
            "game_id", "season_type", "game_date", "team_abbreviation",
            "opponent_team_abbreviation", "assists", "defensive_rebounds", "offensive_rebounds",
            "total_turnovers"])
        return pd.DataFrame({"game_id": b.game_id.astype("int64").astype(str),
                             "season_type": b.season_type, "date": pd.to_datetime(b.game_date),
                             "team": b.team_abbreviation, "opp": b.opponent_team_abbreviation,
                             "ast": b.assists, "dreb": b.defensive_rebounds,
                             "oreb": b.offensive_rebounds,
                             "tov_total": pd.to_numeric(b.total_turnovers, errors="coerce")})

    return _memo(f"team_box:{season}", r[0], read)


def team_turnovers(con: sqlite3.Connection, seasons) -> pd.DataFrame:
    """(game_id, team, t_tov): each team's total turnovers per game from the team box scores of
    these seasons (every season type), team turnovers credited to no player included."""
    parts = [b[["game_id", "team", "tov_total"]].rename(columns={"tov_total": "t_tov"})
             for b in (_team_box(con, s) for s in seasons) if b is not None]
    if not parts:
        return pd.DataFrame(columns=["game_id", "team", "t_tov"])
    return pd.concat(parts, ignore_index=True).drop_duplicates(["game_id", "team"])


def dreb_pct(dreb: float, opp_oreb: float) -> float | None:
    """Defensive rebound % = DREB / (DREB + opponent OREB), as a fraction."""
    d = dreb + opp_oreb
    return None if pd.isna(d) or d <= 0 else float(dreb / d)


def defence_table(tb: pd.DataFrame) -> pd.DataFrame:
    """tb: one row per team and game (game_id, team, opp, ast, dreb, oreb). Per team: games,
    assists allowed a game, DREB% and their ranks (1 = fewest assists allowed / highest DREB%)
    among teams with at least half the median number of games (no exhibition sides)."""
    cols = ["games", "ast_allowed", "ast_rank", "dreb_pct", "dreb_rank", "of"]
    o = tb[["game_id", "team", "ast", "oreb"]].rename(columns={"team": "opp", "ast": "opp_ast",
                                                               "oreb": "opp_oreb"})
    m = tb.merge(o, on=["game_id", "opp"])
    if m.empty:
        return pd.DataFrame(columns=cols)
    a = m.groupby("team").agg(games=("game_id", "nunique"), opp_ast=("opp_ast", "sum"),
                              dreb=("dreb", "sum"), opp_oreb=("opp_oreb", "sum"))
    a = a[a.games >= 0.5 * a.games.median()].copy()
    a["ast_allowed"] = a.opp_ast / a.games
    a["dreb_pct"] = [dreb_pct(d, o) for d, o in zip(a.dreb, a.opp_oreb, strict=True)]
    a["ast_rank"] = a.ast_allowed.rank(method="min").astype(int)
    a["dreb_rank"] = a.dreb_pct.rank(ascending=False, method="min").astype(int)
    a["of"] = len(a)
    return a[cols]


def defence(tb: pd.DataFrame | None, season: int, cutoff: pd.Timestamp, ok: set) -> dict:
    """{team: {...}} for one regular season before `cutoff`, league games only."""
    if tb is None:
        return {}
    tb = tb[(tb.season_type == 2) & (tb.date < cutoff) & tb.game_id.isin(ok)]
    t = defence_table(tb)
    return {team: {"season": season, "games": int(r.games), "ast_allowed": _r(r.ast_allowed),
                   "ast_rank": int(r.ast_rank), "dreb_pct": _r(r.dreb_pct, 4),
                   "dreb_rank": int(r.dreb_rank), "of": int(r.of)} for team, r in t.iterrows()}


# --------------------------------------------------------------------------- (d) injuries
def _spec(v):
    return None if v is None or pd.isna(v) or str(v).strip().lower() in UNSPECIFIED else str(v)


def injury_report(r: dict) -> dict:
    """Structured fields of one injury-snapshot row (never the comment text). `type` is the
    body part or reason (Shoulder, Rest), `location` the area (Arm, Leg, Torso), `detail`
    e.g. Surgery or Sprain, `return_date` the date the source expects him back."""
    rep = {"status": _spec(r.get("status")), "type": _spec(r.get("detail_type")),
           "location": _spec(r.get("detail_location")), "detail": _spec(r.get("detail_detail")),
           "side": _spec(r.get("detail_side")), "return_date": _spec(r.get("detail_return_date")),
           "as_of_date": _spec(r.get("as_of_date"))}
    if rep["return_date"]:
        rep["return_date"] = rep["return_date"][:10]
    if rep["as_of_date"]:
        rep["as_of_date"] = rep["as_of_date"][:10]
    words = [rep["type"]] if rep["type"] else []
    if rep["side"] and words:
        words[0] += f" ({rep['side'].lower()})"
    if rep["detail"]:
        words.append(rep["detail"].lower() if words else rep["detail"])
    rep["label"] = ", ".join(words) or None
    return rep


def injury_reports(inj: pd.DataFrame, day: str | None) -> dict:
    """{player_id: report} for one day of the snapshot (the newest day when `day` is None)."""
    if inj is None or inj.empty:
        return {}
    d = inj.as_of_date.astype(str).str[:10]
    day = day[:10] if day else d.max()
    i = inj[d == day].drop_duplicates("athlete_id")
    return {str(int(r["athlete_id"])): injury_report(r) for r in i.to_dict("records")}


def edition_injuries(con: sqlite3.Connection, e: dict) -> dict:
    """Injury reports from the snapshot file and day the edition used (its recorded SHA-256 and
    snapshot date); the newest stored file and its last day on or before the slate otherwise."""
    inputs = e.get("inputs") or {}
    meta = inputs.get("injuries") or {}
    day = inputs.get("injury_snapshot") or meta.get("as_of_date")
    r = None
    if meta.get("sha256"):
        r = con.execute("SELECT path FROM raw_files WHERE source=? AND dataset='nba_injuries' AND "
                        "sha256=? AND is_synthetic=0 ORDER BY id DESC LIMIT 1",
                        (source.SOURCE, meta["sha256"])).fetchone()
    if r is None:
        r = con.execute("SELECT path FROM raw_files WHERE source=? AND dataset='nba_injuries' AND "
                        "is_synthetic=0 ORDER BY retrieved_at DESC, id DESC LIMIT 1",
                        (source.SOURCE,)).fetchone()
    if r is None:
        return {}

    def read():
        inj = pd.read_parquet(DATA_HOME / r[0])
        if day is None:
            inj = inj[inj.as_of_date.astype(str).str[:10] <= e["date_et"]]
        return injury_reports(inj, day)

    return _memo("injuries", (r[0], day, e["date_et"]), read)


def with_report(injury: dict | None, report: dict | None) -> dict | None:
    """A copy of an edition's injury entry with the snapshot's structured fields under
    `report` (None when the snapshot does not list him); the edition itself is never changed."""
    if injury is None:
        return None
    return {**injury, "report": report}


# --------------------------------------------------------------------------- stored lines
def consensus_by_game(q: pd.DataFrame) -> pd.DataFrame:
    """Consensus line (npl.books rules) per (game, player, market) from quote rows holding one
    snapshot per game and market; `snapshot` is when it was taken."""
    q = q[q.player_id.notna() & q.game_id.notna() & q.line.notna() & q.market.isin(M.MARKETS)]
    if q.empty:
        return pd.DataFrame(columns=LINE_COLS)
    q = q.assign(game_id=q.game_id.astype(str), player_id=q.player_id.astype(str))
    cf = books.consensus_frame(books.pairs(q, keys=tuple(KEYS)), keys=tuple(KEYS))
    snap = q.groupby(KEYS).snapshot.max().rename("snapshot").reset_index()
    return cf[KEYS + ["line", "books"]].merge(snap, on=KEYS, how="left")[LINE_COLS]


def _before_tip(p: pd.DataFrame | None, q: pd.DataFrame, event: str) -> pd.DataFrame | None:
    """The pulls of q's events taken before their game's tip-off (q carries `tip`)."""
    if p is None or p.empty or event not in q:
        return None
    tip = q[[event, "tip"]].drop_duplicates(event).assign(**{event: lambda d: d[event].astype(str)})
    p = p.merge(tip.rename(columns={event: "event_id"}), on="event_id")
    return p[pd.to_datetime(p.observed_at, utc=True, format="ISO8601") < p.tip].drop(
        columns="tip")


def live_lines(q: pd.DataFrame, tips: pd.Series, pulls: pd.DataFrame | None = None
               ) -> pd.DataFrame:
    """2026-27 lines from stored quotes: per game and market, the board the last pull before tip
    saw (`tips`: game_id -> tip-off; `pulls`: the pull log, books.pull_board), or without a pull
    log the board at the last collection before tip rebuilt per bookmaker
    (books.complete_board). Games not in the box scores yet are skipped."""
    q = q[q.game_id.notna() & q.market.isin(M.MARKETS)]
    if q.empty:
        return pd.DataFrame(columns=LINE_COLS)
    q = q.assign(game_id=q.game_id.astype(str),
                 obs=pd.to_datetime(q.observed_at, utc=True, format="ISO8601"))
    q = q.assign(tip=q.game_id.map(tips))
    q = q[q.tip.notna() & (q.obs < q.tip)]
    q, _ = books.pull_board(q, _before_tip(pulls, q, "provider_event_id"),
                            keys=("game_id", "market"))
    q = q[q.player_id.notna()]
    return consensus_by_game(q.assign(snapshot=q.board_at))


def _hist_key(con: sqlite3.Connection):
    try:
        n, top = con.execute("SELECT COUNT(*), MAX(id) FROM hist_quotes WHERE sport='nba'"
                             ).fetchone()
    except sqlite3.OperationalError:  # no historical lines bought on this store
        return None
    return [LINES_VERSION, int(n), top]


def hist_lines(con: sqlite3.Connection) -> pd.DataFrame:
    """2024-25 and 2025-26 lines from hist_quotes (main lines only), computed once and kept on
    disk under data/ until the table changes."""
    key = _hist_key(con)
    if key is None or key[1] == 0:
        return pd.DataFrame(columns=LINE_COLS)
    meta = HIST_LINES.with_suffix(".json")

    def build():
        try:
            if json.loads(meta.read_text()).get("key") == key:
                return pd.read_parquet(HIST_LINES)
        except (OSError, ValueError):
            pass
        h = pd.read_sql_query(
            "SELECT game_id, player_id, market, bookmaker, line, selection, decimal_price, "
            "snapshot FROM hist_quotes WHERE sport='nba' AND alternate=0 AND player_id IS NOT "
            "NULL AND line IS NOT NULL", con)
        lines = consensus_by_game(h)
        HIST_LINES.parent.mkdir(parents=True, exist_ok=True)
        lines.to_parquet(HIST_LINES, index=False)
        meta.write_text(json.dumps({"key": key, "built_at": iso(utcnow()), "rows": len(lines)}))
        return lines

    return _memo("hist_lines", (str(HIST_LINES), *key), build)


def prop_lines(con: sqlite3.Connection, games: pd.DataFrame, quotes: pd.DataFrame | None = None,
               key=None) -> pd.DataFrame:
    """Every stored consensus line, one per (game, player, market): the snapshot nearest
    before tip when both tables hold the game."""
    def build():
        from ..sources import odds_api

        q, pulls = quotes, None
        if q is None:
            try:
                q = odds_api.quotes_frame(con)
            except Exception:  # noqa: BLE001 - no quotes table: no live lines
                q = pd.DataFrame(columns=["player_id", "game_id", "market"])
            pulls = odds_api.pulls_frame(con, "event_odds", "nba")
        tips = games.assign(game_id=games.game_id.astype(str),
                            tip_utc=pd.to_datetime(games.tip_utc, utc=True)).set_index(
            "game_id").tip_utc
        parts = [x for x in (hist_lines(con), live_lines(q, tips, pulls)) if len(x)]
        if not parts:
            return pd.DataFrame(columns=LINE_COLS)
        d = pd.concat(parts, ignore_index=True).sort_values("snapshot", kind="stable")
        return d.drop_duplicates(KEYS, keep="last").reset_index(drop=True)

    return _memo("prop_lines", key, build)


# --------------------------------------------------------------------------- (e) v opponent
def lines_wide(lines: pd.DataFrame) -> pd.DataFrame:
    """(game_id, player_id) -> one column per stat with that night's line (NaN when none)."""
    if lines.empty:
        return pd.DataFrame(columns=["game_id", "player_id", *STATS])
    w = lines.pivot_table(index=["game_id", "player_id"], columns="market", values="line",
                          aggfunc="first")
    return w.reindex(columns=list(STATS)).reset_index()


def grid_row(r: dict, tonight: list) -> dict:
    """One game against tonight's opponent: d date, s season, t type (R regular season, I
    play-in, P playoffs), tm his team then, h home (1/0), min minutes, v his stats, diff each
    stat minus tonight's consensus line, line that night's stored consensus line. Arrays follow
    STATS with None where a line is missing; diff or line is None when no stat has one."""
    vals = [None if pd.isna(r[s]) else float(r[s]) for s in STATS]
    diff = [None if t is None or v is None else half_up(v - t, 1)
            for v, t in zip(vals, tonight, strict=True)]
    line = [_r(r.get(f"line_{s}")) for s in STATS]
    return {"d": pd.Timestamp(r["date"]).strftime("%Y-%m-%d"), "s": int(r["season"]),
            "t": STYPE.get(r["season_type"], "R"), "tm": r["team"], "h": int(bool(r["is_home"])),
            "min": None if pd.isna(r["minutes"]) else int(half_up(r["minutes"], 0)),
            "v": [None if v is None else int(v) for v in vals],
            "diff": diff if any(x is not None for x in diff) else None,
            "line": line if any(x is not None for x in line) else None}


_GRID_COLS = ["player_id", "opp", "game_id", "tip_utc", "date", "season", "season_type", "team",
              "is_home", "minutes", *STATS]


def vs_opp(p: pd.DataFrame, slate: pd.DataFrame, lines: pd.DataFrame, tonight: dict,
           cap: int = GRID_CAP) -> dict:
    """{pid: {opp, tonight, n, games}}: each slate player's played games against tonight's
    opponent in our box scores (n of them), newest first, up to `cap` (rows: grid_row).
    `tonight`: {(pid, stat): tonight's consensus line, or None}."""
    pairs = slate[["player_id", "opp"]].drop_duplicates("player_id")
    g = p[_GRID_COLS].merge(pairs, on=["player_id", "opp"])
    n = g.groupby("player_id").size()
    g = g.sort_values(["player_id", "tip_utc"], ascending=[True, False]).groupby(
        "player_id").head(cap)
    w = lines_wide(lines).rename(columns={s: f"line_{s}" for s in STATS})
    g = g.merge(w, on=["game_id", "player_id"], how="left")  # keeps the newest-first order
    rows: dict = {}
    for r in g.to_dict("records"):
        rows.setdefault(r["player_id"], []).append(r)
    out = {}
    for pid, opp in zip(pairs.player_id, pairs.opp, strict=True):
        t = [tonight.get((pid, s)) for s in STATS]
        out[pid] = {"opp": opp, "tonight": t, "n": int(n.get(pid, 0)),
                    "games": [grid_row(r, t) for r in rows.get(pid, [])]}
    return out


# --------------------------------------------------------------------------- (f) position v lines
def lined_results(lines: pd.DataFrame, p: pd.DataFrame) -> pd.DataFrame:
    """Stored lines joined to results of players who played (a void prop has no result)."""
    act = p.melt(id_vars=["game_id", "player_id", "season", "date", "team", "opp", "pos"],
                 value_vars=list(STATS), var_name="market", value_name="actual")
    d = lines[KEYS + ["line"]].merge(act, on=KEYS)
    return d[d.actual.notna() & d.line.notna()]


def pos_v_opp_table(d: pd.DataFrame) -> dict:
    """{"OPP|POS": {season: {player_games, markets: {stat: {n, result, line, diff, over,
    under}}}}} from lined results: players of each position group against each opponent.
    `result` and `line` are means rounded to one decimal; `diff` is the shown result minus the
    shown line, so it always equals what the two figures give."""
    out: dict = {}
    if d.empty:
        return out
    d = d.assign(diff=d.actual - d.line, pgk=d.game_id + "|" + d.player_id)
    pg_n = d.groupby(["opp", "pos", "season"]).pgk.nunique()
    a = d.groupby(["opp", "pos", "season", "market"]).agg(
        n=("line", "size"), result=("actual", "mean"), line=("line", "mean"),
        over=("diff", lambda s: int((s > 0).sum())), under=("diff", lambda s: int((s < 0).sum())))
    for (opp, pos, season, mk), r in a.iterrows():
        e = out.setdefault(f"{opp}|{pos}", {}).setdefault(
            str(int(season)), {"player_games": int(pg_n[(opp, pos, season)]), "markets": {}})
        res, line = _r(r.result), _r(r.line)
        e["markets"][mk] = {"n": int(r.n), "result": res, "line": line, "diff": _minus(res, line),
                            "over": int(r.over), "under": int(r.under)}
    return out


def pos_v_opp(lines: pd.DataFrame, p: pd.DataFrame, seasons, key=None) -> dict:
    """Cached per (opponent, position, season) for the given seasons."""
    return _memo("pos_v_opp", key, lambda: pos_v_opp_table(
        lined_results(lines, p[p.season.isin(seasons)])))


# --------------------------------------------------------------------------- (g) game lines
def implied_points(total: float | None, home_spread: float | None) -> tuple:
    """(home, away) implied points from the total and the home side's spread (negative when the
    home team is favoured): home = (total - spread) / 2, away = (total + spread) / 2."""
    if total is None or home_spread is None:
        return None, None
    return (total - home_spread) / 2, (total + home_spread) / 2


def board(x: pd.DataFrame) -> dict:
    """One observation's game lines for one game, regulated books only: median home spread and
    total, best moneyline price per side, books per market."""
    if x.empty:  # a pull that listed the game with no prices, or every book withdrew
        return {"home_spread": None, "total": None,
                "moneyline": {"home": None, "home_book": None, "away": None, "away_book": None},
                "books": {"spread": 0, "total": 0, "moneyline": 0}}
    r = x[x.bookmaker.map(books.regulated).astype(bool)]
    sp = r[r.market == "spread"]
    home = sp[sp.side == "home"].groupby("bookmaker").point.first()
    away = sp[sp.side == "away"].groupby("bookmaker").point.first()
    hs = home.combine_first(-away).dropna()  # a book listing only the away side
    tot = r[(r.market == "total") & r.side.isin(["over", "under"])].groupby(
        "bookmaker").point.first().dropna()
    ml = r[(r.market == "moneyline") & r.side.isin(["home", "away"])]
    out = {"home_spread": _r(hs.median(), 2) if len(hs) else None,
           "total": _r(tot.median(), 2) if len(tot) else None, "moneyline": {},
           "books": {"spread": int(len(hs)), "total": int(len(tot)),
                     "moneyline": int(ml.bookmaker.nunique())}}
    for side in ("home", "away"):
        s = ml[ml.side == side].sort_values(["price", "bookmaker"], ascending=[False, True])
        out["moneyline"][side] = float(s.price.iloc[0]) if len(s) else None
        out["moneyline"][f"{side}_book"] = s.bookmaker.iloc[0] if len(s) else None
    return out


def game_line_summary(x: pd.DataFrame, pulls: pd.DataFrame | None = None) -> dict:
    """Latest pre-game consensus for one game, the first one and the move between. With the pull
    log (`pulls`: the game's pre-game featured-markets pulls) each board is the one a pull saw
    (books.pull_board: a book whose lines did not change since an earlier pull counts with those
    rows, a book missing from the pull does not), `seen` and `first_seen` are the last and first
    pulls' times and `observations` counts the pulls, also those in which nothing changed.
    Without it, each stored observation's board rebuilt per bookmaker (books.complete_board, a
    book's rows dropped when more than 12 hours older)."""
    keys = ("game_id", "market")
    p = None
    if pulls is not None and len(pulls) and "event_id" in x:
        p = pulls[pulls.event_id.isin(set(x.event_id.astype(str)))]
    if p is not None and len(p):
        t = pd.to_datetime(p.observed_at, utc=True, format="ISO8601")
        obs = list(dict.fromkeys(p.observed_at.iloc[np.argsort(t.to_numpy(), kind="stable")]))
    else:
        p, obs = None, sorted(x.observed_at.unique())
    last = board(books.pull_board(x, p, keys, ref=obs[-1], event="event_id")[0])
    first = board(books.pull_board(x, p, keys, ref=obs[0], event="event_id")[0])
    hp, ap = implied_points(last["total"], last["home_spread"])

    def move(k):
        a, b = first[k], last[k]
        return None if len(obs) < 2 else _minus(b, a)  # of the shown figures

    return {"seen": obs[-1], "first_seen": obs[0], "observations": len(obs),
            "home_spread": last["home_spread"], "total": last["total"],
            "home_points": _r(hp), "away_points": _r(ap), "moneyline": last["moneyline"],
            "books": last["books"],
            "first": {"home_spread": first["home_spread"], "total": first["total"],
                      "moneyline": {s: first["moneyline"][s] for s in ("home", "away")}},
            "move": {"home_spread": move("home_spread"), "total": move("total")},
            "availability": "verified_uk" if (x.availability == "verified_uk").all()
            else "research_reference"}


def game_lines(g: pd.DataFrame, game_ids: set | None = None,
               pulls: pd.DataFrame | None = None) -> dict:
    """{game_id: summary} from stored pre-game game-line rows and their pre-game pulls (the pull
    log, or None); games never pulled are absent."""
    g = g[g.game_id.notna()]
    if game_ids is not None:
        g = g[g.game_id.astype(str).isin({str(i) for i in game_ids})]
    return {str(gid): game_line_summary(x, pulls) for gid, x in g.groupby("game_id")}


def stored_game_lines(con: sqlite3.Connection, game_ids: set) -> dict:
    from ..sources import odds_api

    try:
        g = odds_api.game_lines_frame(con, "nba", pregame=True)
    except Exception:  # noqa: BLE001 - no game_lines table: nothing pulled
        return {}
    return game_lines(g, game_ids, odds_api.pulls_frame(con, "featured_odds", "nba",
                                                        pregame=True))


# --------------------------------------------------------------------------- assembly
def slate_players(e: dict) -> pd.DataFrame:
    """One row per player in the edition: player_id, team, opp, pos, game_id."""
    f = [x for x in e["forecasts"] if x["model"] == e["headline_model"] and x["market"] == "pts"]
    return pd.DataFrame({"player_id": [x["player_id"] for x in f], "team": [x["team"] for x in f],
                         "opp": [x["opponent"] for x in f], "pos": [x["position"] for x in f],
                         "game_id": [x["game_id"] for x in f]}).drop_duplicates("player_id")


def _keys(con: sqlite3.Connection, lake_key) -> dict:
    """Cache keys: the lake, the play-by-play and team-box files, and the stored lines."""
    if lake_key is None:
        return {}
    try:
        q = tuple(con.execute("SELECT (SELECT MAX(id) FROM quotes), "
                              "(SELECT MAX(id) FROM quote_links), "
                              "(SELECT MAX(id) FROM odds_pulls)").fetchone())
    except sqlite3.OperationalError:
        q = None
    return {"lake": lake_key, "lines": (lake_key, q, tuple(_hist_key(con) or ()))}


def _seasons(cur: int) -> dict:
    return {"current": cur, "current_label": season_label(cur), "last": cur - 1,
            "last_label": season_label(cur - 1)}


def _base(con: sqlite3.Connection, L: dict, k: dict, cur: int, cutoff: pd.Timestamp):
    """Played games and regular-season period points before `cutoff`, and the league games
    before it (memoised on the lake and the play-by-play files)."""
    allp = _memo("played", k.get("lake"), lambda: played_frame(L))
    ok = set(L["games"].game_id[L["games"].date < cutoff]) & _memo(
        "league_games", k.get("lake"), lambda: league_games(L["games"]))
    files = _pbp_files(con, (cur, cur - 1))
    pp = _memo("period_points", files if k else None, lambda: _period_points(files))
    return allp[allp.date < cutoff], pp[pp.game_id.isin(ok)], ok


def build(e: dict, mkt: dict, L: dict, con: sqlite3.Connection, *, lake_key=None) -> dict:
    """The `extras` block for an edition's prop cards. `mkt`: tonight's consensus per
    (player_id, market), as on the cards; `lake_key` (the lake file's modification time)
    turns on caching. Everything is as of the start of the slate's US date."""
    k = _keys(con, lake_key)
    cur = season_of(e["date_et"])
    cutoff = pd.Timestamp(e["date_et"])
    sp = slate_players(e)
    pids = set(sp.player_id)
    p, pp, ok = _base(con, L, k, cur, cutoff)
    sc, league = script(p, pp, pids, cur)
    vol = volume(p, pids, cur, team_turnovers(con, (cur, cur - 1)))
    lines = prop_lines(con, L["games"], key=k.get("lines"))
    lines = lines[lines.game_id.isin(ok)]
    tonight = {(pid, s): (mkt.get((pid, s)) or {}).get("line") for pid in pids for s in STATS}
    grid = vs_opp(p, sp, lines, tonight)
    pvo_all = pos_v_opp(lines, p, (cur, cur - 1),
                        key=(k["lines"], e["date_et"]) if k else None)
    want = {f"{o}|{q}" for o, q in zip(sp.opp, sp.pos, strict=True)}
    tb = _team_box(con, cur)
    teams = set(sp.opp) | set(sp.team)
    dfn = defence(tb, cur, cutoff, ok)
    return {
        "stats": list(STATS),
        "seasons": _seasons(cur),
        "players": {pid: {"volume": vol[pid], "script": sc[pid], "vs_opp": grid[pid]}
                    for pid in sorted(pids)},
        "league": league,
        "defence": {t: v for t, v in dfn.items() if t in teams},
        "pos_v_opp": {kk: v for kk, v in pvo_all.items() if kk in want},
        "game_lines": stored_game_lines(con, set(sp.game_id)),
        "notes": NOTES,
    }


def player_view(ex: dict, card: dict) -> dict:
    """A slate player's page extras, taken from the props `extras` block and one of his cards:
    his volume, script and grid, his opponent's defence, his position against that opponent,
    his game's lines and his injury entry (the same figures as on his cards)."""
    me = (ex.get("players") or {}).get(card["player_id"])
    if me is None:
        return {}
    opp = card["opponent"]
    return {**me, "game_id": card["game_id"], "opponent": opp,
            "defence": (ex.get("defence") or {}).get(opp),
            "pos_v_opp": (ex.get("pos_v_opp") or {}).get(f"{opp}|{card['position']}"),
            "game_lines": (ex.get("game_lines") or {}).get(card["game_id"]),
            "injury": card.get("injury"), "league": ex.get("league"),
            "seasons": ex.get("seasons"), "stats": ex.get("stats"), "notes": ex.get("notes")}


def for_player(pid: str, L: dict, con: sqlite3.Connection, *, lake_key=None,
               date_et: str | None = None) -> dict:
    """Page extras for a player who is not on the latest slate: his volume and game script
    from every game in the lake before `date_et` (default: today's US date)."""
    if not (L["player_games"].player_id == pid).any():
        return {}
    date_et = date_et or pd.Timestamp(utcnow()).tz_convert("America/New_York").strftime(
        "%Y-%m-%d")
    cur = season_of(date_et)
    p, pp, _ = _base(con, L, _keys(con, lake_key), cur, pd.Timestamp(date_et))
    sc, league = script(p, pp, {pid}, cur)
    vol = volume(p, {pid}, cur, team_turnovers(con, (cur, cur - 1)))
    return {"volume": vol[pid], "script": sc[pid], "league": league,
            "seasons": _seasons(cur), "stats": list(STATS),
            "notes": {n: NOTES[n] for n in ("volume", "script")}}


def team_defence(con: sqlite3.Connection, L: dict, date_et: str, teams: set, *,
                 lake_key=None) -> dict:
    """Defence additions for some teams before a slate date (the game page)."""
    cur = season_of(date_et)
    cutoff = pd.Timestamp(date_et)
    ok = set(L["games"].game_id[L["games"].date < cutoff]) & _memo(
        "league_games", lake_key, lambda: league_games(L["games"]))
    d = defence(_team_box(con, cur), cur, cutoff, ok)
    return {t: d.get(t) for t in teams}
