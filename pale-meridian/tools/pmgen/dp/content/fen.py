"""Optional: the drowned chapel in the Fen (the first Keeper's vow, the sluice puzzle, keepsake 10)."""
from __future__ import annotations

from ... import poi
from .. import fid, tellraw, xyz
from ..engine import R, Area, Dlg, NPC, Quest, activate, active, complete


def register() -> None:
    R.area(Area("fen_chapel", poi.get("fen.chapel")["box"]))
    R.quest(Quest("s.fen", 9, "The Drowned Chapel", "Drain the flooded crypt under the Fen chapel.",
                  "A half-sunk chapel stands in the western Fen. Its crypt is under water, behind a glass window.",
                  auto=False, main=False, target="fen.levers", icon="minecraft:lever",
                  hint="Three sluice levers on the south wall. The verse by the altar tells you which to open and which to keep shut."))
    R.location_hooks.append(("fen_chapel", "unless score s.fen pm.q matches 1..", [activate("s.fen")]))

    R.npc(NPC("vow", "The Vow", "", "white", "none", body=False, label="✎ The Vow of the First Keeper", size=(1.0, 1.4),
              places=[("", "fen.vow")], talk=[("", "prop/vow")]))
    R.dlg(Dlg("prop/vow", "The Vow of the First Keeper", [
        {"text": "Carved into the lectern, the letters filled with old gold:", "color": "gray", "italic": True},
        {"text": "\"I will look at all of it, always: the fields and the fen, the living and the lost. What is looked at is kept. What is kept is not lost.\"", "color": "white"},
        {"text": "Below, in a smaller hand: the Vale buried its dead here with their names said aloud, so the fen would remember them when the people could not.", "color": "gray"},
    ], exit_label="Close"))

    lv = poi.get("fen.levers")
    n, m, s = lv["north"], lv["middle"], lv["south"]
    crypt = poi.get("fen.crypt")
    x1, y1, z1, x2, y2, z2 = crypt["water"]
    R.func("fen/check", [
        f"execute if block {xyz(n)} minecraft:lever[powered=true] if block {xyz(m)} minecraft:lever[powered=false] "
        f"if block {xyz(s)} minecraft:lever[powered=true] run function {fid('fen/drain')}",
    ])
    R.func("fen/drain", [
        "execute if score #fen.drained pm.world matches 1 run return fail",
        "scoreboard players set #fen.drained pm.world 1",
        f"fill {x1} {y1} {z1} {x2} {y2} {z2} minecraft:air replace minecraft:water",
        *[f"setblock {x} {y} {z} minecraft:air destroy" for (x, y, z) in crypt["grate"]],
        f"playsound minecraft:block.water.ambient master @a {xyz(crypt)} 2 0.6",
        f"playsound minecraft:block.iron_door.open master @a {xyz(crypt)} 1 0.6",
        tellraw("@a", {"text": "Somewhere under the chapel, stone grinds on stone. Water rushes out into the fen, and the window into the crypt cracks and falls away.", "color": "gray", "italic": True}),
        complete("s.fen"),
    ])
    R.slow_hooks.append(f"execute if score s.fen pm.q matches 1 positioned {xyz(lv)} if entity @a[distance=..16] run function {fid('fen/check')}")
    for key, pos in (("north", n), ("middle", m), ("south", s)):
        R.location_hooks.append(("fen_chapel", active("s.fen"), [
            f"execute unless block {xyz(pos)} minecraft:lever run setblock {xyz(pos)} minecraft:lever[face=wall,facing=south,powered=false]"]))
    R.load_hooks.append(f"execute if score #fen.drained pm.world matches 1 run fill {x1} {y1} {z1} {x2} {y2} {z2} minecraft:air replace minecraft:water")
