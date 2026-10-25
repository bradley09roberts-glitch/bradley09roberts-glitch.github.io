"""`npl` command line. Every pipeline step is a repeatable, idempotent subcommand."""

from __future__ import annotations

import argparse
import json
import logging
import sys

from . import config, db, rawstore


def _seasons(s: str | None) -> list[int] | None:
    if not s:
        return None
    out: list[int] = []
    for chunk in s.split(","):
        if "-" in chunk:
            a, b = chunk.split("-")
            out.extend(range(int(a), int(b) + 1))
        else:
            out.append(int(chunk))
    return out


def cmd_ingest(a) -> int:
    from .sources import nflverse

    con = db.connect()
    with rawstore.job(con, "ingest.nflverse", vars(a) | {"func": None}) as st:
        res = nflverse.fetch(con, a.datasets.split(",") if a.datasets else None,
                             _seasons(a.seasons), max_age_hours=a.max_age_hours, force=a.force)
        st["detail"] = {k: len(v) for k, v in res.items()} | {"failed_items": res["failed"]}
        if res["failed"]:
            st["status"] = "partial"
    print(json.dumps({k: (len(v) if k != "failed" else v) for k, v in res.items()}, indent=1))
    return 0 if not res["failed"] else 2


def cmd_audit(a) -> int:
    from . import audit

    con = db.connect()
    with rawstore.job(con, "audit.coverage") as st:
        st["detail"] = audit.run(con)
    print(json.dumps(st["detail"], indent=1))
    return 0


def cmd_build(a) -> int:
    from . import canon

    con = db.connect()
    with rawstore.job(con, "build.canonical", {"as_of": a.as_of}) as st:
        st["detail"] = canon.build(con, as_of=a.as_of, seasons=_seasons(a.seasons))
    print(json.dumps({k: v for k, v in st["detail"].items() if k != "inputs"}, indent=1,
                     default=str))
    return 0


def cmd_publish(a) -> int:
    from . import edition, features, forecast

    con = db.connect()
    with rawstore.job(con, "publish.edition", {"season": a.season, "week": a.week,
                                               "kind": a.kind}) as st:
        from . import backtest

        code = edition.code_version()  # the code loaded for this run
        if code["dirty"] and not a.allow_dirty and not a.dry_run:
            raise edition.EditionError("commit the code before publishing (tree is dirty)")
        lake = features.load_lake()
        mlm = backtest._ml_models_for(lake, a.season, a.week,
                                      {"models": None, "season": None, "fits": []})
        res = forecast.run_slate(lake, a.season, a.week, n_sims=a.sims, seed=a.seed,
                                 models=("X1", "X", "J1", "J0", "A", "C"), ml_models=mlm)
        if a.dry_run:
            ok = [f for f in res["forecasts"] if f["status"] == "ok"]
            st["detail"] = {"dry_run": True, "forecasts_ok": len(ok)}
            print(json.dumps(st["detail"]))
            return 0
        st["detail"] = edition.publish(con, season=a.season, week=a.week, kind=a.kind,
                                       result=res, lake=lake, allow_dirty=a.allow_dirty,
                                       notes=a.note or [], headline=a.headline, code=code)
    print(json.dumps(st["detail"], indent=1))
    return 0


def cmd_serve(a) -> int:
    import uvicorn

    uvicorn.run("npl.api:app", host=a.host, port=a.port, reload=False, log_level="info")
    return 0


def cmd_update(a) -> int:
    """Daily job: refresh public data, coverage audit and canonical tables."""
    rc = cmd_ingest(argparse.Namespace(datasets=None, seasons=a.seasons or "2026",
                                       max_age_hours=a.max_age_hours, force=False))
    cmd_audit(a)
    cmd_build(argparse.Namespace(as_of=None, seasons=None))
    return rc


def cmd_settle(a) -> int:
    from . import settle

    con = db.connect()
    with rawstore.job(con, "settle.record") as st:
        st["detail"] = settle.run(con)
    print(json.dumps(st["detail"], indent=1, default=str)[:3000])
    return 0


def cmd_quotes(a) -> int:
    from .sources import odds_api

    con = db.connect()
    try:
        odds_api.api_key()
    except odds_api.MissingKey as e:
        print(f"BLOCKED: {e}")
        print(json.dumps(odds_api.status(con), indent=1))
        return 3
    with rawstore.job(con, "quotes.snapshot", {"regions": a.regions}) as st:
        st["detail"] = odds_api.snapshot_upcoming(con, regions=a.regions,
                                                  hours_ahead=a.hours_ahead)
    print(json.dumps(st["detail"], indent=1, default=str))
    return 0


def cmd_money(a) -> int:
    """Price-honest money backtest: only stored real quotes; otherwise ROI unavailable."""
    import pandas as pd

    from . import money
    from .config import REPORTS_DIR
    from .sources import odds_api

    con = db.connect()
    q = odds_api.quotes_frame(con)
    led = pd.read_csv(a.ledger, low_memory=False)
    elig = int(((led.model == a.model) & (led.status == "ok")).sum())
    if q.empty:
        res = money.report(pd.DataFrame(), n_eligible_forecasts=elig)
        res["detail"] = ("No quotes are stored (no odds key; historical props need a paid "
                         "plan). Probability evaluation is unaffected; profit is not claimed.")
    else:  # pragma: no cover - needs real quotes
        res = {"state": "quotes present: run the strategy notebook path (not yet validated)"}
    out = REPORTS_DIR / "money"
    out.mkdir(parents=True, exist_ok=True)
    (out / f"{a.label}.json").write_text(json.dumps(res, indent=1, default=str))
    print(json.dumps(res, indent=1, default=str))
    return 0


def cmd_verify(a) -> int:
    """Full local verification: lint, tests, edition hashes, API smoke test."""
    import glob
    import subprocess

    from . import edition
    from .config import EDITIONS_DIR, PROJECT_ROOT

    ok = True
    for cmd in (["ruff", "check", "src", "tests"], ["pytest", "-q"]):
        r = subprocess.run(["uv", "run", *cmd], cwd=PROJECT_ROOT)
        print(" ".join(cmd), "->", "OK" if r.returncode == 0 else "FAILED")
        ok &= r.returncode == 0
    files = glob.glob(str(EDITIONS_DIR / "*" / "*" / "*.json"))
    bad = [f for f in files if not edition.verify(f)]
    print(f"edition hashes: {len(files) - len(bad)}/{len(files)} verified")
    ok &= not bad
    from fastapi.testclient import TestClient

    from .api import app

    c = TestClient(app)
    for path in ("/api/status", "/api/slate", "/api/props", "/api/backtests", "/api/record",
                 "/api/data", "/"):
        code = c.get(path).status_code
        print(f"GET {path} -> {code}")
        ok &= code == 200
    print("VERIFY", "PASSED" if ok else "FAILED")
    return 0 if ok else 1


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="npl", description="NFL Props Lab pipeline")
    p.add_argument("-v", "--verbose", action="store_true")
    sub = p.add_subparsers(dest="cmd", required=True)

    s = sub.add_parser("ingest", help="download public nflverse data into the raw store")
    s.add_argument("--datasets", help="comma-separated dataset keys (default: all)")
    s.add_argument("--seasons", help="e.g. 2012-2026 or 2024,2025")
    s.add_argument("--max-age-hours", type=float, default=6.0)
    s.add_argument("--force", action="store_true")
    s.set_defaults(func=cmd_ingest)

    s = sub.add_parser("audit", help="coverage matrix from the stored raw files")
    s.set_defaults(func=cmd_audit)

    s = sub.add_parser("build", help="build canonical lake tables from the raw store")
    s.add_argument("--as-of", help="use raw files retrieved at or before this UTC time")
    s.add_argument("--seasons")
    s.set_defaults(func=cmd_build)

    s = sub.add_parser("publish", help="publish an immutable forecast edition for a slate")
    s.add_argument("--season", type=int, required=True)
    s.add_argument("--week", type=int, required=True)
    s.add_argument("--kind", choices=["early", "late"], default="early")
    s.add_argument("--sims", type=int, default=10_000)
    s.add_argument("--seed", type=int, default=20261025)
    s.add_argument("--note", action="append")
    s.add_argument("--dry-run", action="store_true")
    s.add_argument("--allow-dirty", action="store_true")
    s.add_argument("--headline", default="X1")
    s.set_defaults(func=cmd_publish)

    s = sub.add_parser("serve", help="run the web app (read-only over saved outputs)")
    s.add_argument("--host", default="127.0.0.1")
    s.add_argument("--port", type=int, default=8765)
    s.set_defaults(func=cmd_serve)

    s = sub.add_parser("update", help="daily: ingest current season, audit, rebuild lake")
    s.add_argument("--seasons")
    # short window: availability files (injuries, rosters, depth charts) change on the
    # morning of games, and a 6-hour cache once hid nflverse's 07:00 UTC refresh
    s.add_argument("--max-age-hours", type=float, default=0.5)
    s.set_defaults(func=cmd_update)

    s = sub.add_parser("settle", help="append settlement revisions; score the record")
    s.set_defaults(func=cmd_settle)

    s = sub.add_parser("quotes", help="snapshot current prop quotes (needs ODDS_API_KEY)")
    s.add_argument("--regions", default="uk")
    s.add_argument("--hours-ahead", type=float, default=48)
    s.set_defaults(func=cmd_quotes)

    s = sub.add_parser("money", help="price-honest money backtest over a ledger")
    s.add_argument("--ledger", required=True)
    s.add_argument("--model", default="X")
    s.add_argument("--label", default="money")
    s.set_defaults(func=cmd_money)

    s = sub.add_parser("verify", help="lint, tests, edition hashes and API smoke test")
    s.set_defaults(func=cmd_verify)
    return p


def main(argv: list[str] | None = None) -> int:
    a = build_parser().parse_args(argv)
    logging.basicConfig(level=logging.INFO if a.verbose else logging.WARNING,
                        format="%(asctime)s %(levelname)s %(message)s")
    config.ensure_dirs()
    return a.func(a)


if __name__ == "__main__":
    sys.exit(main())
