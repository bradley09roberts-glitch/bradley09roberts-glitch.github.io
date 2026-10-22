"""Static snapshot of the website: the built frontend plus the API's real responses in one file.

The site needs an API server, and there is no always-on host. A snapshot is a read-only copy
that needs none: `npm run build:snapshot` builds the frontend so that it answers every API
request from `snapshot.json` (frontend/src/snapshot.ts), and `uv run footy export-snapshot`
writes that file by asking the real FastAPI app, in-process and on a read-only database
connection, for every request the frontend can make. Re-running both refreshes the copy.

Nothing is computed here: every body is the API's own response, stored as it was sent.
Identical bodies are stored once (most `/api/fixtures?days=N` windows are the same list).

Before anything is written the output is checked: no football-data price may appear (the API
already withholds them, D-013/D-024; this is a second line of defence), no secret from `.env`
or the settings may appear in any file, and the whole site must fit the 16 MB page limit.

The request set mirrors frontend/src/pages (see FRONTEND_* below). Keep them in step: a page
that starts making a new request shows "Not in this snapshot" until it is added here.
"""

from __future__ import annotations

import json
import re
import shutil
import sqlite3
import tempfile
import time
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.parse import quote, unquote

from fastapi import FastAPI
from fastapi.testclient import TestClient
from sqlalchemy import Engine, create_engine, event, select
from sqlalchemy.engine import make_url
from sqlalchemy.orm import Session

from footy.config import PROJECT_ROOT, Settings

FORMAT = "footy-api-snapshot/1"
SNAPSHOT_FILE = "snapshot.json"
DEFAULT_DIST = PROJECT_ROOT / "frontend" / "dist-snapshot"
SITE_SIZE_LIMIT = 16 * 1024 * 1024  # bytes, page plus every supporting file

# ---------------------------------------------------------------------------------------------
# What the frontend requests (frontend/src/pages and src/api.ts)

# Data.tsx: refreshHistory(HISTORY_LIMIT).
FRONTEND_REFRESH_HISTORY_LIMITS = (200,)
# Fixtures.tsx: parseDays() accepts any whole number 1-30 from the address (the buttons give
# 3, 8 and 14; anything else falls back to 8). Data.tsx asks for 14.
FRONTEND_FIXTURE_DAYS = tuple(range(1, 31))
# Past.tsx: two record types (KINDS), LIMIT = 500, with and without a competition.
FRONTEND_PAST_RECORD_TYPES = ("genuine_prematch", "retrospective_backtest")
FRONTEND_PAST_LIMIT = 500
# Performance.tsx: the latest genuine pre-match record.
FRONTEND_PAST_LATEST = {"record_type": "genuine_prematch", "limit": 1}
# Lab.tsx and Data.tsx: report("model_selection"); Lab.tsx: report("benchmark").
FRONTEND_REPORTS = ("model_selection", "benchmark")
# Lab.tsx: the competition choices of a UEFA run whose manifest lists no leagues.
FRONTEND_UEFA_CODES = ("UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ")

# Requests the site can make that a snapshot does not hold (each answers "Not in this
# snapshot"). Reported with every export.
NOT_COVERED = (
    "/api/backtests/{run}/ledger.csv and ledger.json: file downloads; the snapshot build hides "
    "the download links",
    "/api/predictions/{id} for an id typed into the address that is not in the database (the "
    "API would answer 404), or written with leading zeros",
    "/api/past?competition=<code> for a code typed into the address that is neither a "
    "competition with records nor a known competition code (the page's own choices are all "
    "covered)",
)

# ---------------------------------------------------------------------------------------------
# Request keys. Must match canonicalKey() in frontend/src/snapshot.ts.

_URI_COMPONENT_SAFE = "-_.!~*'()"  # what encodeURIComponent leaves alone


def _encode(s: str) -> str:
    return quote(s, safe=_URI_COMPONENT_SAFE)


def canonical_key(path: str, params: dict[str, Any] | None = None) -> str:
    """Path segments percent-encoded as encodeURIComponent does, then the query parameters
    sorted by name (then value), encoded the same way. Empty values are dropped, as the
    frontend's endpoint() drops them."""
    segments = "/".join(_encode(unquote(s)) for s in path.split("/"))
    items = sorted(
        (str(k), str(v).lower() if isinstance(v, bool) else str(v))
        for k, v in (params or {}).items()
        if v is not None and v != ""
    )
    qs = "&".join(f"{_encode(k)}={_encode(v)}" for k, v in items)
    return f"{segments}?{qs}" if qs else segments


# ---------------------------------------------------------------------------------------------
# Read-only database


def read_only_engine(database_url: str) -> Engine:
    """An engine that cannot write: SQLite opened with mode=ro and query_only, or Postgres
    with read-only transactions."""
    url = make_url(database_url)
    backend = url.get_backend_name()
    if backend == "sqlite":
        if not url.database or url.database == ":memory:":
            raise ValueError("a snapshot needs a database file, not an in-memory database")
        path = Path(url.database).resolve()
        if not path.exists():
            raise FileNotFoundError(f"no database at {path}")
        uri = f"file:{quote(str(path))}?mode=ro"

        def connect() -> sqlite3.Connection:
            return sqlite3.connect(uri, uri=True, check_same_thread=False)

        engine = create_engine("sqlite://", creator=connect, future=True)

        @event.listens_for(engine, "connect")
        def _pragmas(dbapi_conn, _record):
            cur = dbapi_conn.cursor()
            cur.execute("PRAGMA query_only=ON")
            cur.execute("PRAGMA busy_timeout=30000")  # jobs may be writing meanwhile
            cur.close()

        return engine
    if backend == "postgresql":
        return create_engine(
            url, future=True, connect_args={"options": "-c default_transaction_read_only=on"}
        )
    raise ValueError(f"read-only snapshot export is not set up for {backend} databases")


# ---------------------------------------------------------------------------------------------
# Export


@dataclass
class Snapshot:
    taken_at_utc: str
    finished_at_utc: str = ""
    bodies: list[Any] = field(default_factory=list)
    entries: dict[str, tuple[int, int]] = field(default_factory=dict)
    _index: dict[str, int] = field(default_factory=dict, repr=False)

    def add(self, key: str, status: int, body: Any) -> None:
        text = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
        i = self._index.get(text)
        if i is None:
            i = self._index[text] = len(self.bodies)
            self.bodies.append(body)
        self.entries[key] = (status, i)

    def body(self, key: str) -> Any:
        status, i = self.entries[key]
        return self.bodies[i] if status == 200 else None

    def to_json(self) -> dict:
        return {
            "format": FORMAT,
            "taken_at_utc": self.taken_at_utc,
            "finished_at_utc": self.finished_at_utc,
            "bodies": self.bodies,
            "entries": {k: list(self.entries[k]) for k in sorted(self.entries)},
        }

    def statuses(self) -> dict[int, int]:
        out: dict[int, int] = {}
        for status, _ in self.entries.values():
            out[status] = out.get(status, 0) + 1
        return dict(sorted(out.items()))


def _iso_now() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds")


def _past_competitions(engine: Engine) -> list[str]:
    """Every competition the Past page could filter by: those with any record, plus every
    competition code the API names."""
    from footy.api.routes import COMPETITION_NAMES
    from footy.models import Prediction

    with Session(engine) as s:
        seen = set(s.scalars(select(Prediction.competition).distinct()))
    return sorted(seen | set(COMPETITION_NAMES))


def _prediction_ids(engine: Engine) -> set[int]:
    from footy.models import Prediction

    with Session(engine) as s:
        return set(s.scalars(select(Prediction.id)))


def _linked_prediction_ids(body: Any) -> Iterator[int]:
    """Prediction ids a response links to: cards, editions and counterparts."""
    if not isinstance(body, dict):
        return
    for comp in body.get("competitions") or []:
        for card in comp.get("fixtures") or []:
            yield card["id"]
    for card in body.get("matches") or []:
        yield card["id"]
    for e in body.get("editions") or []:
        yield e["id"]
    if isinstance(body.get("counterpart"), dict):
        yield body["counterpart"]["id"]


def _summary_requests(runs: list[dict]) -> Iterator[tuple[str, dict]]:
    """Lab.tsx: per run, every league in its list (or the UEFA codes for a UEFA run without a
    list) and every season in its list, each alone, together, or not at all."""
    for run in runs:
        leagues = run.get("leagues")
        if leagues is None:
            leagues = list(FRONTEND_UEFA_CODES) if run.get("kind") == "backtests_euro" else []
        seasons = run.get("seasons") or []
        path = f"/api/backtests/{_encode(str(run['run_id']))}/summary"
        for league in [None, *leagues]:
            for season in [None, *seasons]:
                yield path, {"league": league, "season": season}


def export_snapshot(
    app: FastAPI,
    engine: Engine,
    progress: Callable[[int, str], None] | None = None,
) -> Snapshot:
    """Ask `app` (wired to `engine`) for every request the frontend can make."""
    client = TestClient(app, raise_server_exceptions=False)
    snap = Snapshot(taken_at_utc=_iso_now())
    count = 0

    def get(path: str, params: dict | None = None) -> Any:
        nonlocal count
        key = canonical_key(path, params)
        if key in snap.entries:
            return snap.body(key)
        res = client.get(key, headers={"Accept": "application/json"})
        if "json" in res.headers.get("content-type", ""):
            body = res.json()
        elif res.status_code >= 400:
            # A plain-text error (an unhandled exception is a text/plain 500). The site shows
            # only the status for these, so the status is what is kept.
            body = None
        else:
            raise RuntimeError(f"{key}: the API sent {res.headers.get('content-type')}, not JSON")
        snap.add(key, res.status_code, body)
        count += 1
        if progress:
            progress(count, key)
        return body if res.status_code == 200 else None

    get("/api/health")
    get("/api/coverage")
    for limit in FRONTEND_REFRESH_HISTORY_LIMITS:
        get("/api/refresh-history", {"limit": limit})
    get("/api/import-issues")
    for days in FRONTEND_FIXTURE_DAYS:
        get("/api/fixtures", {"days": days})
    get("/api/performance")
    get("/api/past", FRONTEND_PAST_LATEST)
    competitions = _past_competitions(engine)
    for record_type in FRONTEND_PAST_RECORD_TYPES:
        get("/api/past", {"record_type": record_type, "limit": FRONTEND_PAST_LIMIT})
        for competition in competitions:
            get(
                "/api/past",
                {
                    "record_type": record_type,
                    "competition": competition,
                    "limit": FRONTEND_PAST_LIMIT,
                },
            )
    get("/api/hit-rates")  # Lab.tsx, the Hit rates tab
    for name in FRONTEND_REPORTS:
        get(f"/api/reports/{_encode(name)}")
    runs = (get("/api/backtests") or {}).get("runs", [])
    for path, params in _summary_requests(runs):
        get(path, params)

    # Every prediction in the database, then any a saved response links to that wasn't there
    # yet (one published while the export ran), until every link resolves.
    wanted = _prediction_ids(engine)
    done: set[int] = set()
    while wanted - done:
        for pid in sorted(wanted - done):
            get(f"/api/predictions/{pid}")
            done.add(pid)
        for status, i in snap.entries.values():
            if status == 200:
                wanted.update(_linked_prediction_ids(snap.bodies[i]))
    snap.finished_at_utc = _iso_now()
    return snap


# ---------------------------------------------------------------------------------------------
# Checks: no prices, no secrets, within the size limit

# football-data price columns: bookmaker prefix, C for closing, then the selection, e.g.
# B365H, PSCH, AvgC>2.5, BbAv<2.5, MaxAHH.
PRICE_COLUMN = re.compile(
    r"^(?:B365|BW|IW|PS|WH|VC|LB|SJ|GB|BS|SB|SY|Max|Avg|BbMx|BbAv|Bb1X2|1XB|BF|BFE|BFD|BMGM|BV|CL)"
    r"C?(?:H|D|A|>2\.5|<2\.5|AHH|AHA)$"
)
# A key that names a price or odds, holding a fractional number (a count such as
# fixtures_with_prices is a whole number and is fine).
PRICE_KEY = re.compile(r"(?:^|_)(?:price|prices|odds|decimal_odds)$", re.IGNORECASE)
# A price cell quoted in text, e.g. "B365H=2.10" or "PSCH: '1.95'".
PRICE_CELL_TEXT = re.compile(
    r"\b(?:B365|BW|IW|PS|WH|VC|LB|Max|Avg|BbMx|BbAv|1XB|BFE?)C?(?:[HDA]|[<>]2\.5)\s*[=:]\s*'?\d"
)


def _walk(value: Any, path: str) -> Iterator[tuple[str, str | None, Any]]:
    if isinstance(value, dict):
        for k, v in value.items():
            yield f"{path}.{k}", str(k), v
            yield from _walk(v, f"{path}.{k}")
    elif isinstance(value, list):
        for i, v in enumerate(value):
            yield from _walk(v, f"{path}[{i}]")
    else:
        yield path, None, value


def find_prices(snapshot: dict) -> list[str]:
    """JSON paths in a snapshot that look like bookmakers' prices. Should always be empty."""
    hits: list[str] = []
    for i, body in enumerate(snapshot.get("bodies", [])):
        for path, key, value in _walk(body, f"bodies[{i}]"):
            if key is not None:
                fractional = (
                    isinstance(value, float)
                    and not value.is_integer()
                    or isinstance(value, list)
                    and any(isinstance(x, float) and not x.is_integer() for x in value)
                )
                if PRICE_COLUMN.match(key) or (PRICE_KEY.search(key) and fractional):
                    hits.append(path)
            elif isinstance(value, str) and PRICE_CELL_TEXT.search(value):
                hits.append(path)
    return hits


SECRET_NAME = re.compile(r"TOKEN|KEY|SECRET|PASSWORD|PASSWD|CREDENTIAL|AUTH", re.IGNORECASE)


def env_values(env_file: Path | None = None) -> dict[str, str]:
    """The project's .env entries (name -> value), empty values left out."""
    from dotenv import dotenv_values

    path = env_file or PROJECT_ROOT / ".env"
    if not path.exists():
        return {}
    return {k: v for k, v in dotenv_values(path).items() if v}


def secret_values(settings: Settings, env: dict[str, str]) -> dict[str, str]:
    """Values that must never be published: secret-named .env entries and the provider
    credentials in the settings, wherever they were set."""
    out = {k: v for k, v in env.items() if SECRET_NAME.search(k)}
    for name in ("sportmonks_api_token", "api_football_key"):
        secret = getattr(settings, name, None)
        if secret is not None and secret.get_secret_value():
            out[name.upper()] = secret.get_secret_value()
    url = make_url(settings.database_url)
    if url.password:
        out["FOOTY_DATABASE_URL (password)"] = str(url.password)
    return out


def _forms(value: str) -> set[bytes]:
    """The ways a value could be written in a file: as is, URL-encoded, JSON-escaped."""
    return {
        f.encode("utf-8")
        for f in (value, quote(value, safe=""), json.dumps(value, ensure_ascii=False)[1:-1])
        if f
    }


def find_values(files: Iterable[Path], values: dict[str, str]) -> list[tuple[str, str]]:
    """(file name, value name) for each value found in a file. The values are never
    returned or printed."""
    hits = []
    for f in files:
        data = f.read_bytes()
        for name, value in values.items():
            if any(form in data for form in _forms(value)):
                hits.append((f.name, name))
    return hits


# ---------------------------------------------------------------------------------------------
# The site


_TAG = re.compile(r"<(script|link)\b[^>]*>(?:\s*</script>)?", re.IGNORECASE)
_BODY = re.compile(r"<body[^>]*>(.*)</body>", re.IGNORECASE | re.DOTALL)
# The one outside stylesheet host the artifact host allows (its CSP): web fonts. Each font has
# a system fallback in styles.css, so the page still reads if the fonts are blocked.
FONT_STYLESHEET = "https://fonts.googleapis.com/"


def _is_font_link(tag: str) -> bool:
    ref = re.search(r'href="([^"]+)"', tag)
    return ref is not None and ref.group(1).startswith(FONT_STYLESHEET)


def body_only_html(index_html: str) -> str:
    """The built page reduced to what the artifact host accepts: the stylesheet link, the
    body's content and the module script, without <!doctype>, <html>, <head> or <body> (the
    host wraps the page in its own). The stylesheet loses `crossorigin`, so it loads even if
    the host doesn't answer CORS requests; the module script is left as Vite wrote it."""
    head = index_html.split("<body", 1)[0]
    tags = [m.group(0) for m in _TAG.finditer(head)]
    links = [t for t in tags if t.lower().startswith("<link") and "stylesheet" in t.lower()]
    scripts = [t for t in tags if t.lower().startswith("<script")]
    if not scripts:
        raise ValueError("the built page has no script tag")
    for t in [t for t in links if not _is_font_link(t)] + scripts:
        ref = re.search(r'(?:src|href)="([^"]+)"', t)
        if ref is None or ref.group(1).startswith(("/", "http:", "https:", "//")):
            raise ValueError(f"not a relative reference: {t} (build with npm run build:snapshot)")
    links = [re.sub(r"\s+crossorigin(?:=\"[^\"]*\")?", "", t) for t in links]
    body = _BODY.search(index_html)
    content = body.group(1).strip() if body else '<div id="root"></div>'
    return "\n".join([*links, content, *scripts]) + "\n"


def single_page(site: Path, title: str = "Football Model") -> str:
    """One page with the stylesheet and the module script inlined, for a host whose sandbox
    may block a separately served module script (no CORS headers). Only snapshot.json is
    then fetched, from next to the page."""
    index = (site / "index.html").read_text(encoding="utf-8")
    css_refs = re.findall(r'<link[^>]*href="([^"]+\.css)"', index)
    js_refs = re.findall(r'<script[^>]*src="([^"]+\.js)"', index)
    if len(js_refs) != 1:
        raise ValueError(f"expected one module script in {site / 'index.html'}, got {js_refs}")
    css = "".join((site / r).read_text(encoding="utf-8") for r in css_refs)
    js = (site / js_refs[0]).read_text(encoding="utf-8")
    js = js.replace("<script>", "\\x3cscript>")  # only ever inside string literals
    if "</script" in js.lower() or "<!--" in js or "</style" in css.lower():
        raise ValueError("the bundle can't be inlined safely")
    fonts = "".join(f"{m.group(0)}\n" for m in _TAG.finditer(index) if _is_font_link(m.group(0)))
    body = re.sub(r"<(link|script)\b[^>]*>(?:\s*</script>)?", "", index).strip()
    return (
        f"<title>{title}</title>\n{fonts}<style>{css}</style>\n{body}\n"
        f'<script type="module">{js}</script>\n'
    )


def write_single_page(site: Path, page: Path) -> list[Path]:
    """Write `page` (the inlined HTML) and snapshot.json beside it, from an exported site."""
    page.parent.mkdir(parents=True, exist_ok=True)
    page.write_text(single_page(site), encoding="utf-8")
    shutil.copyfile(site / SNAPSHOT_FILE, page.parent / SNAPSHOT_FILE)
    return [page, page.parent / SNAPSHOT_FILE]


SITE_NAMES = ("index.html", SNAPSHOT_FILE, "assets")  # what an export writes into `out`


def _is_snapshot_dir(out: Path) -> bool:
    try:
        return json.loads((out / SNAPSHOT_FILE).read_text()).get("format") == FORMAT
    except (OSError, ValueError, AttributeError):
        return False


def check_out_dir(out: Path) -> None:
    """`out` must be missing, empty, or an earlier snapshot (whose files are replaced)."""
    if out.exists() and (not out.is_dir() or (any(out.iterdir()) and not _is_snapshot_dir(out))):
        raise FileExistsError(f"{out} is not empty and is not an earlier snapshot")


def write_site(snap: Snapshot, dist: Path, out: Path) -> list[Path]:
    """Write the page, its assets and snapshot.json into the empty directory `out`."""
    index = dist / "index.html"
    if not index.exists():
        raise FileNotFoundError(
            f"no snapshot build at {dist}: run `npm run build:snapshot` in frontend/ first"
        )
    out.mkdir(parents=True, exist_ok=True)
    if any(out.iterdir()):
        raise FileExistsError(f"{out} is not empty")
    (out / "index.html").write_text(body_only_html(index.read_text(encoding="utf-8")))
    if (dist / "assets").is_dir():
        shutil.copytree(dist / "assets", out / "assets")
    (out / SNAPSHOT_FILE).write_text(
        json.dumps(snap.to_json(), ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
    )
    return sorted(p for p in out.rglob("*") if p.is_file())


def _replace_site(staged: Path, out: Path) -> None:
    """Swap the staged files into `out`, replacing an earlier snapshot's files only."""
    check_out_dir(out)
    out.mkdir(parents=True, exist_ok=True)
    for name in SITE_NAMES:
        old = out / name
        if old.is_dir():
            shutil.rmtree(old)
        else:
            old.unlink(missing_ok=True)
        if (staged / name).exists():
            (staged / name).rename(old)
    staged.rmdir()


@dataclass
class ExportResult:
    out: Path
    snapshot: Snapshot
    files: list[tuple[str, int]]
    env_values_shown: list[tuple[str, str]]
    seconds: float

    def report(self) -> dict:
        total = sum(n for _, n in self.files)
        return {
            "out": str(self.out),
            "taken_at_utc": self.snapshot.taken_at_utc,
            "finished_at_utc": self.snapshot.finished_at_utc,
            "seconds": round(self.seconds, 1),
            "entries": len(self.snapshot.entries),
            "distinct_bodies": len(self.snapshot.bodies),
            "statuses": self.snapshot.statuses(),
            "not_ok": sorted(k for k, (s, _) in self.snapshot.entries.items() if s != 200),
            "files": [{"path": p, "bytes": n} for p, n in self.files],
            "total_bytes": total,
            "size_limit_bytes": SITE_SIZE_LIMIT,
            "checks": {
                "price_like_fields": 0,
                "secret_values_found": 0,
                "within_size_limit": total <= SITE_SIZE_LIMIT,
            },
            "non_secret_env_values_shown": [
                {"file": f, "variable": v} for f, v in self.env_values_shown
            ],
            "not_covered": list(NOT_COVERED),
        }


class SnapshotRefused(RuntimeError):
    """The snapshot failed a check. Nothing was written to the output directory."""


def run_export(
    settings: Settings,
    out: Path,
    dist: Path = DEFAULT_DIST,
    app: FastAPI | None = None,
    engine: Engine | None = None,
    env_file: Path | None = None,
    progress: Callable[[int, str], None] | None = None,
) -> ExportResult:
    """Export the snapshot to `out`, check it, and return what was written.

    The site is written to a staging folder beside `out` and checked there; only a site that
    passes replaces `out`'s files. Raises SnapshotRefused if a price-like field or a secret
    is found or the site is over the size limit (an earlier snapshot in `out` is kept)."""
    from footy.api.main import create_app

    started = time.monotonic()
    check_out_dir(out)
    if not (dist / "index.html").exists():
        raise FileNotFoundError(
            f"no snapshot build at {dist}: run `npm run build:snapshot` in frontend/ first"
        )
    engine = engine or read_only_engine(settings.database_url)
    app = app or create_app(settings=settings, engine=engine)
    snap = export_snapshot(app, engine, progress=progress)

    prices = find_prices(snap.to_json())
    if prices:
        raise SnapshotRefused(f"price-like fields in the API's responses: {prices[:10]}")

    out.parent.mkdir(parents=True, exist_ok=True)
    staged = Path(tempfile.mkdtemp(prefix=f".{out.name}-", dir=out.parent))
    try:
        files = write_site(snap, dist, staged)
        env = env_values(env_file)
        secrets = secret_values(settings, env)
        leaked = find_values(files, secrets)
        if leaked:
            raise SnapshotRefused(f"secret values found in the output: {sorted(set(leaked))}")
        total = sum(f.stat().st_size for f in files)
        if total > SITE_SIZE_LIMIT:
            raise SnapshotRefused(
                f"the site is {total:,} bytes, over the {SITE_SIZE_LIMIT:,}-byte limit"
            )
        public = {k: v for k, v in env.items() if k not in secrets}
        shown = find_values(files, public)
        sizes = [(str(f.relative_to(staged)), f.stat().st_size) for f in files]
        _replace_site(staged, out)
    except BaseException:
        shutil.rmtree(staged, ignore_errors=True)
        raise
    return ExportResult(
        out=out,
        snapshot=snap,
        files=sizes,
        env_values_shown=shown,
        seconds=time.monotonic() - started,
    )
