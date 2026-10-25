"""Team defence profiles: how each defence does against the pass, the run, each receiving
position and quarterback runs, compared with what the same opponents managed in their
other games (an opponent adjustment), using only games before the chosen week.

For a metric num/den (e.g. receiving yards to TEs / targets to TEs) and defence D:
  actual    = sum(num) / sum(den) over D's games
  expected  = den-weighted mean, over those games, of the opponent offence's own rate in
              its other games this season before the week (league rate if it has < 2)
  diff      = actual - expected            (negative = allowed less than usual)
  shrunk    = diff * n / (n + K)           (expected difference over the rest of the season;
              K per metric measured by `stability` on 2012-2021: how much an early
              difference carried over)
  p_better  = P(truly better than average), normal-normal model with skill sd = sigma/sqrt(K)
  verdict   = 'likely strong'/'likely weak' when p_better >= 0.8 / <= 0.2,
              otherwise 'can't tell yet'
Ranks 1-32 order teams by the shrunk difference (1 = best defence on that metric).

Descriptive context. The forecasts use team-level opponent factors (plays, pass rate,
sack rate, yards per completion, catch rate, yards per carry); whether position splits
improve forecasts is tested separately (reports/EXPERIMENTS.md).
"""

from __future__ import annotations

import json
from dataclasses import dataclass

import numpy as np
import pandas as pd
from scipy.stats import norm

from .config import PROJECT_ROOT
from .features import ROLE_POS

K_SHRINK = 3.0  # fallback when no calibration file exists
P_LIKELY = 0.8
CALIBRATION = PROJECT_ROOT / "reports" / "defense" / "stability.json"
POSITIONS = ("WR", "TE", "RB")


@dataclass(frozen=True)
class Metric:
    key: str
    facet: str
    label: str
    num: str
    den: str  # column name, or "games" for per-game metrics
    better: str  # "lower" or "higher" for the defence
    fmt: str  # "pct", "dec1", "dec2", "int"
    headline: bool = False


METRICS: tuple[Metric, ...] = (
    Metric("pts_pg", "overall", "Points allowed per game", "points", "games", "lower", "dec1", True),
    Metric("ypp", "overall", "Yards per play allowed", "tot_yds", "plays", "lower", "dec2"),
    Metric("net_ypd", "pass", "Net yards per dropback", "net_pass_yds", "db", "lower", "dec2", True),
    Metric("cmp_pct", "pass", "Completion rate allowed", "cmp", "att", "lower", "pct"),
    Metric("pass_ypg", "pass", "Passing yards allowed per game", "pass_yds", "games", "lower", "dec1"),
    Metric("int_rate", "pass", "Interception rate", "ints", "att", "higher", "pct"),
    Metric("sack_rate", "rush", "Sack rate (per dropback)", "sacks", "db", "higher", "pct", True),
    Metric("ypc", "run", "Yards per designed run (non-QB)", "dr_yds", "dr", "lower", "dec2", True),
    Metric("expl_run", "run", "Runs of 10+ yards", "dr_10", "dr", "lower", "pct"),
    Metric("rush_ypg", "run", "Non-QB rushing yards per game", "dr_yds", "games", "lower", "dec1"),
    Metric("qb_rush_ypg", "qb", "QB rushing yards per game", "qb_run_yds", "games", "lower", "dec1",
           True),
) + tuple(
    m for p in POSITIONS for m in (
        Metric(f"ypt_{p}", f"vs_{p}", f"Yards per target to {p}s", f"yds_{p}", f"tgt_{p}", "lower",
               "dec2", True),
        Metric(f"catch_{p}", f"vs_{p}", f"Catch rate allowed to {p}s", f"rec_{p}", f"tgt_{p}",
               "lower", "pct"),
        Metric(f"ypg_{p}", f"vs_{p}", f"Receiving yards to {p}s per game", f"yds_{p}", "games",
               "lower", "dec1"),
        Metric(f"tpg_{p}", f"vs_{p}", f"Targets to {p}s per game", f"tgt_{p}", "games", "lower",
               "dec1"),
    )
)
FACETS = {"overall": "Overall", "pass": "Pass defence", "rush": "Pass rush", "run": "Run defence",
          "qb": "QB runs", "vs_WR": "v wide receivers", "vs_TE": "v tight ends",
          "vs_RB": "v running backs (receiving)"}
HEAT_COLUMNS = ("ypp", "net_ypd", "sack_rate", "ypc", "qb_rush_ypg", "ypg_WR", "ypg_TE",
                "ypg_RB")


def position_map(pg: pd.DataFrame) -> pd.Series:
    """player_id -> WR/TE/RB/QB from the latest roster or stats position."""
    d = pg[pg.position.isin(ROLE_POS)].sort_values(["season", "week"])
    return d.groupby("player_id").position.last().map(ROLE_POS)


def game_frame(plays: pd.DataFrame, tg: pd.DataFrame, pos: pd.Series) -> pd.DataFrame:
    """One row per (game, offence, defence) with the totals every metric needs."""
    p = plays.copy()
    rp = p.receiver_player_id.map(pos)
    rr = p.rusher_player_id.map(pos)
    comp = p.kind == "completion"
    tgt = p.kind.isin(["completion", "incompletion"]) & p.receiver_player_id.notna()
    dr = (p.kind == "rush") & (rr != "QB")
    qbr = ((p.kind == "rush") & (rr == "QB")) | (p.kind == "scramble")
    cols = {
        "db": p.kind.isin(["completion", "incompletion", "sack", "scramble", "spike"]),
        "att": p.kind.isin(["completion", "incompletion"]),
        "cmp": comp,
        "pass_yds": np.where(comp, p.receiving_yards.fillna(0), 0.0),
        "sacks": p.kind == "sack",
        "sack_yds": np.where(p.kind == "sack", p.yards_gained.fillna(0), 0.0),
        "scr_yds": np.where(p.kind == "scramble", p.rushing_yards.fillna(0), 0.0),
        "ints": p.interception.fillna(0) == 1,
        "dr": dr,
        "dr_yds": np.where(dr, p.rushing_yards.fillna(0), 0.0),
        "dr_10": dr & (p.rushing_yards.fillna(0) >= 10),
        "qb_run_yds": np.where(qbr, p.rushing_yards.fillna(0), 0.0),
        "plays": p.kind.isin(["completion", "incompletion", "sack", "scramble", "spike", "rush"]),
        "all_rush_yds": np.where(p.kind.isin(["rush"]), p.rushing_yards.fillna(0), 0.0),
    }
    for P in POSITIONS:
        cols[f"tgt_{P}"] = tgt & (rp == P)
        cols[f"rec_{P}"] = comp & (rp == P)
        cols[f"yds_{P}"] = np.where(comp & (rp == P), p.receiving_yards.fillna(0), 0.0)
    f = pd.DataFrame({k: np.asarray(v, dtype=float) for k, v in cols.items()})
    f[["game_id", "season", "week", "offense", "defense"]] = p[
        ["game_id", "season", "week", "posteam", "defteam"]].to_numpy()
    g = f.groupby(["game_id", "season", "week", "offense", "defense"], as_index=False).sum()
    g["net_pass_yds"] = g.pass_yds + g.sack_yds + g.scr_yds
    g["tot_yds"] = g.net_pass_yds + g.all_rush_yds
    g["games"] = 1.0
    pts = tg[["game_id", "team", "points_for"]].rename(columns={"team": "offense",
                                                                 "points_for": "points"})
    g = g.merge(pts, on=["game_id", "offense"], how="left")
    return g


def _metric_rows(cur: pd.DataFrame, base: pd.DataFrame, m: Metric) -> pd.DataFrame:
    """One metric for every defence in `cur`; opponents' usual rates come from `base`
    (excluding the game itself when it is in `base`)."""
    per_game = m.den == "games"
    num_c = cur[m.num].to_numpy(dtype=float)
    den_c = np.ones(len(cur)) if per_game else cur[m.den].to_numpy(dtype=float)
    den_b = base["games"] if per_game else base[m.den]
    league = base[m.num].sum() / den_b.sum() if den_b.sum() > 0 else np.nan
    bt = base.assign(_den=den_b).groupby("offense")
    in_base = cur.game_id.isin(base.game_id).to_numpy(dtype=float)
    num_o = cur.offense.map(bt[m.num].sum()).fillna(0).to_numpy() - num_c * in_base
    den_o = cur.offense.map(bt["_den"].sum()).fillna(0).to_numpy() - den_c * in_base
    n_o = cur.offense.map(bt.size()).fillna(0).to_numpy() - in_base
    exp = np.where((n_o >= 2) & (den_o > 0), num_o / np.where(den_o > 0, den_o, 1), league)
    res = np.where(den_c > 0, num_c / np.where(den_c > 0, den_c, 1) - exp, np.nan)
    f = pd.DataFrame({"team": cur.defense.to_numpy(), "num": num_c, "den": den_c,
                      "wexp": den_c * exp, "res": res})
    a = f.groupby("team").agg(num=("num", "sum"), den=("den", "sum"), wexp=("wexp", "sum"),
                              games=("num", "size"), res_sd=("res", "std"))
    a = a[a.den > 0].reset_index()
    a["actual"] = a.num / a.den
    a["expected"] = a.wexp / a.den
    a["diff"] = a.actual - a.expected
    a["league"] = league
    a["volume"] = a.den
    for k in ("key", "facet", "label", "better", "fmt"):
        a[k if k != "key" else "metric"] = getattr(m, k)
    return a.drop(columns=["num", "den", "wexp"])


def _rows(cur: pd.DataFrame, base: pd.DataFrame | None = None) -> pd.DataFrame:
    base = cur if base is None else base
    if cur.empty:
        return pd.DataFrame()
    return pd.concat([_metric_rows(cur, base, m) for m in METRICS], ignore_index=True)


def _regular(g: pd.DataFrame, games: pd.DataFrame) -> pd.DataFrame:
    reg = set(games.loc[games.season_type == "REG", "game_id"])
    return g[g.game_id.isin(reg)]


def load_calibration() -> dict:
    """Per-metric shrinkage K and per-game noise sd measured by `stability` (if run)."""
    try:
        return json.loads(CALIBRATION.read_text())["metrics"]
    except (OSError, ValueError, KeyError):
        return {}


def _shrink(rows: pd.DataFrame, cal: dict) -> pd.DataFrame:
    """Shrunk difference = expected rest-of-season difference (normal-normal model with
    K = noise variance / skill variance), and the probability that the defence is truly
    better than average on the metric."""
    rows = rows.copy()
    K = rows.metric.map(lambda k: cal.get(k, {}).get("K", K_SHRINK)).astype(float)
    pooled = rows.groupby("metric").res_sd.transform(lambda x: np.sqrt(np.nanmean(x ** 2)))
    sd = rows.metric.map(lambda k: cal.get(k, {}).get("sigma", np.nan)).astype(float)
    sd = sd.fillna(pooled)
    n = rows.games.astype(float)
    rows["k_shrink"] = K
    rows["shrunk"] = rows["diff"] * n / (n + K)
    rows["se"] = sd / np.sqrt(n + K)
    z = rows.shrunk / rows.se.replace(0, np.nan)
    p_low = pd.Series(norm.cdf(-z), index=rows.index)
    rows["p_better"] = np.where(rows.better == "lower", p_low, 1 - p_low)
    rows["verdict"] = np.select([rows.p_better >= P_LIKELY, rows.p_better <= 1 - P_LIKELY],
                                ["likely strong", "likely weak"], "can't tell yet")
    rows["rank"] = 0
    for _, x in rows.groupby("metric"):
        asc = x.better.iloc[0] == "lower"
        rows.loc[x.index, "rank"] = x.shrunk.rank(ascending=asc, method="min").astype(int)
    return rows


def profiles(lake: dict, season: int, week: int | None) -> dict:
    """Profiles for all teams using regular-season games of `season` before `week` (all if
    None), plus the full previous regular season for context."""
    pos = position_map(lake["player_games"])
    pl = lake["plays"]
    pl = pl[pl.season.isin([season, season - 1])]
    g = _regular(game_frame(pl, lake["team_games"], pos), lake["games"])
    cal = load_calibration()
    cur_g = g[(g.season == season) & ((g.week < week) if week else True)]
    cur = _rows(cur_g)
    prev = _rows(g[g.season == season - 1])
    out = {"season": season, "before_week": week, "calibrated": bool(cal),
           "p_likely": P_LIKELY, "teams": {}}
    if cur.empty:
        return out
    cur = _shrink(cur, cal)
    prev_r = (_shrink(prev, cal).set_index(["team", "metric"])["rank"] if len(prev)
              else pd.Series(dtype=int))
    for t, x in cur.groupby("team"):
        mets = {}
        for r in x.itertuples():
            pr = prev_r.get((t, r.metric))
            mets[r.metric] = {
                "label": r.label, "facet": r.facet, "better": r.better, "fmt": r.fmt,
                "games": int(r.games), "actual": r.actual, "expected": r.expected,
                "diff": r.diff, "shrunk": r.shrunk, "se": r.se, "k_shrink": r.k_shrink,
                "p_better": float(r.p_better), "league": r.league, "rank": int(r.rank),
                "verdict": r.verdict, "volume": r.volume,
                "prev_rank": int(pr) if pr is not None and not pd.isna(pr) else None}
        out["teams"][t] = {"team": t, "games": int(x.games.max()), "metrics": mets,
                           "summary": summarise(mets)}
    return out


def stability(lake: dict, fit_seasons: range, check_seasons: range,
              cutoffs: tuple[int, ...] = (5, 8, 11, 14)) -> dict:
    """How much of an early-season defensive difference carries over to the rest of the
    same regular season. For each season and cutoff week: the point-in-time difference
    from games before the cutoff (opponents' rates also before the cutoff, as live) and
    the difference over the remaining games (opponents' rates from the whole season).
    K per metric is fitted on `fit_seasons` by least squares of rest = before * n/(n+K);
    `check_seasons` are scored out of sample."""
    pos = position_map(lake["player_games"])
    seasons = sorted(set(fit_seasons) | set(check_seasons))
    pl = lake["plays"]
    g = _regular(game_frame(pl[pl.season.isin(seasons)], lake["team_games"], pos), lake["games"])
    pairs = []
    for s in seasons:
        gs = g[g.season == s]
        full = gs
        for c in cutoffs:
            pre_g, post_g = gs[gs.week < c], gs[gs.week >= c]
            if pre_g.empty or post_g.empty:
                continue
            pre = _rows(pre_g)
            post = _rows(post_g, full)
            j = pre.merge(post[["team", "metric", "diff", "games"]], on=["team", "metric"],
                          suffixes=("", "_post"))
            j["season"], j["cutoff"] = s, c
            pairs.append(j)
        sd = _rows(full)[["metric", "res_sd"]].assign(season=s)
        pairs.append(sd.assign(kind="sigma"))
    allp = pd.concat(pairs, ignore_index=True)
    sig = allp[allp.get("kind") == "sigma"]
    pr = allp[allp.get("kind") != "sigma"].dropna(subset=["diff", "diff_post"])
    grid = np.unique(np.concatenate([np.arange(0.5, 10, 0.5), np.arange(10, 60, 2),
                                     np.arange(60, 401, 20)]))
    out = {}
    for m in METRICS:
        x = pr[pr.metric == m.key]
        fit = x[x.season.isin(fit_seasons)]
        chk = x[x.season.isin(check_seasons)]
        sd_fit = sig.loc[(sig.metric == m.key) & sig.season.isin(fit_seasons), "res_sd"]
        sigma = float(np.sqrt(np.nanmean(sd_fit ** 2)))

        def sse(K, d):
            return float(((d.diff_post - d["diff"] * d.games / (d.games + K)) ** 2).sum())
        K = float(grid[np.argmin([sse(k, fit) for k in grid])])
        mse0 = float((chk.diff_post ** 2).mean())
        mse_raw = float(((chk.diff_post - chk["diff"]) ** 2).mean())
        mse_k = sse(K, chk) / len(chk) if len(chk) else np.nan
        c7 = chk[chk.cutoff == 8]
        r7 = float(np.corrcoef(c7["diff"], c7.diff_post)[0, 1]) if len(c7) > 2 else np.nan
        # label check: P(truly better) >= P_LIKELY at the cutoff -> was the rest of the
        # season on the better side?
        cs = _shrink(chk.drop(columns=["shrunk", "se", "verdict", "rank"], errors="ignore"),
                     {m.key: {"K": K, "sigma": sigma}})
        sign = -1 if m.better == "lower" else 1
        lab = cs[cs.verdict != "can't tell yet"]
        good_side = np.where(lab.verdict == "likely strong", sign * lab.diff_post > 0,
                             sign * lab.diff_post < 0)
        # what a calibrated label should achieve given the noise in the remaining games
        z_post = lab.shrunk.abs() / np.sqrt(lab.se ** 2 + sigma ** 2 / lab.games_post)
        out[m.key] = {
            "label": m.label, "facet": m.facet, "K": K, "sigma": sigma,
            "carry_over_6g": 6 / (6 + K), "r_week8": r7,
            "skill_vs_average": 1 - mse_k / mse0 if mse0 else np.nan,
            "skill_raw_vs_average": 1 - mse_raw / mse0 if mse0 else np.nan,
            "n_fit": int(len(fit)), "n_check": int(len(chk)),
            "labels_check": int(len(lab)),
            "labels_held": float(np.mean(good_side)) if len(lab) else None,
            "labels_expected": float(np.mean(norm.cdf(z_post))) if len(lab) else None,
            "label_rate": float(len(lab) / len(chk)) if len(chk) else None}
    return {"fit_seasons": [min(fit_seasons), max(fit_seasons)],
            "check_seasons": [min(check_seasons), max(check_seasons)],
            "cutoffs": list(cutoffs), "p_likely": P_LIKELY, "metrics": out}


def summarise(mets: dict) -> dict:
    """Plain-language strengths and weaknesses (only where the evidence is clear)."""
    strong = [m["label"] for k, m in mets.items() if k in HEAT_COLUMNS and
              m["verdict"] == "likely strong"]
    weak = [m["label"] for k, m in mets.items() if k in HEAT_COLUMNS and
            m["verdict"] == "likely weak"]
    overall = mets.get("ypp", {}).get("verdict")
    if strong and weak:
        tag = "mixed: clear strengths and clear weaknesses"
    elif overall == "likely strong" and len(strong) >= 3:
        tag = "strong across the board"
    elif overall == "likely weak" and len(weak) >= 3:
        tag = "weak across the board"
    elif overall == "likely strong":
        tag = "strong overall"
    elif overall == "likely weak":
        tag = "weak overall"
    elif strong:
        tag = "some clear strengths"
    elif weak:
        tag = "some clear weaknesses"
    else:
        tag = "no clear strengths or weaknesses yet"
    return {"strong": strong, "weak": weak, "tag": tag}


def position_factors(prof: dict, team: str, position: str) -> dict | None:
    """Shrunk ratios (actual/expected) for a defence against one receiving position:
    yards per target and target share proxy. Used by the experimental model variant."""
    t = prof["teams"].get(team)
    if not t:
        return None
    m = t["metrics"]
    out = {}
    for key in (f"ypt_{position}", f"tpg_{position}"):
        x = m.get(key)
        if not x or not x["expected"]:
            continue
        ratio = (x["expected"] + x["shrunk"]) / x["expected"]
        out[key] = float(np.clip(ratio, 0.7, 1.3))
    return out


def write_stability(st: dict, out_dir=CALIBRATION.parent) -> None:
    """Save the calibration (read by `profiles`) and a readable report."""
    from .edition import code_version

    out_dir.mkdir(parents=True, exist_ok=True)
    st = {**st, "code": code_version()}
    (out_dir / "stability.json").write_text(json.dumps(st, indent=1, default=float) + "\n")
    f0, f1 = st["fit_seasons"]
    c0, c1 = st["check_seasons"]
    lines = [
        "# How much a defensive difference carries over",
        "",
        f"Regular seasons; K fitted on {f0}-{f1}, everything else scored on {c0}-{c1} "
        f"(out of sample). Cutoff weeks {', '.join(map(str, st['cutoffs']))}: the difference "
        "from games before the cutoff (opponents also measured before it, as in the app) "
        "against the difference over the rest of that season. Code "
        f"{st['code']['git_commit'][:7]}{' (uncommitted changes)' if st['code']['dirty'] else ''}.",
        "",
        "* **Carries over after 6 games** = 6/(6+K): the share of an observed difference "
        "expected to persist for the rest of the season.",
        "* **Skill** = how much better the shrunk difference predicts the rest of the season "
        "than assuming every defence is average (1 - MSE ratio). **Raw** = the same for the "
        "unshrunk difference; negative means taking early numbers at face value is worse "
        "than ignoring them.",
        "* **Labels held** = of 'likely strong/weak' labels at a cutoff, the share whose "
        "remaining games were on the same side of average; **expected** = what a calibrated "
        "label should achieve given the noise in those remaining games.",
        "",
        "| Measure | Area | K | Carries over after 6 games | Week-8 correlation | Skill | "
        "Raw | Labels | Labels held | Expected |",
        "|---|---|---|---|---|---|---|---|---|---|",
    ]
    for m in st["metrics"].values():
        held = f"{m['labels_held']:.0%}" if m["labels_held"] is not None else "-"
        exp = f"{m['labels_expected']:.0%}" if m["labels_expected"] is not None else "-"
        lines.append(
            f"| {m['label']} | {FACETS[m['facet']]} | {m['K']:.0f} | {m['carry_over_6g']:.0%} "
            f"| {m['r_week8']:.2f} | {m['skill_vs_average']:.3f} | "
            f"{m['skill_raw_vs_average']:.2f} | {m['labels_check']} | {held} | {exp} |")
    (out_dir / "STABILITY.md").write_text("\n".join(lines) + "\n")
