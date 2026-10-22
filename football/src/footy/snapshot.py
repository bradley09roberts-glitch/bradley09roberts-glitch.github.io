"""Static snapshot of the website: the built frontend plus the API's real responses in files.

The site needs an API server, and there is no always-on host. A snapshot is a read-only copy
that needs none: `npm run build:snapshot` builds the frontend so that it answers every API
request from the snapshot's JSON files (frontend/src/snapshot.ts), and `uv run footy
export-snapshot` writes them by asking the real FastAPI app, in-process and on a read-only
database connection, for every request the frontend can make. Re-running both refreshes it.

Nothing is computed here: every body is the API's own response, stored as it was sent, with
one exception: a picture's URL (/media/teams/...) is made relative to the page, or null when the
snapshot doesn't carry that file (the generated badge.svg; the site draws its own badge).
Identical bodies are stored once per file, ignoring the second they were made at
(`generated_at_utc`). The fixtures are stored once, for the next 30 days: the page takes every
shorter window from that response (frontend/src/snapshot.ts, `derived`), as the API would.

Files (docs/snapshot.md):

- `snapshot.json`: what every page needs first (the status, performance, past results, reports
  and the list of backtest runs), and the list of shard files with the request keys each one
  answers;
- shard files `snapshot-<family>-<n>.json`, fetched only when a page asks for one of their
  keys: `fixtures` (the fixtures page), `data` (the Data page's coverage, import issues and
  refresh history), `matches` (match pages), `lab` (backtest summaries), `teams` (the teams
  list alone in the first, then each team's profile, match log and media, a team never split
  across files);
- `media/teams/<team_key>/crest.png` and `venue.jpg`: downloaded, freely licensed pictures the
  carried teams' media responses link to (their attribution is in those responses).

Before anything is written the output is checked: no football-data price may appear in any
file (the API already withholds them, D-013/D-024; this is a second line of defence), no
secret from `.env` or the settings may appear in any file, and the files must fit the limits
of the host the snapshot is published on (HOST_LIMITS).

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
from sqlalchemy import Engine, create_engine, event, func, select
from sqlalchemy.engine import make_url
from sqlalchemy.orm import Session

from footy.config import PROJECT_ROOT, Settings

# 2: shard files and pictures beside snapshot.json. 3: shared parts (`parts`, see PART_FIELDS).
FORMAT = "footy-api-snapshot/3"
# An earlier snapshot folder is still replaced; the site still reads one.
EARLIER_FORMATS = ("footy-api-snapshot/1", "footy-api-snapshot/2")
SNAPSHOT_FILE = "snapshot.json"
SHARD_FILE = re.compile(r"snapshot-[a-z]+-\d+\.json")
MEDIA_DIR = "media"
DEFAULT_DIST = PROJECT_ROOT / "frontend" / "dist-snapshot"

# Limits of the host the snapshot is published on (a private Artifact: the page plus its
# supporting files, sent in one publish). Decimal megabytes, which is the stricter reading.
MB = 1_000_000
HOST_LIMITS = {
    "text_file_bytes": 16 * MB,  # the page and each HTML, JS, CSS or JSON file
    "binary_file_bytes": 15 * MB,  # each picture
    "files": 255,  # per publish, the page included
    "total_bytes": 64 * MB,  # per publish
}
TEXT_SUFFIXES = (".html", ".json", ".js", ".css", ".svg", ".txt", ".map")
# A shard is closed once it holds this much. A team page or a match page loads one shard, so
# theirs are kept small for a phone (PAGE_SHARD_FAMILIES): about 8 teams' pages a file (about
# 120 KB a team) or about 90 match pages, as either is often the first page opened from a
# shared link. 12 MB is the most any shard may hold (a single group bigger than the target gets
# a file of its own).
SHARD_TARGET_BYTES = 2 * MB
PAGE_SHARD_TARGET_BYTES = 1 * MB
PAGE_SHARD_FAMILIES = ("matches", "teams")
SHARD_MAX_BYTES = 12 * MB
SHARD_FAMILIES = ("fixtures", "data", "matches", "lab", "teams", "backtest")  # in file order
# Shared parts. Every record of a fixture has its own match page, and each page repeats the
# fixture's form and stats (`teams`), recent results (`recent`) and list of records
# (`editions`): about 7 KB of a 11 KB page, the same on every page of the fixture. With a
# model of record, its fallbacks and some twenty shadow models per fixture, that repetition
# alone would outgrow the host's 64 MB. So in a match page these fields are written once per
# file, in the file's `parts` list, and the page holds {PART_REF: n} in their place (only a
# field of at least PART_MIN_BYTES; identical values share one part). frontend/src/snapshot.ts
# puts them back before a page sees the body, so every body is still the API's own response.
PART_FIELDS = ("teams", "recent", "editions")
PARTED_KEYS = ("/api/predictions/",)
PART_REF = "$part"
PART_MIN_BYTES = 256

# ---------------------------------------------------------------------------------------------
# What the frontend requests (frontend/src/pages and src/api.ts)

# Data.tsx: refreshHistory(HISTORY_LIMIT).
FRONTEND_REFRESH_HISTORY_LIMITS = (200,)
# Fixtures.tsx: parseDays() accepts any whole number 1-30 from the address (the buttons give
# 3, 8 and 14; anything else falls back to 8). Data.tsx asks for 14. Only the 30-day window is
# stored: snapshot.ts answers every other from it (the same cards, up to today + N days).
FRONTEND_FIXTURE_DAYS = tuple(range(1, 31))
SNAPSHOT_FIXTURE_DAYS = 30
# Past.tsx: two record types (KINDS), LIMIT = 500, with and without a competition.
FRONTEND_PAST_RECORD_TYPES = ("genuine_prematch", "retrospective_backtest")
FRONTEND_PAST_LIMIT = 500
# Performance.tsx: the latest genuine pre-match record.
FRONTEND_PAST_LATEST = {"record_type": "genuine_prematch", "limit": 1}
# Lab.tsx and Data.tsx: report("model_selection"); Lab.tsx: report("benchmark"); the machine-
# learning panel: the D-028 reports and their summary.
FRONTEND_REPORTS = ("model_selection", "benchmark", "ml_learning_curve", "ml_selection")
FRONTEND_ML_SUMMARY = "/api/ml/summary"
# Lab.tsx: the competition choices of a UEFA run whose manifest lists no leagues.
FRONTEND_UEFA_CODES = ("UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ")
# Team pages: the teams list, unfiltered (the page filters it itself), and per team its
# profile, its match log (the API's default page: the latest 100 matches, newest first) and
# its media. Paths are formatted with the team key.
FRONTEND_TEAMS_LIST = "/api/teams"
FRONTEND_TEAM_PATHS = ("/api/teams/{key}", "/api/teams/{key}/matches", "/api/teams/{key}/media")
# Which teams have pages: every team on a fixture, past or match card the snapshot holds, every
# team in these leagues this season, and every team in the league phase of the latest UEFA
# season held (2026/27 on 22 Oct 2026).
SNAPSHOT_TEAM_LEAGUES = ("E0", "SP1", "I1", "D1", "F1", "E1")
SNAPSHOT_TEAM_UEFA = ("UCL", "UEL", "UECL")
# Every model's predictions (docs/model-comparison.md). The match page: every model's genuine
# pre-match record for its fixture, kept in the same shard as the fixture's match pages (one
# per fixture with a genuine record). The leaderboards of genuine records, both groups.
FRONTEND_FIXTURE_MODELS = "/api/fixtures/{ref}/models"
FRONTEND_LEADERBOARD = "/api/model-leaderboard"
FRONTEND_LEADERBOARD_GROUPS = ("domestic", "uefa")
# The backtest explorer: the curated models list, then every division and season it covers
# (its groups' `coverage`), each asked for once with every curated model and every match:
# /api/backtest-matches?division=<code>&limit=1000&season=<season>. The page filters by team
# and model itself. One shard per league and split (a league's two test seasons together) and
# one per UEFA split, so a page loads only about what it shows.
FRONTEND_BACKTEST_MODELS = "/api/backtest-models"
FRONTEND_BACKTEST_MATCHES = "/api/backtest-matches"
FRONTEND_BACKTEST_LIMIT = 1000  # more than any division-season holds (380); checked

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
    "/api/teams with filters, a team's match log with filters or beyond its latest 100 "
    "matches, /api/teams/{key}?series=full, and the pages of teams outside the set above",
    "/media/teams/{key}/badge.svg (drawn on request by the API; the site draws its own badge)",
    "/api/backtest-matches with any other query (a team, a model list, an offset or another "
    "limit): the page asks for whole divisions and seasons and filters them itself",
    "/api/fixtures/{ref}/models for a fixture without a genuine pre-match record (the API "
    "would answer 404)",
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
    # Keys answered from shard files: family -> groups of keys that stay in one file (one
    # team's pages, one backtest run's summaries, one match page), each with whether it gets a
    # file of its own. Every other key is in snapshot.json.
    groups: dict[str, list[tuple[list[str], bool]]] = field(default_factory=dict)
    _index: dict[str, int] = field(default_factory=dict, repr=False)
    # Per body: its size as written (shared parts replaced by references) and its shared
    # parts (field -> part id). Per part: its value and size, by its JSON text.
    _sizes: list[int] = field(default_factory=list, repr=False)
    _parts_of: list[dict[str, int]] = field(default_factory=list, repr=False)
    _parted: set[int] = field(default_factory=set, repr=False)
    _part_ids: dict[str, int] = field(default_factory=dict, repr=False)
    _part_values: list[Any] = field(default_factory=list, repr=False)
    _part_sizes: list[int] = field(default_factory=list, repr=False)

    def add(self, key: str, status: int, body: Any) -> None:
        # Two answers that differ only in the second they were made at are the same answer.
        same = body
        if isinstance(body, dict) and "generated_at_utc" in body:
            same = {k: v for k, v in body.items() if k != "generated_at_utc"}
        text = json.dumps(same, ensure_ascii=False, separators=(",", ":"))
        i = self._index.get(text)
        if i is None:
            i = self._index[text] = len(self.bodies)
            self.bodies.append(body)
            self._sizes.append(0)
            self._parts_of.append({})
            if status == 200 and key.startswith(PARTED_KEYS):
                self._parted.add(i)
            self.measure(i)
        self.entries[key] = (status, i)

    def measure(self, i: int) -> None:
        """Work out body `i`'s shared parts and its size as written (again after a change)."""
        body = self.bodies[i]
        fields: dict[str, int] = {}
        if i in self._parted and isinstance(body, dict):
            for name in PART_FIELDS:
                value = body.get(name)
                if not isinstance(value, dict | list):
                    continue
                text = _dumps(value)
                size = len(text.encode("utf-8"))
                if size < PART_MIN_BYTES:
                    continue
                pid = self._part_ids.get(text)
                if pid is None:
                    pid = self._part_ids[text] = len(self._part_values)
                    self._part_values.append(value)
                    self._part_sizes.append(size)
                fields[name] = pid
        self._parts_of[i] = fields
        written = (
            {k: {PART_REF: 0} if k in fields else v for k, v in body.items()} if fields else body
        )
        self._sizes[i] = len(_dumps(written).encode("utf-8"))

    def body(self, key: str) -> Any:
        status, i = self.entries[key]
        return self.bodies[i] if status == 200 else None

    def group(self, family: str, keys: Iterable[str], alone: bool = False) -> None:
        """Answer these keys from a `family` shard file, all from the same one (`alone`: a
        file holding nothing else)."""
        if family not in SHARD_FAMILIES:
            raise ValueError(f"unknown shard family {family!r}")
        self.groups.setdefault(family, []).append((list(dict.fromkeys(keys)), alone))

    def _doc(self, keys: Iterable[str], **extra: Any) -> dict:
        """One file's document: its keys' bodies, each stored once, and the shared parts they
        refer to (`parts`, each stored once; only when some body has one)."""
        bodies: list[Any] = []
        parts: list[Any] = []
        where: dict[int, int] = {}
        part_where: dict[int, int] = {}
        entries = {}
        for k in sorted(keys):
            status, i = self.entries[k]
            if i not in where:
                where[i] = len(bodies)
                body = self.bodies[i]
                if self._parts_of[i]:
                    body = dict(body)
                    for name, pid in self._parts_of[i].items():
                        if pid not in part_where:
                            part_where[pid] = len(parts)
                            parts.append(self._part_values[pid])
                        body[name] = {PART_REF: part_where[pid]}
                bodies.append(body)
            entries[k] = [status, where[i]]
        return {
            "format": FORMAT,
            "taken_at_utc": self.taken_at_utc,
            "finished_at_utc": self.finished_at_utc,
            **extra,
            **({"parts": parts} if parts else {}),
            "bodies": bodies,
            "entries": entries,
        }

    def _bytes(self, bodies: set[int], keys: list[str], parts: set[int] = frozenset()) -> int:
        return (
            sum(self._sizes[i] for i in bodies)
            + sum(self._part_sizes[p] for p in parts)
            + sum(len(k) + 16 for k in keys)
        )

    def shards(self) -> list[tuple[str, list[str]]]:
        """(file name, keys) per shard file: each family's groups in order, a file closed once
        it holds SHARD_TARGET_BYTES, or PAGE_SHARD_TARGET_BYTES for team and match pages (by its
        bodies' size, counted once each)."""
        out: list[tuple[str, list[str]]] = []
        for family in SHARD_FAMILIES:
            target = SHARD_TARGET_BYTES
            if family in PAGE_SHARD_FAMILIES:
                target = min(target, PAGE_SHARD_TARGET_BYTES)
            files: list[list[str]] = []
            size, seen, seen_parts, closed = 0, set(), set(), True
            for keys, alone in self.groups.get(family, []):
                bodies = {self.entries[k][1] for k in keys}
                parts = {p for i in bodies for p in self._parts_of[i].values()}
                extra = self._bytes(bodies - seen, keys, parts - seen_parts)
                if closed or alone or size + extra > target:
                    files.append([])
                    size, seen, seen_parts = 0, set(), set()
                    extra = self._bytes(bodies, keys, parts)
                files[-1].extend(keys)
                size += extra
                seen |= bodies
                seen_parts |= parts
                closed = alone
            out += [(f"snapshot-{family}-{n}.json", f) for n, f in enumerate(files, 1)]
        return out

    def documents(self) -> list[tuple[str, dict]]:
        """(file name, document) for snapshot.json and every shard. snapshot.json lists each
        shard with the keys it answers; its `bytes` are filled in when the files are written."""
        shards = self.shards()
        sharded = {k for _, keys in shards for k in keys}
        main = self._doc(
            (k for k in self.entries if k not in sharded),
            shards=[{"file": name, "bytes": None, "keys": sorted(keys)} for name, keys in shards],
        )
        return [
            (SNAPSHOT_FILE, main),
            *((name, self._doc(keys, shard=name)) for name, keys in shards),
        ]

    def to_json(self) -> dict:
        """Every entry in one document (for checks; the files are `documents()`)."""
        return self._doc(self.entries)

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


def _prediction_fixtures(engine: Engine) -> tuple[dict[int, str], set[str]]:
    """Every prediction's fixture (id -> fixture_ref), and the fixtures with a genuine
    pre-match record."""
    from footy.models import Prediction

    with Session(engine) as s:
        rows = s.execute(
            select(Prediction.id, Prediction.fixture_ref, Prediction.record_type)
        ).all()
    return {i: ref for i, ref, _ in rows}, {ref for _, ref, t in rows if t == "genuine_prematch"}


def _linked_prediction_ids(body: Any) -> Iterator[int]:
    """Prediction ids a response links to: cards, editions and counterparts, and the records on
    a team's fixtures and match log rows."""
    if not isinstance(body, dict):
        return
    linked = [c for comp in body.get("competitions") or [] for c in comp.get("fixtures") or []]
    linked += body.get("matches") or []  # past cards, or a team's match log rows
    linked += body.get("editions") or []
    if isinstance(body.get("counterpart"), dict):
        linked.append(body["counterpart"])
    rows = [*(body.get("matches") or []), *(body.get("upcoming") or [])]
    linked += [r["prediction"] for r in rows if isinstance(r, dict) and r.get("prediction")]
    for x in linked:
        if isinstance(x, dict) and isinstance(x.get("id"), int):
            yield x["id"]
    # A fixture's every-model list: each model's first edition and its later ones.
    if body.get("kind") == "genuine_prematch" and "fixture_ref" in body:
        for m in body.get("models") or []:
            if not isinstance(m, dict):
                continue
            for x in [m, *(m.get("later_editions") or [])]:
                if isinstance(x, dict) and isinstance(x.get("prediction_id"), int):
                    yield x["prediction_id"]


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


def _card_teams(body: Any) -> Iterator[str]:
    """Team keys on the cards of a fixtures, past or match-page response."""
    if not isinstance(body, dict):
        return
    cards = [c for comp in body.get("competitions") or [] for c in comp.get("fixtures") or []]
    cards += body.get("matches") or []
    if "fixture_ref" in body:  # a match page: its own card
        cards.append(body)
    for card in cards:
        if isinstance(card, dict):
            yield from (t for t in (card.get("home_team"), card.get("away_team")) if t)


def snapshot_teams(engine: Engine, snap: Snapshot) -> list[str]:
    """The teams whose pages the snapshot carries (see SNAPSHOT_TEAM_LEAGUES), by key."""
    from footy.models import EuroMatch, Team
    from footy.teams import teams_list

    wanted: set[str] = set()
    for key, (status, i) in snap.entries.items():
        if status == 200 and key.startswith(("/api/fixtures", "/api/past", "/api/predictions/")):
            wanted.update(_card_teams(snap.bodies[i]))
    with Session(engine) as s:
        wanted.update(t["key"] for t in teams_list(s, SNAPSHOT_TEAM_LEAGUES))
        latest = s.scalar(
            select(func.max(EuroMatch.season)).where(EuroMatch.competition.in_(SNAPSHOT_TEAM_UEFA))
        )
        phase = s.execute(
            select(EuroMatch.home_team, EuroMatch.away_team).where(
                EuroMatch.competition.in_(SNAPSHOT_TEAM_UEFA),
                EuroMatch.season == latest,
                EuroMatch.stage.like("League%"),
            )
        ).all()
        wanted.update(t for row in phase for t in row)
        known = set(s.scalars(select(Team.team_key)))
    return sorted(wanted & known)


def backtest_matches(get: Callable[..., Any], snap: Snapshot) -> None:
    """The backtest explorer's requests: the models list, then each division and season its
    groups cover, one shard per league and split and one per UEFA split. Raises RuntimeError
    if a division
    and season holds more matches than FRONTEND_BACKTEST_LIMIT (the page would miss some)."""
    body = get(FRONTEND_BACKTEST_MODELS) or {}
    files: dict[tuple[str, str, str], list[str]] = {}
    for group in body.get("groups") or []:
        for c in group.get("coverage") or []:
            params = {
                "division": c["division"],
                "season": c["season"],
                "limit": FRONTEND_BACKTEST_LIMIT,
            }
            page = get(FRONTEND_BACKTEST_MATCHES, params) or {}
            if page.get("total", 0) > page.get("count", 0):
                raise RuntimeError(
                    f"{c['division']} {c['season']}: {page['total']} backtest matches, more than "
                    f"FRONTEND_BACKTEST_LIMIT ({FRONTEND_BACKTEST_LIMIT})"
                )
            # League games: one file per division and split. UEFA: one per split (its
            # competitions are small).
            split = c.get("split") or c["season"]
            division = c["division"] if group.get("group") == "domestic" else "*"
            where = (group.get("group"), division, split)
            files.setdefault(where, []).append(canonical_key(FRONTEND_BACKTEST_MATCHES, params))
    for keys in files.values():
        snap.group("backtest", keys, alone=True)


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
    # The Data page's own tables, in a file of their own.
    data_keys = [canonical_key("/api/coverage"), canonical_key("/api/import-issues")]
    get("/api/coverage")
    for limit in FRONTEND_REFRESH_HISTORY_LIMITS:
        get("/api/refresh-history", {"limit": limit})
        data_keys.append(canonical_key("/api/refresh-history", {"limit": limit}))
    get("/api/import-issues")
    snap.group("data", data_keys)
    # One fixtures window; the page takes the shorter ones from it.
    get("/api/fixtures", {"days": SNAPSHOT_FIXTURE_DAYS})
    snap.group("fixtures", [canonical_key("/api/fixtures", {"days": SNAPSHOT_FIXTURE_DAYS})])
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
    get(FRONTEND_ML_SUMMARY)
    runs = (get("/api/backtests") or {}).get("runs", [])
    for run in runs:  # one run's summaries stay in one shard
        requests = list(_summary_requests([run]))
        for path, params in requests:
            get(path, params)
        snap.group("lab", (canonical_key(path, params) for path, params in requests))
    # Every model's genuine records: the leaderboards (first screen).
    for group in FRONTEND_LEADERBOARD_GROUPS:
        get(FRONTEND_LEADERBOARD, {"group": group})
    # The backtest explorer: the models list (first screen), then every division and season.
    backtest_matches(get, snap)

    # Every prediction in the database, then any a saved response links to that wasn't there
    # yet (one published while the export ran), until every link resolves. A fixture's match
    # pages and its every-model list stay in one shard.
    refs, genuine = _prediction_fixtures(engine)
    wanted = set(refs)
    done: set[int] = set()
    listed: set[str] = set()

    def match_pages() -> None:
        while wanted - done:
            by_fixture: dict[str, list[int]] = {}
            for pid in sorted(wanted - done):
                if pid not in refs:  # published since the export started
                    page = get(f"/api/predictions/{pid}")
                    ref = page.get("fixture_ref") if isinstance(page, dict) else None
                    refs[pid] = ref or f"#{pid}"
                    if isinstance(page, dict) and page.get("record_type") == "genuine_prematch":
                        genuine.add(refs[pid])
                by_fixture.setdefault(refs[pid], []).append(pid)
            for ref, pids in by_fixture.items():
                keys = []
                for pid in pids:
                    get(f"/api/predictions/{pid}")
                    keys.append(canonical_key(f"/api/predictions/{pid}"))
                    done.add(pid)
                if ref in genuine and ref not in listed:
                    path = FRONTEND_FIXTURE_MODELS.format(ref=_encode(ref))
                    get(path)
                    keys.append(canonical_key(path))
                    listed.add(ref)
                snap.group("matches", keys)
            for status, i in snap.entries.values():
                if status == 200:
                    wanted.update(_linked_prediction_ids(snap.bodies[i]))

    match_pages()
    # Team pages: the list in a shard of its own, then each team's pages together.
    get(FRONTEND_TEAMS_LIST)
    snap.group("teams", [canonical_key(FRONTEND_TEAMS_LIST)], alone=True)
    for team in snapshot_teams(engine, snap):
        paths = [p.format(key=_encode(team)) for p in FRONTEND_TEAM_PATHS]
        for path in paths:
            get(path)
        snap.group("teams", (canonical_key(path) for path in paths))
    # A record published since the match pages were taken that a team page links to.
    for status, i in snap.entries.values():
        if status == 200:
            wanted.update(_linked_prediction_ids(snap.bodies[i]))
    match_pages()
    snap.finished_at_utc = _iso_now()
    return snap


# ---------------------------------------------------------------------------------------------
# Pictures: downloaded crests and venue photos the carried media responses link to

# A picture URL as footy.media.service writes it (the only files /media/teams/ serves).
MEDIA_URL = re.compile(
    r"/media/teams/([a-z0-9][a-z0-9-]{0,63})/((?:crest|venue)\.(?:png|jpg|gif|webp)|badge\.svg)"
)


@dataclass
class Picture:
    path: str  # published path, relative to the page: media/teams/<key>/<file>
    source: Path
    kind: str  # crest | venue
    bytes: int


def pictures(snap: Snapshot, settings: Settings) -> list[Picture]:
    """Every downloaded picture a carried /api/teams/{key}/media response links to, crests
    first (they appear wherever the team does), then venue photos; each file checked to be
    under data/media/teams/, as the API's own picture route does."""
    from footy.media.service import media_dir

    root = media_dir(settings).resolve()
    found: dict[str, list[Picture]] = {"crest": [], "venue": []}
    for key in sorted(snap.entries):
        status, i = snap.entries[key]
        body = snap.bodies[i]
        if status != 200 or not re.fullmatch(r"/api/teams/[^/?]+/media", key):
            continue
        for kind in ("crest", "venue"):
            url = (body.get(kind) or {}).get("url")
            m = MEDIA_URL.fullmatch(url or "")
            if m is None or m.group(2) == "badge.svg":
                continue
            source = (root / m.group(1) / m.group(2)).resolve()
            if root in source.parents and source.is_file():
                rel = f"{MEDIA_DIR}/teams/{m.group(1)}/{m.group(2)}"
                found[kind].append(Picture(rel, source, kind, source.stat().st_size))
    return found["crest"] + found["venue"]


def relocate_media(snap: Snapshot, carried: set[str]) -> int:
    """Point every picture URL in the stored bodies at the copy beside the page
    ("/media/teams/x/crest.png" -> "media/teams/x/crest.png"), or at nothing (None) when the
    snapshot doesn't carry that file. Returns how many URLs were changed."""
    changed = 0

    def fix(value: Any) -> Any:
        nonlocal changed
        if isinstance(value, dict):
            return {k: fix(v) for k, v in value.items()}
        if isinstance(value, list):
            return [fix(v) for v in value]
        if isinstance(value, str) and MEDIA_URL.fullmatch(value):
            changed += 1
            rel = value.lstrip("/")
            return rel if rel in carried else None
        return value

    for i, body in enumerate(snap.bodies):
        before = changed
        snap.bodies[i] = fix(body)
        if changed != before:
            snap.measure(i)  # its size and its shared parts, which may have changed too
    return changed


# ---------------------------------------------------------------------------------------------
# Checks: no prices, no secrets (the host's limits are under "Files and the host's limits")

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
    """JSON paths in a snapshot (its bodies and shared parts) that look like bookmakers'
    prices. Should always be empty."""
    hits: list[str] = []
    values = [
        *((f"bodies[{i}]", b) for i, b in enumerate(snapshot.get("bodies", []))),
        *((f"parts[{i}]", p) for i, p in enumerate(snapshot.get("parts", []))),
    ]
    for where, body in values:
        for path, key, value in _walk(body, where):
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


_NON_ASCII = re.compile(r"[^\x00-\x7f]")


def ascii_js(js: str) -> str:
    """Every non-ASCII character as a \\u escape (UTF-16 units), which means the same in a JS
    string, template literal, regular expression or identifier, and nothing in a comment."""

    def esc(m: re.Match) -> str:
        units = m.group().encode("utf-16-be")
        return "".join(f"\\u{units[i]:02x}{units[i + 1]:02x}" for i in range(0, len(units), 2))

    return _NON_ASCII.sub(esc, js)


def ascii_css(css: str) -> str:
    """Every non-ASCII character as a CSS escape (hex code point and one space, which the
    escape consumes)."""
    return _NON_ASCII.sub(lambda m: f"\\{ord(m.group()):x} ", css)


def ascii_html(text: str) -> str:
    return _NON_ASCII.sub(lambda m: f"&#x{ord(m.group()):x};", text)


def single_page(site: Path, title: str = "Football Model") -> str:
    """One page with the stylesheet and the module script inlined, for a host whose sandbox
    may block a separately served module script (no CORS headers). Only the data files are
    then fetched (snapshot.json, and a shard when a page needs it), from next to the page.

    The page is plain ASCII (non-ASCII characters escaped), so it reads the same whatever
    character set it is served with: served as Latin-1 (as `python -m http.server` does), the
    bundle's "←" and its accent-stripping regular expression would otherwise break the page.
    The data files are JSON, which fetch always reads as UTF-8."""
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
    if re.search(r"\\[^\x00-\x7f]", js + css):  # escaping that character would change it
        raise ValueError("the bundle can't be made ASCII safely (a backslash before a character)")
    fonts = "".join(f"{m.group(0)}\n" for m in _TAG.finditer(index) if _is_font_link(m.group(0)))
    body = re.sub(r"<(link|script)\b[^>]*>(?:\s*</script>)?", "", index).strip()
    return (
        f"<title>{ascii_html(title)}</title>\n{fonts}<style>{ascii_css(css)}</style>\n"
        f"{ascii_html(body)}\n"
        f'<script type="module">{ascii_js(js)}</script>\n'
    )


# ---------------------------------------------------------------------------------------------
# Files and the host's limits


def data_files(site: Path) -> list[Path]:
    """snapshot.json, every shard it lists and every picture under media/, in `site`."""
    main = site / SNAPSHOT_FILE
    shards = json.loads(main.read_text(encoding="utf-8")).get("shards") or []
    files = [main, *(site / sh["file"] for sh in shards)]
    if (site / MEDIA_DIR).is_dir():
        files += sorted(f for f in (site / MEDIA_DIR).rglob("*") if f.is_file())
    return files


def limit_problems(files: list[tuple[str, int]]) -> list[str]:
    """What breaks HOST_LIMITS if these files (published path, bytes) go up together, or
    makes a shard bigger than SHARD_MAX_BYTES."""
    problems = []
    for name, size in files:
        text = Path(name).suffix.lower() in TEXT_SUFFIXES
        limit = HOST_LIMITS["text_file_bytes" if text else "binary_file_bytes"]
        if size > limit:
            kind = "text" if text else "binary"
            problems.append(f"{name} is {size:,} bytes, over the {limit:,}-byte {kind} file limit")
        elif SHARD_FILE.fullmatch(Path(name).name) and size > SHARD_MAX_BYTES:
            problems.append(
                f"{name} is {size:,} bytes, over the {SHARD_MAX_BYTES:,}-byte shard limit"
            )
    if len(files) > HOST_LIMITS["files"]:
        problems.append(f"{len(files)} files, over the limit of {HOST_LIMITS['files']}")
    total = sum(size for _, size in files)
    if total > HOST_LIMITS["total_bytes"]:
        problems.append(f"{total:,} bytes in all, over the {HOST_LIMITS['total_bytes']:,} limit")
    return problems


def _clear_data_files(folder: Path) -> None:
    """Remove an earlier snapshot's shard files and pictures from `folder` (only if it holds
    an earlier snapshot.json), so a smaller snapshot leaves none behind."""
    if not _is_snapshot_dir(folder):
        return
    for f in folder.iterdir():
        if f.is_file() and SHARD_FILE.fullmatch(f.name):
            f.unlink()
    if (folder / MEDIA_DIR / "teams").is_dir():
        shutil.rmtree(folder / MEDIA_DIR / "teams")
        if not any((folder / MEDIA_DIR).iterdir()):
            (folder / MEDIA_DIR).rmdir()


def write_single_page(site: Path, page: Path) -> list[Path]:
    """Write `page` (the inlined HTML) with snapshot.json, every shard and every picture
    beside it, from an exported site. Returns the files written: the page and the supporting
    files to publish with it (their paths relative to the page's folder are the published
    paths). Raises SnapshotRefused, writing nothing, if they would break HOST_LIMITS."""
    html = single_page(site)
    data = data_files(site)
    problems = limit_problems(
        [(page.name, len(html.encode("utf-8")))]
        + [(str(f.relative_to(site)), f.stat().st_size) for f in data]
    )
    if problems:
        raise SnapshotRefused(
            "the page and its files break the host's limits: " + "; ".join(problems)
        )
    page.parent.mkdir(parents=True, exist_ok=True)
    same = page.parent.resolve() == site.resolve()
    if not same:
        _clear_data_files(page.parent)
    page.write_text(html, encoding="utf-8")
    written = [page]
    for f in data:
        target = page.parent / f.relative_to(site)
        if not same:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(f, target)
        written.append(target)
    return written


SITE_NAMES = ("index.html", SNAPSHOT_FILE, "assets", MEDIA_DIR)  # plus the shard files


def _is_snapshot_dir(out: Path) -> bool:
    try:
        fmt = json.loads((out / SNAPSHOT_FILE).read_text()).get("format")
    except (OSError, ValueError, AttributeError):
        return False
    return fmt == FORMAT or fmt in EARLIER_FORMATS


def check_out_dir(out: Path) -> None:
    """`out` must be missing, empty, or an earlier snapshot (whose files are replaced)."""
    if out.exists() and (not out.is_dir() or (any(out.iterdir()) and not _is_snapshot_dir(out))):
        raise FileExistsError(f"{out} is not empty and is not an earlier snapshot")


def _dumps(doc: Any) -> str:
    return json.dumps(doc, ensure_ascii=False, separators=(",", ":"))


def write_site(
    snap: Snapshot, dist: Path, out: Path, carried: Iterable[Picture] = ()
) -> list[Path]:
    """Write the page, its assets, snapshot.json, the shard files and the carried pictures
    into the empty directory `out`."""
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
    (main_name, main), *shards = snap.documents()
    sizes = {}
    for name, doc in shards:
        text = _dumps(doc).encode("utf-8")
        (out / name).write_bytes(text)
        sizes[name] = len(text)
    for sh in main["shards"]:
        sh["bytes"] = sizes[sh["file"]]
    (out / main_name).write_text(_dumps(main), encoding="utf-8")
    for pic in carried:
        target = out / pic.path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(pic.source, target)
    return sorted(p for p in out.rglob("*") if p.is_file())


def _replace_site(staged: Path, out: Path) -> None:
    """Swap the staged files into `out`, replacing an earlier snapshot's files only."""
    check_out_dir(out)
    out.mkdir(parents=True, exist_ok=True)
    old_shards = [f.name for f in out.iterdir() if f.is_file() and SHARD_FILE.fullmatch(f.name)]
    new_shards = [f.name for f in staged.iterdir() if SHARD_FILE.fullmatch(f.name)]
    for name in dict.fromkeys((*SITE_NAMES, *old_shards, *new_shards)):
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
    teams: int = 0
    pictures_carried: list[str] = field(default_factory=list)
    pictures_left_out: list[str] = field(default_factory=list)

    def report(self) -> dict:
        total = sum(n for _, n in self.files)
        sizes = dict(self.files)
        main = json.loads((self.out / SNAPSHOT_FILE).read_text(encoding="utf-8"))
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
            "file_count": len(self.files),
            "total_bytes": total,
            "first_screen_bytes": sizes.get(SNAPSHOT_FILE),
            "shards": [
                {"file": sh["file"], "bytes": sh["bytes"], "keys": len(sh["keys"])}
                for sh in main.get("shards") or []
            ],
            "team_pages": self.teams,
            "pictures": {"carried": self.pictures_carried, "left_out": self.pictures_left_out},
            "limits": HOST_LIMITS,
            "checks": {
                "price_like_fields": 0,
                "secret_values_found": 0,
                "within_limits": True,
            },
            "non_secret_env_values_shown": [
                {"file": f, "variable": v} for f, v in self.env_values_shown
            ],
            "not_covered": list(NOT_COVERED),
        }


class SnapshotRefused(RuntimeError):
    """The snapshot failed a check. Nothing was written to the output directory."""


def _choose_pictures(
    found: list[Picture], files: int, size: int
) -> tuple[list[Picture], list[Picture]]:
    """The pictures that fit beside `files` other files of `size` bytes in one publish, in
    order (crests first); the rest are left out (their URLs become null)."""
    carried, left = [], []
    for pic in found:
        fits = (
            pic.bytes <= HOST_LIMITS["binary_file_bytes"]
            and files + len(carried) + 1 <= HOST_LIMITS["files"]
            and size + pic.bytes <= HOST_LIMITS["total_bytes"]
        )
        if fits:
            carried.append(pic)
            size += pic.bytes
        else:
            left.append(pic)
    return carried, left


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
    is found in any file or the files break HOST_LIMITS (an earlier snapshot in `out` is
    kept)."""
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

    # Pictures: as many as the publish limits leave room for, beside everything else.
    assets = (
        [f for f in (dist / "assets").rglob("*") if f.is_file()]
        if (dist / "assets").is_dir()
        else []
    )
    docs = snap.documents()
    other_files = 1 + len(assets) + len(docs)
    other_bytes = (
        (dist / "index.html").stat().st_size
        + sum(f.stat().st_size for f in assets)
        + sum(len(_dumps(doc).encode("utf-8")) for _, doc in docs)
    )
    carried, left_out = _choose_pictures(pictures(snap, settings), other_files, other_bytes)
    relocate_media(snap, {pic.path for pic in carried})

    out.parent.mkdir(parents=True, exist_ok=True)
    staged = Path(tempfile.mkdtemp(prefix=f".{out.name}-", dir=out.parent))
    try:
        files = write_site(snap, dist, staged, carried)
        for f in files:  # every data file, as written
            if f.suffix == ".json":
                prices = find_prices(json.loads(f.read_text(encoding="utf-8")))
                if prices:
                    raise SnapshotRefused(
                        f"price-like fields in {f.name} (the API's responses): {prices[:10]}"
                    )
        env = env_values(env_file)
        secrets = secret_values(settings, env)
        leaked = find_values(files, secrets)
        if leaked:
            raise SnapshotRefused(f"secret values found in the output: {sorted(set(leaked))}")
        sizes = [(str(f.relative_to(staged)), f.stat().st_size) for f in files]
        problems = limit_problems(sizes)
        if problems:
            raise SnapshotRefused("the site breaks the host's limits: " + "; ".join(problems))
        public = {k: v for k, v in env.items() if k not in secrets}
        shown = find_values(files, public)
        _replace_site(staged, out)
    except BaseException:
        shutil.rmtree(staged, ignore_errors=True)
        raise
    teams = {
        k.split("/")[3]
        for keys, _ in snap.groups.get("teams", [])
        for k in keys
        if k.count("/") >= 3
    }
    return ExportResult(
        out=out,
        snapshot=snap,
        files=sizes,
        env_values_shown=shown,
        seconds=time.monotonic() - started,
        teams=len(teams),
        pictures_carried=[pic.path for pic in carried],
        pictures_left_out=[pic.path for pic in left_out],
    )
