"""Player photos for the app: small square WebP thumbnails, cached locally.

NBA photos come from ESPN's headshot path for the player's ESPN id (the same ids as the box
scores); NFL photos from the headshot address in the nflverse player table. Thumbnails are cut
to a square around the head and shoulders, stored under data/headshots/<sport>/ (never in git)
and embedded in the snapshot as data URIs, because the hosted page may not load images from
other sites. Players without a photo show their initials instead.
"""

from __future__ import annotations

import base64
import io
import json

from .config import DATA_HOME
from .util import FetchError, http_get, log

DIR = DATA_HOME / "headshots"
SIZE = 112
NBA_URL = "https://a.espncdn.com/combiner/i?img=/i/headshots/nba/players/full/{pid}.png&w=350&h=254"


def _nfl_urls(pids: set[str]) -> dict[str, str]:
    from . import appdata

    pl = appdata.lake()["players"]
    pl = pl[pl.gsis_id.isin(pids) & pl.headshot.notna()]
    # a smaller rendition from the same image service (the full image is about 400 KB)
    return {r.gsis_id: r.headshot.replace("f_auto,q_auto", "f_png,q_auto,w_240")
            for r in pl.itertuples()}


def _thumb(raw: bytes) -> bytes:
    from PIL import Image

    im = Image.open(io.BytesIO(raw)).convert("RGBA")
    w, h = im.size
    side = min(w, h)
    left = (w - side) // 2
    im = im.crop((left, 0, left + side, side))  # heads sit at the top centre of both sources
    im = im.resize((SIZE, SIZE), Image.LANCZOS)
    out = io.BytesIO()
    im.save(out, "WEBP", quality=72, method=6)
    return out.getvalue()


def fetch(sport: str, pids: set[str]) -> dict:
    """Download and cache thumbnails that are not cached yet; misses are remembered so a
    player without a photo is not requested on every build."""
    d = DIR / sport
    d.mkdir(parents=True, exist_ok=True)
    miss_f = d / "_missing.json"
    missing = set(json.loads(miss_f.read_text())) if miss_f.exists() else set()
    urls = (_nfl_urls(pids) if sport == "nfl" else {p: NBA_URL.format(pid=p) for p in pids})
    out = {"cached": 0, "new": 0, "missing": 0}
    for pid in sorted(pids):
        f = d / f"{pid}.webp"
        if f.exists():
            out["cached"] += 1
            continue
        if pid in missing or pid not in urls:
            out["missing"] += 1
            continue
        try:
            r = http_get(urls[pid], timeout=30, max_tries=2)
            f.write_bytes(_thumb(r.content))
            out["new"] += 1
        except (FetchError, OSError, ValueError) as e:
            log.info("no headshot for %s %s: %s", sport, pid, e)
            missing.add(pid)
            out["missing"] += 1
    miss_f.write_text(json.dumps(sorted(missing)))
    return out


def data_uris(sport: str, pids: set[str]) -> dict[str, str]:
    d = DIR / sport
    return {p: "data:image/webp;base64," + base64.b64encode((d / f"{p}.webp").read_bytes()).decode()
            for p in sorted(pids) if (d / f"{p}.webp").exists()}
