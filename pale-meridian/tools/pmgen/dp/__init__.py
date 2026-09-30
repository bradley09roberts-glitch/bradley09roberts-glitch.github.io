"""Data-pack writers shared by all generator modules.

Conventions
* Namespace `palemeridian`; scoreboard objectives are prefixed `pm.`.
* World facts live on fake players in `pm.world`; quest states in `pm.q` (0 locked, 1 active, 2 done);
  quest progress counters in `pm.qp`. This scoreboard is the single authoritative campaign state.
* Every generated function is idempotent or guarded by a state check.
"""
from __future__ import annotations

import json
import re

from ..jsonio import write_json, write_text
from ..paths import DATA, PM_DATA

NS = "palemeridian"
_written_fns: set[str] = set()


def fn(path: str, lines: list[str] | str, header: str | None = None) -> str:
    """Write data/palemeridian/function/<path>.mcfunction and return its resource id."""
    if isinstance(lines, str):
        lines = [lines]
    body = []
    if header:
        body += [f"# {h}" for h in header.split("\n")]
    for ln in lines:
        if ln is None:
            continue
        body.append(ln)
    if path in _written_fns:
        raise ValueError(f"function {path} written twice")
    _written_fns.add(path)
    write_text(PM_DATA / "function" / f"{path}.mcfunction", "\n".join(body))
    return f"{NS}:{path}"


def fid(path: str) -> str:
    return f"{NS}:{path}"


def adv(path: str, obj: dict) -> str:
    write_json(PM_DATA / "advancement" / f"{path}.json", obj)
    return f"{NS}:{path}"


def dialog(path: str, obj: dict) -> str:
    write_json(PM_DATA / "dialog" / f"{path}.json", obj)
    return f"{NS}:{path}"


def predicate(path: str, obj) -> str:
    write_json(PM_DATA / "predicate" / f"{path}.json", obj)
    return f"{NS}:{path}"


def loot(path: str, obj: dict) -> str:
    write_json(PM_DATA / "loot_table" / f"{path}.json", obj)
    return f"{NS}:{path}"


def item_modifier(path: str, obj) -> str:
    write_json(PM_DATA / "item_modifier" / f"{path}.json", obj)
    return f"{NS}:{path}"


def tag(registry_folder: str, namespace: str, name: str, values: list, replace: bool = False) -> None:
    base = DATA / namespace / "tags" / registry_folder
    write_json(base / f"{name}.json", {"replace": replace, "values": values})


def snbt(obj) -> str:
    """Compact SNBT for text components / NBT given as Python data (JSON syntax is valid SNBT)."""
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def tellraw(target: str, comp) -> str:
    return f"tellraw {target} {snbt(comp)}"


def title(target: str, kind: str, comp) -> str:
    return f"title {target} {kind} {snbt(comp)}"


def say_npc(target: str, name: str, color: str, text: str) -> str:
    return tellraw(target, [{"text": f"{name}: ", "color": color, "bold": False}, {"text": text, "color": "white"}])


def xyz(p: dict | tuple | list, dy: float = 0.0) -> str:
    if isinstance(p, dict):
        x, y, z = p["x"], p["y"], p["z"]
    else:
        x, y, z = p
    return f"{_num(x)} {_num(y + dy)} {_num(z)}"


def _num(v) -> str:
    if isinstance(v, float) and not v.is_integer():
        return f"{v:.2f}".rstrip("0").rstrip(".")
    return str(int(v))


def slug(s: str) -> str:
    return re.sub(r"[^a-z0-9_./-]", "_", s.lower())
