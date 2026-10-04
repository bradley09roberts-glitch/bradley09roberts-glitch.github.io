"""Language fragment: display names for every squidgame block and item.

Written to ``tools/assets/lang/textures.json`` (merged into ``lang/en_us.json`` by the integrator's script).
Block items use the block's translation key (``block.squidgame.<id>``); the standalone items (marble, recruiter
card and the three spawn eggs registered by ModItems) use ``item.squidgame.<id>``.
"""
from __future__ import annotations

from common import BLOCK_IDS, ITEM_IDS, PANEL_LIGHTS, PASTELS, SPAWN_EGGS, SYMBOLS, TILES, Out

NAMES: dict[str, str] = {
    "bridge_glass": "Glass Bridge Panel",
    "cash_block": "Bundle of Cash",
    "monitor": "Control Room Monitor",
    "playground_ground": "Playground Dirt",
    "registration_terminal": "Registration Terminal",
    "dalgona_station": "Dalgona Station",
    "invisible_wall": "Invisible Wall",
}
NAMES.update({f"panel_light_{k}": f"{k.title()} Ceiling Light Panel" for k in PANEL_LIGHTS})
NAMES.update({f"tile_{k}": f"{k.title()} Floor Tile" for k in TILES})
for _c in PASTELS:
    NAMES[f"pastel_{_c}"] = f"Pastel {_c.title()} Block"
    NAMES[f"pastel_{_c}_stairs"] = f"Pastel {_c.title()} Stairs"
    NAMES[f"pastel_{_c}_slab"] = f"Pastel {_c.title()} Slab"
NAMES.update({f"symbol_{k}": f"{k.title()} Symbol Block" for k in SYMBOLS})

ITEM_NAMES = {
    "marble": "Marble",
    "recruiter_card": "Recruiter's Card",
    "contestant_spawn_egg": "Contestant Spawn Egg",
    "guard_spawn_egg": "Masked Guard Spawn Egg",
    "doll_spawn_egg": "Giant Doll Spawn Egg",
}


def lang() -> dict[str, str]:
    missing = [b for b in BLOCK_IDS if b not in NAMES]
    if missing:
        raise RuntimeError(f"gen_lang: no display name for {missing}")
    out: dict[str, str] = {}
    for b in BLOCK_IDS:
        out[f"block.squidgame.{b}"] = NAMES[b]
    for i in ITEM_IDS + SPAWN_EGGS:
        out[f"item.squidgame.{i}"] = ITEM_NAMES[i]
    return out


def generate(out: Out) -> None:
    out.json("textures.json", lang(), root=out.lang_dir)


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
