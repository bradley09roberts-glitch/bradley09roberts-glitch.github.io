"""Authored sites. Each module exposes build() -> list[Piece] and registers POIs."""
from __future__ import annotations

import zlib
from dataclasses import dataclass

from ..jsonio import write_bytes, write_json
from ..paths import PM_DATA
from ..structure import Build


@dataclass
class Piece:
    name: str          # template path under data/palemeridian/structure/<site>/<name>.nbt
    build: Build
    origin: tuple      # world coordinates of the template's (0,0,0)


def export_site(site_id: str, pieces: list[Piece], step: str = "top_layer_modification", terrain: str = "none") -> None:
    for p in pieces:
        write_bytes(PM_DATA / "structure" / site_id / f"{p.name}.nbt", p.build.to_nbt())
    anchor = pieces[0].origin
    write_json(PM_DATA / "worldgen" / "structure" / f"{site_id}.json", {
        "type": "palemeridian:site",
        "biomes": "#palemeridian:valley",
        "step": step,
        "spawn_overrides": {},
        "terrain_adaptation": terrain,
        "pieces": [{"template": f"palemeridian:{site_id}/{p.name}", "pos": list(p.origin), "rotation": "none"} for p in pieces],
    })
    write_json(PM_DATA / "worldgen" / "structure_set" / f"{site_id}.json", {
        "structures": [{"structure": f"palemeridian:{site_id}", "weight": 1}],
        "placement": {
            "type": "palemeridian:fixed",
            "salt": zlib.crc32(site_id.encode()) & 0x7FFFFFFF,
            "chunks": [[anchor[0] >> 4, anchor[2] >> 4]],
        },
    })
