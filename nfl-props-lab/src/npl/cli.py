"""`npl` command line. Every pipeline step is a repeatable, idempotent subcommand."""

from __future__ import annotations

import argparse
import json
import logging
import os
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


def cmd_snapshot(a) -> int:
    """Self-contained HTML copy of the app (saved outputs embedded; opens offline). It embeds
    US prices, so the default path is under data/ (git-ignored)."""
    from pathlib import Path

    from . import snapshot

    try:
        res = snapshot.build(Path(a.out), standalone=not a.fragment, min_p_play=a.min_p_play,
                             nba_min_p_play=a.nba_min_p_play, allow_oversize=a.allow_oversize)
    except snapshot.SnapshotTooLarge as e:
        print(f"REFUSED: {e}")
        return 4
    print(json.dumps(res, indent=1))
    return 0


def cmd_nba(a) -> int:
    """NBA jobs: update (fetch + build), publish (edition for an ET date), settle, quotes."""
    from .nba import canon, live, settle, source

    con = db.connect()
    if a.action == "update":
        # SportsDataverse only (D-046): a finished game arrives with its next-morning release
        out = source.fetch(con, seasons=[source.CURRENT_SEASON], max_age_hours=a.max_age_hours,
                           force=a.force)
        manifest = canon.build(con)
        out["build"] = manifest["counts"]
        from .nba import shots
        out["shots"] = shots.build(con)
    elif a.action == "publish":
        out = live.publish(a.date or live.next_date_et(), a.kind, con)
    elif a.action == "settle":
        out = settle.run()
    else:
        raise SystemExit(f"unknown action {a.action}")
    print(json.dumps(out, indent=1, default=str))
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


def cmd_picks(a) -> int:
    """Paper-tracking lists (D-043), saved to data/runs and added to the paper record (D-044)."""
    from . import picks, track
    from .config import DATA_HOME
    from .util import utcnow

    res = picks.nba() if a.sport == "nba" else picks.nfl(game_id=a.game)
    print(picks.table(res))
    if a.sport == "nba" and not res.get("error"):
        con, now = db.connect(), utcnow().isoformat()
        lists = [("simple_gap", picks.SIMPLE_RULE)]
        if res["model_version"] == picks.TRACK_MODEL:
            lists.insert(0, ("tracking", picks.TRACK_RULE))
        else:  # the frozen rule's stack was fitted on TRACK_MODEL's probabilities only
            print(f"Tracked rule not recorded: this edition was made by {res['model_version']}, "
                  f"the rule needs {picks.TRACK_MODEL}. Publish a new edition first.")
        for k, r in lists:
            n = track.record(con, res[k], rule=r, sport="nba", edition_id=res["edition_id"],
                             flagged_at=now)
            print(f"Paper record: {n} new {k} props for {r} (first flag only; see `npl track`).")
    out = DATA_HOME / "runs" / f"picks_{a.sport}_{utcnow().strftime('%Y%m%dT%H%MZ')}.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(res, indent=1, default=str))
    return 0


def cmd_track(a) -> int:
    """The paper record: every tracked prop once, settled, with closing-line value (D-044)."""
    from . import track

    print(track.table(track.report(db.connect()), last=a.last))
    return 0


def _skipped_lines(budget) -> list[str]:
    out = [f"SKIPPED {s['call']} (up to {s['estimated_credits']} credits): {s['reason']}"
           for s in budget.skipped]
    left = "unknown" if budget.remaining is None else f"{budget.remaining:,}"
    return out + [f"Credits left: {left} (floor {budget.floor:,}, env NPL_ODDS_FLOOR)."]


def cmd_quotes(a) -> int:
    from .sources import odds_api

    con = db.connect()
    try:
        odds_api.api_key()
        budget = odds_api.Budget()  # one floor for the whole pull (props, then game lines)
    except (odds_api.MissingKey, ValueError) as e:
        print(f"BLOCKED: {e}")
        print(json.dumps(odds_api.status(con), indent=1))
        return 3
    lines = a.sport == "nba" and not a.no_game_lines
    with rawstore.job(con, "quotes.snapshot", {"regions": a.regions, "sport": a.sport,
                                               "game_lines": lines}) as st:
        st["detail"] = odds_api.snapshot_upcoming(con, regions=a.regions,
                                                  hours_ahead=a.hours_ahead, sport=a.sport,
                                                  budget=budget)
        if lines:
            st["detail"]["game_lines"] = odds_api.snapshot_game_lines(
                con, sport=a.sport, regions=a.regions, budget=budget)
    print(json.dumps(st["detail"], indent=1, default=str))
    print("\n".join(_skipped_lines(budget)))
    return 0


def _uk_time(ts: str) -> str:
    from .config import LONDON
    from .util import parse_iso

    return parse_iso(ts).astimezone(LONDON).strftime("%a %H:%M %Z")


def close_lines(d: dict) -> list[str]:
    """What `npl close` prints: the planned calls with estimated credits, then what happened."""
    from .sources.odds_api import CLOSE_MARGIN_MIN

    rules = d.get("rules")
    which = f"props tracked under {', '.join(rules)}" if rules else "tracked props"
    out = [f"Closing snapshot, {d['sport'].upper()}: games with {which} tipping off within "
           f"{d['within_minutes']:g} min."]
    if not d["planned"]:
        return out + [f"Nothing planned: no game with {which} tips off in that window."]
    for p in d["planned"]:
        mk = ", ".join(p["markets"]) or "no market the provider prices"
        if p.get("started"):
            out.append(f"  {p.get('label') or p['game_id']}: started at "
                       f"{_uk_time(p['tip_utc'])} by the provider's start time; skipped")
            continue
        if p.get("too_close"):
            out.append(f"  {p.get('label') or p['game_id']}: starts at {_uk_time(p['tip_utc'])} "
                       f"by the provider's start time, within {CLOSE_MARGIN_MIN} min; skipped")
            continue
        out.append(f"  {p.get('label') or p['game_id']}, tip {_uk_time(p['tip_utc'])} "
                   f"(in {p['minutes_to_tip']:g} min): {mk}; up to {p['estimated_credits']} "
                   "credits")
    out += [f"WARNING {w}." for w in d.get("warnings", [])]
    calls = sum(1 for p in d["planned"] if p["markets"] and not p.get("started")
                and not p.get("too_close") and p.get("event_id", True))
    left = d.get("credits_left")
    out.append(f"Planned: {calls} call{'' if calls == 1 else 's'}, up to "
               f"{d['estimated_credits']} credits. Credits left: "
               f"{'unknown' if left is None else f'{left:,}'} (floor {d.get('floor', '?')}).")
    if d["dry_run"]:
        return out + ["Dry run: no paid call made. On a real run each game's start time is "
                      "checked again just before its call."]
    for r in d["results"]:
        out.append(f"  {r['label'] or r['game_id']}: {r['stored']} new quotes stored "
                   f"({r['linked_players']} linked to players).")
    out += [f"  SKIPPED {s['call']}: {s['reason']}" for s in d["skipped"]]
    left = d.get("credits_left")
    return out + [f"Credits left: {'unknown' if left is None else f'{left:,}'}."]


def cmd_close(a) -> int:
    """Closing snapshot for closing-line value (D-044): event odds shortly before tip-off, only
    for games and markets with props on the paper record; see odds_api.snapshot_close."""
    from .sources import odds_api

    con = db.connect()
    try:
        odds_api.api_key()
        budget = odds_api.Budget()
    except (odds_api.MissingKey, ValueError) as e:
        print(f"BLOCKED: {e}")
        return 3
    rules = getattr(a, "rules", None)
    with rawstore.job(con, "quotes.close", {"sport": a.sport, "within": a.within,
                                            "regions": a.regions, "dry_run": a.dry_run,
                                            "rules": rules}) as st:
        st["detail"] = odds_api.snapshot_close(con, sport=a.sport, within_minutes=a.within,
                                               regions=a.regions, dry_run=a.dry_run,
                                               budget=budget, rules=rules)
    print("\n".join(close_lines(st["detail"])))
    return 0


def cmd_backfill_pulls(a) -> int:
    """One-off: log the stored event-odds and featured-markets responses in the pull log
    (odds_pulls) from their stored JSON; prints counts only, never a price."""
    from .sources import odds_api

    con = db.connect()
    with rawstore.job(con, "quotes.backfill_pulls") as st:
        st["detail"] = odds_api.backfill_pulls(con)
    print(json.dumps(st["detail"], indent=1))
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
                 "/api/data", "/api/defense", "/api/nba/backtests", "/"):
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

    s = sub.add_parser("nba", help="NBA jobs: update | publish | settle")
    s.add_argument("action", choices=["update", "publish", "settle"])
    s.add_argument("--date", help="US (ET) date of the slate, YYYY-MM-DD (publish)")
    s.add_argument("--kind", default="early", help="edition label, e.g. early or final")
    s.add_argument("--max-age-hours", type=float, default=0.5)
    s.add_argument("--force", action="store_true")
    s.set_defaults(func=cmd_nba)

    s = sub.add_parser("snapshot", help="write a self-contained offline HTML copy of the app")
    s.add_argument("--out", default=str(config.DATA_HOME / "snapshots" / "nfl-props-lab.html"),
                   help="default data/snapshots/nfl-props-lab.html (git-ignored: the page "
                        "embeds US prices)")
    s.add_argument("--fragment", action="store_true",
                   help="omit the HTML document skeleton (for hosts that add their own)")
    # defaults chosen so a full slate stays under the host's limit: on the 2 Nov 2026 NBA slate
    # (15 games) with the Week 8 NFL edition the page measured 14.9 MB (18.2 MB at 0.5 / 0.5)
    s.add_argument("--min-p-play", type=float, default=0.9,
                   help="NFL: play probability a player needs for his player page and photo "
                        "(every NFL card is kept; default 0.9)")
    s.add_argument("--nba-min-p-play", type=float, default=0.6,
                   help="NBA: play probability a player needs for his cards, page and photo "
                        "(default 0.6, the props board's default filter)")
    s.add_argument("--allow-oversize", action="store_true",
                   help="write the page even above the host's 16,000,000-byte limit")
    s.set_defaults(func=cmd_snapshot)

    s = sub.add_parser("update", help="daily: ingest current season, audit, rebuild lake")
    s.add_argument("--seasons")
    # short window: availability files (injuries, rosters, depth charts) change on the
    # morning of games, and a 6-hour cache once hid nflverse's 07:00 UTC refresh
    s.add_argument("--max-age-hours", type=float, default=0.5)
    s.set_defaults(func=cmd_update)

    s = sub.add_parser("settle", help="append settlement revisions; score the record")
    s.set_defaults(func=cmd_settle)

    s = sub.add_parser("quotes", help="snapshot current prop quotes (needs ODDS_API_KEY)")
    s.add_argument("--regions", default="us",
                   help="provider regions; props exist only for US books (uk returns none)")
    s.add_argument("--hours-ahead", type=float, default=48)
    s.add_argument("--sport", choices=["nfl", "nba"], default="nfl")
    s.add_argument("--no-game-lines", action="store_true",
                   help="NBA: skip the game-lines snapshot (moneyline, spread, total; 3 credits)")
    s.set_defaults(func=cmd_quotes)

    s = sub.add_parser("close", help="closing snapshot of tracked props before tip-off (credits)")
    s.add_argument("--sport", choices=["nba", "nfl"], default="nba")
    s.add_argument("--within", type=float, required=True, metavar="MINUTES",
                   help="games with tracked props tipping off within this many minutes")
    s.add_argument("--regions", default="us")
    s.add_argument("--dry-run", action="store_true",
                   help="show the planned calls and credits; make no paid call")
    s.add_argument("--rule", action="append", dest="rules", metavar="RULE",
                   help="only props tracked under this rule (repeat for several; default: all)")
    s.set_defaults(func=cmd_close)

    s = sub.add_parser("backfill-pulls",
                       help="one-off: log stored odds responses in the pull log (counts only)")
    s.set_defaults(func=cmd_backfill_pulls)

    s = sub.add_parser("picks", help="paper-tracking lists against the stored market lines")
    s.add_argument("--sport", choices=["nfl", "nba"], default="nba")
    s.add_argument("--game", default=None, help="NFL game id, e.g. 2026_08_CAR_GB")
    s.set_defaults(func=cmd_picks)

    s = sub.add_parser("track", help="paper record: settled tracked props and closing-line value")
    s.add_argument("--last", type=int, default=30, help="rows to list")
    s.set_defaults(func=cmd_track)

    s = sub.add_parser("money", help="price-honest money backtest over a ledger")
    s.add_argument("--ledger", required=True)
    s.add_argument("--model", default="X")
    s.add_argument("--label", default="money")
    s.set_defaults(func=cmd_money)

    s = sub.add_parser("verify", help="lint, tests, edition hashes and API smoke test")
    s.set_defaults(func=cmd_verify)
    return p


def load_dotenv(path=None) -> None:
    """KEY=VALUE lines from the project's git-ignored .env; never overrides the real
    environment and never prints values."""
    path = path or config.PROJECT_ROOT / ".env"
    try:
        lines = path.read_text().splitlines()
    except OSError:
        return
    for line in lines:
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))


def main(argv: list[str] | None = None) -> int:
    load_dotenv()
    a = build_parser().parse_args(argv)
    logging.basicConfig(level=logging.INFO if a.verbose else logging.WARNING,
                        format="%(asctime)s %(levelname)s %(message)s")
    config.ensure_dirs()
    return a.func(a)


if __name__ == "__main__":
    sys.exit(main())
