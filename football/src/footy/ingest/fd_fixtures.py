"""This week's fixtures with football-data's current prices (`fixtures.csv`), for live
model + market predictions (footy.modeling.lab.market_blend).

football-data publishes `fixtures.csv` with the coming fixtures and the prices it collects at
the same times as the season files' pre-closing prices (D-012): Friday afternoons for the
weekend, Tuesday afternoons for midweek. It is fetched under the same rules as the season files
(D-013, extended by D-024): politely, at most once every 6 hours unless forced, stored once in
the raw store and never committed. The prices are quoted at the latest when the file was
first retrieved, so `quote_time_utc` is that retrieval time (identical content keeps its first
retrieval). `published_utc` is when football-data published the file (its HTTP Last-Modified,
if earlier than the retrieval), which says how old the prices may be: the model + market
candidate refuses a file published before the fixture's own D-012 collection day. A file
whose Last-Modified header is missing or unreadable has no known publication time, so its
prices are not used for live predictions at all (the reason is logged and reported).
"""

import csv
import io
import logging
from datetime import UTC, datetime
from email.utils import parsedate_to_datetime
from pathlib import Path

import pandas as pd
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy.ingest.fd_download import FetchResult, PoliteClient, _fetch_file, _looks_like_fd_csv
from footy.ingest.fd_import import alias_map
from footy.ingest.fd_parse import (
    BOOKS_1X2,
    BOOKS_OU25,
    OU_SELECTIONS,
    OVERROUND_RANGE,
    _parse_date,
    decode,
)
from footy.ingest.raw_store import latest_revision, read_raw
from footy.reference import DIVISIONS

log = logging.getLogger(__name__)

SOURCE = "football-data-fixtures"
LOGICAL_KEY = "fixtures"
URL = "https://football-data.co.uk/fixtures.csv"
COLUMNS = [
    "division",
    "date",
    "kickoff_local",
    "home_team",
    "away_team",
    "bookmaker",
    "market_key",
    "selection",
    "price",
    "quote_time_utc",
    "published_utc",
]


def fetch(
    session: Session,
    client: PoliteClient,
    raw_dir: Path,
    *,
    force: bool = False,
    refresh_hours: float = 6.0,
    job_run_id: int | None = None,
) -> FetchResult:
    return _fetch_file(
        session,
        client,
        raw_dir,
        source=SOURCE,
        key=LOGICAL_KEY,
        url=URL,
        final=False,
        looks_ok=_looks_like_fd_csv,
        job_run_id=job_run_id,
        force=force,
        refresh_hours=refresh_hours,
    )


def published_time(last_modified: str | None, retrieved_at: datetime) -> datetime | None:
    """When the file was published: its HTTP Last-Modified, but never later than the
    retrieval (a header in the future is clock skew). None when the header is missing or
    unreadable: the retrieval time can't stand in, because a file can sit on the site for
    days before it is fetched (Tuesday's prices still there on Friday, say)."""
    ret = retrieved_at if retrieved_at.tzinfo else retrieved_at.replace(tzinfo=UTC)
    try:
        lm = parsedate_to_datetime(last_modified) if last_modified else None
    except (TypeError, ValueError, IndexError):
        lm = None
    if lm is None or lm.tzinfo is None:
        return None
    return min(lm.astimezone(UTC), ret)


def unknown_publication(last_modified: str | None) -> str:
    """Why a file's prices are refused for live use when its publication time is unknown."""
    if not last_modified:
        return "no Last-Modified header, so the publication time is unknown"
    return f"unreadable Last-Modified header {last_modified!r}, so the publication time is unknown"


def parse(
    content: bytes,
    aliases: dict[str, str],
    retrieved_at: datetime,
    published_at: datetime | None = None,
) -> tuple[pd.DataFrame, dict]:
    """Pre-closing prices per fixture for the divisions this project covers. Returns the
    prices and a report (rows read, fixtures kept, unmapped names, unreadable dates, dropped
    price sets). Without `published_at` the rows' `published_utc` is empty (unknown), and the
    staleness rule in footy.modeling.lab.market_blend refuses them."""
    text_, _ = decode(content)
    reader = csv.DictReader(io.StringIO(text_))
    rows, unmapped, dropped, kept, read, bad_dates = [], set(), [], set(), 0, []
    for row in reader:
        div = (row.get("Div") or "").strip()
        if div not in DIVISIONS:
            continue
        read += 1
        home, away = (row.get("HomeTeam") or "").strip(), (row.get("AwayTeam") or "").strip()
        if home not in aliases or away not in aliases:
            unmapped |= {n for n in (home, away) if n not in aliases}
            continue
        try:
            day = _parse_date((row.get("Date") or "").strip())
        except ValueError:
            bad_dates.append(f"{div} {home} v {away}: {(row.get('Date') or '').strip()!r}")
            continue
        base = {
            "division": div,
            "date": day.isoformat(),
            "kickoff_local": (row.get("Time") or "").strip() or None,
            "home_team": aliases[home],
            "away_team": aliases[away],
            "quote_time_utc": retrieved_at,
            "published_utc": published_at,
        }
        for books, market, sels in (
            (BOOKS_1X2, "1X2", {"H": "H", "D": "D", "A": "A"}),
            (BOOKS_OU25, "OU_2.5", {s: OU_SELECTIONS[s] for s in (">", "<")}),
        ):
            for book, (pre_cols, _closing) in books.items():
                prices = {}
                for col_s, sel in sels.items():
                    for pattern in pre_cols:
                        v = (row.get(pattern.format(s=col_s)) or "").strip()
                        if v:
                            prices[sel] = v
                            break
                if len(prices) != len(sels):
                    continue
                try:
                    vals = {k: float(v) for k, v in prices.items()}
                except ValueError:
                    dropped.append(f"{div} {home} v {away} {book} {market}: not a number")
                    continue
                if min(vals.values()) <= 1.0:
                    dropped.append(f"{div} {home} v {away} {book} {market}: price <= 1")
                    continue
                overround = sum(1 / v for v in vals.values())
                if not OVERROUND_RANGE[0] <= overround <= OVERROUND_RANGE[1]:
                    dropped.append(f"{div} {home} v {away} {book} {market}: {overround:.3f}")
                    continue
                kept.add((div, day, home, away))
                rows.extend(
                    base | {"bookmaker": book, "market_key": market, "selection": s, "price": p}
                    for s, p in vals.items()
                )
    report = {
        "rows_in_covered_divisions": read,
        "fixtures_with_prices": len(kept),
        "unmapped_names": sorted(unmapped),
        "unreadable_dates": bad_dates,
        "dropped_price_sets": dropped,
    }
    return pd.DataFrame(rows, columns=COLUMNS), report


def latest_snapshot(engine: Engine, raw_dir: Path | None = None) -> pd.DataFrame | None:
    """Prices from the latest stored `fixtures.csv`, or None if it was never fetched.
    `attrs` carries the raw file's SHA-256, retrieval time and publication time. If the
    publication time is unknown (no readable Last-Modified), `published_at` is None, the
    reason is in `attrs["live_use_refused"]` and in the report, and a warning is logged: the
    rows keep an empty `published_utc`, so no live prediction uses them."""
    from footy.config import get_settings

    raw_dir = raw_dir or get_settings().raw_dir
    with Session(engine) as s:
        raw = latest_revision(s, SOURCE, LOGICAL_KEY)
        if raw is None:
            return None
        aliases = alias_map(s)
        content = read_raw(raw_dir, raw)
        published = published_time(raw.http_last_modified, raw.retrieved_at)
        df, report = parse(content, aliases, raw.retrieved_at, published)
        refused = None if published is not None else unknown_publication(raw.http_last_modified)
        if refused:
            report["live_use_refused"] = refused
            log.warning(
                "fixtures.csv raw file %s (retrieved %s): %s; its prices will not be used "
                "for live predictions",
                raw.id,
                raw.retrieved_at.isoformat(),
                refused,
            )
        df.attrs.update(
            raw_sha256=raw.sha256,
            retrieved_at=raw.retrieved_at.isoformat(),
            published_at=None if published is None else published.isoformat(),
            raw_file_id=raw.id,
            report=report,
        )
        if refused:
            df.attrs["live_use_refused"] = refused
    return df
