#!/usr/bin/env python3
"""Offline syntax check of the Grand Line Shaders pack with glslangValidator (if installed).

Expands the pack's #include lines the way Iris does (paths from the shaders/ root), applies a profile's option values
and compiles every program of every dimension folder as GLSL 330 compatibility. This catches GLSL errors before a
(slow) game launch; the real verification is still Iris compiling the pack in game.

    python3 tools/check_shaders.py [LOW|BALANCED|HIGH|ALL]
"""
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

SHADERS = Path(__file__).resolve().parent.parent / "shaderpack" / "GrandLineShaders" / "shaders"
INCLUDE = re.compile(r'^\s*#include\s+"([^"]+)"\s*$')


def read_profiles():
    profiles = {}
    text = (SHADERS / "shaders.properties").read_text()
    text = text.replace("\\\n", " ")
    for line in text.splitlines():
        m = re.match(r"^profile\.(\w+)\s*=\s*(.*)$", line.strip())
        if m:
            profiles[m.group(1)] = m.group(2).split()
    return profiles


def apply_options(src, options):
    for opt in options:
        if opt.startswith("!"):
            name = opt[1:]
            src = re.sub(rf"^(\s*)#define {name}\b(.*)$", rf"\1//#define {name}\2", src, flags=re.M)
            continue
        if "=" in opt or ":" in opt:
            name, value = re.split("[=:]", opt, maxsplit=1)
            src = re.sub(rf"^(\s*#define {name}) \S+", rf"\1 {value}", src, flags=re.M)
            src = re.sub(rf"^(\s*const \w+ {name}) = [^;]+;", rf"\1 = {value};", src, flags=re.M)
        else:
            src = re.sub(rf"^(\s*)//\s*#define {opt}\b(.*)$", rf"\1#define {opt}\2", src, flags=re.M)
    return src


def expand(path, options, depth=0):
    if depth > 16:
        raise RuntimeError("include depth exceeded at " + str(path))
    out = []
    for line in path.read_text().splitlines():
        m = INCLUDE.match(line)
        if m:
            inc = SHADERS / m.group(1).lstrip("/")
            out.append(expand(inc, options, depth + 1))
        else:
            out.append(line)
    text = "\n".join(out)
    if path.name == "settings.glsl":
        text = apply_options(text, options)
    return text


def main():
    validator = shutil.which("glslangValidator")
    if not validator:
        print("glslangValidator not found; nothing checked")
        return 0
    profiles = read_profiles()
    wanted = sys.argv[1] if len(sys.argv) > 1 else "ALL"
    names = list(profiles) if wanted == "ALL" else [wanted]
    failures = 0
    checked = 0
    with tempfile.TemporaryDirectory() as tmp:
        for profile in names:
            options = profiles[profile]
            for folder in sorted(p for p in SHADERS.iterdir() if p.is_dir() and p.name not in ("lib", "program", "lang")):
                for vsh in sorted(folder.glob("*.vsh")):
                    # compile and link each program's vertex + fragment stage together (catches in/out mismatches)
                    files = []
                    for f in (vsh, vsh.with_suffix(".fsh")):
                        stage = "vert" if f.suffix == ".vsh" else "frag"
                        out = Path(tmp) / f"{profile}_{folder.name}_{f.stem}.{stage}"
                        out.write_text(expand(f, options))
                        files.append(str(out))
                    r = subprocess.run([validator, "-l"] + files, capture_output=True, text=True)
                    checked += 1
                    if r.returncode != 0:
                        failures += 1
                        print(f"FAIL [{profile}] {folder.name}/{vsh.stem}")
                        print("\n".join(l for l in r.stdout.splitlines() if "ERROR" in l)[:3000])
    print(f"checked {checked} programs (vertex + fragment, linked), {failures} failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
