"""Point-in-time model inputs for one weekly slate.

`History` holds only rows strictly before the slate (season, week): every game of the
slate is predicted before any of its outcomes is used (weekly batch rule). Nothing from
the target games enters: no target-week stats, snaps, participation or inactive lists.

Estimates use exponentially weighted (EW) history with an extra decay at each season
boundary, then shrink towards a position or league prior with an explicit prior weight
in effective games. Small samples therefore stay close to the prior and carry wide
uncertainty instead of extrapolating the last few games.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import pandas as pd

from .config import LAKE_DIR

# --- hyper-parameters (frozen per model version; recorded in every manifest) -----------
HP = {
    "team_half_life": 8.0,       # team games
    "player_half_life": 6.0,     # player games
    "season_decay": 0.55,        # extra weight multiplier per season boundary
    "team_prior_games": 6.0,     # shrinkage weight of the league mean for team rates
    "share_prior_games": 3.0,    # shrinkage weight of the role prior for player shares
    "eff_prior_games": 8.0,      # shrinkage for efficiency (catch rate, per-play yards)
    "pool_max_plays": 80,        # most recent per-play observations kept per player
    "pool_prior_plays": 40.0,    # weight of the position pool when mixing per-play yards
    "play_prior_games": 3.0,     # shrinkage weight for participation probability
    "opp_prior_games": 10.0,     # shrinkage of opponent defensive factors
}

ROLE_POS = {"QB": "QB", "RB": "RB", "FB": "RB", "WR": "WR", "TE": "TE"}


def load_lake() -> dict[str, pd.DataFrame]:
    return {
        "games": pd.read_parquet(LAKE_DIR / "games.parquet"),
        "team_games": pd.read_parquet(LAKE_DIR / "team_games.parquet"),
        "player_games": pd.read_parquet(LAKE_DIR / "player_games.parquet"),
        "plays": pd.read_parquet(LAKE_DIR / "plays.parquet"),
        "membership": pd.read_parquet(LAKE_DIR / "membership.parquet"),
        "injuries": pd.read_parquet(LAKE_DIR / "injuries.parquet"),
        "players": pd.read_parquet(LAKE_DIR / "players.parquet"),
        "depth_qb": pd.read_parquet(LAKE_DIR / "depth_qb.parquet"),
    }


def slate_key(season: int, week: int) -> int:
    return season * 100 + week


@dataclass
class History:
    """Everything known before slate (season, week)."""

    season: int
    week: int
    tg: pd.DataFrame
    pg: pd.DataFrame
    plays: pd.DataFrame

    @classmethod
    def before(cls, lake: dict, season: int, week: int) -> History:
        k = slate_key(season, week)
        tg = lake["team_games"]
        tg = tg[(tg.season * 100 + tg.week < k) & tg.is_final & tg.pbp_present]
        pg = lake["player_games"]
        pg = pg[(pg.season * 100 + pg.week < k) & pg.is_final]
        pl = lake["plays"]
        pl = pl[pl.season * 100 + pl.week < k]
        return cls(season, week, tg, pg, pl)


def ew_weights(order: np.ndarray, seasons: np.ndarray, target_season: int, half_life: float,
               season_decay: float) -> np.ndarray:
    """order: 0 = most recent observation. Weight halves every `half_life` observations
    and is multiplied by `season_decay` per season boundary crossed."""
    return 0.5 ** (order / half_life) * season_decay ** (target_season - seasons)


def _ew_group(df: pd.DataFrame, key: str, cols: list[str], target_season: int,
              half_life: float, decay: float, sort_col: str = "kickoff_utc") -> pd.DataFrame:
    d = df.sort_values(sort_col, ascending=False).copy()
    d["_ord"] = d.groupby(key).cumcount()
    d["_w"] = ew_weights(d["_ord"].to_numpy(), d["season"].to_numpy(), target_season,
                         half_life, decay)
    out = {"_wsum": d.groupby(key)["_w"].sum(), "_n": d.groupby(key).size()}
    for c in cols:
        out[c] = (d[c] * d["_w"]).groupby(d[key]).sum() / out["_wsum"]
    return pd.DataFrame(out)


@dataclass
class Params:
    """Structural dispersion parameters estimated from training history only."""

    league: dict = field(default_factory=dict)
    resid_cov: np.ndarray | None = None  # [plays_h, plays_a, dbr_h, dbr_a] residual cov
    alpha_target: float = 25.0
    alpha_carry: float = 25.0
    q_qb_exit: float = 0.06
    p_qb_cameo: float = 0.05  # non-starting active QB takes the field without a real share
    kneel_mean: float = 0.77
    role_priors: dict = field(default_factory=dict)
    pools: dict = field(default_factory=dict)  # position -> per-play yard arrays
    inj_play: dict = field(default_factory=dict)  # (pos, report_status) -> P(played)
    band_priors: dict = field(default_factory=dict)  # (role, experience band) -> priors


def estimate_params(h: History, train_from: int = 2016) -> Params:
    tg = h.tg[h.tg.season >= train_from]
    tg = tg[tg.plays > 0]
    p = Params()
    t = tg.assign(dbr=tg.dropbacks / tg.plays)
    p.league = {
        "plays": float(t.plays.mean()), "dbr": float(t.dbr.mean()),
        "sack_rate": float(t.sack.sum() / t.dropbacks.sum()),
        "scr_rate": float(t.scramble.sum() / t.dropbacks.sum()),
        "notarget": None,
    }
    # residual covariance of (plays, dropback rate) for both teams of a game, after
    # removing each team-season mean (an estimate of within-season game-to-game noise).
    t["plays_r"] = t.plays - t.groupby(["season", "team"]).plays.transform("mean")
    t["dbr_r"] = t.dbr - t.groupby(["season", "team"]).dbr.transform("mean")
    t["home"] = t.groupby("game_id").cumcount()
    w = t.pivot_table(index="game_id", columns="home", values=["plays_r", "dbr_r"]).dropna()
    m = np.column_stack([w[("plays_r", 0)], w[("plays_r", 1)], w[("dbr_r", 0)], w[("dbr_r", 1)]])
    p.resid_cov = np.cov(m.T)
    p.kneel_mean = float(t.kneel.mean())

    pg = h.pg[(h.pg.season >= train_from) & (h.pg.participation == "played")]
    team_tot = pg.groupby(["game_id", "team"])[["targets", "carries"]].sum().rename(
        columns={"targets": "team_targets", "carries": "team_carries"})
    team_att = tg.set_index(["game_id", "team"])[["pass_att", "designed_rush", "kneel",
                                                   "scramble", "dropbacks"]]
    tot = team_tot.join(team_att, how="inner")
    p.league["notarget"] = float(1 - tot.team_targets.sum() / tot.pass_att.sum())
    pj = pg.join(team_att, on=["game_id", "team"], how="inner")
    pj = pj[pj.pass_att > 0]
    pj["tshare"] = pj.targets / pj.pass_att
    p.alpha_target = _alpha(pj, "tshare", "pass_att")
    pj = pj[pj.designed_rush > 0]
    # designed-rush share (QB carries include scrambles/kneels, modelled separately)
    pj["dr"] = np.where(pj.position == "QB", np.nan, pj.carries / pj.designed_rush)
    p.alpha_carry = _alpha(pj[pj.position != "QB"], "dr", "designed_rush")
    p.role_priors = _role_priors(pg, team_att)
    p.band_priors = _band_priors(h, team_att, train_from)
    p.pools = _pools(h.plays[h.plays.season >= train_from], h.pg)
    p.q_qb_exit = _qb_exit(h, train_from)
    p.p_qb_cameo = _qb_cameo(h, train_from)
    return p


def _alpha(d: pd.DataFrame, share: str, n: str) -> float:
    """Dirichlet-multinomial concentration by method of moments on regular players."""
    g = d.groupby(["season", "player_id"])
    s = g[share].mean()
    cnt = g.size()
    var = g[share].var()
    nbar = g[n].mean()
    keep = (cnt >= 8) & (s > 0.08)
    s, var, nbar = s[keep], var[keep], nbar[keep]
    excess = var - s * (1 - s) / nbar
    a = (s * (1 - s) / excess.clip(lower=1e-4) - 1).clip(2, 400)
    return float(np.median(a))


def _role_priors(pg: pd.DataFrame, team_att: pd.DataFrame) -> dict:
    """Share and efficiency priors by position (all player-games, played only)."""
    d = pg.join(team_att, on=["game_id", "team"], how="inner")
    d["role"] = d.position.map(ROLE_POS)
    out = {}
    for role, g in d.groupby("role"):
        out[role] = {
            "tshare": float((g.targets / g.pass_att.clip(lower=1)).median()),
            "tshare_mean": float((g.targets / g.pass_att.clip(lower=1)).mean()),
            "dshare": float((g.carries / g.designed_rush.clip(lower=1)).median()),
            "catch": float(g.receptions.sum() / max(g.targets.sum(), 1)),
        }
    return out


BANDS = ("0", "1-2", "3-5", "6+")


def exp_band(n_prior_played: float) -> str:
    n = 0 if n_prior_played is None or pd.isna(n_prior_played) else n_prior_played
    return "0" if n == 0 else ("1-2" if n <= 2 else ("3-5" if n <= 5 else "6+"))


def _band_priors(h: History, team_att: pd.DataFrame, train_from: int) -> dict:
    """Empirical-Bayes priors by role and experience band (number of games the player had
    played before). New and low-history players get the usage that such players actually
    received, not the usage of established players at their position."""
    pg = h.pg[h.pg.position.isin(ROLE_POS)].sort_values(["season", "week"]).copy()
    pg["role"] = pg.position.map(ROLE_POS)
    played = (pg.participation == "played").astype(int)
    pg["prior_played"] = played.groupby(pg.player_id).cumsum() - played
    pg["band"] = [exp_band(x) for x in pg.prior_played]
    pg = pg[pg.season >= train_from]
    dr = (h.plays[h.plays.kind == "rush"].groupby(["game_id", "rusher_player_id"]).size()
          .rename("dr_n"))
    pl = pg[pg.participation == "played"].join(team_att, on=["game_id", "team"], how="inner")
    pl = pl.join(dr, on=["game_id", "player_id"])
    pl["tshare"] = pl.targets / pl.pass_att.clip(lower=1)
    pl["dshare"] = pl.dr_n.fillna(0) / pl.designed_rush.clip(lower=1)
    cand = pg[pg.participation.isin(["played", "dnp", "inactive"])]
    out = {}
    for (role, band), g in pl.groupby(["role", "band"]):
        c = cand[(cand.role == role) & (cand.band == band)]
        out[(role, band)] = {"tshare": float(g.tshare.mean()), "dshare": float(g.dshare.mean()),
                             "p_play": float((c.participation == "played").mean())
                             if len(c) else None, "n": len(g)}
    return out


def _pools(plays: pd.DataFrame, pg: pd.DataFrame) -> dict:
    pos = pg.drop_duplicates("player_id").set_index("player_id")["position"].map(ROLE_POS)
    c = plays[plays.kind == "completion"]
    r = plays[plays.kind == "rush"]
    s = plays[plays.kind == "scramble"]
    out = {}
    for role in ("RB", "WR", "TE", "QB"):
        out[("rec", role)] = c.loc[c.receiver_player_id.map(pos) == role,
                                   "receiving_yards"].dropna().to_numpy()
        out[("rush", role)] = r.loc[r.rusher_player_id.map(pos) == role,
                                    "rushing_yards"].dropna().to_numpy()
    out[("scr", "QB")] = s["rushing_yards"].dropna().to_numpy()
    out[("kneel", "QB")] = plays.loc[plays.kind == "kneel", "rushing_yards"].dropna().to_numpy()
    return out


def _qb_exit(h: History, train_from: int) -> float:
    """P(the team's leading passer threw < 85% of the team's attempts)."""
    pg = h.pg[(h.pg.season >= train_from) & (h.pg.position == "QB") &
              (h.pg.participation == "played")]
    att = pg.groupby(["game_id", "team"]).attempts.agg(["sum", "max"])
    att = att[att["sum"] > 5]
    return float((att["max"] / att["sum"] < 0.85).mean())


def _qb_cameo(h: History, train_from: int) -> float:
    """P(an active non-leading QB is on the field) in games where the leading passer threw
    >= 85% of attempts (i.e. no early exit): holders, kneels, garbage time, gadget plays."""
    pg = h.pg[(h.pg.season >= train_from) & (h.pg.position == "QB") &
              h.pg.participation.isin(["played", "dnp"])]
    att = pg.groupby(["game_id", "team"]).attempts.agg(["sum", "max"])
    full = att[(att["sum"] > 5) & (att["max"] / att["sum"] >= 0.85)].index
    pg = pg.set_index(["game_id", "team"])
    pg = pg[pg.index.isin(full)].reset_index()
    lead = pg.groupby(["game_id", "team"]).attempts.transform("max")
    non = pg[pg.attempts.fillna(-1) != lead]
    return float((non.participation == "played").mean()) if len(non) else 0.05


# --------------------------------------------------------------------------------------
@dataclass
class PlayerIn:
    player_id: str
    name: str
    position: str
    role: str
    team: str
    p_play: float
    tshare: float
    dshare: float
    catch: float
    rec_pool: np.ndarray
    rush_pool: np.ndarray
    scr_rate: float = 0.0
    scr_pool: np.ndarray | None = None
    p_start: float = 0.0  # QB only: probability he starts
    games_hist: int = 0
    targets_hist: int = 0
    carries_hist: int = 0
    injury_status: str | None = None
    flags: list = field(default_factory=list)


@dataclass
class TeamIn:
    team: str
    opponent: str
    is_home: bool
    mu_plays: float
    dbr: float
    sack_rate: float
    notarget: float
    rec_factor: float  # opponent pass-defence yards factor
    catch_factor: float
    rush_factor: float
    players: list[PlayerIn]


@dataclass
class GameIn:
    game_id: str
    season: int
    week: int
    season_type: str
    kickoff_utc: pd.Timestamp
    home: TeamIn
    away: TeamIn


def _team_rates(h: History, P: Params) -> pd.DataFrame:
    t = h.tg[h.tg.plays > 0].copy()
    t["dbr"] = t.dropbacks / t.plays
    t["sack_rate"] = t.sack / t.dropbacks.clip(lower=1)
    off = _ew_group(t, "team", ["plays", "dbr", "sack_rate"], h.season, HP["team_half_life"],
                    HP["season_decay"])
    k = HP["team_prior_games"]
    lg = P.league
    for c, prior in [("plays", lg["plays"]), ("dbr", lg["dbr"]), ("sack_rate", lg["sack_rate"])]:
        off[c] = (off[c] * off["_wsum"] + prior * k) / (off["_wsum"] + k)
    # defence: what opponents did against each team
    d = t.rename(columns={"team": "off_team", "opponent": "team"})
    d["comp"] = d["completion"]
    deff = _ew_group(d, "team", ["plays", "dbr", "sack_rate"], h.season, HP["team_half_life"],
                     HP["season_decay"])
    k2 = HP["opp_prior_games"]
    for c, prior in [("plays", lg["plays"]), ("dbr", lg["dbr"]), ("sack_rate", lg["sack_rate"])]:
        deff[c] = (deff[c] * deff["_wsum"] + prior * k2) / (deff["_wsum"] + k2)
    return off.join(deff, rsuffix="_allowed", how="outer")


def _def_eff(h: History) -> pd.DataFrame:
    """Opponent efficiency factors (yards per completion, catch rate, yards per carry
    allowed) relative to league, EW and shrunk."""
    pl = h.plays
    g = pl[pl.kind.isin(["completion", "incompletion", "rush"])].copy()
    g["comp"] = (g.kind == "completion").astype(float)
    g["att"] = g.kind.isin(["completion", "incompletion"]).astype(float)
    g["recy"] = np.where(g.kind == "completion", g.receiving_yards, 0.0)
    g["rush"] = (g.kind == "rush").astype(float)
    g["rushy"] = np.where(g.kind == "rush", g.rushing_yards, 0.0)
    per = g.groupby(["game_id", "defteam", "season", "week"])[
        ["comp", "att", "recy", "rush", "rushy"]].sum().reset_index()
    per["kickoff_utc"] = per.season * 100 + per.week
    lg_ypc = per.recy.sum() / per.comp.sum()
    lg_cr = per.comp.sum() / per.att.sum()
    lg_ypr = per.rushy.sum() / per["rush"].sum()
    d = per.sort_values("kickoff_utc", ascending=False).copy()
    d["_ord"] = d.groupby("defteam").cumcount()
    d["_w"] = ew_weights(d._ord.to_numpy(), d.season.to_numpy(), h.season,
                         HP["team_half_life"], HP["season_decay"])
    agg = d.assign(**{c: d[c] * d._w for c in ["comp", "att", "recy", "rush", "rushy"]}).groupby(
        "defteam")[["comp", "att", "recy", "rush", "rushy", "_w"]].sum()
    k = HP["opp_prior_games"]
    n_comp_prior = k * per.comp.mean()
    n_att_prior = k * per.att.mean()
    n_rush_prior = k * per["rush"].mean()
    out = pd.DataFrame(index=agg.index)
    out["rec_factor"] = ((agg.recy + lg_ypc * n_comp_prior) / (agg.comp + n_comp_prior)) / lg_ypc
    out["catch_factor"] = ((agg.comp + lg_cr * n_att_prior) / (agg.att + n_att_prior)) / lg_cr
    out["rush_factor"] = ((agg.rushy + lg_ypr * n_rush_prior) / (agg["rush"] + n_rush_prior)
                          ) / lg_ypr
    return out


def _player_hist(h: History, P: Params) -> pd.DataFrame:
    pg = h.pg[h.pg.position.isin(ROLE_POS)].copy()
    tg = h.tg.set_index(["game_id", "team"])[["pass_att", "designed_rush", "dropbacks",
                                              "scramble"]]
    played = pg[pg.participation == "played"].join(tg, on=["game_id", "team"], how="inner")
    played["tshare"] = played.targets / played.pass_att.clip(lower=1)
    qb = played.position == "QB"
    # designed runs (excludes scrambles and kneels) per player-game, from play-by-play
    dr = (h.plays[h.plays.kind == "rush"].groupby(["game_id", "rusher_player_id"]).size()
          .rename("dr_n"))
    played = played.join(dr, on=["game_id", "player_id"])
    played["dshare"] = played["dr_n"].fillna(0) / played.designed_rush.clip(lower=1)
    played["dbshare"] = np.where(qb, (played.attempts + played.sacks_suffered) /
                                 (played.dropbacks - played.scramble).clip(lower=1), np.nan)
    ew = _ew_group(played, "player_id", ["tshare", "dbshare", "dshare"], h.season,
                   HP["player_half_life"], HP["season_decay"])
    tot = played.groupby("player_id")[["targets", "receptions", "carries"]].sum()
    ew = ew.join(tot)
    # participation: share of the player's team games (while on a game-day roster) played
    cand = pg[pg.participation.isin(["played", "dnp", "inactive", "active_unverified"])].copy()
    cand["played"] = (cand.participation == "played").astype(float)
    ewp = _ew_group(cand, "player_id", ["played"], h.season, HP["player_half_life"],
                    HP["season_decay"])
    ew = ew.join(ewp[["played", "_wsum"]].rename(columns={"_wsum": "_wsum_p"}), how="outer")
    return ew


def _qb_scrambles(h: History) -> pd.DataFrame:
    pl = h.plays[h.plays.kind.isin(["completion", "incompletion", "sack", "scramble"])]
    pid = np.where(pl.kind == "scramble", pl.rusher_player_id, pl.passer_player_id)
    d = pd.DataFrame({"pid": pid, "scr": (pl.kind == "scramble").astype(float)})
    g = d.groupby("pid").scr.agg(["sum", "size"])
    return g


def build_slate(lake: dict, season: int, week: int, *, variant: str = "J0",
                injuries_asof: pd.DataFrame | None = None, P: Params | None = None,
                h: History | None = None) -> tuple[list[GameIn], Params, History]:
    """Model inputs for every game of (season, week).

    variant J0: participation probability from history only (cutoff-faithful).
    variant J1: additionally conditions participation on the week's official injury
                report status (live: as observed before publication; backtest: the final
                weekly report, assumed published >= 24h before kickoff).
    """
    h = h or History.before(lake, season, week)
    # Parameters are fitted on 2016+ (participation-labelled seasons); slates in 2016 itself
    # have no earlier 2016+ rows, so they fall back to 2012-2015 history.
    P = P or estimate_params(h, train_from=2016 if season >= 2017 else 2012)
    games = lake["games"]
    slate = games[(games.season == season) & (games.week == week)]
    rates = _team_rates(h, P)
    deff = _def_eff(h)
    ph = _player_hist(h, P)
    scr = _qb_scrambles(h)
    mem = lake["membership"]
    mem = mem[(mem.season == season) & (mem.week == week)]
    pinfo = lake["players"].set_index("gsis_id")
    inj = injuries_asof
    if inj is None and variant == "J1":
        i = lake["injuries"]
        inj = i[(i.season == season) & (i.week == week)]
    inj_map = {} if inj is None else inj.set_index("gsis_id")["report_status"].to_dict()

    # per-player per-play pools (most recent first), from plays before the slate
    pl = h.plays
    comp = pl[pl.kind == "completion"].sort_values(["season", "week"], ascending=False)
    rush = pl[pl.kind == "rush"].sort_values(["season", "week"], ascending=False)
    scrp = pl[pl.kind == "scramble"].sort_values(["season", "week"], ascending=False)
    nmax = HP["pool_max_plays"]
    rec_pool = comp.groupby("receiver_player_id").receiving_yards.apply(
        lambda s: s.to_numpy()[:nmax])
    rush_pool = rush.groupby("rusher_player_id").rushing_yards.apply(
        lambda s: s.to_numpy()[:nmax])
    scr_pool = scrp.groupby("rusher_player_id").rushing_yards.apply(
        lambda s: s.to_numpy()[:nmax])

    out = []
    for g in slate.itertuples():
        teams = {}
        for side, team, opp in (("home", g.home_team, g.away_team), ("away", g.away_team,
                                                                     g.home_team)):
            r = rates.loc[team] if team in rates.index else None
            o = rates.loc[opp] if opp in rates.index else None
            lg = P.league
            # multiplicative combination of offence and opponent defence (both shrunk)
            mu_plays = lg["plays"] * (r.plays / lg["plays"] if r is not None else 1) * (
                o.plays_allowed / lg["plays"] if o is not None else 1)
            dbr = _logit_combine(lg["dbr"], r.dbr if r is not None else lg["dbr"],
                                 o.dbr_allowed if o is not None else lg["dbr"])
            sack = _logit_combine(lg["sack_rate"], r.sack_rate if r is not None else
                                  lg["sack_rate"], o.sack_rate_allowed if o is not None else
                                  lg["sack_rate"])
            de = deff.loc[opp] if opp in deff.index else None
            roster = mem[(mem.team == team) & mem.status.isin(["ACT", "INA"])]
            players = []
            for m in roster.itertuples():
                if m.position not in ROLE_POS:
                    continue
                players.append(_player_in(m, team, ph, P, rec_pool, rush_pool, scr_pool,
                                          scr, inj_map if variant == "J1" else {},
                                          pinfo))
            qb1 = depth_qb1(lake["depth_qb"], season, week, team,
                            g.kickoff_utc - pd.Timedelta(hours=24)) if variant == "J1" else None
            _qb_start_probs(players, h, team, qb1)
            teams[side] = TeamIn(team, opp, side == "home" and not g.neutral_site,
                                 float(mu_plays), float(dbr), float(sack),
                                 float(P.league["notarget"]),
                                 float(de.rec_factor) if de is not None else 1.0,
                                 float(de.catch_factor) if de is not None else 1.0,
                                 float(de.rush_factor) if de is not None else 1.0, players)
        out.append(GameIn(g.game_id, int(g.season), int(g.week), g.season_type,
                          g.kickoff_utc, teams["home"], teams["away"]))
    return out, P, h


def _logit(p):
    p = np.clip(p, 1e-4, 1 - 1e-4)
    return np.log(p / (1 - p))


def _sigmoid(x):
    return 1 / (1 + np.exp(-x))


def _logit_combine(lg, off, deff):
    return float(_sigmoid(_logit(off) + _logit(deff) - _logit(lg)))


# v0.4: official-report adjustment of P(plays), estimated on 2016-2021 skill players who
# played their previous game: played 0.1% when Out, 1.1% when Doubtful, 74.6% when
# Questionable vs 95.6% when not listed (ratio 0.78). (v0.1-v0.3 used guesses: caps of 2%,
# 25% and 80%.)
INJ_CAP = {"Out": 0.002, "Doubtful": 0.015}
INJ_RATIO = {"Questionable": 0.78}
INJ_DEFAULT = {**INJ_CAP, **INJ_RATIO}


def _player_in(m, team, ph, P: Params, rec_pool, rush_pool, scr_pool, scr, inj_map,
               pinfo) -> PlayerIn:
    role = ROLE_POS[m.position]
    rp = P.role_priors.get(role, {})
    pid = m.player_id
    has = pid in ph.index
    row = ph.loc[pid] if has else None
    flags = []
    wsum = float(row["_wsum"]) if has and pd.notna(row["_wsum"]) else 0.0
    k = HP["share_prior_games"]
    # Shrinkage target: the empirical usage of players at this position with the same
    # experience band (v0.2); falls back to the position median.
    # v0.3: experience-band priors only for players with fewer than 6 games played (where
    # v0.1 over-predicted); established players keep the position-median prior (exp02 showed
    # band means hurt them).
    n_prior = int(row["_n"]) if has and pd.notna(row["_n"]) else 0
    band = exp_band(n_prior)
    bp = P.band_priors.get((role, band), {}) if band != "6+" else {}
    prior_t = bp.get("tshare", rp.get("tshare", 0.02))
    prior_d = bp.get("dshare", rp.get("dshare", 0.02))
    if has and wsum > 0:
        tshare = (row.tshare * wsum + prior_t * k) / (wsum + k)
        dsh = row.dshare if pd.notna(row.dshare) else prior_d
        dshare = (dsh * wsum + prior_d * k) / (wsum + k)
    else:
        tshare, dshare = prior_t, prior_d
        flags.append("no_history")
    if wsum and wsum < 3:
        flags.append("thin_history")
    tg_n = int(row.targets) if has and pd.notna(row.targets) else 0
    rec_n = int(row.receptions) if has and pd.notna(row.receptions) else 0
    ke = HP["eff_prior_games"] * 3  # prior targets
    catch = (rec_n + rp.get("catch", 0.65) * ke) / (tg_n + ke)
    # participation probability
    wp = float(row["_wsum_p"]) if has and pd.notna(row.get("_wsum_p", np.nan)) else 0.0
    base_play = bp.get("p_play") or rp_play(role)
    pplay = ((row.played * wp if wp else 0) + base_play * HP["play_prior_games"]) / (
        wp + HP["play_prior_games"])
    status = inj_map.get(pid)
    status = status if isinstance(status, str) and status else None  # practice-only rows
    if status in INJ_CAP:
        pplay = min(pplay, INJ_CAP[status])
        flags.append(f"injury_report:{status}")
    elif status in INJ_RATIO:
        pplay = pplay * INJ_RATIO[status]
        flags.append(f"injury_report:{status}")
    if role == "QB":
        sc = scr.loc[pid] if pid in scr.index else None
        n_db = float(sc["size"]) if sc is not None else 0.0
        scr_rate = ((sc["sum"] if sc is not None else 0) + P.league["scr_rate"] * 60) / (n_db + 60)
    else:
        scr_rate = 0.0
    return PlayerIn(
        player_id=pid, name=str(m.full_name), position=m.position, role=role, team=team,
        p_play=float(np.clip(pplay, 0.0, 0.995)), tshare=float(max(tshare, 1e-4)),
        dshare=float(max(dshare, 0.0)), catch=float(np.clip(catch, 0.2, 0.95)),
        rec_pool=rec_pool.get(pid, np.array([])), rush_pool=rush_pool.get(pid, np.array([])),
        scr_rate=float(scr_rate), scr_pool=scr_pool.get(pid, np.array([])) if role == "QB"
        else None, games_hist=int(row["_n"]) if has and pd.notna(row["_n"]) else 0,
        targets_hist=tg_n, carries_hist=int(row.carries) if has and pd.notna(row.carries) else 0,
        injury_status=status, flags=flags)


def rp_play(role: str) -> float:
    # prior probability that a game-day-roster player at this position takes the field
    return {"QB": 0.35, "RB": 0.75, "WR": 0.8, "TE": 0.85}.get(role, 0.7)


def depth_qb1(dq: pd.DataFrame, season: int, week: int, team: str, cutoff) -> str | None:
    """Depth-chart QB1 in force at the cutoff. From 2025 the latest snapshot with
    dt <= cutoff; before 2025 the week's chart (publication time not recorded, so this is
    an assumption that only the availability-informed variant J1 makes)."""
    d = dq[(dq.season == season) & (dq.team == team)]
    if not len(d):
        return None
    if d["dt"].notna().any():
        d = d[d["dt"] <= cutoff]
        if not len(d):
            return None
        d = d[d["dt"] == d["dt"].max()]
    else:
        d = d[d["week"] == week]
    d = d[d["rank"] == 1]
    return str(d["player_id"].iloc[0]) if len(d) else None


# J1: P(start) for the depth-chart QB1, estimated on 2016-2021 (QB1 led his team in pass
# attempts in 92.5% of games when not on the injury report, 61.7% when Questionable, never
# when Out/Doubtful; divided by ~0.97 because early exits are simulated separately).
DEPTH_QB1_START = 0.95
DEPTH_QB1_START_QUESTIONABLE = 0.64


def _qb_start_probs(players: list[PlayerIn], h: History, team: str,
                    qb1: str | None = None) -> None:
    """Starter probabilities: the QB with most dropbacks in the team's latest games,
    weighted by recency, among QBs available this week. J1 also uses the depth-chart
    QB1 in force at the cutoff."""
    qbs = [p for p in players if p.role == "QB"]
    if not qbs:
        return
    pg = h.pg[(h.pg.team == team) & (h.pg.position == "QB") & (h.pg.participation == "played")]
    last = pg.sort_values(["season", "week"]).groupby("game_id").tail(10)
    recent_games = last.drop_duplicates("game_id").sort_values(["season", "week"]).game_id.tail(4)
    lead = (last[last.game_id.isin(recent_games)].sort_values("attempts")
            .groupby("game_id").tail(1))
    w = {}
    for i, gid in enumerate(reversed(recent_games.tolist())):
        sel = lead[lead.game_id == gid]
        if len(sel):
            w[sel.player_id.iloc[0]] = w.get(sel.player_id.iloc[0], 0) + 0.5 ** i
    score = np.array([w.get(q.player_id, 0.0) + 0.05 for q in qbs])
    for i, q in enumerate(qbs):
        if q.injury_status == "Out":
            score[i] = 0.0
        elif q.injury_status == "Doubtful":
            score[i] *= 0.25
    if score.sum() == 0:
        score = np.ones(len(qbs))
    probs = score / score.sum()
    ids = [q.player_id for q in qbs]
    if qb1 in ids and qbs[ids.index(qb1)].injury_status not in ("Out", "Doubtful"):
        i = ids.index(qb1)
        base = (DEPTH_QB1_START_QUESTIONABLE if qbs[i].injury_status == "Questionable"
                else DEPTH_QB1_START)
        rest = np.delete(probs, i)
        rest = rest / rest.sum() * (1 - base) if rest.sum() > 0 else rest
        probs = np.insert(rest, i, base) if len(qbs) > 1 else np.array([1.0])
        qbs[i].flags.append("depth_chart_qb1")
    for q, pr in zip(qbs, probs, strict=True):
        q.p_start = float(pr)
        if pr < 0.5:
            q.flags.append("backup_qb")
