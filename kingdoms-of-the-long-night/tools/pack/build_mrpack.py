#!/usr/bin/env python3
"""Build the Kingdoms of the Long Night .mrpack files (Modrinth modpack format v1) from
modpack/manifest.json. Python 3 standard library only; works offline.

One manifest, one .mrpack per preset:
    dist/KingdomsOfTheLongNight-<version>-performance.mrpack   (default preset)
    dist/KingdomsOfTheLongNight-<version>-rich.mrpack

Each pack contains:
    modrinth.index.json   files[] reference cdn.modrinth.com (no third-party jar is redistributed)
    overrides/...         modpack/overrides/common + modpack/overrides/<preset> (later wins);
                          @@SHADERPACK@@ etc. placeholders in text files are filled from the manifest
    overrides/mods/longnight-*.jar   only if mod/build/libs/longnight-*.jar exists at build time
                                     (-sources/-slim/-javadoc/-dev jars are ignored)

The output is deterministic: entries are sorted, timestamps fixed (1980-01-01), permissions fixed,
JSON key order stable. Re-running with the same inputs yields byte-identical files.

Usage:
    python3 tools/pack/build_mrpack.py [version]            (default version 0.1.0)
    python3 tools/pack/build_mrpack.py 0.1.0 --preset rich  (build only one preset)
    python3 tools/pack/build_mrpack.py --no-longnight       (never include our jar)
"""
import argparse
import hashlib
import json
import os
import re
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import packlib  # noqa: E402

FIXED_TIME = (1980, 1, 1, 0, 0, 0)
ALLOWED_DOWNLOAD_HOSTS = ("https://cdn.modrinth.com/",)


def zinfo(name, mode=0o644):
    zi = zipfile.ZipInfo(name, date_time=FIXED_TIME)
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = (0o100000 | mode) << 16
    zi.create_system = 3  # unix
    return zi


def index_for(manifest, preset, version):
    pack = manifest["pack"]
    pcfg = manifest["presets"][preset]
    files = []
    for f in packlib.preset_files(manifest, preset):
        if not f["url"].startswith(ALLOWED_DOWNLOAD_HOSTS):
            raise SystemExit(f"refusing non-Modrinth download URL for {f['slug']}: {f['url']}")
        if not re.fullmatch(r"[0-9a-f]{40}", f["sha1"]) or not re.fullmatch(r"[0-9a-f]{128}", f["sha512"]):
            raise SystemExit(f"bad hashes for {f['slug']}")
        files.append({
            "path": f["path"],
            "hashes": {"sha1": f["sha1"], "sha512": f["sha512"]},
            "env": {"client": f["env"]["client"], "server": f["env"]["server"]},
            "downloads": [f["url"]],
            "fileSize": f["size"],
        })
    files.sort(key=lambda x: x["path"].lower())
    paths = [x["path"] for x in files]
    if len(paths) != len(set(paths)):
        raise SystemExit(f"duplicate file paths in preset {preset}")
    return {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": f"{version}+{preset}",
        "name": f"{pack['name']} ({pcfg['title']})",
        "summary": f"{pack['summary']} Preset: {pcfg['title']} - {pcfg['description']}",
        "files": files,
        "dependencies": {"minecraft": pack["minecraft"], pack["loader"]: pack["loader_version"]},
    }


def build(manifest, preset, version, longnight_jar, out_dir):
    index = index_for(manifest, preset, version)
    overrides = packlib.render_overrides(manifest, preset)
    if longnight_jar:
        with open(longnight_jar, "rb") as fh:
            overrides[f"mods/{os.path.basename(longnight_jar)}"] = fh.read()
    for rel in overrides:
        if rel.startswith("mods/") and not os.path.basename(rel).startswith("longnight"):
            raise SystemExit(f"only our own longnight jar may live in overrides/mods (found {rel})")
    os.makedirs(out_dir, exist_ok=True)
    out = os.path.join(out_dir, f"KingdomsOfTheLongNight-{version}-{preset}.mrpack")
    tmp = out + ".tmp"
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        data = json.dumps(index, indent=2, ensure_ascii=False).encode("utf-8") + b"\n"
        z.writestr(zinfo("modrinth.index.json"), data)
        for rel in sorted(overrides):
            z.writestr(zinfo("overrides/" + rel), overrides[rel])
    os.replace(tmp, out)
    sha = hashlib.sha256(open(out, "rb").read()).hexdigest()
    return out, index, sorted(overrides), sha


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("version", nargs="?", default="0.1.0")
    ap.add_argument("--manifest", default=packlib.MANIFEST)
    ap.add_argument("--preset", action="append", help="build only this preset (repeatable)")
    ap.add_argument("--out", default=packlib.DIST)
    ap.add_argument("--no-longnight", action="store_true")
    args = ap.parse_args()
    if not re.fullmatch(r"[0-9A-Za-z.+-]+", args.version):
        raise SystemExit("version may only contain [0-9A-Za-z.+-]")

    manifest = packlib.load_manifest(args.manifest)
    presets = args.preset or list(manifest["presets"])
    ln = None if args.no_longnight else packlib.find_longnight_jar()
    if not ln:
        print("WARNING: no mod/build/libs/longnight-*.jar found - building WITHOUT the longnight core mod"
              if not args.no_longnight else "note: --no-longnight given, building without the core mod")
    else:
        print("including core mod:", os.path.relpath(ln, packlib.ROOT))
    for preset in presets:
        out, index, ovr, sha = build(manifest, preset, args.version, ln, args.out)
        mods = sum(1 for f in index["files"] if f["path"].startswith("mods/"))
        other = len(index["files"]) - mods
        size = sum(f["fileSize"] for f in index["files"])
        print(f"{os.path.relpath(out, packlib.ROOT)}: {mods} mods + {other} other files referenced "
              f"({size / 1048576:.1f} MiB download), {len(ovr)} override files, sha256 {sha[:16]}...")


if __name__ == "__main__":
    main()
