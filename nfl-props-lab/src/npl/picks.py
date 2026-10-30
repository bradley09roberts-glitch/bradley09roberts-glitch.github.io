"""Pick rules for both sports (D-028), applied to the latest edition and the stored US lines.

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


def nba() -> dict:
    """Picks from the latest NBA edition and the newest stored lines."""
    from .dist import Dist
    from .nba import appdata as A
    from .nba import backtest as B
    from .nba import canon, live
    from .nba import model as M

    P = A.props()
    L = canon.load()
    played = L["player_games"][L["player_games"].participation == "played"]
    cur = played[played.season == played.season.max()]
    gp = cur.groupby(["player_id", "team"]).size()
    prev = played[played.season == played.season.max() - 1].groupby("player_id").team.agg(
        lambda s: s.value_counts().index[0])
    b = live.build(P["date_et"])
    if "error" in b:  # every game of the edition has tipped off: picks are made before tip only
        return {"edition_id": P["edition_id"], "picks": [], "left_out_15_plus": 0,
                "error": f"{P['date_et']}: {b['error']}; publish the next edition first", "rules": __doc__}
    x, prm = b["x"], b["params"]
    lg = M.league_rates(L["player_games"], L["team_games"], B.FIT_SEASONS)
    star = stars_out(P["cards"], cur)
    rows, out_of_range = [], 0
    for c in P["cards"]:
        m = c.get("market_line")
        if (not m or c["p_play"] < 0.85 or c["injury"] or c["teammates_out"] or m["books"] < 3
                or float(m["line"]).is_integer() or gp.get((c["player_id"], c["team"]), 0) < 3
                or c["team"] in star):
            continue
        line = m["line"]
        over = Dist.from_json(c["dist"]).line(line)["over"]
        side = "over" if over >= m["fair_over"] else "under"
        mp = over if side == "over" else 1 - over
        kp = m["fair_over"] if side == "over" else 1 - m["fair_over"]
        if mp - kp < GAP_MIN:
            continue
        if mp - kp >= GAP_MAX:
            out_of_range += 1
            continue
        row = x[x.player_id == c["player_id"]].copy().reset_index(drop=True)
        row["min_mu"] = float(cur[cur.player_id == c["player_id"]].minutes.mean())
        pm = M.n1_pmfs(row, prm, lg, only=(c["market"],))[c["market"]][0]
        o2 = float(pm[int(np.floor(line)) + 1:].sum())
        mp2 = o2 if side == "over" else 1 - o2
        low = min(mp, mp2)
        if low - kp < GAP_MIN:
            continue
        lg_ = c["log"]
        vals = np.array(lg_["values"], float)
        sv = np.array([v for v, s in zip(lg_["values"], lg_["season"], strict=True)
                       if s == lg_["current_season"]], float)
        l10 = _hits(vals[-10:], line, side)
        avg = float(sv.mean()) if len(sv) else float("nan")
        margin = (avg - line) / line * (1 if side == "over" else -1) if len(sv) else -1
        strict = (prev.get(c["player_id"]) in (None, c["team"]) and margin >= 0.10 and l10[0] >= 7)
        rows.append({"sport": "nba", "player": c["name"], "team": c["team"], "opponent": c["opponent"],
                     "game_id": c["game_id"], "tip_london": c["tip_london"], "market": c["market"],
                     "line": line, "side": side, "model": round(mp, 4), "model_low": round(low, 4),
                     "market_p": round(kp, 4), "best_price": m["best_over"] if side == "over" else m["best_under"],
                     "books": m["books"], "line_seen": m["observed_at"], "last10": l10,
                     "season": _hits(sv, line, side), "season_avg": round(avg, 2), "strict": bool(strict)})
    pk = [r for r in rows if r["model"] - r["market_p"] >= NBA_GAP_MIN]
    leans = [r for r in rows if r["model"] - r["market_p"] < NBA_GAP_MIN]
    return {"edition_id": P["edition_id"], "picks": _finish(pk), "leans": _finish(leans),
            "left_out_15_plus": out_of_range, "teams_left_out": star, "rules": __doc__}


def nfl(season: int | None = None, week: int | None = None, game_id: str | None = None) -> dict:
    """Picks for the latest NFL edition (optionally one game) against the newest stored lines."""
    from . import appdata as AP
    from . import db
    from .dist import Dist
    from .sources import odds_api

    e = AP.latest_edition(season, week)
    fc: dict = {}
    for f in e["forecasts"]:
        if f["status"] == "ok" and (game_id is None or f["game_id"] == game_id):
            fc.setdefault((f["game_id"], f["player_id"], f["market"]), {})[f["model"]] = f
    q = odds_api.quotes_frame(db.connect())
    games = {k[0] for k in fc}
    q = q[q.game_id.isin(games) & q.player_id.notna()]
    if q.empty:
        return {"edition_id": e["edition_id"], "picks": [], "left_out_15_plus": 0, "rules": __doc__}
    q = q[q.observed_at == q.groupby(["game_id", "market"]).observed_at.transform("max")]
    logs = AP._nfl_logs({k[1] for k in fc}, e["season"], e["week"])
    rows, out_of_range = [], 0
    for (gid, pid, mk), x in q.groupby(["game_id", "player_id", "market"]):
        fm = fc.get((gid, pid, mk))
        if not fm or not {"X1", "J1", "M", "A"} <= set(fm):
            continue
        line = float(x.line.mode().iloc[0])
        y = x[x.line == line].pivot_table(index="bookmaker", columns="selection",
                                          values="decimal_price", aggfunc="last").dropna()
        if line.is_integer() or not {"over", "under"} <= set(y.columns) or len(y) < 3:
            continue
        h = fm["X1"]
        if (h.get("p_play") or 0) < 0.85 or h.get("injury_report"):
            continue
        fair = float(((1 / y.over) / (1 / y.over + 1 / y.under)).mean())
        po = {m: Dist.from_json(f["dist"]).line(line)["over"] for m, f in fm.items() if f.get("dist")}
        side = "over" if po["X1"] >= fair else "under"
        sp = {m: (v if side == "over" else 1 - v) for m, v in po.items()}
        kp = fair if side == "over" else 1 - fair
        low = min(sp["X1"], sp["J1"], sp["M"])
        if sp["A"] <= 0.5 or low - kp < GAP_MIN:
            continue
        if sp["X1"] - kp >= GAP_MAX:
            out_of_range += 1
            continue
        g = logs.get(pid)
        stat = AP.NFL_STAT.get(mk)
        vals = g[stat].fillna(0).to_numpy(float) if g is not None and stat else np.array([])
        rows.append({"sport": "nfl", "player": h["name"], "team": h["team"], "opponent": h["opponent"],
                     "game_id": gid, "market": mk, "line": line, "side": side,
                     "model": round(sp["X1"], 4), "model_low": round(low, 4), "market_p": round(kp, 4),
                     "best_price": float(y[side].max()), "books": int(len(y)),
                     "line_seen": str(x.observed_at.max()), "last10": _hits(vals[-10:], line, side)})
    return {"edition_id": e["edition_id"], "picks": _finish(rows), "left_out_15_plus": out_of_range,
            "rules": __doc__}


def table(res: dict) -> str:
    if res.get("error"):
        return res["error"]
    cols = ["player", "team", "opponent", "market", "side", "line", "model_low", "market_p",
            "estimate", "min_price", "best_price", "last10", "strict"]
    out = []
    for key, title in (("picks", "Picks"), ("leans", "Leans (6-10 points, not picks)")):
        if key not in res:
            continue
        d = pd.DataFrame(res[key])
        out.append(f"{title}: none" if d.empty else f"{title}:\n" + d[[c for c in cols if c in d]].to_string(index=False))
    tail = f"\n{res['left_out_15_plus']} props left out for a 15+ point gap."
    if res.get("teams_left_out"):
        tail += "\nTeams left out (starter out, not yet in the model): " + "; ".join(
            f"{t} ({', '.join(n)})" for t, n in res["teams_left_out"].items())
    return "\n\n".join(out) + tail
