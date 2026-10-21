"""Score our headline pre-match records against the owner's ChatGPT app forecasts.

    uv run python comparisons/score.py 2026-10-21

Reads `chatgpt_<date>.json` (the owner's transcribed forecasts), `results_<date>.json` (final
scores with their sources) and `records/predictions/<date>.jsonl`, and writes
`scored_<date>.json`. Our forecast for each fixture is the record of record: the first model
of record with a genuine pre-match record for it (D-026). Fixtures without a result are left
out and listed. Lower RPS and log loss are better.
"""

import json
import sys
from datetime import date, datetime
from pathlib import Path

import numpy as np

from footy.modeling.metrics import logloss_binary, logloss_multi, outcome_index, rps
from footy.models_of_record import fixture_time, load, models_at

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
UEFA = {"UCL", "UEL", "UECL"}


def our_records(day: str) -> dict[str, dict]:
    periods = load(ROOT / "src/footy/reference/primary_models.json")
    recs: dict[str, list[dict]] = {}
    for line in (ROOT / f"records/predictions/{day}.jsonl").open():
        r = json.loads(line)
        if r["record_type"] == "genuine_prematch":
            recs.setdefault(r["fixture_ref"], []).append(r)
    out = {}
    for ref, rs in recs.items():
        r0 = rs[0]
        group = "uefa" if r0["competition"] in UEFA else "domestic"
        ko = datetime.fromisoformat(r0["kickoff_utc"]) if r0["kickoff_utc"] else None
        when = fixture_time(ko, date.fromisoformat(r0["match_date"]))
        by_model = {r["model_name"]: r for r in rs}
        for m in models_at(periods.get(group, []), when):
            if m in by_model:
                out[ref] = by_model[m]
                break
    return out


def score(p: dict, hg: int, ag: int) -> dict:
    """p: H, D, A, over_2_5, btts_yes as probabilities."""
    p1x2 = np.array([[p["H"], p["D"], p["A"]]])
    o = outcome_index([hg], [ag])
    over, btts = int(hg + ag > 2), int(hg > 0 and ag > 0)
    return {
        "pick": "HDA"[int(np.argmax(p1x2))],
        "result_right": bool(np.argmax(p1x2) == o[0]),
        "ou_2_5_right": (p["over_2_5"] >= 0.5) == bool(over),
        "btts_right": (p["btts_yes"] >= 0.5) == bool(btts),
        "rps": float(rps(p1x2, o)[0]),
        "log_loss_1x2": float(logloss_multi(p1x2, o)[0]),
        "log_loss_ou_2_5": float(logloss_binary([p["over_2_5"]], [over])[0]),
        "log_loss_btts": float(logloss_binary([p["btts_yes"]], [btts])[0]),
    }


def totals(rows: list[dict], who: str) -> dict:
    s = [r[who]["score"] for r in rows]
    if not s:
        return {}
    return {
        "games": len(s),
        "results_right": sum(x["result_right"] for x in s),
        "ou_2_5_right": sum(x["ou_2_5_right"] for x in s),
        "btts_right": sum(x["btts_right"] for x in s),
        "rps": round(float(np.mean([x["rps"] for x in s])), 4),
        "log_loss_1x2": round(float(np.mean([x["log_loss_1x2"] for x in s])), 4),
        "log_loss_ou_2_5": round(float(np.mean([x["log_loss_ou_2_5"] for x in s])), 4),
        "log_loss_btts": round(float(np.mean([x["log_loss_btts"] for x in s])), 4),
    }


def main(day: str) -> None:
    gpt = json.loads((HERE / f"chatgpt_{day}.json").read_text())
    res = json.loads((HERE / f"results_{day}.json").read_text())
    results = {r["fixture_ref"]: r for r in res["results"]}
    ours = our_records(day)
    rows, unscored = [], []
    for g in gpt["fixtures"]:
        ref = g["fixture_ref"]
        r, rec = results.get(ref), ours.get(ref)
        if r is None or rec is None:
            unscored.append(
                {"fixture_ref": ref, "reason": "no result yet" if r is None else "no record"}
            )
            continue
        hg, ag = r["home_goals"], r["away_goals"]
        pr = rec["probabilities"]
        our_p = {**pr["1X2"], "over_2_5": pr["OU_2.5"]["O"], "btts_yes": pr["BTTS"]["Y"]}
        gpt_p = {k: g[k] / 100 for k in ("H", "D", "A", "over_2_5", "btts_yes")}
        total = sum(gpt_p[k] for k in "HDA")  # the app's percentages are rounded
        gpt_p.update({k: gpt_p[k] / total for k in "HDA"})
        rows.append(
            {
                "fixture_ref": ref,
                "match": r["match"],
                "score": f"{hg}-{ag}",
                "ours": {
                    "model": rec["model_name"],
                    "record_id": rec["id"],
                    "probabilities": {k: round(v, 4) for k, v in our_p.items()},
                    "score": score(our_p, hg, ag),
                },
                "chatgpt_app": {
                    "probabilities": {k: round(v, 4) for k, v in gpt_p.items()},
                    "score": score(gpt_p, hg, ag),
                },
            }
        )
    ucl = [r for r in rows if r["fixture_ref"].startswith("UCL:")]
    out = {
        "_note": (
            "Our headline pre-match records against the owner's ChatGPT app forecasts "
            f"(chatgpt_{day}.json, whose caveats apply), on final scores from results_{day}.json. "
            "Lower RPS and log loss are better. Ten games are far too few to tell the two apart. "
            "Written by comparisons/score.py."
        ),
        "results_status": res["status"],
        "totals": {
            "all": {"ours": totals(rows, "ours"), "chatgpt_app": totals(rows, "chatgpt_app")},
            "champions_league": {
                "ours": totals(ucl, "ours"),
                "chatgpt_app": totals(ucl, "chatgpt_app"),
            },
        },
        "lower_rps_games": {
            "ours": sum(r["ours"]["score"]["rps"] < r["chatgpt_app"]["score"]["rps"] for r in rows),
            "chatgpt_app": sum(
                r["chatgpt_app"]["score"]["rps"] < r["ours"]["score"]["rps"] for r in rows
            ),
        },
        "unscored": unscored,
        "games": rows,
    }
    (HERE / f"scored_{day}.json").write_text(json.dumps(out, indent=1) + "\n")
    for k, t in out["totals"].items():
        print(k, json.dumps(t))
    print("lower RPS games", out["lower_rps_games"], "unscored", unscored)


if __name__ == "__main__":
    main(sys.argv[1])
