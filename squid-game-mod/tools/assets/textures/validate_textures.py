#!/usr/bin/env python3
"""Validate the generated squidgame block / item / GUI / entity assets against ASSET_CONTRACT.md section 2.

    python3 tools/assets/textures/validate_textures.py            # validate the committed assets
    python3 tools/assets/textures/validate_textures.py --no-fresh # skip the regenerate-and-compare step

Exit code 0 = all good (warnings allowed), 1 = at least one error.

What is checked
  contract   every block id / HUD / dalgona / marbles / entity file named in the contract exists
  blockstates one per block id; JSON parses; all models exist; stairs = 40 variants, slabs = 3 types,
              facing blocks = 4 facings with the right y rotation, playground = 4 y rotations
  models     parents resolve, every texture variable resolves to an existing PNG, uv / rotation / range sanity,
             particle texture present, no coplanar same-facing overlapping faces (z-fighting), contract shapes
             (terminal ~12x16 with ring light / sloped screen / card slot, dalgona table 8px high, monitor <= 6px deep)
  textures   exact sizes, mode/alpha rules (opaque cubes, translucent bridge glass, tintable HUD icons, nine-slice
             uniformity, crack nesting, needle tip, tracksuit layout cross-checked against the vanilla Steve / Alex
             skins), seam tiling, pastel harmony (LCH), symbol symmetry, tile grout / gloss
  animation  monitor_screen strip + .mcmeta consistency
  lang       every block / item key present
  orientation  the real blockstate variants are baked: monitors / terminals face the right way for all 4 facings
  freshness  regenerating into a temp dir reproduces the committed files (PNG pixels / JSON bytes) and is deterministic
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
from common import BLOCK_IDS, ITEM_IDS, PANEL_LIGHTS, PASTELS, SPAWN_EGGS, SYMBOLS, TILES, rgb_to_lch  # noqa: E402

NS = "squidgame"
FACINGS_Y = {"north": 0, "east": 90, "south": 180, "west": 270}
DIRS = {"down", "up", "north", "south", "west", "east"}

HUD_ICONS = ["icon_survivors", "icon_timer", "icon_stamina", "icon_marble", "icon_lick", "icon_skull",
             "icon_warning", "icon_eye_red", "icon_eye_green", "icon_heart", "icon_rope", "icon_glass",
             "icon_circle", "icon_triangle", "icon_square"]
EXPECTED_GUI = {
    **{f"hud/{n}.png": (16, 16) for n in HUD_ICONS},
    "hud/number_plate.png": (32, 16),
    "hud/vignette_red.png": (256, 256),
    "hud/panel.png": (48, 48),
    "dalgona/cookie.png": (128, 128),
    "dalgona/table.png": (64, 64),
    "dalgona/needle.png": (16, 32),
    **{f"dalgona/crack_{i}.png": (64, 64) for i in range(4)},
    "marbles/hand_closed.png": (48, 48),
    "marbles/hand_open.png": (48, 48),
    "marbles/marble_big.png": (16, 16),
    "marbles/ring_target.png": (128, 128),
}
EXPECTED_ENTITY = {"rope.png": (16, 16), "rope_flag.png": (16, 16),
                   "player_tracksuit.png": (64, 64), "player_tracksuit_slim.png": (64, 64)}

# vanilla parents we rely on, with the texture keys each of them needs
PARENT_KEYS = {
    "minecraft:block/cube_all": {"all"},
    "minecraft:block/cube_column": {"end", "side"},
    "minecraft:block/cube_bottom_top": {"top", "bottom", "side"},
    "minecraft:block/stairs": {"top", "bottom", "side"},
    "minecraft:block/inner_stairs": {"top", "bottom", "side"},
    "minecraft:block/outer_stairs": {"top", "bottom", "side"},
    "minecraft:block/slab": {"top", "bottom", "side"},
    "minecraft:block/slab_top": {"top", "bottom", "side"},
    "minecraft:block/block": set(),
    "minecraft:item/generated": {"layer0"},
}


class Report:
    def __init__(self):
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.checks = 0

    def ok(self, cond: bool, msg: str) -> bool:
        self.checks += 1
        if not cond:
            self.errors.append(msg)
        return cond

    def warn(self, cond: bool, msg: str) -> bool:
        self.checks += 1
        if not cond:
            self.warnings.append(msg)
        return cond


# --------------------------------------------------------------------------- helpers
def load_json(path: Path, rep: Report):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        rep.ok(False, f"{path}: cannot parse JSON ({e})")
        return None


def split_ref(ref: str) -> tuple[str, str]:
    return tuple(ref.split(":", 1)) if ":" in ref else ("minecraft", ref)  # type: ignore[return-value]


class Assets:
    def __init__(self, root: Path, rep: Report):
        self.root = root
        self.rep = rep
        self._img: dict[Path, np.ndarray | None] = {}

    def png(self, rel: str) -> np.ndarray | None:
        p = self.root / "textures" / rel
        if p in self._img:
            return self._img[p]
        arr = None
        if p.exists():
            try:
                im = Image.open(p)
                im.load()
                arr = np.array(im.convert("RGBA"))
                self.rep.warn(im.mode in ("RGBA", "RGB"), f"{rel}: PNG mode {im.mode} (expected RGBA)")
            except Exception as e:  # noqa: BLE001
                self.rep.ok(False, f"{rel}: unreadable PNG ({e})")
        self._img[p] = arr
        return arr

    def texture_exists(self, ref: str) -> bool:
        ns, path = split_ref(ref)
        if ns == NS:
            return (self.root / "textures" / f"{path}.png").exists()
        return True  # vanilla textures are not our business

    def model_path(self, ref: str) -> Path | None:
        ns, path = split_ref(ref)
        if ns == NS:
            return self.root / "models" / f"{path}.json"
        van = common.vanilla_root()
        if van is not None:
            return van / "models" / f"{path}.json"
        return None


# --------------------------------------------------------------------------- contract parsing
def contract_ids(rep: Report) -> tuple[set[str], set[str]]:
    """Block ids / item ids named in section 2.1 of the contract (None-safe if the file is missing)."""
    if not common.CONTRACT.exists():
        rep.warn(False, "ASSET_CONTRACT.md not found; skipping contract cross-check")
        return set(), set()
    text = common.CONTRACT.read_text(encoding="utf-8")
    sec = text.split("### 2.1", 1)[1].split("### 2.2", 1)[0]
    colours: list[str] = []
    ids: set[str] = set()
    for line in sec.splitlines():
        if not line.startswith("|") or line.startswith("|--") or "Block id" in line:
            continue
        first = line.split("|")[1]
        toks = re.findall(r"`([^`]+)`", first)
        if any(t.startswith("pastel_") and "<" not in t and not t.endswith(("_stairs", "_slab")) for t in toks):
            colours = [t[len("pastel_"):] for t in toks]
        for t in toks:
            if "<c>" in t:
                ids.update(t.replace("<c>", c) for c in colours)
            else:
                ids.add(t)
    items = set(re.findall(r"`([a-z_]+)`", sec.split("Items:", 1)[1].split("\n\n", 1)[0])) if "Items:" in sec else set()
    items = {i for i in items if i in ("marble", "recruiter_card")}
    return ids, items


def contract_gui_files(rep: Report) -> set[str]:
    if not common.CONTRACT.exists():
        return set()
    text = common.CONTRACT.read_text(encoding="utf-8")
    sec = text.split("### 2.3", 1)[1].split("### 2.4", 1)[0]
    return set(re.findall(r"`([a-z_0-9]+\.png)`", sec))


# --------------------------------------------------------------------------- model checks
def collect_model(a: Assets, ref: str, rep: Report, depth: int = 0):
    """Return (textures dict, elements list or None, parents chain) for a squidgame / vanilla model ref."""
    import mcrender
    ns, path = split_ref(ref)
    if depth > 8:
        rep.ok(False, f"model {ref}: parent chain too deep")
        return {}, None, []
    p = a.model_path(ref)
    if ns == NS:
        if not rep.ok(p is not None and p.exists(), f"model {ref}: file {p} missing"):
            return {}, None, []
        data = load_json(p, rep)
    elif p is not None and p.exists():
        data = load_json(p, rep)
    elif path in mcrender.BUILTIN_MODELS:
        data = mcrender.BUILTIN_MODELS[path]
    elif path.startswith("item/") or ref in PARENT_KEYS:
        return {}, None, [ref]                 # item/generated, template_spawn_egg: nothing to resolve
    else:
        rep.ok(False, f"model {ref}: vanilla parent not found")
        return {}, None, []
    if data is None:
        return {}, None, []
    parent = data.get("parent")
    texs, elems, chain = {}, None, []
    if parent and not parent.startswith("builtin/"):
        texs, elems, chain = collect_model(a, parent, rep, depth + 1)
    texs = {**texs, **data.get("textures", {})}
    if "elements" in data:
        elems = data["elements"]
    return texs, elems, [ref] + chain


def resolve_var(texs: dict, name: str) -> str | None:
    n = 0
    while isinstance(name, str) and name.startswith("#"):
        name = texs.get(name[1:])
        n += 1
        if n > 16 or name is None:
            return None
    return name


def check_elements(ref: str, texs: dict, elems: list, a: Assets, rep: Report) -> None:
    for idx, el in enumerate(elems):
        tag = f"{ref} element {idx} ({el.get('name', '?')})"
        f, t = el.get("from"), el.get("to")
        if not rep.ok(isinstance(f, list) and isinstance(t, list) and len(f) == 3 and len(t) == 3, f"{tag}: bad from/to"):
            continue
        rep.ok(all(-16 <= v <= 32 for v in f + t), f"{tag}: coordinates out of [-16,32]")
        rep.ok(all(f[i] <= t[i] for i in range(3)), f"{tag}: from > to")
        rot = el.get("rotation")
        if rot:
            rep.ok(rot.get("axis") in ("x", "y", "z"), f"{tag}: bad rotation axis")
            rep.ok(rot.get("angle") in (-45, -22.5, 0, 22.5, 45), f"{tag}: rotation angle {rot.get('angle')} not in -45..45 step 22.5")
            rep.ok(isinstance(rot.get("origin"), list) and len(rot["origin"]) == 3, f"{tag}: bad rotation origin")
        faces = el.get("faces", {})
        rep.ok(len(faces) > 0, f"{tag}: no faces")
        for d, fd in faces.items():
            rep.ok(d in DIRS, f"{tag}: unknown face {d}")
            tex = fd.get("texture", "")
            rep.ok(tex.startswith("#"), f"{tag}.{d}: texture must be a #variable")
            target = resolve_var(texs, tex)
            if rep.ok(target is not None, f"{tag}.{d}: texture variable {tex} unresolved"):
                rep.ok(a.texture_exists(target), f"{tag}.{d}: texture {target} missing")
            uv = fd.get("uv")
            if uv is not None:
                rep.ok(len(uv) == 4 and all(0 <= u <= 16 for u in uv), f"{tag}.{d}: uv {uv} outside 0..16")
            rep.ok(fd.get("rotation", 0) in (0, 90, 180, 270), f"{tag}.{d}: bad face rotation")
            cf = fd.get("cullface")
            rep.ok(cf is None or cf in DIRS, f"{tag}.{d}: bad cullface {cf}")


def zfight_check(ref: str, elems: list, rep: Report) -> None:
    """Axis-aligned faces of one direction that lie in the same plane and overlap in area z-fight."""
    planes: dict[tuple[str, float], list[tuple[int, tuple[float, float, float, float]]]] = {}
    for idx, el in enumerate(elems):
        if el.get("rotation"):
            continue
        f, t = el["from"], el["to"]
        for d in el.get("faces", {}):
            if d == "up":
                key, rect = (d, round(t[1], 4)), (f[0], f[2], t[0], t[2])
            elif d == "down":
                key, rect = (d, round(f[1], 4)), (f[0], f[2], t[0], t[2])
            elif d == "north":
                key, rect = (d, round(f[2], 4)), (f[0], f[1], t[0], t[1])
            elif d == "south":
                key, rect = (d, round(t[2], 4)), (f[0], f[1], t[0], t[1])
            elif d == "west":
                key, rect = (d, round(f[0], 4)), (f[2], f[1], t[2], t[1])
            else:
                key, rect = (d, round(t[0], 4)), (f[2], f[1], t[2], t[1])
            planes.setdefault(key, []).append((idx, rect))
    for key, lst in planes.items():
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                (ia, ra), (ib, rb) = lst[i], lst[j]
                ox = min(ra[2], rb[2]) - max(ra[0], rb[0])
                oy = min(ra[3], rb[3]) - max(ra[1], rb[1])
                rep.ok(not (ox > 1e-4 and oy > 1e-4),
                       f"{ref}: elements {ia} and {ib} have overlapping coplanar '{key[0]}' faces at {key[1]} (z-fighting)")


def model_bounds(elems: list) -> tuple[np.ndarray, np.ndarray]:
    pts = []
    for el in elems:
        f, t = np.array(el["from"], float), np.array(el["to"], float)
        corners = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
        rot = el.get("rotation")
        if rot:
            a = math.radians(rot["angle"])
            c, s = math.cos(a), math.sin(a)
            o = np.array(rot["origin"], float)
            q = corners - o
            if rot["axis"] == "x":
                q = np.stack([q[:, 0], q[:, 1] * c - q[:, 2] * s, q[:, 1] * s + q[:, 2] * c], 1)
            elif rot["axis"] == "y":
                q = np.stack([q[:, 0] * c + q[:, 2] * s, q[:, 1], -q[:, 0] * s + q[:, 2] * c], 1)
            else:
                q = np.stack([q[:, 0] * c - q[:, 1] * s, q[:, 0] * s + q[:, 1] * c, q[:, 2]], 1)
            corners = q + o
        pts.append(corners)
    P = np.concatenate(pts)
    return P.min(0), P.max(0)


def check_blockstates_and_models(a: Assets, rep: Report) -> dict[str, set[str]]:
    """Returns {model_ref: set(texture refs)} used, for orphan detection."""
    used_tex: set[str] = set()
    seen_models: set[str] = set()
    root = a.root
    for bid in BLOCK_IDS:
        sp = root / "blockstates" / f"{bid}.json"
        if not rep.ok(sp.exists(), f"blockstate {bid}.json missing"):
            continue
        bs = load_json(sp, rep)
        if bs is None:
            continue
        variants = bs.get("variants")
        rep.ok(isinstance(variants, dict) and len(variants) > 0, f"{bid}: blockstate needs 'variants'")
        if not isinstance(variants, dict):
            continue
        entries = []
        for key, v in variants.items():
            for e in (v if isinstance(v, list) else [v]):
                entries.append((key, e))
        for key, e in entries:
            m = e.get("model")
            if rep.ok(bool(m), f"{bid}[{key}]: missing model"):
                seen_models.add(m)
            rep.ok(e.get("x", 0) in (0, 90, 180, 270) and e.get("y", 0) in (0, 90, 180, 270), f"{bid}[{key}]: bad x/y rotation")
        # shape-specific
        if bid.endswith("_stairs"):
            want = {f"facing={f},half={h},shape={s}" for f in FACINGS_Y for h in ("bottom", "top")
                    for s in ("straight", "inner_left", "inner_right", "outer_left", "outer_right")}
            rep.ok(set(variants) == want, f"{bid}: stairs variants incomplete ({len(variants)} of {len(want)})")
        elif bid.endswith("_slab"):
            rep.ok(set(variants) == {"type=bottom", "type=top", "type=double"}, f"{bid}: slab needs bottom/top/double")
        elif bid in ("monitor", "registration_terminal", "dalgona_station"):
            rep.ok(set(variants) == {f"facing={f}" for f in FACINGS_Y}, f"{bid}: needs the 4 facing variants")
            for f, y in FACINGS_Y.items():
                v = variants.get(f"facing={f}")
                if isinstance(v, dict):
                    rep.ok(v.get("y", 0) == y, f"{bid}: facing={f} should rotate y={y}")
        elif bid == "playground_ground":
            lst = variants.get("")
            if rep.ok(isinstance(lst, list) and len(lst) >= 4, "playground_ground: needs >= 4 random variants"):
                rep.ok({e.get("y", 0) for e in lst} >= {0, 90, 180, 270}, "playground_ground: variants must cover y=0/90/180/270")
        else:
            rep.ok(set(variants) == {""}, f"{bid}: expected a single '' variant")

    # models
    for ref in sorted(seen_models):
        ns, path = split_ref(ref)
        texs, elems, chain = collect_model(a, ref, rep)
        own = a.model_path(ref)
        # parents we depend on must be the documented vanilla ones
        if ns == NS and own and own.exists():
            data = json.loads(own.read_text(encoding="utf-8"))
            parent = data.get("parent")
            if parent:
                rep.ok(parent in PARENT_KEYS or split_ref(parent)[0] == NS, f"{ref}: unexpected parent {parent}")
                need = PARENT_KEYS.get(parent, set())
                have = set(data.get("textures", {}))
                rep.ok(need <= have, f"{ref}: missing texture keys {sorted(need - have)} for parent {parent}")
            if elems is not None:
                rep.ok("particle" in texs or parent in PARENT_KEYS, f"{ref}: no particle texture")
            for k, v in texs.items():
                if isinstance(v, str) and not v.startswith("#"):
                    if rep.ok(a.texture_exists(v), f"{ref}: texture '{k}' -> {v} missing"):
                        if split_ref(v)[0] == NS:
                            used_tex.add(split_ref(v)[1])
            if elems:
                check_elements(ref, texs, elems, a, rep)
                zfight_check(ref, elems, rep)
            if path == "block/invisible_wall":
                rep.ok(not data.get("elements") and "particle" in data.get("textures", {}),
                       f"{ref}: invisible wall must be an empty model with only a particle texture")
    # item models
    for bid in BLOCK_IDS + ITEM_IDS:
        ip = root / "models" / "item" / f"{bid}.json"
        if not rep.ok(ip.exists(), f"item model {bid}.json missing"):
            continue
        data = load_json(ip, rep)
        if not data:
            continue
        parent = data.get("parent", "")
        if bid in ITEM_IDS or bid == "invisible_wall":
            rep.ok(parent == "minecraft:item/generated", f"item {bid}: parent should be minecraft:item/generated")
            layer = data.get("textures", {}).get("layer0", "")
            rep.ok(layer.startswith(f"{NS}:item/") and a.texture_exists(layer), f"item {bid}: layer0 {layer} missing")
            if layer.startswith(f"{NS}:"):
                used_tex.add(split_ref(layer)[1])
        else:
            rep.ok(parent.startswith(f"{NS}:block/"), f"item {bid}: parent should be a squidgame block model, got {parent}")
            pm = a.model_path(parent)
            rep.ok(pm is not None and pm.exists(), f"item {bid}: parent model {parent} missing")
    for egg in SPAWN_EGGS:
        ip = root / "models" / "item" / f"{egg}.json"
        if rep.ok(ip.exists(), f"item model {egg}.json missing (spawn egg registered by ModItems)"):
            data = load_json(ip, rep) or {}
            rep.ok(data.get("parent") == "minecraft:item/template_spawn_egg", f"item {egg}: parent should be minecraft:item/template_spawn_egg")
    # texture usage via block models
    for mp in (root / "models" / "block").glob("*.json"):
        data = load_json(mp, rep)
        for v in (data or {}).get("textures", {}).values():
            if isinstance(v, str) and v.startswith(f"{NS}:"):
                used_tex.add(split_ref(v)[1])
    return {"used": used_tex}


# --------------------------------------------------------------------------- texture checks
def opaque_ratio(arr: np.ndarray) -> float:
    return float((arr[..., 3] == 255).mean())


def lum(rgb: np.ndarray) -> np.ndarray:
    return 0.2126 * rgb[..., 0] + 0.7152 * rgb[..., 1] + 0.0722 * rgb[..., 2]


def seam_ratio(arr: np.ndarray) -> tuple[float, float]:
    """How much worse the wrap-around seam is than the average neighbouring row/column difference."""
    rgb = arr[..., :3].astype(float)
    dx_adj = np.abs(np.diff(rgb, axis=1)).mean() + 1e-6
    dy_adj = np.abs(np.diff(rgb, axis=0)).mean() + 1e-6
    dx_seam = np.abs(rgb[:, 0] - rgb[:, -1]).mean()
    dy_seam = np.abs(rgb[0] - rgb[-1]).mean()
    return dx_seam / dx_adj, dy_seam / dy_adj


def seam_ok(arr: np.ndarray) -> tuple[bool, bool]:
    """Tiling test: the wrap-around transition must be no harsher than the harshest interior transition (+25%).

    Plank textures legitimately contain hard gap lines, so the seam is compared with the worst *interior* step
    instead of an average one.
    """
    rgb = arr[..., :3].astype(float)
    dx = np.abs(np.diff(rgb, axis=1)).mean(axis=(0, 2))       # per column pair
    dy = np.abs(np.diff(rgb, axis=0)).mean(axis=(1, 2))
    sx = np.abs(rgb[:, 0] - rgb[:, -1]).mean()
    sy = np.abs(rgb[0] - rgb[-1]).mean()
    return bool(sx <= dx.max() * 1.25 + 2), bool(sy <= dy.max() * 1.25 + 2)


def check_block_textures(a: Assets, rep: Report) -> None:
    root = a.root
    tex_dir = root / "textures" / "block"
    for p in sorted(tex_dir.glob("*.png")):
        arr = a.png(f"block/{p.name}")
        if arr is None:
            continue
        h, w = arr.shape[:2]
        if p.name == "monitor_screen.png":
            rep.ok(w == 16 and h % 16 == 0 and h >= 32, f"{p.name}: animated strip must be 16 x (16*n), got {w}x{h}")
            continue
        rep.ok((w, h) == (16, 16), f"block/{p.name}: expected 16x16, got {w}x{h}")
        if p.name == "bridge_glass.png":
            continue
        if p.name == "invisible_wall.png":
            continue
        rep.ok(opaque_ratio(arr) == 1.0, f"block/{p.name}: must be fully opaque (cube/machine textures)")

    # bridge glass: translucent but readable
    g = a.png("block/bridge_glass.png")
    if g is not None:
        al = g[..., 3].astype(int)
        rep.ok(((al > 0) & (al < 255)).sum() >= 100, "bridge_glass: needs translucent pixels")
        rep.ok(al.min() >= 60, f"bridge_glass: weakest alpha {al.min()} < 60 (would vanish against a dark void)")
        rep.ok(al[0, :].min() >= 200 and al[:, 0].min() >= 200 and al[-1, :].min() >= 200 and al[:, -1].min() >= 200,
               "bridge_glass: frame must be (nearly) opaque")
        inner = al[3:13, 3:13]
        rep.ok(70 <= inner.mean() <= 150, f"bridge_glass: interior mean alpha {inner.mean():.0f} outside 70..150")
        body = g[3:13, 3:13, :3].astype(float).mean(axis=(0, 1))
        rep.ok(body[2] > body[0] + 15 and body[1] > body[0] and body.min() > 150,
               f"bridge_glass: interior colour {body.round()} is not pale cyan")
        frame_dark = lum(g[0, :, :3].astype(float)).mean()
        rep.ok(frame_dark < lum(g[6:10, 6:10, :3].astype(float)).mean(), "bridge_glass: frame should be darker than the glass")

    # tiling seams for textures that must continue across neighbouring blocks
    tile_names = [f"tile_{c}" for c in TILES] + [f"panel_light_{c}" for c in PANEL_LIGHTS] + ["playground_ground"]
    tile_names += [f"pastel_{c}" for c in PASTELS] + ["cash_block_side"]
    for n in tile_names:
        arr = a.png(f"block/{n}.png")
        if arr is None:
            continue
        if n.startswith(("pastel_", "tile_", "panel_light_")):
            continue  # edge/grout lines are intentional seams
        sx, sy = seam_ratio(arr)
        rep.warn(sx < 2.2 and sy < 2.2, f"block/{n}: wrap-around seam looks harsh (x {sx:.1f}, y {sy:.1f})")

    # tiles: 2x2 grid with grout lines on x/y = 7 and 15 that differ from the tile body
    for c in TILES:
        arr = a.png(f"block/tile_{c}.png")
        if arr is None:
            continue
        rgb = arr[..., :3].astype(float)
        body = rgb[2:6, 2:6].mean(axis=(0, 1))
        grout = np.concatenate([rgb[7, :], rgb[15, :], rgb[:, 7], rgb[:, 15]]).mean(axis=0)
        rep.ok(abs(lum(body) - lum(grout)) > 12, f"tile_{c}: grout not distinguishable from the tile")
        corners = [rgb[7, 7], rgb[15, 15], rgb[7, 15], rgb[15, 7]]
        rep.ok(all(np.abs(cr - grout).max() < 60 for cr in corners), f"tile_{c}: grout junctions inconsistent")
        # gloss: the tile must contain highlights brighter than its body
        rep.ok(lum(rgb[0:7, 0:7]).max() > lum(body) + 12, f"tile_{c}: no gloss highlights")

    # pastel harmony
    lch = {}
    for c in PASTELS:
        arr = a.png(f"block/pastel_{c}.png")
        if arr is None:
            continue
        mean = tuple(arr[2:14, 2:14, :3].reshape(-1, 3).mean(axis=0))
        lch[c] = rgb_to_lch(mean)
    if len(lch) == len(PASTELS):
        Ls = [v[0] for v in lch.values()]
        rep.ok(all(72 <= L <= 96 for L in Ls), f"pastels: lightness outside 72..96 ({[round(L) for L in Ls]})")
        rep.ok(max(Ls) - min(Ls) <= 16, f"pastels: lightness spread {max(Ls) - min(Ls):.1f} > 16")
        chrom = {k: v for k, v in lch.items() if k != "cream"}
        rep.ok(all(14 <= v[1] <= 48 for v in chrom.values()), f"pastels: chroma outside 14..48 {[(k, round(v[1])) for k, v in chrom.items()]}")
        rep.ok(lch["cream"][1] < min(v[1] for v in chrom.values()), "pastels: cream should be the least saturated")
        hues = sorted(v[2] for v in chrom.values())
        gaps = [(hues[(i + 1) % len(hues)] - hues[i]) % 360 for i in range(len(hues))]
        rep.ok(min(gaps) >= 20, f"pastels: two colours too close in hue (min gap {min(gaps):.0f} deg)")
        # each pastel must have a lighter 1px edge than the body, and only a subtle texture
        for c in PASTELS:
            arr = a.png(f"block/pastel_{c}.png")
            rgb = arr[..., :3].astype(float)
            edge = lum(np.concatenate([rgb[0], rgb[-1], rgb[:, 0], rgb[:, -1]]))
            body = lum(rgb[2:14, 2:14].reshape(-1, 3))
            rep.ok(edge.mean() > body.mean() + 3, f"pastel_{c}: edge line should be lighter than the body")
            rep.ok(body.std() < 6.0, f"pastel_{c}: texture too noisy (std {body.std():.1f}); should be 'very subtle'")

    # symbols: symmetric white outline on black ground
    for s in SYMBOLS:
        arr = a.png(f"block/symbol_{s}.png")
        if arr is None:
            continue
        white = lum(arr[..., :3].astype(float)) > 150
        frac = white.mean()
        rep.ok(0.06 <= frac <= 0.34, f"symbol_{s}: white coverage {frac:.2f} outside 0.06..0.34")
        rep.ok((white == white[:, ::-1]).all(), f"symbol_{s}: symbol is not left-right symmetric")
        if s in ("circle", "square"):
            rep.ok((white == white[::-1, :]).all(), f"symbol_{s}: symbol is not top-bottom symmetric")
        dark = lum(arr[..., :3].astype(float)[~white])
        rep.ok(dark.max() < 70, f"symbol_{s}: background should be black")
        # the symbol must be an outline (hollow centre)
        rep.ok(not white[7:9, 7:9].any(), f"symbol_{s}: centre must be empty (outline only)")
    pl = a.png("block/symbol_plain.png")
    if pl is not None:
        rep.ok(lum(pl[..., :3].astype(float)).max() < 70, "symbol_plain: should be plain black")

    # cash block: yellow-green notes
    for n in ("cash_block_top", "cash_block_side"):
        arr = a.png(f"block/{n}.png")
        if arr is not None:
            m = arr[..., :3].astype(float).mean(axis=(0, 1))
            rep.ok(m[1] > m[2] + 40 and m[0] > m[2] + 20, f"{n}: mean colour {m.round()} is not yellow-green")

    # light panels bright
    for c in PANEL_LIGHTS:
        arr = a.png(f"block/panel_light_{c}.png")
        if arr is not None:
            rep.ok(lum(arr[..., :3].astype(float)).mean() > 190, f"panel_light_{c}: not bright enough")


def check_monitor(a: Assets, rep: Report) -> None:
    strip = a.png("block/monitor_screen.png")
    mp = a.root / "textures" / "block" / "monitor_screen.png.mcmeta"
    if not rep.ok(mp.exists(), "monitor_screen.png.mcmeta missing"):
        return
    meta = load_json(mp, rep)
    if not meta or strip is None:
        return
    anim = meta.get("animation")
    if not rep.ok(isinstance(anim, dict), "monitor_screen.png.mcmeta: no 'animation' object"):
        return
    n = strip.shape[0] // 16
    rep.ok(n == 8, f"monitor_screen: expected 8 frames, found {n}")
    frames = anim.get("frames", list(range(n)))
    rep.ok(all(isinstance(i, int) and 0 <= i < n for i in frames), "monitor_screen.png.mcmeta: frame index out of range")
    rep.ok(len(set(frames)) == n, "monitor_screen.png.mcmeta: every frame should be used")
    rep.ok(int(anim.get("frametime", 1)) >= 1, "monitor_screen.png.mcmeta: bad frametime")
    fr = [strip[i * 16:(i + 1) * 16] for i in range(n)]
    diffs = [np.abs(fr[i].astype(int) - fr[(i + 1) % n].astype(int)).mean() for i in range(n)]
    rep.ok(min(diffs) > 0.5, "monitor_screen: two consecutive frames are (almost) identical")
    rep.ok(max(diffs) < 40, "monitor_screen: frames change too violently (not 'faint')")
    mean_l = lum(strip[..., :3].astype(float)).mean()
    rep.ok(8 < mean_l < 110, f"monitor_screen: mean luminance {mean_l:.0f} should be a dim glowing screen")
    case = a.png("block/monitor_case.png")
    if case is not None:
        rep.ok(lum(case[..., :3].astype(float)).mean() < 70, "monitor_case: should be dark plastic")


def check_items(a: Assets, rep: Report) -> None:
    for n in ITEM_IDS + ["invisible_wall"]:
        arr = a.png(f"item/{n}.png")
        if arr is None:
            rep.ok(False, f"item/{n}.png missing")
            continue
        rep.ok(arr.shape[:2] == (16, 16), f"item/{n}: expected 16x16")
        al = arr[..., 3]
        rep.ok((al == 0).sum() >= 20 and (al > 0).sum() >= 40, f"item/{n}: needs a transparent background and a visible icon")
        rep.ok(al[0, 0] == 0 and al[0, 15] == 0, f"item/{n}: corners should be transparent")


def check_gui(a: Assets, rep: Report) -> None:
    for rel, (w, h) in EXPECTED_GUI.items():
        arr = a.png(f"gui/{rel}")
        if not rep.ok(arr is not None, f"gui/{rel} missing"):
            continue
        rep.ok(arr.shape[1] == w and arr.shape[0] == h, f"gui/{rel}: expected {w}x{h}, got {arr.shape[1]}x{arr.shape[0]}")
    # HUD icons: transparent background, white-ish, not empty
    for n in HUD_ICONS:
        arr = a.png(f"gui/hud/{n}.png")
        if arr is None:
            continue
        al = arr[..., 3]
        rep.ok(all(al[y, x] == 0 for y, x in [(0, 0), (0, 15), (15, 0), (15, 15)]), f"{n}: corners must be transparent (clear margin around the icon)")
        cov = (al > 0).mean()
        rep.ok(0.08 <= cov <= 0.75, f"{n}: coverage {cov:.2f} outside 0.08..0.75")
        vis = arr[al > 0, :3].astype(float)
        rep.ok(lum(vis).mean() >= 185, f"{n}: icon is not white-ish enough to tint (mean luminance {lum(vis).mean():.0f})")
        rep.ok(vis.min() >= 100, f"{n}: icon has very dark pixels (min channel {vis.min():.0f}); keep tintable")
        rep.ok(set(np.unique(al)) <= {0, 255}, f"{n}: icons must not use partial alpha (crisp pixel art)")
    # number plate
    np_ = a.png("gui/hud/number_plate.png")
    if np_ is not None:
        rep.ok((np_[..., 3] > 0).mean() > 0.95, "number_plate: should be an (almost) fully opaque white bib")
        rep.ok(lum(np_[2:14, 2:30, :3].astype(float)).mean() > 225, "number_plate: bib should be white")
        rep.ok(lum(np_[0, 4:28, :3].astype(float)).mean() < lum(np_[8, 4:28, :3].astype(float)).mean() - 30, "number_plate: needs a darker thin border")
    # vignette
    v = a.png("gui/hud/vignette_red.png")
    if v is not None:
        al = v[..., 3].astype(int)
        rep.ok(al[96:160, 96:160].max() <= 4, "vignette_red: centre must be transparent")
        rep.ok(al[0, 0] >= 200 and al[0, 255] >= 200 and al[255, 0] >= 200 and al[255, 255] >= 200, "vignette_red: corners must be opaque-ish")
        mid_edge = al[128, 0]
        rep.ok(mid_edge >= 90, f"vignette_red: edge midpoints too transparent ({mid_edge})")
        rep.ok(al[128, 64] < mid_edge, "vignette_red: alpha should grow towards the edge")
        px = v[v[..., 3] > 40, :3].astype(float).mean(axis=0)
        rep.ok(px[0] > px[1] + 60 and px[0] > px[2] + 60 and px[0] > 200,
               f"vignette_red: colour {px.round()} is not (pale) red")
    # panel: nine-slice (4px) uniformity + pink border
    p = a.png("gui/hud/panel.png")
    if p is not None:
        rep.ok((p[..., 3] < 255).any() and p[24, 24, 3] < 255, "panel: body should be translucent")
        rep.ok(90 <= p[24, 24, 3] <= 235, f"panel: body alpha {p[24, 24, 3]} should read as a translucent dark panel")
        rep.ok(lum(p[24, 24, :3].astype(float)[None])[0] < 60, "panel: body must be dark")
        edge = p[0, 24]
        rep.ok(edge[0] > edge[1] + 60 and edge[3] >= 200, f"panel: border pixel {edge} should be (near-)opaque pink")
        top = p[0:4, 4:44].astype(int)
        rep.ok((top == top[:, :1]).all(), "panel: top slice is not uniform along x (breaks nine-slice stretching)")
        left = p[4:44, 0:4].astype(int)
        rep.ok((left == left[:1]).all(), "panel: left slice is not uniform along y")
        bot = p[44:48, 4:44].astype(int)
        rep.ok((bot == bot[:, :1]).all(), "panel: bottom slice is not uniform along x")
        right = p[4:44, 44:48].astype(int)
        rep.ok((right == right[:1]).all(), "panel: right slice is not uniform along y")
        mid = p[4:44, 4:44].astype(int)
        rep.ok((mid == mid[:1, :1]).all(), "panel: centre is not uniform")
    # dalgona
    ck = a.png("gui/dalgona/cookie.png")
    if ck is not None:
        yy, xx = np.mgrid[0:128, 0:128]
        d = np.hypot(xx + 0.5 - 64, yy + 0.5 - 64)
        rep.ok((ck[..., 3][d < 58] == 255).all(), "cookie: disc interior must be opaque")
        rep.ok((ck[..., 3][d > 64.5] == 0).all(), "cookie: must be transparent outside the disc")
        inside = ck[..., :3][d < 55].astype(float)
        m = inside.mean(axis=0)
        rep.ok(m[0] > m[1] > m[2] and m[0] > 150 and m[2] < 110, f"cookie: mean colour {m.round()} is not honey-brown")
        # mottled caramel: real tonal variation, but no large dark shape painted on it
        rep.ok(lum(inside).std() > 12, "cookie: not mottled enough")
        rep.ok(lum(inside).min() > 45, "cookie: has near-black marks (a stamped shape?)")
    tb = a.png("gui/dalgona/table.png")
    if tb is not None:
        rep.ok(opaque_ratio(tb) == 1.0, "table: must be opaque")
        okx, oky = seam_ok(tb)
        rep.ok(okx and oky, f"table: visible seam when tiled (x ok={okx}, y ok={oky})")
    nd = a.png("gui/dalgona/needle.png")
    if nd is not None:
        rep.ok(nd[0, 0, 3] == 255, "needle: tip pixel (0,0) must be opaque")
        rep.ok(nd[0, 15, 3] == 0 and nd[31, 0, 3] == 0, "needle: only the needle itself should be opaque")
        ys, xs = np.nonzero(nd[..., 3])
        rep.ok(ys.min() == 0 and xs.min() == 0, "needle: tip must be the top-left extreme of the image")
        rep.ok(ys.max() >= 29, "needle: should extend to the bottom of the image")
        rep.ok(lum(nd[0:6, 0:6, :3].astype(float)[nd[0:6, 0:6, 3] > 0]).mean() > 150, "needle: tip should look like bright metal")
    sets = []
    for i in range(4):
        cr = a.png(f"gui/dalgona/crack_{i}.png")
        if cr is None:
            continue
        al = cr[..., 3] > 0
        rep.ok(al.any(), f"crack_{i}: empty")
        rgb = cr[al, :3]
        rep.ok((rgb >= 250).all(), f"crack_{i}: must be white-on-transparent")
        rep.ok(cr[..., 3][0, 0] == 0 and cr[..., 3][63, 63] == 0, f"crack_{i}: corners must be transparent")
        sets.append(al)
    for i in range(len(sets) - 1):
        rep.ok((sets[i] & ~sets[i + 1]).sum() == 0, f"crack_{i} is not contained in crack_{i + 1} (severity should nest)")
        rep.ok(sets[i + 1].sum() > sets[i].sum() * 1.25, f"crack_{i + 1} should be clearly more severe than crack_{i}")
    # marbles
    for n in ("hand_closed", "hand_open"):
        arr = a.png(f"gui/marbles/{n}.png")
        if arr is not None:
            al = arr[..., 3]
            rep.ok(al[0, 0] == 0 and al[0, 47] == 0, f"{n}: corners must be transparent")
            rep.ok((al > 0).mean() > 0.30, f"{n}: hand too small")
            rep.ok(al[24, 24] == 255, f"{n}: centre must be opaque")
    mb = a.png("gui/marbles/marble_big.png")
    if mb is not None:
        rep.ok(mb[0, 0, 3] == 0 and mb[8, 8, 3] == 255, "marble_big: round marble on transparent background")
        rep.ok(lum(mb[..., :3].astype(float)).max() > 235, "marble_big: needs a bright highlight")
    rt = a.png("gui/marbles/ring_target.png")
    if rt is not None:
        al = rt[..., 3]
        rep.ok(al[0, 0] == 0 and al[64, 64] == 255, "ring_target: round target on transparent background")
        ring_cols = [tuple(rt[64, 64 + dx, :3]) for dx in (3, 20, 32, 44, 56)]
        rep.ok(len(set(ring_cols)) >= 4, f"ring_target: expected 5 distinct concentric bands, got {len(set(ring_cols))}")
        reds = sum(1 for c in ring_cols if c[0] > 170 and c[1] < 90 and c[2] < 100)
        blues = sum(1 for c in ring_cols if c[2] > 140 and c[0] < 90)
        whites = sum(1 for c in ring_cols if min(c) > 220)
        rep.ok(reds >= 1 and blues >= 1 and whites >= 1, "ring_target: needs red, white and blue bands")
        def cat(c):
            r_, g_, b_ = (int(v) for v in c[:3])
            if min(r_, g_, b_) > 200:
                return "white"
            if r_ > 150 and g_ < 110 and b_ < 120:
                return "red"
            if b_ > 120 and r_ < 110:
                return "blue"
            return "other"
        for rad in (59, 45, 32, 20, 8):
            cats = {cat(rt[64, 64 + rad]), cat(rt[64, 64 - rad]), cat(rt[64 + rad, 64]), cat(rt[64 - rad, 64])}
            rep.ok(len(cats) == 1 and "other" not in cats, f"ring_target: radius {rad} is not one clean band colour in all four directions ({sorted(cats)})")


# --------------------------------------------------------------------------- tracksuit
def _box_uv(u0, v0, w, h, d):
    return [(u0 + d, v0, w, d), (u0 + d + w, v0, w, d), (u0, v0 + d, d, h), (u0 + d, v0 + d, w, h),
            (u0 + d + w, v0 + d, d, h), (u0 + 2 * d + w, v0 + d, w, h)]


def check_entities(a: Assets, rep: Report) -> None:
    for n, (w, h) in EXPECTED_ENTITY.items():
        arr = a.png(f"entity/{n}")
        if not rep.ok(arr is not None, f"entity/{n} missing"):
            continue
        rep.ok(arr.shape[1] == w and arr.shape[0] == h, f"entity/{n}: expected {w}x{h}")
    rope = a.png("entity/rope.png")
    if rope is not None:
        rep.ok(opaque_ratio(rope) == 1.0, "rope: must be opaque")
        m = rope[..., :3].astype(float).mean(axis=(0, 1))
        rep.ok(m[0] > m[1] > m[2] and 120 < m[0] < 230, f"rope: mean colour {m.round()} is not tan/brown")
        rgb = lum(rope[..., :3].astype(float))
        rep.ok(rgb.std(axis=1).mean() > 15, "rope: strands should show clear tonal contrast along each row")
        # twist: two rows down the pattern matches a horizontally shifted copy better than the unshifted one
        errs = [np.abs(np.roll(rgb, s_, axis=1)[2:] - rgb[:-2]).mean() for s_ in range(-4, 5)]
        rep.ok(int(np.argmin(errs)) != 4 and min(errs) < errs[4] * 0.75, "rope: strands do not twist (no diagonal structure)")
        sx, sy = seam_ratio(rope)
        rep.ok(sy < 3.0, f"rope: not tileable along the rope axis (seam ratio {sy:.1f})")
        v_jump = np.abs(rgb[0] - rgb[-1]).mean()
        rep.ok(v_jump < np.abs(np.diff(rgb, axis=0)).mean() * 2.5 + 3, "rope: vertical wrap seam visible")
    fl = a.png("entity/rope_flag.png")
    if fl is not None:
        rep.ok(opaque_ratio(fl) == 1.0, "rope_flag: must be opaque")
        m = fl[..., :3].astype(float).mean(axis=(0, 1))
        rep.ok(m[0] > m[1] + 90 and m[0] > m[2] + 90, f"rope_flag: mean colour {m.round()} is not red")

    van = common.vanilla_root()
    vanilla_skin = {}
    for slim_, rel in ((False, "wide/steve.png"), (True, "slim/alex.png")):
        if van is not None and (van / "textures/entity/player" / rel).exists():
            sk = np.array(Image.open(van / "textures/entity/player" / rel).convert("RGBA"))
            vanilla_skin[slim_] = sk[..., 3] > 0
    for slim in (False, True):
        name = "player_tracksuit_slim.png" if slim else "player_tracksuit.png"
        arr = a.png(f"entity/{name}")
        if arr is None:
            continue
        al = arr[..., 3] > 0
        aw = 3 if slim else 4
        used = np.zeros((64, 64), bool)
        parts = {  # name -> (u0, v0, w, h, d)
            "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
            "r_arm": (40, 16, aw, 12, 4), "r_sleeve": (40, 32, aw, 12, 4),
            "l_arm": (32, 48, aw, 12, 4), "l_sleeve": (48, 48, aw, 12, 4),
            "r_leg": (0, 16, 4, 12, 4), "r_pants": (0, 32, 4, 12, 4),
            "l_leg": (16, 48, 4, 12, 4), "l_pants": (0, 48, 4, 12, 4),
        }
        for pname, (u0, v0, w, h, d) in parts.items():
            for (x, y, fw, fh) in _box_uv(u0, v0, w, h, d):
                used[y:y + fh, x:x + fw] = True
        rep.ok(not al[0:16, 0:64].any(), f"{name}: head / hat area (rows 0..15) must be fully transparent")
        stray = al & ~used
        rep.ok(not stray.any(), f"{name}: {int(stray.sum())} opaque pixels outside the used UV regions")
        if slim in vanilla_skin:
            # independent check of the UV table: every opaque pixel of the real vanilla skin below the head rows
            # must lie inside the regions this overlay paints / reserves
            miss = (vanilla_skin[slim] & ~used)[16:, :]
            rep.ok(not miss.any(), f"{name}: layout differs from the vanilla {'Alex' if slim else 'Steve'} skin "
                                   f"({int(miss.sum())} vanilla pixels outside the overlay's UV regions)")
        for pname in ("body", "jacket", "r_leg", "l_leg", "r_pants", "l_pants", "r_arm", "l_arm", "r_sleeve", "l_sleeve"):
            u0, v0, w, h, d = parts[pname]
            faces = _box_uv(u0, v0, w, h, d)
            for fi, (x, y, fw, fh) in enumerate(faces):
                region = al[y:y + fh, x:x + fw]
                if fi == 1 and pname.endswith(("arm", "sleeve")):
                    rep.ok(not region.any(), f"{name}:{pname}: hand (bottom face) must stay transparent")
                    continue
                if pname.endswith(("arm", "sleeve")) and fi in (2, 3, 4, 5):
                    rep.ok(region[:10].all(), f"{name}:{pname} face {fi}: sleeve rows 0..9 must be fully painted")
                    rep.ok(not region[10:].any(), f"{name}:{pname} face {fi}: hand rows 10..11 must be transparent")
                else:
                    rep.ok(region.all(), f"{name}:{pname} face {fi}: must be fully painted ({int(region.sum())}/{region.size})")
        # colour sanity
        body = arr[20:32, 20:28, :3].astype(float).mean(axis=(0, 1))
        rep.ok(body[1] > body[0] + 30 and body[1] >= body[2], f"{name}: jacket colour {body.round()} is not green")
        # white stripe on the outer face of the right sleeve and leg, white sneakers
        sleeve_out = arr[20:30, 40:44, :3].astype(float)
        rep.ok((lum(sleeve_out) > 200).any(), f"{name}: no white stripe on the sleeve")
        leg_out = arr[22:28, 0:4, :3].astype(float)
        rep.ok((lum(leg_out) > 200).any(), f"{name}: no white stripe on the trouser leg")
        shoe = arr[29:31, 4:8, :3].astype(float)
        rep.ok(lum(shoe).mean() > 200, f"{name}: sneakers should be white")
        zip_col = arr[20:30, 23:25, :3].astype(float)
        rep.ok(lum(zip_col).max() > 190, f"{name}: no visible zipper on the jacket front")


def check_lang(root: Path, lang_path: Path, rep: Report) -> None:
    if not rep.ok(lang_path.exists(), f"{lang_path} missing"):
        return
    data = load_json(lang_path, rep)
    if not isinstance(data, dict):
        return
    for b in BLOCK_IDS:
        v = data.get(f"block.{NS}.{b}")
        rep.ok(isinstance(v, str) and len(v) >= 3, f"lang: block.{NS}.{b} missing/empty")
    for i in ITEM_IDS + SPAWN_EGGS:
        v = data.get(f"item.{NS}.{i}")
        rep.ok(isinstance(v, str) and len(v) >= 3, f"lang: item.{NS}.{i} missing/empty")
    known = {f"block.{NS}.{b}" for b in BLOCK_IDS} | {f"item.{NS}.{i}" for i in ITEM_IDS + SPAWN_EGGS}
    extra = [k for k in data if k not in known]
    rep.warn(not extra, f"lang: unexpected extra keys {extra[:5]}")
    names = list(data.values())
    rep.warn(len(set(names)) == len(names), "lang: duplicate display names")


def check_model_shapes(a: Assets, rep: Report) -> None:
    """Contract shape sanity for the three custom multi-cube models."""
    def elems(ref):
        _, el, _ = collect_model(a, f"{NS}:block/{ref}", rep)
        return el or []

    term = elems("registration_terminal")
    if term:
        lo, hi = model_bounds(term)
        rep.ok(10 <= hi[0] - lo[0] <= 14.5, f"registration_terminal: width {hi[0] - lo[0]:.1f}px should be about 12")
        rep.ok(14.5 <= hi[1] - lo[1] <= 16.001 and lo[1] >= -0.001, f"registration_terminal: height {hi[1] - lo[1]:.1f}px should be about 16 (and <= 16)")
        rep.ok(lo[0] >= 0 and hi[0] <= 16 and lo[2] >= 0 and hi[2] <= 16, "registration_terminal: model sticks out of the block")
        names = " ".join(str(e.get("name", "")) for e in term)
        rep.ok("ring" in names and "screen" in names and "card" in names, "registration_terminal: needs ring light, screen and card slot parts")
        rep.ok(any(e.get("rotation") for e in term), "registration_terminal: screen should be sloped (rotated element)")
    dal = elems("dalgona_station")
    if dal:
        lo, hi = model_bounds(dal)
        top = [e for e in dal if e["from"][1] == 6 and e["to"][1] == 8 and e["to"][0] - e["from"][0] >= 14 and e["to"][2] - e["from"][2] >= 14]
        rep.ok(len(top) == 1, "dalgona_station: needs a full-size tabletop whose top is 8px high")
        names = " ".join(str(e.get("name", "")) for e in dal)
        rep.ok("tin" in names and "cookie" in names, "dalgona_station: needs a tin and a cookie")
        rep.ok(hi[1] <= 14, f"dalgona_station: too tall ({hi[1]:.1f}px) for a low table")
        rep.ok(0 <= lo[0] and hi[0] <= 16 and lo[2] >= 0 and hi[2] <= 16, "dalgona_station: model sticks out of the block")
    mon = elems("monitor")
    if mon:
        lo, hi = model_bounds(mon)
        rep.ok(hi[2] - lo[2] <= 6.01, f"monitor: casing is {hi[2] - lo[2]:.1f}px deep; should be thin (<= 6)")
        rep.ok(any(fd.get("texture") == "#screen" for e in mon for fd in e["faces"].values()), "monitor: no screen face")


def check_orientation(a: Assets, rep: Report) -> None:
    """Bake the real blockstate variants and verify which way the screens face (north-facing models, y-rotated)."""
    import mcrender
    R = mcrender.Resolver(a.root, common.vanilla_root())
    dirs = {"north": (0, 0, -1), "east": (1, 0, 0), "south": (0, 0, 1), "west": (-1, 0, 0)}

    def quads_for(block: str, facing: str):
        sc = mcrender.Scene(R)
        sc.set((0, 0, 0), f"{NS}:{block}", facing=facing)
        try:
            return sc.quads()
        except (FileNotFoundError, KeyError) as e:  # missing assets are reported elsewhere
            rep.ok(False, f"{block}[facing={facing}]: cannot bake ({e})")
            return []

    def normal(q) -> np.ndarray:
        n = np.cross(q.verts[2] - q.verts[1], q.verts[0] - q.verts[1])
        return n / (np.linalg.norm(n) + 1e-9)

    def screen_quads(quads, texname: str):
        ref = a.png(f"block/{texname}.png")
        if ref is None:
            return []
        return [q for q in quads if q.tex.shape == ref.shape and np.array_equal(q.tex, ref)]

    for facing, d in dirs.items():
        dv = np.array(d, float)
        # monitor: screen faces the facing direction, casing leans on the opposite block side, <= 6px deep
        qs = quads_for("monitor", facing)
        if qs:
            scr = [q for q in screen_quads(qs, "monitor_screen") if float(np.dot(normal(q), dv)) > 0.9]
            rep.ok(len(scr) >= 1, f"monitor[facing={facing}]: no screen face points {facing}")
            allv = np.concatenate([q.verts for q in qs])
            lo, hi = allv.min(0), allv.max(0)
            axis = int(np.argmax(np.abs(dv)))
            if dv[axis] < 0:       # screen towards -axis, wall at +axis (coordinate 16)
                rep.ok(abs(hi[axis] - 16) < 1e-3 and 16 - lo[axis] <= 6.001, f"monitor[facing={facing}]: casing must sit against the opposite block side and be <= 6px deep")
            else:
                rep.ok(abs(lo[axis]) < 1e-3 and hi[axis] <= 6.001, f"monitor[facing={facing}]: casing must sit against the opposite block side and be <= 6px deep")
        # terminal: sloped screen looks towards facing and upwards; card slot is on the facing side
        qs = quads_for("registration_terminal", facing)
        if qs:
            scr = [q for q in screen_quads(qs, "registration_terminal_screen")]
            good = [q for q in scr if np.dot(normal(q), dv) > 0.7 and normal(q)[1] > 0.2]
            rep.ok(len(good) >= 1, f"registration_terminal[facing={facing}]: screen does not tilt up towards {facing}")
            card = [q for q in qs if q.tex is not None and q.tex.shape == (16, 16, 4)
                    and np.array_equal(q.tex, a.png("block/registration_terminal_front.png")) and q.verts[:, 1].max() < 5.2
                    and q.verts[:, 1].min() > 3.8]
            if card:
                cc = np.concatenate([q.verts for q in card]).mean(0)
                rep.ok(float(np.dot(cc - 8.0, dv)) > 2.0, f"registration_terminal[facing={facing}]: card slot should be on the {facing} side")
    # dalgona: tabletop must stay 8px high for every facing
    for facing in dirs:
        qs = quads_for("dalgona_station", facing)
        if qs:
            ups = [q for q in qs if abs(q.verts[:, 1].max() - 8.0) < 1e-6 and abs(q.verts[:, 1].min() - 8.0) < 1e-6]
            rep.ok(len(ups) >= 1, f"dalgona_station[facing={facing}]: no tabletop surface at y=8")


def check_orphans(a: Assets, used: set[str], rep: Report) -> None:
    for sub in ("block", "item"):
        for p in (a.root / "textures" / sub).glob("*.png"):
            ref = f"{sub}/{p.stem}"
            rep.warn(ref in used, f"textures/{sub}/{p.name} is not referenced by any model")


# --------------------------------------------------------------------------- freshness / determinism
def _digest(path: Path) -> str:
    """Content digest: decoded pixels for PNGs (the zlib stream may differ between builds), raw bytes otherwise."""
    if path.suffix == ".png":
        im = Image.open(path).convert("RGBA")
        return hashlib.sha256(f"{im.size}".encode() + im.tobytes()).hexdigest()
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _hash_tree(files: list[Path], root: Path) -> dict[str, str]:
    return {str(p.relative_to(root)): _digest(p) for p in files}


def check_fresh(root: Path, lang_path: Path, rep: Report) -> None:
    import gen_all
    with tempfile.TemporaryDirectory() as t1, tempfile.TemporaryDirectory() as t2, \
            tempfile.TemporaryDirectory() as l1, tempfile.TemporaryDirectory() as l2:
        o1 = gen_all.run(t1, l1, quiet=True)
        o2 = gen_all.run(t2, l2, quiet=True)
        h1 = _hash_tree([p for p in o1.written if o1.root in p.parents], o1.root)
        h2 = _hash_tree([p for p in o2.written if o2.root in p.parents], o2.root)
        rep.ok(h1 == h2, "generators are not deterministic (two runs differ: "
                         f"{[k for k in h1 if h1.get(k) != h2.get(k)][:5]})")
        stale, missing = [], []
        for rel, digest in h1.items():
            cur = root / rel
            if not cur.exists():
                missing.append(rel)
            elif _digest(cur) != digest:
                stale.append(rel)
        rep.ok(not stale and not missing, f"committed assets differ from the generators' output: stale={stale[:6]} missing={missing[:6]} (run gen_all.py)")
        lt = Path(l1) / "textures.json"
        if lt.exists() and lang_path.exists():
            rep.ok(lt.read_bytes() == lang_path.read_bytes(), "tools/assets/lang/textures.json is stale (run gen_all.py)")


# --------------------------------------------------------------------------- main
def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=str(common.DEFAULT_RES), help="assets/squidgame directory to validate")
    ap.add_argument("--lang", default=str(common.LANG_DIR / "textures.json"))
    ap.add_argument("--no-fresh", action="store_true", help="skip the regenerate-and-compare check")
    args = ap.parse_args(argv)

    root = Path(args.root)
    rep = Report()
    a = Assets(root, rep)

    # contract cross-check
    c_blocks, c_items = contract_ids(rep)
    if c_blocks:
        rep.ok(set(BLOCK_IDS) == c_blocks, f"contract block ids differ from the generator list: only-contract={sorted(c_blocks - set(BLOCK_IDS))} only-generator={sorted(set(BLOCK_IDS) - c_blocks)}")
    if c_items:
        rep.ok(set(ITEM_IDS) == c_items, f"contract items differ: {sorted(c_items ^ set(ITEM_IDS))}")
    gui_tokens = contract_gui_files(rep)
    known = {Path(k).name for k in EXPECTED_GUI}
    rep.ok(gui_tokens <= known, f"contract names GUI files the validator does not know: {sorted(gui_tokens - known)}")
    for tok in sorted(gui_tokens):
        rep.ok(any((root / "textures" / "gui" / sub / tok).exists() for sub in ("hud", "dalgona", "marbles")), f"contract file {tok} not found under textures/gui/*")

    used = check_blockstates_and_models(a, rep)
    check_block_textures(a, rep)
    check_monitor(a, rep)
    check_items(a, rep)
    check_gui(a, rep)
    check_entities(a, rep)
    check_model_shapes(a, rep)
    check_orientation(a, rep)
    check_lang(root, Path(args.lang), rep)
    check_orphans(a, used["used"], rep)
    if not args.no_fresh:
        check_fresh(root, Path(args.lang), rep)

    for w in rep.warnings:
        print(f"WARN  {w}")
    for e in rep.errors:
        print(f"ERROR {e}")
    print(f"validate_textures: {rep.checks} checks, {len(rep.errors)} errors, {len(rep.warnings)} warnings")
    return 1 if rep.errors else 0


if __name__ == "__main__":
    sys.exit(main())
