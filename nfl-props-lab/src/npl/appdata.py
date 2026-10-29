"""View models for the web app. Read-only: the app never writes forecasts, editions or
settlements (heavy work stays in the CLI jobs). Every number shown comes from a saved
edition, a lake table or a backtest ledger summary."""

from __future__ import annotations

import json
import math
from functools import lru_cache
from pathlib import Path

import numpy as np
import pandas as pd

from . import db
from .config import CORE_MARKETS, EDITIONS_DIR, LAKE_DIR, LONDON, MARKETS, REPORTS_DIR
from .dist import Dist, fair_decimal
from .sources import odds_api

HEADLINE = "J1"  # fallback for editions that predate the headline field
MARKET_LABELS = {m.key: m.label for m in CORE_MARKETS}
MILESTONES = {"pass_yds": [200, 250, 300], "pass_att": [30, 35, 40], "pass_cmp": [20, 25],
              "rush_yds": [25, 50, 75, 100], "rush_att": [10, 15, 20],
              "receptions": [3, 4, 5, 6, 7], "rec_yds": [25, 40, 50, 75, 100]}


def _mtime(p: Path) -> float:
    return p.stat().st_mtime if p.exists() else 0.0


def edition_files() -> list[Path]:
    return sorted(EDITIONS_DIR.glob("*/W*/*.json"))


@lru_cache(maxsize=8)
def _load_edition(path: str, mtime: float) -> dict:
    return json.loads(Path(path).read_text())


def load_edition(p: Path) -> dict:
    return _load_edition(str(p), _mtime(p))


@lru_cache(maxsize=2)
def _lake(mt: float) -> dict:
    pg = pd.read_parquet(LAKE_DIR / "player_games.parquet")
    games = pd.read_parquet(LAKE_DIR / "games.parquet")
    teams = pd.read_parquet(LAKE_DIR / "teams.parquet")
    players = pd.read_parquet(LAKE_DIR / "players.parquet")
    tg = pd.read_parquet(LAKE_DIR / "team_games.parquet")
    return {"pg": pg, "games": games, "teams": teams, "players": players, "tg": tg}


def lake() -> dict:
    return _lake(_mtime(LAKE_DIR / "player_games.parquet"))


def london(ts) -> str:
    t = pd.Timestamp(ts)
    if t.tzinfo is None:
        t = t.tz_localize("UTC")
    return t.tz_convert(LONDON).strftime("%a %d %b %H:%M %Z")


def _clean(o):
    """JSON-safe: NaN/inf -> None, numpy -> python."""
    if isinstance(o, dict):
        return {k: _clean(v) for k, v in o.items()}
    if isinstance(o, list | tuple):
        return [_clean(v) for v in o]
    if isinstance(o, np.generic):
        o = o.item()
    if isinstance(o, float) and (math.isnan(o) or math.isinf(o)):
        return None
    if isinstance(o, pd.Timestamp):
        return o.isoformat()
    return o


# ------------------------------------------------------------------ editions / slates
def editions_index() -> list[dict]:
    out = []
    for p in edition_files():
        e = load_edition(p)
        out.append({"edition_id": e["edition_id"], "season": e["season"], "week": e["week"],
                    "season_type": e["season_type"], "kind": e["kind"],
                    "published_at": e["published_at"],
                    "published_london": e["published_at_london"],
                    "headline_model": e.get("headline_model"),
                    "code": e["code"], "inputs_hash": e["inputs_hash"],
                    "file": str(p.relative_to(EDITIONS_DIR.parent.parent)),
                    "sha256": p.with_suffix(".json.sha256").read_text().split()[0]
                    if p.with_suffix(".json.sha256").exists() else None,
                    "games": e["games"],
                    "n_forecasts": sum(1 for f in e["forecasts"] if f["status"] == "ok"),
                    "retrospective": e.get("retrospective", False)})
    return sorted(out, key=lambda x: x["published_at"], reverse=True)


def latest_edition(season: int | None = None, week: int | None = None) -> dict | None:
    idx = editions_index()
    if season is not None:
        idx = [e for e in idx if e["season"] == season and e["week"] == week]
    if not idx:
        return None
    p = EDITIONS_DIR.parent.parent / idx[0]["file"]
    return load_edition(p)


def _card(f: dict, game: dict, models: dict | None = None) -> dict:
    """Prop card fields from a saved forecast row (no recomputation of the model)."""
    c = {k: f.get(k) for k in ("player_id", "name", "position", "team", "opponent", "game_id",
                               "market", "model", "status", "p_play", "mean", "median", "q05",
                               "mix_weight_J0",
                               "q10", "q25", "q75", "q90", "q95", "injury_report", "flags",
                               "n_draws", "n_draws_played", "is_home")}
    if not isinstance(c.get("injury_report"), str):
        c["injury_report"] = None  # no game status on the report (NaN in older editions)
    c["market_label"] = MARKET_LABELS.get(f["market"], f["market"])
    c["kickoff_utc"] = game.get("kickoff_utc")
    c["kickoff_london"] = game.get("kickoff_london")
    c["published_before_kickoff"] = game.get("published_before_kickoff")
    c["role_inputs"] = f.get("role_inputs")
    if f["status"] == "ok":
        d = Dist.from_json(f["dist"])
        c["dist"] = f["dist"]
        ms = []
        for k in MILESTONES.get(f["market"], []):
            p = d.p_ge(k)
            ms.append({"k": k, "p": round(p, 4), "mc_se": mc_se(f, p)})
        c["milestones"] = ms
        c["sample_size"] = (f.get("role_inputs") or {}).get("games_in_history")
        c["uncertainty"] = _uncertainty(f)
    if models:
        c["other_models"] = models
    return c


def mc_se(f: dict, p: float) -> float | None:
    """Monte Carlo standard error of a probability. Simulation models: binomial on the
    draws in which the player took part. A pool w*J + (1-w)*M carries w times the J error
    (M has no simulation noise). Analytic baselines: none."""
    n = f.get("n_draws_played")
    if not n:
        return None
    w = f.get("mix_weight_J0", 1.0 if f.get("model", "").startswith("J") else None)
    if w is None:
        return None
    return round(w * math.sqrt(max(p * (1 - p), 0.0) / n), 4)


def _uncertainty(f: dict) -> dict:
    ri = f.get("role_inputs") or {}
    g = ri.get("games_in_history") or 0
    flags = f.get("flags") or []
    level = "low" if g >= 16 else ("medium" if g >= 6 else "high")
    if "no_history" in flags:
        level = "very high"
    reasons = []
    if g < 6:
        reasons.append(f"only {g} games of history")
    if (f.get("p_play") or 1) < 0.8:
        reasons.append(f"participation uncertain (P(plays) {f['p_play']:.0%})")
    if isinstance(f.get("injury_report"), str):
        reasons.append(f"official report: {f['injury_report']}")
    if "backup_qb" in flags:
        reasons.append("backup QB: volume depends on the starter leaving")
    return {"level": level, "reasons": reasons}


def slate(season: int | None = None, week: int | None = None) -> dict:
    e = latest_edition(season, week)
    if e is None:
        return {"edition": None, "games": []}
    head = e.get("headline_model", HEADLINE)
    teams = {(t["game_id"], t["team"]): t for t in e["teams"] if t["model"] == head}
    out = []
    now = pd.Timestamp.now(tz="UTC")
    for g in e["games"]:
        ko = pd.Timestamp(g["kickoff_utc"])
        alerts = sorted({(f["name"], f["team"], f["injury_report"]) for f in e["forecasts"]
                         if f["game_id"] == g["game_id"] and f["model"] == head and
                         isinstance(f.get("injury_report"), str)}, key=lambda x: (x[1], x[0]))
        out.append({**g, "state": "upcoming" if now < ko else
                    ("in play / awaiting result" if now < ko + pd.Timedelta(hours=4)
                     else "finished"),
                    "home_sim": teams.get((g["game_id"], g["home"]), {}).get("sim"),
                    "away_sim": teams.get((g["game_id"], g["away"]), {}).get("sim"),
                    "alerts": [{"name": a, "team": b, "status": c} for a, b, c in alerts]})
    return _clean({"edition": {k: e[k] for k in ("edition_id", "kind", "season", "week",
                                                 "season_type", "published_at",
                                                 "published_at_london", "status_note", "notes",
                                                 "code", "inputs_hash", "headline_model",
                                                 "models")},
                   "games": out})


NFL_STAT = {"pass_yds": "passing_yards", "pass_att": "attempts", "pass_cmp": "completions",
            "rush_yds": "rushing_yards", "rush_att": "carries", "receptions": "receptions",
            "rec_yds": "receiving_yards"}


def _nfl_logs(pids: set, season: int, week: int, n: int = 20) -> dict:
    """Last `n` games played before (season, week), for hit rates on prop cards."""
    pg = lake()["pg"]
    pg = pg[pg.player_id.isin(pids) & (pg.participation == "played") &
            ((pg.season < season) | ((pg.season == season) & (pg.week < week)))]
    pg = pg.sort_values(["season", "week"]).groupby("player_id").tail(n)
    return {pid: g for pid, g in pg.groupby("player_id")}


def props(season=None, week=None, model: str | None = None) -> dict:
    e = latest_edition(season, week)
    if e is None:
        return {"edition": None, "cards": []}
    model = model or e.get("headline_model", HEADLINE)
    games = {g["game_id"]: g for g in e["games"]}
    by_key = {}
    for f in e["forecasts"]:
        by_key.setdefault((f["game_id"], f["player_id"], f["market"]), {})[f["model"]] = f
    cards = []
    for (gid, _pid, _mk), fm in by_key.items():
        f = fm.get(model)
        if f is None:
            continue
        others = {m: {"mean": x.get("mean"), "median": x.get("median"), "q10": x.get("q10"),
                      "q90": x.get("q90"), "status": x["status"]}
                  for m, x in fm.items() if m != model}
        cards.append(_card(f, games[gid], others))
    logs = _nfl_logs({c["player_id"] for c in cards}, e["season"], e["week"])
    for c in cards:
        g = logs.get(c["player_id"])
        if g is None or c["market"] not in NFL_STAT:
            c["log"] = {"values": [], "opp": [], "date": [], "season": []}
            continue
        c["log"] = {"values": [int(v) for v in g[NFL_STAT[c["market"]]].fillna(0)],
                    "opp": g.opponent.tolist(), "date": [f"W{w}" for w in g.week],
                    "season": g.season.astype(str).tolist(), "current_season": str(e["season"])}
    return _clean({"edition_id": e["edition_id"], "model": model, "season": e["season"],
                   "week": e["week"], "published_at_london": e["published_at_london"],
                   "cards": cards})


def forecast_dist(edition_id: str, game_id: str, player_id: str, market: str, model: str):
    for p in edition_files():
        if p.stem == edition_id:
            for f in load_edition(p)["forecasts"]:
                if (f["game_id"], f["player_id"], f["market"], f["model"]) == (
                        game_id, player_id, market, model) and f["status"] == "ok":
                    return Dist.from_json(f["dist"]), f
    return None, None


def manual_line(edition_id, game_id, player_id, market, model, line: float) -> dict:
    """User-entered line WITHOUT a price: probabilities and fair prices only."""
    if not math.isfinite(line) or abs(line) > 2000 or (line * 2) % 1 != 0:
        return {"error": "line must be a whole or half number between -2000 and 2000"}
    d, f = forecast_dist(edition_id, game_id, player_id, market, model)
    if d is None:
        return {"error": "forecast not found"}
    p = d.line(line)
    return _clean({"label": "manual line — no verified price", "line": line,
                   "over": round(p["over"], 4), "under": round(p["under"], 4),
                   "push": round(p["push"], 4),
                   "fair_over": fair_decimal(p["over"], p["push"]),
                   "fair_under": fair_decimal(p["under"], p["push"]),
                   "mc_se": mc_se(f, p["over"]),
                   "conditional_on": "player takes part (bookmakers usually void otherwise)",
                   "p_play": f.get("p_play"), "edition_id": edition_id, "model": model,
                   "value_claim": None})


# ------------------------------------------------------------------ players / games
def player(player_id: str, season: int | None = None) -> dict:
    L = lake()
    pg = L["pg"]
    d = pg[pg.player_id == player_id].sort_values(["season", "week"])
    if d.empty:
        return {"error": "unknown player"}
    info = L["players"].set_index("gsis_id").loc[player_id] if player_id in set(
        L["players"].gsis_id) else None
    tg = L["tg"].set_index(["game_id", "team"])
    rows = []
    for r in d.itertuples():
        t = tg.loc[(r.game_id, r.team)] if (r.game_id, r.team) in tg.index else None
        rows.append({
            "season": r.season, "week": r.week, "season_type": r.season_type,
            "game_id": r.game_id, "team": r.team, "opponent": r.opponent,
            "kickoff_london": london(r.kickoff_utc), "participation": r.participation,
            "roster_status": r.status, "position": r.position,
            **{c: (None if pd.isna(getattr(r, c)) else float(getattr(r, c)))
               for c in ("attempts", "completions", "passing_yards", "carries",
                         "rushing_yards", "targets", "receptions", "receiving_yards")},
            "team_pass_att": None if t is None or pd.isna(t.pass_att) else float(t.pass_att),
            "team_designed_runs": None if t is None or pd.isna(t.designed_rush) else
            float(t.designed_rush),
        })
    log = pd.DataFrame(rows)
    played = log[log.participation == "played"]
    seasons = sorted(log.season.unique().tolist())
    cur = season or seasons[-1]
    windows = {}
    for label, w in (("season", played[played.season == cur]),
                     ("last 4 played", played.tail(4)),
                     ("previous season", played[played.season == cur - 1])):
        windows[label] = {"games": len(w), **{c: round(float(w[c].mean()), 2) if len(w) else
                                              None for c in ("attempts", "completions",
                                                             "passing_yards", "carries",
                                                             "rushing_yards", "targets",
                                                             "receptions", "receiving_yards")}}
    # historical frequencies at milestones (NOT probabilities)
    freq = {}
    for m in CORE_MARKETS:
        w = played[played.season == cur][m.stat].dropna()
        if len(w):
            freq[m.key] = [{"k": k, "hits": int((w >= k).sum()), "games": len(w)}
                           for k in MILESTONES[m.key]]
    fc = []
    for p in edition_files()[-6:]:
        e = load_edition(p)
        for f in e["forecasts"]:
            if f["player_id"] == player_id:
                g = next(x for x in e["games"] if x["game_id"] == f["game_id"])
                fc.append({**_card(f, g), "edition_id": e["edition_id"]})
    return _clean({"player_id": player_id,
                   "name": (info["display_name"] if info is not None else d.name.iloc[-1]),
                   "position": d.position.iloc[-1], "team": d.team.iloc[-1],
                   "birth_date": None if info is None else info.get("birth_date"),
                   "rookie_season": None if info is None else info.get("rookie_season"),
                   "seasons": seasons, "season": cur,
                   "game_log": log[log.season.isin([cur, cur - 1])].to_dict("records"),
                   "windows": windows, "historical_frequency": freq, "forecasts": fc})


def game(game_id: str) -> dict:
    for p in reversed(edition_files()):
        e = load_edition(p)
        g = next((x for x in e["games"] if x["game_id"] == game_id), None)
        if g is None:
            continue
        head = e.get("headline_model", HEADLINE)
        # pooled models (X1 = J1 + M, X = J0 + M) have no team simulation of their own
        sim_model = {"X1": "J1", "X": "J0"}.get(head, head)
        teams = [t for t in e["teams"] if t["game_id"] == game_id and t["model"] == sim_model]
        cards = [_card(f, g) for f in e["forecasts"] if f["game_id"] == game_id and
                 f["model"] == head]
        return _clean({"game": g, "edition_id": e["edition_id"], "model": head,
                       "sim_model": sim_model, "teams": teams, "cards": cards})
    return {"error": "game not in any edition"}


# ------------------------------------------------------------------ backtests / record / data
def backtests() -> list[dict]:
    out = []
    for p in sorted((REPORTS_DIR / "backtests").glob("*/summary.json")):
        s = json.loads(p.read_text())
        out.append({"id": p.parent.name, **s})
    return _clean(out)


def record() -> dict:
    """Published editions, and the prospective record under D-008 (for each model and
    game, the last edition published before kickoff) with scores once results are final."""
    from .settle import record_rows

    idx = editions_index()
    pg = lake()["pg"].set_index(["game_id", "player_id"])
    rows = []
    for eid, f in record_rows():
        if f["status"] != "ok":
            continue
        key = (f["game_id"], f["player_id"])
        part = pg["participation"].get(key) if key in pg.index else None
        val = pg[MARKETS[f["market"]].stat].get(key) if key in pg.index else None
        r = {"edition_id": eid, "model": f["model"], "market": f["market"],
             "participation": part or "not_final",
             "actual": None if val is None or pd.isna(val) else float(val)}
        if r["actual"] is not None and part == "played":
            r["crps"] = Dist.from_json(f["dist"]).crps(r["actual"])
        rows.append(r)
    df = pd.DataFrame(rows)
    summ = {}
    if len(df):
        for m, g in df.groupby("model"):
            sc = g.dropna(subset=["crps"]) if "crps" in g else g.iloc[0:0]
            summ[m] = {"forecasts": len(g), "pre_kickoff": len(g),
                       "settled_scored": len(sc),
                       "pending": int((g.participation == "not_final").sum()),
                       "void": int(g.participation.isin(["inactive", "dnp"]).sum()),
                       "mean_crps": round(float(sc.crps.mean()), 4) if len(sc) else None,
                       "by_market": {mk: {"n": len(x), "mean_crps": round(float(x.crps.mean()), 4)}
                                     for mk, x in sc.groupby("market")} if len(sc) else {}}
    return _clean({"editions": idx, "summary": summ,
                   "note": "Genuine pre-match record: for each model and game, the last "
                           "edition published before kickoff counts (earlier editions stay on "
                           "file). Scores appear once nflverse publishes final statistics."})


def data_status() -> dict:
    con = db.connect()
    fresh = pd.read_sql_query(
        "SELECT dataset, part, MAX(r.retrieved_at) retrieved_at, MAX(o.observed_at) checked_at,"
        " r.upstream_last_modified, r.n_rows FROM raw_files r JOIN raw_observations o ON"
        " o.raw_file_id=r.id WHERE r.part IN ('all','2026') GROUP BY dataset, part", con)
    jobs = pd.read_sql_query("SELECT job, started_at, finished_at, status FROM job_runs"
                             " ORDER BY id DESC LIMIT 30", con)
    # a job counts as failing only if its most recent run failed (later success supersedes)
    latest = jobs.drop_duplicates("job", keep="first")
    failed = latest[latest.status.isin(["failed", "partial"])]
    stale = []
    now = pd.Timestamp.now(tz="UTC")
    for r in fresh.itertuples():
        age = (now - pd.Timestamp(r.checked_at)).total_seconds() / 3600
        if age > 30:
            stale.append({"dataset": r.dataset, "part": r.part, "hours_since_check": round(age)})
    cov = REPORTS_DIR / "coverage" / "coverage_matrix.csv"
    return _clean({"freshness": fresh.to_dict("records"), "jobs": jobs.to_dict("records"),
                   "failed_jobs": failed.to_dict("records"), "stale": stale,
                   "odds": odds_api.status(con),
                   "coverage": pd.read_csv(cov).to_dict("records") if cov.exists() else [],
                   "docs": {"audit": "docs/DATA_AUDIT.md", "decisions": "DECISIONS.md",
                            "methodology": "docs/METHODOLOGY.md"}})


# ------------------------------------------------------------------ exploratory scenarios
_SCEN_CACHE: dict = {}


def scenario(game_id: str, unavailable: list[str], n: int = 4000, seed: int = 99) -> dict:
    """Re-simulate one game with chosen players removed (J1 inputs, fixed seed).

    Exploratory only: never written to an edition. Rejects players who are not on either
    team's game-day roster and any scenario that removes every quarterback of a team."""
    import copy

    import numpy as np

    from . import features, sim

    e = latest_edition()
    if e is None:
        return {"error": "no edition"}
    g = next((x for x in e["games"] if x["game_id"] == game_id), None)
    if g is None:
        return {"error": "game not in the latest edition"}
    key = (e["edition_id"], e["season"], e["week"])
    if key not in _SCEN_CACHE:
        lk = features.load_lake()
        games, P, _ = features.build_slate(lk, e["season"], e["week"], variant="J1")
        _SCEN_CACHE.clear()
        _SCEN_CACHE[key] = ({x.game_id: x for x in games}, P)
    games, P = _SCEN_CACHE[key]
    gi = copy.deepcopy(games[game_id])
    roster = {p.player_id: p for t in (gi.home, gi.away) for p in t.players}
    bad = [pid for pid in unavailable if pid not in roster]
    if bad:
        return {"error": f"not on either game-day roster: {', '.join(bad)}"}
    if len(unavailable) > 12:
        return {"error": "at most 12 players can be removed"}
    for t in (gi.home, gi.away):
        qbs = [p for p in t.players if p.role == "QB"]
        if qbs and all(q.player_id in unavailable for q in qbs):
            return {"error": f"{t.team} would have no quarterback; keep at least one"}
        for p in t.players:
            if p.player_id in unavailable:
                p.p_play, p.p_start = 0.0, 0.0
        left = [q for q in qbs if q.player_id not in unavailable]
        tot = sum(q.p_start for q in left)
        for q in left:
            q.p_start = q.p_start / tot if tot > 0 else 1.0 / len(left)

    def run(game):
        out = sim.simulate_game(game, P, n, seed)
        res = {}
        for side in ("home", "away"):
            sims, tt = out[side]
            res[side] = (sims, tt)
        return res

    base, scen = run(games[game_id]), run(gi)
    players, teams, joint = [], [], []
    for side in ("home", "away"):
        (bs, bt), (ss, st) = base[side], scen[side]
        t = gi.home if side == "home" else gi.away
        teams.append({"team": t.team, **{k: {"base": round(float(bt[k].mean()), 2),
                                            "scenario": round(float(st[k].mean()), 2)}
                                         for k in ("plays", "attempts", "designed_runs")}})
        for b, s in zip(bs, ss, strict=True):
            p = s.player
            row = {"player_id": p.player_id, "name": p.name, "position": p.position,
                   "team": t.team, "removed": p.player_id in unavailable,
                   "p_play_base": round(float(b.played.mean()), 3),
                   "p_play_scenario": round(float(s.played.mean()), 3)}
            for stat in ("attempts", "passing_yards", "carries", "rushing_yards", "targets",
                         "receiving_yards"):
                bm = b.stats[stat][b.played].mean() if b.played.any() else np.nan
                sm = s.stats[stat][s.played].mean() if s.played.any() else np.nan
                row[stat] = {"base": None if np.isnan(bm) else round(float(bm), 2),
                             "scenario": None if np.isnan(sm) else round(float(sm), 2)}
            players.append(row)
        # joint structure: correlation of the starting QB's passing yards with each
        # receiver's receiving yards across scenario draws where both played
        qb = max((s for s in ss if s.player.role == "QB"), key=lambda s: s.played.mean())
        for s in ss:
            if s.player.role in ("WR", "TE", "RB") and s.played.mean() > 0.5:
                both = qb.played & s.played
                if both.sum() > 200 and s.stats["receiving_yards"][both].std() > 0:
                    c = np.corrcoef(qb.stats["passing_yards"][both],
                                    s.stats["receiving_yards"][both])[0, 1]
                    joint.append({"team": t.team, "qb": qb.player.name, "receiver": s.player.name,
                                  "corr": round(float(c), 3)})
    joint.sort(key=lambda r: -r["corr"])
    return _clean({"label": "Exploratory scenario — not a published forecast",
                   "edition_id": e["edition_id"], "game_id": game_id, "removed": unavailable,
                   "model": "J1 inputs, joint simulation only (stacking not applied)",
                   "n_draws": n, "seed": seed, "teams": teams, "players": players,
                   "joint": joint[:10]})


# ------------------------------------------------------------------ defence profiles
_DEF_CACHE: dict = {}


def defense(season: int | None = None, week: int | None = None) -> dict:
    """Team defence profiles before (season, week); defaults to the latest edition's slate,
    i.e. the same information the forecasts used."""
    from . import defense as dmod
    from .features import load_lake

    if season is None:
        e = latest_edition()
        if e is None:
            return {"error": "no edition"}
        season, week = e["season"], e["week"]
    key = (season, week, _mtime(LAKE_DIR / "plays.parquet"), _mtime(dmod.CALIBRATION))
    if key not in _DEF_CACHE:
        _DEF_CACHE.clear()
        prof = dmod.profiles(load_lake(), season, week)
        prof["facets"] = dmod.FACETS
        prof["heat_columns"] = list(dmod.HEAT_COLUMNS)
        cal = dmod.load_calibration()
        prof["reliability"] = {k: {f: v.get(f) for f in ("label", "facet", "K", "carry_over_6g",
                                                        "skill_vs_average", "labels_held",
                                                        "labels_check")}
                               for k, v in cal.items()}
        prof["note"] = ("Compared with what the same opponents managed in their other games "
                        "this season before this week, then shrunk towards average by how much "
                        "such differences carried over in past seasons. Rank 1 = best defence. "
                        "'Likely' = at least 80% probability of being truly better (or worse) "
                        "than average on that measure.")
        _DEF_CACHE[key] = _clean(prof)
    return _DEF_CACHE[key]


# ------------------------------------------------------------------ look: colours, photos
def visuals(pids: set | None = None) -> dict:
    """Team colours and cached player photos (data URIs) for the app's NFL pages."""
    from . import headshots

    t = lake()["teams"]
    teams = {r.team_abbr: [r.team_color, r.team_color2] for r in t.itertuples()
             if isinstance(r.team_color, str)}
    if pids is None:
        pids = {c["player_id"] for c in props()["cards"]}
    return {"teams": teams, "photos": headshots.data_uris("nfl", set(pids)),
            "logos": headshots.logo_uris("nfl"),
            "logos_dark": headshots.logo_uris("nfl", dark=True)}
