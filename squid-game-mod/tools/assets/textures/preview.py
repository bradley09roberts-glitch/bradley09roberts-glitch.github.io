#!/usr/bin/env python3
"""Build preview images into tools/assets/textures/preview/ (all nearest-neighbour upscaled).

    contact.png            every block texture (8x) incl. the 8 monitor frames; glass shown on a dark void
    contact_items_gui.png  item textures, HUD icons, GUI sprites
    contact_entity.png     rope, flag ribbon, tracksuit overlays
    iso_machines.png       monitor / registration terminal / dalgona station from several angles
    iso_blocks.png         cube blocks (cash, glass over a void, tiles, panels, playground, symbols)
    iso_pastels.png        blocks / stairs / slabs in the seven candy colours
    iso_stairway.png       a small pastel stairway scene using every stairs shape
    iso_controlroom.png    a tiny control-room diorama
    player_tracksuit.png   the tracksuit overlay on Steve (wide) and Alex (slim) from four angles

These are rendered by mcrender.py (a small software model renderer) -- close to, but not identical to, the game.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import common  # noqa: E402
import mcrender  # noqa: E402
import sheet  # noqa: E402
from common import BLOCK_IDS, PANEL_LIGHTS, PASTELS, SYMBOLS, TILES  # noqa: E402

BG = (58, 64, 78, 255)


def _save(img, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(img, np.ndarray):
        img = Image.fromarray(img, "RGBA")
    img.save(path)
    print(f"  wrote {path.relative_to(common.REPO_ROOT)}  {img.size[0]}x{img.size[1]}")


def _grid(tiles: list[np.ndarray], cols: int, bg=BG, pad: int = 0) -> np.ndarray:
    H = max(t.shape[0] for t in tiles)
    W = max(t.shape[1] for t in tiles)
    rows = (len(tiles) + cols - 1) // cols
    out = np.zeros((rows * H, cols * W, 4), np.uint8)
    out[:] = bg
    for i, t in enumerate(tiles):
        r, c = divmod(i, cols)
        y = r * H + (H - t.shape[0]) // 2
        x = c * W + (W - t.shape[1]) // 2
        out[y:y + t.shape[0], x:x + t.shape[1]] = t
    return out


def _vstack(rows: list[np.ndarray], bg=BG) -> np.ndarray:
    W = max(r.shape[1] for r in rows)
    out = []
    for r in rows:
        if r.shape[1] < W:
            pad = np.zeros((r.shape[0], W - r.shape[1], 4), np.uint8)
            pad[:] = bg
            r = np.concatenate([r, pad], axis=1)
        out.append(r)
    return np.concatenate(out, axis=0)


def _label(img: np.ndarray, text: str) -> np.ndarray:
    im = Image.fromarray(img, "RGBA")
    d = ImageDraw.Draw(im)
    d.text((6, 4), text, fill=(235, 238, 245, 255), font=sheet._font(12))
    return np.array(im)


# ------------------------------------------------------------------------------ contact sheets
def contact_blocks(root: Path) -> Image.Image:
    items = []
    tex = root / "textures" / "block"
    order = ["bridge_glass", "cash_block_top", "cash_block_side"]
    order += [f"panel_light_{c}" for c in PANEL_LIGHTS] + ["playground_ground"]
    order += [f"tile_{c}" for c in TILES] + [f"pastel_{c}" for c in PASTELS]
    order += [f"symbol_{c}" for c in SYMBOLS] + ["symbol_plain", "invisible_wall"]
    order += ["monitor_case", "monitor_front"]
    order += sorted(p.stem for p in tex.glob("registration_terminal_*.png"))
    order += sorted(p.stem for p in tex.glob("dalgona_station_*.png"))
    for n in order:
        p = tex / f"{n}.png"
        if p.exists():
            items.append((n, Image.open(p).convert("RGBA")))
    top = sheet.sheet(items, scale=8, cols=8, backdrop="void")
    # glass on three backdrops + monitor frames
    glass = Image.open(tex / "bridge_glass.png").convert("RGBA")
    strip = Image.open(tex / "monitor_screen.png").convert("RGBA")
    frames = [(f"monitor_screen {i}", strip.crop((0, 16 * i, 16, 16 * i + 16))) for i in range(strip.height // 16)]
    bottom = sheet.sheet(frames, scale=8, cols=8, backdrop="void")
    # glass: void / sky / lit floor
    W = 3 * (16 * 8 + 12) + 12
    gl = Image.new("RGBA", (W, 16 * 8 + 36), (36, 39, 48, 255))
    d = ImageDraw.Draw(gl)
    for i, (name, col) in enumerate([("void", (6, 7, 10)), ("sky", (150, 190, 235)), ("lit floor", (200, 196, 188))]):
        x = 12 + i * (16 * 8 + 12)
        bg = Image.new("RGBA", (16 * 8, 16 * 8), col + (255,))
        bg.alpha_composite(glass.resize((128, 128), Image.NEAREST))
        gl.paste(bg, (x, 8))
        d.text((x, 16 * 8 + 12), f"bridge_glass on {name}", fill=(225, 228, 235, 255), font=sheet._font(11))
    Wd = max(top.width, bottom.width, gl.width)
    out = Image.new("RGBA", (Wd, top.height + bottom.height + gl.height), (36, 39, 48, 255))
    out.paste(top, (0, 0))
    out.paste(bottom, (0, top.height))
    out.paste(gl, (0, top.height + bottom.height))
    return out


def contact_items_gui(root: Path) -> Image.Image:
    items = []
    for sub, names in (("item", ["marble", "recruiter_card", "invisible_wall"]),):
        for n in names:
            items.append((f"item/{n}", Image.open(root / "textures" / sub / f"{n}.png")))
    hud = sorted((root / "textures" / "gui" / "hud").glob("icon_*.png"))
    small = items + [(f"hud/{p.stem}", Image.open(p)) for p in hud]
    a = sheet.sheet(small, scale=8, cols=9, backdrop="void")
    # HUD icons tinted red / green / gold to show tintability
    tint_items = []
    for p in hud[:9]:
        arr = np.array(Image.open(p).convert("RGBA")).astype(float)
        for tint, nm in (((255, 90, 90), "red"), ((110, 255, 150), "green")):
            t = arr.copy()
            t[..., :3] = t[..., :3] * np.array(tint) / 255.0
            tint_items.append((f"{p.stem[5:]} {nm}", Image.fromarray(t.astype(np.uint8), "RGBA")))
    b = sheet.sheet(tint_items, scale=6, cols=12, backdrop="void")
    rest = []
    gui = root / "textures" / "gui"
    for rel in ["hud/number_plate", "hud/panel", "hud/vignette_red", "dalgona/cookie", "dalgona/table", "dalgona/needle",
                "dalgona/crack_0", "dalgona/crack_1", "dalgona/crack_2", "dalgona/crack_3",
                "marbles/hand_closed", "marbles/hand_open", "marbles/marble_big", "marbles/ring_target"]:
        rest.append((rel, Image.open(gui / f"{rel}.png")))
    # numbers + panel demo: nine-slice the panel to 120x60 and draw the plate
    c = sheet.sheet(rest, scale=3, cols=5, backdrop="checker", max_cell=(260, 260))
    Wd = max(a.width, b.width, c.width)
    out = Image.new("RGBA", (Wd, a.height + b.height + c.height), (36, 39, 48, 255))
    out.paste(a, (0, 0))
    out.paste(b, (0, a.height))
    out.paste(c, (0, a.height + b.height))
    return out


def contact_entity(root: Path) -> Image.Image:
    items = []
    for n in ["rope", "rope_flag", "player_tracksuit", "player_tracksuit_slim"]:
        im = Image.open(root / "textures" / "entity" / f"{n}.png").convert("RGBA")
        items.append((n, im))
    # rope tiled 1x4 to show the twist continues
    rope = items[0][1]
    tiled = Image.new("RGBA", (16, 64))
    for i in range(4):
        tiled.paste(rope, (0, 16 * i))
    items.insert(1, ("rope x4 (vertical)", tiled))
    return sheet.sheet(items, scale=8, cols=3, backdrop="checker", max_cell=(520, 520))


# ------------------------------------------------------------------------------ iso renders
def resolver(root: Path) -> mcrender.Resolver:
    return mcrender.Resolver(root, common.vanilla_root())


def _one(R, block: str, yaw: float, pitch: float, scale: float, props=None, size=(0, 0), ssaa=2) -> np.ndarray:
    sc = mcrender.Scene(R)
    sc.set((0, 0, 0), f"squidgame:{block}", **(props or {}))
    return mcrender.render(sc.quads(), yaw=yaw, pitch=pitch, scale=scale, center=(8, 8, 8), bg=BG, ssaa=ssaa, min_size=size)


def iso_machines(R) -> np.ndarray:
    rows = []
    for b in ("monitor", "registration_terminal", "dalgona_station"):
        row = [_one(R, b, yaw, 28, 11, {"facing": "north"}, (230, 250)) for yaw in (215, 150, 180, 20)]
        rows.append(_label(_grid(row, 4), b))
    return _vstack(rows)


def iso_blocks(R) -> np.ndarray:
    """Cube blocks on a dark base plate so the translucent glass can be judged."""
    tiles = []
    for b in ["cash_block", "tile_pink", "tile_white", "tile_black", "panel_light_white", "panel_light_warm",
              "panel_light_pink", "playground_ground", "symbol_circle", "symbol_triangle", "symbol_square"]:
        tiles.append(_label(_one(R, b, 225, 30, 7, size=(150, 160)), b))
    # glass: 3x3 void with a floor underneath -> shows translucency
    sc = mcrender.Scene(R)
    for x in range(3):
        for z in range(3):
            sc.set((x, 0, z), "squidgame:bridge_glass")
    for x in range(-1, 4):
        for z in range(-1, 4):
            sc.set((x, -2, z), "squidgame:tile_black")
    tiles.append(_label(mcrender.render(sc.quads(), yaw=225, pitch=32, scale=4, center=(24, 0, 24), bg=BG, ssaa=2, min_size=(220, 220)),
                        "bridge_glass 3x3 over a dark floor"))
    return _grid(tiles, 4)


def iso_pastels(R) -> np.ndarray:
    sc = mcrender.Scene(R)
    for i, c in enumerate(PASTELS):
        sc.set((i, 0, 0), f"squidgame:pastel_{c}")
        sc.set((i, 0, 2), f"squidgame:pastel_{c}_stairs", facing="north", half="bottom", shape="straight")
        sc.set((i, 0, 4), f"squidgame:pastel_{c}_slab", type="bottom")
        sc.set((i, 0, 6), f"squidgame:pastel_{c}_stairs", facing="west", half="top", shape="straight")
    img_a = mcrender.render(sc.quads(), yaw=215, pitch=30, scale=5, center=(56, 8, 48), bg=BG, ssaa=2)
    img_b = mcrender.render(sc.quads(), yaw=150, pitch=30, scale=5, center=(56, 8, 48), bg=BG, ssaa=2)
    return _grid([img_a, img_b], 1)


def iso_stairway(R) -> np.ndarray:
    """A candy-coloured stairway: straight, inner and outer stairs, slabs and full blocks."""
    sc = mcrender.Scene(R)
    cols = PASTELS
    # base platform
    for x in range(0, 9):
        for z in range(0, 9):
            sc.set((x, -1, z), f"squidgame:pastel_{cols[(x + z) % 7]}")
    # staircase climbing east
    for i in range(6):
        c = cols[i % 7]
        sc.set((1 + i, 0, 3), f"squidgame:pastel_{c}_stairs", facing="east", half="bottom", shape="straight")
        sc.set((1 + i, 0, 4), f"squidgame:pastel_{c}_stairs", facing="east", half="bottom", shape="straight")
        for y in range(i):
            sc.set((1 + i, y, 3), f"squidgame:pastel_{c}")
            sc.set((1 + i, y, 4), f"squidgame:pastel_{c}")
        sc.set((1 + i, i, 3), f"squidgame:pastel_{c}_stairs", facing="east", half="bottom", shape="straight")
        sc.set((1 + i, i, 4), f"squidgame:pastel_{c}_stairs", facing="east", half="bottom", shape="straight")
    # a little corner set: inner / outer
    sc.set((1, 0, 6), "squidgame:pastel_pink_stairs", facing="north", half="bottom", shape="outer_left")
    sc.set((2, 0, 6), "squidgame:pastel_pink_stairs", facing="north", half="bottom", shape="straight")
    sc.set((3, 0, 6), "squidgame:pastel_pink_stairs", facing="north", half="bottom", shape="outer_right")
    sc.set((1, 0, 7), "squidgame:pastel_mint_stairs", facing="east", half="bottom", shape="inner_left")
    sc.set((3, 0, 7), "squidgame:pastel_mint_stairs", facing="west", half="bottom", shape="inner_right")
    sc.set((5, 0, 7), "squidgame:pastel_sky_slab", type="bottom")
    sc.set((6, 0, 7), "squidgame:pastel_sky_slab", type="top")
    sc.set((7, 0, 7), "squidgame:pastel_sky_slab", type="double")
    a = mcrender.render(sc.quads(), yaw=215, pitch=30, scale=4.2, center=(72, 24, 72), bg=BG, ssaa=2)
    b = mcrender.render(sc.quads(), yaw=145, pitch=30, scale=4.2, center=(72, 24, 72), bg=BG, ssaa=2)
    return _grid([a, b], 1)


def iso_controlroom(R) -> np.ndarray:
    sc = mcrender.Scene(R)
    n = 9
    for x in range(n):
        for z in range(n):
            col = "black" if (x + z) % 2 == 0 else "white"
            if x in (3, 4, 5) and z > 1:
                col = "pink"
            sc.set((x, -1, z), f"squidgame:tile_{col}")
    # back wall of black tiles on the south side (z = 9), monitors facing north
    for x in range(n):
        for y in range(0, 4):
            sc.set((x, y, n), "squidgame:tile_black")
    for x in range(1, n, 2):
        sc.set((x, 1, n - 1), "squidgame:monitor", facing="north")
        sc.set((x, 2, n - 1), "squidgame:monitor", facing="north")
    # ceiling light panels row along the back
    for x in range(0, n, 2):
        kind = ("white", "warm", "pink")[(x // 2) % 3]
        sc.set((x, 4, n), f"squidgame:panel_light_{kind}")
    sc.set((1, 0, 3), "squidgame:registration_terminal", facing="east")
    sc.set((7, 0, 3), "squidgame:registration_terminal", facing="west")
    sc.set((4, 0, 6), "squidgame:dalgona_station", facing="north")
    sc.set((6, 0, 6), "squidgame:dalgona_station", facing="north")
    sc.set((0, 0, 0), "squidgame:symbol_circle")
    sc.set((1, 0, 0), "squidgame:symbol_triangle")
    sc.set((2, 0, 0), "squidgame:symbol_square")
    sc.set((8, 0, 0), "squidgame:cash_block")
    sc.set((8, 1, 0), "squidgame:cash_block")
    sc.set((7, 0, 0), "squidgame:cash_block")
    sc.set((0, 0, 8), "squidgame:playground_ground")
    a = mcrender.render(sc.quads(), yaw=145, pitch=30, scale=4.4, center=(72, 24, 72), bg=BG, ssaa=2)
    b = mcrender.render(sc.quads(), yaw=215, pitch=30, scale=4.4, center=(72, 24, 72), bg=BG, ssaa=2)
    return _grid([a, b], 1)


def player_views(root: Path) -> np.ndarray:
    van = common.vanilla_root()
    if van is None:
        return np.zeros((8, 8, 4), np.uint8)
    tiles = []
    for slim in (False, True):
        sk = np.array(Image.open(van / "textures/entity/player" / ("slim/alex.png" if slim else "wide/steve.png")).convert("RGBA"))
        ov = common.load_png(root / "textures" / "entity" / ("player_tracksuit_slim.png" if slim else "player_tracksuit.png"))
        for yaw in (0, 180, 50, -50):
            q = mcrender.player_quads(sk, ov, slim=slim)
            tiles.append(mcrender.render(q, yaw=yaw, pitch=10, scale=8, center=(0, 16, 0), bg=BG, ssaa=2, min_size=(140, 330)))
    return _grid(tiles, 4)


# ------------------------------------------------------------------------------ main
def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", default=str(common.DEFAULT_RES))
    ap.add_argument("--out", default=str(common.PREVIEW_DIR))
    ap.add_argument("--only", nargs="*", help="subset of: contact items entity machines blocks pastels stairway room player")
    args = ap.parse_args(argv)
    root, out = Path(args.root), Path(args.out)
    want = set(args.only or ["contact", "items", "entity", "machines", "blocks", "pastels", "stairway", "room", "player"])
    R = resolver(root)
    print("building previews ...")
    if "contact" in want:
        _save(contact_blocks(root), out / "contact.png")
    if "items" in want:
        _save(contact_items_gui(root), out / "contact_items_gui.png")
    if "entity" in want:
        _save(contact_entity(root), out / "contact_entity.png")
    if "machines" in want:
        _save(iso_machines(R), out / "iso_machines.png")
    if "blocks" in want:
        _save(iso_blocks(R), out / "iso_blocks.png")
    if "pastels" in want:
        _save(iso_pastels(R), out / "iso_pastels.png")
    if "stairway" in want:
        _save(iso_stairway(R), out / "iso_stairway.png")
    if "room" in want:
        _save(iso_controlroom(R), out / "iso_controlroom.png")
    if "player" in want:
        _save(player_views(root), out / "player_tracksuit.png")
    return 0


if __name__ == "__main__":
    sys.exit(main())
