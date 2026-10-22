"""Resumable download of crest and venue pictures from Wikimedia Commons, free licences only.

For every team in reference/team_media.json:

- the crest (its P154 file) as a PNG thumbnail about 128 px wide, saved as `crest.png`;
- the venue photo (the venue's P18 file) as a JPEG about 640 px wide, saved as `venue.jpg`.

Each picture costs two requests: one Commons API request (imageinfo with extmetadata, which
gives the licence, author and credit, and a thumbnail URL at the wanted width), then the
thumbnail itself from upload.wikimedia.org. Requests are paced at one per second in all, with
the project's identifying User-Agent.

- Licences: kept only when the file is CC0, public domain, CC BY or CC BY-SA (any version or
  port). Anything else is refused and logged in the team's manifest; nothing is saved.
- HTTP 429 stops the whole run at once; the report says how far it got, and the Retry-After
  is saved so that the next run refuses to start before it has passed. HTTP 403 stops it
  too (asking again won't help). A 502/503/504 with a Retry-After of at most two minutes is
  waited out and tried once more.
- Resumable: a picture whose file is on disk and whose manifest entry names the same Commons
  file is skipped; so is one already refused for its licence.

Files go to data/media/teams/<team_key>/ with a manifest.json holding, per picture, the
source URL, licence, author and the attribution text to display.
"""

import hashlib
import html
import json
import re
import time
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import quote

import httpx

from footy.europe.wiki_fetch import USER_AGENT

COMMONS_API = "https://commons.wikimedia.org/w/api.php"
FILE_PAGE = "https://commons.wikimedia.org/wiki/File:{}"
CREST_WIDTH = 128
VENUE_WIDTH = 640
MAX_BYTES = 5_000_000
MAX_RETRY_WAIT = 120.0  # seconds; a longer Retry-After on a 5xx fails the file instead
RETRY_STATUSES = {502, 503, 504}
EXTENSIONS = {"image/png": ".png", "image/jpeg": ".jpg", "image/gif": ".gif", "image/webp": ".webp"}
STATE_FILE = "_download_state.json"


class Stopped(Exception):
    """An answer that stops the whole run."""

    status = 0

    def __init__(self, url: str, retry_after: float | None = None):
        self.url = url
        self.retry_after = retry_after
        suffix = f", Retry-After {retry_after:g} s" if retry_after is not None else ""
        super().__init__(f"HTTP {self.status} from {url}{suffix}")


class RateLimited(Stopped):
    """HTTP 429: Wikimedia is rate-limiting this machine."""

    status = 429


class Forbidden(Stopped):
    """HTTP 403: refused (by Wikimedia, or by a proxy on the way); asking again won't help."""

    status = 403


class CommonsClient:
    """One request per `min_interval` seconds across every Wikimedia host."""

    def __init__(
        self,
        min_interval: float = 1.0,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
        max_retry_wait: float = MAX_RETRY_WAIT,
    ):
        self.min_interval = min_interval
        self.sleep = sleep
        self.max_retry_wait = max_retry_wait
        self.requests_made = 0
        self._last = 0.0
        self.client = client or httpx.Client(
            follow_redirects=True, timeout=60.0, headers={"User-Agent": USER_AGENT}
        )

    def get(self, url: str, params: dict[str, str] | None = None) -> httpx.Response:
        for attempt in range(2):
            wait = self.min_interval - (time.monotonic() - self._last)
            if wait > 0:
                self.sleep(wait)
            self._last = time.monotonic()
            self.requests_made += 1
            resp = self.client.get(url, params=params)
            if resp.status_code == 429:
                raise RateLimited(str(resp.url), retry_after_seconds(resp))
            if resp.status_code == 403:
                raise Forbidden(str(resp.url))
            retry_after = retry_after_seconds(resp)
            if (
                attempt == 0
                and resp.status_code in RETRY_STATUSES
                and retry_after is not None
                and retry_after <= self.max_retry_wait
            ):
                self.sleep(retry_after)
                continue
            return resp
        return resp


def retry_after_seconds(resp: httpx.Response) -> float | None:
    value = resp.headers.get("Retry-After")
    if value is None:
        return None
    try:
        return max(0.0, float(value))
    except ValueError:
        return None


@dataclass(frozen=True)
class Picture:
    kind: str  # crest | venue
    commons_file: str  # e.g. "Arsenal FC.svg"
    width: int


def pictures_for(facts: dict) -> list[Picture]:
    out = []
    if facts.get("crest_file"):
        out.append(Picture("crest", facts["crest_file"], CREST_WIDTH))
    venue = facts.get("venue") or {}
    if venue.get("image_file"):
        out.append(Picture("venue", venue["image_file"], VENUE_WIDTH))
    return out


def file_page_url(commons_file: str) -> str:
    return FILE_PAGE.format(quote(commons_file.replace(" ", "_")))


def imageinfo_params(commons_file: str, width: int) -> dict[str, str]:
    return {
        "action": "query",
        "titles": f"File:{commons_file}",
        "prop": "imageinfo",
        "iiprop": "url|mime|size|extmetadata",
        "iiurlwidth": str(width),
        "iiextmetadatafilter": (
            "LicenseShortName|License|LicenseUrl|Artist|Credit|AttributionRequired|"
            "UsageTerms|Restrictions|ObjectName"
        ),
        "format": "json",
        "formatversion": "2",
    }


FREE_LICENCE = re.compile(
    r"(cc0( 1\.0)?|cc zero|public domain|pd|pd .*"
    r"|cc by( sa)?( \d(\.\d)?([ ,/]+\d(\.\d)?)*)?( [a-z]{2,3})?)"
)


def licence_is_free(short_name: str | None, code: str | None = None) -> bool:
    """CC0, public domain, CC BY or CC BY-SA, by Commons' licence name or machine code
    ("CC BY-SA 4.0", "cc-by-sa-4.0", "Public domain", "pd"). NC and ND never pass."""
    for value in (code, short_name):
        if not value:
            continue
        text = re.sub(r"[\s_-]+", " ", value.strip().casefold())
        if re.search(r"\b(nc|nd)\b", text):
            return False
        if FREE_LICENCE.fullmatch(text):
            return True
    return False


class _Text(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        self.parts.append(data)


def plain_text(value: str | None, limit: int = 300) -> str | None:
    """Commons' extmetadata values are HTML: the text only, whitespace collapsed."""
    if not value:
        return None
    parser = _Text()
    parser.feed(value)
    text = re.sub(r"\s+", " ", html.unescape("".join(parser.parts))).strip()
    if len(text) > limit:
        text = text[: limit - 1].rstrip() + "…"
    return text or None


def attribution_text(title: str, author: str | None, licence: str) -> str:
    """What the site shows under the picture, e.g. "Emirates Stadium.jpg by Jane Doe,
    CC BY-SA 4.0, via Wikimedia Commons"."""
    by = f" by {author}" if author else ""
    return f"{title}{by}, {licence}, via Wikimedia Commons"


def _meta(info: dict, name: str) -> str | None:
    return (info.get("extmetadata") or {}).get(name, {}).get("value")


@dataclass
class Outcome:
    team_key: str
    kind: str
    status: str  # downloaded | skipped | refused | missing | error
    detail: str = ""


def read_manifest(team_dir: Path) -> dict:
    path = team_dir / "manifest.json"
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {}


def write_manifest(team_dir: Path, manifest: dict) -> None:
    team_dir.mkdir(parents=True, exist_ok=True)
    tmp = team_dir / "manifest.json.tmp"
    tmp.write_text(json.dumps(manifest, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    tmp.replace(team_dir / "manifest.json")


def fetch_picture(
    client: CommonsClient, team_key: str, picture: Picture, team_dir: Path, manifest: dict
) -> Outcome:
    """Download one picture (if its licence is free) and record it in `manifest`."""
    now = datetime.now(UTC).isoformat(timespec="seconds")
    files = manifest.setdefault("files", {})
    refused = manifest.setdefault("refused", [])
    done = files.get(picture.kind)
    if (
        done
        and done.get("commons_file") == picture.commons_file
        and (team_dir / done["file"]).exists()
    ):
        return Outcome(team_key, picture.kind, "skipped", "already downloaded")
    if any(
        r.get("kind") == picture.kind and r.get("commons_file") == picture.commons_file
        for r in refused
    ):
        return Outcome(team_key, picture.kind, "skipped", "refused earlier")

    resp = client.get(COMMONS_API, params=imageinfo_params(picture.commons_file, picture.width))
    if resp.status_code != 200:
        return Outcome(team_key, picture.kind, "error", f"Commons API HTTP {resp.status_code}")
    try:
        pages = resp.json()["query"]["pages"]
    except (ValueError, KeyError):
        return Outcome(team_key, picture.kind, "error", "unexpected Commons API response")
    page = pages[0] if pages else {}
    infos = page.get("imageinfo") or []
    if page.get("missing") or not infos:
        return Outcome(team_key, picture.kind, "missing", f"no such file: {picture.commons_file}")
    info = infos[0]
    short = plain_text(_meta(info, "LicenseShortName"))
    code = plain_text(_meta(info, "License"))
    source_url = info.get("descriptionurl") or file_page_url(picture.commons_file)
    if not licence_is_free(short, code):
        refused.append(
            {
                "kind": picture.kind,
                "commons_file": picture.commons_file,
                "source_url": source_url,
                "licence": short or code,
                "reason": "not CC0, public domain, CC BY or CC BY-SA",
                "checked_at": now,
            }
        )
        write_manifest(team_dir, manifest)
        return Outcome(team_key, picture.kind, "refused", f"licence {short or code!r}")

    image_url = info.get("thumburl") or info.get("url")
    mime = info.get("thumbmime") or info.get("mime")
    if not image_url or mime not in EXTENSIONS:
        return Outcome(team_key, picture.kind, "error", f"no usable thumbnail ({mime})")
    img = client.get(image_url)
    if img.status_code != 200:
        return Outcome(team_key, picture.kind, "error", f"image HTTP {img.status_code}")
    got_mime = img.headers.get("Content-Type", "").split(";")[0].strip()
    if got_mime not in EXTENSIONS:
        return Outcome(team_key, picture.kind, "error", f"not an image: {got_mime!r}")
    if len(img.content) > MAX_BYTES:
        return Outcome(team_key, picture.kind, "error", f"too large ({len(img.content)} bytes)")
    name = picture.kind + EXTENSIONS[got_mime]
    team_dir.mkdir(parents=True, exist_ok=True)
    tmp = team_dir / (name + ".tmp")
    tmp.write_bytes(img.content)
    tmp.replace(team_dir / name)

    author = plain_text(_meta(info, "Artist"))
    licence = short or code or ""
    files[picture.kind] = {
        "file": name,
        "commons_file": picture.commons_file,
        "source_url": source_url,
        "image_url": str(img.url),
        "mime": got_mime,
        "width": info.get("thumbwidth") or info.get("width"),
        "height": info.get("thumbheight") or info.get("height"),
        "sha256": hashlib.sha256(img.content).hexdigest(),
        "licence": licence,
        "licence_url": plain_text(_meta(info, "LicenseUrl")),
        "author": author,
        "credit": plain_text(_meta(info, "Credit")),
        "attribution_required": (_meta(info, "AttributionRequired") or "").lower() == "true",
        "usage_terms": plain_text(_meta(info, "UsageTerms")),
        "restrictions": plain_text(_meta(info, "Restrictions")),
        "attribution": attribution_text(picture.commons_file, author, licence),
        "retrieved_at": now,
    }
    write_manifest(team_dir, manifest)
    return Outcome(team_key, picture.kind, "downloaded", f"{licence}: {picture.commons_file}")


def blocked_until(media_dir: Path) -> datetime | None:
    """When the last run's Retry-After ends, if that is still in the future."""
    path = media_dir / STATE_FILE
    if not path.exists():
        return None
    until = json.loads(path.read_text(encoding="utf-8")).get("retry_after_until")
    if not until:
        return None
    when = datetime.fromisoformat(until)
    return when if when > datetime.now(UTC) else None


def run_download(
    teams: dict[str, dict],
    media_dir: Path,
    client: CommonsClient,
    *,
    only: Iterable[str] | None = None,
    progress: Callable[[Outcome], None] | None = None,
) -> dict:
    """Download every team's pictures (or only those of `only`), in team_key order."""
    until = blocked_until(media_dir)
    if until is not None:
        return {
            "status": "refused",
            "reason": f"the last run hit HTTP 429; its Retry-After lasts until {until.isoformat()}",
        }
    keys = sorted(only) if only else sorted(teams)
    counts = {s: 0 for s in ("downloaded", "skipped", "refused", "missing", "error")}
    outcomes: list[Outcome] = []
    report: dict = {"status": "complete", "teams": len(keys), "teams_finished": 0}
    for i, key in enumerate(keys):
        facts = teams.get(key)
        if facts is None:
            continue
        team_dir = media_dir / key
        manifest = read_manifest(team_dir) or {"team_key": key, "qid": facts.get("qid")}
        try:
            for picture in pictures_for(facts):
                try:
                    out = fetch_picture(client, key, picture, team_dir, manifest)
                except httpx.TransportError as exc:
                    out = Outcome(key, picture.kind, "error", f"{type(exc).__name__}: {exc}")
                outcomes.append(out)
                counts[out.status] += 1
                if progress:
                    progress(out)
        except Stopped as exc:
            report["status"] = f"stopped_http_{exc.status}"
            report["stopped_at"] = {"team_key": key, "team_index": i, "url": exc.url}
            report["retry_after"] = exc.retry_after
            state = {"stopped_at": datetime.now(UTC).isoformat(timespec="seconds"), "url": exc.url}
            if exc.retry_after:
                until = datetime.now(UTC) + timedelta(seconds=exc.retry_after)
                state["retry_after_until"] = until.isoformat(timespec="seconds")
            media_dir.mkdir(parents=True, exist_ok=True)
            (media_dir / STATE_FILE).write_text(json.dumps(state, indent=1) + "\n")
            break
        report["teams_finished"] = i + 1
    report["requests"] = client.requests_made
    report["counts"] = counts
    report["refused"] = [o.__dict__ for o in outcomes if o.status == "refused"]
    report["errors"] = [o.__dict__ for o in outcomes if o.status in ("error", "missing")]
    if report["status"] == "complete":
        (media_dir / STATE_FILE).unlink(missing_ok=True)
    return report


def probe(client: CommonsClient) -> dict:
    """One Commons API request (site information), to see whether Wikimedia answers this
    machine again. `ok` is true only for HTTP 200; a 403 can come from Wikimedia or from a
    proxy between this machine and Wikimedia, and the probe can't tell which."""
    params = {"action": "query", "meta": "siteinfo", "format": "json", "formatversion": "2"}
    try:
        resp = client.get(COMMONS_API, params=params)
    except RateLimited as exc:
        return {"status": 429, "ok": False, "rate_limited": True, "retry_after": exc.retry_after}
    except Forbidden:
        return {"status": 403, "ok": False, "rate_limited": False}
    except httpx.TransportError as exc:
        return {"status": None, "ok": False, "error": f"{type(exc).__name__}: {exc}"}
    return {"status": resp.status_code, "ok": resp.status_code == 200, "rate_limited": False}
