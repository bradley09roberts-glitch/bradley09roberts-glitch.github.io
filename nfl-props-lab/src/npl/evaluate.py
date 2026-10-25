"""Evaluation reports from a backtest ledger. Every number is recomputed from the ledger
file alone, so anyone holding the ledger can reproduce the report."""

from __future__ import annotations

import json
import shutil  # noqa: F401
from pathlib import Path

import numpy as np
import pandas as pd

from .config import REPORTS_DIR

PRIMARY = "crps"


def load_ledger(path: Path) -> pd.DataFrame:
    return pd.read_csv(path, low_memory=False)


def common_subset(led: pd.DataFrame, models: list[str]) -> pd.DataFrame:
    """Rows (game, player, market) scored by every model in `models`."""
    s = led[led.scored & led.model.isin(models)]
    k = s.groupby(["game_id", "player_id", "market"]).model.nunique()
    keep = k[k == len(models)].index
    return s.set_index(["game_id", "player_id", "market"]).loc[keep].reset_index()


def week_block_ci(diff: pd.Series, weeks: pd.Series, n: int = 2000, seed: int = 0):
    """95% interval for a mean difference, resampling whole (season, week) blocks."""
    g = pd.DataFrame({"d": diff.to_numpy(), "w": weeks.to_numpy()}).groupby("w").d.agg(["sum",
                                                                                         "size"])
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(g), size=(n, len(g)))
    s, c = g["sum"].to_numpy()[idx].sum(1), g["size"].to_numpy()[idx].sum(1)
    m = s / c
    return float(np.percentile(m, 2.5)), float(np.percentile(m, 97.5))


def pit_hist(s: pd.DataFrame, bins: int = 10) -> list[float]:
    """Randomised PIT for integer outcomes: u ~ U(F(y-1), F(y)). Uniform if calibrated."""
    rng = np.random.default_rng(1)
    u = s.pit_lo + (s.pit_hi - s.pit_lo) * rng.random(len(s))
    h, _ = np.histogram(u, bins=bins, range=(0, 1))
    return (h / max(len(s), 1)).round(4).tolist()


def milestone_reliability(s: pd.DataFrame, bins=(0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1.0)):
    """Binary calibration at fixed milestone thresholds (not bookmaker lines), for one
    market's rows."""
    rows = []
    for c in [c for c in s.columns if c.startswith("p_ge_") and s[c].notna().any()]:
        k = c[5:]
        y = s.get(f"y_ge_{k}")
        d = pd.DataFrame({"p": s[c], "y": y}).dropna()
        if d.empty:
            continue
        d["bin"] = pd.cut(d.p, bins, include_lowest=True)
        for b, g in d.groupby("bin", observed=True):
            rows.append({"threshold": int(float(k)), "bin": str(b), "n": len(g),
                         "mean_p": round(g.p.mean(), 4), "freq": round(g.y.mean(), 4)})
        p = d.p.clip(1e-4, 1 - 1e-4)
        rows.append({"threshold": int(float(k)), "bin": "ALL", "n": len(d),
                     "brier": round(float(((d.p - d.y) ** 2).mean()), 5),
                     "log_loss": round(float(-(d.y * np.log(p) + (1 - d.y) *
                                               np.log(1 - p)).mean()), 5),
                     "mean_p": round(d.p.mean(), 4), "freq": round(d.y.mean(), 4)})
    return rows


def compare(led: pd.DataFrame, models: list[str], baseline: str = "A") -> dict:
    out = {"models": models, "baseline": baseline, "primary_metric": PRIMARY, "markets": {}}
    led = led.copy()
    led["week_key"] = led.season.astype(int) * 100 + led.week.astype(int)
    for mk, gm in led.groupby("market"):
        cs = common_subset(gm, models)
        if cs.empty:
            continue
        res = {"n_common": int(cs.groupby("model").size().iloc[0]),
               "n_weeks": int(cs.week_key.nunique()), "models": {}}
        base = cs[cs.model == baseline].set_index(["game_id", "player_id"])
        for m, g in cs.groupby("model"):
            g = g.assign(err=g["mean"] - g.actual, aerr=(g["median"] - g.actual).abs())
            r = {"crps": round(g.crps.mean(), 4), "log_score": round(g.log_score.mean(), 4),
                 "mae_median": round(g.aerr.mean(), 3), "bias_mean": round(g.err.mean(), 3),
                 "rmse_mean": round(float(np.sqrt((g.err ** 2).mean())), 3),
                 "cov50": round(g.in50.mean(), 4), "cov80": round(g.in80.mean(), 4),
                 "cov90": round(g.in90.mean(), 4), "pit_deciles": pit_hist(g)}
            if m != baseline and len(base):
                gi = g.set_index(["game_id", "player_id"])
                d = gi.crps - base.crps.reindex(gi.index)
                lo, hi = week_block_ci(d, gi.week_key)
                r["crps_minus_baseline"] = round(float(d.mean()), 4)
                r["crps_minus_baseline_95ci"] = [round(lo, 4), round(hi, 4)]
                r["crps_skill_vs_baseline"] = round(float(1 - g.crps.mean() /
                                                          base.crps.mean()), 4)
            res["models"][m] = r
        out["markets"][mk] = res
    return out


def breakdown(led: pd.DataFrame, models: list[str], by: str) -> pd.DataFrame:
    rows = []
    for mk, gm in led.groupby("market"):
        cs = common_subset(gm, models)
        if cs.empty:
            continue
        for (m, k), g in cs.groupby(["model", by]):
            rows.append({"market": mk, "model": m, by: k, "n": len(g),
                         "crps": round(g.crps.mean(), 4),
                         "bias": round((g["mean"] - g.actual).mean(), 3),
                         "cov80": round(g.in80.mean(), 3)})
    return pd.DataFrame(rows)


def _team_change_flag(led: pd.DataFrame) -> pd.Series:
    """Sensitivity flag: the player's previous game (any participation) was for a different
    team, i.e. a trade, release or signing since his last appearance."""
    from .config import LAKE_DIR

    p = LAKE_DIR / "player_games.parquet"
    if not p.exists():
        return pd.Series("unknown", index=led.index)
    pg = pd.read_parquet(p, columns=["player_id", "game_id", "team", "season", "week",
                                     "participation"])
    pg = pg[pg.participation == "played"].sort_values(["season", "week"])
    pg["prev_team"] = pg.groupby("player_id").team.shift(1)
    # forecasts are made for games the player may not have played; use his last played
    # game strictly before the target slate
    last = pg[["player_id", "season", "week", "team"]].rename(columns={"team": "t"})
    key = led[["player_id", "season", "week", "team"]].copy()
    key["k"] = (key.season * 100 + key.week).astype("int64")
    last["k"] = (last.season * 100 + last.week).astype("int64")
    last = last.sort_values("k")
    key = key.reset_index().sort_values("k")
    m = pd.merge_asof(key, last[["player_id", "k", "t"]], on="k", by="player_id",
                      allow_exact_matches=False)
    flag = np.where(m.t.isna(), "no previous game",
                    np.where(m.t == m.team, "same team", "changed team"))
    return pd.Series(flag, index=m["index"]).reindex(led.index)


def write_report(run_dir: Path, models: list[str], baseline: str = "A",
                 label: str | None = None, headline: bool = False) -> Path:
    """Summaries are always written to reports/. The full ledger is copied (compactly)
    only for headline runs; other experiments keep theirs in data/runs and record its
    SHA-256 in the committed manifest."""
    run_dir = Path(run_dir)
    manifest = json.loads((run_dir / "manifest.json").read_text())
    led = load_ledger(run_dir / "ledger.csv.gz")
    led["history_band"] = pd.cut(led.games_in_history.fillna(0), [-1, 2, 8, 24, 1e9],
                                 labels=["0-2 games", "3-8", "9-24", "25+"]).astype(str)
    led["usage"] = np.where(led.p_play.fillna(0) >= 0.8, "regular (p_play>=0.8)",
                            "reserve/uncertain")
    out = REPORTS_DIR / "backtests" / (label or manifest["run_id"])
    out.mkdir(parents=True, exist_ok=True)
    summ = compare(led, models, baseline)
    summ["run"] = {k: manifest[k] for k in ("run_id", "label", "created_at", "n_sims", "seed",
                                            "model_version", "code", "ledger_sha256",
                                            "splits", "rows")}
    summ["slates"] = len(manifest["slates"])
    summ["exclusions"] = manifest["exclusions"]
    sc = led[led.scored]
    summ["milestones"] = {m: {mk: milestone_reliability(gg) for mk, gg in g.groupby("market")}
                          for m, g in sc.groupby("model")}
    (out / "summary.json").write_text(json.dumps(summ, indent=1, default=str))
    led["role_change"] = _team_change_flag(led)
    for by in ("season", "position", "usage", "history_band", "season_type", "team",
               "role_change"):
        breakdown(led, models, by).to_csv(out / f"by_{by}.csv", index=False)
    if headline:
        led.drop(columns=["history_band", "usage", "role_change"]).to_csv(out / "ledger.csv.gz", index=False,
                                                           float_format="%.6g")
    shutil.copy(run_dir / "manifest.json", out / "manifest.json")
    (out / "README.md").write_text(markdown(summ))
    return out


def markdown(s: dict) -> str:
    L = [f"# Backtest {s['run']['run_id']}", "",
         f"Label `{s['run']['label']}`, {s['slates']} slates, {s['run']['rows']:,} ledger rows, "
         f"{s['run']['n_sims']:,} simulations per game (seed {s['run']['seed']}), model "
         f"v{s['run']['model_version']}, code {s['run']['code'].get('git_commit', '')[:10]}"
         f"{' (dirty)' if s['run']['code'].get('dirty') else ''}.", "",
         "Retrospective walk-forward research: each slate was forecast with history before "
         "that week only. This is not a published pre-match record.", "",
         f"Primary metric: mean CRPS (lower is better) on the common subset scored by every "
         f"model; differences vs baseline `{s['baseline']}` with 95% intervals from "
         "resampling whole weeks.", ""]
    for mk, r in s["markets"].items():
        L += [f"## {mk} (n = {r['n_common']:,} player-games, {r['n_weeks']} weeks)", "",
              "| model | CRPS | vs baseline (95% CI) | skill | log score | MAE | bias | "
              "cov50 | cov80 | cov90 |", "|---|---|---|---|---|---|---|---|---|---|"]
        for m, x in sorted(r["models"].items(), key=lambda kv: kv[1]["crps"]):
            ci = x.get("crps_minus_baseline_95ci")
            diff = (f"{x['crps_minus_baseline']:+.3f} [{ci[0]:+.3f}, {ci[1]:+.3f}]"
                    if ci else "baseline")
            sk = f"{x['crps_skill_vs_baseline']:+.1%}" if "crps_skill_vs_baseline" in x else ""
            L.append(f"| {m} | {x['crps']:.3f} | {diff} | {sk} | {x['log_score']:.3f} | "
                     f"{x['mae_median']:.2f} | {x['bias_mean']:+.2f} | {x['cov50']:.2f} | "
                     f"{x['cov80']:.2f} | {x['cov90']:.2f} |")
        L.append("")
    L += ["## Exclusions (rows not scored, by model / participation / status)", ""]
    ex = pd.DataFrame(s["exclusions"])
    if len(ex):
        L.append(ex.pivot_table(index=["participation", "status"], columns="model",
                                values="n", aggfunc="sum").fillna(0).astype(int).to_markdown())
    return "\n".join(L) + "\n"
