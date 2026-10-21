"""Command-line entry point: `uv run footy <command>`."""

import argparse
import json
from datetime import UTC, datetime

from footy.config import get_settings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="footy")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("migrate", help="apply database migrations up to head")

    serve = sub.add_parser("serve", help="run the API server")
    serve.add_argument("--host", default="127.0.0.1")
    serve.add_argument("--port", type=int, default=8000)
    serve.add_argument("--reload", action="store_true")

    fd = sub.add_parser("fd-update", help="download (if enabled) and import football-data CSVs")
    fd.add_argument("--division", action="append", help="e.g. E0 (repeatable; default all)")
    fd.add_argument("--season", action="append", help="e.g. 2526 (repeatable; default all)")
    fd.add_argument("--force", action="store_true", help="re-download even if cached")
    fd.add_argument("--reimport", action="store_true", help="re-parse the latest revisions")
    fd.add_argument("--no-download", action="store_true", help="use stored and inbox files only")
    fd.add_argument(
        "--scope",
        choices=["all", "core", "europe"],
        default="all",
        help="core: the ten M1 divisions; europe: the extra European leagues (default all)",
    )

    cov = sub.add_parser("coverage", help="coverage report per division, season and field")
    cov.add_argument("--markdown", help="also write the report as Markdown to this path")

    spot = sub.add_parser("spot-check", help="compare N random imported matches with raw rows")
    spot.add_argument("-n", type=int, default=20)
    spot.add_argument("--seed", type=int, default=20261019)
    spot.add_argument("--markdown", help="write the comparison as Markdown to this path")

    lab = sub.add_parser("lab-validate", help="walk-forward validation (2023/24) of named models")
    lab.add_argument("models", nargs="+", help="model names (see footy.modeling.registry)")
    lab.add_argument("--seasons", default="2324", help="comma-separated, default 2324")
    lab.add_argument("--workers", type=int, default=2)
    lab.add_argument("--baseline", default="baseline_frequency")

    pd_ = sub.add_parser("predict-domestic", help="publish early-edition league predictions")
    pd_.add_argument(
        "--model", required=True, help="registered model name (footy.modeling.registry)"
    )
    pd_.add_argument("--divisions", default="E0,SP1,I1,D1,F1,E1")
    pd_.add_argument("--days", type=int, default=7)
    pd_.add_argument("--dry-run", action="store_true")
    pd_.add_argument("--notes")

    pe = sub.add_parser("predict-euro", help="publish early-edition UEFA predictions")
    pe.add_argument(
        "--config",
        default="{}",
        help='model config as JSON, e.g. {"family": "mix", "dc": {...}, "elo": {...}, "w_dc": 0.7}',
    )
    pe.add_argument("--model-name", default="pan_euro_dc")
    pe.add_argument("--comps", default="UCL,UEL,UECL")
    pe.add_argument("--days", type=int, default=3)
    pe.add_argument("--dry-run", action="store_true")
    pe.add_argument("--notes")

    sub.add_parser("settle", help="settle published predictions whose results are in")

    fr = sub.add_parser(
        "final-report", help="validation selection, one test run, benchmark, profit"
    )
    fr.add_argument("--selected", required=True)
    fr.add_argument("--candidates", default="", help="comma-separated lab candidates to compare")
    fr.add_argument("--workers", type=int, default=3)

    eu = sub.add_parser("euro-update", help="load UEFA competition matches into euro_matches")
    eu.add_argument(
        "--openfootball-dir",
        help="clone of openfootball/champions-league (default: $FOOTY_OPENFOOTBALL_DIR or "
        "../../openfootball/champions-league from the project root); without one, the stored "
        "revisions are used",
    )
    eu.add_argument(
        "--fetch-wikipedia", action="store_true", help="re-fetch the 2026/27 Wikipedia pages first"
    )
    eu.add_argument(
        "--fetch-uefa", action="store_true", help="fetch UEFA.com pages not yet stored (slow)"
    )
    eu.add_argument(
        "--no-rebuild", action="store_true", help="load uefa_2627.csv as it is, without rebuilding"
    )

    ec = sub.add_parser(
        "euro-crosscheck", help="compare the openfootball matches with Wikipedia's match boxes"
    )
    ec.add_argument("--fetch", action="store_true", help="fetch the Wikipedia pages first")

    cap = sub.add_parser("capture-odds", help="take any due pre-match odds snapshots (cron: 5 min)")
    cap.add_argument("--horizon-days", type=int, default=3)

    args = parser.parse_args(argv)
    settings = get_settings()

    if args.command == "migrate":
        from footy.db import upgrade_to_head

        upgrade_to_head()
        print(f"Migrated {settings.database_url}")
    elif args.command == "serve":
        import uvicorn

        from footy.db import upgrade_to_head

        upgrade_to_head()
        uvicorn.run("footy.api.main:app", host=args.host, port=args.port, reload=args.reload)
    elif args.command == "fd-update":
        from footy.db import get_engine, upgrade_to_head
        from footy.ingest.fd_update import run_fd_update

        upgrade_to_head()
        detail = run_fd_update(
            get_engine(),
            settings,
            today=datetime.now(UTC).date(),
            divisions=args.division,
            seasons=args.season,
            force=args.force,
            reimport=args.reimport,
            allow_download=not args.no_download,
            scope=args.scope,
        )
        print(json.dumps(detail, indent=2, default=str))
        return 1 if detail.get("import_failures") or detail.get("download_problems") else 0
    elif args.command == "coverage":
        from footy.coverage import coverage_markdown, coverage_rows
        from footy.db import get_engine

        rows = coverage_rows(get_engine())
        md = coverage_markdown(rows, generated_at=datetime.now(UTC))
        if args.markdown:
            with open(args.markdown, "w", encoding="utf-8") as f:
                f.write(md)
        print(md)
    elif args.command == "spot-check":
        from footy.db import get_engine
        from footy.spot_check import run_spot_check

        ok, md = run_spot_check(get_engine(), settings, n=args.n, seed=args.seed)
        if args.markdown:
            with open(args.markdown, "w", encoding="utf-8") as f:
                f.write(md)
        print(md)
        return 0 if ok else 1
    elif args.command == "lab-validate":
        import pandas as pd

        from footy.modeling.runner import compare, run_models, save, to_json

        names = list(dict.fromkeys([args.baseline, *args.models]))
        seasons = args.seasons.split(",")
        leds = run_models(names, seasons, workers=args.workers)
        split = "validation" if seasons == ["2324"] else "custom"
        for m, led in leds.items():
            save(m, led, split)
        table = compare(leds, baseline=args.baseline)
        out = (
            settings.data_dir
            / "lab"
            / f"{'+'.join(sorted(args.models))[:120]}__{'-'.join(seasons)}.json"
        )
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(to_json(table))
        with pd.option_context("display.width", 250, "display.max_columns", 40):
            print(table.to_string())
        print(f"saved {out}")
    elif args.command == "predict-domestic":
        from footy.db import get_engine
        from footy.ingest.jobs import job_run
        from footy.live import predict_domestic
        from footy.modeling.registry import candidates

        factory = candidates()[args.model]
        with job_run(get_engine(), "predict") as ctx:
            out = predict_domestic(
                get_engine(),
                factory,
                args.model,
                args.divisions.split(","),
                days=args.days,
                dry_run=args.dry_run,
                notes=args.notes,
            )
            ctx.detail.update(
                kind="domestic", model=args.model, published=len(out), dry_run=args.dry_run
            )
        print(json.dumps(out, indent=1, default=str))
    elif args.command == "predict-euro":
        from footy.db import get_engine
        from footy.ingest.jobs import job_run
        from footy.live import predict_euro
        from footy.modeling.euro_eval import make_factory

        config = json.loads(args.config)
        with job_run(get_engine(), "predict") as ctx:
            out = predict_euro(
                get_engine(),
                _named(make_factory(config), args.model_name),
                args.model_name,
                tuple(args.comps.split(",")),
                days=args.days,
                dry_run=args.dry_run,
                notes=args.notes,
            )
            ctx.detail.update(
                kind="uefa", model=args.model_name, published=len(out), dry_run=args.dry_run
            )
        print(json.dumps(out, indent=1, default=str))
    elif args.command == "final-report":
        from footy.modeling.report import run

        cands = [c for c in args.candidates.split(",") if c]
        print(
            json.dumps(run(args.selected, cands, workers=args.workers), indent=1, default=str)[
                :4000
            ]
        )
    elif args.command == "settle":
        from footy.db import get_engine
        from footy.ingest.jobs import job_run
        from footy.publish import settle

        with job_run(get_engine(), "settle") as ctx:
            res = settle(get_engine())
            ctx.detail.update(res)
        print(json.dumps(res))
    elif args.command == "euro-update":
        from pathlib import Path

        from footy.db import get_engine, upgrade_to_head
        from footy.europe.euro_load import run_euro_update

        upgrade_to_head()
        detail = run_euro_update(
            get_engine(),
            settings,
            openfootball_dir=Path(args.openfootball_dir) if args.openfootball_dir else None,
            rebuild_2627=not args.no_rebuild,
            fetch_wikipedia=args.fetch_wikipedia,
            fetch_uefa=args.fetch_uefa,
        )
        print(json.dumps(detail, indent=2, default=str))
        wiki = detail.get("wikipedia", {})
        failed = wiki.get("rebuild_error") or str(wiki.get("csv", "")).startswith("missing")
        return 1 if failed else 0
    elif args.command == "euro-crosscheck":
        from footy.db import get_engine
        from footy.europe.of_crosscheck import run_crosscheck

        print(json.dumps(run_crosscheck(get_engine(), settings, fetch=args.fetch), indent=2))
    elif args.command == "capture-odds":
        from footy.capture import run_capture
        from footy.db import get_engine, upgrade_to_head

        upgrade_to_head()
        detail = run_capture(get_engine(), settings, horizon_days=args.horizon_days)
        print(json.dumps(detail, indent=2, default=str))
        return 0 if detail.get("status") in ("ok", "no_provider") else 1
    return 0


def _named(factory, name: str):
    def make():
        m = factory()
        m.name = name
        return m

    return make


if __name__ == "__main__":
    raise SystemExit(main())
