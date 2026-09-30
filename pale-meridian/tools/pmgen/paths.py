"""Repository paths used by the generators (no absolute developer paths)."""
from pathlib import Path

TOOLS = Path(__file__).resolve().parent.parent
ROOT = TOOLS.parent
MOD = ROOT / "mod"
RES = MOD / "src" / "main" / "resources"
DATA = RES / "data"
ASSETS = RES / "assets"
PM_DATA = DATA / "palemeridian"
PM_ASSETS = ASSETS / "palemeridian"
CACHE = TOOLS / ".cache"
LAYOUT = TOOLS / "layout.json"
PREVIEWS = ROOT / "docs" / "previews"
