"""Settlement of published editions (append-only revisions) and the prospective record.

For every forecast in a published edition, the latest lake value for (game, player,
market) is compared with the latest settlement revision; a new revision row is appended
only when the status or value changed (stat corrections, late participation data).
Published editions are never modified.
"""

from __future__ import annotations

import json
import sqlite3

import pandas as pd

from . import appdata, db, rawstore
from .config import LAKE_DIR, MARKETS, REPORTS_DIR
from .dist import Dist
from .sources.nflverse import SOURCE
from .util import iso, utcnow


def latest_revisions(con: sqlite3.Connection) -> pd.DataFrame:
    return pd.read_sql_query(
        "SELECT s.* FROM settlements s JOIN (SELECT game_id, player_id, market, MAX(revision) r"
        " FROM settlements GROUP BY game_id, player_id, market) m ON s.game_id=m.game_id AND"
        " s.player_id=m.player_id AND s.market=m.market AND s.revision=m.r", con)


def run(con: sqlite3.Connection | None = None) -> dict:
    con = con or db.connect()
    pg = pd.read_parquet(LAKE_DIR / "player_games.parquet").set_index(["game_id", "player_id"])
    manifest = json.loads((LAKE_DIR / "build_manifest.json").read_text())
    games = pd.read_parquet(LAKE_DIR / "games.parquet").set_index("game_id")
    stats_file = rawstore.latest(con, SOURCE, "stats_player_week", str(pd.Timestamp.now().year))
    raw_id = stats_file["id"] if stats_file else None
    prev = latest_revisions(con).set_index(["game_id", "player_id", "market"])
    keys = set()
    for e in appdata.editions_index():
        full = appdata.load_edition(appdata.EDITIONS_DIR.parent.parent / e["file"])
        for f in full["forecasts"]:
            keys.add((f["game_id"], f["player_id"], f["market"]))
    new, now = [], iso(utcnow())
    for g, p, mk in sorted(keys):
        final = bool(games.is_final.get(g, False))
        if not final:
            status, value = "not_final", None
        elif (g, p) in pg.index:
            part = pg.participation.get((g, p))
            status = {"played": "final", "inactive": "inactive", "dnp": "dnp"}.get(part, "missing")
            v = pg[MARKETS[mk].stat].get((g, p))
            value = None if v is None or pd.isna(v) else float(v)
        else:
            status, value = "missing", None
        if (g, p, mk) in prev.index:
            old = prev.loc[(g, p, mk)]
            same_v = (pd.isna(old.value) and value is None) or (old.value == value)
            if old.status == status and same_v:
                continue
            rev = int(old.revision) + 1
        else:
            if status == "not_final":
                continue  # nothing to record until the game is final
            rev = 1
        new.append((g, p, mk, rev, status, value, now, raw_id))
    with db.tx(con):
        con.executemany("INSERT INTO settlements(game_id, player_id, market, revision, status,"
                        " value, observed_at, raw_file_id) VALUES (?,?,?,?,?,?,?,?)", new)
    summ = prospective_scores()
    return {"new_revisions": len(new), "lake_built_at": manifest["built_at"], **summ}


def record_rows() -> list[tuple[str, dict]]:
    """(edition_id, forecast) pairs that make up the prospective record under D-008: for
    each model and game, every row of the LAST edition published before that game's
    kickoff (rows of earlier editions for the same model and game are dropped)."""
    latest: dict = {}
    eds = {}
    for e in sorted(appdata.editions_index(), key=lambda x: x["published_at"]):
        if e["retrospective"]:
            continue
        full = appdata.load_edition(appdata.EDITIONS_DIR.parent.parent / e["file"])
        eds[e["edition_id"]] = full
        pre = {g["game_id"] for g in full["games"] if g["published_before_kickoff"]}
        for m in {f["model"] for f in full["forecasts"]}:
            for g in pre:
                latest[(m, g)] = e["edition_id"]
    out = []
    for (m, g), eid in latest.items():
        out += [(eid, f) for f in eds[eid]["forecasts"] if f["model"] == m and f["game_id"] == g]
    return out


def prospective_scores() -> dict:
    """Score the prospective record per model and market (D-008)."""
    pg = pd.read_parquet(LAKE_DIR / "player_games.parquet").set_index(["game_id", "player_id"])
    best = {(f["model"], f["game_id"], f["player_id"], f["market"]): (eid, f)
            for eid, f in record_rows()}
    rows = []
    for (m, g, p, mk), (eid, f) in best.items():
        part = pg.participation.get((g, p)) if (g, p) in pg.index else None
        v = pg[MARKETS[mk].stat].get((g, p)) if (g, p) in pg.index else None
        r = {"model": m, "edition_id": eid, "game_id": g, "player_id": p, "market": mk,
             "status": f["status"], "participation": part, "actual": v}
        if f["status"] == "ok" and part == "played" and v is not None and not pd.isna(v):
            r["crps"] = Dist.from_json(f["dist"]).crps(float(v))
        rows.append(r)
    df = pd.DataFrame(rows)
    out = {"forecasts": len(df), "scored": int(df.get("crps", pd.Series(dtype=float)).notna().sum())
           if len(df) else 0}
    if len(df) and "crps" in df:
        out["by_model_market"] = (df.dropna(subset=["crps"]).groupby(["model", "market"])
                                  .crps.agg(["mean", "size"]).round(4).reset_index()
                                  .to_dict("records"))
    p = REPORTS_DIR / "record"
    p.mkdir(parents=True, exist_ok=True)
    if len(df):
        df.to_csv(p / "prospective_ledger.csv", index=False)
    (p / "summary.json").write_text(json.dumps(out, indent=1, default=str))
    return out
