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
        lake = features.load_lake()
        res = forecast.run_slate(lake, a.season, a.week, n_sims=a.sims, seed=a.seed)
        if a.dry_run:
            ok = [f for f in res["forecasts"] if f["status"] == "ok"]
            st["detail"] = {"dry_run": True, "forecasts_ok": len(ok)}
            print(json.dumps(st["detail"]))
            return 0
        st["detail"] = edition.publish(con, season=a.season, week=a.week, kind=a.kind,
                                       result=res, lake=lake, allow_dirty=a.allow_dirty,
                                       notes=a.note or [])
    print(json.dumps(st["detail"], indent=1))
    return 0


def cmd_serve(a) -> int:
    import uvicorn

    uvicorn.run("npl.api:app", host=a.host, port=a.port, reload=False, log_level="info")
    return 0


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
    s.set_defaults(func=cmd_publish)

    s = sub.add_parser("serve", help="run the web app (read-only over saved outputs)")
    s.add_argument("--host", default="127.0.0.1")
    s.add_argument("--port", type=int, default=8765)
    s.set_defaults(func=cmd_serve)
    return p


def main(argv: list[str] | None = None) -> int:
    a = build_parser().parse_args(argv)
    logging.basicConfig(level=logging.INFO if a.verbose else logging.WARNING,
                        format="%(asctime)s %(levelname)s %(message)s")
    config.ensure_dirs()
    return a.func(a)


if __name__ == "__main__":
    sys.exit(main())
