"""D-028 in machine-readable form, for the website's machine-learning panel.

D-028 (DECISIONS.md, proposed): four machine-learning candidates that learn from the models'
own track record were compared with the models of record; none replaces them. Every number
here is read from the saved report `data/reports/ml_selection.json` (written with the
research note, docs/research/machine-learning.md); only the candidates' roles and the
reasons that aren't numbers are D-028's own words. Composites are D-025's: lower is better,
and the frequency baseline scores 1. No prices are read or sent (D-013, D-024).
"""

import json
from pathlib import Path

DECISION = "D-028"
STATUS = "proposed"  # until the lead and the owner accept it
SELECTION_REPORT = "ml_selection"
LEARNING_CURVE_REPORT = "ml_learning_curve"
WRITE_UP = "docs/research/machine-learning.md"

# The four registered candidates in D-028's order, what each is, and whether it is for fixtures
# with prices (target: the model of record with prices) or without (the pure fallback).
CANDIDATES = (
    ("ml_online_market", "online mixture of the models' score grids, tilted onto the prices", True),
    ("ml_stack_market", "learned correction (stacked logistic models) of the model + market", True),
    ("ml_stack_pure", "learned correction (stacked logistic models) of the pure fallback", False),
    ("ml_online_pure", "online mixture of the models' score grids", False),
)
# D-028's reasons that aren't numbers (the numbered ones are built from the report).
CONTEXT = (
    "With prices the match result and over/under 2.5 come from the market, so a learner can "
    "only change the score grid's shape (over/under 1.5 and 3.5, both teams to score). The "
    "models' shapes are too similar for that choice to matter reliably.",
    "What could move this is more data rather than more learners: historical prices for the "
    "other goal lines and both teams to score, and lineups and injuries; none are held.",
    "The learners keep learning after every result and stay in the lab; nothing in the models "
    "of record changed.",
)


def _row(table: list[dict], model: str) -> dict | None:
    return next((r for r in table if r.get("model") == model), None)


def _split(section: dict, model: str, target: str) -> dict | None:
    """The candidate's and its target's composites on one split, and their paired difference
    (candidate minus target) with its 95% interval; None if the candidate wasn't run."""
    table = section.get("table") or []
    mine, theirs = _row(table, model), _row(table, target)
    if mine is None or theirs is None:
        return None
    paired = (section.get("paired_vs_target") or {}).get(f"{model} - {target}") or {}
    return {
        "fixtures": mine.get("fixtures"),
        "composite": mine.get("composite"),
        "target_composite": theirs.get("composite"),
        "difference": paired.get("composite_diff"),
        "difference_ci95": paired.get("composite_diff_ci95"),
    }


def ml_summary(reports_dir: Path) -> dict | None:
    """The D-028 summary built from ml_selection.json (None if the report isn't there)."""
    path = reports_dir / f"{SELECTION_REPORT}.json"
    if not path.exists():
        return None
    report = json.loads(path.read_text(encoding="utf-8"))
    targets = report.get("targets") or {}
    validation = report.get("validation_2324") or {}
    test = report.get("test_2425_2526") or {}
    tested = set(test.get("tested") or [])

    candidates = []
    for model, what, with_prices in CANDIDATES:
        target = targets.get("with_prices" if with_prices else "without_prices")
        val = _split(validation, model, target)
        beat = (
            val is not None
            and val["composite"] is not None
            and val["target_composite"] is not None
            and val["composite"] < val["target_composite"]
        )
        entry: dict = {
            "model": model,
            "what": what,
            "fixtures_with_prices": with_prices,
            "target": target,
            "validation": None if val is None else {**val, "beat_target": beat},
        }
        res = _split(test, model, target) if model in tested else None
        if res is not None:
            as_good = res["composite"] <= res["target_composite"]
            entry["test"] = {"run": True, **res, "at_least_as_good": as_good}
            entry["outcome"] = (
                "passes D-027: at least as good as its target on test"
                if as_good
                else "not adopted: worse than its target on test (D-027)"
            )
        elif beat:
            entry["test"] = {"run": False}
            entry["outcome"] = (
                "not tested: it beat its target on validation, but D-025 tests only the one "
                "selected candidate (the lowest validation composite)"
            )
        elif val is None:  # no row for it (or its target) in the report: nothing to compare
            entry["test"] = {"run": False}
            entry["outcome"] = "not in the saved report: no validation figures to compare"
        else:
            entry["test"] = {"run": False}
            entry["outcome"] = "not tested: worse than its target on validation"
        candidates.append(entry)

    switch = [c["model"] for c in candidates if (c["test"] or {}).get("at_least_as_good")]
    why = []
    for c in candidates:
        t, v = c["test"], c["validation"]
        if t.get("run"):
            why.append(
                f"{c['model']} beat {c['target']} on validation ({v['composite']:.5f} against "
                f"{v['target_composite']:.5f}) and was the one candidate tested; on test it "
                f"scored {t['composite']:.5f} against {t['target_composite']:.5f}"
                + ("." if t["at_least_as_good"] else ", so under D-027 it does not replace it.")
            )
    lost = [c for c in candidates if c["validation"] and not c["validation"]["beat_target"]]
    for target in sorted({c["target"] for c in lost}):
        names = " and ".join(c["model"] for c in lost if c["target"] == target)
        why.append(f"Without a win on validation over {target}, {names} had no test run.")
    return {
        "decision": DECISION,
        "status": STATUS,
        "verdict": "switch" if switch else "no switch",
        "switch_to": switch,
        "models_of_record": {
            "with_prices": targets.get("with_prices"),
            "without_prices": targets.get("without_prices"),
        },
        "rule": report.get("rule"),
        "lower_is_better": True,
        "candidates": candidates,
        "why": [*why, *CONTEXT],
        "report_generated_utc": report.get("generated_utc"),
        "sources": {
            "selection": f"/api/reports/{SELECTION_REPORT}",
            "learning_curve": f"/api/reports/{LEARNING_CURVE_REPORT}",
            "write_up": WRITE_UP,
        },
    }
