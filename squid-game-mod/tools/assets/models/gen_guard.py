#!/usr/bin/env python3
"""Generate the guard model: geo json, texture, animations."""
from __future__ import annotations

from common import *  # noqa: F401,F403
import guard_model as gm
import guard_tex as gt


def main(with_anims: bool = True) -> None:
    ensure_dirs()
    mb = gm.build_model()
    mb.geo.write(str(GEO_DIR / "guard.geo.json"))
    cv = gt.paint_guard(mb)
    cv.save(str(TEX_DIR / "guard.png"))
    if with_anims:
        try:
            import guard_anim as ga
        except ImportError:
            print("guard_anim not available yet - skipping animations")
        else:
            ga.build_and_write(mb)
    print(f"guard: {len(mb.geo.bones)} bones, texture {mb.packer.used_fraction():.0%} used")


if __name__ == "__main__":
    main()
