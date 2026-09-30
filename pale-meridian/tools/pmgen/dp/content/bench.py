"""Optional: Tamsin's survey benchmarks — eight marked stones along her route, each with a field note."""
from __future__ import annotations

from .. import fid, snbt, tellraw
from ..engine import R, Dlg, NPC, Quest, activate, complete

NOTES = [
    "S.B. 1 — the Vell rim. The old road is still here under the fog. So am I. Day one.",
    "S.B. 2 — Hollin. They keep one lamp lit at the gate. Nobody can tell me why that one.",
    "S.B. 3 — the ferry steps. The lake doesn't reflect anything. I checked twice, then felt silly, then checked again.",
    "S.B. 4 — Aldercross. The trees watch you back. The keeper says it's rude to stare. He's not wrong.",
    "S.B. 5 — the Glassworks. The kilns are cold, but the glass still rings when I tap it. Like it's waiting.",
    "S.B. 6 — the Deepcut, upper gallery. Can't take a sighting: there's nothing to sight. I suspect that's the point.",
    "S.B. 7 — the Meridian causeway. The island keeps folding me back to shore. It isn't ready for me. Or I'm not for it.",
    "S.B. 8 — the fen chapel. 'Look at all of it, always.' Somebody stopped looking. I'd like to know who.",
]


def register() -> None:
    R.quest(Quest("s.bench", 9, "Tamsin's Benchmarks", "Find the survey benchmarks Tamsin left along her route.",
                  "Low stone posts marked S.B. Every Chartered surveyor leaves them. Tamsin left eight.",
                  auto=False, main=False, progress_max=len(NOTES), icon="minecraft:chiseled_stone_bricks",
                  hint="One wherever her route took her: the rim, the village (twice), the orchards, the kilns, the mine, the causeway, the fen.",
                  on_complete=[f"function {fid('bench/all')}"]))
    for i, note in enumerate(NOTES, start=1):
        R.npc(NPC(f"bench_{i}", "Survey benchmark", "", "gold", "none", body=False, label="✎ Survey benchmark", size=(1.0, 1.2),
                  places=[("", f"bench.{i}")], talk=[("", f"/function {fid(f'bench/read/{i}')}")]))
        R.dlg(Dlg(f"prop/bench/{i}", f"Survey benchmark No. {i}", [
            {"text": "Scratched on the plate in Tamsin's hand:", "color": "gray", "italic": True},
            {"text": note, "color": "white"},
        ], exit_label="Close"))
        R.func(f"bench/read/{i}", [
            f"execute unless score #bench.{i} pm.world matches 1 run function {fid(f'bench/first/{i}')}",
            f"function {fid(f'dlg/show/prop/bench/{i}')}",
        ])
        R.func(f"bench/first/{i}", [
            f"scoreboard players set #bench.{i} pm.world 1",
            "scoreboard players add #bench.count pm.world 1",
            activate("s.bench"),
            "scoreboard players operation s.bench pm.qp = #bench.count pm.world",
            "playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.8 1.2",
            tellraw("@a", [{"text": "Benchmark found ", "color": "gold"}, {"score": {"name": "#bench.count", "objective": "pm.world"}, "color": "gold"},
                           {"text": f"/{len(NOTES)}", "color": "gold"}]),
            f"execute if score #bench.count pm.world matches {len(NOTES)}.. run {complete('s.bench')}",
        ])
    glass = ("minecraft:spyglass[minecraft:custom_name=" + snbt({"text": "Surveyor's Spyglass", "italic": False, "color": "gold"}) +
             ",minecraft:lore=[" + snbt({"text": "Tamsin's spare. Her initials are scratched by the eyepiece.", "italic": False, "color": "gray"}) + "]"
             ",minecraft:custom_data={pm:{item:\"surveyors_spyglass\"}}]")
    R.func("bench/all", [
        tellraw("@a", {"text": "Eight benchmarks: the whole of Tamsin's route, charted. Whoever finds a survey this complete deserves the good spyglass.", "color": "gray", "italic": True}),
        f"give @a {glass} 1",
    ])
    R.load_hooks.append("execute unless score #bench.count pm.world matches 0.. run scoreboard players set #bench.count pm.world 0")
