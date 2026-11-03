"""Player photos and team and league logos for the app: small WebP thumbnails, cached locally.

Photos and logos are cache-only (D-046): nothing is downloaded, from any address. NBA photos
and every team and league logo came from ESPN's image server (its terms forbid automated access)
and NFL photos from NFL.com's image server, static.www.nfl.com, the address the nflverse player
table lists (NFL.com terms sect. 1.3 forbid systematic retrieval). The thumbnails cached before
3 Nov 2026 are still shown; a player or team without one shows initials or the team's
abbreviation. Photo thumbnails are 112 px squares around the head and shoulders, stored under
data/headshots/<sport>/ and logos under data/logos/<sport>/ (never in git), and both are
embedded in the snapshot as data URIs, because the hosted page may not load images from other
sites.
"""

from __future__ import annotations

import base64

from .config import DATA_HOME

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
def logo_urls(sport: str) -> dict[str, str]:
    """Team logo addresses listed in the data (nflverse teams table for the NFL, the
    SportsDataverse box scores for the NBA), used only for the list of teams: both list ESPN
    image addresses, and no logo is fetched from anywhere (fetch_logos is cache-only); the
    league logo has no permitted source at all."""
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
    """Cached team logos (<abbr>.webp and <abbr>.dark.webp for light and dark pages), counted for
    the teams in the data. Cache-only (D-046): nothing is requested from any address, so a team
    without a cached logo shows its abbreviation."""
    d = DIR.parent / "logos" / sport
    files = [f for abbr in logo_urls(sport) for f in (d / f"{abbr}.webp", d / f"{abbr}.dark.webp")]
    cached = sum(f.exists() for f in files)
    return {"cached": cached, "new": 0, "missing": len(files) - cached}


def logo_uris(sport: str, dark: bool = False) -> dict[str, str]:
    d = DIR.parent / "logos" / sport
    if not d.exists():
        return {}
    files = sorted(d.glob("*.dark.webp")) if dark else [f for f in sorted(d.glob("*.webp"))
                                                         if not f.name.endswith(".dark.webp")]
    return {f.name.split(".")[0]: "data:image/webp;base64," + base64.b64encode(f.read_bytes()).decode()
            for f in files}
