"""The machine-learning panel's data: /api/reports/ml_learning_curve, /api/reports/ml_selection
and the D-028 summary at /api/ml/summary (footy.ml_decision).

SYNTHETIC TEST DATA: made-up composites, intervals and months shaped like the saved reports.
None of these numbers is a real model's score.
"""

import json

from footy.snapshot import find_prices


def row(model: str, composite: float, fixtures: int = 100) -> dict:
    return {"model": model, "fixtures": fixtures, "rps": 0.19, "composite": composite}


def paired(diff: float) -> dict:
    return {"composite_diff": diff, "composite_diff_ci95": [diff - 0.001, diff + 0.001]}


def selection(test_candidate: float = 0.9560) -> dict:
    """SYNTHETIC ml_selection.json: online-market beats its target on validation and is tested;
    stack-market also beats it but isn't selected; both pure learners lose."""
    return {
        "generated_utc": "2026-10-22T00:18:57+00:00",
        "rule": "SYNTHETIC rule",
        "targets": {"with_prices": "tilt_m", "without_prices": "ctx_m"},
        "validation_2324": {
            "table": [
                row("ml_online_market", 0.9440),
                row("ml_stack_market", 0.9444),
                row("tilt_m", 0.9445),
                row("ctx_m", 0.9500),
                row("ml_stack_pure", 0.9520),
                row("ml_online_pure", 0.9530),
            ],
            "paired_vs_target": {
                "ml_online_market - tilt_m": paired(-0.0005),
                "ml_stack_market - tilt_m": paired(-0.0001),
                "ml_stack_pure - ctx_m": paired(0.0020),
                "ml_online_pure - ctx_m": paired(0.0030),
            },
        },
        "test_2425_2526": {
            "tested": ["ml_online_market"],
            "table": [row("tilt_m", 0.9555, 200), row("ml_online_market", test_candidate, 200)],
            "paired_vs_target": {
                "ml_online_market - tilt_m": paired(round(test_candidate - 0.9555, 6))
            },
        },
        "decision": "SYNTHETIC",
    }


def write(settings, name: str, body: dict) -> None:
    folder = settings.data_dir / "reports"
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{name}.json").write_text(json.dumps(body))


def test_no_switch_with_each_candidates_numbers_and_why(client, settings):
    assert client.get("/api/ml/summary").json() == {"detail": "no such report"}
    write(settings, "ml_selection", selection())
    body = client.get("/api/ml/summary").json()
    assert (body["decision"], body["status"], body["verdict"]) == ("D-028", "proposed", "no switch")
    assert body["switch_to"] == [] and body["lower_is_better"] is True
    assert body["models_of_record"] == {"with_prices": "tilt_m", "without_prices": "ctx_m"}
    c = {x["model"]: x for x in body["candidates"]}
    assert list(c) == ["ml_online_market", "ml_stack_market", "ml_stack_pure", "ml_online_pure"]
    online = c["ml_online_market"]
    assert online["target"] == "tilt_m" and online["fixtures_with_prices"] is True
    assert online["validation"] == {
        "fixtures": 100,
        "composite": 0.9440,
        "target_composite": 0.9445,
        "difference": -0.0005,
        "difference_ci95": [-0.0015, 0.0005],
        "beat_target": True,
    }
    assert online["test"]["run"] is True and online["test"]["composite"] == 0.9560
    assert online["test"]["target_composite"] == 0.9555
    assert online["test"]["at_least_as_good"] is False
    assert "worse than its target on test" in online["outcome"]
    assert c["ml_stack_market"]["test"] == {"run": False}
    assert "D-025 tests only the one selected" in c["ml_stack_market"]["outcome"]
    for pure in ("ml_stack_pure", "ml_online_pure"):
        assert c[pure]["target"] == "ctx_m" and c[pure]["validation"]["beat_target"] is False
        assert c[pure]["outcome"] == "not tested: worse than its target on validation"
    assert "0.95600 against 0.95550" in body["why"][0]
    assert "ml_stack_pure and ml_online_pure had no test run" in body["why"][1]
    assert body["sources"]["learning_curve"] == "/api/reports/ml_learning_curve"
    assert body["report_generated_utc"].endswith("+00:00")
    assert find_prices({"bodies": [body]}) == []


def test_a_candidate_as_good_on_test_would_be_named(client, settings):
    write(settings, "ml_selection", selection(test_candidate=0.9550))
    body = client.get("/api/ml/summary").json()
    assert body["verdict"] == "switch" and body["switch_to"] == ["ml_online_market"]


def test_the_reports_themselves_are_served(client, settings):
    curve = {
        "generated_utc": "2026-10-22T00:18:24+00:00",
        "panels": [
            {
                "candidate": "ml_online_market",
                "target": "tilt_m",
                "splits": {"validation": {"months": ["2023-08"], "fixtures": [10]}},
            }
        ],
    }
    write(settings, "ml_learning_curve", curve)
    write(settings, "ml_selection", selection())
    assert client.get("/api/reports/ml_learning_curve").json() == curve
    assert client.get("/api/reports/ml_selection").json()["targets"]["with_prices"] == "tilt_m"
