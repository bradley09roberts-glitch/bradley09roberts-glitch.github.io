"""n06: does WHERE a player scores, against how the opponent defends those zones, tell us
anything the model does not already know?

N1 already moves each forecast by the opponent's overall points allowed per possession. This
asks about the interaction: a rim scorer against a defence that is weak at the rim, a
three-point shooter against one that is strong on threes, beyond an average player.

For every player-game (2024-2025 validation, N1 points forecasts from n02): the player's shot
mix by zone (exponentially weighted over his previous games, half-life 40) and the opponent's
field-goal percentage allowed by zone (previous team games, half-life 20, shrunk towards the
league with 150 attempts), both strictly before tip. Zone index = expected points per shot for
this player's mix against this defence, divided by the same mix against a league-average defence,
minus the same ratio for a league-average shot mix (so only the player-specific part remains).
Test: regress points residuals (actual - N1 mean) on N1 mean x zone index; slope 1 would mean
the index is fully missing from N1, slope 0 that it adds nothing. Week-block bootstrap.
"""
import json
import sys

import numpy as np
import pandas as pd

from npl.nba import canon, shots

PTS = np.array([2, 2, 2, 3, 3, 3, 3, 3], float)
Z = list(shots.ZONES)


def ew_asof(df, by, cols, hl, tip):
    """Exponentially weighted sums of `cols` per `by` over rows strictly before each row."""
    df = df.sort_values([by, tip]).copy()
    g = df.groupby(by, sort=False)
    out = df[[by, tip]].copy()
    for c in cols:
        s = g[c].transform(lambda v: v.shift().ewm(halflife=hl, adjust=True).mean())
        n = g[c].transform(lambda v: v.shift().notna().ewm(halflife=hl, adjust=True).sum())
        out[c] = s * n  # effective weighted sum
    return out


def build_index():
    S = shots.load()
    L = canon.load()
    tips = L["games"].set_index("game_id").tip_utc
    ps = S["player_shots"].pivot_table(index=["game_id", "player_id"], columns="zone", values=["fga", "fgm"],
                                       aggfunc="sum", fill_value=0)
    ps.columns = [f"{a}_{b}" for a, b in ps.columns]
    ps = ps.reset_index()
    ps["tip"] = ps.game_id.map(tips)
    dz = S["def_shots"].groupby(["game_id", "defense", "zone"])[["fga", "fgm"]].sum().unstack("zone", fill_value=0)
    dz.columns = [f"{a}_{b}" for a, b in dz.columns]
    dz = dz.reset_index()
    dz["tip"] = dz.game_id.map(tips)
    cols = [f"{a}_{z}" for a in ("fga", "fgm") for z in Z]
    for c in cols:
        for d in (ps, dz):
            if c not in d:
                d[c] = 0
    lg_rows = dz[dz.tip.dt.year <= 2023] if (dz.tip.dt.year <= 2023).any() else dz
    lg = np.array([lg_rows[f"fgm_{z}"].sum() / lg_rows[f"fga_{z}"].sum() for z in Z])
    lg_mix = np.array([lg_rows[f"fga_{z}"].sum() for z in Z], float)
    lg_mix /= lg_mix.sum()
    pe = ew_asof(ps.dropna(subset=["tip"]), "player_id", cols, 40, "tip")
    de = ew_asof(dz.dropna(subset=["tip"]), "defense", cols, 20, "tip")
    return pe, de, lg, lg_mix


def index_rows(rows, pe, de, lg, lg_mix, k_def=150.0, min_fga=30.0):
    """rows: player_id, opp, tip. Returns the zone index (NaN without enough history)."""
    r = rows.sort_values("tip").reset_index()
    p = pd.merge_asof(r, pe.rename(columns={"tip": "_t"}).sort_values("_t"), left_on="tip", right_on="_t",
                      by="player_id", allow_exact_matches=False, direction="backward")
    p = pd.merge_asof(p.sort_values("tip"), de.rename(columns={"tip": "_t2", "defense": "opp"}).sort_values("_t2"),
                      left_on="tip", right_on="_t2", by="opp", allow_exact_matches=False, direction="backward",
                      suffixes=("_p", "_d"))
    fga_p = np.column_stack([p[f"fga_{z}_p"].to_numpy(float) for z in Z])
    fga_d = np.column_stack([p[f"fga_{z}_d"].to_numpy(float) for z in Z])
    fgm_d = np.column_stack([p[f"fgm_{z}_d"].to_numpy(float) for z in Z])
    mix = fga_p / fga_p.sum(1, keepdims=True)
    opp = (fgm_d + k_def * lg) / (fga_d + k_def)
    ratio_p = (mix * PTS * opp).sum(1) / (mix * PTS * lg).sum(1)
    ratio_lg = (lg_mix * PTS * opp).sum(1) / (lg_mix * PTS * lg).sum()
    idx = ratio_p - ratio_lg
    ok = (fga_p.sum(1) >= min_fga) & np.isfinite(idx)
    out = pd.Series(np.where(ok, idx, np.nan), index=p["index"])
    mixdf = pd.DataFrame(mix, columns=Z, index=p["index"])
    return out.sort_index(), mixdf.sort_index(), pd.Series(ratio_p - 1, index=p["index"]).sort_index()


def main(out_path):
    pe, de, lg, lg_mix = build_index()
    led = pd.read_parquet("data/nba/runs/n02-validation-N1-empirical-minutes.parquet")
    led = led[(led.model == "N1") & (led.market == "pts")].copy()
    led["player_id"] = led.player_id.str.replace(r"\.0$", "", regex=True)  # old ledger stored floats
    pg = canon.load()["player_games"][["game_id", "player_id", "opp", "tip_utc", "fga", "fta"]]
    d = led.merge(pg, on=["game_id", "player_id"]).rename(columns={"tip_utc": "tip"})
    idx, _, raw = index_rows(d[["player_id", "opp", "tip"]], pe, de, lg, lg_mix)
    d["zone_idx"] = idx.to_numpy()
    d["raw_idx"] = raw.to_numpy()
    d = d[d.zone_idx.notna() & (d["mean"] >= 4)].copy()
    d["resid"] = d.actual - d["mean"]
    d["adj"] = d["mean"] * d.zone_idx
    d["week"] = pd.to_datetime(d.date).dt.strftime("%G-%V")
    rng = np.random.default_rng(6)
    wk = d.week.unique()
    g = {w: x for w, x in d.groupby("week")}

    def slope(x):
        a, r = x.adj.to_numpy(), x.resid.to_numpy()
        return float((a * r).sum() / (a * a).sum())
    boots = [slope(pd.concat([g[w] for w in rng.choice(wk, len(wk))])) for _ in range(500)]
    q = pd.qcut(d.zone_idx, 5, labels=False)
    res = {"rows": int(len(d)), "index_sd": float(d.zone_idx.std()),
           "adj_sd_points": float(d.adj.std()),
           "slope": slope(d), "slope_ci": [float(np.percentile(boots, 2.5)), float(np.percentile(boots, 97.5))],
           "corr": float(np.corrcoef(d.adj, d.resid)[0, 1]),
           "by_quintile": d.groupby(q).agg(idx=("zone_idx", "mean"), adj=("adj", "mean"),
                                           resid=("resid", "mean"), n=("resid", "size")).round(3).to_dict("records"),
           "league_fg_by_zone": dict(zip(Z, np.round(lg, 3).tolist(), strict=True))}
    json.dump(res, open(out_path, "w"), indent=1)
    print(json.dumps(res, indent=1))


if __name__ == "__main__":
    main(sys.argv[1])
