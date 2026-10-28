"""Score the NBA pre-tip record. For each game the last edition published before tip-off
counts (D-008). Distributions are scored on players who played (books void the rest);
P(plays) is scored on every forecast player. Results go to reports/nba/record/."""

from __future__ import annotations

import json

import numpy as np
import pandas as pd

from ..config import REPORTS_DIR
from ..dist import Dist
from ..util import iso, utcnow
from . import canon, live

OUT = REPORTS_DIR / "nba" / "record"


def counting_forecasts() -> pd.DataFrame:
    rows = []
    for p in live.edition_files():
        e = json.loads(p.read_text())
        tips = {g["game_id"]: g["tip_utc"] for g in e["games"]}
        for f in e["forecasts"]:
            if e["published_at"] < tips[f["game_id"]]:
                rows.append({**{k: f[k] for k in ("game_id", "player_id", "name", "team", "model",
                                                  "market", "p_play", "mean", "median", "dist")},
                             "edition_id": e["edition_id"], "published_at": e["published_at"]})
    if not rows:
        return pd.DataFrame()
    d = pd.DataFrame(rows).sort_values("published_at")
    return d.drop_duplicates(["game_id", "player_id", "model", "market"], keep="last")


def run() -> dict:
    fc = counting_forecasts()
    if fc.empty:
        return {"forecasts": 0}
    L = canon.load()
    games, pg = L["games"], L["player_games"]
    final = set(games.loc[games.is_final, "game_id"])
    fc = fc[fc.game_id.isin(final)].copy()
    if fc.empty:
        return {"forecasts": 0, "note": "no counted game is final yet"}
    out = pg[pg.game_id.isin(final)].set_index(["game_id", "player_id"])
    part = [out.participation.get((g, p), "absent") for g, p in
            zip(fc.game_id, fc.player_id, strict=True)]
    fc["participation"] = part
    stat = {"pts": "pts", "reb": "reb", "ast": "ast", "fg3m": "fg3m"}
    vals = []
    for g, p, mk, pa in zip(fc.game_id, fc.player_id, fc.market, fc.participation, strict=True):
        if pa != "played":
            vals.append(np.nan)
            continue
        r = out.loc[(g, p)]
        vals.append(float(r.pts + r.reb + r.ast) if mk == "pra" else float(r[stat[mk]]))
    fc["actual"] = vals
    fc["crps"] = [Dist.from_json(d).crps(y) if not np.isnan(y) else np.nan
                  for d, y in zip(fc.dist, fc.actual, strict=True)]
    played = fc.participation.eq("played")
    by = (fc[played].groupby(["model", "market"]).crps.agg(["mean", "size"]).round(4)
          .reset_index().to_dict("records"))
    pp = fc[(fc.model == "N1") & (fc.market == "pts")]
    y = pp.participation.eq("played").to_numpy()
    q = pp.p_play.clip(1e-4, 1 - 1e-4).to_numpy()
    summary = {"settled_at": iso(utcnow()), "games": int(fc.game_id.nunique()),
               "forecasts": int(len(fc)), "scored": int(played.sum()),
               "participation": fc.drop_duplicates(["game_id", "player_id"]).participation
               .value_counts().to_dict(),
               "p_play_brier": float(((q - y) ** 2).mean()) if len(q) else None,
               "by_model_market": by}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "summary.json").write_text(json.dumps(summary, indent=1, default=float) + "\n")
    fc.drop(columns=["dist"]).to_csv(OUT / "prospective_ledger.csv", index=False)
    return summary
