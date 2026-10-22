"""SYNTHETIC backtest ledgers for the backtest explorer's tests (footy.backtest_explorer).

Fictional teams (fdfixtures) and made-up probabilities in the walk-forward ledger's column
layout. Nothing here is a real prediction, result or price.
"""

import json
from pathlib import Path

import pandas as pd

TEAMS = ("eng-testtown", "eng-sampleton", "eng-mockford")
# (date, home, away, home goals, away goals) per season: a made-up three-match round.
ROUNDS = {
    "2324": [
        ("2023-08-12", "eng-testtown", "eng-sampleton", 2, 1),
        ("2023-08-19", "eng-sampleton", "eng-mockford", 0, 0),
        ("2023-08-26", "eng-mockford", "eng-testtown", 1, 3),
    ],
    "2425": [
        ("2024-08-17", "eng-testtown", "eng-mockford", 1, 1),
        ("2024-08-24", "eng-mockford", "eng-sampleton", 0, 2),
    ],
    "2526": [
        ("2025-08-16", "eng-sampleton", "eng-testtown", 3, 0),
    ],
}


def rows(model: str, seasons, probs=(0.5, 0.3, 0.2), division="E0", key="league", skip=()):
    """One predicted row per synthetic match of these seasons, all with the same made-up
    probabilities (`skip`: dates left out, as if the model had not predicted them)."""
    h, d, a = probs
    out = []
    for season in seasons:
        for n, (date, home, away, hg, ag) in enumerate(ROUNDS[season], 1):
            if date in skip:
                continue
            actual = 0 if hg > ag else 1 if hg == ag else 2
            pick = max(range(3), key=lambda i: probs[i])
            cum = [h, h + d]
            obs = [1.0 if actual == 0 else 0.0, 1.0 if actual <= 1 else 0.0]
            out.append(
                {
                    "model": model,
                    key: division,
                    "match_id": int(season) * 100 + n,
                    "season": season,
                    "date": date,
                    "home_team": home,
                    "away_team": away,
                    "home_goals": hg,
                    "away_goals": ag,
                    "status": "predicted",
                    "p_H": h,
                    "p_D": d,
                    "p_A": a,
                    "rps": sum((c - o) ** 2 for c, o in zip(cum, obs, strict=True)) / 2,
                    "correct_1x2": int(pick == actual),
                    "p_over_2.5": 0.5123456,
                    "p_btts": 0.4987654,
                }
            )
    return out


def write_run(root: Path, run_id: str, manifest: dict, ledger_rows: list[dict]) -> None:
    d = root / run_id
    d.mkdir(parents=True, exist_ok=True)
    (d / "manifest.json").write_text(json.dumps({"run_id": run_id, **manifest}))
    pd.DataFrame(ledger_rows).to_csv(d / "ledger.csv", index=False)


def write_curated(data_dir: Path) -> None:
    """Ledgers under curated names: dixon_coles (validation and test), baseline_frequency
    (validation), a market-only ledger saved under a curated name (must be refused) and a
    UEFA pair, the earlier record's test run saved under its lab name."""
    dom = data_dir / "backtests"
    write_run(
        dom,
        "dixon_coles__validation__2324",
        {"model": "dixon_coles", "split": "validation", "seasons": ["2324"]},
        rows("dixon_coles", ["2324"], (0.5, 0.3, 0.2)),
    )
    write_run(
        dom,
        "dixon_coles__test__2425-2526",
        {"model": "dixon_coles", "split": "test", "seasons": ["2425", "2526"]},
        rows("dixon_coles", ["2425", "2526"], (0.45, 0.3, 0.25)),
    )
    write_run(
        dom,
        "baseline_frequency__validation__2324",
        {"model": "baseline_frequency", "split": "validation", "seasons": ["2324"]},
        rows("baseline_frequency", ["2324"], (0.44, 0.27, 0.29), skip=("2023-08-26",)),
    )
    # SYNTHETIC market-only ledger under a curated name: prices with no model under them.
    write_run(
        dom,
        "market_tilt_dc__validation__2324",
        {
            "model": "market_tilt_dc",
            "split": "validation",
            "seasons": ["2324"],
            "model_params": {"books": ["Avg"], "devig": "shin"},
        },
        rows("market_tilt_dc", ["2324"], (0.6111, 0.2222, 0.1667)),
    )
    euro = data_dir / "backtests_euro"
    write_run(
        euro,
        "pan_euro_mix_mxg__test_round3",
        {"model": "pan_euro_mix_mxg", "split": "test_round3", "seasons": ["2425", "2526"]},
        rows("pan_euro_mix_mxg", ["2425"], (0.4, 0.3, 0.3), division="UCL", key="comp"),
    )
    write_run(
        euro,
        "euro_mix_0.7_0.3__test_mxg",
        {"model": "euro_mix_0.7_0.3", "split": "test_mxg", "seasons": ["2425", "2526"]},
        rows("euro_mix_0.7_0.3", ["2425"], (0.35, 0.3, 0.35), division="UCL", key="comp"),
    )
