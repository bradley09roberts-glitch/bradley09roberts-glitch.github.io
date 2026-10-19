"""Forward odds capture: snapshots of pre-match prices at fixed points before kickoff.

Run every 5 minutes from cron on an always-on machine (DECISIONS.md D-009):
    */5 * * * *  cd /path/to/football && uv run footy capture-odds

Rules, for each fixture and target (48 h, 24 h, 2 h, 15 min before kickoff):
- Not due before kickoff minus the offset.
- Due from then until the next target's time (until kickoff for the last one). Lateness is
  stored with every snapshot.
- If that window passes without a capture, the target is recorded as missed, so gaps stay
  visible. Nothing is ever captured at or after kickoff.
- Errors are recorded and retried on the next run while the window is still open.
"""

from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timedelta

from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from footy.config import Settings
from footy.ingest.jobs import job_run
from footy.ingest.raw_store import store_raw
from footy.models import CaptureEvent, OddsSnapshot, ProviderFixture, TeamAlias, utcnow
from footy.providers import get_provider
from footy.providers.base import LiveProvider

TARGETS: list[tuple[str, timedelta]] = [
    ("T-48h", timedelta(hours=48)),
    ("T-24h", timedelta(hours=24)),
    ("T-2h", timedelta(hours=2)),
    ("T-15m", timedelta(minutes=15)),
]
FINAL_STATUSES = ("captured", "no_odds", "missed")


@dataclass(frozen=True)
class Planned:
    provider_fixture_id: str
    target: str
    scheduled_for: datetime
    kickoff: datetime
    action: str  # capture | miss


def plan(
    fixtures: list[tuple[str, datetime]], done: set[tuple[str, str]], now: datetime
) -> list[Planned]:
    """What to do now for each (fixture id, kickoff). `done` holds (fixture id, target) pairs
    that already have a final outcome."""
    out = []
    for fid, kickoff in fixtures:
        for i, (target, offset) in enumerate(TARGETS):
            if (fid, target) in done:
                continue
            scheduled = kickoff - offset
            if now < scheduled:
                continue
            window_end = kickoff - TARGETS[i + 1][1] if i + 1 < len(TARGETS) else kickoff
            action = "capture" if now < window_end else "miss"
            out.append(Planned(fid, target, scheduled, kickoff, action))
    return out


def run_capture(
    engine: Engine,
    settings: Settings,
    *,
    horizon_days: int = 3,
    provider: LiveProvider | None = None,
    now: datetime | None = None,
) -> dict:
    provider = provider or get_provider(settings)
    with job_run(engine, "odds_capture") as ctx:
        if provider is None:
            ctx.detail.update(
                status="no_provider",
                message="No live provider key configured (DECISIONS.md D-010); nothing captured.",
            )
            return ctx.detail
        now = now or utcnow()
        counts: Counter = Counter()

        fetched = provider.upcoming_fixtures(
            now.date(), (now + timedelta(days=horizon_days)).date()
        )
        with Session(engine) as s:
            for page in fetched.pages:
                _store_page(s, settings, provider.name, page, now)
            changed = _upsert_fixtures(s, provider.name, fetched.fixtures, now)
            s.commit()
        ctx.detail["fixtures_seen"] = len(fetched.fixtures)
        ctx.detail["fixtures_skipped"] = fetched.skipped
        ctx.detail["kickoff_changes"] = changed

        with Session(engine) as s:
            upcoming = [
                (f.provider_fixture_id, f.kickoff_utc)
                for f in s.scalars(
                    select(ProviderFixture).where(
                        ProviderFixture.provider == provider.name,
                        ProviderFixture.kickoff_utc > now - timedelta(hours=1),
                    )
                )
            ]
            done = set(
                s.execute(
                    select(CaptureEvent.provider_fixture_id, CaptureEvent.target).where(
                        CaptureEvent.provider == provider.name,
                        CaptureEvent.status.in_(FINAL_STATUSES),
                    )
                ).all()
            )
        for p in plan(upcoming, done, now):
            counts[_execute(engine, settings, provider, p, now)] += 1
        ctx.detail["status"] = "ok"
        ctx.detail["outcomes"] = dict(counts)
        if counts.get("error"):
            ctx.partial = True
        return ctx.detail


def _execute(engine, settings, provider, p: Planned, now: datetime) -> str:
    lateness = int((now - p.scheduled_for).total_seconds())
    with Session(engine) as s:
        event = CaptureEvent(
            provider=provider.name,
            provider_fixture_id=p.provider_fixture_id,
            target=p.target,
            scheduled_for_utc=p.scheduled_for,
            attempted_at_utc=now,
            n_quotes=0,
            lateness_seconds=lateness,
        )
        if p.action == "miss":
            event.status = "missed"
            s.add(event)
            s.commit()
            return "missed"
        try:
            odds = provider.prematch_odds(p.provider_fixture_id)
        except Exception as exc:  # recorded and retried while the window is open
            event.status = "error"
            event.error = f"{type(exc).__name__}: {exc}"
            s.add(event)
            s.commit()
            return "error"
        raw_ids = [_store_page(s, settings, provider.name, page, now) for page in odds.pages]
        for q in odds.quotes:
            s.add(
                OddsSnapshot(
                    provider=provider.name,
                    provider_fixture_id=p.provider_fixture_id,
                    target=p.target,
                    scheduled_for_utc=p.scheduled_for,
                    captured_at_utc=now,
                    kickoff_utc_at_capture=p.kickoff,
                    bookmaker=q.bookmaker,
                    market_key=q.market_key,
                    line=q.line,
                    selection=q.selection,
                    price=q.price,
                    quote_time_utc=q.quote_time_utc,
                    raw_file_id=raw_ids[q.page],
                )
            )
        event.status = "captured" if odds.quotes else "no_odds"
        event.n_quotes = len(odds.quotes)
        event.raw_file_id = raw_ids[0] if raw_ids else None
        s.add(event)
        s.commit()
        return event.status


def _store_page(s: Session, settings: Settings, source: str, page, now) -> int:
    stored = store_raw(
        s,
        settings.raw_dir,
        source=source,
        logical_key=page.logical_key,
        content=page.content,
        retrieved_at=now,
        url=page.url,
        suffix=".json",
    )
    return stored.raw.id


def _upsert_fixtures(s: Session, provider: str, fixtures, now: datetime) -> list[dict]:
    aliases = dict(
        s.execute(
            select(TeamAlias.alias, TeamAlias.team_key).where(TeamAlias.source == provider)
        ).all()
    )
    changes = []
    for f in fixtures:
        row = s.get(ProviderFixture, (provider, f.provider_fixture_id))
        if row is None:
            s.add(
                ProviderFixture(
                    provider=provider,
                    provider_fixture_id=f.provider_fixture_id,
                    league_code=f.league_code,
                    kickoff_utc=f.kickoff_utc,
                    home_name=f.home_name,
                    away_name=f.away_name,
                    home_team=aliases.get(f.home_name),
                    away_team=aliases.get(f.away_name),
                    status=f.status,
                    first_seen_at=now,
                    last_seen_at=now,
                )
            )
            continue
        if row.kickoff_utc != f.kickoff_utc:
            changes.append(
                {"fixture": f.provider_fixture_id, "from": row.kickoff_utc, "to": f.kickoff_utc}
            )
            row.kickoff_utc = f.kickoff_utc
        row.status = f.status
        row.last_seen_at = now
        row.home_team = row.home_team or aliases.get(f.home_name)
        row.away_team = row.away_team or aliases.get(f.away_name)
    return changes
