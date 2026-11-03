"""Backtest of the D-044 tracked rule exactly as coded (npl.books consensus, BetMGM-only, frozen
anchored stack, EV >= 2%, books >= 3, half-point line, p_play >= 0.85) on NBA 2024-25 and the
2025-26 sample, using the stage-2 verifier's independent N1 / outcome / bootstrap helpers.
Aggregates only are printed."""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common as V  # noqa: E402

from npl import books  # noqa: E402

K = V.K
res = {}
for season in (2025, 2026):
    q = V.quotes(season)
    n1, act = V.N1(season), V.outcomes(season)
    pr = books.consensus_frame(books.pairs(q, keys=tuple(K)), keys=tuple(K))
    d = n1.attach(pr).merge(act, on=K, how="inner").reset_index(drop=True)
    date_list = np.array(sorted(d.date.unique()))
    idx = V.boot_idx(len(date_list), 10000, seed=season)
    has = d.mgm_over.notna().to_numpy() & d.mgm_under.notna().to_numpy()
    base = (d.books.to_numpy() >= 3) & (d.line.to_numpy() % 1 != 0) & (d.p_play.to_numpy() >= 0.85)
    f = d.fair_over.to_numpy()
    out = {"props": int(len(d)), "eligible_with_betmgm": int((base & has).sum())}
    for name, elig in (("d044_betmgm_only", base & has),
                       ("d044_no_arb", base & has & ~d.arb.fillna(False).to_numpy())):
        r = V.run_rule(d, f, f, d.mgm_over.to_numpy(), d.mgm_under.to_numpy(), elig)
        b = d[r.sel.to_numpy()].join(r[r.sel])
        st = V.roi_ci(b.date, b.profit, date_list, idx, void=b.push)
        st["win"] = round(float(b.won.mean()), 4)
        st["share_over"] = round(float((b.side == "over").mean()), 3)
        st["claimed_ev"] = round(float(b.ev.mean()), 4) if "ev" in b else None
        mk = {m: V.roi_ci(x.date, x.profit, date_list, idx, void=x.push) for m, x in b.groupby("market")}
        st["by_market"] = {m: {k: v.get(k) for k in ("bets", "roi", "ci")} for m, v in mk.items()}
        out[name] = st
    res[season] = out
    print(season, json.dumps(out, indent=1, default=str), flush=True)
json.dump(res, open(Path(__file__).resolve().parent / f"n12_summary_{V.PARAMS}.json", "w"), indent=1,
          default=str)
