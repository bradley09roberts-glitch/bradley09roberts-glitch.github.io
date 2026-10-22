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
    pd_.add_argument(
        "--only-with-market",
        action="store_true",
        help="publish only fixtures with usable prices (always so for model + market "
        "candidates, except in a dry run)",
    )

    pe = sub.add_parser("predict-euro", help="publish early-edition UEFA predictions")
    pe_model = pe.add_mutually_exclusive_group()
    pe_model.add_argument(
        "--model",
        help="registered UEFA model name (footy.modeling.euro_registry), e.g. pan_euro_mix_mxg; "
        "its records are published under that name",
    )
    pe_model.add_argument(
        "--config",
        help='model config as JSON, e.g. {"family": "mix", "dc": {...}, "elo": {...}, "w_dc": 0.7}'
        " (default {}: pan-European Dixon-Coles)",
    )
    pe.add_argument("--model-name", help="record name for a --config model (default pan_euro_dc)")
    pe.add_argument("--comps", default="UCL,UEL,UECL")
    pe.add_argument("--days", type=int, default=3)
    pe.add_argument("--dry-run", action="store_true")
    pe.add_argument("--notes")

    sh = sub.add_parser(
        "publish-shadows",
        help="publish every shadow model's own pre-match records beside the models of record "
        "(reference/shadow_models.json, docs/shadow-records.md)",
    )
    sh.add_argument("--group", choices=["domestic", "uefa", "all"], default="all")
    sh.add_argument(
        "--days",
        type=int,
        help="days ahead (default as predict-domestic and predict-euro: 7 for league games, "
        "3 for UEFA)",
    )
    sh.add_argument("--divisions", default="E0,SP1,I1,D1,F1,E1")
    sh.add_argument("--comps", default="UCL,UEL,UECL")
    sh.add_argument("--models", help="comma-separated: only these listed shadow models")
    sh.add_argument("--dry-run", action="store_true")
    sh.add_argument(
        "--json",
        help="also write every model's outcome (records or dry-run forecasts, and each skipped "
        "fixture with why) to this file",
    )

    sub.add_parser("settle", help="settle published predictions whose results are in")

    ir = sub.add_parser(
        "import-records",
        help="load records/predictions/*.jsonl into a new or rebuilt database, keeping every "
        "id (sha256 checked), then settle; publishing refuses until the database holds them",
    )
    ir.add_argument("--dir", help="the records folder (default records/predictions)")

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
        "--fetch-uefa",
        action="store_true",
        help="disabled: UEFA.com terms forbid scripted collection (DECISIONS.md D-023)",
    )
    eu.add_argument(
        "--no-rebuild", action="store_true", help="load uefa_2627.csv as it is, without rebuilding"
    )

    eir = sub.add_parser(
        "euro-import-results",
        help="enter UEFA results read in news reports, two publishers each (DECISIONS.md D-029)",
    )
    eir.add_argument("file", help="JSON like comparisons/results_2026-10-21.json")
    eir.add_argument(
        "--correct",
        action="store_true",
        help="replace a different stored result (written to data_corrections first)",
    )
    eir.add_argument("--dry-run", action="store_true", help="report what would change only")

    ec = sub.add_parser(
        "euro-crosscheck", help="compare the openfootball matches with Wikipedia's match boxes"
    )
    ec.add_argument("--fetch", action="store_true", help="fetch the Wikipedia pages first")

    cap = sub.add_parser("capture-odds", help="take any due pre-match odds snapshots (cron: 5 min)")
    cap.add_argument("--horizon-days", type=int, default=3)

    fx = sub.add_parser(
        "fd-fixtures", help="fetch football-data's fixtures.csv (this week's prices; D-024)"
    )
    fx.add_argument("--force", action="store_true", help="ignore the 6-hour refresh limit")

    mr = sub.add_parser(
        "set-model-of-record",
        help="append a period to a group's models of record in primary_models.json (D-026)",
    )
    mr.add_argument("--group", required=True, choices=["domestic", "uefa"])
    mr.add_argument(
        "--models",
        required=True,
        help="comma-separated, in order of preference, e.g. market_tilt_dc,dixon_coles",
    )
    mr.add_argument(
        "--from",
        dest="from_utc",
        required=True,
        help='"now" or an ISO 8601 time with an offset, e.g. 2026-10-24T11:00:00Z; never earlier '
        "than now, so fixtures already played keep their model of record",
    )
    mr.add_argument("--note", help="optional reason saved with the period, e.g. a decision id")
    mr.add_argument("--file", help="the JSON file to change (default: the reference file)")

    ts = sub.add_parser(
        "team-stats",
        help="a team's statistics across league and UEFA matches (docs/teams.md)",
    )
    ts.add_argument("team", help="team key (e.g. eng-arsenal) or name (e.g. Arsenal)")
    ts.add_argument(
        "--type", choices=["league", "europe", "cup"], help="only this type of competition"
    )
    ts.add_argument("--csv", help="also write the full match log to this CSV file")

    snap = sub.add_parser(
        "export-snapshot",
        help="write a static, read-only copy of the website with the API's current responses",
    )
    snap.add_argument(
        "--out",
        required=True,
        help="folder for the site (index.html, assets/, snapshot.json, its shard files and "
        "media/); empty or an earlier snapshot, whose files are replaced",
    )
    snap.add_argument(
        "--single-page",
        help="also write this one-file page (script and styles inlined) with snapshot.json, "
        "its shard files and media/ beside it, for publishing as a private Artifact",
    )
    snap.add_argument(
        "--dist",
        help="the snapshot build of the frontend (default frontend/dist-snapshot, made by "
        "`npm run build:snapshot`)",
    )

    tm = sub.add_parser(
        "team-media",
        help="team pictures from Wikidata and Wikimedia Commons, free licences only "
        "(docs/research/team-media.md); with no option, a summary of what is stored",
    )
    tm.add_argument(
        "--map",
        action="store_true",
        help="rebuild reference/team_wikidata.csv from the Wikidata Query Service "
        "(manual rows kept)",
    )
    tm.add_argument(
        "--facts",
        action="store_true",
        help="rebuild reference/team_media.json (crest file, venue, colours) for mapped teams",
    )
    tm.add_argument(
        "--download",
        action="store_true",
        help="download crests and venue photos from Wikimedia Commons (resumable; stops at "
        "the first HTTP 429)",
    )
    tm.add_argument(
        "--team", action="append", help="with --download: only this team key (repeatable)"
    )
    tm.add_argument(
        "--offline",
        action="store_true",
        help="with --map or --facts: answer from the query cache only, no requests",
    )
    tm.add_argument(
        "--probe",
        action="store_true",
        help="make one Commons API request and report whether Wikimedia still returns 429",
    )

    doc = sub.add_parser(
        "doctor",
        help="health report: jobs, settlements, fetches, records, disk, website "
        "(docs/server.md); exit 1 on a problem",
    )
    doc.add_argument("--json", action="store_true", help="the report as JSON")
    doc.add_argument("--quiet", action="store_true", help="only problems and warnings")
    doc.add_argument("--web", action="store_true", help="check the API and site addresses too")
    doc.add_argument("--summary", action="store_true", help="add the last 7 days in numbers")

    args = parser.parse_args(argv)
    if args.command == "doctor":
        from footy.doctor import main as doctor_main

        return doctor_main(args)
    if args.command == "set-model-of-record":
        return _set_model_of_record(args)
    if args.command == "team-stats":
        return _team_stats(args)
    if args.command == "team-media":
        return _team_media(args)
    if args.command == "publish-shadows":
        return _publish_shadows(args, parser)
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
        _finish_killed_publish(args.dry_run)
        with job_run(get_engine(), "predict") as ctx:
            out = predict_domestic(
                get_engine(),
                factory,
                args.model,
                args.divisions.split(","),
                days=args.days,
                dry_run=args.dry_run,
                notes=args.notes,
                only_with_market=args.only_with_market,
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
        from footy.modeling.euro_registry import EURO_MODELS, euro_factory

        if args.model is not None:
            if args.model not in EURO_MODELS:
                parser.error(
                    f"unknown UEFA model {args.model!r}; registered: {', '.join(EURO_MODELS)}"
                )
            if args.model_name not in (None, args.model):
                parser.error("--model-name can't rename a registered model")
            if EURO_MODELS[args.model] is None:  # no team ratings: not a model of record
                parser.error(
                    f"{args.model} is a baseline: it publishes as a shadow record "
                    "(footy publish-shadows --group uefa)"
                )
            model_name, factory = args.model, euro_factory(args.model)
        else:
            model_name = args.model_name or "pan_euro_dc"
            if model_name in EURO_MODELS:  # its records must come from its pinned config
                parser.error(f"{model_name} is a registered UEFA model: use --model {model_name}")
            factory = _named(make_factory(json.loads(args.config or "{}")), model_name)
        _finish_killed_publish(args.dry_run)
        with job_run(get_engine(), "predict") as ctx:
            out = predict_euro(
                get_engine(),
                factory,
                model_name,
                tuple(args.comps.split(",")),
                days=args.days,
                dry_run=args.dry_run,
                notes=args.notes,
            )
            ctx.detail.update(
                kind="uefa", model=model_name, published=len(out), dry_run=args.dry_run
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
    elif args.command == "import-records":
        from pathlib import Path

        from footy.db import SchemaNotCurrent, get_engine, require_head
        from footy.publish import RECORDS_DIR, PublishError, import_records, settle

        engine = get_engine()
        try:  # never migrates (see require_head)
            require_head(engine)
        except SchemaNotCurrent as e:
            print(f"Refused: {e}")
            return 1
        try:
            res = import_records(engine, Path(args.dir) if args.dir else RECORDS_DIR)
        except PublishError as e:
            print(f"Refused, nothing imported: {e}")
            return 1
        res["settle"] = settle(engine)
        print(json.dumps(res, indent=2, default=str))
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
        # A fetch that failed (refused, or a network error) is a failure too, though the stored
        # pages were loaded: the results are not up to date (the job is "partial").
        gap = detail.get("wikipedia_gap", {})
        failed = failed or wiki.get("fetch_errors") or gap.get("fetch_errors")
        return 1 if failed else 0
    elif args.command == "euro-import-results":
        from pathlib import Path

        from footy.db import SchemaNotCurrent, get_engine, require_head
        from footy.europe.manual_results import ManualImportError, import_results
        from footy.ingest.jobs import job_run

        engine = get_engine()
        try:  # never migrates (see require_head)
            require_head(engine)
        except SchemaNotCurrent as e:
            print(f"Refused: {e}")
            return 1
        path = Path(args.file)
        try:
            if args.dry_run:
                report = import_results(
                    engine, settings.raw_dir, path, correct=args.correct, dry_run=True
                )
            else:
                with job_run(engine, "euro_import_results") as ctx:
                    report = import_results(engine, settings.raw_dir, path, correct=args.correct)
                    ctx.detail.update(report.as_dict())
                    ctx.partial = bool(report.refused)
        except (ManualImportError, OSError) as e:
            print(f"Refused: {e}")
            return 1
        print(json.dumps(report.as_dict(), indent=2, ensure_ascii=False))
        return 1 if report.refused else 0
    elif args.command == "euro-crosscheck":
        from footy.db import get_engine
        from footy.europe.of_crosscheck import run_crosscheck

        print(json.dumps(run_crosscheck(get_engine(), settings, fetch=args.fetch), indent=2))
    elif args.command == "capture-odds":
        from footy.capture import run_capture
        from footy.db import SchemaNotCurrent, get_engine, require_head

        try:  # runs unattended every 5 minutes: never migrates (see require_head)
            require_head(get_engine())
        except SchemaNotCurrent as e:
            print(f"Refused: {e}")
            return 1
        detail = run_capture(get_engine(), settings, horizon_days=args.horizon_days)
        print(json.dumps(detail, indent=2, default=str))
        return 0 if detail.get("status") in ("ok", "no_provider") else 1
    elif args.command == "export-snapshot":
        import sys
        from pathlib import Path

        from footy.snapshot import DEFAULT_DIST, SnapshotRefused, run_export

        def progress(n: int, key: str) -> None:
            if n % 100 == 0:
                print(f"  {n} requests ({key})", file=sys.stderr, flush=True)

        try:
            result = run_export(
                settings,
                Path(args.out).resolve(),
                dist=Path(args.dist).resolve() if args.dist else DEFAULT_DIST,
                progress=progress,
            )
        except (SnapshotRefused, FileExistsError, FileNotFoundError) as e:
            print(f"Refused, nothing written: {e}")
            return 1
        report = result.report()
        if args.single_page:
            from footy.snapshot import write_single_page

            try:
                written = write_single_page(result.out, Path(args.single_page).resolve())
            except SnapshotRefused as e:
                print(json.dumps(report, indent=2))
                print(f"Single page refused, nothing written beside it: {e}")
                return 1
            report["single_page"] = [{"path": str(p), "bytes": p.stat().st_size} for p in written]
        print(json.dumps(report, indent=2))
    elif args.command == "fd-fixtures":
        from sqlalchemy.orm import Session

        from footy.db import SchemaNotCurrent, get_engine, require_head
        from footy.ingest import fd_fixtures
        from footy.ingest.fd_download import PoliteClient
        from footy.ingest.jobs import job_run

        try:  # run by scheduled check-ins: never migrates (see require_head)
            require_head(get_engine())
        except SchemaNotCurrent as e:
            print(f"Refused: {e}")
            return 1
        if not settings.fd_download:
            print("football-data downloads are switched off (FOOTY_FD_DOWNLOAD=false)")
            return 1
        engine = get_engine()
        with job_run(engine, "fd_fixtures") as ctx:
            with Session(engine) as s:
                res = fd_fixtures.fetch(
                    s, PoliteClient(), settings.raw_dir, force=args.force, job_run_id=ctx.run_id
                )
                s.commit()
            snap = fd_fixtures.latest_snapshot(engine)
            ctx.detail.update(fetch=res.outcome, error=res.error)
            if snap is not None:
                ctx.detail.update(
                    retrieved_at=snap.attrs["retrieved_at"],
                    published_at=snap.attrs["published_at"],  # None: refused for live use
                    raw_sha256=snap.attrs["raw_sha256"],
                    **snap.attrs["report"],
                )
        print(json.dumps(ctx.detail, indent=2, default=str))
        return 0 if res.outcome != "error" else 1
    return 0


def _finish_killed_publish(dry_run: bool) -> None:
    """Finish the records of a publish that a container restart cut short, before anything
    else is published (footy.publish.recover_pending; a dry run only says if one is due)."""
    from footy.db import get_engine
    from footy.publish import (
        PENDING_PREFIX,
        RECORDS_DIR,
        PublishError,
        check_records,
        recover_pending,
    )

    if dry_run:
        if RECORDS_DIR.is_dir() and any(RECORDS_DIR.glob(f"{PENDING_PREFIX}*.json")):
            print("a killed publish is waiting to be finished by the next real run", flush=True)
        return
    n = recover_pending(get_engine())
    if n:
        print(f"finished {n} publish(es) cut short by a restart", flush=True)
    # refuse before any model is fitted if the database lacks published records (a rebuilt
    # database: `footy import-records`); `publish` itself refuses too
    try:
        check_records(get_engine())
    except PublishError as e:
        raise SystemExit(f"Refused: {e}") from None


def _publish_shadows(args, parser) -> int:
    from pathlib import Path

    from footy import shadows
    from footy.db import get_engine
    from footy.ingest.jobs import job_run

    listed = shadows.load()
    # UEFA first: its models take about a minute and its games are usually the sooner ones
    groups = ["uefa", "domestic"] if args.group == "all" else [args.group]
    models = None
    if args.models:
        models = [m.strip() for m in args.models.split(",") if m.strip()]
        known = {s.model for g in groups for s in listed.get(g, [])}
        unknown = [m for m in models if m not in known]
        if unknown:
            parser.error(f"not listed as {args.group} shadow models: {', '.join(unknown)}")
    if args.days is not None and args.days < 0:
        parser.error("--days must be 0 or more")
    engine = get_engine()
    print(f"{'dry run: ' if args.dry_run else ''}shadow records, {', '.join(groups)}", flush=True)
    _finish_killed_publish(args.dry_run)
    outcomes = []
    with job_run(engine, "predict") as ctx:
        for g in groups:
            days = args.days if args.days is not None else (3 if g == "uefa" else 7)
            outcomes += shadows.run(
                engine,
                [g],
                days=days,
                divisions=args.divisions.split(","),
                comps=args.comps.split(","),
                models=models,
                dry_run=args.dry_run,
                shadows=listed,
                report=lambda o: print(o.line(), flush=True),
            )
        failed = [o.model for o in outcomes if o.error]
        ctx.partial = bool(failed)
        ctx.detail.update(
            kind="shadows",
            groups=groups,
            dry_run=args.dry_run,
            published={o.model: o.published for o in outcomes},
            failed=failed,
        )
    if args.json:
        Path(args.json).write_text(
            json.dumps([o.as_json() for o in outcomes], indent=1, default=str) + "\n"
        )
    verb = "would publish" if args.dry_run else "published"
    print(
        f"{len(outcomes)} models: {verb} {sum(o.published for o in outcomes)} records, "
        f"{len(failed)} failed{': ' + ', '.join(failed) if failed else ''}"
    )
    return 1 if failed else 0


def _set_model_of_record(args) -> int:
    from pathlib import Path

    from footy import models_of_record
    from footy.api.routes import PRIMARY_MODELS_PATH

    path = Path(args.file) if args.file else PRIMARY_MODELS_PATH
    now = datetime.now(UTC)
    models = [m.strip() for m in args.models.split(",")]
    known = _published_model_names(args.group)
    unknown = [m for m in models if m not in known]
    if unknown:
        print(f"Refused, {path.name} unchanged: not a {args.group} model: {', '.join(unknown)}")
        return 1
    try:
        start = models_of_record.parse_from(args.from_utc, now)
        periods = models_of_record.append_period(
            path,
            args.group,
            models,
            start,
            note=args.note,
            now=now,
        )
    except ValueError as e:
        print(f"Refused, {path.name} unchanged: {e}")
        return 1
    print(f"{path}: {args.group} models of record")
    for p in periods:
        print(
            f"  from {models_of_record.format_utc(p.from_utc) or 'the start'}: "
            f"{', '.join(p.models)}{f'  ({p.note})' if p.note else ''}"
        )
    print("Fixtures kicking off before the new period keep their model of record.")
    return 0


def _team_stats(args) -> int:
    """Print a team's profile (footy.teams); --csv also writes its whole match log. Reads only."""
    from pathlib import Path

    from sqlalchemy.orm import Session

    from footy import teams
    from footy.api.routes import model_periods
    from footy.db import get_engine

    with Session(get_engine()) as s:
        found = teams.find_teams(s, args.team)
        if len(found) != 1:
            names = teams.team_names(s)
            listed = ", ".join(f"{k} ({names.get(k, k)})" for k in found[:20])
            print(
                f"{args.team!r} matches {len(found)} teams: {listed}"
                if found
                else f"No team matches {args.team!r}"
            )
            return 1
        key = found[0]
        periods = model_periods()
        profile = teams.team_profile(s, key, periods=periods, only_type=args.type)
        print(teams.profile_text(profile))
        if args.csv:
            log = teams.team_matches(s, key, periods=periods, type_=args.type) or []
            n = teams.write_csv(log[::-1], Path(args.csv))
            print(f"\nWrote {n} matches to {args.csv}")
    return 0


def _team_media(args) -> int:
    """Team pictures (footy.media). Exit 1 on a query error, 2 when a download stopped early."""
    import sys

    from footy.media import commons, facts, mapping
    from footy.media.wdqs import WdqsClient, WdqsError

    settings = get_settings()
    media_root = settings.data_dir / "media"
    if args.probe:
        print(json.dumps(commons.probe(commons.CommonsClient()), indent=2))
        return 0
    report: dict = {}
    if args.map or args.facts:
        wdqs = WdqsClient(media_root / "wdqs", offline=args.offline)
        try:
            if args.map:
                from footy.db import get_engine

                teams = mapping.teams_in_scope(get_engine())
                rows = mapping.build_mapping(teams, wdqs, mapping.read_mapping())
                mapping.write_mapping(rows)
                countries = {k: t.country for k, t in teams.items()}
                mapping.write_aliases(mapping.club_aliases(wdqs, rows, countries))
                report["mapping"] = {
                    "file": str(mapping.MAPPING_CSV),
                    "coverage": mapping.coverage(rows, teams),
                    "unmapped": {k: r.note for k, r in sorted(rows.items()) if not r.qid},
                }
            if args.facts:
                found = facts.fetch_facts(wdqs, mapping.read_mapping())
                facts.write_facts(found)
                report["facts"] = {"file": str(facts.MEDIA_JSON), **facts.summary(found)}
        except WdqsError as e:
            report["error"] = str(e)
        report["wdqs"] = {"requests": wdqs.requests_made, "cache_hits": wdqs.cache_hits}
        if "error" in report:
            print(json.dumps(report, indent=2))
            return 1
    rc = 0
    if args.download:

        def progress(o: commons.Outcome) -> None:
            print(f"  {o.team_key} {o.kind}: {o.status} {o.detail}", file=sys.stderr, flush=True)

        report["download"] = commons.run_download(
            facts.read_facts(),
            media_root / "teams",
            commons.CommonsClient(),
            only=args.team,
            progress=progress,
        )
        rc = 0 if report["download"]["status"] == "complete" else 2
    if not (args.map or args.facts or args.download):
        rows = mapping.read_mapping()
        stored = facts.read_facts()
        manifests = [commons.read_manifest(media_root / "teams" / k) for k in sorted(stored)]
        report = {
            "mapped": sum(1 for r in rows.values() if r.qid),
            "unmapped": sum(1 for r in rows.values() if not r.qid),
            "facts": facts.summary(stored),
            "downloaded": {
                kind: sum(1 for m in manifests if kind in m.get("files", {}))
                for kind in ("crest", "venue")
            },
            "refused": sum(len(m.get("refused", [])) for m in manifests),
        }
    print(json.dumps(report, indent=2, default=str))
    return rc


def _published_model_names(group: str) -> set[str]:
    """Names a group's records can carry: registered domestic candidates, or UEFA models."""
    if group == "uefa":
        from footy.modeling.euro_registry import EURO_MODELS

        return set(EURO_MODELS)
    from footy.modeling.registry import candidates

    return set(candidates())


def _named(factory, name: str):
    def make():
        m = factory()
        m.name = name
        return m

    return make


if __name__ == "__main__":
    raise SystemExit(main())
