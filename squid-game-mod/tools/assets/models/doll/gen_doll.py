#!/usr/bin/env python3
"""Generate the giant doll's GeckoLib assets.  Deterministic and re-runnable.

    python3 tools/assets/models/doll/gen_doll.py            # write the 4 resource files
    python3 tools/assets/models/doll/gen_doll.py --preview  # ... and regenerate preview/*.png
    python3 tools/assets/models/doll/gen_doll.py --check    # ... and run validate_doll.py

Outputs (relative to <repo>/src/main/resources/assets/squidgame/):
    geo/entity/doll.geo.json
    animations/entity/doll.animation.json
    textures/entity/doll.png
    textures/entity/doll_glowmask.png
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))

from PIL import Image

import doll_anims
import doll_model
import jsonfmt

ASSETS = HERE.parents[3] / "src" / "main" / "resources" / "assets" / "squidgame"
GEO_PATH = ASSETS / "geo" / "entity" / "doll.geo.json"
ANIM_PATH = ASSETS / "animations" / "entity" / "doll.animation.json"
TEX_PATH = ASSETS / "textures" / "entity" / "doll.png"
GLOW_PATH = ASSETS / "textures" / "entity" / "doll_glowmask.png"


def build():
    mb, atlas = doll_model.build()
    geo = mb.to_json()
    anims = doll_anims.build_all()
    anim_doc = {"format_version": "1.8.0", "animations": {"animation.doll." + a.name: a.to_json() for a in anims}}
    return geo, anim_doc, atlas, anims


def write_all(geo, anim_doc, atlas):
    for p in (GEO_PATH, ANIM_PATH, TEX_PATH, GLOW_PATH):
        p.parent.mkdir(parents=True, exist_ok=True)
    jsonfmt.write(geo, GEO_PATH)
    jsonfmt.write(anim_doc, ANIM_PATH)
    Image.fromarray(atlas.to_rgba8(), "RGBA").save(TEX_PATH, optimize=False)
    Image.fromarray(atlas.glowmask_rgba8(), "RGBA").save(GLOW_PATH, optimize=False)


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true", help="regenerate preview/*.png")
    ap.add_argument("--check", action="store_true", help="run validate_doll.py and verify_geckolib.py afterwards")
    ap.add_argument("--determinism", action="store_true", help="build twice and verify the outputs are byte-identical")
    args = ap.parse_args(argv)
    if args.determinism:
        import hashlib
        import io

        def digest():
            geo, anim_doc, atlas, _ = build()
            h = hashlib.sha256()
            h.update(jsonfmt.dumps(geo).encode())
            h.update(jsonfmt.dumps(anim_doc).encode())
            for arr in (atlas.to_rgba8(), atlas.glowmask_rgba8()):
                buf = io.BytesIO()
                Image.fromarray(arr, "RGBA").save(buf, "PNG")
                h.update(buf.getvalue())
            return h.hexdigest()

        a, b = digest(), digest()
        print("determinism:", "OK" if a == b else "MISMATCH", a[:16], b[:16])
        return 0 if a == b else 1
    geo, anim_doc, atlas, anims = build()
    write_all(geo, anim_doc, atlas)
    nb = len(geo["minecraft:geometry"][0]["bones"])
    nc = sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])
    print("wrote %s (%d bones, %d cubes)" % (GEO_PATH.relative_to(HERE.parents[3]), nb, nc))
    print("wrote %s (%d animations)" % (ANIM_PATH.relative_to(HERE.parents[3]), len(anims)))
    print("wrote %s, %s (atlas rows used: %d / %d)" % (TEX_PATH.name, GLOW_PATH.name, atlas.used_rows, atlas.h))
    if args.preview:
        import preview
        preview.make_all()
    if args.check:
        import validate_doll
        import verify_geckolib
        rc = validate_doll.main([])
        rc2 = verify_geckolib.main([])
        return rc or rc2
    return 0


if __name__ == "__main__":
    sys.exit(main())
