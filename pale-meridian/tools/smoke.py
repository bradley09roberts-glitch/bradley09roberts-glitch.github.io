#!/usr/bin/env python3
"""Smoke execution of every generated function under the logic simulator (NOT the game).

1. Every function runs in 4 world states: {start of the campaign, late campaign} x {nothing present,
   every entity and block condition true}. Macro functions run with the sample arguments the offline
   checker also uses (tools/generated/macro_samples.json). Any simulator error or runaway recursion fails.
2. Per-second workload: 30 simulated seconds of core/second with every entity/player check forced true
   (an upper bound on the work the loop can do; not a timing measurement), at the start of the game and
   during the final chapter. Reports function calls and commands per second, and fails if any macro
   function cycles through more argument sets than the game caches per macro function (8 in 26.2,
   MacroFunction.MAX_CACHE_ENTRIES), which would make the game re-parse it every second.

Usage: python3 tools/smoke.py
"""
from __future__ import annotations

import copy
import json
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import simulate as S  # noqa: E402

ROOT = S.ROOT
MACRO_CACHE = 8
SAMPLES = json.loads((ROOT / "tools" / "generated" / "macro_samples.json").read_text())
ORDER = json.loads((ROOT / "mod" / "src" / "gametest" / "resources" / "palemeridian-tests" / "fixture.json").read_text())["main_order"]


class Counting(S.Sim):
    def __init__(self):
        super().__init__()
        self.fcalls = 0
        self.commands = 0
        self.macro_args: dict[str, set] = defaultdict(set)
        self.macro_calls: Counter = Counter()

    def call(self, fid, args=None):
        self.fcalls += 1
        if args is not None:
            self.macro_calls[fid] += 1
            self.macro_args[fid].add(json.dumps(args, sort_keys=True))
        return super().call(fid, args)

    def run(self, cmd):
        self.commands += 1
        return super().run(cmd)


def state(late: bool, cls=S.Sim) -> S.Sim:
    s = cls()
    s.call(f"{S.NS}:core/load")
    s.call(f"{S.NS}:core/tick")
    if late:
        for q in ORDER[:ORDER.index("c4.unlooked")]:
            s.call(f"{S.NS}:admin/force", {"q": q})
    return s


def clone(s: S.Sim) -> S.Sim:
    c = copy.copy(s)                      # shares the read-only function cache
    c.scores = dict(s.scores)
    c.storage = copy.deepcopy(s.storage)
    c.queue = list(s.queue)
    c.log = []
    c.calls = 0
    c.unknown = set(s.unknown)
    return c


def main() -> int:
    fns = sorted(p.relative_to(S.FN).with_suffix("").as_posix() for p in S.FN.rglob("*.mcfunction"))
    bases = {"start": state(False), "late": state(True)}
    errors: dict[str, str] = {}
    macros = runs = 0
    for f in fns:
        fid = f"{S.NS}:{f}"
        is_macro = any(l.startswith("$") for l in (S.FN / f"{f}.mcfunction").read_text().splitlines())
        args = None
        if is_macro:
            macros += 1
            if fid not in SAMPLES:
                errors[f] = "macro function without sample arguments"
                continue
            args = S.parse_snbt(SAMPLES[fid])
        for base in bases.values():
            for present in (False, True):
                s = clone(base)
                s.entity_default = s.block_default = present
                runs += 1
                try:
                    s.call(fid, args)
                except Exception as e:  # noqa: BLE001 - report every kind of simulator failure
                    errors.setdefault(f, f"{type(e).__name__}: {e}")
    print(f"smoke: {len(fns)} functions ({len(fns) - macros} plain, {macros} macro with sample arguments), "
          f"{runs} runs in 4 world states")
    for f, e in sorted(errors.items()):
        print(f"FAIL  {f}: {e}")
    print(f"smoke errors: {len(errors)}")

    thrash = 0
    for label, late in (("start of the game", False), ("final chapter", True)):
        s = state(late, Counting)
        s.entity_default = True
        seconds = 30
        s.fcalls = s.commands = 0
        s.macro_args.clear()
        s.macro_calls.clear()
        for _ in range(seconds):
            s.call(f"{S.NS}:core/second")
        print(f"per second, {label}, every check forced true: {s.fcalls / seconds:.0f} function calls, "
              f"{s.commands / seconds:.0f} commands, {sum(s.macro_calls.values()) / seconds:.1f} macro calls")
        for fid in sorted(s.macro_calls):
            n = len(s.macro_args[fid])
            bad = n > MACRO_CACHE
            thrash += bad
            print(f"  {'FAIL' if bad else 'ok  '}  {fid}: {s.macro_calls[fid]} calls in {seconds} s, "
                  f"{n} distinct argument sets (game caches {MACRO_CACHE})")
    ok = not errors and not thrash
    print(f"RESULT: {'PASS' if ok else 'FAIL'}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
