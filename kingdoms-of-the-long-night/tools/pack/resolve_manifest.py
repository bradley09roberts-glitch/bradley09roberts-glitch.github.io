#!/usr/bin/env python3
"""Resolve modpack/selection.json into modpack/manifest.json using the Modrinth API.

Python 3 standard library only.

For every selected project it picks the newest *release* file for the pack's
loader + Minecraft version (falling back to the newest *beta*; alpha builds are
never chosen) unless the selection pins an exact version_number. Every REQUIRED
dependency declared on Modrinth is resolved recursively with the same rules and
inherits the presets of the mods that need it.

Usage:
    python3 tools/pack/resolve_manifest.py [--selection PATH] [--out PATH]

Run it only when you want to change/upgrade the mod list; build_mrpack.py and
server_test.py work offline from manifest.json.
"""
import argparse
import datetime
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
UA = "kotln-modpack/0.1 (bradley09roberts)"
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))  # kingdoms-of-the-long-night/
TYPE_RANK = {"release": 0, "beta": 1}  # alpha deliberately absent


def api_get(path, params=None):
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    last = None
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            last = e
            time.sleep(2 * (attempt + 1))
        except urllib.error.URLError as e:
            last = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"GET {url} failed: {last}")


class Resolver:
    def __init__(self, pack):
        self.mc = pack["minecraft"]
        self.loader = pack["loader"]
        self.projects = {}   # id -> project json
        self.slug_to_id = {}
        self.versions = {}   # (id, kind) -> list
        self.warnings = []

    def project(self, ref):
        pid = self.slug_to_id.get(ref, ref)
        if pid in self.projects:
            return self.projects[pid]
        p = api_get(f"/project/{ref}")
        if p is None:
            raise SystemExit(f"ERROR: Modrinth project '{ref}' not found")
        self.projects[p["id"]] = p
        self.slug_to_id[p["slug"]] = p["id"]
        self.slug_to_id[ref] = p["id"]
        return p

    def candidate_versions(self, p):
        kind = p["project_type"]
        key = (p["id"], kind)
        if key not in self.versions:
            if kind == "mod":
                loaders = [self.loader]
            elif kind == "shader":
                loaders = ["iris"]
            else:  # resourcepack / datapack
                loaders = None
            params = {"game_versions": json.dumps([self.mc])}
            if loaders:
                params["loaders"] = json.dumps(loaders)
            vs = api_get(f"/project/{p['id']}/version", params) or []
            # double-check filters client-side
            vs = [v for v in vs if self.mc in v["game_versions"] and (not loaders or set(loaders) & set(v["loaders"]))]
            self.versions[key] = vs
        return self.versions[key]

    def pick(self, p, pin=None):
        vs = self.candidate_versions(p)
        if not vs:
            raise SystemExit(f"ERROR: {p['slug']} has no {self.loader} {self.mc} version on Modrinth")
        if pin:
            for v in vs:
                if v["version_number"] == pin or v["id"] == pin:
                    return v
            raise SystemExit(f"ERROR: pinned version {pin} of {p['slug']} not found")
        stable = [v for v in vs if v["version_type"] in TYPE_RANK]
        if not stable:
            raise SystemExit(f"ERROR: {p['slug']} has only alpha builds for {self.loader} {self.mc}")
        best_rank = min(TYPE_RANK[v["version_type"]] for v in stable)
        pool = [v for v in stable if TYPE_RANK[v["version_type"]] == best_rank]
        pool.sort(key=lambda v: v["date_published"], reverse=True)
        return pool[0]


def primary_file(v):
    files = [f for f in v["files"] if f.get("primary")] or v["files"]
    return files[0]


def server_env(p, entry):
    if entry.get("server_env"):
        return entry["server_env"]
    return "unsupported" if p["server_side"] == "unsupported" else "required"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--selection", default=os.path.join(ROOT, "modpack", "selection.json"))
    ap.add_argument("--out", default=os.path.join(ROOT, "modpack", "manifest.json"))
    args = ap.parse_args()

    sel = json.load(open(args.selection, encoding="utf-8"))
    pack = sel["pack"]
    r = Resolver(pack)

    entries = {}  # project id -> manifest entry
    order = []
    queue = []
    for m in sel["mods"]:
        p = r.project(m["slug"])
        queue.append((p, m, None))

    while queue:
        p, m, parent = queue.pop(0)
        pid = p["id"]
        if pid in entries:
            e = entries[pid]
            changed = False
            for pr in m["presets"]:
                if pr not in e["presets"]:
                    e["presets"].append(pr)
                    changed = True
            if parent and parent not in e["required_by"]:
                e["required_by"].append(parent)
            if m.get("category") not in (None, "library") and e["category"] == "library":
                # explicitly selected after being pulled in as a dependency
                e.update(category=m["category"], reason=m.get("reason"))
            if changed:  # propagate new presets to its own deps
                for dep_pid in e["_dep_ids"]:
                    queue.append((r.projects[dep_pid], {"presets": list(e["presets"]), "slug": r.projects[dep_pid]["slug"]}, e["slug"]))
            continue
        v = r.pick(p, m.get("pin"))
        f = primary_file(v)
        ptype = p["project_type"]
        folder = {"mod": "mods", "shader": "shaderpacks", "resourcepack": "resourcepacks"}.get(ptype, "mods")
        dep_ids = []
        dep_notes = []
        for d in v["dependencies"]:
            if d["dependency_type"] == "incompatible" and d.get("project_id"):
                dep_notes.append(("incompatible", d["project_id"]))
            if d["dependency_type"] != "required" or not d.get("project_id"):
                continue
            dep_ids.append(d["project_id"])
            dp = r.project(d["project_id"])
            if d.get("version_id"):
                dep_notes.append(("pinned_version", d["project_id"], d["version_id"]))
            queue.append((dp, {"slug": dp["slug"], "presets": list(m["presets"]),
                               "category": "library", "reason": None}, p["slug"]))
        e = {
            "slug": p["slug"],
            "title": p["title"],
            "project_id": pid,
            "project_type": ptype,
            "version_id": v["id"],
            "version_number": v["version_number"],
            "version_type": v["version_type"],
            "date_published": v["date_published"],
            "filename": f["filename"],
            "path": f"{folder}/{f['filename']}",
            "url": f["url"],
            "sha1": f["hashes"]["sha1"],
            "sha512": f["hashes"]["sha512"],
            "size": f["size"],
            "license": p["license"]["id"],
            "license_url": p["license"].get("url"),
            "project_client_side": p["client_side"],
            "project_server_side": p["server_side"],
            "env": {"client": "required", "server": server_env(p, m)},
            "required_deps": [],  # filled below (slugs)
            "required_by": [parent] if parent else [],
            "category": m.get("category") or "library",
            "reason": m.get("reason"),
            "presets": list(m["presets"]),
            "pinned": bool(m.get("pin")),
            "pin_reason": m.get("pin_reason"),
            "homepage": f"https://modrinth.com/{ptype}/{p['slug']}",
            "_dep_ids": dep_ids,
            "_dep_notes": dep_notes,
        }
        entries[pid] = e
        order.append(pid)

    # post-processing: dep slugs, reasons for libraries, consistency checks
    for pid in order:
        e = entries[pid]
        e["required_deps"] = [r.projects[d]["slug"] for d in e["_dep_ids"]]
        if e["reason"] is None:
            e["reason"] = "Required library for " + ", ".join(e["required_by"]) + "."
        if e["category"] == "library" and e["env"]["server"] == "required":
            # a library is only needed on the server if something server-side needs it
            needers = [x for x in entries.values() if e["slug"] in [r.projects[d]["slug"] for d in x["_dep_ids"]]]
            if needers and all(n["env"]["server"] == "unsupported" for n in needers):
                e["env"]["server"] = "unsupported"
        for note in e["_dep_notes"]:
            if note[0] == "incompatible" and note[1] in entries:
                other = entries[note[1]]
                common = set(other["presets"]) & set(e["presets"])
                if common:
                    raise SystemExit(f"ERROR: {e['slug']} declares {other['slug']} incompatible (presets {sorted(common)})")
            if note[0] == "pinned_version" and note[1] in entries:
                if entries[note[1]]["version_id"] != note[2]:
                    r.warnings.append(f"{e['slug']} {e['version_number']} declares dependency {entries[note[1]]['slug']} pinned to version id {note[2]}, "
                                      f"manifest uses {entries[note[1]]['version_number']} ({entries[note[1]]['version_id']})")
        e["presets"].sort()
        e["required_by"].sort()

    files = []
    for pid in order:
        e = dict(entries[pid])
        e.pop("_dep_ids")
        e.pop("_dep_notes")
        files.append(e)
    files.sort(key=lambda x: (x["path"].lower()))

    manifest = {
        "schema": 1,
        "pack": pack,
        "presets": sel["presets"],
        "resolved_at": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "resolver": "tools/pack/resolve_manifest.py (Modrinth API v2; newest release, else beta; never alpha; pins honoured)",
        "warnings": r.warnings,
        "files": files,
        "rejected": sel.get("rejected", []),
    }
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    for pr in sel["presets"]:
        n = [f for f in files if pr in f["presets"]]
        mods = [f for f in n if f["project_type"] == "mod"]
        print(f"preset {pr}: {len(mods)} mods, {len(n) - len(mods)} other files, "
              f"{sum(1 for f in mods if f['env']['server'] != 'unsupported')} server-side mods")
    for w in r.warnings:
        print("WARNING:", w)
    for f in files:
        print(f"  {f['slug']:32} {f['version_number']:36} {f['version_type']:7} {f['env']['server']:11} {','.join(f['presets']):20} {f['license']}")
    print("wrote", os.path.relpath(args.out))


if __name__ == "__main__":
    main()
