"""Live NBA slates and immutable editions.

An edition is built from games that tipped off before the publish time, the day's
SportsDataverse schedule and the latest injury snapshot. Files go to
records/editions/nba/<ET date>/ with a SHA-256 sidecar, are created exclusively (never
overwritten) and are registered in the append-only `editions` table. For each game the last
edition published before tip-off is the one that counts (D-008 applied to the NBA).
"""

from __future__ import annotations

import json
import sqlite3
import subprocess
from datetime import datetime, timedelta

import numpy as np
import pandas as pd

from .. import db
from ..config import DATA_HOME, EDITIONS_DIR, NEW_YORK, PROJECT_ROOT, REPORTS_DIR
from ..dist import Dist
from ..util import iso, sha256_bytes, sha256_json, utcnow
from . import backtest as B
from . import canon, source
from . import model as M

NBA_EDITIONS = EDITIONS_DIR / "nba"
AVAIL_PATH = REPORTS_DIR / "nba" / "model" / "availability.json"
MODELS = ("N1", "A", "C")
HEADLINE = "N1"
# Injury-report adjustments: assumptions, not validated (no snapshot history before Sep
# 2026); judged prospectively.
INJ_OUT_P = 0.01
INJ_GTD_MULT = 0.65


# --------------------------------------------------------------------------- availability
def save_availability(x: pd.DataFrame) -> dict:
    clf = B.fit_availability(x)
    d = {"model_version": M.MODEL_VERSION, "features": list(M.AVAIL_FEATURES),
         "coef": clf.coef_[0].tolist(), "intercept": float(clf.intercept_[0]),
         "fit_seasons": [min(B.FIT_SEASONS), max(B.FIT_SEASONS)]}
    AVAIL_PATH.parent.mkdir(parents=True, exist_ok=True)
    AVAIL_PATH.write_text(json.dumps(d, indent=1) + "\n")
    return d


def p_play_base(x: pd.DataFrame) -> np.ndarray:
    d = json.loads(AVAIL_PATH.read_text())
    z = M.avail_matrix(x) @ np.asarray(d["coef"]) + d["intercept"]
    return 1 / (1 + np.exp(-z))


# --------------------------------------------------------------------------- inputs
def _latest(con: sqlite3.Connection, dataset: str) -> tuple[pd.DataFrame, dict]:
    r = con.execute("SELECT id, path, sha256, retrieved_at FROM raw_files WHERE source=? AND "
                    "dataset=? AND part=? AND is_synthetic=0 ORDER BY retrieved_at DESC, id "
                    "DESC LIMIT 1", (source.SOURCE, dataset, str(source.CURRENT_SEASON))).fetchone()
    return pd.read_parquet(DATA_HOME / r["path"]), {"dataset": dataset, "sha256": r["sha256"],
                                                    "retrieved_at": r["retrieved_at"]}


def et_date(ts: pd.Timestamp) -> str:
    return ts.tz_convert(NEW_YORK).strftime("%Y-%m-%d")


def slate_games(sched: pd.DataFrame, date_et: str) -> pd.DataFrame:
    s = sched.copy()
    s["tip_utc"] = pd.to_datetime(s.date, utc=True)
    s = s[[et_date(t) == date_et for t in s.tip_utc]]
    return pd.DataFrame({"game_id": s.game_id.astype("int64").astype(str),
                         "home": s.home_abbreviation,
                         "away": s.away_abbreviation, "tip_utc": s.tip_utc,
                         "season": source.CURRENT_SEASON,
                         "season_type": s.season_type.map(canon.SEASON_TYPES).fillna("REG"),
                         "date": pd.Timestamp(date_et),
                         "time_valid": s.time_valid}).sort_values("tip_utc")


def injuries_now(inj: pd.DataFrame, sched: pd.DataFrame, as_of: datetime) -> pd.DataFrame:
    """Latest injury-snapshot row per athlete taken on or before `as_of` (ET date)."""
    day = as_of.astimezone(NEW_YORK).date()
    i = inj[pd.to_datetime(inj.as_of_date).dt.date <= day]
    if i.empty:
        return i.assign(player_id=pd.Series(dtype=str))
    i = i[i.as_of_date == i.as_of_date.max()]
    names = dict(zip(sched.home_display_name, sched.home_abbreviation, strict=False))
    names.update(dict(zip(sched.away_display_name, sched.away_abbreviation, strict=False)))
    return pd.DataFrame({"player_id": i.athlete_id.astype("int64").astype(str),
                         "team": i.team_display_name.map(names),
                         "status": i.status, "fantasy": i.detail_fantasy_status,
                         "detail": i.detail_type.fillna(""), "as_of_date": i.as_of_date.astype(str),
                         "news_at": i.injury_date.astype(str)}).drop_duplicates("player_id")


# --------------------------------------------------------------------------- build
def build(date_et: str, con: sqlite3.Connection | None = None, now: datetime | None = None) -> dict:
    con = con or db.connect()
    now = now or utcnow()
    p = M.load_params()
    L = canon.load()
    pg, tg, games = L["player_games"], L["team_games"], L["games"]
    pg = pg[pg.tip_utc < now]
    sched, sched_meta = _latest(con, "nba_schedule")
    inj, inj_meta = _latest(con, "nba_injuries")
    sg = slate_games(sched, date_et)
    sg = sg[~sg.game_id.isin(games.game_id)]  # only games not yet in the box scores
    if sg.empty:
        return {"error": f"no unplayed games on {date_et}"}
    allg = pd.concat([games[games.tip_utc < now], sg[games.columns.intersection(sg.columns)]],
                     ignore_index=True)
    cand = M.candidates(pg, allg)
    cand = cand[cand.game_id.isin(sg.game_id) & ~cand.first_listing.fillna(False)]
    x = M.assemble(cand, M.player_states(pg, p), M.team_states(tg[tg.tip_utc < now], p))
    x = M.availability_features(x, pg, allg)
    x = x[x.n_played.notna()].reset_index(drop=True)  # no NBA minutes yet: no forecast
    injn = injuries_now(inj, sched, now)
    x = x.merge(injn.drop(columns="team"), on="player_id", how="left")
    base = p_play_base(x)
    st = x.fantasy.fillna("")
    x["p_play"] = np.where(st.isin(["OUT", "OFS"]), INJ_OUT_P,
                           np.where(st.eq("GTD"), base * INJ_GTD_MULT, base))
    lg = M.league_rates(L["player_games"], L["team_games"], B.FIT_SEASONS)
    pms = {"N1": M.n1_pmfs(x, p, lg), "A": M.baseline_pmfs(x, p, "A"),
           "C": M.baseline_pmfs(x, p, "C")}
    return {"x": x, "games": sg, "pms": pms, "params": p,
            "inputs": {"schedule": sched_meta, "injuries": inj_meta,
                       "box_last_tip": iso(pg.tip_utc.max()),
                       "injury_snapshot": injn.as_of_date.max() if len(injn) else None}}


def _trim(pm: np.ndarray) -> Dist:
    c = np.cumsum(pm)
    hi = int(np.searchsorted(c, 1 - 1e-7)) + 1
    return Dist.from_pmf(0, pm[:hi] / pm[:hi].sum())


def _code_version() -> dict:
    def g(*a):
        return subprocess.run(["git", *a], cwd=PROJECT_ROOT, capture_output=True, text=True,
                              check=False).stdout.strip()
    return {"git_commit": g("rev-parse", "HEAD"), "model_version": M.MODEL_VERSION,
            "dirty": bool(g("status", "--porcelain", "--", "src"))}


def publish(date_et: str, kind: str = "early", con: sqlite3.Connection | None = None) -> dict:
    con = con or db.connect()
    code = _code_version()  # captured before computing: the edition records this code
    if code["dirty"]:
        raise RuntimeError("uncommitted source changes: commit before publishing")
    now = utcnow()
    b = build(date_et, con, now)
    if "error" in b:
        return b
    x, sg, pms = b["x"], b["games"], b["pms"]
    forecasts = []
    for i, r in enumerate(x.itertuples()):
        for model, pm in pms.items():
            for mk in M.MARKETS:
                d = _trim(pm[mk][i])
                forecasts.append({
                    "game_id": r.game_id, "player_id": r.player_id, "name": r.name,
                    "team": r.team, "opponent": r.opp, "position": r.pos, "model": model,
                    "market": mk, "p_play": round(float(r.p_play), 4),
                    "injury": None if pd.isna(r.fantasy) else {
                        "status": r.status, "fantasy": r.fantasy, "detail": r.detail,
                        "as_of": r.as_of_date},
                    "minutes_expected": round(float(r.min_mu), 1),
                    "mean": round(d.mean(), 2), "median": d.median(),
                    "q10": d.quantile(0.1), "q90": d.quantile(0.9), "dist": d.to_json()})
    eid = f"NBA-{date_et}-{kind}-{now.strftime('%Y%m%dT%H%MZ')}"
    doc = {"edition_id": eid, "sport": "NBA", "date_et": date_et, "kind": kind,
           "published_at": iso(now), "headline_model": HEADLINE, "models": list(MODELS),
           "code": code, "inputs": b["inputs"], "inputs_hash": sha256_json(b["inputs"]),
           "params_sha256": sha256_bytes(M.frozen_path().read_bytes()),
           "notes": ["Distributions are conditional on the player taking part; P(plays) is "
                     "separate. Injury adjustments (Out 1%, game-time decision x0.65) are "
                     "assumptions, not validated. Minutes do not yet react to teammates "
                     "ruled out. No bookmaker prices were used."],
           "games": [{"game_id": g.game_id, "home": g.home, "away": g.away,
                      "tip_utc": iso(g.tip_utc), "time_valid": bool(g.time_valid),
                      "published_before_tip": now < g.tip_utc} for g in sg.itertuples()],
           "forecasts": forecasts}
    blob = json.dumps(doc, separators=(",", ":"), default=str).encode()
    digest = sha256_bytes(blob)
    d = NBA_EDITIONS / date_et
    d.mkdir(parents=True, exist_ok=True)
    path = d / f"{eid}.json"
    with open(path, "xb") as f:
        f.write(blob)
    path.with_suffix(".json.sha256").write_text(f"{digest}  {path.name}\n")
    with db.tx(con):
        con.execute(
            "INSERT INTO editions(edition_id, season, week, season_type, kind, cutoff_at,"
            " published_at, model_id, code_version, inputs_hash, n_forecasts, file_path,"
            " file_sha256, retrospective) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
            (eid, source.CURRENT_SEASON, int(date_et.replace("-", "")), "REG", f"nba-{kind}",
             iso(now), iso(now), HEADLINE, code["git_commit"], doc["inputs_hash"],
             len(forecasts), str(path.relative_to(PROJECT_ROOT)), digest))
        for g in doc["games"]:
            con.execute("INSERT INTO edition_games(edition_id, game_id, kickoff_at, "
                        "published_before_kickoff) VALUES (?,?,?,?)",
                        (eid, g["game_id"], g["tip_utc"], int(g["published_before_tip"])))
    return {"edition_id": eid, "path": str(path), "sha256": digest, "players": len(x),
            "games": len(sg), "forecasts": len(forecasts),
            "first_tip": iso(sg.tip_utc.min()), "inputs": b["inputs"]}


def edition_files() -> list:
    """Editions in publication order (the file name ends with its UTC publish time; the kind
    label in the middle must not decide the order)."""
    return sorted(NBA_EDITIONS.glob("*/*.json"), key=lambda p: (p.parent.name,
                                                                p.stem.rsplit("-", 1)[-1]))


def latest_edition_path(date_et: str | None = None):
    files = edition_files()
    if date_et:
        files = [f for f in files if f.parent.name == date_et]
    return files[-1] if files else None


def next_date_et(now: datetime | None = None) -> str:
    now = now or utcnow()
    return (now - timedelta(hours=8)).astimezone(NEW_YORK).strftime("%Y-%m-%d")
