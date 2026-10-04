"""GUI / HUD textures: HUD icons + panel, dalgona minigame, marbles minigame."""
from __future__ import annotations

from common import Out
import gen_gui_dalgona
import gen_gui_hud
import gen_gui_marbles


def generate(out: Out) -> None:
    gen_gui_hud.generate(out)
    gen_gui_dalgona.generate(out)
    gen_gui_marbles.generate(out)


if __name__ == "__main__":
    o = Out()
    generate(o)
    print(f"wrote {len(o.written)} files")
