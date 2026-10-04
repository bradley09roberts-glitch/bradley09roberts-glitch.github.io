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

import json
import subprocess
import sys
import time

from common import *  # noqa: F401,F403
from lib import jsonfmt
import gen_contestant
import gen_guard

REFERENCE_SPEEDS = {"walk": 2.4, "run": 6.0}     # blocks/s, docs/ASSET_CONTRACT.md


def write_gait_info() -> None:
    out = {
        "_comment": "Ground speed (blocks/s, at renderer scale 0.9375) at which the stance feet of each locomotion "
                    "cycle stay planted when played at 1.0x. Suggested controller speed = actual_blocks_s / planted_blocks_s.",
    }
    for model in ("contestant", "guard"):
        try:
            meta = json.load(open(HERE / f"{model}_anim_meta.json"))
        except OSError:
            continue
        g = {}
        for name, d in meta.get("info", {}).items():
            if isinstance(d, dict) and "planted_blocks_s" in d:
                g[name] = dict(d)
                if name in REFERENCE_SPEEDS:
                    g[name]["contract_reference_blocks_s"] = REFERENCE_SPEEDS[name]
                    g[name]["speed_multiplier_at_reference"] = round(REFERENCE_SPEEDS[name] / d["planted_blocks_s"], 2)
        out[model] = g
    jsonfmt.write(str(HERE / "gait_info.json"), out)


def main() -> int:
    t0 = time.time()
    ensure_dirs()
    gen_contestant.main()
    gen_guard.main()
    write_gait_info()
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
