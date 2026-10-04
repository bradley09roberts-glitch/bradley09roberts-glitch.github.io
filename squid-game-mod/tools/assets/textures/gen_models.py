"""Blockstates, block models and item models for every squidgame block (and the spawn-egg item models).

Multi-cube models (monitor, registration terminal, dalgona station) come from ``gen_machines``.
Stairs / slabs use the vanilla parent models with the pastel textures; their blockstates carry the
complete vanilla variant table (4 facings x 5 shapes x 2 halves = 40 variants; the contract's "3 shapes"
are straight / inner / outer, where inner and outer each come in a left and a right flavour).
"""
from __future__ import annotations

from common import BLOCK_IDS, ITEM_IDS, PANEL_LIGHTS, PASTELS, SPAWN_EGGS, SYMBOLS, TILES, Out
import gen_machines

NS = "squidgame"


def blk(name: str) -> str:
    return f"{NS}:block/{name}"


# --------------------------------------------------------------------------- stairs variants
def stairs_variants(base: str, inner: str, outer: str) -> dict:
    """Complete vanilla stairs blockstate (same rotation / uvlock rules as ``oak_stairs.json``)."""
    base_y = {"east": 0, "south": 90, "west": 180, "north": 270}
    models = {"straight": base, "inner": inner, "outer": outer}
    variants = {}
    for facing in ("east", "north", "south", "west"):
        for half in ("bottom", "top"):
            x = 0 if half == "bottom" else 180
            for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
                y = base_y[facing]
                if shape.endswith("_left"):
                    y = (y - 90) % 360 if half == "bottom" else y
                elif shape.endswith("_right"):
                    y = y if half == "bottom" else (y + 90) % 360
                kind = shape.split("_")[0]
                v = {"model": models[kind]}
                if x or y:
                    v["uvlock"] = True
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                variants[f"facing={facing},half={half},shape={shape}"] = v
    return dict(sorted(variants.items()))


def facing_variants(model: str) -> dict:
    out = {}
    for facing, y in (("east", 90), ("north", 0), ("south", 180), ("west", 270)):
        v = {"model": model}
        if y:
            v["y"] = y
        out[f"facing={facing}"] = v
    return out


# --------------------------------------------------------------------------- model builders
def cube_all(tex: str) -> dict:
    return {"parent": "minecraft:block/cube_all", "textures": {"all": blk(tex)}}


def parented(parent: str, **textures) -> dict:
    return {"parent": parent, "textures": {k: blk(v) for k, v in textures.items()}}


def generate(out: Out) -> None:
    models: dict[str, dict] = {}       # models/block/<name>
    states: dict[str, dict] = {}       # blockstates/<id>
    items: dict[str, dict] = {}        # models/item/<id>

    def simple(block_id: str, model: dict, model_name: str | None = None):
        name = model_name or block_id
        models[name] = model
        states[block_id] = {"variants": {"": {"model": blk(name)}}}
        items[block_id] = {"parent": blk(name)}

    simple("bridge_glass", cube_all("bridge_glass"))
    simple("cash_block", parented("minecraft:block/cube_column", end="cash_block_top", side="cash_block_side"))
    for k in PANEL_LIGHTS:
        simple(f"panel_light_{k}", cube_all(f"panel_light_{k}"))
    for k in TILES:
        simple(f"tile_{k}", cube_all(f"tile_{k}"))
    for k in SYMBOLS:
        simple(f"symbol_{k}", parented("minecraft:block/cube_bottom_top", side=f"symbol_{k}", top="symbol_plain",
                                       bottom="symbol_plain"))

    # playground: four random y rotations of one model
    models["playground_ground"] = cube_all("playground_ground")
    states["playground_ground"] = {"variants": {"": [
        {"model": blk("playground_ground"), "y": y} for y in (0, 90, 180, 270)]}}
    items["playground_ground"] = {"parent": blk("playground_ground")}

    # invisible wall: only a particle texture; its item gets a flat icon so it can be found in the inventory
    models["invisible_wall"] = {"textures": {"particle": blk("invisible_wall")}}
    states["invisible_wall"] = {"variants": {"": {"model": blk("invisible_wall")}}}
    items["invisible_wall"] = {"parent": "minecraft:item/generated",
                               "textures": {"layer0": f"{NS}:item/invisible_wall"}}

    # pastel family
    for c in PASTELS:
        tex = f"pastel_{c}"
        simple(tex, cube_all(tex))
        st = f"pastel_{c}_stairs"
        sl = f"pastel_{c}_slab"
        all3 = {"bottom": blk(tex), "side": blk(tex), "top": blk(tex)}
        models[st] = {"parent": "minecraft:block/stairs", "textures": dict(all3)}
        models[st + "_inner"] = {"parent": "minecraft:block/inner_stairs", "textures": dict(all3)}
        models[st + "_outer"] = {"parent": "minecraft:block/outer_stairs", "textures": dict(all3)}
        models[sl] = {"parent": "minecraft:block/slab", "textures": dict(all3)}
        models[sl + "_top"] = {"parent": "minecraft:block/slab_top", "textures": dict(all3)}
        states[st] = {"variants": stairs_variants(blk(st), blk(st + "_inner"), blk(st + "_outer"))}
        states[sl] = {"variants": {
            "type=bottom": {"model": blk(sl)},
            "type=double": {"model": blk(tex)},
            "type=top": {"model": blk(sl + "_top")},
        }}
        items[st] = {"parent": blk(st)}
        items[sl] = {"parent": blk(sl)}

    # multi-cube machines
    for name, spec in gen_machines.models().items():
        models[name] = spec
        states[name] = {"variants": facing_variants(blk(name))}
        items[name] = {"parent": blk(name)}

    # marble / recruiter card flat items
    for name in ITEM_IDS:
        items[name] = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}

    # spawn eggs (not in the contract, registered by ModItems): vanilla template, tinted by the egg colours
    for name in SPAWN_EGGS:
        items[name] = {"parent": "minecraft:item/template_spawn_egg"}

    # sanity: every id in the contract list is covered
    missing = [b for b in BLOCK_IDS if b not in states or b not in items]
    if missing:
        raise RuntimeError(f"gen_models: missing blocks {missing}")

    for name, m in sorted(models.items()):
        out.json(f"models/block/{name}.json", m)
    for name, s in sorted(states.items()):
        out.json(f"blockstates/{name}.json", s)
    for name, m in sorted(items.items()):
        out.json(f"models/item/{name}.json", m)


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
