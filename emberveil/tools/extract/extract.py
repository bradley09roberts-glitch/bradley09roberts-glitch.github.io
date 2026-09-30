#!/usr/bin/env python3
"""Extract gameplay data from the locked mod jars into data/extracted/<modid>.json.

Captured per jar: en_us lang, entity/item/block ids (from lang keys), loot tables, structures,
structure sets, biome modifiers, biome tags, recipes (ids + result), Better Combat weapon
attributes, Curios slot definitions and neoforge.mods.toml metadata. The book generator and
validator read these files so documentation references real ids and real names."""
import json, zipfile, pathlib, os, re, sys, tomllib
ROOT = pathlib.Path(__file__).resolve().parents[2]
cache = pathlib.Path(os.environ.get("EMBERVEIL_CACHE", ROOT / ".cache")) / "mods"
out_dir = pathlib.Path(os.environ.get("EXTRACT_OUT", ROOT / ".cache/extracted"))
out_dir.mkdir(parents=True, exist_ok=True)

def jload(zf, name):
    try:
        return json.loads(zf.read(name).decode("utf-8-sig"))
    except Exception as e:
        try:  # some mods ship JSON with comments / trailing commas
            txt = re.sub(r"//.*", "", zf.read(name).decode("utf-8-sig", "replace"))
            txt = re.sub(r",\s*([}\]])", r"\1", txt)
            return json.loads(txt)
        except Exception:
            return {"_error": str(e)}

def summarize(jar):
    zf = zipfile.ZipFile(jar)
    names = zf.namelist()
    info = {"jar": jar.name, "mods": [], "lang": {}, "loot_tables": {}, "structures": {}, "structure_sets": {},
            "biome_modifiers": {}, "biome_tags": {}, "recipes": {}, "weapon_attributes": {}, "curios": {},
            "advancements": [], "entity_ids": [], "item_ids": [], "block_ids": [], "configs": []}
    for tn in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
        if tn in names:
            t = tomllib.loads(zf.read(tn).decode("utf-8", "replace"))
            info["mods"] = [{"modId": m["modId"], "displayName": m.get("displayName"), "description": (m.get("description") or "").strip()[:400]} for m in t.get("mods", [])]
            info["license"] = t.get("license")
            break
    for n in names:
        m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
        if m:
            d = jload(zf, n)
            if isinstance(d, dict): info["lang"].update(d)
            continue
        m = re.match(r"data/([^/]+)/loot_tables?/(.+)\.json$", n)
        if m: info["loot_tables"][f"{m[1]}:{m[2]}"] = jload(zf, n); continue
        m = re.match(r"data/([^/]+)/worldgen/structure/(.+)\.json$", n)
        if m: info["structures"][f"{m[1]}:{m[2]}"] = jload(zf, n); continue
        m = re.match(r"data/([^/]+)/worldgen/structure_set/(.+)\.json$", n)
        if m: info["structure_sets"][f"{m[1]}:{m[2]}"] = jload(zf, n); continue
        m = re.match(r"data/([^/]+)/neoforge/biome_modifier/(.+)\.json$", n) or re.match(r"data/([^/]+)/forge/biome_modifier/(.+)\.json$", n)
        if m: info["biome_modifiers"][f"{m[1]}:{m[2]}"] = jload(zf, n); continue
        m = re.match(r"data/([^/]+)/tags/worldgen/biome/(.+)\.json$", n)
        if m: info["biome_tags"][f"{m[1]}:{m[2]}"] = jload(zf, n); continue
        m = re.match(r"data/([^/]+)/recipes?/(.+)\.json$", n)
        if m:
            r = jload(zf, n)
            res = r.get("result") if isinstance(r, dict) else None
            if isinstance(res, dict): res = res.get("id") or res.get("item")
            info["recipes"][f"{m[1]}:{m[2]}"] = {"type": r.get("type") if isinstance(r, dict) else None, "result": res}
            continue
        m = re.match(r"data/([^/]+)/weapon_attributes/(.+)\.json$", n)
        if m: info["weapon_attributes"][f"{m[1]}:{m[2]}"] = jload(zf, n); continue
        m = re.match(r"data/([^/]+)/curios/(slots|entities)/(.+)\.json$", n)
        if m: info["curios"][f"{m[1]}:{m[2]}/{m[3]}"] = jload(zf, n); continue
        m = re.match(r"data/([^/]+)/advancements?/(.+)\.json$", n)
        if m: info["advancements"].append(f"{m[1]}:{m[2]}"); continue
        if re.match(r"^(config|defaultconfigs)/", n) or n.endswith((".toml", ".json5")) and "config" in n.lower():
            info["configs"].append(n)
    for k in info["lang"]:
        m = re.match(r"^entity\.([a-z0-9_.-]+)\.([a-z0-9_/.-]+)$", k)
        if m and "." not in m[2]: info["entity_ids"].append(f"{m[1]}:{m[2]}")
        m = re.match(r"^item\.([a-z0-9_.-]+)\.([a-z0-9_/-]+)$", k)
        if m: info["item_ids"].append(f"{m[1]}:{m[2]}")
        m = re.match(r"^block\.([a-z0-9_.-]+)\.([a-z0-9_/-]+)$", k)
        if m: info["block_ids"].append(f"{m[1]}:{m[2]}")
    return info

index = {}
for jar in sorted(cache.glob("*.jar")):
    info = summarize(jar)
    key = info["mods"][0]["modId"] if info["mods"] else jar.stem
    (out_dir / f"{key}.json").write_text(json.dumps(info, indent=1, sort_keys=True))
    index[key] = {k: (len(v) if isinstance(v, (list, dict)) else v) for k, v in info.items() if k not in ("mods",)}
    index[key]["jar"] = jar.name
print(f"{'mod':28s} {'ent':>4s} {'item':>5s} {'blk':>4s} {'loot':>5s} {'strc':>5s} {'sset':>4s} {'bmod':>4s} {'rcp':>5s} {'watt':>4s}")
for k, v in index.items():
    print(f"{k:28s} {v['entity_ids']:4d} {v['item_ids']:5d} {v['block_ids']:4d} {v['loot_tables']:5d} {v['structures']:5d} {v['structure_sets']:4d} {v['biome_modifiers']:4d} {v['recipes']:5d} {v['weapon_attributes']:4d}")
(out_dir / "_index.json").write_text(json.dumps(index, indent=1))
