"""Campaign engine generator: quests, dialogs, NPCs, blueprints, location objectives, HUD and journal.

Content modules (pmgen/dp/content/*.py) register specs with the Registry below; generate() then
writes all functions, dialogs, advancements, predicates and tags. Everything that changes campaign
state goes through the quest functions, which are guarded so repeated or late triggers are harmless.
"""
from __future__ import annotations

import hashlib
import struct
from dataclasses import dataclass, field

from . import NS, adv, dialog, fid, fn, predicate, snbt, tag, tellraw, xyz

# ------------------------------------------------------------------------------------------------
# Specs
# ------------------------------------------------------------------------------------------------


@dataclass
class Quest:
    id: str
    chapter: int
    title: str
    objective: str                     # short HUD text
    detail: str                        # journal text
    prereq: list = field(default_factory=list)
    auto: bool = True                  # auto-activate once prerequisites are complete
    main: bool = True                  # main path (drives HUD / chapter)
    target: str | None = None          # POI name for the objective waypoint
    progress_max: int = 0              # show "n/max" on the HUD from pm.qp <id>
    on_activate: list = field(default_factory=list)
    on_complete: list = field(default_factory=list)
    icon: str = "minecraft:map"
    frame: str = "task"
    hint: str = ""                     # extra help shown in the journal
    num: int = 0


@dataclass
class Choice:
    label: str
    run: list = field(default_factory=list)     # commands run as the player when chosen
    goto: str | None = None                      # next dialog id to show
    tooltip: str | None = None
    code: int = 0


@dataclass
class Dlg:
    id: str                                      # e.g. "npc/odile/c1_intro"
    title: str
    body: list                                   # paragraphs: str or component dict/list
    choices: list = field(default_factory=list)
    speaker: str | None = None                   # NPC id, used for the header colour
    exit_label: str | None = "Leave"
    columns: int = 1
    num: int = 0


@dataclass
class NPC:
    id: str
    name: str
    role: str                                    # description shown under the name
    color: str                                   # chat colour
    skin: str                                    # default skin state (texture suffix)
    model: str = "wide"
    places: list = field(default_factory=list)   # [(condition-prefix or "", poi-name)] first match wins
    talk: list = field(default_factory=list)     # [(condition-prefix or "", dialog id or command)] first match wins
    present: str = ""                            # execute-condition prefix for existence ("" = always)
    held: str | None = None                      # item id held in main hand
    pose: str = "standing"
    body: bool = True                            # False = prop: interaction + floating label only
    label: str | None = None                     # prop label text
    size: tuple = (0.9, 1.95)                    # interaction width, height
    num: int = 0


@dataclass
class Part:
    pos: tuple
    accept: list                                 # block ids or '#tag'
    ghost: str                                   # block state shown as the ghost


@dataclass
class Blueprint:
    id: str
    quest: str
    parts: list
    center: tuple
    radius: int = 16
    on_complete: list = field(default_factory=list)


@dataclass
class Area:
    id: str
    box: list                                    # [x1,y1,z1,x2,y2,z2]


class Registry:
    def __init__(self):
        self.quests: list[Quest] = []
        self.dialogs: dict[str, Dlg] = {}
        self.npcs: list[NPC] = []
        self.blueprints: list[Blueprint] = []
        self.areas: dict[str, Area] = {}
        self.location_hooks: list[tuple] = []    # (area id, condition prefix, commands) run as player each second
        self.slow_hooks: list[str] = []          # commands run once per second (world context)
        self.load_hooks: list[str] = []
        self.world_init: list[str] = []
        self.player_first_join: list[str] = []
        self.player_rejoin: list[str] = []
        self.block_use: list[tuple] = []         # (id, pos, commands-as-player)
        self.functions: dict[str, list] = {}     # extra hand-written functions path -> lines
        self._next_code = 1000

    # -- registration helpers -------------------------------------------------------------------
    def quest(self, q: Quest) -> Quest:
        q.num = len(self.quests) + 1
        self.quests.append(q)
        return q

    def dlg(self, d: Dlg) -> Dlg:
        if d.id in self.dialogs:
            raise KeyError(d.id)
        d.num = len(self.dialogs) + 1
        for c in d.choices:
            c.code = self._next_code
            self._next_code += 1
        self.dialogs[d.id] = d
        return d

    def npc(self, n: NPC) -> NPC:
        n.num = len(self.npcs) + 1
        self.npcs.append(n)
        return n

    def blueprint(self, b: Blueprint) -> Blueprint:
        self.blueprints.append(b)
        return b

    def area(self, a: Area) -> Area:
        self.areas[a.id] = a
        return a

    def func(self, path: str, lines: list) -> str:
        if path in self.functions:
            raise KeyError(path)
        self.functions[path] = lines
        return fid(path)

    def q(self, qid: str) -> Quest:
        for x in self.quests:
            if x.id == qid:
                return x
        raise KeyError(qid)


R = Registry()


# ------------------------------------------------------------------------------------------------
# Helpers used by content
# ------------------------------------------------------------------------------------------------

def uuid_for(key: str) -> tuple[list[int], str]:
    """Deterministic UUID (as int-array and string form) for a unique story entity."""
    h = bytearray(hashlib.md5(("palemeridian:" + key).encode()).digest())
    h[6] = (h[6] & 0x0F) | 0x30   # version 3 (name based)
    h[8] = (h[8] & 0x3F) | 0x80
    ints = list(struct.unpack(">iiii", bytes(h)))
    hx = h.hex()
    return ints, f"{hx[0:8]}-{hx[8:12]}-{hx[12:16]}-{hx[16:20]}-{hx[20:32]}"


def uuid_nbt(key: str) -> str:
    ints, _ = uuid_for(key)
    return "[I;" + ",".join(str(i) for i in ints) + "]"


def uuid_str(key: str) -> str:
    return uuid_for(key)[1]


def active(qid: str) -> str:
    return f"if score {qid} pm.q matches 1"


def done(qid: str) -> str:
    return f"if score {qid} pm.q matches 2"


def not_done(qid: str) -> str:
    return f"unless score {qid} pm.q matches 2"


def complete(qid: str) -> str:
    return f"function {fid('q/' + qid + '/complete')}"


def activate(qid: str) -> str:
    return f"function {fid('q/' + qid + '/activate')}"


def show(dlg_id: str) -> str:
    return f"function {fid('dlg/show/' + dlg_id)}"


def announce(text: str, color: str = "gray", italic: bool = True) -> str:
    return tellraw("@a", {"text": text, "color": color, "italic": italic})


def actionbar(target: str, text: str, color: str = "gray") -> str:
    return f"title {target} actionbar {snbt({'text': text, 'color': color})}"


# ------------------------------------------------------------------------------------------------
# Generation
# ------------------------------------------------------------------------------------------------

def _gen_quests() -> None:
    qs = R.quests
    ids = {q.id for q in qs}
    for q in qs:
        for p in q.prereq:
            if p not in ids:
                raise KeyError(f"{q.id} prereq {p} unknown")
    # activation / completion
    for q in qs:
        act = [
            f"execute unless score {q.id} pm.q matches 0 run return fail",
            f"scoreboard players set {q.id} pm.q 1",
        ]
        if q.progress_max:
            act.append(f"execute unless score {q.id} pm.qp matches 0.. run scoreboard players set {q.id} pm.qp 0")
        if q.main:
            act.append(tellraw("@a", [{"text": "» ", "color": "dark_aqua"}, {"text": q.title, "color": "aqua", "bold": True},
                                      {"text": " — " + q.objective, "color": "gray"}]))
            act.append("playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0")
        act += q.on_activate
        act += [f"function {fid('hud/refresh')}"]
        fn(f"q/{q.id}/activate", act, header=f"Quest {q.id}: {q.title} — activate (idempotent)")
        comp = [
            f"execute unless score {q.id} pm.q matches 1 run return fail",
            f"scoreboard players set {q.id} pm.q 2",
            f"advancement grant @a only {fid('journal/' + q.id.replace('.', '/'))}",
        ]
        if q.main:
            comp.append("scoreboard players add #rev pm.world 1")
            comp.append(tellraw("@a", [{"text": "✔ ", "color": "green"}, {"text": q.title, "color": "green"}]))
            comp.append("playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9")
        comp += q.on_complete
        comp += [f"function {fid('q/_advance')}", f"function {fid('hud/refresh')}"]
        fn(f"q/{q.id}/complete", comp, header=f"Quest {q.id}: {q.title} — complete (idempotent)")
    # auto advance
    adv_lines = []
    for q in qs:
        if not q.auto:
            continue
        cond = " ".join(done(p) for p in q.prereq)
        adv_lines.append(f"execute if score {q.id} pm.q matches 0 {cond} run {activate(q.id)}".replace("  ", " "))
    fn("q/_advance", adv_lines, header="Activate every auto quest whose prerequisites are complete")
    # hud: pick latest active main quest
    hud = [f"scoreboard players set #cur pm.world 0"]
    for q in reversed([x for x in qs if x.main]):
        hud.append(f"execute if score #cur pm.world matches 0 if score {q.id} pm.q matches 1 run function {fid('q/' + q.id + '/hud')}")
    hud.append(f"execute if score #cur pm.world matches 0 run function {fid('hud/none')}")
    fn("hud/refresh", hud, header="Recompute the objective HUD (bossbar + waypoint) from quest state")
    for q in qs:
        lines = [f"scoreboard players set #cur pm.world {q.num}"]
        name = [{"text": q.title + ": ", "color": "aqua"}, {"text": q.objective, "color": "white"}]
        if q.progress_max:
            name.append({"text": " (", "color": "gray"})
            name.append({"score": {"name": q.id, "objective": "pm.qp"}, "color": "gray"})
            name.append({"text": f"/{q.progress_max})", "color": "gray"})
            lines.append(f"bossbar set {NS}:objective max {q.progress_max}")
            lines.append(f"execute store result bossbar {NS}:objective value run scoreboard players get {q.id} pm.qp")
        else:
            lines.append(f"bossbar set {NS}:objective max 1")
            lines.append(f"bossbar set {NS}:objective value 0")
        lines.append(f"bossbar set {NS}:objective name {snbt(name)}")
        lines.append(f"bossbar set {NS}:objective players @a[scores={{pm.optbar=1}}]")
        lines.append(f"bossbar set {NS}:objective visible true")
        if q.target:
            lines.append(f"function {fid('hud/wp_at')} {{poi:\"{q.target}\"}}")
        else:
            lines.append(f"function {fid('hud/wp_hide')}")
        fn(f"q/{q.id}/hud", lines)
    # journal advancements (per quest), parent chains by chapter
    chapter_titles = {0: "Prologue: The Letter", 1: "I · Hollin, Unremembered", 2: "II · The Heartwood", 3: "III · The Glassworks",
                      4: "IV · The Meridian", 5: "Epilogue", 9: "Side Paths"}
    adv("journal/root", {
        "display": {"icon": {"id": "minecraft:writable_book"}, "title": "Field Journal",
                    "description": "Pale Meridian — chart what the fog forgot.",
                    "background": "minecraft:block/pale_oak_planks", "show_toast": False, "announce_to_chat": False},
        "criteria": {"start": {"trigger": "minecraft:tick"}},
    })
    last_by_chapter: dict[int, str] = {}
    for q in qs:
        path = "journal/" + q.id.replace(".", "/")
        ch = q.chapter if q.main else 9
        parent = last_by_chapter.get(ch, fid("journal/root"))
        adv(path, {
            "parent": parent,
            "display": {
                "icon": {"id": q.icon},
                "title": q.title,
                "description": q.detail if len(q.detail) < 180 else q.detail[:177] + "...",
                "frame": q.frame,
                "show_toast": True,
                "announce_to_chat": False,
                "hidden": True,
            },
            "criteria": {"done": {"trigger": "minecraft:impossible"}},
        })
        last_by_chapter[ch] = fid(path)
    # journal sync for a player
    sync = []
    for q in qs:
        sync.append(f"execute if score {q.id} pm.q matches 2 run advancement grant @s only {fid('journal/' + q.id.replace('.', '/'))}")
    fn("journal/sync", sync, header="Grant the journal entries of every completed quest to this player (late joiners, rejoins)")
    # journal "current objective" dialogs
    cur = []
    for q in qs:
        body = [{"text": q.objective, "color": "white"}]
        if q.detail:
            body.append({"text": q.detail, "color": "gray"})
        if q.hint:
            body.append({"text": "Hint: " + q.hint, "color": "dark_aqua", "italic": True})
        dialog(f"journal/obj/{q.id.replace('.', '_')}", {
            "type": "minecraft:notice",
            "title": {"text": q.title, "color": "aqua"},
            "external_title": {"text": "Objective"},
            "body": [{"type": "minecraft:plain_message", "contents": b, "width": 300} for b in body],
            "action": {"label": {"text": "Back to the journal"}, "action": {"type": "minecraft:run_command", "command": "/trigger pm.ui set 1"}},
        })
        cur.append(f"execute if score #cur pm.world matches {q.num} run return run dialog show @s {fid('journal/obj/' + q.id.replace('.', '_'))}")
    cur.append(f"dialog show @s {fid('journal/free_play')}")
    fn("journal/current", cur)


def _choice_action(c: Choice) -> dict:
    return {"type": "minecraft:run_command", "command": f"/trigger pm.talk set {c.code}"}


def _body_component(p):
    if isinstance(p, str):
        return {"text": p}
    return p


def _gen_dialogs() -> None:
    npc_colors = {n.id: n.color for n in R.npcs}
    npc_names = {n.id: n.name for n in R.npcs}
    for d in R.dialogs.values():
        title = {"text": d.title}
        if d.speaker:
            title = [{"text": npc_names.get(d.speaker, d.speaker), "color": npc_colors.get(d.speaker, "white"), "bold": True},
                     {"text": "  ·  " + d.title, "color": "gray"}] if d.title else \
                    {"text": npc_names.get(d.speaker, d.speaker), "color": npc_colors.get(d.speaker, "white"), "bold": True}
        body = [{"type": "minecraft:plain_message", "contents": _body_component(p), "width": 320} for p in d.body]
        if d.choices:
            obj = {
                "type": "minecraft:multi_action",
                "title": title,
                "body": body,
                "columns": d.columns,
                "actions": [dict({"label": {"text": c.label}, "width": 300, "action": _choice_action(c)},
                                 **({"tooltip": {"text": c.tooltip}} if c.tooltip else {})) for c in d.choices],
                "can_close_with_escape": True,
                "pause": False,
                "after_action": "close",
            }
            if d.exit_label:
                obj["exit_action"] = {"label": {"text": d.exit_label}, "width": 200}
        else:
            obj = {
                "type": "minecraft:notice",
                "title": title,
                "body": body,
                "can_close_with_escape": True,
                "pause": False,
                "after_action": "close",
                "action": {"label": {"text": d.exit_label or "Close"}, "width": 200},
            }
        dialog(d.id, obj)
        fn(f"dlg/show/{d.id}", [
            f"scoreboard players set @s pm.dctx {d.num}",
            "scoreboard players enable @s pm.talk",
            f"dialog show @s {fid(d.id)}",
        ])
        for c in d.choices:
            lines = [f"execute unless score @s pm.dctx matches {d.num} run return fail",
                     "scoreboard players set @s pm.dctx 0"]
            lines += c.run
            if c.goto:
                if c.goto not in R.dialogs:
                    raise KeyError(f"dialog {d.id} choice goto unknown {c.goto}")
                lines.append(show(c.goto))
            fn(f"dlg/c/{c.code}", lines, header=f"{d.id}: {c.label}")
    fn("dlg/handle", [
        "scoreboard players operation #code pm.tmp = @s pm.talk",
        "scoreboard players set @s pm.talk 0",
        "scoreboard players enable @s pm.talk",
        "execute unless score #code pm.tmp matches 1000..99999 run return fail",
        "execute store result storage palemeridian:tmp code int 1 run scoreboard players get #code pm.tmp",
        f"function {fid('dlg/_call')} with storage palemeridian:tmp",
    ], header="Dispatch a dialog choice (pm.talk trigger). Choices validate their dialog context.")
    codes = sorted(c.code for d in R.dialogs.values() for c in d.choices)
    fn("dlg/_call", [
        f"$execute if score #code pm.tmp matches {codes[0] if codes else 1000}..{codes[-1] if codes else 1000} run function {NS}:dlg/c/$(code)",
    ])


def _gen_npcs() -> None:
    for n in R.npcs:
        body_uuid = uuid_nbt(f"npc:{n.id}:body")
        int_uuid = uuid_nbt(f"npc:{n.id}:int")
        body = uuid_str(f"npc:{n.id}:body")
        inter = uuid_str(f"npc:{n.id}:int")
        held = f',equipment:{{mainhand:{{id:"{n.held}",count:1}}}}' if n.held else ""
        # spawn at a POI given via macro args (x y z yaw)
        label_uuid = uuid_nbt(f"npc:{n.id}:label")
        label = uuid_str(f"npc:{n.id}:label")
        w, h = n.size
        spawn = []
        if n.body:
            spawn.append(
                f"$execute unless entity {body} run summon minecraft:mannequin $(x) $(y) $(z) {{UUID:{body_uuid},Rotation:[$(yaw)f,0f],"
                f"profile:{{texture:\"{NS}:entity/npc/{n.id}_{n.skin}\",model:\"{n.model}\"}},immovable:1b,Invulnerable:1b,"
                f"CustomName:{snbt({'text': n.name, 'color': n.color})},CustomNameVisible:1b,description:{snbt({'text': n.role, 'color': 'gray', 'italic': True})},"
                f"pose:\"{n.pose}\",Tags:[\"pm.npc\",\"pm.npc.{n.id}\"]{held}}}")
            spawn.append(f"$tp {body} $(x) $(y) $(z) $(yaw) 0")
        else:
            spawn.append(
                f"$execute unless entity {label} run summon minecraft:text_display $(x) $(y) $(z) {{UUID:{label_uuid},billboard:\"center\","
                f"text:{snbt({'text': n.label or n.name, 'color': n.color})},background:1073741824,Tags:[\"pm.label\",\"pm.label.{n.id}\"],"
                f"transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,{h + 0.35}f,0f],scale:[0.8f,0.8f,0.8f]}}}}")
            spawn.append(f"$tp {label} $(x) $(y) $(z)")
        spawn += [
            f"$execute unless entity {inter} run summon minecraft:interaction $(x) $(y) $(z) {{UUID:{int_uuid},width:{w}f,height:{h}f,response:1b,Tags:[\"pm.int\",\"pm.int.{n.id}\"]}}",
            f"scoreboard players set {inter} pm.nid {n.num}",
            f"$tp {inter} $(x) $(y) $(z)",
        ]
        if n.body:
            spawn.append(f"function {fid('npc/' + n.id + '/apply_skin')}")
        fn(f"npc/{n.id}/spawn_at", spawn)
        fn(f"npc/{n.id}/despawn", [f"kill {body}", f"kill {inter}", f"kill {label}"])
        # skin: stored per NPC in storage palemeridian:npc <id>
        fn(f"npc/{n.id}/apply_skin", [
            f"execute unless data storage palemeridian:npc {n.id} run data modify storage palemeridian:npc {n.id} set value \"{n.skin}\"",
            f"data modify storage palemeridian:tmp skin set value {{id:\"{n.id}\",model:\"{n.model}\"}}",
            f"data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc {n.id}",
            f"function {fid('npc/_skin')} with storage palemeridian:tmp skin",
        ])
        # placement: first matching place wins
        place = []
        for cond, poi_name in n.places:
            place.append(f"execute {cond} run return run function {fid('npc/' + n.id + '/_at')} {{poi:\"{poi_name}\"}}".replace("execute  run", "execute run"))
        place.append(f"function {fid('npc/' + n.id + '/despawn')}")
        fn(f"npc/{n.id}/place", place, header="Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)")
        fn(f"npc/{n.id}/_at", [
            f"$function {fid('poi/get')} {{name:\"$(poi)\"}}",
            f"function {fid('npc/' + n.id + '/spawn_at')} with storage palemeridian:tmp poi",
        ])
        talk = []
        for cond, what in n.talk:
            action = show(what) if not what.startswith("/") else what[1:]
            talk.append(f"execute {cond} run return run {action}".replace("execute  run", "execute run"))
        fn(f"npc/{n.id}/talk", talk or ["return fail"], header=f"{n.name}: choose the conversation for the current state")
    fn("npc/_skin", [
        f"$data modify entity @e[type=mannequin,tag=pm.npc.$(id),limit=1] profile set value {{texture:\"{NS}:entity/npc/$(id)_$(state)\",model:\"$(model)\"}}",
    ])
    # click dispatch
    fn("npc/_clicked", [
        f"advancement revoke @s only {fid('trigger/npc_click')}",
        "tag @s add pm.me",
        "scoreboard players set #clicked pm.tmp 0",
        f"execute as @e[type=interaction,tag=pm.int,distance=..8] if data entity @s interaction if function {fid('npc/_is_me')} run function {fid('npc/_take')}",
        "tag @s remove pm.me",
    ] + [f"execute if score #clicked pm.tmp matches {n.num} run return run function {fid('npc/' + n.id + '/talk')}" for n in R.npcs])
    fn("npc/_is_me", ["return run execute on target if entity @s[tag=pm.me]"])
    fn("npc/_take", ["scoreboard players operation #clicked pm.tmp = @s pm.nid", "data remove entity @s interaction"])
    adv("trigger/npc_click", {
        "criteria": {"click": {"trigger": "minecraft:player_interacted_with_entity", "conditions": {
            "entity": [{"condition": "minecraft:entity_properties", "entity": "this",
                        "predicate": {"minecraft:entity_type": "minecraft:interaction", "minecraft:nbt": "{Tags:[\"pm.int\"]}"}}]}}},
        "rewards": {"function": fid("npc/_clicked")},
    })
    # maintenance: called each second; place NPCs near players only
    maint = []
    for n in R.npcs:
        maint.append(f"function {fid('npc/' + n.id + '/maintain')}")
        lines = []
        for cond, poi_name in n.places:
            pass
        fn(f"npc/{n.id}/maintain", [f"function {fid('npc/' + n.id + '/place')}"])
    fn("npc/_maintain_all", maint)


def _gen_blueprints() -> None:
    for bp in R.blueprints:
        lines = [f"scoreboard players set #bp pm.tmp 0"]
        for i, p in enumerate(bp.parts):
            key = f"bp:{bp.id}:{i}"
            g = uuid_str(key)
            gn = uuid_nbt(key)
            accept_tag = f"#{NS}:bp/{bp.id}_{i}"
            tag("block", NS, f"bp/{bp.id}_{i}", p.accept)
            x, y, z = p.pos
            ghost_state = _state_snbt(p.ghost)
            lines.append(f"execute if block {x} {y} {z} {accept_tag} run scoreboard players add #bp pm.tmp 1")
            lines.append(f"execute if block {x} {y} {z} {accept_tag} run kill {g}")
            lines.append(f"execute unless block {x} {y} {z} {accept_tag} unless entity {g} run summon minecraft:block_display {x} {y} {z} "
                         f"{{UUID:{gn},block_state:{ghost_state},Glowing:1b,glow_color_override:9764863,"
                         f"brightness:{{sky:15,block:15}},transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],"
                         f"translation:[0.1f,0.1f,0.1f],scale:[0.8f,0.8f,0.8f]}},Tags:[\"pm.ghost\",\"pm.ghost.{bp.id}\"]}}")
        lines.append(f"scoreboard players operation {bp.quest} pm.qp = #bp pm.tmp")
        lines.append(f"execute if score #bp pm.tmp matches {len(bp.parts)}.. run function {fid('bp/' + bp.id + '/done')}")
        fn(f"bp/{bp.id}/check", lines, header=f"Blueprint {bp.id}: count placed parts, keep ghosts in sync")
        done_lines = [f"kill @e[type=block_display,tag=pm.ghost.{bp.id}]"] + bp.on_complete
        fn(f"bp/{bp.id}/done", done_lines)
        cx, cy, cz = bp.center
        R.slow_hooks.append(f"execute if score {bp.quest} pm.q matches 1 positioned {cx} {cy} {cz} if entity @a[distance=..{bp.radius}] run function {fid('bp/' + bp.id + '/check')}")


def _state_snbt(state: str) -> str:
    from ..structure import parse
    bs = parse(state)
    props = ",".join(f'{k}:"{v}"' for k, v in bs.props)
    return f'{{Name:"{bs.name}"' + (f",Properties:{{{props}}}" if props else "") + "}"


def _gen_areas() -> None:
    for a in R.areas.values():
        x1, y1, z1, x2, y2, z2 = a.box
        predicate(f"area/{a.id}", {
            "condition": "minecraft:entity_properties", "entity": "this",
            "predicate": {"minecraft:location": {"position": {
                "x": {"min": x1, "max": x2 + 1}, "y": {"min": y1, "max": y2 + 1}, "z": {"min": z1, "max": z2 + 1}}}},
        })
    for area_id, cond, cmds in R.location_hooks:
        if area_id not in R.areas:
            raise KeyError(area_id)
    # generated per hook: run as players inside the area, gated by a world condition
    for i, (area_id, cond, cmds) in enumerate(R.location_hooks):
        p = f"loc/h{i}"
        fn(p, cmds, header=f"location hook: {area_id}")
        R.slow_hooks.append(f"execute {cond} as @a[predicate={fid('area/' + area_id)}] at @s run function {fid(p)}".replace("execute  as", "execute as"))


def _gen_block_use() -> None:
    for bid, pos, cmds in R.block_use:
        x, y, z = pos
        path = f"trigger/use/{bid}"
        adv(path, {
            "criteria": {"use": {"trigger": "minecraft:any_block_use", "conditions": {
                "location": [{"condition": "minecraft:location_check",
                              "predicate": {"position": {"x": {"min": x, "max": x}, "y": {"min": y, "max": y}, "z": {"min": z, "max": z}}}}]}}},
            "rewards": {"function": fid(f"use/{bid}")},
        })
        fn(f"use/{bid}", [f"advancement revoke @s only {fid(path)}"] + cmds)


def generate() -> None:
    _gen_areas()
    _gen_block_use()
    _gen_blueprints()
    _gen_quests()
    _gen_npcs()
    _gen_dialogs()
    for path, lines in R.functions.items():
        fn(path, lines)
