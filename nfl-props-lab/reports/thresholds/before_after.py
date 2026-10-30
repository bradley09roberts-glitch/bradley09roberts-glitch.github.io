"""Before and after: the pick rules with and without a projection-gap requirement, on every prop
with a stored US line so far (NBA 28 and 29 Oct 2026, NFL W7 DAL@PHI and W8 CAR@GB).

Gap requirement: for each market (NFL: and position group), the smallest gap between our
projection (forecast mean) and the line at which the over (or under) won at least 60% of the time
on the validation seasons, with the line built from our own projection (gap_check.py; optimistic
by construction). Chosen before looking at these nights, so they are a fair check of it.

Rules compared, each on props with an eligible line (plays >= 85%, no injury flag, >= 3 books,
half-point line):
  all       every prop, the model's side by probability v the market (margin removed)
  picks     the pick rules: NBA gap 10-15 points (D-030); NFL 6-15 with J1, M and A agreeing
            (simplified: no NBA minutes cross-check, games-for-team or starter-out checks)
  gap       projection-gap requirement alone (side from the projection v the line)
  both      pick rules and the projection gap on the same side
Units: 1 unit flat at the best US price for that side (research reference; not UK prices).
Outputs aggregates only; per-prop rows (with prices) go to data/runs/, which is not in git.
"""
import json
import sys

import numpy as np
import pandas as pd

from npl import appdata as AP
from npl import db
from npl.dist import Dist
from npl.nba import appdata as A
from npl.nba import canon
from npl.sources import odds_api as oa

GRID = np.arange(-20, 40.5, 0.5)
NFL_GROUPS = {("pass_yds", "QB"), ("pass_att", "QB"), ("pass_cmp", "QB"), ("rush_yds", "RB"), ("rush_yds", "QB"),
              ("rush_att", "RB"), ("rush_att", "QB"), ("rec_yds", "WR/TE"), ("rec_yds", "RB"),
              ("receptions", "WR/TE"), ("receptions", "RB")}
NFL_FLOOR = {"pass_yds": 180, "pass_att": 25, "pass_cmp": 15, ("rush_yds", "RB"): 30, ("rush_yds", "QB"): 8,
             ("rush_att", "RB"): 7, ("rush_att", "QB"): 3, ("rec_yds", "WR/TE"): 25, ("rec_yds", "RB"): 10,
             ("receptions", "WR/TE"): 2, ("receptions", "RB"): 1.5}
NBA_FLOOR = {"pts": 8, "reb": 3, "ast": 2, "fg3m": 0.8, "pra": 12}


def group(pos):
    return {"QB": "QB", "RB": "RB", "FB": "RB", "WR": "WR/TE", "TE": "WR/TE"}.get(pos)


def needed(proj, act, target=0.60):
    over = under = None
    for g in GRID:
        if over is None and (act > np.floor(proj - g - 0.5) + 0.5).mean() >= target:
            over = float(g)
        if under is None and (act < np.ceil(proj + g - 0.5) + 0.5).mean() >= target:
            under = float(g)
    return over, under


def thresholds():
    th = {}
    led = pd.read_csv(sorted(__import__("glob").glob("data/runs/exp11-validation-backup-trim-d0.7-2*/ledger.csv.gz"))[-1],
                      usecols=["model", "market", "position", "mean", "actual", "scored"])
    led = led[(led.model == "X1") & led.scored]
    led["grp"] = led.position.map(group)
    for mk, grp in NFL_GROUPS:
        lo = NFL_FLOOR.get((mk, grp), NFL_FLOOR.get(mk))
        x = led[(led.market == mk) & (led.grp == grp) & (led["mean"] >= lo)]
        th[("nfl", mk, grp)] = needed(x["mean"].to_numpy(), x.actual.to_numpy())
    nb = pd.read_parquet("data/nba/runs/n02-validation-N1-empirical-minutes.parquet")
    nb = nb[(nb.model == "N1") & nb.actual.notna()]
    for mk, lo in NBA_FLOOR.items():
        x = nb[(nb.market == mk) & (nb["mean"] >= lo)]
        th[("nba", mk, None)] = needed(x["mean"].to_numpy(), x.actual.to_numpy())
    return th


def settle(act, line, side):
    if act is None or act == line:
        return None
    return (act > line) == (side == "over")


def nba_rows(path, q, pg):
    e = json.load(open(path))
    tips = {g["game_id"]: pd.Timestamp(g["tip_utc"]) for g in e["games"]}
    qq = q[q.game_id.isin(tips)]
    qq = qq[pd.to_datetime(qq.observed_at, utc=True) < qq.game_id.map(tips)]
    lines = A.consensus_lines(qq)
    rows = []
    for f in e["forecasts"]:
        if f["model"] != e["headline_model"] or (f["player_id"], f["market"]) not in lines:
            continue
        m = lines[(f["player_id"], f["market"])]
        r = pg[(pg.game_id == f["game_id"]) & (pg.player_id == f["player_id"]) & (pg.participation == "played")]
        if r.empty:
            continue
        act = float(r[["pts", "reb", "ast"]].iloc[0].sum()) if f["market"] == "pra" else float(r[f["market"]].iloc[0])
        po = Dist.from_json(f["dist"]).line(m["line"])["over"]
        rows.append({"sport": "nba", "night": e["date_et"], "player": f["name"], "market": f["market"], "grp": None,
                     "line": m["line"], "proj": f["mean"], "p_over": po, "fair_over": m["fair_over"],
                     "best_over": m["best_over"], "best_under": m["best_under"], "actual": act,
                     "eligible": f["p_play"] >= 0.85 and not f.get("injury") and m["books"] >= 3
                     and not float(m["line"]).is_integer(), "agree": True})
    return rows


def nfl_rows(season, week, gid, q, actuals, kick):
    e = AP.latest_edition(season, week)
    fc = {}
    for f in e["forecasts"]:
        if f["status"] == "ok" and f["game_id"] == gid:
            fc.setdefault((f["player_id"], f["market"]), {})[f["model"]] = f
    qq = q[(q.game_id == gid) & q.player_id.notna()]
    qq = qq[pd.to_datetime(qq.observed_at, utc=True) < kick]
    rows = []
    for (pid, mk), x in qq.groupby(["player_id", "market"]):
        fm = fc.get((pid, mk))
        if not fm or "X1" not in fm or mk not in AP.NFL_STAT:
            continue
        x = x[x.observed_at == x.observed_at.max()]
        line = float(x.line.mode().iloc[0])
        y = x[x.line == line].pivot_table(index="bookmaker", columns="selection", values="decimal_price", aggfunc="last").dropna()
        if not {"over", "under"} <= set(y.columns):
            continue
        act = actuals.get(pid, {}).get(mk)
        if act is None:
            continue
        h = fm["X1"]
        po = {m: Dist.from_json(f["dist"]).line(line)["over"] for m, f in fm.items() if f.get("dist")}
        fair = float(((1 / y.over) / (1 / y.over + 1 / y.under)).mean())
        side = "over" if po["X1"] >= fair else "under"
        sp = {m: (v if side == "over" else 1 - v) for m, v in po.items()}
        kp = fair if side == "over" else 1 - fair
        agree = all(k in sp for k in ("J1", "M", "A")) and min(sp["J1"], sp["M"]) - kp >= 0.06 and sp["A"] > 0.5
        rows.append({"sport": "nfl", "night": e["edition_id"][:12], "player": h["name"], "market": mk,
                     "grp": group(h["position"]), "line": line, "proj": h["mean"], "p_over": po["X1"], "fair_over": fair,
                     "best_over": float(y.over.max()), "best_under": float(y.under.max()), "actual": float(act),
                     "eligible": (h.get("p_play") or 0) >= 0.85 and not h.get("injury_report") and len(y) >= 3
                     and not line.is_integer(), "agree": bool(agree)})
    return rows


def nfl_actuals_lake(gid):
    pg = AP.lake()["pg"]
    pg = pg[(pg.game_id == gid) & (pg.participation == "played")]
    return {r.player_id: {mk: float(getattr(r, col)) if pd.notna(getattr(r, col)) else 0.0 for mk, col in AP.NFL_STAT.items()}
            for r in pg.itertuples()}


def nfl_actuals_espn(path):
    box = json.load(open(path))
    pl = AP.lake()["players"]
    e2g = {str(int(x)): g for x, g in zip(pl.espn_id, pl.gsis_id, strict=True) if pd.notna(x)}
    out = {}
    for t in box["boxscore"]["players"]:
        for s in t["statistics"]:
            for a in s["athletes"]:
                gid = e2g.get(str(a["athlete"]["id"]))
                v = dict(zip(s["labels"], a["stats"], strict=False))
                d = out.setdefault(gid, {k: 0.0 for k in AP.NFL_STAT})
                if s["name"] == "passing":
                    c, at = v["C/ATT"].split("/")
                    d.update(pass_cmp=float(c), pass_att=float(at), pass_yds=float(v["YDS"]))
                elif s["name"] == "rushing":
                    d.update(rush_att=float(v["CAR"]), rush_yds=float(v["YDS"]))
                elif s["name"] == "receiving":
                    d.update(receptions=float(v["REC"]), rec_yds=float(v["YDS"]))
    return out


def evaluate(d, th):
    d = d[d.eligible].copy()
    d["side"] = np.where(d.p_over >= d.fair_over, "over", "under")
    d["mp"] = np.where(d.side == "over", d.p_over, 1 - d.p_over)
    d["kp"] = np.where(d.side == "over", d.fair_over, 1 - d.fair_over)
    d["gap"] = d.mp - d.kp
    lo = np.where(d.sport == "nba", 0.10, 0.06)
    d["pick"] = (d.gap >= lo) & (d.gap < 0.15) & d.agree
    g_over = [th.get((s, m, None if s == "nba" else g), (None, None))[0] for s, m, g in zip(d.sport, d.market, d.grp, strict=True)]
    g_under = [th.get((s, m, None if s == "nba" else g), (None, None))[1] for s, m, g in zip(d.sport, d.market, d.grp, strict=True)]
    d["g_over"], d["g_under"] = g_over, g_under
    ov = d.g_over.notna() & (d.proj - d.line >= d.g_over.astype(float))
    un = d.g_under.notna() & (d.line - d.proj >= d.g_under.astype(float))
    d["gap_side"] = np.where(ov & ~un, "over", np.where(un & ~ov, "under", None))
    out = {}
    for name, side_col, mask in (("all", "side", np.ones(len(d), bool)), ("picks", "side", d.pick.to_numpy()),
                                 ("gap", "gap_side", d.gap_side.notna().to_numpy()),
                                 ("both", "side", (d.pick & (d.gap_side == d.side)).to_numpy())):
        x = d[mask]
        res = [settle(a, line, s) for a, line, s in zip(x.actual, x.line, x[side_col], strict=True)]
        price = np.where(x[side_col] == "over", x.best_over, x.best_under)
        won = np.array([r is True for r in res]); lost = np.array([r is False for r in res])
        units = float((won * (price - 1)).sum() - lost.sum())
        n = int(won.sum() + lost.sum())
        out[name] = {"props": n, "won": int(won.sum()), "win_rate": round(won.sum() / n, 3) if n else None,
                     "units": round(units, 2), "roi": round(units / n, 3) if n else None}
    return out, d


def main(out_path, rows_path):
    th = thresholds()
    q = oa.quotes_frame(db.connect())
    L = canon.load()
    rows = []
    rows += nba_rows("records/editions/nba/2026-10-28/NBA-2026-10-28-final-20261028T1908Z.json", q, L["player_games"])
    rows += nba_rows("records/editions/nba/2026-10-29/NBA-2026-10-29-early-20261029T0855Z.json", q, L["player_games"])
    g = AP.lake()["games"].set_index("game_id")
    rows += nfl_rows(2026, 7, "2026_07_DAL_PHI", q, nfl_actuals_lake("2026_07_DAL_PHI"),
                     pd.Timestamp(g.loc["2026_07_DAL_PHI", "kickoff_utc"]))
    rows += nfl_rows(2026, 8, "2026_08_CAR_GB", q, nfl_actuals_espn(sys.argv[3]),
                     pd.Timestamp(g.loc["2026_08_CAR_GB", "kickoff_utc"]))
    d = pd.DataFrame(rows)
    res = {"thresholds_60pct": {f"{s}|{m}|{gr or ''}": {"over": o, "under": u} for (s, m, gr), (o, u) in th.items()}}
    full = []
    for key, x in (("nba_29oct", d[(d.sport == "nba") & (d.night == "2026-10-29")]),
                   ("nfl_w8_car_gb", d[(d.sport == "nfl") & d.night.str.contains("W08")]),
                   ("nba_both_nights", d[d.sport == "nba"]), ("nfl_both_games", d[d.sport == "nfl"])):
        res[key], ev = evaluate(x, th)
        full.append(ev.assign(set=key))
    pd.concat(full).to_csv(rows_path, index=False)
    json.dump(res, open(out_path, "w"), indent=1)
    print(json.dumps(res, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
