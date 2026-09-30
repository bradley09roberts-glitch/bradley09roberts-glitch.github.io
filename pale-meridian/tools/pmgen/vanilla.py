"""Access to pinned vanilla Minecraft data (downloaded once, SHA-1 verified, cached in tools/.cache).

Nothing here is redistributed: the jars stay in the local cache. Reports (block states, registries,
command tree) are produced with Mojang's official data generator, which needs no EULA acceptance
and starts no server.
"""
import hashlib
import io
import json
import os
import shutil
import subprocess
import urllib.request
import zipfile
from functools import lru_cache
from pathlib import Path

from .paths import CACHE, TOOLS

PINS = json.loads((TOOLS / "pins.json").read_text())


def _download(url: str, dest: Path, sha1: str) -> Path:
    if dest.exists() and hashlib.sha1(dest.read_bytes()).hexdigest() == sha1:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    with urllib.request.urlopen(url, timeout=120) as r, open(tmp, "wb") as f:
        shutil.copyfileobj(r, f)
    got = hashlib.sha1(tmp.read_bytes()).hexdigest()
    if got != sha1:
        tmp.unlink(missing_ok=True)
        raise RuntimeError(f"SHA-1 mismatch for {url}: expected {sha1}, got {got}")
    tmp.replace(dest)
    return dest


def server_jar() -> Path:
    p = PINS["server_jar"]
    return _download(p["url"], CACHE / "server-26.2.jar", p["sha1"])


def client_jar() -> Path:
    p = PINS["client_jar"]
    return _download(p["url"], CACHE / "client-26.2.jar", p["sha1"])


@lru_cache(maxsize=1)
def inner_server_zip() -> zipfile.ZipFile:
    outer = zipfile.ZipFile(server_jar())
    return zipfile.ZipFile(io.BytesIO(outer.read(PINS["inner_server_jar"])))


@lru_cache(maxsize=1)
def client_zip() -> zipfile.ZipFile:
    return zipfile.ZipFile(client_jar())


def data_json(path: str):
    """Read a vanilla data-pack JSON, e.g. 'data/minecraft/worldgen/biome/plains.json'."""
    return json.loads(inner_server_zip().read(path))


def data_exists(path: str) -> bool:
    try:
        inner_server_zip().getinfo(path)
        return True
    except KeyError:
        return False


def asset_bytes(path: str) -> bytes:
    """Read a vanilla client asset, e.g. 'assets/minecraft/textures/block/stone.png'."""
    return client_zip().read(path)


def asset_exists(path: str) -> bool:
    try:
        client_zip().getinfo(path)
        return True
    except KeyError:
        return False


def java_cmd() -> str:
    return os.environ.get("PM_JAVA") or shutil.which("java") or "java"


def reports_dir() -> Path:
    out = CACHE / "datagen"
    marker = out / "reports" / "blocks.json"
    if not marker.exists():
        out.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            [java_cmd(), "-DbundlerMainClass=net.minecraft.data.Main", "-jar", str(server_jar()), "--reports", "--output", str(out)],
            cwd=str(CACHE), check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    return out / "reports"


@lru_cache(maxsize=1)
def blocks_report() -> dict:
    return json.loads((reports_dir() / "blocks.json").read_text())


@lru_cache(maxsize=1)
def registries_report() -> dict:
    return json.loads((reports_dir() / "registries.json").read_text())


def registry_ids(registry: str) -> set[str]:
    return set(registries_report()[registry]["entries"].keys())
