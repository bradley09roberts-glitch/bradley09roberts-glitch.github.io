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
MACRO_SAMPLES: dict[str, str] = {}

# Sample arguments used by the offline checker to instantiate (and so fully parse) macro functions.
_SAMPLE_BY_PREFIX = {
    "dlg/_call": None,  # filled in by the dialog generator (needs a real choice code)
    "hud/wp_at": '{poi:"landing.lamp"}',
    "hud/_wp_move": '{x:1.5d,y:86.0d,z:2.5d,yaw:0.0f,bx:1,by:86,bz:2}',
    "hud/_wp_unforce": '{x:1,z:2}',
    "poi/get": '{name:"landing.lamp"}',
    "ui/_settings_show": '{bar:"On",fx:"On",wp:"On",chill:"On",keep:"Off",diff:"Normal"}',
    "ui/_eleven_cat": '{list:"",item:"A name"}',
    "ui/_eleven_dialog": '{list:"A name  ·  "}',
    "enc/_set_bulb": '{x:1,y:70,z:2,lit:"true"}',
    "enc/_set_candle": '{x:1,y:70,z:2,lit:"true"}',
    "c1/bell": '{n:1}',
    "admin/force": '{q:"p.letter"}',
    "admin/goto": '{poi:"landing.lamp"}',
    "admin/_goto": '{x:1.5d,y:86.0d,z:2.5d}',
}


def macro_sample(path: str, snbt_args: str) -> None:
    MACRO_SAMPLES[f"{NS}:{path}"] = snbt_args


def fn(path: str, lines: list[str] | str, header: str | None = None, sample: str | None = None) -> str:
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
    if any(l and l.startswith("$") for l in lines):
        smp = sample
        if smp is None:
            if path.startswith("npc/") and path.endswith("/spawn_at"):
                smp = '{x:1.5d,y:67.0d,z:2.5d,yaw:90.0f,bx:1,by:67,bz:2}'
            elif path.startswith("npc/") and path.endswith("/_at"):
                smp = '{poi:"landing.lamp"}'
            else:
                smp = _SAMPLE_BY_PREFIX.get(path)
        if smp is None and path != "dlg/_call":
            raise ValueError(f"macro function {path} needs sample arguments for validation")
        if smp is not None:
            MACRO_SAMPLES[f"{NS}:{path}"] = smp
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
