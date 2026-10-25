"""Canonical tables built from the raw store (written to data/lake/*.parquet).

games         one row per game; season_type REG/POST (nflverse carries no preseason)
teams         franchise codes (relocations mapped: OAK->LV, SD->LAC, STL->LA)
players       GSIS-keyed directory; cross-ids recorded in player_id_map (audited)
membership    player x team x week from weekly rosters (trades, elevations, IR, inactive)
player_games  player x game with an explicit participation status and core stats
team_games    team x game offensive volume from play-by-play
plays         skill plays (per-play yards) for the simulation's empirical pools

Participation status is never inferred from a missing row:
  played            recorded a stat, or was on the field for at least one play
  inactive          game-day inactive (roster status INA), no stats
  dnp               active (ACT), on-field data present for the game, never on the field
  active_unverified active (ACT), no stat, and no on-field data to tell DNP from a zero
  reserve           reserve/practice-squad/cut status that week: not a game-day candidate
  not_final         game has no final score yet

On-field presence comes from nflverse participation (NGS 2016-2022; FTN 2023+, released
only after each season). It is used ONLY to label outcomes of past games, never as a
pre-game feature. Pro Football Reference snap counts are not used anywhere in the model
pipeline: Sports Reference's terms (section 5, May 2023) prohibit using its content to
support machine-learning prediction. See docs/DATA_AUDIT.md.
"""

from __future__ import annotations

import json
import sqlite3

import numpy as np
import pandas as pd

from . import db, rawstore
from .config import DATA_HOME, LAKE_DIR, NEW_YORK, SKILL_POSITIONS
from .sources.nflverse import SOURCE
from .util import iso, utcnow

FRANCHISE = {"OAK": "LV", "SD": "LAC", "STL": "LA", "LAR": "LA", "JAC": "JAX", "WSH": "WAS",
             "LVR": "LV", "GNB": "GB", "KAN": "KC", "NWE": "NE", "NOR": "NO", "SFO": "SF",
             "TAM": "TB", "RAI": "LV", "RAM": "LA", "SDG": "LAC", "CRD": "ARI", "HTX": "HOU",
             "CLT": "IND", "OTI": "TEN", "RAV": "BAL"}

# Betting-market columns that nflverse embeds in schedules/pbp. They are stored only in
# `schedule_lines` and must never reach an odds-free feature matrix.
MARKET_COLUMNS = {"away_moneyline", "home_moneyline", "spread_line", "away_spread_odds",
                  "home_spread_odds", "total_line", "under_odds", "over_odds", "vegas_wp",
                  "vegas_home_wp", "vegas_wpa", "vegas_home_wpa"}

STAT_COLS = ["attempts", "completions", "passing_yards", "passing_tds", "passing_interceptions",
             "sacks_suffered", "passing_air_yards", "carries", "rushing_yards", "rushing_tds",
             "targets", "receptions", "receiving_yards", "receiving_tds", "receiving_air_yards",
             "receiving_yards_after_catch", "fumbles_lost_total"]

POST_TYPES = {"WC", "DIV", "CON", "SB"}


def franchise(code):
    if code is None or (isinstance(code, float) and np.isnan(code)):
        return code
    return FRANCHISE.get(code, code)


class Inputs:
    """Latest raw file per dataset/part, recorded for the build manifest."""

    def __init__(self, con: sqlite3.Connection, as_of: str | None = None):
        self.con, self.as_of, self.used = con, as_of, {}

    def read(self, dataset: str, part: str, columns: list[str] | None = None) -> pd.DataFrame | None:
        row = rawstore.latest(self.con, SOURCE, dataset, part, self.as_of)
        if row is None:
            return None
        self.used[f"{dataset}/{part}"] = {"raw_file_id": row["id"], "sha256": row["sha256"],
                                          "retrieved_at": row["retrieved_at"]}
        if columns:
            import pyarrow.parquet as pq

            have = set(pq.ParquetFile(DATA_HOME / row["path"]).schema_arrow.names)
            columns = [c for c in columns if c in have]
        return pd.read_parquet(DATA_HOME / row["path"], columns=columns)

    def seasons(self, dataset: str, seasons, columns=None) -> pd.DataFrame:
        parts = [self.read(dataset, str(s), columns) for s in seasons]
        parts = [p for p in parts if p is not None and len(p)]
        return pd.concat(parts, ignore_index=True) if parts else pd.DataFrame()


def build_games(sched: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    g = sched.copy()
    g["season_type"] = np.where(g["game_type"].isin(POST_TYPES), "POST",
                                np.where(g["game_type"] == "REG", "REG", "PRE"))
    local = pd.to_datetime(g["gameday"] + " " + g["gametime"].fillna("13:00"),
                           format="%Y-%m-%d %H:%M")
    g["kickoff_utc"] = local.dt.tz_localize(NEW_YORK, ambiguous="NaT",
                                            nonexistent="NaT").dt.tz_convert("UTC")
    g["kickoff_time_known"] = g["gametime"].notna()
    g["is_final"] = g["home_score"].notna() & g["away_score"].notna()
    g["home_team"] = g["home_team"].map(franchise)
    g["away_team"] = g["away_team"].map(franchise)
    g["neutral_site"] = g["location"].eq("Neutral")
    keep = ["game_id", "season", "season_type", "game_type", "week", "gameday", "gametime",
            "kickoff_utc", "kickoff_time_known", "weekday", "home_team", "away_team",
            "home_score", "away_score", "overtime", "is_final", "neutral_site", "home_rest",
            "away_rest", "div_game", "roof", "surface", "stadium", "stadium_id",
            "home_coach", "away_coach", "home_qb_id", "away_qb_id", "pfr", "espn", "gsis",
            "old_game_id"]
    # temp/wind in schedules are observed game-day values (retrospective only): kept apart.
    retro = g[["game_id", "temp", "wind"]].rename(columns={"temp": "obs_temp_f",
                                                           "wind": "obs_wind_mph"})
    lines = g[["game_id"] + [c for c in g.columns if c in MARKET_COLUMNS]]
    return g[keep].merge(retro, on="game_id"), lines


def team_game_frame(games: pd.DataFrame) -> pd.DataFrame:
    h = games.assign(team=games.home_team, opponent=games.away_team, is_home=True)
    a = games.assign(team=games.away_team, opponent=games.home_team, is_home=False)
    tg = pd.concat([h, a], ignore_index=True)
    tg.loc[tg.neutral_site, "is_home"] = False
    tg["points_for"] = np.where(tg.team == tg.home_team, tg.home_score, tg.away_score)
    tg["points_against"] = np.where(tg.team == tg.home_team, tg.away_score, tg.home_score)
    tg["rest"] = np.where(tg.team == tg.home_team, tg.home_rest, tg.away_rest)
    return tg[["game_id", "season", "season_type", "week", "kickoff_utc", "team", "opponent",
               "is_home", "neutral_site", "is_final", "points_for", "points_against", "rest",
               "roof", "surface", "div_game"]]


PBP_COLS = ["game_id", "play_id", "posteam", "defteam", "season", "week", "season_type",
            "play_type", "pass_attempt", "rush_attempt", "sack", "qb_scramble", "qb_kneel",
            "qb_spike", "qb_dropback", "two_point_attempt", "complete_pass", "incomplete_pass",
            "interception", "passer_player_id", "receiver_player_id", "rusher_player_id",
            "passing_yards", "receiving_yards", "rushing_yards", "yards_gained", "air_yards",
            "yards_after_catch", "lateral_reception", "lateral_rush", "down", "qtr", "wp",
            "score_differential", "yardline_100", "play_deleted", "aborted_play", "penalty",
            "fumble_lost", "touchdown", "pass_touchdown", "rush_touchdown", "xpass", "shotgun",
            "no_huddle", "ydstogo", "game_seconds_remaining", "half_seconds_remaining"]


def build_plays(pbp: pd.DataFrame) -> pd.DataFrame:
    p = pbp[pbp["play_type"].isin(["pass", "run", "qb_kneel", "qb_spike"])].copy()
    p = p[p["two_point_attempt"].fillna(0) == 0]
    if "play_deleted" in p:
        p = p[p["play_deleted"].fillna(0) == 0]
    p["posteam"] = p["posteam"].map(franchise)
    p["defteam"] = p["defteam"].map(franchise)
    sack = p["sack"].fillna(0) == 1
    scr = p["qb_scramble"].fillna(0) == 1
    kn = p["qb_kneel"].fillna(0) == 1
    spike = p["qb_spike"].fillna(0) == 1
    is_pass = (p["play_type"] == "pass") & ~sack & ~scr
    kind = np.select(
        [sack, scr, kn, spike, is_pass & (p["complete_pass"] == 1), is_pass,
         p["play_type"] == "run"],
        ["sack", "scramble", "kneel", "spike", "completion", "incompletion", "rush"],
        default="other",
    )
    p["kind"] = kind
    neutral = p["wp"].between(0.2, 0.8) & p["down"].isin([1, 2]) & (p["qtr"] <= 3)
    p["neutral"] = neutral
    keep = ["game_id", "play_id", "season", "week", "season_type", "posteam", "defteam", "kind",
            "passer_player_id", "receiver_player_id", "rusher_player_id", "passing_yards",
            "receiving_yards", "rushing_yards", "yards_gained", "air_yards", "yards_after_catch",
            "interception", "neutral", "xpass", "yardline_100", "down", "qtr",
            "score_differential", "lateral_reception", "lateral_rush", "touchdown",
            "game_seconds_remaining"]
    return p[keep].reset_index(drop=True)


def build_team_games(tg: pd.DataFrame, plays: pd.DataFrame) -> pd.DataFrame:
    k = plays.assign(n=1)
    agg = k.pivot_table(index=["game_id", "posteam"], columns="kind", values="n", aggfunc="sum",
                        fill_value=0).reset_index().rename(columns={"posteam": "team"})
    for c in ["sack", "scramble", "kneel", "spike", "completion", "incompletion", "rush"]:
        if c not in agg:
            agg[c] = 0
    agg["pass_att"] = agg["completion"] + agg["incompletion"]  # spikes reconciled separately
    agg["dropbacks"] = agg["pass_att"] + agg["sack"] + agg["scramble"] + agg["spike"]
    agg["designed_rush"] = agg["rush"]
    agg["plays"] = agg["dropbacks"] + agg["designed_rush"] + agg["kneel"]
    nz = plays[plays.neutral & plays.kind.isin(["completion", "incompletion", "sack", "scramble",
                                                "rush"])]
    nz = nz.assign(isp=nz.kind != "rush").groupby(["game_id", "posteam"]).agg(
        neutral_plays=("isp", "size"), neutral_pass_rate=("isp", "mean"),
        neutral_xpass=("xpass", "mean")).reset_index().rename(columns={"posteam": "team"})
    out = tg.merge(agg, on=["game_id", "team"], how="left").merge(nz, on=["game_id", "team"],
                                                                     how="left")
    out["pbp_present"] = out["plays"].notna()
    return out


def build(con: sqlite3.Connection | None = None, as_of: str | None = None,
          seasons: list[int] | None = None) -> dict:
    con = con or db.connect()
    inp = Inputs(con, as_of)
    sched = inp.read("schedules", "all")
    games, lines = build_games(sched)
    seasons = seasons or sorted(games.season.unique().tolist())
    seasons = [s for s in seasons if s >= 2012]
    games = games[games.season.isin(seasons)].reset_index(drop=True)
    lines = lines[lines.game_id.isin(games.game_id)]
    tg = team_game_frame(games)

    players = inp.read("players", "all")
    teams = inp.read("teams", "all")

    pbp = inp.seasons("pbp", seasons, PBP_COLS)
    plays = build_plays(pbp)
    del pbp
    team_games = build_team_games(tg, plays)

    stats = inp.seasons("stats_player_week", seasons)
    stats["team"] = stats["team"].map(franchise)
    stats["opponent_team"] = stats["opponent_team"].map(franchise)
    rosters = inp.seasons("rosters_weekly", seasons)
    rosters["team"] = rosters["team"].map(franchise)
    onfield = build_onfield(inp, seasons)

    # ---- identities -------------------------------------------------------------------
    idmap = build_id_map(players, rosters)

    # ---- membership (player x team x week) ----------------------------------------------
    mem = rosters[["season", "week", "game_type", "team", "gsis_id", "full_name", "position",
                   "depth_chart_position", "status", "status_description_abbr", "birth_date",
                   "entry_year", "rookie_year", "years_exp", "jersey_number"]].rename(
        columns={"gsis_id": "player_id"})
    mem = mem[mem.player_id.notna()].drop_duplicates(["season", "week", "team", "player_id"])
    mem["jersey_number"] = pd.to_numeric(mem["jersey_number"], errors="coerce")
    mem["birth_date"] = mem["birth_date"].astype("string")

    # ---- player x game universe ---------------------------------------------------------
    tg_keys = tg[["game_id", "season", "week", "team", "opponent", "season_type", "is_final",
                  "kickoff_utc", "is_home"]]
    m = mem.merge(tg_keys, on=["season", "week", "team"], how="inner")  # drops bye weeks
    s = stats[["player_id", "game_id", "team", "position", "player_display_name"] + STAT_COLS]
    s = s.assign(has_stats=True)
    of_games = set(onfield["game_id"].unique())

    keys = pd.concat([m[["game_id", "team", "player_id"]], s[["game_id", "team", "player_id"]]]
                     ).drop_duplicates()
    pg = keys.merge(tg_keys, on=["game_id", "team"], how="inner")
    pg = pg.merge(m[["game_id", "team", "player_id", "status", "status_description_abbr",
                     "position", "depth_chart_position", "full_name", "years_exp", "entry_year",
                     "birth_date"]].rename(columns={"position": "roster_position"}),
                  on=["game_id", "team", "player_id"], how="left")
    pg = pg.merge(s.rename(columns={"position": "stats_position"}), on=["game_id", "team",
                                                                         "player_id"], how="left")
    pg = pg.merge(onfield, on=["game_id", "player_id"], how="left")
    pinfo = players.set_index("gsis_id")
    pg["position"] = pg["roster_position"].fillna(pg["stats_position"]).fillna(
        pg["player_id"].map(pinfo["position"]))
    pg["name"] = pg["full_name"].fillna(pg["player_display_name"]).fillna(
        pg["player_id"].map(pinfo["display_name"]))
    pg["has_stats"] = pg["has_stats"].eq(True)
    pg["onfield_present"] = pg["game_id"].isin(of_games)
    pg["onfield_plays"] = np.where(pg["onfield_present"], pg["onfield_plays"].fillna(0), np.nan)
    status = participation_status(pg["is_final"], pg["has_stats"], pg["onfield_plays"],
                                  pg["onfield_present"], pg["status"])
    pg["participation"] = status
    played = pg["participation"].eq("played")
    for c in STAT_COLS:
        # A player who played but has no stat row recorded a genuine zero; anyone who did
        # not play (or whose participation is unverified) has no value, never zero.
        pg[c] = np.where(played, pg[c].fillna(0), np.nan)
    pg["skill"] = pg["position"].isin(SKILL_POSITIONS)
    pg = pg.drop(columns=["full_name", "player_display_name", "stats_position"])

    # ---- write ------------------------------------------------------------------------
    LAKE_DIR.mkdir(parents=True, exist_ok=True)
    for df in (pg, players):
        for c in df.columns:
            if df[c].dtype == object:
                df[c] = df[c].astype("string")
    games.to_parquet(LAKE_DIR / "games.parquet", index=False)
    lines.to_parquet(LAKE_DIR / "schedule_lines.parquet", index=False)
    team_games.to_parquet(LAKE_DIR / "team_games.parquet", index=False)
    plays.to_parquet(LAKE_DIR / "plays.parquet", index=False)
    pg.to_parquet(LAKE_DIR / "player_games.parquet", index=False)
    mem.to_parquet(LAKE_DIR / "membership.parquet", index=False)
    players.to_parquet(LAKE_DIR / "players.parquet", index=False)
    teams.to_parquet(LAKE_DIR / "teams.parquet", index=False)
    idmap.to_parquet(LAKE_DIR / "player_id_map.parquet", index=False)
    write_id_map(con, idmap)
    build_depth_qb(inp, seasons).to_parquet(LAKE_DIR / "depth_qb.parquet", index=False)
    inj = inp.seasons("injuries", seasons)
    if len(inj):
        inj["team"] = inj["team"].map(franchise)
        inj.to_parquet(LAKE_DIR / "injuries.parquet", index=False)
    manifest = {"built_at": iso(utcnow()), "as_of": as_of, "seasons": seasons,
                "inputs": inp.used,
                "counts": {"games": len(games), "player_games": len(pg), "plays": len(plays),
                           "team_games": len(team_games)},
                "participation": pg.loc[pg.skill, "participation"].value_counts().to_dict()}
    (LAKE_DIR / "build_manifest.json").write_text(json.dumps(manifest, indent=1, default=str))
    return manifest


def participation_status(is_final, has_stats, onfield_plays, onfield_present,
                         roster_status) -> np.ndarray:
    """Vectorised participation label for player-games (see module docstring)."""
    is_final = pd.Series(is_final).astype(bool).to_numpy()
    has_stats = pd.Series(has_stats).astype(bool).to_numpy()
    on = pd.Series(onfield_plays).fillna(0).to_numpy() > 0
    present = pd.Series(onfield_present).astype(bool).to_numpy()
    rs = pd.Series(roster_status)
    reserve = (rs.notna() & ~rs.isin(["ACT", "INA"])).to_numpy()
    return np.select(
        [~is_final, has_stats | on, (rs == "INA").to_numpy(), (rs == "ACT").to_numpy() & present,
         (rs == "ACT").to_numpy(), reserve],
        ["not_final", "played", "inactive", "dnp", "active_unverified", "reserve"],
        default="unknown")


def build_depth_qb(inp: Inputs, seasons) -> pd.DataFrame:
    """QB depth-chart ranks. Up to 2024 one chart per week (publication time not recorded);
    from 2025 timestamped snapshots (`dt`), so the chart in force at a cutoff is known."""
    out = []
    for season in seasons:
        d = inp.read("depth_charts", str(season))
        if d is None or not len(d):
            continue
        if "dt" in d.columns:
            q = d[d["pos_abb"] == "QB"]
            out.append(pd.DataFrame({"season": season, "week": np.nan,
                                     "dt": pd.to_datetime(q["dt"], utc=True),
                                     "team": q["team"].map(franchise), "player_id": q["gsis_id"],
                                     "rank": pd.to_numeric(q["pos_rank"], errors="coerce")}))
        else:
            q = d[(d["position"] == "QB") & (d.get("formation", "Offense") == "Offense")]
            out.append(pd.DataFrame({"season": season, "week": q["week"].astype(float),
                                     "dt": pd.NaT, "team": q["club_code"].map(franchise),
                                     "player_id": q["gsis_id"],
                                     "rank": pd.to_numeric(q["depth_team"], errors="coerce")}))
    out = [o for o in out if len(o)]
    if not out:
        return pd.DataFrame(columns=["season", "week", "dt", "team", "player_id", "rank"])
    r = pd.concat(out, ignore_index=True)
    r["dt"] = pd.to_datetime(r["dt"], utc=True)
    return r.dropna(subset=["player_id", "rank"])


def build_onfield(inp: Inputs, seasons) -> pd.DataFrame:
    """Plays on the field per player-game, from participation data (outcome labels only)."""
    out = []
    for season in seasons:
        d = inp.read("participation", str(season),
                     ["nflverse_game_id", "play_id", "offense_players", "defense_players"])
        if d is None or not len(d):
            continue
        ids = (d["offense_players"].fillna("") + ";" + d["defense_players"].fillna(""))
        e = pd.DataFrame({"game_id": d["nflverse_game_id"], "pid": ids.str.split(";")}).explode(
            "pid")
        e = e[e["pid"].str.len() > 0]
        out.append(e.groupby(["game_id", "pid"]).size().rename("onfield_plays").reset_index())
    if not out:
        return pd.DataFrame(columns=["game_id", "player_id", "onfield_plays"])
    return pd.concat(out).rename(columns={"pid": "player_id"})


def build_id_map(players: pd.DataFrame, rosters: pd.DataFrame) -> pd.DataFrame:
    """Cross-provider ids, linked only through explicit id columns (never by name)."""
    rows = []
    for src in ["pfr_id", "espn_id", "pff_id", "nfl_id", "esb_id", "smart_id", "otc_id"]:
        if src not in players:
            continue
        d = players[["gsis_id", src]].dropna()
        d = d[d[src].astype(str).str.len() > 0]
        rows.append(pd.DataFrame({"player_id": d["gsis_id"], "source": src.replace("_id", ""),
                                  "source_id": d[src].astype(str),
                                  "method": "nflverse players directory cross-id"}))
    out = pd.concat(rows, ignore_index=True)
    # Roster files carry their own pfr/gsis pairs: add the ones the directory lacks, and
    # flag (do not merge) any pfr id the two sources attach to different players.
    rp = rosters[["gsis_id", "pfr_id"]].dropna().drop_duplicates()
    known = out[out.source == "pfr"].set_index("source_id")["player_id"]
    rp["dir"] = rp["pfr_id"].map(known)
    extra = rp[rp["dir"].isna()]
    if len(extra):
        out = pd.concat([out, pd.DataFrame({"player_id": extra.gsis_id, "source": "pfr",
                                            "source_id": extra.pfr_id,
                                            "method": "weekly roster cross-id"})])
    conflicts = rp[rp["dir"].notna() & (rp["dir"] != rp["gsis_id"])]
    out["conflict"] = False
    if len(conflicts):
        bad = set(conflicts.pfr_id)
        out.loc[(out.source == "pfr") & out.source_id.isin(bad), "conflict"] = True
    out = out.drop_duplicates(["source", "source_id"], keep="first")
    out.attrs["conflicts"] = conflicts.to_dict("records")
    return out.reset_index(drop=True)


def write_id_map(con: sqlite3.Connection, idmap: pd.DataFrame) -> None:
    now = iso(utcnow())
    with db.tx(con):
        con.executemany(
            "INSERT OR IGNORE INTO player_id_map(player_id, source, source_id, method, evidence,"
            " created_at) VALUES (?,?,?,?,?,?)",
            [(r.player_id, r.source, r.source_id, r.method,
              "CONFLICT: same source id on different players" if r.conflict else None, now)
             for r in idmap.itertuples()],
        )
