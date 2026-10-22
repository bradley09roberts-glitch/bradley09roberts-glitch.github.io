"""Health report for the always-on server: `uv run footy doctor` (docs/server.md).

One line per check, each `ok`, `warn`, `problem` or `skipped`; the exit code is 1 when any
check finds a problem, so the scheduled jobs (deploy/jobs/) can send a push notification.
It only reads: the database, the job scripts' state folder, git and two web addresses.

Checks:
- `schema`: the database is at the code's latest migration.
- `jobs`: the last successful run of every command that records itself in `job_runs`
  (fd_update, settle, predict, ...), a failed latest run, and runs left "running" by a kill.
- `timers`: the last success of every scheduled job (deploy/jobs/*.sh write
  `<state>/last-success-<job>`), when the state folder exists (the server).
- `settlements`: genuine pre-match records still unsettled 3 hours after kick-off; a problem
  once a league game is 4 days past kick-off or a UEFA game 2 days (results sources lag).
- `fetches`: failed requests in the last 48 hours by source and HTTP status; a problem when a
  main source's latest request failed.
- `records`: the records files and the database agree (footy.publish.check_records), and,
  on the server, every records line is pushed (FOOTY_RECORDS_REF, e.g. refs/remotes/origin/main).
- `disk`: free space where the data lives.
- `web`: the API's /api/health and the public site (FOOTY_SITE_URL; 200 or 401 is up),
  only when FOOTY_SITE_URL is set or `--web` is given.

Server settings come from the environment (deploy/jobs/lib.sh sets them): FOOTY_STATE_DIR,
FOOTY_RECORDS_REF, FOOTY_SITE_URL, FOOTY_API_URL, FOOTY_SNAPSHOT_URL.
"""

import json
import os
import shutil
import subprocess
from collections import Counter, defaultdict
from collections.abc import Callable
from dataclasses import asdict, dataclass, field
from datetime import UTC, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

from sqlalchemy import Engine, func, select
from sqlalchemy.orm import Session

from footy.config import PROJECT_ROOT, Settings
from footy.models import FetchLog, JobRun, Prediction, PredictionSettlement

UK = ZoneInfo("Europe/London")
OK, WARN, PROBLEM, SKIPPED = "ok", "warn", "problem", "skipped"
ORDER = {PROBLEM: 0, WARN: 1, OK: 2, SKIPPED: 3}

# job_runs.job -> longest gap between successes before it is a problem. The scheduled jobs
# (deploy/systemd) run fd_update and settle daily, predict daily, euro_update on Mondays and
# UEFA nights, fd_fixtures on Tuesdays and Fridays.
JOB_MAX_AGE = {
    "fd_update": timedelta(hours=30),
    "settle": timedelta(hours=30),
    "predict": timedelta(hours=30),
    "euro_update": timedelta(days=4),
    "fd_fixtures": timedelta(days=4, hours=12),
    "odds_capture": timedelta(minutes=30),  # only when a live provider is configured
}
# deploy/jobs/<job>.sh -> longest gap between successes (deploy/systemd/*.timer)
TIMER_MAX_AGE = {
    "capture-odds": timedelta(minutes=20),
    "daily": timedelta(hours=26),
    "prices": timedelta(days=4, hours=6),
    "uefa-night": timedelta(days=5, hours=12),
    "monday": timedelta(days=7, hours=6),
    "backup": timedelta(hours=26),
    "update": timedelta(days=7, hours=6),
}
STALE_RUNNING = timedelta(hours=3)  # a job still "running" after this was killed
SETTLE_AFTER = timedelta(hours=3)  # a result can't be expected before kick-off + 3 h
SETTLE_GRACE = {"domestic": timedelta(days=4), "uefa": timedelta(days=2)}
UEFA = {"UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ"}
FETCH_WINDOW = timedelta(hours=48)
MAIN_SOURCES = {"football-data", "football-data-fixtures", "wikipedia", "sportmonks"}
FETCH_OK = {"new_revision", "unchanged", "not_modified", "cached"}
UNPUSHED_GRACE = timedelta(hours=2)
MIN_FREE_BYTES = 2 * 1024**3
MIN_FREE_FRACTION = 0.10


def worst(*statuses: str) -> str:
    """The most serious of these statuses (problem, then warn, ok, skipped)."""
    return min(statuses, key=lambda x: ORDER[x])


@dataclass
class Check:
    name: str
    status: str
    summary: str
    details: list[str] = field(default_factory=list)


@dataclass
class Report:
    generated_at: datetime
    checks: list[Check]
    summary: dict | None = None

    @property
    def problems(self) -> list[Check]:
        return [c for c in self.checks if c.status == PROBLEM]

    @property
    def warnings(self) -> list[Check]:
        return [c for c in self.checks if c.status == WARN]

    def exit_code(self) -> int:
        return 1 if self.problems else 0

    def headline(self) -> str:
        p, w = len(self.problems), len(self.warnings)
        if not p and not w:
            verdict = "all checks ok"
        else:
            verdict = ", ".join(
                s for s in (_plural(p, "problem"), _plural(w, "warning")) if not s.startswith("0")
            )
        return f"footy doctor, {_uk(self.generated_at)} UK: {verdict}"

    def text(self, quiet: bool = False) -> str:
        lines = [self.headline()]
        for c in sorted(self.checks, key=lambda c: ORDER[c.status]):
            if quiet and c.status in (OK, SKIPPED):
                continue
            label = {PROBLEM: "PROBLEM", WARN: "WARN"}.get(c.status, c.status)
            lines.append(f"{label:<8} {c.name:<12} {c.summary}")
            lines += [f"{'':<22}{d}" for d in c.details]
        if self.summary:
            lines.append("")
            lines += summary_lines(self.summary)
        return "\n".join(lines)

    def as_json(self) -> dict:
        return {
            "generated_at_utc": self.generated_at.isoformat(),
            "problems": len(self.problems),
            "warnings": len(self.warnings),
            "checks": [asdict(c) for c in self.checks],
            **({"summary": self.summary} if self.summary is not None else {}),
        }


def _plural(n: int, word: str) -> str:
    return f"{n} {word}{'' if n == 1 else 's'}"


def _uk(dt: datetime | None) -> str:
    return "never" if dt is None else dt.astimezone(UK).strftime("%a %d %b %H:%M")


def _ago(now: datetime, dt: datetime) -> str:
    s = (now - dt).total_seconds()
    if s < 3600:
        return f"{max(int(s // 60), 0)} min ago"
    if s < 2 * 86400:
        return f"{s / 3600:.0f} h ago"
    return f"{s / 86400:.1f} days ago"


def _aware(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    return dt if dt.tzinfo else dt.replace(tzinfo=UTC)


# --- checks -------------------------------------------------------------------------------


def check_schema(engine: Engine) -> Check:
    from footy.db import current_revision, head_revision

    try:
        current, head = current_revision(engine), head_revision()
    except Exception as e:  # an unreadable database is itself the problem
        return Check("schema", PROBLEM, f"can't read the database: {e}")
    if current == head:
        return Check("schema", OK, f"at the latest migration ({head})")
    return Check(
        "schema",
        PROBLEM,
        f"database at migration {current}, the code expects {head}",
        ["back up data/, then run `uv run footy migrate` (deploy/jobs/update.sh does both)"],
    )


def check_jobs(s: Session, now: datetime, provider_configured: bool) -> Check:
    names = sorted(s.scalars(select(JobRun.job).distinct()))
    details, status = [], OK
    for job in names:
        last = s.scalars(select(JobRun).where(JobRun.job == job).order_by(JobRun.id.desc())).first()
        last_ok = s.scalars(
            select(JobRun)
            .where(JobRun.job == job, JobRun.status == "ok")
            .order_by(JobRun.id.desc())
        ).first()
        ok_at = _aware(last_ok.finished_at or last_ok.started_at) if last_ok else None
        limit = JOB_MAX_AGE.get(job)
        if job == "odds_capture" and not provider_configured:
            limit = None
        line = f"{job}: last ok {_uk(ok_at)}" + (f" ({_ago(now, ok_at)})" if ok_at else "")
        flag = OK
        if limit is not None and (ok_at is None or now - ok_at > limit):
            flag = PROBLEM
            line += f", expected at least every {_span(limit)}"
        if last is not None and last.status == "failed":
            flag = PROBLEM
            error = (last.error or "")[:160]
            line += f"; latest run failed {_uk(_aware(last.started_at))}: {error}"
        elif last is not None and last.status == "partial":
            flag = worst(flag, WARN)
            line += f"; latest run partial {_uk(_aware(last.started_at))}"
        if flag != OK or limit is not None:
            details.append(("" if flag == OK else f"[{flag}] ") + line)
        status = worst(status, flag)
    stale = s.scalars(
        select(JobRun).where(
            JobRun.status == "running",
            JobRun.started_at < now - STALE_RUNNING,
            JobRun.started_at > now - timedelta(days=7),
        )
    ).all()
    if stale:
        status = worst(status, WARN)
        details.append(
            f"[warn] {len(stale)} run(s) still 'running' after {_span(STALE_RUNNING)} (killed): "
            + ", ".join(f"{r.job} #{r.id} {_uk(_aware(r.started_at))}" for r in stale[:5])
        )
    if not names:
        return Check("jobs", WARN, "no job has run yet", details)
    bad = [d for d in details if d.startswith("[")]
    summary = (
        f"{len(names)} commands; every scheduled one ran recently"
        if not bad
        else f"{len(bad)} of {len(names)} commands need attention"
    )
    return Check("jobs", status, summary, details if status != OK else [])


def _span(td: timedelta) -> str:
    h = td.total_seconds() / 3600
    return f"{h * 60:.0f} min" if h < 1 else f"{h:.0f} h" if h < 48 else f"{h / 24:.1f} days"


def check_timers(state_dir: Path | None, now: datetime) -> Check:
    if state_dir is None or not state_dir.is_dir():
        return Check("timers", SKIPPED, "no job state folder (not the server)")
    details, status = [], OK
    for job, limit in TIMER_MAX_AGE.items():
        ok_at = _read_stamp(state_dir / f"last-success-{job}")
        failed_at = _read_stamp(state_dir / f"last-failure-{job}")
        flag, line = OK, f"{job}: last success {_uk(ok_at)}"
        if ok_at is None or now - ok_at > limit:
            flag = PROBLEM
            line += f", expected at least every {_span(limit)}"
        if failed_at is not None and (ok_at is None or failed_at > ok_at):
            flag = PROBLEM
            line += f"; failed {_uk(failed_at)} (journalctl -u footy-{job})"
        status = worst(status, flag)
        details.append(("" if flag == OK else f"[{flag}] ") + line)
    bad = [d for d in details if d.startswith("[")]
    summary = (
        f"all {len(TIMER_MAX_AGE)} scheduled jobs succeeded on time"
        if not bad
        else f"{len(bad)} of {len(TIMER_MAX_AGE)} scheduled jobs late or failing"
    )
    return Check("timers", status, summary, bad)


def _read_stamp(path: Path) -> datetime | None:
    try:
        text = path.read_text().split()[0]
        return _aware(datetime.fromisoformat(text))
    except (OSError, IndexError, ValueError):
        return None


def check_settlements(s: Session, now: datetime) -> Check:
    from footy.models_of_record import fixture_time

    settled = select(PredictionSettlement.prediction_id)
    rows = s.execute(
        select(
            Prediction.fixture_ref,
            Prediction.competition,
            Prediction.match_date,
            Prediction.kickoff_utc,
            Prediction.home_team,
            Prediction.away_team,
        ).where(
            Prediction.record_type == "genuine_prematch",
            Prediction.match_date <= (now + timedelta(days=1)).date(),
            Prediction.id.not_in(settled),
        )
    ).all()
    fixtures: dict[str, dict] = {}
    for ref, comp, day, ko, home, away in rows:
        when = fixture_time(_aware(ko), day)
        if now - when < SETTLE_AFTER:
            continue
        f = fixtures.setdefault(
            ref,
            {"comp": comp, "when": when, "home": home, "away": away, "records": 0},
        )
        f["records"] += 1
    if not fixtures:
        return Check("settlements", OK, "nothing past kick-off + 3 h is unsettled")
    late = []
    for f in fixtures.values():
        group = "uefa" if f["comp"] in UEFA else "domestic"
        f["late"] = now - f["when"] > SETTLE_GRACE[group]
        late += [f] if f["late"] else []
    records = sum(f["records"] for f in fixtures.values())
    by_comp = Counter(f["comp"] for f in fixtures.values())
    summary = (
        f"{len(fixtures)} fixtures ({records} records) past kick-off + 3 h unsettled: "
        + ", ".join(f"{c} {n}" for c, n in sorted(by_comp.items()))
    )
    oldest = sorted(fixtures.values(), key=lambda f: f["when"])
    details = [
        f"{'[problem] ' if f['late'] else ''}{f['comp']} {_uk(f['when'])} "
        f"{f['home']} v {f['away']} ({f['records']} records, {_ago(now, f['when'])})"
        for f in oldest[:8]
    ]
    if len(oldest) > 8:
        details.append(f"... and {len(oldest) - 8} more")
    if late:
        summary += f"; {len(late)} overdue (league 4 days, UEFA 2 days after kick-off)"
    return Check("settlements", PROBLEM if late else WARN, summary, details)


def check_fetches(s: Session, now: datetime) -> Check:
    since = now - FETCH_WINDOW
    rows = s.execute(
        select(FetchLog.source, FetchLog.outcome, FetchLog.http_status, FetchLog.requested_at)
        .where(FetchLog.requested_at >= since)
        .order_by(FetchLog.id)
    ).all()
    failures: dict[str, Counter] = defaultdict(Counter)
    latest: dict[str, tuple[str, int | None, datetime]] = {}
    for source, outcome, status, at in rows:
        latest[source] = (outcome, status, _aware(at))
        if outcome not in FETCH_OK:
            failures[source][f"HTTP {status}" if status else outcome] += 1
    if not rows:
        return Check("fetches", OK, "no requests in the last 48 h")
    details, status = [], OK
    for source in sorted(failures):
        outcome, code, at = latest[source]
        last_ok = s.scalar(
            select(func.max(FetchLog.requested_at)).where(
                FetchLog.source == source, FetchLog.outcome.in_(FETCH_OK)
            )
        )
        failing_now = outcome not in FETCH_OK
        flag = (PROBLEM if source in MAIN_SOURCES else WARN) if failing_now else OK
        status = worst(status, flag)
        counts = ", ".join(f"{n} x {k}" for k, n in failures[source].most_common())
        details.append(
            f"{'[' + flag + '] ' if flag != OK else ''}{source}: {counts} in 48 h; latest "
            f"{'failed' if failing_now else 'ok'} {_uk(at)}; last success "
            f"{_uk(_aware(last_ok)) if last_ok else 'never'}"
        )
    n = len(rows)
    if not failures:
        return Check("fetches", OK, f"{n} requests in 48 h, none failed")
    bad = sum(sum(c.values()) for c in failures.values())
    summary = f"{bad} of {n} requests in 48 h failed ({', '.join(sorted(failures))})"
    return Check("fetches", status, summary, details)


def check_disk(path: Path) -> Check:
    try:
        usage = shutil.disk_usage(path)
    except OSError as e:
        return Check("disk", PROBLEM, f"can't read {path}: {e}")
    free_gb = usage.free / 1024**3
    pct = usage.free / usage.total if usage.total else 0
    summary = f"{free_gb:.1f} GB free of {usage.total / 1024**3:.0f} GB ({pct:.0%})"
    low = usage.free < MIN_FREE_BYTES or pct < MIN_FREE_FRACTION
    return Check("disk", PROBLEM if low else OK, summary + (" (low)" if low else ""))


def check_records(
    engine: Engine,
    records_dir: Path,
    *,
    now: datetime,
    ref: str | None,
    state_dir: Path | None,
    git: Callable[..., subprocess.CompletedProcess] | None = None,
) -> Check:
    details, status = [], OK
    try:
        from footy.publish import check_records as db_has_every_record
    except ImportError:  # an older footy.publish
        db_has_every_record = None
    if db_has_every_record is not None:
        try:
            db_has_every_record(engine, records_dir)
        except Exception as e:  # PublishError: the database lacks a published record
            status = PROBLEM
            details.append(f"[problem] the database and the records files disagree: {e}")
    files = sorted(records_dir.glob("*.jsonl")) if records_dir.is_dir() else []
    lines = 0
    for p in files:
        with p.open("rb") as f:
            lines += sum(1 for line in f if line.strip())
    pushed_at = _read_stamp(state_dir / "last-records-push") if state_dir else None
    summary = f"{lines} records in {len(files)} files"
    if pushed_at:
        summary += f"; last push {_uk(pushed_at)}"
    if ref and files:
        unpushed, missing, err = records_vs_ref(records_dir, files, ref, git or _git)
        if err:
            status = worst(status, WARN)
            details.append(f"[warn] can't compare the records with {ref}: {err}")
        elif missing:
            status = PROBLEM
            details.append(
                f"[problem] {ref} has records files this server lacks: {', '.join(missing)} "
                "(restore them, then `footy import-records`)"
            )
        if unpushed:
            changed = max(p.stat().st_mtime for p in files if p.name in unpushed)
            changed_at = datetime.fromtimestamp(changed, UTC)
            flag = PROBLEM if now - changed_at > UNPUSHED_GRACE else WARN
            status = worst(status, flag)
            details.append(
                f"[{flag}] {', '.join(unpushed)} changed {_uk(changed_at)} and differ from "
                f"{ref} (the jobs push records; deploy/jobs/lib.sh push_records)"
            )
        elif not err and not missing:
            summary += f"; every file matches {ref}"
    return Check("records", status, summary, details)


def records_vs_ref(
    records_dir: Path, files: list[Path], ref: str, git: Callable[..., subprocess.CompletedProcess]
) -> tuple[list[str], list[str], str]:
    """(local files whose content differs from `ref`'s, files in `ref` missing here, error),
    compared by blob id, so it doesn't matter which commit the working tree is at."""
    listing = git(records_dir, "ls-tree", "-r", ref, "--", ".")
    hashes = git(records_dir, "hash-object", "--", *[p.name for p in files])
    if listing.returncode != 0 or hashes.returncode != 0:
        return [], [], (listing.stderr or hashes.stderr).strip()[:200]
    theirs = {}
    for line in listing.stdout.splitlines():
        meta, _, path = line.partition("\t")
        parts = meta.split()
        if len(parts) == 3 and parts[1] == "blob" and "/" not in path:
            theirs[path] = parts[2]
    ours = dict(zip([p.name for p in files], hashes.stdout.split(), strict=True))
    unpushed = [name for name, blob in ours.items() if theirs.get(name) != blob]
    missing = sorted(n for n in theirs if n.endswith(".jsonl") and n not in ours)
    return unpushed, missing, ""


def _git(cwd: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["git", "-C", str(cwd), *args], capture_output=True, text=True, timeout=60
    )


def _http_status(url: str, timeout: float = 15.0) -> tuple[int | None, str]:
    import httpx

    try:
        r = httpx.get(url, timeout=timeout, follow_redirects=False)
        return r.status_code, ""
    except Exception as e:  # unreachable: reported, never raised
        return None, f"{type(e).__name__}: {e}"


def check_web(
    api_url: str | None,
    site_url: str | None,
    snapshot_url: str | None,
    http_status: Callable[[str], tuple[int | None, str]] = _http_status,
) -> Check:
    targets = [
        ("API", api_url, {200}),
        ("site", site_url, {200, 401}),  # 401: up, behind its password
        ("snapshot", snapshot_url, {200, 401}),
    ]
    targets = [t for t in targets if t[1]]
    if not targets:
        return Check("web", SKIPPED, "no address to check (FOOTY_SITE_URL unset)")
    details, status = [], OK
    for name, url, good in targets:
        code, err = http_status(url)
        if code in good:
            details.append(f"{name} {url}: HTTP {code}")
        else:
            status = PROBLEM
            details.append(f"[problem] {name} {url}: {f'HTTP {code}' if code else err}")
    down = [d for d in details if d.startswith("[")]
    summary = (
        "API and site answer"
        if not down
        else f"{len(down)} of {len(targets)} addresses not answering"
    )
    return Check("web", status, summary, down if down else [])


# --- weekly numbers -------------------------------------------------------------------------


def week_summary(s: Session, now: datetime) -> dict:
    """The last 7 days in numbers, for the Monday notification."""
    since = now - timedelta(days=7)
    published = s.execute(
        select(Prediction.competition, Prediction.fixture_ref).where(
            Prediction.created_at_utc >= since
        )
    ).all()
    settled = s.scalar(
        select(func.count(PredictionSettlement.id)).where(
            PredictionSettlement.settled_at_utc >= since
        )
    )
    upcoming = s.scalar(
        select(func.count(func.distinct(Prediction.fixture_ref))).where(
            Prediction.match_date >= now.astimezone(UK).date()
        )
    )
    groups = Counter("uefa" if c in UEFA else "domestic" for c, _ in published)
    fixtures = {
        g: len({ref for c, ref in published if ("uefa" if c in UEFA else "domestic") == g})
        for g in groups
    }
    return {
        "records_published": len(published),
        "by_group": {g: {"records": n, "fixtures": fixtures[g]} for g, n in groups.items()},
        "settlements": settled or 0,
        "upcoming_fixtures_with_records": upcoming or 0,
    }


def summary_lines(summary: dict) -> list[str]:
    parts = [
        f"{g}: {v['records']} records for {v['fixtures']} fixtures"
        for g, v in sorted(summary.get("by_group", {}).items())
    ]
    return [
        f"Last 7 days: {summary['records_published']} records published"
        + (f" ({'; '.join(parts)})" if parts else ""),
        f"{summary['settlements']} settled; "
        f"{summary['upcoming_fixtures_with_records']} upcoming fixtures have records",
    ]


# --- entry points ---------------------------------------------------------------------------


@dataclass
class Options:
    state_dir: Path | None = None
    records_ref: str | None = None
    api_url: str | None = None
    site_url: str | None = None
    snapshot_url: str | None = None
    web: bool = False
    summary: bool = False

    @classmethod
    def from_env(cls, web: bool = False, summary: bool = False) -> "Options":
        env = os.environ
        state = env.get("FOOTY_STATE_DIR")
        site = env.get("FOOTY_SITE_URL") or None
        check_web_now = web or bool(site)
        return cls(
            state_dir=Path(state) if state else None,
            records_ref=env.get("FOOTY_RECORDS_REF") or None,
            api_url=(env.get("FOOTY_API_URL") or "http://127.0.0.1:8000/api/health")
            if check_web_now
            else None,
            site_url=site,
            snapshot_url=env.get("FOOTY_SNAPSHOT_URL") or None,
            web=check_web_now,
            summary=summary,
        )


def run(
    engine: Engine,
    settings: Settings,
    options: Options,
    *,
    now: datetime | None = None,
    records_dir: Path | None = None,
    http_status: Callable[[str], tuple[int | None, str]] = _http_status,
    git: Callable[..., subprocess.CompletedProcess] | None = None,
) -> Report:
    from footy.publish import RECORDS_DIR

    now = now or datetime.now(UTC)
    records_dir = records_dir or RECORDS_DIR
    checks = [check_schema(engine)]
    schema_ok = checks[0].status == OK
    summary = None
    try:
        with Session(engine) as s:
            checks.append(check_jobs(s, now, settings.live_provider_configured()))
            checks.append(check_settlements(s, now))
            checks.append(check_fetches(s, now))
            if options.summary:
                summary = week_summary(s, now)
    except Exception as e:  # e.g. tables missing: the schema check already says why
        checks.append(
            Check("database", PROBLEM if schema_ok else WARN, f"can't read the tables: {e}")
        )
    checks.append(check_timers(options.state_dir, now))
    checks.append(
        check_records(
            engine,
            records_dir,
            now=now,
            ref=options.records_ref,
            state_dir=options.state_dir,
            git=git,
        )
    )
    checks.append(check_disk(settings.data_dir if settings.data_dir.exists() else PROJECT_ROOT))
    if options.web:
        checks.append(
            check_web(options.api_url, options.site_url, options.snapshot_url, http_status)
        )
    else:
        checks.append(Check("web", SKIPPED, "not checked (FOOTY_SITE_URL unset; --web checks)"))
    return Report(now, checks, summary)


def main(args) -> int:
    """`footy doctor`: print the report; exit 1 when any check finds a problem."""
    from footy.config import get_settings
    from footy.db import get_engine

    report = run(
        get_engine(),
        get_settings(),
        Options.from_env(web=getattr(args, "web", False), summary=getattr(args, "summary", False)),
    )
    if getattr(args, "json", False):
        print(json.dumps(report.as_json(), indent=2, default=str))
    else:
        print(report.text(quiet=getattr(args, "quiet", False)))
    return report.exit_code()
