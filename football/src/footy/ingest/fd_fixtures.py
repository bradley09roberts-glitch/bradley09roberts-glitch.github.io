"""This week's fixtures with football-data's current prices (`fixtures.csv`), for live
model + market predictions (footy.modeling.lab.market_blend).

football-data publishes `fixtures.csv` with the coming fixtures and the prices it collects at
the same times as the season files' pre-closing prices (D-012): Friday afternoons for the
weekend, Tuesday afternoons for midweek. It is fetched under the same rules as the season files
(D-013, extended by D-024): politely, at most once every 6 hours unless forced, stored once in
the raw store and never committed. The prices are quoted at the latest when the file was
retrieved, so `quote_time_utc` is the retrieval time.
"""

import csv
import io
from datetime import datetime
from pathlib import Path

import pandas as pd
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy.ingest.fd_download import FetchResult, PoliteClient, _fetch_file, _looks_like_fd_csv
from footy.ingest.fd_import import alias_map
from footy.ingest.fd_parse import BOOKS_1X2, BOOKS_OU25, OU_SELECTIONS, OVERROUND_RANGE, decode
from footy.ingest.raw_store import latest_revision, read_raw
from footy.reference import DIVISIONS

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


def parse(
    content: bytes, aliases: dict[str, str], retrieved_at: datetime
) -> tuple[pd.DataFrame, dict]:
    """Pre-closing prices per fixture for the divisions this project covers. Returns the
    prices and a report (rows read, fixtures kept, unmapped names, dropped price sets)."""
    text_, _ = decode(content)
    reader = csv.DictReader(io.StringIO(text_))
    rows, unmapped, dropped, kept, read = [], set(), [], set(), 0
    for row in reader:
        div = (row.get("Div") or "").strip()
        if div not in DIVISIONS:
            continue
        read += 1
        home, away = (row.get("HomeTeam") or "").strip(), (row.get("AwayTeam") or "").strip()
        if home not in aliases or away not in aliases:
            unmapped |= {n for n in (home, away) if n not in aliases}
            continue
        day = datetime.strptime(row["Date"].strip(), "%d/%m/%Y").date()  # noqa: DTZ007
        base = {
            "division": div,
            "date": day.isoformat(),
            "kickoff_local": (row.get("Time") or "").strip() or None,
            "home_team": aliases[home],
            "away_team": aliases[away],
            "quote_time_utc": retrieved_at,
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
        "dropped_price_sets": dropped,
    }
    return pd.DataFrame(rows, columns=COLUMNS), report


def latest_snapshot(engine: Engine, raw_dir: Path | None = None) -> pd.DataFrame | None:
    """Prices from the latest stored `fixtures.csv`, or None if it was never fetched.
    `attrs` carries the raw file's SHA-256 and retrieval time."""
    from footy.config import get_settings

    raw_dir = raw_dir or get_settings().raw_dir
    with Session(engine) as s:
        raw = latest_revision(s, SOURCE, LOGICAL_KEY)
        if raw is None:
            return None
        aliases = alias_map(s)
        content = read_raw(raw_dir, raw)
        df, report = parse(content, aliases, raw.retrieved_at)
        df.attrs.update(
            raw_sha256=raw.sha256,
            retrieved_at=raw.retrieved_at.isoformat(),
            raw_file_id=raw.id,
            report=report,
        )
    return df
