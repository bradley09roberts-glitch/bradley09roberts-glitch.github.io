"""The Eleven: keepsakes of the lost miners, detected when they enter any player's inventory.

Each keepsake item carries custom_data {pm:{keepsake:N}}. An inventory_changed advancement per
keepsake records a world flag #k.N (shared by the whole party, never revoked) and plays its echo.
The optional quest s.eleven counts them; finding all eleven changes lines in either ending.
"""
from __future__ import annotations

from .. import adv, fid, snbt, tellraw
from ..engine import R, Quest, activate, complete

# (number, name, keepsake, echo line)
ELEVEN = [
    (1, "Tobin Vane", "brass compass", "The needle ignores north and points at the island. Tobin Vane led the crew down."),
    (2, "Col Hale", "carved apple token", "Col Hale carved apples for luck. He gave the best one away every summer."),
    (3, "Agnes Pell", "knitted scarf", "Agnes Pell darned this scarf more times than she wore it."),
    (4, "Wendel Tarn", "tin whistle", "Wendel Tarn whistled the Round all the way down the shaft, every shift."),
    (5, "Ruth Anning", "pressed flower", "Ruth Anning pressed a cornflower for somebody, and never got to give it."),
    (6, "Emory Dunn", "recipe card", "Emory Dunn wrote down his mother's recipe so it would outlive him. It did."),
    (7, "Silas Crane", "wire spectacles", "Silas Crane needed these to read the Keeper's orders. He read them twice."),
    (8, "Nell Farrow", "child's drawing", "Nell Farrow's daughter drew her with a lamp as big as the sun."),
    (9, "Hob Tulley", "bone dice", "Hob Tulley's dice were loaded. Everyone knew. Nobody minded."),
    (10, "Mae Ostrander", "hymn sheet", "Mae Ostrander sang in the chapel on Sundays and in the dark on weekdays."),
    (11, "Piet Lund", "lamp hook", "Piet Lund was a lamplighter's boy who went underground to light the way."),
]


def register() -> None:
    R.quest(Quest("s.eleven", 9, "The Eleven", "Find the keepsakes of the eleven lost miners.",
                  "Eleven people never came back from the Deepcut. Things they carried are still scattered through the Vale.",
                  prereq=["c2.lamp"], main=False, progress_max=11, icon="minecraft:candle",
                  hint="Keepsakes are hidden where each person lived or worked: homes, lofts, shelves, a tree hollow, a boathouse. The journal lists every name you have found.",
                  on_complete=[f"function {fid('keepsake/all_found')}"]))
    for n, name, thing, echo in ELEVEN:
        adv(f"trigger/keepsake/{n}", {
            "criteria": {"found": {"trigger": "minecraft:inventory_changed", "conditions": {
                "items": [{"predicates": {"minecraft:custom_data": f"{{pm:{{keepsake:{n}}}}}"}}]}}},
            "rewards": {"function": fid(f"keepsake/found/{n}")},
        })
        R.func(f"keepsake/found/{n}", [
            f"execute if score #k.{n} pm.world matches 1 run return fail",
            f"scoreboard players set #k.{n} pm.world 1",
            "scoreboard players add #k.count pm.world 1",
            activate("s.eleven"),
            "scoreboard players operation s.eleven pm.qp = #k.count pm.world",
            "playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 0.8 0.5",
            "particle minecraft:white_ash ~ ~1 ~ 0.6 0.8 0.6 0.01 60 normal",
            tellraw("@a", [{"text": "A name surfaces: ", "color": "gray", "italic": True},
                           {"text": name, "color": "white", "bold": True},
                           {"text": f"  ({thing})", "color": "dark_gray"}]),
            tellraw("@a", {"text": echo, "color": "gray", "italic": True}),
            f"execute if score #k.count pm.world matches 11.. run {complete('s.eleven')}",
        ])
    R.func("keepsake/all_found", [
        "scoreboard players set #k.all pm.world 1",
        tellraw("@a", {"text": "Eleven names. Every one of them carried something, and every one of them was carried home.", "color": "gray", "italic": True}),
        f"give @a minecraft:lantern[minecraft:custom_name={snbt({'text': 'Memorial Lantern', 'italic': False, 'color': 'gold'})},"
        f"minecraft:lore=[{snbt({'text': 'Eleven names are scratched around the base.', 'italic': False, 'color': 'gray'})}]] 1",
    ])
    # s.eleven starts either after Chapter 2 (auto, via prereq) or when the first keepsake is found
    R.load_hooks.append("execute unless score #k.count pm.world matches 0.. run scoreboard players set #k.count pm.world 0")


def eleven_journal() -> list[tuple[str, str]]:
    return [(f"#k.{n}", name) for n, name, _t, _e in ELEVEN]
