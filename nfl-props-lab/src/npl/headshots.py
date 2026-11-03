"""Player photos and team and league logos for the app: small WebP thumbnails, cached locally.

Photos are cache-only (D-046): nothing is downloaded. NBA photos and every team and league logo
came from ESPN's image server (its terms forbid automated access) and NFL photos from NFL.com's
image server, static.www.nfl.com, the address the nflverse player table lists (NFL.com terms
sect. 1.3 forbid systematic retrieval). The thumbnails cached before 3 Nov 2026 are still shown;
a player or team without one shows initials or the team's abbreviation. Thumbnails are 112 px
squares around the head and shoulders, stored under data/headshots/<sport>/ and
data/logos/<sport>/ (never in git) and embedded in the snapshot as data URIs, because the hosted
page may not load images from other sites.
"""

from __future__ import annotations

import base64
import io

from .config import DATA_HOME
from .util import FetchError, blocked_host, http_get, log

DIR = DATA_HOME / "headshots"


def fetch(sport: str, pids: set[str]) -> dict:
    """Cached thumbnails for these players. Cache-only for both sports (D-046): nothing is
    requested, so a player without a cached thumbnail is counted as missing and shows his
    initials."""
    d = DIR / sport
    cached = {p for p in pids if (d / f"{p}.webp").exists()}
    return {"cached": len(cached), "new": 0, "missing": len(set(pids) - cached)}


def data_uris(sport: str, pids: set[str]) -> dict[str, str]:
    d = DIR / sport
    return {p: "data:image/webp;base64," + base64.b64encode((d / f"{p}.webp").read_bytes()).decode()
            for p in sorted(pids) if (d / f"{p}.webp").exists()}


# ------------------------------------------------------------------ team and league logos
LOGO_SIZE = 96


def _logo_thumb(raw: bytes) -> bytes:
    from PIL import Image

    im = Image.open(io.BytesIO(raw)).convert("RGBA")
    im.thumbnail((LOGO_SIZE, LOGO_SIZE), Image.LANCZOS)
    out = io.BytesIO()
    im.save(out, "WEBP", quality=90, method=6)
    return out.getvalue()


def logo_urls(sport: str) -> dict[str, str]:
    """Team logo addresses listed in the data (nflverse teams table for the NFL, the
    SportsDataverse box scores for the NBA). Both list ESPN image addresses, which are never
    fetched (fetch_logos skips them); the league logo has no permitted source at all."""
    if sport == "nfl":
        from . import appdata

        L = appdata.lake()
        cur = set(L["games"].query("season == season.max()")[["home_team", "away_team"]].values.ravel())
        t = L["teams"]
        urls = {r.team_abbr: r.team_logo_espn for r in t.itertuples()
                if r.team_abbr in cur and isinstance(r.team_logo_espn, str)}
    else:
        from . import db
        from .nba import live

        b, _ = live._latest(db.connect(), "nba_player_box")
        b = b.drop_duplicates("team_abbreviation")
        urls = {r.team_abbreviation: r.team_logo for r in b.itertuples() if isinstance(r.team_logo, str)}
    return urls


def fetch_logos(sport: str) -> dict:
    """Each logo in two versions with transparent backgrounds, for light and dark pages
    (<abbr>.webp and <abbr>.dark.webp). Cached copies are kept and used; an address on an ESPN
    host is never requested (D-046), so a team without a cached logo shows its abbreviation."""
    d = DIR.parent / "logos" / sport
    d.mkdir(parents=True, exist_ok=True)
    out = {"cached": 0, "new": 0, "failed": 0, "not_fetched": 0}
    for abbr, url in logo_urls(sport).items():
        for f, u in ((d / f"{abbr}.webp", url), (d / f"{abbr}.dark.webp", url.replace("/500/", "/500-dark/"))):
            if f.exists():
                out["cached"] += 1
                continue
            if blocked_host(u):
                out["not_fetched"] += 1
                continue
            try:
                f.write_bytes(_logo_thumb(http_get(u, timeout=30, max_tries=2).content))
                out["new"] += 1
            except (FetchError, OSError, ValueError) as e:
                log.info("no logo for %s %s (%s): %s", sport, abbr, u, e)
                out["failed"] += 1
    return out


def logo_uris(sport: str, dark: bool = False) -> dict[str, str]:
    d = DIR.parent / "logos" / sport
    if not d.exists():
        return {}
    files = sorted(d.glob("*.dark.webp")) if dark else [f for f in sorted(d.glob("*.webp"))
                                                         if not f.name.endswith(".dark.webp")]
    return {f.name.split(".")[0]: "data:image/webp;base64," + base64.b64encode(f.read_bytes()).decode()
            for f in files}
