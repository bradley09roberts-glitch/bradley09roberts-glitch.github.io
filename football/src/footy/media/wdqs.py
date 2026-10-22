"""Polite, cached queries to the Wikidata Query Service (https://query.wikidata.org/sparql).

- At most one request every `min_interval` seconds (2 s), with the project's identifying
  User-Agent (the one Wikipedia fetching uses, which carries contact details).
- Every response is cached on disk, keyed by the SHA-256 of the query text, so a rebuild
  repeats no request. `offline=True` answers from the cache only.
- HTTP 429 is never retried: the run stops with `WdqsRateLimited` (and its Retry-After).
  Other errors (5xx, timeouts) are retried once after a pause, then raised.

Wikidata's data is CC0; the cache holds the query service's JSON verbatim.
"""

import hashlib
import json
import time
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from urllib.parse import unquote

import httpx

from footy.europe.wiki_fetch import USER_AGENT

WDQS_URL = "https://query.wikidata.org/sparql"
ENTITY_PREFIX = "http://www.wikidata.org/entity/"
FILEPATH_PREFIXES = (
    "http://commons.wikimedia.org/wiki/Special:FilePath/",
    "https://commons.wikimedia.org/wiki/Special:FilePath/",
)
RETRY_STATUSES = {500, 502, 503, 504}


class WdqsError(RuntimeError):
    """The query service answered with an error, or not at all."""


class WdqsRateLimited(WdqsError):
    """HTTP 429: stop, never retry in a loop."""

    def __init__(self, retry_after: float | None):
        self.retry_after = retry_after
        suffix = f" (Retry-After {retry_after:g} s)" if retry_after is not None else ""
        super().__init__(f"Wikidata Query Service returned HTTP 429{suffix}")


class WdqsCacheMiss(WdqsError):
    """Offline mode and the query is not in the cache."""


class WdqsClient:
    def __init__(
        self,
        cache_dir: Path,
        *,
        min_interval: float = 2.0,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
        offline: bool = False,
        retry_pause: float = 30.0,
    ):
        self.cache_dir = cache_dir
        self.min_interval = min_interval
        self.retry_pause = retry_pause
        self.sleep = sleep
        self.offline = offline
        self.requests_made = 0
        self.cache_hits = 0
        self._last = 0.0
        self.client = client or httpx.Client(
            follow_redirects=True, timeout=90.0, headers={"User-Agent": USER_AGENT}
        )

    def cache_path(self, sparql: str) -> Path:
        return self.cache_dir / f"{hashlib.sha256(sparql.encode()).hexdigest()}.json"

    def query(self, sparql: str) -> list[dict[str, str]]:
        """The result rows, each a {variable: value} dict (see `simplify`)."""
        return [simplify(b) for b in self.raw_query(sparql)["results"]["bindings"]]

    def raw_query(self, sparql: str) -> dict:
        path = self.cache_path(sparql)
        if path.exists():
            self.cache_hits += 1
            return json.loads(path.read_text(encoding="utf-8"))["response"]
        if self.offline:
            raise WdqsCacheMiss(f"not cached: {path.name}")
        body = self._fetch(sparql)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        tmp = path.with_suffix(".tmp")
        tmp.write_text(
            json.dumps(
                {
                    "endpoint": WDQS_URL,
                    "retrieved_at": datetime.now(UTC).isoformat(timespec="seconds"),
                    "query": sparql,
                    "response": body,
                },
                ensure_ascii=False,
            ),
            encoding="utf-8",
        )
        tmp.replace(path)
        return body

    def _fetch(self, sparql: str, attempts: int = 2) -> dict:
        last_error = "no attempt"
        for attempt in range(attempts):
            if attempt:
                self.sleep(self.retry_pause)
            wait = self.min_interval - (time.monotonic() - self._last)
            if wait > 0:
                self.sleep(wait)
            self._last = time.monotonic()
            self.requests_made += 1
            try:
                resp = self.client.post(
                    WDQS_URL,
                    data={"query": sparql},
                    headers={"Accept": "application/sparql-results+json"},
                )
            except httpx.TransportError as exc:
                last_error = f"{type(exc).__name__}: {exc}"
                continue
            if resp.status_code == 429:
                raise WdqsRateLimited(retry_after_seconds(resp))
            if resp.status_code == 200:
                try:  # strict=False: a label may hold a raw control character
                    return json.loads(resp.content, strict=False)
                except ValueError as exc:  # a timeout mid-stream leaves truncated JSON
                    raise WdqsError(f"response is not JSON, ends {resp.text[-300:]!r}") from exc
            last_error = f"HTTP {resp.status_code}: {resp.text[:200]!r}"
            if resp.status_code not in RETRY_STATUSES:
                break
        raise WdqsError(last_error)


def retry_after_seconds(resp: httpx.Response) -> float | None:
    value = resp.headers.get("Retry-After")
    if value is None:
        return None
    try:
        return float(value)
    except ValueError:
        return None


def simplify(binding: dict) -> dict[str, str]:
    """One SPARQL JSON binding as {variable: value}: entity URIs become QIDs ("Q9617") and
    Commons file URLs become file names ("Arsenal FC.svg")."""
    out = {}
    for var, cell in binding.items():
        value = cell["value"]
        if cell["type"] == "uri":
            if value.startswith(ENTITY_PREFIX):
                value = value[len(ENTITY_PREFIX) :]
            else:
                for prefix in FILEPATH_PREFIXES:
                    if value.startswith(prefix):
                        value = unquote(value[len(prefix) :])
                        break
        out[var] = value
    return out


def qid_values(qids) -> str:
    """A SPARQL VALUES body: "wd:Q1 wd:Q2"."""
    return " ".join(f"wd:{q}" for q in sorted(set(qids), key=lambda q: int(q[1:])))
