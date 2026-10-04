"""Shared paths for the model generators."""
from __future__ import annotations

import os
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]                      # .../squid-game-mod
ASSETS = REPO / "src" / "main" / "resources" / "assets" / "squidgame"
GEO_DIR = ASSETS / "geo" / "entity"
ANIM_DIR = ASSETS / "animations" / "entity"
TEX_DIR = ASSETS / "textures" / "entity"
PREVIEW_DIR = HERE / "preview"

if str(HERE) not in sys.path:
    sys.path.insert(0, str(HERE))


def ensure_dirs() -> None:
    for d in (GEO_DIR, ANIM_DIR, TEX_DIR, PREVIEW_DIR):
        os.makedirs(d, exist_ok=True)
