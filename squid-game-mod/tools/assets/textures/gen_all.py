#!/usr/bin/env python3
"""Regenerate every texture, blockstate, model and the lang fragment (deterministic, re-runnable).

    python3 tools/assets/textures/gen_all.py              # write into src/main/resources/assets/squidgame/
    python3 tools/assets/textures/gen_all.py --validate   # ... and run the validator afterwards
    python3 tools/assets/textures/gen_all.py --preview    # ... and rebuild tools/assets/textures/preview/*.png
    python3 tools/assets/textures/gen_all.py --out /tmp/x # write somewhere else (determinism checks)
"""
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import Out  # noqa: E402
import gen_blocks  # noqa: E402
import gen_entity  # noqa: E402
import gen_gui  # noqa: E402
import gen_items  # noqa: E402
import gen_lang  # noqa: E402
import gen_machines  # noqa: E402
import gen_models  # noqa: E402

STAGES = [
    ("block textures", gen_blocks),
    ("machine textures", gen_machines),
    ("item textures", gen_items),
    ("gui / hud textures", gen_gui),
    ("entity textures", gen_entity),
    ("blockstates + models", gen_models),
    ("language fragment", gen_lang),
]


def run(out_root: str | None = None, lang_dir: str | None = None, quiet: bool = False) -> Out:
    out = Out(out_root, lang_dir)
    for label, mod in STAGES:
        t0 = time.time()
        n0 = len(out.written)
        mod.generate(out)
        if not quiet:
            print(f"  {label:<24} {len(out.written) - n0:>4} files  ({time.time() - t0:.1f}s)")
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--out", help="output assets root (default: src/main/resources/assets/squidgame)")
    ap.add_argument("--lang-out", help="directory for textures.json (default: tools/assets/lang)")
    ap.add_argument("--validate", action="store_true", help="run validate_textures.py afterwards")
    ap.add_argument("--preview", action="store_true", help="rebuild preview images afterwards")
    args = ap.parse_args()

    print("generating squidgame assets ...")
    out = run(args.out, args.lang_out)
    print(f"done: {len(out.written)} files written under {out.root}")

    rc = 0
    if args.validate:
        import validate_textures
        rc = validate_textures.main(["--root", str(out.root), "--lang", str(out.lang_dir / "textures.json")])
    if args.preview:
        import preview
        preview.main(["--root", str(out.root)])
    return rc


if __name__ == "__main__":
    sys.exit(main())
