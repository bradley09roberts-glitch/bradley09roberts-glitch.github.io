"""Compact, diff-friendly JSON writer: objects multi-line, short arrays / small leaf objects inline."""
from __future__ import annotations

import json

_INLINE = dict(separators=(", ", ": "), ensure_ascii=False)


def _leafy(o) -> bool:
    if isinstance(o, dict):
        return all(not isinstance(v, (dict, list)) or _leafy(v) for v in o.values())
    if isinstance(o, list):
        return all(not isinstance(v, (dict, list)) or _leafy(v) for v in o)
    return True


def dumps(o, level=0, width=120) -> str:
    pad = "  " * level
    if isinstance(o, dict):
        if not o:
            return "{}"
        inline = json.dumps(o, **_INLINE)
        if _leafy(o) and len(inline) + len(pad) <= width:
            return inline
        parts = ["%s  %s: %s" % (pad, json.dumps(k, ensure_ascii=False), dumps(v, level + 1, width)) for k, v in o.items()]
        return "{\n" + ",\n".join(parts) + "\n" + pad + "}"
    if isinstance(o, list):
        if not o:
            return "[]"
        inline = json.dumps(o, **_INLINE)
        if _leafy(o) and len(inline) + len(pad) <= width:
            return inline
        parts = ["%s  %s" % (pad, dumps(v, level + 1, width)) for v in o]
        return "[\n" + ",\n".join(parts) + "\n" + pad + "]"
    return json.dumps(o, ensure_ascii=False)


def write(obj, path):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(dumps(obj))
        fh.write("\n")
