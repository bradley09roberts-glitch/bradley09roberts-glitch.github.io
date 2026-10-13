#!/usr/bin/env python3
"""Build the Grand Line Odyssey Modrinth modpack (.mrpack) from dist/.
usage: python3 tools/build_mrpack.py . dist/Grand-Line-Odyssey-0.3.0.mrpack
Check it with: python3 tools/check_mrpack.py dist/Grand-Line-Odyssey-0.3.0.mrpack <empty folder>"""
import hashlib, json, os, sys, zipfile

root, out = sys.argv[1], sys.argv[2]
dist = os.path.join(root, 'dist')
version = '0.3.0'
mod_jar = os.path.join(dist, f'grandline-{version}.jar')
shaders = os.path.join(dist, 'GrandLineShaders-1.0.0.zip')

def remote(path, url, sha1, sha512, size, client, server):
    return {"path": path, "hashes": {"sha1": sha1, "sha512": sha512}, "env": {"client": client, "server": server},
            "downloads": [url], "fileSize": size}

files = [
    # required library (client and server), the version the mod is built against
    remote("mods/geckolib-neoforge-1.21.1-4.9.3.jar",
           "https://cdn.modrinth.com/data/8BmcQJ2H/versions/Grwn5rUB/geckolib-neoforge-1.21.1-4.9.3.jar",
           "5cfe835472a5c5457ae9ac192d9d7b317454d5ac",
           "8dfa5f8c57224a5e34e5098367ea9819ca6dc9c11357cd3062c16da3455c8bd1a483098af7383debc4b9ce8e4d7ab3fdc799b415644eb829da83eb72a7335dfd",
           634531, "required", "required"),
    # the renderer and shader loader the Grand Line Shaders were tested with (client only)
    remote("mods/sodium-neoforge-0.6.13+mc1.21.1.jar",
           "https://cdn.modrinth.com/data/AANobbMI/versions/Pb3OXVqC/sodium-neoforge-0.6.13%2Bmc1.21.1.jar",
           "38af70fa4dc4b2aaac636e92fdba3bedd5a025e1",
           "ce58f34d05d96c0a109a5cea23c741f6bdb2e6be31fc087c5989274cefca5f10ba0c08c62083cf554a51f2c7667bf46e4164383f675c844e77633aef2659996b",
           1162994, "required", "unsupported"),
    remote("mods/iris-neoforge-1.8.12+mc1.21.1.jar",
           "https://cdn.modrinth.com/data/YL57xq9U/versions/t3ruzodq/iris-neoforge-1.8.12%2Bmc1.21.1.jar",
           "a3e6355915c7d3b2bc392724795113e51d289378",
           "57b8026a3c3c433cf6123d63dc6ce7e11f6d480a72926370db1fc7f2b06059bc16a753ecd7e7af659c19e90b592103196b0e89585ce4f0744a4ca433f59bcf1a",
           2438548, "required", "unsupported"),
]

index = {
    "formatVersion": 1,
    "game": "minecraft",
    "versionId": version,
    "name": "Grand Line Odyssey",
    "summary": "An original One Piece RPG on an ocean world for Minecraft 1.21.1 (NeoForge), with the Grand Line Shaders switched on.",
    "files": files,
    "dependencies": {"minecraft": "1.21.1", "neoforge": "21.1.259"},
}

iris = "\n".join([
    "# Grand Line Odyssey: the Grand Line Shaders are on from the first launch (Video Settings > Shader Packs to change).",
    "enableShaders=true",
    "shaderPack=GrandLineShaders-1.0.0.zip",
    "",
])

def add(z, arc, data):
    zi = zipfile.ZipInfo(arc, date_time=(2026, 1, 1, 0, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    z.writestr(zi, data)

with zipfile.ZipFile(out, 'w') as z:
    add(z, "modrinth.index.json", json.dumps(index, indent=2))
    add(z, f"overrides/mods/grandline-{version}.jar", open(mod_jar, 'rb').read())
    add(z, "client-overrides/shaderpacks/GrandLineShaders-1.0.0.zip", open(shaders, 'rb').read())
    add(z, "client-overrides/config/iris.properties", iris)
print(out, os.path.getsize(out))
