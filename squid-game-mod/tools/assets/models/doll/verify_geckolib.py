#!/usr/bin/env python3
"""Cross-check the generated doll against the REAL GeckoLib 4.9.3 classes (optional, needs a JDK + the Gradle cache).

    python3 tools/assets/models/doll/verify_geckolib.py [--strict]

It compiles verify_geckolib/GeckoCheck.java (plain javac, no Gradle) against the GeckoLib / Minecraft
(intermediary) / library jars found in ~/.gradle/caches, loads doll.geo.json + doll.animation.json with GeckoLib's
own loader, and compares every transformed quad vertex + UV of 49 poses (bind pose + every animation at 6 times)
with the python port (gl_model.py) that the preview renderer and the validator use.

Exit code 0 = identical to float precision, or SKIPPED (no JDK / jars; use --strict to turn a skip into a failure).
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import numpy as np

import gl_model as gl

ASSETS = HERE.parents[3] / "src" / "main" / "resources" / "assets" / "squidgame"
GEO = ASSETS / "geo" / "entity" / "doll.geo.json"
ANIM = ASSETS / "animations" / "entity" / "doll.animation.json"
LIB_GROUPS = ["com.google.code.gson", "org.joml", "it.unimi.dsi", "org.apache.commons", "commons-io", "commons-codec",
              "org.apache.logging.log4j", "org.jetbrains", "com.google.guava", "com.mojang", "io.netty", "org.slf4j",
              "com.google.code.findbugs", "net.sf.jopt-simple", "com.ibm.icu", "com.github.oshi", "net.java.dev.jna"]


def _ver(v):
    return [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-]", v)]


def find_classpath():
    gradle = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches"
    mods = gradle / "modules-2" / "files-2.1"
    gecko = sorted(glob.glob(str(mods / "software.bernie.geckolib" / "geckolib-fabric-1.21.1" / "4.9.3" / "*" / "geckolib-fabric-1.21.1-4.9.3.jar")))
    mc = sorted(p for p in glob.glob(str(gradle / "fabric-loom" / "minecraftMaven" / "net" / "minecraft" / "minecraft-*-intermediary" / "*" / "*.jar"))
                if ("common-intermediary" in p or "clientonly-intermediary" in p) and not p.endswith("-sources.jar"))
    if not gecko or len(mc) < 2:
        return None
    libs = {}
    for g in LIB_GROUPS:
        for p in glob.glob(str(mods / g / "*" / "*" / "*" / "*.jar")):
            if p.endswith(("-sources.jar", "-javadoc.jar")):
                continue
            parts = Path(p).parts
            art, ver = parts[-4], parts[-3]
            if g == "org.apache.logging.log4j" and art != "log4j-api":
                continue
            if (g, art) not in libs or _ver(ver) > _ver(libs[(g, art)][0]):
                libs[(g, art)] = (ver, p)
    return [gecko[-1]] + mc + [p for _, p in libs.values()]


def parse_java(path):
    poses, cur, header = {}, None, []
    for line in open(path):
        line = line.rstrip("\n")
        if line.startswith("@pose"):
            _, name, tick = line.split()
            cur = (name, float(tick))
            poses[cur] = {}
        elif line.startswith("#"):
            header.append(line)
        elif line:
            parts = line.split("|")
            poses[cur][(parts[0], int(parts[1]), parts[2])] = np.array([[float(x) for x in p.split(",")] for p in parts[3:7]])
    return poses, header


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args(argv)
    skip = lambda msg: (print("verify_geckolib: SKIPPED - " + msg), 2 if args.strict else 0)[1]
    java, javac = shutil.which("java"), shutil.which("javac")
    if not java or not javac:
        return skip("no JDK on PATH")
    cp = find_classpath()
    if cp is None:
        return skip("GeckoLib / Minecraft jars not found in the Gradle cache (run a Gradle build once)")
    sep = os.pathsep
    with tempfile.TemporaryDirectory() as tmp:
        r = subprocess.run([javac, "-nowarn", "-cp", sep.join(cp), "-d", tmp, str(HERE / "verify_geckolib" / "GeckoCheck.java")],
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(r.stdout, r.stderr)
            print("verify_geckolib: FAIL (javac)")
            return 1
        out = os.path.join(tmp, "out.txt")
        r = subprocess.run([java, "-cp", sep.join(cp + [tmp]), "GeckoCheck", str(GEO), str(ANIM), out], capture_output=True, text=True)
        if r.returncode != 0 or not os.path.exists(out):
            print(r.stdout, r.stderr[-3000:])
            print("verify_geckolib: FAIL (GeckoLib could not load the doll)")
            return 1
        poses, header = parse_java(out)
    anim_doc = gl.load_json(ANIM)
    n_java = int(next(h for h in header if h.startswith("# animations")).split()[2])
    problems = []
    if n_java != len(anim_doc["animations"]):
        problems.append("GeckoLib loaded %d of %d animations (the others failed to parse)" % (n_java, len(anim_doc["animations"])))
    for h in header:
        if h.startswith("# MISSING"):
            problems.append(h)
    for h in header:
        if h.startswith("# anim "):
            name = h.split()[2]
            ticks = float(h.split("ticks=")[1].split()[0])
            loop = h.split("loop=")[1]
            a = anim_doc["animations"][name]
            if abs(ticks - a["animation_length"] * 20) > 1e-6:
                problems.append("%s: length %s ticks != %s" % (name, ticks, a["animation_length"] * 20))
            if (loop == "loop") != (a.get("loop") is True):
                problems.append("%s: loop type %s" % (name, loop))
    model = gl.bake(gl.load_json(GEO))
    anims = gl.parse_animations(anim_doc)
    max_pos = max_uv = 0.0
    nq = 0
    for (name, tick), jq in poses.items():
        anim = None if name == "bind" else anims[name]
        pose = gl.pose_at(model, anim, tick / 20.0, loop_wrap=False)
        pose.hidden.add("eyes_on")
        counters, pq = {}, {}
        for c in gl.world_quads_ex(model, pose):
            ci = counters.get(c["bone"], 0)
            counters[c["bone"]] = ci + 1
            for (v, uv, n, q) in c["quads"]:
                pq[(c["bone"], ci, q.direction)] = np.c_[v, uv]
        if set(pq) != set(jq):
            problems.append("%s@%.2f: quad set differs (%d vs %d)" % (name, tick, len(pq), len(jq)))
            continue
        for k in pq:
            max_pos = max(max_pos, float(np.max(np.abs(pq[k][:, :3] - jq[k][:, :3]))))
            max_uv = max(max_uv, float(np.max(np.abs(pq[k][:, 3:] - jq[k][:, 3:]))))
            nq += 1
    print("verify_geckolib: %d poses, %d quads: max vertex diff %.2g px, max uv diff %.2g" % (len(poses), nq, max_pos * 16, max_uv))
    if max_pos * 16 > 2e-3:
        problems.append("vertex positions differ from the real GeckoLib by %.3g px" % (max_pos * 16))
    if max_uv > 1e-5:
        problems.append("UVs differ from the real GeckoLib by %.3g" % max_uv)
    for p in problems:
        print("  FAIL:", p)
    print("verify_geckolib: %s" % ("FAIL" if problems else "OK (python port == real GeckoLib 4.9.3)"))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
