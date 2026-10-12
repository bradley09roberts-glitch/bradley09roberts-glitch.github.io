"""Shared helpers for the Kingdoms of the Long Night pack tools (Python 3 stdlib only)."""
import glob
import re
import hashlib
import json
import os
import shutil
import urllib.request

UA = "kotln-modpack/0.1 (bradley09roberts)"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))          # kingdoms-of-the-long-night/
MODPACK = os.path.join(ROOT, "modpack")
MANIFEST = os.path.join(MODPACK, "manifest.json")
MOD_LIBS = os.path.join(ROOT, "mod", "build", "libs")
DIST = os.path.join(ROOT, "dist")
DEFAULT_CACHE = os.environ.get("KOTLN_CACHE", os.path.join(
    os.environ.get("TMPDIR", "/tmp"), "kotln-pack-cache"))


def load_manifest(path=MANIFEST):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def preset_files(manifest, preset, side=None):
    """Files of a preset; side='server' keeps only files the server may run."""
    if preset not in manifest["presets"]:
        raise SystemExit(f"unknown preset '{preset}' (have: {', '.join(manifest['presets'])})")
    out = [f for f in manifest["files"] if preset in f["presets"]]
    if side == "server":
        out = [f for f in out if f["env"]["server"] != "unsupported"]
    elif side == "client":
        out = [f for f in out if f["env"]["client"] != "unsupported"]
    return out


def sha512_of(path):
    h = hashlib.sha512()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(entry, cache_dir=DEFAULT_CACHE):
    """Download a manifest file entry into the cache (by sha512) and return its path.

    The file is verified against the manifest sha512 and sha1; nothing unverified is returned.
    """
    d = os.path.join(cache_dir, "files", entry["sha512"][:2], entry["sha512"][:16])
    os.makedirs(d, exist_ok=True)
    p = os.path.join(d, entry["filename"])
    if os.path.exists(p) and sha512_of(p) == entry["sha512"]:
        return p
    req = urllib.request.Request(entry["url"], headers={"User-Agent": UA})
    tmp = p + ".part"
    with urllib.request.urlopen(req, timeout=300) as r, open(tmp, "wb") as out:
        shutil.copyfileobj(r, out)
    data_sha512 = sha512_of(tmp)
    with open(tmp, "rb") as fh:
        data_sha1 = hashlib.sha1(fh.read()).hexdigest()
    if data_sha512 != entry["sha512"] or data_sha1 != entry["sha1"]:
        os.remove(tmp)
        raise SystemExit(f"HASH MISMATCH for {entry['filename']} from {entry['url']}")
    if os.path.getsize(tmp) != entry["size"]:
        os.remove(tmp)
        raise SystemExit(f"SIZE MISMATCH for {entry['filename']}")
    os.replace(tmp, p)
    return p


def find_longnight_jar(libs_dir=MOD_LIBS):
    """Newest longnight-*.jar in mod/build/libs, ignoring -sources/-slim/-javadoc/-dev jars."""
    cands = []
    for p in glob.glob(os.path.join(libs_dir, "longnight-*.jar")):
        name = os.path.basename(p)[:-4]
        if any(name.endswith(s) for s in ("-sources", "-slim", "-javadoc", "-dev", "-api")):
            continue
        cands.append(p)
    if not cands:
        return None
    cands.sort(key=lambda p: (os.path.getmtime(p), p))
    return cands[-1]


TEXT_SUFFIXES = (".txt", ".toml", ".json", ".json5", ".properties", ".cfg", ".yaml", ".yml", ".snbt")


def placeholders(manifest, preset):
    """Values substituted into text overrides at build time (@@NAME@@)."""
    files = preset_files(manifest, preset)
    shaders = [f["filename"] for f in files if f["path"].startswith("shaderpacks/")]
    return {
        "@@SHADERPACK@@": shaders[0] if shaders else "",
        "@@PACK_NAME@@": manifest["pack"]["name"],
        "@@PRESET@@": preset,
    }


def render_overrides(manifest, preset):
    """Merge override layers (later wins) and substitute placeholders.

    Returns {relative_path (posix): bytes}, sorted-iterable and deterministic.
    """
    out = {}
    subs = placeholders(manifest, preset)
    for layer in manifest["presets"][preset].get("overrides", []):
        base = os.path.join(MODPACK, "overrides", layer)
        if not os.path.isdir(base):
            continue
        for root, dirs, files in os.walk(base):
            dirs.sort()
            for f in sorted(files):
                if f in (".DS_Store", ".gitkeep") or f.endswith("~"):
                    continue
                src = os.path.join(root, f)
                rel = os.path.relpath(src, base).replace(os.sep, "/")
                with open(src, "rb") as fh:
                    data = fh.read()
                if f.endswith(TEXT_SUFFIXES):
                    text = data.decode("utf-8")
                    for k, v in subs.items():
                        text = text.replace(k, v)
                    if "@@" in text and re.search(r"@@[A-Z_]+@@", text):
                        raise SystemExit(f"unresolved placeholder in override {layer}/{rel}")
                    data = text.encode("utf-8")
                out[rel] = data
    return out


def write_overrides(manifest, preset, dest_dir, only_prefixes=None):
    """Write rendered overrides into an instance/server directory. Returns list of paths."""
    written = []
    for rel, data in sorted(render_overrides(manifest, preset).items()):
        if only_prefixes and not rel.startswith(tuple(only_prefixes)):
            continue
        p = os.path.join(dest_dir, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as fh:
            fh.write(data)
        written.append(rel)
    return written
