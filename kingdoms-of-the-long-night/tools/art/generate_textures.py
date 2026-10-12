#!/usr/bin/env python3
"""Regenerate every texture of Kingdoms of the Long Night (mod id `longnight`).

    python3 tools/art/generate_textures.py            # write textures + previews, verify
    python3 tools/art/generate_textures.py --no-preview

Deterministic: every random choice uses a fixed seed, so re-running produces
byte-identical PNGs. Requires Python 3, Pillow and numpy.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.dont_write_bytecode = True          # keep the repo free of __pycache__
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import numpy as np                      # noqa: E402
from PIL import Image                    # noqa: E402

from art_armor import ARMOR, make_armor          # noqa: E402
from art_blocks import ANIMATED, BLOCKS          # noqa: E402
from art_bodies import BODIES, make_body         # noqa: E402
from art_clothes import CLOTHES, make_clothes    # noqa: E402
from art_gui import GUI                          # noqa: E402
from art_hair import STYLES, make_hair           # noqa: E402
from art_items import ITEMS                      # noqa: E402
from art_skin import FACES, PARTS, face_rect, used_mask  # noqa: E402
import art_preview as pv                         # noqa: E402

PROJECT = HERE.parent.parent
TEX = PROJECT / 'mod' / 'src' / 'main' / 'resources' / 'assets' / 'longnight' / 'textures'
PREVIEW = HERE / 'preview'
CROPS = {'blight_crop_stage0', 'blight_crop_stage1', 'blight_crop_stage2'}


def save(img: Image.Image, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, 'PNG', optimize=True)
    return path


# ------------------------------------------------------------------------- generation
def generate():
    written = {}
    layers = {'body': {}, 'clothes': {}, 'hair': {}, 'armor': {}}
    for bid in BODIES:
        arr = make_body(bid).a
        layers['body'][bid] = arr
        written[save(Image.fromarray(arr, 'RGBA'), TEX / 'entity/citizen/body' / f'{bid}.png')] = ('body', (64, 64))
    for cid in CLOTHES:
        arr = make_clothes(cid).rgba()
        layers['clothes'][cid] = arr
        written[save(Image.fromarray(arr, 'RGBA'), TEX / 'entity/citizen/clothes' / f'{cid}.png')] = ('clothes', (64, 64))
    for hid in STYLES:
        arr = make_hair(hid).rgba()
        layers['hair'][hid] = arr
        written[save(Image.fromarray(arr, 'RGBA'), TEX / 'entity/citizen/hair' / f'{hid}.png')] = ('hair', (64, 64))
    for aid in ARMOR:
        arr = make_armor(aid).a
        layers['armor'][aid] = arr
        written[save(Image.fromarray(arr, 'RGBA'), TEX / 'entity/citizen/armor' / f'{aid}.png')] = ('armor', (64, 64))
    tiles = []
    for name, fn in BLOCKS.items():
        img = fn().image()
        size = (16, 64) if name in ANIMATED else (16, 16)
        kind = 'crop' if name in CROPS else 'block'
        path = save(img, TEX / 'block' / f'{name}.png')
        written[path] = (kind, size)
        if name in ANIMATED:
            meta = path.with_name(path.name + '.mcmeta')
            meta.write_text(json.dumps(ANIMATED[name], indent=2) + '\n')
            written[meta] = ('mcmeta', None)
        tiles.append((f'block/{name}', img))
    for name, fn in ITEMS.items():
        img = fn().image()
        written[save(img, TEX / 'item' / f'{name}.png')] = ('item', (16, 16))
        tiles.append((f'item/{name}', img))
    gui = {}
    for name, fn in GUI.items():
        img = fn().image()
        size = (256, 256) if name == 'ledger_background' else (128, 128)
        written[save(img, TEX / 'gui' / f'{name}.png')] = ('gui', size)
        gui[name] = img
    return written, layers, tiles, gui


# ------------------------------------------------------------------------- verification
def _greyscale(arr):
    m = arr[..., 3] > 0
    return bool(np.all(arr[m][:, 0] == arr[m][:, 1]) and np.all(arr[m][:, 1] == arr[m][:, 2]))


def verify(written):
    errors = []
    used = used_mask()
    base_only = used_mask(base=True, overlay=False)
    overlay_only = used_mask(base=False, overlay=True)
    expected = (
        [TEX / 'entity/citizen/body' / f'{b}.png' for b in BODIES]
        + [TEX / 'entity/citizen/clothes' / f'{c}.png' for c in CLOTHES]
        + [TEX / 'entity/citizen/hair' / f'{h}.png' for h in STYLES]
        + [TEX / 'entity/citizen/armor' / f'{a}.png' for a in ARMOR]
        + [TEX / 'block' / f'{n}.png' for n in BLOCKS]
        + [TEX / 'block/ward_stone_side_active.png.mcmeta']
        + [TEX / 'item' / f'{n}.png' for n in ITEMS]
        + [TEX / 'gui/ledger_background.png', TEX / 'gui/logo.png']
    )
    for p in expected:
        if not p.exists():
            errors.append(f'missing: {p}')
    for path, (kind, size) in written.items():
        if kind == 'mcmeta':
            json.loads(path.read_text())
            continue
        im = Image.open(path)
        if im.format != 'PNG' or im.mode != 'RGBA':
            errors.append(f'{path.name}: expected RGBA PNG, got {im.format}/{im.mode}')
        if im.size != size:
            errors.append(f'{path.name}: size {im.size} != {size}')
            continue
        arr = np.asarray(im.convert('RGBA'))
        alpha = arr[..., 3]
        if np.any((alpha != 0) & (alpha != 255)):
            errors.append(f'{path.name}: partial alpha')
        if kind in ('body', 'clothes', 'hair', 'armor'):
            if np.any(alpha[~used] != 0):
                errors.append(f'{path.name}: pixels outside the player UV layout')
        if kind == 'body':
            for part in PARTS:
                for face in FACES:
                    x0, y0, x1, y1 = face_rect(part, face)
                    if np.any(alpha[y0:y1, x0:x1] != 255):
                        errors.append(f'{path.name}: base face {part}/{face} not opaque')
            if np.any(alpha[overlay_only & ~base_only] != 0):
                errors.append(f'{path.name}: body layer must leave overlay regions empty')
        if kind in ('clothes', 'hair') and not _greyscale(arr):
            errors.append(f'{path.name}: tinted layer must be greyscale')
        if kind == 'clothes':
            x0, y0, x1, y1 = face_rect('head', 'front')
            if np.any(alpha[y0:y1, x0:x1]):
                errors.append(f'{path.name}: clothes cover the face')
            for arm in ('right_arm', 'left_arm'):
                x0, y0, x1, y1 = face_rect(arm, 'front')
                if np.any(alpha[y1 - 2:y1, x0:x1]):
                    errors.append(f'{path.name}: clothes cover the hands')
        if kind == 'armor' and np.any(alpha[base_only & ~overlay_only] != 0):
            errors.append(f'{path.name}: armour must only use overlay regions')
        if kind == 'block' and np.any(alpha != 255):
            errors.append(f'{path.name}: full block texture must be opaque')
        if kind == 'gui' and path.name == 'ledger_background.png':
            outside = np.ones((256, 256), dtype=bool)
            outside[:180, :248] = False
            if np.any(alpha[outside]):
                errors.append('ledger_background.png: pixels outside the 248x180 area')
    return errors


def manifest(written):
    rows = []
    for path in sorted(written, key=lambda p: str(p)):
        kind, size = written[path]
        rel = path.relative_to(PROJECT)
        rows.append(f'  {str(rel):<92} {"x".join(map(str, size)) if size else "json":>7}  {kind}')
    return rows


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--no-preview', action='store_true', help='skip writing preview sheets')
    args = ap.parse_args()

    written, layers, tiles, gui = generate()
    errors = verify(written)
    print('Kingdoms of the Long Night - texture manifest')
    print('\n'.join(manifest(written)))
    pngs = sum(1 for p in written if p.suffix == '.png')
    print(f'{pngs} PNG textures + {len(written) - pngs} mcmeta written under {TEX.relative_to(PROJECT)}')
    if not args.no_preview:
        PREVIEW.mkdir(parents=True, exist_ok=True)
        pv.contact_sheet(tiles, PREVIEW / 'contact_sheet.png')
        pv.citizens_sheet(layers, PREVIEW / 'citizens.png', PREVIEW / 'citizens_back.png')
        pv.citizens_3d(layers, PREVIEW / 'citizens_3d.png')
        pv.uv_sheet(layers, PREVIEW / 'uv_layers.png')
        pv.gui_sheet(gui['ledger_background'], gui['logo'], PREVIEW / 'gui.png')
        print(f'previews written to {PREVIEW.relative_to(PROJECT)}/')
    if errors:
        print('VERIFICATION FAILED:')
        print('\n'.join('  - ' + e for e in errors))
        sys.exit(1)
    print('verification passed: sizes, alpha, UV regions, greyscale tint layers, opacity')


if __name__ == '__main__':
    main()
