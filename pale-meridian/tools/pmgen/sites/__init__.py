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


def export_site(site_id: str, pieces: list[Piece], step: str = "top_layer_modification", terrain: str = "none",
                anchor: tuple | None = None) -> None:
    """Write the templates and a fixed-position structure. The structure starts in the anchor's chunk;
    vanilla only places pieces within 8 chunks of the start, so large sites pass their centre."""
    for p in pieces:
        write_bytes(PM_DATA / "structure" / site_id / f"{p.name}.nbt", p.build.to_nbt())
    anchor = anchor or pieces[0].origin
    for p in pieces:
        for (x, z) in ((p.origin[0], p.origin[2]), (p.origin[0] + p.build.size_x - 1, p.origin[2] + p.build.size_z - 1)):
            if abs((x >> 4) - (anchor[0] >> 4)) > 8 or abs((z >> 4) - (anchor[2] >> 4)) > 8:
                raise ValueError(f"{site_id}/{p.name} reaches beyond 8 chunks of the structure start")
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
