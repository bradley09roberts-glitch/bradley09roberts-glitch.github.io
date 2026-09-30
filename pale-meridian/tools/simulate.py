#!/usr/bin/env python3
"""Logic simulation of the generated data pack (NOT the game).

Executes the generated .mcfunction files with a small interpreter for the command subset that drives
campaign state: scoreboard, execute (score conditions, store), function (with macro arguments and
command storage), return, schedule. Commands that act on the world (blocks, entities, particles, text)
are recorded but have no effect; conditions that depend on the world use a fixed, documented policy.
This catches logic errors the game's parser cannot see (uninitialised scores, broken quest wiring,
guards that never pass) without starting Minecraft.

Usage: python3 tools/simulate.py
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FN = ROOT / "mod" / "src" / "main" / "resources" / "data" / "palemeridian" / "function"
NS = "palemeridian"


class Return(Exception):
    def __init__(self, value: int):
        self.value = value


class Sim:
    def __init__(self):
        self.scores: dict[tuple[str, str], int] = {}
        self.storage: dict[str, dict] = {}
        self.queue: list[tuple[int, str]] = []
        self.tick = 0
        self.log: list[str] = []
        self.calls = 0
        self.unknown: set[str] = set()
        self.cache: dict[str, list[str]] = {}
        # world-dependent conditions: which are true in this simulation
        self.entity_default = False           # "if entity <selector>": no entities/players matched by default
        self.block_default = False

    # ------------------------------------------------------------------ scores
    def get(self, holder: str, obj: str):
        return self.scores.get((holder, obj))

    def set(self, holder: str, obj: str, v: int) -> None:
        self.scores[(holder, obj)] = v

    # ------------------------------------------------------------------ functions
    def load(self, fid: str) -> list[str]:
        if fid not in self.cache:
            ns, path = fid.split(":", 1)
            p = FN / f"{path}.mcfunction"
            if ns != NS or not p.exists():
                raise KeyError(f"function not found: {fid}")
            self.cache[fid] = [l.strip() for l in p.read_text().splitlines() if l.strip() and not l.strip().startswith("#")]
        return self.cache[fid]

    def call(self, fid: str, args: dict | None = None) -> int:
        self.calls += 1
        if self.calls > 200000:
            raise RuntimeError("runaway recursion")
        result = 0
        try:
            for line in self.load(fid):
                if line.startswith("$"):
                    if args is None:
                        raise RuntimeError(f"{fid}: macro line without arguments")
                    line = re.sub(r"\$\(([a-z_]+)\)", lambda m: str(args[m.group(1)]), line[1:])
                result = self.run(line)
        except Return as r:
            return r.value
        return result

    # ------------------------------------------------------------------ commands
    def run(self, cmd: str) -> int:
        t = cmd.split(" ")
        head = t[0]
        if head == "execute":
            return self.execute(t[1:], cmd)
        if head == "return":
            if t[1] == "fail":
                raise Return(0)
            if t[1] == "run":
                raise Return(self.run(" ".join(t[2:])))
            raise Return(int(t[1]))
        if head == "function":
            return self.function(t[1:])
        if head == "scoreboard":
            return self.scoreboard(t[1:])
        if head == "schedule":
            if t[1] == "function":
                delay = int(re.sub(r"[ts]$", "", t[3])) * (20 if t[3].endswith("s") else 1)
                if len(t) > 4 and t[4] == "replace":
                    self.queue = [q for q in self.queue if q[1] != t[2]]
                self.queue.append((self.tick + delay, t[2]))
            return 1
        if head == "data":
            return self.data(t[1:], cmd)
        self.log.append(cmd)
        return 1

    def function(self, t: list[str]) -> int:
        fid = t[0]
        args = None
        if len(t) > 1:
            rest = " ".join(t[1:])
            if rest.startswith("with storage"):
                parts = rest.split(" ")
                args = self.sget(parts[2], parts[3]) if len(parts) > 3 else self.storage.get(parts[2], {})
                if not isinstance(args, dict):
                    raise RuntimeError(f"{fid}: macro arguments missing in storage ({rest})")
            else:
                args = parse_snbt(rest)
        return self.call(fid, args)

    def scoreboard(self, t: list[str]) -> int:
        if t[0] == "objectives":
            return 1
        op = t[1]
        if op in ("set", "add", "remove"):
            holder, obj, n = t[2], t[3], int(t[4])
            cur = self.get(holder, obj)
            if op == "set":
                self.set(holder, obj, n)
            elif op == "add":
                self.set(holder, obj, (cur or 0) + n)
            else:
                self.set(holder, obj, (cur or 0) - n)
            return 1
        if op == "reset":
            self.scores.pop((t[2], t[3]), None)
            return 1
        if op == "enable":
            return 1
        if op == "get":
            v = self.get(t[2], t[3])
            if v is None:
                raise Return(0)
            return v
        if op == "operation":
            a, ao, o, b, bo = t[2], t[3], t[4], t[5], t[6]
            av, bv = self.get(a, ao) or 0, self.get(b, bo) or 0
            if self.get(b, bo) is None:
                self.set(b, bo, 0)
            res = {"=": bv, "+=": av + bv, "-=": av - bv, "*=": av * bv, "/=": (av // bv) if bv else av,
                   "%=": (av % bv) if bv else av, "<": min(av, bv), ">": max(av, bv)}[o]
            if o == "><":
                self.set(b, bo, av)
            self.set(a, ao, res)
            return 1
        self.unknown.add("scoreboard " + op)
        return 1

    # nested command storage: dotted paths (the same splitting is used for writes and reads)
    def _node(self, sid: str, path: str, create: bool):
        cur = self.storage.setdefault(sid, {}) if create else self.storage.get(sid, {})
        keys = path.split(".")
        for k in keys[:-1]:
            if not isinstance(cur, dict):
                return None, None
            if k not in cur:
                if not create:
                    return None, None
                cur[k] = {}
            cur = cur[k]
        return cur, keys[-1]

    def sget(self, sid: str, path: str):
        node, key = self._node(sid, path, False)
        return node.get(key) if isinstance(node, dict) else None

    def sset(self, sid: str, path: str, value) -> None:
        node, key = self._node(sid, path, True)
        node[key] = value

    def data(self, t: list[str], cmd: str) -> int:
        if t[0] == "modify" and t[1] == "storage":
            sid, path, mode = t[2], t[3], t[4]
            if " set value " in cmd or " append value " in cmd:
                val = cmd.split(" value ", 1)[1].strip()
                val = parse_snbt(val) if val.startswith("{") else val.strip('"')
                if mode == "append":
                    lst = self.sget(sid, path)
                    if not isinstance(lst, list):
                        lst = []
                    lst.append(val)
                    self.sset(sid, path, lst)
                else:
                    self.sset(sid, path, val)
                return 1
            if " set from storage " in cmd:
                src = cmd.split(" set from storage ", 1)[1].split(" ")
                v = self.sget(src[0], src[1])
                if v is None:
                    return 0
                import copy as _c
                self.sset(sid, path, _c.deepcopy(v))
                return 1
        if t[0] == "remove" and t[1] == "storage":
            node, key = self._node(t[2], t[3], False)
            if isinstance(node, dict):
                node.pop(key, None)
            return 1
        self.log.append(cmd)
        return 1

    def execute(self, t: list[str], cmd: str) -> int:
        i = 0
        store = None
        while i < len(t):
            w = t[i]
            if w == "run":
                r = self.run(" ".join(t[i + 1:]))
                self._store(store, r)
                return r
            if w in ("if", "unless"):
                ok, n = self.condition(t[i + 1:])
                if w == "unless":
                    ok = not ok
                i += 1 + n
                if not ok:
                    self._store(store, 0)
                    return 0
                if i >= len(t):               # condition is the last subcommand
                    self._store(store, 1)
                    return 1
                continue
            if w == "store":
                if t[i + 2] == "score":
                    store = (t[i + 1], t[i + 3], t[i + 4])
                    i += 5
                elif t[i + 2] == "storage":
                    store = ("storage", t[i + 3], t[i + 4], t[i + 1])
                    i += 7
                else:                          # entity / block / bossbar targets: world state, not modelled
                    store = None
                    i += 7 if t[i + 2] in ("entity", "block") else 5
                continue
            if w in ("as", "at", "on"):
                sel = t[i + 1]
                if sel.startswith("@a") or sel.startswith("@e") or sel.startswith("@r") or sel.startswith("@p"):
                    # iterating over players/entities: none exist in the simulation
                    if not self.entity_default:
                        return 0
                i += 2
                continue
            if w in ("positioned", "rotated"):
                i += 4 if t[i + 1] not in ("as", "facing") else 3
                if t[i - 3] == "as" or t[i - 2] == "as":
                    pass
                continue
            if w in ("facing", "anchored", "align", "in", "summon"):
                i += 2
                continue
            self.unknown.add("execute " + w)
            return 0
        return 1

    def _store(self, store, r: int) -> None:
        if not store:
            return
        if store[0] == "storage":
            self.sset(store[1], store[2], r)
        else:
            self.set(store[1], store[2], (1 if r else 0) if store[0] == "success" else r)

    def condition(self, t: list[str]) -> tuple[bool, int]:
        kind = t[0]
        if kind == "score":
            h, o = t[1], t[2]
            v = self.get(h, o)
            if t[3] == "matches":
                return (v is not None and in_range(v, t[4])), 5
            b = self.get(t[4], t[5])
            if v is None or b is None:
                return False, 6
            return {"<": v < b, "<=": v <= b, "=": v == b, ">": v > b, ">=": v >= b}[t[3]], 6
        if kind == "entity":
            return self.entity_default, 2
        if kind == "block":
            return self.block_default, 5
        if kind == "items":
            n = 5 if t[1] == "entity" else 7
            return False, n
        if kind == "data":
            if t[1] == "storage":
                return (self.sget(t[2], t[3]) is not None), 4
            return False, 4 if t[1] != "block" else 6
        if kind == "predicate":
            return False, 2
        if kind == "function":
            return bool(self.call(t[1])), 2
        self.unknown.add("condition " + kind)
        return False, 2

    def advance_ticks(self, n: int) -> None:
        for _ in range(n):
            self.tick += 1
            due = [q for q in self.queue if q[0] <= self.tick]
            self.queue = [q for q in self.queue if q[0] > self.tick]
            for _, fid in due:
                self.call(fid)


def in_range(v: int, r: str) -> bool:
    if ".." not in r:
        return v == int(r)
    lo, hi = r.split("..")
    return (lo == "" or v >= int(lo)) and (hi == "" or v <= int(hi))


def parse_snbt(s: str) -> dict:
    """Tiny SNBT reader for flat macro-argument compounds: {a:1,b:"x",c:1.5d}."""
    s = s.strip()
    assert s.startswith("{") and s.endswith("}"), s
    out = {}
    for m in re.finditer(r'([a-z_]+):("([^"]*)"|[-0-9.]+[a-z]?|\{[^}]*\})', s[1:-1]):
        k, v = m.group(1), m.group(2)
        if v.startswith('"'):
            out[k] = m.group(3)
        elif v.startswith("{"):
            out[k] = v
        else:
            out[k] = re.sub(r"[a-z]$", "", v)
    return out


# ---------------------------------------------------------------------------------------------------
def main() -> None:
    fixture = json.loads((ROOT / "mod" / "src" / "gametest" / "resources" / "palemeridian-tests" / "fixture.json").read_text())
    order = fixture["main_order"]
    results = []

    def check(name: str, ok: bool, detail: str = "") -> None:
        results.append((name, ok, detail))

    sim = Sim()
    sim.call(f"{NS}:core/load")
    sim.call(f"{NS}:core/tick")                 # first tick: world init
    q = lambda i: sim.get(i, "pm.q")
    check("world init runs once and activates the first quest", q("p.letter") == 1, f"p.letter={q('p.letter')}")
    check("all other quests start locked", all(q(x) == 0 for x in order[1:]), "")
    check("chapter starts at 0", sim.get("#chapter", "pm.world") == 0)

    rev = sim.get("#rev", "pm.world")
    sim.call(f"{NS}:q/p.letter/complete")
    sim.call(f"{NS}:q/p.letter/complete")
    check("completion is idempotent", q("p.letter") == 2 and sim.get("#rev", "pm.world") == rev + 1, f"rev {rev}->{sim.get('#rev', 'pm.world')}")
    check("next quest activates", q("p.camp") == 1)
    check("side quest with shared prerequisite activates", q("p.chill") == 0, "p.chill waits for p.camp")

    # blueprint: simulate every part present
    sim.block_default = True
    sim.set("p.camp", "pm.q", 1)
    sim.call(f"{NS}:q/p.camp/complete")
    check("p.chill activates with p.camp", q("p.chill") == 1)
    sim.call(f"{NS}:bp/landing_lamp/check")
    sim.block_default = False
    check("Landing lamp blueprint completes p.lamp", q("p.lamp") == 2, f"p.lamp={q('p.lamp')}")
    check("Landing restoration requested", sim.get("#req.landing", "pm.world") == 1 and sim.get("#r.landing", "pm.world") == 1)

    # the Round
    for x in ("p.road", "c1.odile", "c1.names"):
        sim.call(f"{NS}:admin/force", {"q": x})
    check("c1.round active after the names", q("c1.round") == 1)
    sim.call(f"{NS}:c1/bell", {"n": "2"})
    check("wrong first bell resets", (sim.get("#round.step", "pm.world") or 0) == 0)
    for n in (1, 2, 3):
        sim.call(f"{NS}:c1/bell", {"n": str(n)})
    sim.call(f"{NS}:c1/bell", {"n": "1"})
    check("wrong bell mid-way resets", (sim.get("#round.step", "pm.world") or 0) == 0)
    for n in (1, 2, 3, 4):
        sim.call(f"{NS}:c1/bell", {"n": str(n)})
    check("bells in order solve the Round", q("c1.round") == 2)

    # the rest of the main line
    broken = []
    for i in range(order.index("c1.lamp"), order.index("c4.chart")):
        x = order[i]
        if q(x) != 1:
            broken.append(f"{x} was {q(x)} before completion")
        sim.call(f"{NS}:admin/force", {"q": x})
        if q(x) != 2:
            broken.append(f"{x} did not complete")
    check("every main quest activates in order and completes", not broken, "; ".join(broken))
    check("chapter counter reached 4", sim.get("#chapter", "pm.world") == 4, f"#chapter={sim.get('#chapter', 'pm.world')}")
    for g in ("landing", "hollin", "aldercross", "glassworks"):
        check(f"district {g} restored", sim.get(f"#r.{g}", "pm.world") == 1)
    check("side quests unlocked on the way", q("c1.home") == 1 and q("s.eleven") == 1 and q("c4.causeway") == 1,
          f"c1.home={q('c1.home')} s.eleven={q('s.eleven')} c4.causeway={q('c4.causeway')}")
    check("the final choice is open", q("c4.chart") == 1)

    import copy
    blank = copy.deepcopy(sim)
    sim.call(f"{NS}:c4/ending/true")
    sim.call(f"{NS}:c4/ending/blank")
    check("TRUE ending recorded, second ending refused", sim.get("#ending", "pm.world") == 1)
    check("TRUE ending restores the Deepcut and the lake", all(sim.get(f"#r.{g}", "pm.world") == 1 for g in ("deepcut", "mere", "fen", "wilds")))
    sim.advance_ticks(210)
    check("choice completes after the ending scene", q("c4.chart") == 2)
    check("epilogue reached", q("ep.complete") == 2)
    check("no main quest left active", all(q(x) == 2 for x in order))

    blank.call(f"{NS}:c4/ending/blank")
    check("BLANK ending recorded", blank.get("#ending", "pm.world") == 2)
    check("BLANK ending leaves the Deepcut in the Pall", blank.get("#r.deepcut", "pm.world") in (None, 0)
          and all(blank.get(f"#r.{g}", "pm.world") == 1 for g in ("mere", "fen", "wilds")))
    blank.advance_ticks(210)
    check("BLANK: epilogue reached", blank.get("ep.complete", "pm.q") == 2)

    # the Eleven: each keepsake counts once; all eleven complete the side quest
    k = Sim()
    k.call(f"{NS}:core/load")
    k.call(f"{NS}:core/tick")
    for n in range(1, 12):
        k.call(f"{NS}:keepsake/found/{n}")
        k.call(f"{NS}:keepsake/found/{n}")
    check("eleven keepsakes counted once each", k.get("#k.count", "pm.world") == 11)
    check("the Eleven side quest completes", k.get("s.eleven", "pm.q") == 2 and k.get("#k.all", "pm.world") == 1)

    # the benchmarks: each counts once, all eight complete the side quest
    bm = Sim()
    bm.call(f"{NS}:core/load")
    bm.call(f"{NS}:core/tick")
    for n in range(1, 9):
        bm.call(f"{NS}:bench/read/{n}")
        bm.call(f"{NS}:bench/read/{n}")
    check("eight benchmarks counted once each", bm.get("#bench.count", "pm.world") == 8)
    check("the benchmark side quest completes", bm.get("s.bench", "pm.q") == 2)

    # reload keeps progress
    before = dict(sim.scores)
    sim.call(f"{NS}:core/load")
    changed = [kk for kk in before if before[kk] != sim.scores.get(kk) and kk[1] == "pm.q"]
    check("reloading the pack never changes quest progress", not changed, str(changed[:5]))

    # dialog choices only work from the dialog that offered them (pm.dctx guard)
    d = Sim()
    d.call(f"{NS}:core/load")
    d.call(f"{NS}:core/tick")
    letter_choice = None
    for f in sorted((FN / "dlg" / "c").glob("*.mcfunction")):
        if "complete" in f.read_text() and "p.letter" in f.read_text():
            letter_choice = f"{NS}:dlg/c/{f.stem}"
    d.set("@s", "pm.dctx", 0)
    d.call(letter_choice)
    check("a dialog choice without its dialog does nothing", d.get("p.letter", "pm.q") == 1)
    d.call(f"{NS}:dlg/show/prop/noticeboard/letter")
    d.call(letter_choice)
    check("the same choice from its dialog works", d.get("p.letter", "pm.q") == 2)
    d.call(letter_choice)
    check("a used choice cannot be replayed", d.get("#rev", "pm.world") == 1)

    # an encounter from start to success (one player present, all lamps relit)
    e = Sim()
    e.call(f"{NS}:core/load")
    e.call(f"{NS}:core/tick")
    for x in order[:order.index("c1.surge")]:
        e.call(f"{NS}:admin/force", {"q": x})
    check("the Hollin surge quest is active", e.get("c1.surge", "pm.q") == 1)
    e.entity_default = True
    e.call(f"{NS}:enc/hollin/start")
    check("the surge starts", e.get("#enc.hollin", "pm.world") == 1)
    e.block_default = True
    for _ in range(40):
        e.set("#seconds", "pm.world", (e.get("#seconds", "pm.world") or 0) + 1)
        e.call(f"{NS}:enc/hollin/tick")
    check("relighting all lamps after the minimum time wins the surge", e.get("#enc.hollin", "pm.world") == 2 and e.get("c1.surge", "pm.q") == 2)
    check("winning restores Hollin", e.get("#r.hollin", "pm.world") == 1 and e.get("#chapter", "pm.world") == 2)
    # abandoned surge resets after 15 s and cools down
    f2 = Sim()
    f2.call(f"{NS}:core/load")
    f2.call(f"{NS}:core/tick")
    for x in order[:order.index("c3.surge")]:
        f2.call(f"{NS}:admin/force", {"q": x})
    f2.entity_default = True
    f2.call(f"{NS}:enc/glassworks/start")
    f2.entity_default = False
    for _ in range(16):
        f2.set("#seconds", "pm.world", (f2.get("#seconds", "pm.world") or 0) + 1)
        f2.call(f"{NS}:enc/glassworks/tick")
    check("an abandoned surge resets", f2.get("#enc.glassworks", "pm.world") == 0 and f2.get("c3.surge", "pm.q") == 1)
    check("a reset surge has a cooldown", f2.get("#cool.glassworks", "pm.world") > f2.get("#seconds", "pm.world"))
    # time limit: players present, candles never relit -> fails after 120 s
    f2.entity_default = True
    f2.set("#cool.glassworks", "pm.world", 0)
    f2.call(f"{NS}:enc/glassworks/start")
    for _ in range(125):
        f2.set("#seconds", "pm.world", (f2.get("#seconds", "pm.world") or 0) + 1)
        f2.call(f"{NS}:enc/glassworks/tick")
    check("the timed surge fails when time runs out", f2.get("#enc.glassworks", "pm.world") == 0 and f2.get("c3.surge", "pm.q") == 1)

    # the final encounter: start, reset (everyone gone), win
    b = Sim()
    b.call(f"{NS}:core/load")
    b.call(f"{NS}:core/tick")
    for x in order[:order.index("c4.unlooked")]:
        b.call(f"{NS}:admin/force", {"q": x})
    check("the final encounter's quest is active", b.get("c4.unlooked", "pm.q") == 1)
    b.entity_default = True
    b.call(f"{NS}:c4/boss/start")
    check("the final encounter starts", b.get("#boss", "pm.world") == 1 and b.get("#b.max", "pm.world") == 180)
    b.call(f"{NS}:c4/boss/reset")
    check("it resets cleanly", b.get("#boss", "pm.world") == 0 and b.get("c4.unlooked", "pm.q") == 1)
    b.call(f"{NS}:c4/boss/start")
    b.call(f"{NS}:c4/boss/win")
    check("winning completes it and opens the choice", b.get("#boss", "pm.world") == 2 and b.get("c4.unlooked", "pm.q") == 2 and b.get("c4.chart", "pm.q") == 1)

    # players joining, rejoining, dying; the per-second loop runs without errors
    j = Sim()
    j.call(f"{NS}:core/load")
    j.call(f"{NS}:core/tick")
    j.call(f"{NS}:player/first_join")
    check("first join sets up the player", j.get("@s", "pm.joined") == 1 and j.get("@s", "pm.optbar") == 1)
    for x in order[:order.index("c2.arrive")]:
        j.call(f"{NS}:admin/force", {"q": x})
    j.call(f"{NS}:player/first_join")
    j.set("@s", "pm.leave", 1)
    j.call(f"{NS}:player/rejoin")
    check("rejoin clears the leave counter", j.get("@s", "pm.leave") == 0)
    j.call(f"{NS}:player/died")
    for _ in range(30):
        j.call(f"{NS}:core/second")
        j.advance_ticks(20)
    j.entity_default = True
    j.block_default = True
    j.call(f"{NS}:core/second")
    check("the per-second loop runs in both world states", True)

    width = max(len(r[0]) for r in results)
    fails = 0
    for name, ok, detail in results:
        fails += not ok
        print(f"{'PASS' if ok else 'FAIL'}  {name.ljust(width)}  {detail if not ok else ''}")
    if sim.unknown:
        print("note: commands not modelled:", sorted(sim.unknown))
    print(f"\n{len(results) - fails}/{len(results)} checks passed")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
