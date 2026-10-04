#!/usr/bin/env python3
"""Regenerate every contestant / guard asset (geo, animations, 128x128 textures), then validate.

    python3 tools/assets/models/gen_all.py              # generate + validate
    python3 tools/assets/models/gen_all.py --preview    # + contact sheets in tools/assets/models/preview/

Outputs (under src/main/resources/assets/squidgame/):
    geo/entity/{contestant,guard}.geo.json
    animations/entity/{contestant,guard}.animation.json
    textures/entity/{contestant,guard}.png
plus tools/assets/models/contestant_bones.json (+ *_anim_meta.json used by the validator).
Everything is deterministic (fixed hash-based noise, no RNG state)."""
from __future__ import annotations

import subprocess
import sys
import time

from common import *  # noqa: F401,F403
import gen_contestant
import gen_guard


def main() -> int:
    t0 = time.time()
    ensure_dirs()
    gen_contestant.main()
    gen_guard.main()
    import validate_models
    rc = validate_models.main()
    if "--preview" in sys.argv:
        py = sys.executable
        for model in ("contestant", "guard"):
            subprocess.check_call([py, str(HERE / "preview.py"), model, "--static", "--all"])
    print(f"gen_all finished in {time.time() - t0:.1f}s (validation {'OK' if rc == 0 else 'FAILED'})")
    return rc


if __name__ == "__main__":
    sys.exit(main())
