#!/usr/bin/env python3
"""Resolve pack/modlist.json against the Modrinth API and write pack/mods.lock.json.

Every entry is frozen to an exact Modrinth version ID with its primary file URL,
SHA-1, SHA-512 and size. Re-running with --refresh re-resolves unpinned entries;
without it, entries already present in the lockfile are kept unchanged.
"""
import json, sys, urllib.request, urllib.parse, pathlib, argparse

ROOT = pathlib.Path(__file__).resolve().parents[1]
UA = {"User-Agent": "emberveil-modpack/1.0 (github.com/bradley09roberts-glitch)"}
API = "https://api.modrinth.com/v2"

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--refresh", action="store_true")
    args = ap.parse_args()
    spec = json.loads((ROOT / "pack/modlist.json").read_text())
    lock_path = ROOT / "pack/mods.lock.json"
    old = {}
    if lock_path.exists() and not args.refresh:
        old = {m["slug"]: m for m in json.loads(lock_path.read_text())["mods"]}
    mc, loader = spec["minecraft"], spec["loader"]
    out = []
    for m in spec["mods"]:
        slug = m["slug"]
        if slug in old and (not m.get("pin") or old[slug]["version_number"] == m["pin"]):
            e = old[slug]; e.update({k: m[k] for k in ("side", "group") if k in m}); out.append(e); continue
        q = urllib.parse.quote(json.dumps([loader])), urllib.parse.quote(json.dumps([mc]))
        versions = get(f"{API}/project/{slug}/version?loaders={q[0]}&game_versions={q[1]}")
        if m.get("pin"):
            cand = [v for v in versions if v["version_number"] == m["pin"]]
        else:
            cand = [v for v in versions if v["version_type"] == "release"] or versions
        if not cand:
            sys.exit(f"no {loader} {mc} version for {slug} (pin={m.get('pin')})")
        v = cand[0]
        proj = get(f"{API}/project/{slug}")
        f = next((x for x in v["files"] if x["primary"]), v["files"][0])
        out.append({
            "slug": slug, "title": proj["title"], "project_id": proj["id"], "version_id": v["id"],
            "version_number": v["version_number"], "version_type": v["version_type"],
            "date_published": v["date_published"], "loaders": v["loaders"], "game_versions": v["game_versions"],
            "license": proj["license"]["id"], "source_url": proj.get("source_url"),
            "page": f"https://modrinth.com/mod/{slug}", "filename": f["filename"], "url": f["url"],
            "sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"], "size": f["size"],
            "side": m.get("side", "both"), "group": m.get("group", "misc"),
            "declared_deps": [{"type": d["dependency_type"], "project_id": d.get("project_id"), "version_id": d.get("version_id")} for d in v["dependencies"]],
        })
        print(f"{slug:34s} {v['version_number']}")
    lock_path.write_text(json.dumps({"minecraft": mc, "loader": loader, "mods": out}, indent=1) + "\n")
    print(f"wrote {lock_path} ({len(out)} mods)")

if __name__ == "__main__":
    main()
