#!/usr/bin/env python3
"""Make a test instance's mods/ folder match pack/mods.lock.json exactly (+ companion mod, + optional
test kit) and copy the shader ZIP into shaderpacks/. Used only for local testing."""
import json, os, pathlib, shutil, sys
ROOT = pathlib.Path(__file__).resolve().parents[2]
inst = pathlib.Path(sys.argv[1])
cache = pathlib.Path(os.environ["EMBERVEIL_CACHE"]) / "mods"
with_testkit = "--testkit" in sys.argv
lock = json.loads((ROOT / "pack/mods.lock.json").read_text())
mods = inst / "mods"; mods.mkdir(parents=True, exist_ok=True)
for f in mods.glob("*.jar"): f.unlink()
for m in lock["mods"]:
    shutil.copy2(cache / m["filename"], mods / m["filename"])
shutil.copy2(ROOT / "companion-mod/build/libs/emberveil-core-1.0.0.jar", mods)
if with_testkit:
    shutil.copy2(ROOT / "tools/testkit/build/libs/emberveil-testkit-1.0.0.jar", mods)
sp = inst / "shaderpacks"; sp.mkdir(exist_ok=True)
for z in (ROOT / "dist").glob("Emberveil-Shaders-*.zip"):
    shutil.copy2(z, sp / z.name)
print(f"{len(list(mods.glob('*.jar')))} jars in {mods}")
