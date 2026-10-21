"""End-to-end evaluation report: selection on validation, one test run, benchmark, profit.

Order matters and is enforced here: the model and every strategy threshold are chosen on
validation (2023/24) and frozen before the test seasons (2024/25, 2025/26) are run once.
Outputs land in data/reports/*.json (read by the website) and docs/results/*.md.
"""

import json
from datetime import UTC, datetime
from itertools import product

import numpy as np
import pandas as pd

from footy.config import PROJECT_ROOT, get_settings
from footy.db import get_engine
from footy.modeling.benchmark import WARNING, benchmark
from footy.modeling.ledger import headline
from footy.modeling.profit import (
    Strategy,
    candidates,
    coverage,
    load_prices,
    report,
    select,
    simulate,
    strategy_dict,
)
from footy.modeling.runner import compare, per_league, run_models, save
from footy.modeling.walkforward import SPLITS

BASELINES = ["baseline_frequency", "baseline_poisson", "dixon_coles"]

# football-data terms (D-013, D-024): no raw prices in reports. Over a handful of bets, the
# figures computed from prices (average odds, profit, closing-line value, monthly sums...) can
# give away an individual price, e.g. the average odds of a single bet are its price. Below
# this many bets a result keeps only its counts.
MIN_BETS_FOR_PRICE_FIGURES = 20
# Above it, no published profit (a breakdown row, or a step of the equity curve) may sum
# fewer bets than this; see aggregate_breakdowns.
MIN_BETS_PER_ROW = 5
BREAKDOWN_RULE = (
    "Every published profit sums whole periods of at least {n} bets (D-013): the equity curve "
    "is cumulative at the end of periods of whole months with at least {n} bets each; a month "
    "or season shows its sum only if it is a whole period or block on its own; league and team "
    "rows keep their counts only (sum null), since they cut across the periods and could be "
    "subtracted from them"
)
COUNT_FIELDS = (
    "bets",
    "wins",
    "losses",
    "voids",
    "hit_rate",
    "hit_rate_denominator",
    "staked_units",
    "bankroll_start_gbp",
    "unit_gbp",
    "skipped_insufficient_funds",
    "fixtures_bet",
    "max_bets_per_fixture",
    "closing_line_value_n",
)


def _reports_dir():
    d = get_settings().data_dir / "reports"
    d.mkdir(parents=True, exist_ok=True)
    return d


def _write(name: str, obj) -> None:
    (_reports_dir() / f"{name}.json").write_text(json.dumps(obj, indent=1, default=_default))


def _default(o):
    if isinstance(o, (np.generic,)):
        return o.item()
    if isinstance(o, (pd.Timestamp, datetime)):
        return o.isoformat()
    if isinstance(o, set):
        return sorted(o)
    return str(o)


def _periods(rows: list[dict], min_bets: int) -> list[list[int]]:
    """Indices of `rows` (in time order) grouped into consecutive runs of at least `min_bets`
    bets; a short run at the end joins the one before it."""
    out: list[list[int]] = []
    run: list[int] = []
    n = 0
    for i, row in enumerate(rows):
        run.append(i)
        n += row["count"]
        if n >= min_bets:
            out.append(run)
            run, n = [], 0
    if run:
        if out:
            out[-1].extend(run)
        else:
            out.append(run)
    return out


def aggregate_breakdowns(r: dict, min_bets: int = MIN_BETS_PER_ROW) -> dict:
    """A profit result with enough bets to publish its overall figures, made fit to publish
    in its breakdowns too (D-013): no published profit sums fewer than `min_bets` bets.

    - Seasons are grouped, in order, into blocks of at least `min_bets` bets (a short last
      block joins the one before). Within each block, months are grouped the same way into
      periods. A season shows its profit only if it is a block on its own, and a month only
      if it is a period on its own; the others keep their counts.
    - The per-bet equity curve is replaced by `equity_curve_monthly`: the cumulative bets and
      profit at the end of each period, so every step of the curve is a whole period.
    - League and team rows keep their counts only. They cut across the periods, so a shown
      league or team sum could be subtracted from a period, month or season (or from each
      other): a September of 7 bets, 6 of them all of a league's bets, would give away the
      seventh bet's profit and so its price, however the small rows were hidden.

    So every published profit is a sum of whole periods, and no combination of them isolates
    fewer than `min_bets` bets. This holds within one result; results of nested strategies or
    markets published side by side can still be subtracted from each other (final_report).
    """
    out = {k: v for k, v in r.items() if k != "equity_curve_units"}
    seasons = sorted(r.get("seasonal", []), key=lambda x: x["season"])
    months = r.get("monthly", [])
    season_rows, month_rows, curve = [], [], []
    bets, profit = 0, 0.0
    for block in _periods(seasons, min_bets):
        in_block = {seasons[i]["season"] for i in block}
        for i in block:
            season_rows.append(
                {**seasons[i], "sum": seasons[i]["sum"] if len(block) == 1 else None}
            )
        ms = sorted(
            (m for m in months if m["season"] in in_block), key=lambda m: (m["season"], m["month"])
        )
        for period in _periods(ms, min_bets):
            for i in period:
                month_rows.append({**ms[i], "sum": ms[i]["sum"] if len(period) == 1 else None})
            bets += sum(ms[i]["count"] for i in period)
            profit += sum(ms[i]["sum"] for i in period)
            end = ms[period[-1]]
            curve.append(
                {
                    "season": end["season"],
                    "month": end["month"],
                    "bets": bets,
                    "profit_units": profit,
                }
            )
    out["seasonal"] = season_rows
    out["monthly"] = month_rows
    out["equity_curve_monthly"] = curve
    out["by_league"] = [{**row, "sum": None} for row in r.get("by_league", [])]
    out["top_teams_by_exposure"] = [
        {**row, "sum": None} for row in r.get("top_teams_by_exposure", [])
    ]
    out["breakdown_rule"] = BREAKDOWN_RULE.format(n=min_bets)
    return out


def withhold_prices(r: dict, min_bets: int = MIN_BETS_FOR_PRICE_FIGURES) -> dict:
    """A profit result fit to publish: below `min_bets` bets only its counts are kept; from
    `min_bets` on, its breakdowns and equity curve are aggregated (aggregate_breakdowns)."""
    if not r.get("bets"):
        return r
    if r["bets"] >= min_bets:
        return aggregate_breakdowns(r)
    out = {k: r[k] for k in COUNT_FIELDS if k in r}
    out["withheld"] = (
        f"fewer than {min_bets} bets: figures computed from prices are withheld, since over "
        "so few bets they can reveal an individual price (D-013)"
    )
    return out


def choose_strategy(cands_val: pd.DataFrame, min_bets: int = 60) -> tuple[Strategy, list[dict]]:
    """Grid over thresholds on VALIDATION only; best yield with at least `min_bets` bets."""
    tried = []
    best, best_yield = None, -np.inf
    for edge, prob, max_odds, one in product(
        [0.0, 0.03, 0.06, 0.1], [0.0, 0.25, 0.4], [3.0, 5.0, 1000.0], [False, True]
    ):
        s = Strategy(
            f"filtered_e{edge}_p{prob}_o{max_odds:g}_{'one' if one else 'all'}",
            min_edge=edge,
            min_prob=prob,
            max_odds=max_odds,
            one_per_fixture=one,
        )
        b = select(cands_val, s)
        if len(b) < min_bets:
            tried.append({"strategy": strategy_dict(s), "bets": len(b), "yield": None})
            continue
        y = float(np.where(b.won, b.odds - 1, -1.0).mean())
        tried.append({"strategy": strategy_dict(s), "bets": len(b), "yield": y})
        if y > best_yield:
            best, best_yield = s, y
    return best, tried


def run(selected: str, lab_candidates: list[str], workers: int = 3) -> dict:
    engine = get_engine()
    generated = datetime.now(UTC).isoformat(timespec="seconds")
    names = list(dict.fromkeys([*BASELINES, *lab_candidates, selected]))

    # 1. Validation comparison (selection evidence).
    val = run_models(names, SPLITS["validation"], workers=workers)
    for m, led in val.items():
        save(m, led, "validation")
    val_table = compare(val)
    best_composite = val_table.iloc[0]["model"]
    best_acc = val_table.sort_values("acc_1x2", ascending=False).iloc[0]["model"]

    # 2. Test, once, for the selected model and the baselines.
    test_names = list(dict.fromkeys([*BASELINES, selected]))
    test = run_models(test_names, SPLITS["test"], workers=workers)
    for m, led in test.items():
        save(m, led, "test")
    test_table = compare(test)

    # 3. Benchmark gate on validation and test.
    bench = {
        "validation": benchmark(engine, val[selected]),
        "test": benchmark(engine, test[selected]),
    }
    gate_label = bench["test"]["gate"]["label"]

    # 4. Profit simulation: unfiltered and validation-frozen filtered strategies.
    pv = load_prices(engine, val[selected].match_id.tolist())
    pt = load_prices(engine, test[selected].match_id.tolist())
    cv, ct = candidates(val[selected], pv), candidates(test[selected], pt)
    frozen, tried = choose_strategy(cv)
    strategies = [
        Strategy("unfiltered_ev_positive"),
        Strategy("unfiltered_one_per_fixture", one_per_fixture=True),
    ]
    if frozen is not None:
        strategies.append(frozen)
    profit = {
        "label": gate_label,
        "coverage_validation": coverage(val[selected], cv),
        "coverage_test": coverage(test[selected], ct),
        "frozen_on_validation": strategy_dict(frozen) if frozen else None,
        "threshold_search_validation": tried,
        "results": {},
    }
    for s in strategies:
        for split, cands in (("validation", cv), ("test", ct)):
            for market in (("1X2",), ("OU_2.5",), ("1X2", "OU_2.5")):
                st = Strategy(**{**strategy_dict(s), "markets": market})
                r = report(simulate(select(cands, st)))
                r.pop("equity_curve_units", None) if split == "validation" else None
                profit["results"][f"{s.name}|{split}|{'+'.join(market)}"] = withhold_prices(r)

    selection = {
        "generated_at_utc": generated,
        "rule": "Lowest composite on validation 2023/24 (mean of model/frequency-baseline "
        "over 1X2 RPS and log losses of O/U 1.5, 2.5, 3.5 and BTTS), five leagues pooled on "
        "identical fixtures",
        "selected": selected,
        "best_composite_on_validation": best_composite,
        "best_1x2_accuracy_on_validation": best_acc,
        "validation_table": val_table.replace({np.nan: None}).to_dict(orient="records"),
        "test_table": test_table.replace({np.nan: None}).to_dict(orient="records"),
        "test_per_league": per_league(test[selected]),
        "test_per_season": {s: headline(g) for s, g in test[selected].groupby("season")},
        "benchmark_warning": WARNING,
    }
    _write("model_selection", selection)
    _write("benchmark", bench)
    _write("profit", profit)
    _markdown(selection, bench, profit)
    return {
        "selection": selection,
        "benchmark_gate": bench["test"]["gate"],
        "frozen": profit["frozen_on_validation"],
    }


def _fmt(row: dict, key: str, places: int = 4) -> str:
    v = row.get(key)
    return "–" if v is None else f"{v:.{places}f}"


def _ci(v: dict) -> str:
    lo, hi = v["difference_ci95"]
    return f"difference {v['difference']:+.4f} (95% CI {lo:+.4f} to {hi:+.4f}), n={v['fixtures']}"


def _markdown(sel: dict, bench: dict, profit: dict) -> None:
    d = PROJECT_ROOT / "docs" / "results"
    d.mkdir(parents=True, exist_ok=True)
    out = [
        f"# Model results (generated {sel['generated_at_utc']})",
        "",
        f"Selection rule: {sel['rule']}.",
        "",
        f"Selected model: **{sel['selected']}**. "
        f"Best composite on validation: {sel['best_composite_on_validation']}; "
        f"best 1X2 accuracy on validation: {sel['best_1x2_accuracy_on_validation']}.",
        "",
    ]
    cols = ["rps", "ll_ou_1.5", "ll_ou_2.5", "ll_ou_3.5", "ll_btts", "composite"]
    accs = ["acc_1x2", "acc_ou_2.5", "acc_btts"]
    for title, rows in (
        ("Validation 2023/24", sel["validation_table"]),
        ("Test 2024/25 + 2025/26", sel["test_table"]),
    ):
        out += [
            f"## {title}",
            "",
            "| Model | Fixtures | RPS | LL O/U 1.5 | LL O/U 2.5 | LL O/U 3.5 | LL BTTS "
            "| Composite | Acc 1X2 | Acc O/U 2.5 | Acc BTTS |",
            "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
        for r in rows:
            cells = [_fmt(r, c) for c in cols] + [_fmt(r, c, 3) for c in accs]
            out.append(f"| {r['model']} | {r['fixtures']} | " + " | ".join(cells) + " |")
        out.append("")
    out += ["## Market benchmark (test)", ""]
    for k, v in bench["test"]["shin"].items():
        if "1X2" in k:
            out.append(
                f"- {k}: model RPS {v['model_rps']:.4f}, market {v['market_rps']:.4f}, {_ci(v)}"
            )
        else:
            out.append(
                f"- {k}: model log loss {v['model_log_loss']:.4f}, "
                f"market {v['market_log_loss']:.4f}, {_ci(v)}"
            )
    gate = bench["test"]["gate"]
    verdict = "model beats the closing market" if gate["model_beats_market"] else gate["label"]
    out += [
        "",
        f"Gate: {verdict}",
        "",
        "## Profit simulation (flat 1 unit, Bet365 pre-closing)",
        "",
    ]
    if profit["label"]:
        out += [f"**{profit['label']}**", ""]
    out += [
        "| Strategy | Split | Markets | Bets | Hit rate | Avg odds | Profit (units) | Yield "
        "| Max drawdown (units) |",
        "| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for key, r in profit["results"].items():
        s, split, mk = key.split("|")
        if not r.get("bets"):
            out.append(f"| {s} | {split} | {mk} | 0 | – | – | – | – | – |")
            continue
        if r.get("withheld"):
            w = "withheld"
            out.append(
                f"| {s} | {split} | {mk} | {r['bets']} | {r['hit_rate']:.3f} | {w} | {w} "
                f"| {w} | {w} |"
            )
            continue
        out.append(
            f"| {s} | {split} | {mk} | {r['bets']} | {r['hit_rate']:.3f} | {r['average_odds']:.2f} "
            f"| {r['profit_units']:+.1f} | {r['yield']:+.3f} | {r['max_drawdown_units']:.1f} |"
        )
    out += [
        "",
        "O/U 1.5, O/U 3.5 and BTTS: ROI unavailable (no historical prices).",
        "",
        f"Withheld: with fewer than {MIN_BETS_FOR_PRICE_FIGURES} bets, figures computed from "
        "prices could reveal an individual price (football-data terms, D-013).",
        "",
    ]
    (d / "domestic.md").write_text("\n".join(out))
