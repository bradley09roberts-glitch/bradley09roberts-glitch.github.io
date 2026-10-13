#!/usr/bin/env python3
"""Install a .mrpack the way a launcher does: download every file, check size, sha1 and sha512, then copy
overrides/ and client-overrides/ on top. usage: install_check.py <pack.mrpack> <instance dir>"""
import hashlib, json, os, sys, urllib.request, zipfile
pack, inst = sys.argv[1], sys.argv[2]
os.makedirs(inst, exist_ok=True)
allowed = ("https://cdn.modrinth.com/", "https://github.com/", "https://raw.githubusercontent.com/", "https://gitlab.com/")
z = zipfile.ZipFile(pack)
idx = json.loads(z.read("modrinth.index.json"))
assert idx["formatVersion"] == 1 and idx["game"] == "minecraft", "bad header"
print("pack:", idx["name"], idx["versionId"], idx["dependencies"])
ok = True
for f in idx["files"]:
    url = f["downloads"][0]
    assert url.startswith(allowed), "download host not allowed: " + url
    data = urllib.request.urlopen(url, timeout=60).read()
    good = len(data) == f["fileSize"] and hashlib.sha1(data).hexdigest() == f["hashes"]["sha1"] and hashlib.sha512(data).hexdigest() == f["hashes"]["sha512"]
    ok &= good
    dest = os.path.join(inst, f["path"])
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    open(dest, "wb").write(data)
    print(("OK  " if good else "BAD ") + f["path"], len(data), f["env"])
for name in z.namelist():
    for pre in ("overrides/", "client-overrides/"):
        if name.startswith(pre) and not name.endswith("/"):
            dest = os.path.join(inst, name[len(pre):])
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            open(dest, "wb").write(z.read(name))
            print("override", name[len(pre):])
print("ALL FILES VERIFIED" if ok else "HASH MISMATCH")
