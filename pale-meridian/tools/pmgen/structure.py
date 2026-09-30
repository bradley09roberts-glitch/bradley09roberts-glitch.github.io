"""A small voxel-building DSL that emits Minecraft structure templates (NBT).

Every block state is validated against the 26.2 block report (names and property values) and
completed with the block's default property values, so a typo fails the build instead of producing
a broken template in game.
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass, field

from . import nbt
from . import vanilla

DATA_VERSION = 4903  # Minecraft 26.2 (from the jar's version.json)

_STATE_RE = re.compile(r"^([a-z0-9_.:/-]+)(?:\[(.*)\])?$")


@dataclass(frozen=True)
class BlockState:
    name: str
    props: tuple = ()

    def with_(self, **kw) -> "BlockState":
        d = dict(self.props)
        for k, v in kw.items():
            d[k] = str(v).lower() if isinstance(v, bool) else str(v)
        return BlockState(self.name, tuple(sorted(d.items())))

    def prop(self, key: str, default=None):
        return dict(self.props).get(key, default)

    def __str__(self) -> str:
        if not self.props:
            return self.name
        return self.name + "[" + ",".join(f"{k}={v}" for k, v in self.props) + "]"


_cache: dict[str, BlockState] = {}


def parse(spec: str | BlockState) -> BlockState:
    """Parse 'minecraft:oak_stairs[facing=north]' (namespace optional) into a complete, validated state."""
    if isinstance(spec, BlockState):
        return spec
    hit = _cache.get(spec)
    if hit is not None:
        return hit
    m = _STATE_RE.match(spec.strip())
    if not m:
        raise ValueError(f"bad block state: {spec}")
    name, propstr = m.group(1), m.group(2)
    if ":" not in name:
        name = "minecraft:" + name
    report = vanilla.blocks_report()
    if name not in report:
        raise ValueError(f"unknown block {name!r} (from {spec!r})")
    entry = report[name]
    allowed = entry.get("properties", {})
    props = {}
    for st in entry["states"]:
        if st.get("default"):
            props = dict(st.get("properties", {}))
            break
    if propstr:
        for part in propstr.split(","):
            if not part.strip():
                continue
            k, v = part.split("=", 1)
            k, v = k.strip(), v.strip()
            if k not in allowed:
                raise ValueError(f"block {name} has no property {k!r} (from {spec!r}); allowed: {sorted(allowed)}")
            if v not in allowed[k]:
                raise ValueError(f"block {name} property {k}={v!r} not in {allowed[k]} (from {spec!r})")
            props[k] = v
    bs = BlockState(name, tuple(sorted(props.items())))
    _cache[spec] = bs
    return bs


def validate_state(bs: BlockState) -> BlockState:
    return parse(str(bs))


AIR = "minecraft:air"


@dataclass
class Build:
    """A sparse 3D block grid. Only positions that are set are written (unset = structure void)."""

    size_x: int
    size_y: int
    size_z: int
    blocks: dict = field(default_factory=dict)  # (x,y,z) -> (BlockState, nbt-or-None)
    entities: list = field(default_factory=list)  # (x,y,z floats, nbt.Tag compound)

    # -- primitives -------------------------------------------------------------------------
    def inside(self, x, y, z) -> bool:
        return 0 <= x < self.size_x and 0 <= y < self.size_y and 0 <= z < self.size_z

    def set(self, x: int, y: int, z: int, block, nbt_tag=None, overwrite: bool = True) -> None:
        if not self.inside(x, y, z):
            raise IndexError(f"({x},{y},{z}) outside {self.size_x}x{self.size_y}x{self.size_z}")
        if not overwrite and (x, y, z) in self.blocks and self.blocks[(x, y, z)][0].name != AIR:
            return
        bs = parse(block)
        self.blocks[(x, y, z)] = (bs, nbt_tag)

    def get(self, x, y, z):
        v = self.blocks.get((x, y, z))
        return v[0] if v else None

    def is_set(self, x, y, z) -> bool:
        v = self.blocks.get((x, y, z))
        return v is not None and v[0].name != AIR

    def clear(self, x, y, z) -> None:
        self.blocks.pop((x, y, z), None)

    def fill(self, x1, y1, z1, x2, y2, z2, block, nbt_tag=None, overwrite: bool = True) -> None:
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for z in range(min(z1, z2), max(z1, z2) + 1):
                    self.set(x, y, z, block, nbt_tag, overwrite)

    def air(self, x1, y1, z1, x2, y2, z2) -> None:
        """Explicit air: clears terrain/vegetation inside the volume when placed."""
        self.fill(x1, y1, z1, x2, y2, z2, AIR)

    def box(self, x1, y1, z1, x2, y2, z2, block) -> None:
        """Hollow box walls (no floor/ceiling)."""
        for x in range(min(x1, x2), max(x1, x2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                self.set(x, y, z1, block)
                self.set(x, y, z2, block)
        for z in range(min(z1, z2), max(z1, z2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                self.set(x1, y, z, block)
                self.set(x2, y, z, block)

    def entity(self, x: float, y: float, z: float, nbt_compound: nbt.Tag) -> None:
        self.entities.append((x, y, z, nbt_compound))

    def paste(self, other: "Build", ox: int, oy: int, oz: int, skip_air: bool = False) -> None:
        for (x, y, z), (bs, tag) in other.blocks.items():
            if skip_air and bs.name == AIR:
                continue
            if self.inside(ox + x, oy + y, oz + z):
                self.blocks[(ox + x, oy + y, oz + z)] = (bs, tag)
        for (x, y, z, tag) in other.entities:
            self.entities.append((x + ox, y + oy, z + oz, tag))

    # -- serialisation ------------------------------------------------------------------------
    def to_nbt(self) -> bytes:
        palette: list[BlockState] = []
        index: dict[BlockState, int] = {}
        blocks_out = []
        for (x, y, z) in sorted(self.blocks.keys(), key=lambda p: (p[1], p[2], p[0])):
            bs, tag = self.blocks[(x, y, z)]
            if bs not in index:
                index[bs] = len(palette)
                palette.append(bs)
            entry = {"pos": nbt.List(nbt.TAG_INT, [nbt.Int(x), nbt.Int(y), nbt.Int(z)]), "state": nbt.Int(index[bs])}
            if tag is not None:
                entry["nbt"] = tag
            blocks_out.append(nbt.Compound(entry))
        pal_out = []
        for bs in palette:
            c = {"Name": nbt.String(bs.name)}
            if bs.props:
                c["Properties"] = nbt.Compound({k: nbt.String(v) for k, v in bs.props})
            pal_out.append(nbt.Compound(c))
        ents = []
        for (x, y, z, tag) in self.entities:
            ents.append(nbt.Compound({
                "pos": nbt.List(nbt.TAG_DOUBLE, [nbt.Double(x), nbt.Double(y), nbt.Double(z)]),
                "blockPos": nbt.List(nbt.TAG_INT, [nbt.Int(int(x // 1)), nbt.Int(int(y // 1)), nbt.Int(int(z // 1))]),
                "nbt": tag,
            }))
        root = nbt.Compound({
            "DataVersion": nbt.Int(DATA_VERSION),
            "size": nbt.List(nbt.TAG_INT, [nbt.Int(self.size_x), nbt.Int(self.size_y), nbt.Int(self.size_z)]),
            "palette": nbt.List(nbt.TAG_COMPOUND, pal_out),
            "blocks": nbt.List(nbt.TAG_COMPOUND, blocks_out),
            "entities": nbt.List(nbt.TAG_COMPOUND, ents),
        })
        return nbt.write_root(root)

    def stats(self) -> dict:
        solid = sum(1 for bs, _ in self.blocks.values() if bs.name != AIR)
        return {"size": [self.size_x, self.size_y, self.size_z], "blocks": solid, "air": len(self.blocks) - solid,
                "block_entities": sum(1 for _, t in self.blocks.values() if t is not None), "entities": len(self.entities)}


# -- block-entity helpers ------------------------------------------------------------------------

def component_nbt(c) -> nbt.Tag:
    """Encode a text component (str | dict | list) as native NBT, as 26.2 stores components."""
    if isinstance(c, str):
        return nbt.String(c)
    if isinstance(c, list):
        return nbt.List(nbt.TAG_COMPOUND, [component_nbt({"text": x} if isinstance(x, str) else x) for x in c])
    if isinstance(c, dict):
        out = {}
        for k, v in c.items():
            if isinstance(v, bool):
                out[k] = nbt.Byte(1 if v else 0)
            elif isinstance(v, int):
                out[k] = nbt.Int(v)
            elif isinstance(v, float):
                out[k] = nbt.Double(v)
            elif isinstance(v, str):
                out[k] = nbt.String(v)
            elif k in ("extra", "with") and isinstance(v, list):
                out[k] = component_nbt(v)
            elif isinstance(v, dict):
                out[k] = component_nbt(v)
            else:
                raise TypeError(f"unsupported component value {k}={v!r}")
        return nbt.Compound(out)
    raise TypeError(c)


def chest_nbt(loot_table: str | None = None, items: list | None = None, kind: str = "minecraft:chest") -> nbt.Tag:
    d = {"id": nbt.String(kind)}
    if loot_table:
        d["LootTable"] = nbt.String(loot_table)
    if items:
        d["Items"] = nbt.List(nbt.TAG_COMPOUND, [
            nbt.Compound({"Slot": nbt.Byte(slot), "id": nbt.String(item), "count": nbt.Int(count)})
            for slot, item, count in items])
    return nbt.Compound(d)


def sign_nbt(lines: list[str], kind: str = "minecraft:sign", color: str = "black", glowing: bool = False,
             back: list[str] | None = None, waxed: bool = True) -> nbt.Tag:
    def side(ls):
        ls = (ls + ["", "", "", ""])[:4]
        return nbt.Compound({
            "messages": nbt.List(nbt.TAG_STRING, [nbt.String(t) for t in ls]),
            "color": nbt.String(color),
            "has_glowing_text": nbt.Byte(1 if glowing else 0),
        })
    return nbt.Compound({
        "id": nbt.String(kind),
        "front_text": side(lines),
        "back_text": side(back or []),
        "is_waxed": nbt.Byte(1 if waxed else 0),
    })


def lectern_nbt() -> nbt.Tag:
    return nbt.Compound({"id": nbt.String("minecraft:lectern")})


def text_display(text_json: dict, yaw: float = 0.0, scale: float = 1.0, line_width: int = 200,
                 background: int = 0x00000000, tags: list[str] | None = None, glow: bool = False, billboard: str = "fixed") -> nbt.Tag:
    d = {
        "id": nbt.String("minecraft:text_display"),
        "text": component_nbt(text_json),
        "line_width": nbt.Int(line_width),
        "background": nbt.Int(background),
        "billboard": nbt.String(billboard),
        "see_through": nbt.Byte(0),
        "shadow": nbt.Byte(1),
        "Rotation": nbt.List(nbt.TAG_FLOAT, [nbt.Float(yaw), nbt.Float(0.0)]),
        "transformation": nbt.Compound({
            "left_rotation": nbt.List(nbt.TAG_FLOAT, [nbt.Float(0), nbt.Float(0), nbt.Float(0), nbt.Float(1)]),
            "right_rotation": nbt.List(nbt.TAG_FLOAT, [nbt.Float(0), nbt.Float(0), nbt.Float(0), nbt.Float(1)]),
            "translation": nbt.List(nbt.TAG_FLOAT, [nbt.Float(0), nbt.Float(0), nbt.Float(0)]),
            "scale": nbt.List(nbt.TAG_FLOAT, [nbt.Float(scale), nbt.Float(scale), nbt.Float(scale)]),
        }),
    }
    if glow:
        d["brightness"] = nbt.Compound({"sky": nbt.Int(15), "block": nbt.Int(15)})
    if tags:
        d["Tags"] = nbt.List(nbt.TAG_STRING, [nbt.String(t) for t in tags])
    return nbt.Compound(d)


def marker(tags: list[str], data: dict | None = None) -> nbt.Tag:
    d = {"id": nbt.String("minecraft:marker"), "Tags": nbt.List(nbt.TAG_STRING, [nbt.String(t) for t in tags])}
    return nbt.Compound(d)


def item_stack_nbt(item: str, count: int = 1, name=None, lore: list | None = None, custom: dict | None = None,
                   extra_components: dict | None = None, slot: int | None = None) -> nbt.Tag:
    """An item stack as stored in containers (26.2 format: id, count, components)."""
    d = {"id": nbt.String(item if ":" in item else "minecraft:" + item), "count": nbt.Int(count)}
    comps = {}
    if name is not None:
        comps["minecraft:custom_name"] = component_nbt(name)
    if lore:
        comps["minecraft:lore"] = nbt.List(nbt.TAG_COMPOUND, [component_nbt(l if isinstance(l, dict) else {"text": l, "italic": False, "color": "gray"}) for l in lore])
    if custom:
        comps["minecraft:custom_data"] = _plain_to_nbt(custom)
    if extra_components:
        for k, v in extra_components.items():
            comps[k] = v if isinstance(v, nbt.Tag) else _plain_to_nbt(v)
    if comps:
        d["components"] = nbt.Compound(comps)
    if slot is not None:
        d["Slot"] = nbt.Byte(slot)
    return nbt.Compound(d)


def _plain_to_nbt(v):
    if isinstance(v, nbt.Tag):
        return v
    if isinstance(v, bool):
        return nbt.Byte(1 if v else 0)
    if isinstance(v, int):
        return nbt.Int(v)
    if isinstance(v, float):
        return nbt.Double(v)
    if isinstance(v, str):
        return nbt.String(v)
    if isinstance(v, dict):
        return nbt.Compound({k: _plain_to_nbt(x) for k, x in v.items()})
    if isinstance(v, list):
        if not v:
            return nbt.List(nbt.TAG_END, [])
        tags = [_plain_to_nbt(x) for x in v]
        return nbt.List(tags[0].type, tags)
    raise TypeError(v)


def container_nbt(kind: str, stacks: list, loot_table: str | None = None) -> nbt.Tag:
    d = {"id": nbt.String(kind)}
    if loot_table:
        d["LootTable"] = nbt.String(loot_table)
    if stacks:
        d["Items"] = nbt.List(nbt.TAG_COMPOUND, stacks)
    return nbt.Compound(d)
