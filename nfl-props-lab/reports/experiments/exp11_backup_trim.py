"""exp11: trim a backup's usage share instead of down-weighting his games (follow-up to exp10).

exp10 gave a player's past games without a key teammate (one on this week's roster, with the same
or a bigger role) a lower weight. That made predictions worse; one suspected reason is that it
thins the player's history, so his share drifts towards the generic role prior. Here those games
keep full weight and the player's share in them is multiplied by `trim` instead
(features.context_shares). exp10's down-weighting is re-run alongside because its first run did
not leave out teammates listed Out or Doubtful on that week's injury report; this run does, for
both approaches, as the live model does.

Same scoring as exp10: for every team-game the shares of the players who actually played are
renormalised and the realised designed runs and targets scored by multinomial log-likelihood
(higher is better). Settings are chosen on 2018-2021 and judged on the 2022-2023 validation
seasons; 2024-2025 (test) are not used.
"""
import json
import sys

import numpy as np
import pandas as pd

from npl import features as F

VARIANTS = {"base": (1.0, 1.0), "w0.5": (0.5, 1.0), "w0.25": (0.25, 1.0),
            "t0.9": (1.0, 0.9), "t0.8": (1.0, 0.8), "t0.7": (1.0, 0.7), "t0.6": (1.0, 0.6),
            "t0.5": (1.0, 0.5)}


def slates(lk, seasons):
    g = lk["games"]
    g = g[g.season.isin(seasons)]
    return sorted({(int(s), int(w)) for s, w in zip(g.season, g.week, strict=True)})


def shrunk(c, pid, col, wcol, prior, k):
    if pid not in c.index or pd.isna(c.at[pid, col]):
        return prior
    w = c.at[pid, wcol]
    w = c.at[pid, "_wsum"] if pd.isna(w) else w
    return (c.at[pid, col] * w + prior * k) / (w + k)


def main(out_path, rows_path):
    lk = F.load_lake()
    P = F.estimate_params(F.History.before(lk, 2018, 1), train_from=2012)
    pri = {r: (v.get("tshare", 0.02), v.get("dshare", 0.02)) for r, v in P.role_priors.items()}
    k = F.HP["share_prior_games"]
    pg_all, plays, mem_all, inj = lk["player_games"], lk["plays"], lk["membership"], lk["injuries"]
    rows = []
    for season, week in slates(lk, range(2018, 2024)):
        h = F.History.before(lk, season, week)
        played = F._played_shares(h)
        ph = F._player_hist(h, P, played)
        mem = mem_all[(mem_all.season == season) & (mem_all.week == week)]
        roster = mem[mem.status.isin(["ACT", "INA"]) & mem.position.isin(F.ROLE_POS)]
        role = dict(zip(roster.player_id, roster.position.map(F.ROLE_POS), strict=False))
        iw = inj[(inj.season == season) & (inj.week == week)]
        out_listed = set(iw.gsis_id[iw.report_status.isin(["Out", "Doubtful"])])
        act = pg_all[(pg_all.season == season) & (pg_all.week == week) & (pg_all.participation == "played")
                     & pg_all.position.isin(F.ROLE_POS)]
        if act.empty:
            continue
        dr = plays[(plays.season == season) & (plays.week == week) & (plays.kind == "rush")].groupby(
            ["game_id", "rusher_player_id"]).size()
        miss = F._context_misses(played, ph, roster, out_listed)
        ctx = {v: F.context_shares(played, ph, roster, lam, out_listed, trim_d=trim, trim_t=trim, misses=miss)
               for v, (lam, trim) in VARIANTS.items()}
        for (gid, team), g in act.groupby(["game_id", "team"]):
            pids = g.player_id.tolist()
            n_d = np.array([dr.get((gid, p), 0) for p in pids], float)
            n_t = g.targets.fillna(0).to_numpy(float)
            r = [role.get(p, F.ROLE_POS.get(pos, "WR")) for p, pos in zip(pids, g.position, strict=True)]
            rec = {"season": season, "week": week, "game_id": gid, "team": team, "n_d": n_d.sum(), "n_t": n_t.sum()}
            base_d = base_t = None
            for v, c in ctx.items():
                sd = np.array([shrunk(c, p, "dshare", "_wsum_d", pri.get(ro, (0.02, 0.02))[1], k) for p, ro in zip(pids, r, strict=True)])
                st = np.array([shrunk(c, p, "tshare", "_wsum_t", pri.get(ro, (0.02, 0.02))[0], k) for p, ro in zip(pids, r, strict=True)])
                sd, st = np.maximum(sd, 1e-4), np.maximum(st, 1e-4)
                pd_, pt = sd / sd.sum(), st / st.sum()
                rec[f"ll_d_{v}"] = float((n_d * np.log(pd_)).sum())
                rec[f"ll_t_{v}"] = float((n_t * np.log(pt)).sum())
                if v == "base":
                    base_d, base_t = pd_, pt
                else:
                    rec[f"chg_{v}"] = bool(np.abs(pd_ - base_d).max() > 0.02 or np.abs(pt - base_t).max() > 0.02)
            rows.append(rec)
        print(season, week, len(rows), file=sys.stderr, flush=True)
    d = pd.DataFrame(rows)
    d.to_parquet(rows_path)
    res = {"variants": {v: {"weight": lam, "trim": t} for v, (lam, t) in VARIANTS.items()}}
    for split, seasons in (("fit_2018_2021", range(2018, 2022)), ("validation_2022_2023", range(2022, 2024))):
        x = d[d.season.isin(seasons)]
        wk = x.season * 100 + x.week
        rng = np.random.default_rng(11)
        weeks = wk.unique()
        r = {}
        for v in list(VARIANTS)[1:]:
            for kind, n in (("d", "n_d"), ("t", "n_t")):
                diff = x[f"ll_{kind}_{v}"] - x[f"ll_{kind}_base"]
                agg = pd.DataFrame({"wk": wk, "diff": diff, "n": x[n]}).groupby("wk")[["diff", "n"]].sum()
                boot = [agg.loc[rng.choice(weeks, len(weeks))].pipe(lambda a: a["diff"].sum() / a["n"].sum()) for _ in range(1000)]
                aff = x[x[f"chg_{v}"]]
                r[f"{kind}|{v}"] = {"gain_per_event": float(diff.sum() / x[n].sum()),
                                    "ci": [float(np.percentile(boot, 2.5)), float(np.percentile(boot, 97.5))],
                                    "affected_team_games": int(len(aff)),
                                    "gain_per_event_affected": float((aff[f"ll_{kind}_{v}"] - aff[f"ll_{kind}_base"]).sum() / max(aff[n].sum(), 1))}
        res[split] = {"team_games": int(len(x)), **r}
    json.dump(res, open(out_path, "w"), indent=1)
    print(json.dumps(res, indent=1))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
