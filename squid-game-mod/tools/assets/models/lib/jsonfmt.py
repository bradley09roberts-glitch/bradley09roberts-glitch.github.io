"""Compact, deterministic JSON writer: arrays of scalars stay on one line."""
from __future__ import annotations

import json
from typing import Any


def _scalar(v: Any) -> bool:
    return not isinstance(v, (dict, list, tuple))


def dumps(obj: Any, indent: int = 1, _level: int = 0) -> str:
    pad = " " * (indent * (_level + 1))
    pad0 = " " * (indent * _level)
    if isinstance(obj, dict):
        if not obj:
            return "{}"
        items = []
        for k, v in obj.items():
            items.append(f"{pad}{json.dumps(k)}: {dumps(v, indent, _level + 1)}")
        return "{\n" + ",\n".join(items) + "\n" + pad0 + "}"
    if isinstance(obj, (list, tuple)):
        if not obj:
            return "[]"
        if all(_scalar(x) for x in obj):
            return "[" + ", ".join(json.dumps(x) for x in obj) + "]"
        items = [f"{pad}{dumps(x, indent, _level + 1)}" for x in obj]
        return "[\n" + ",\n".join(items) + "\n" + pad0 + "]"
    return json.dumps(obj)


def write(path: str, obj: Any, indent: int = 1) -> None:
    with open(path, "w", newline="\n") as f:
        f.write(dumps(obj, indent))
        f.write("\n")
