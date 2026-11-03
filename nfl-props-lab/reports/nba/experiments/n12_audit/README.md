# n12: audit backtest of the D-044 tracked rule (as coded)

The stage-2 audit (reports/AUDIT_2026-11.md) found that no published figure described the rule
the app actually tracked. These scripts backtest the D-044 rule exactly as coded: the consensus
from `npl.books` (one vote per book at its main line, fair from every two-way price at the
consensus line, BetMGM priced only at its own main line), the anchored stack from
`reports/nba/model/calibration.json` for the chosen N1 version, EV >= 2% at BetMGM's price, at
least 3 books, half-point lines, P(plays) >= 0.85. `common.py` is the independent verifier's
helper library (its own N1 attachment, outcomes, rule and date-block bootstrap), copied with
repo-relative paths.

Run from the repo root (raw quotes and per-prop tables go to the git-ignored data/runs/n12):

    uv run python reports/nba/experiments/n12_audit/extract.py
    N12_PARAMS=nba-0.1 uv run python reports/nba/experiments/n12_audit/n1_sf.py
    N12_PARAMS=nba-0.1 uv run python reports/nba/experiments/n12_audit/d044_rule.py
    N12_PARAMS=nba-0.3 uv run python reports/nba/experiments/n12_audit/n1_sf.py
    N12_PARAMS=nba-0.3 uv run python reports/nba/experiments/n12_audit/d044_rule.py

Outputs (aggregates only): `n12_summary_nba-0.1.json`, `n12_summary_nba-0.3.json`.

| N1 | season | bets | ROI [95% CI] | won | note |
|---|---|---|---|---|---|
| nba-0.1 | 2024-25 | 713 | +7.9% [+0.9, +14.5] | 56.1% | in-sample for the stack |
| nba-0.1 | 2025-26 sample | 225 | +13.1% [-0.4, +25.7] | 58.7% | sample examined before |
| nba-0.3 | 2024-25 | 686 | +8.6% [+0.9, +15.8] | 56.3% | in-sample for the stack |
| nba-0.3 | 2025-26 sample | 220 | +11.5% [-1.4, +23.6] | 57.7% | sample examined before |

Not an edge. The rule claims about +3.3% EV per bet; realised returns ran above that in both
seasons, carried mostly by rebounds, and 89-95% of the bets had BetMGM as the strict best
regulated price: soft, off-market prices of the kind books limit. All prices are US research
references a UK customer cannot take. The rule is judged prospectively on its paper record,
closing-line value first (D-044).

Checks: the shipped consensus equals the verifier's independent corrected consensus on every
prop of both seasons (73,488 and 19,327) for the line, BetMGM's pricing, the best regulated
price and the arbitrage flag; the fair differs only because it averages every two-way price at
the line (mean difference 0.0003 / 0.0007). The repo re-run reproduces the scratch run exactly.
