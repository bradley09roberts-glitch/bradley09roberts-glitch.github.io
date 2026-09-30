#!/usr/bin/env python3
"""Static dependency validation: read META-INF/neoforge.mods.toml from every cached jar (including
jar-in-jar libraries), then check each mandatory/required dependency exists with a version inside
its declared Maven range. Also reports declared incompatibilities and the NeoForge floor."""
import json, sys, zipfile, tomllib, re, pathlib, os, io
ROOT = pathlib.Path(__file__).resolve().parents[1]
cache = pathlib.Path(os.environ.get("EMBERVEIL_CACHE", ROOT / ".cache")) / "mods"
extra = [pathlib.Path(p) for p in sys.argv[1:]]  # e.g. companion mod jar
MC, NEOFORGE = "1.21.1", os.environ.get("NEOFORGE_VERSION", "21.1.252")

def tokens(v):
    v = v.lower().replace("-", ".").replace("+", ".").replace("_", ".")
    out = []
    for part in re.split(r"\.", v):
        for t in re.findall(r"\d+|[a-z]+", part):
            out.append((0, int(t)) if t.isdigit() else (-1 if t in ("alpha","beta","rc","snapshot","pre") else 1, t))
    while out and out[-1] == (0, 0): out.pop()
    return out
def cmp(a, b):
    ta, tb = tokens(a), tokens(b)
    for x, y in zip(ta, tb):
        if x != y:
            if x[0] != y[0]: return -1 if x[0] < y[0] else 1
            return -1 if x[1] < y[1] else 1
    if len(ta) == len(tb): return 0
    longer = ta if len(ta) > len(tb) else tb
    nxt = longer[min(len(ta), len(tb))]
    r = -1 if nxt[0] == -1 else 1
    return r if longer is ta else -r
def in_range(ver, rng):
    rng = rng.strip()
    if rng in ("", "*"): return True
    for part in re.findall(r"[\[\(][^\]\)]*[\]\)]", rng) or [rng]:
        if not part.startswith(("[", "(")):
            return cmp(ver, part) >= 0
        lo_inc, hi_inc = part[0] == "[", part[-1] == "]"
        body = part[1:-1]
        if "," not in body:
            if cmp(ver, body) == 0: return True
            continue
        lo, hi = [x.strip() for x in body.split(",", 1)]
        ok = True
        if lo: ok &= cmp(ver, lo) >= 0 if lo_inc else cmp(ver, lo) > 0
        if hi: ok &= cmp(ver, hi) <= 0 if hi_inc else cmp(ver, hi) < 0
        if ok: return True
    return False

mods = {}   # modid -> (version, source)
reqs = []   # (owner, modid, range, type, side)
def read_toml(data, source, jarver):
    t = tomllib.loads(data.decode("utf-8", "replace"))
    for m in t.get("mods", []):
        mid = m["modId"]; ver = str(m.get("version", "?"))
        if "${file.jarVersion}" in ver: ver = jarver or ver
        mods.setdefault(mid, (ver, source))
        deps = t.get("dependencies", {}).get(mid, [])
        for d in deps:
            typ = d.get("type") or ("required" if d.get("mandatory", False) else "optional")
            reqs.append((mid, d["modId"], d.get("versionRange", "*"), typ.lower(), d.get("side", "BOTH")))
def scan(zf, source):
    names = set(zf.namelist())
    jarver = None
    if "META-INF/MANIFEST.MF" in names:
        mf = zf.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
        mm = re.search(r"Implementation-Version:\s*(\S+)", mf); jarver = mm.group(1) if mm else None
    for tn in ("META-INF/neoforge.mods.toml", "META-INF/mods.toml"):
        if tn in names:
            read_toml(zf.read(tn), source, jarver); break
    if "META-INF/jarjar/metadata.json" in names:
        meta = json.loads(zf.read("META-INF/jarjar/metadata.json"))
        for j in meta.get("jars", []):
            path = j["path"]
            if path in names:
                try:
                    with zipfile.ZipFile(io.BytesIO(zf.read(path))) as inner:
                        scan(inner, source + "!" + path.split("/")[-1])
                except zipfile.BadZipFile: pass
for jar in sorted(cache.glob("*.jar")) + extra:
    with zipfile.ZipFile(jar) as zf: scan(zf, jar.name)
mods["minecraft"] = (MC, "game"); mods["neoforge"] = (NEOFORGE, "loader"); mods["java"] = ("21", "jvm")
# FML 4.x VersionSupportMatrix (verified in fancymodloader loader-4.0.44): on Minecraft 1.21.1 a mod
# whose range admits Minecraft "1.21" (or NeoForge "21.0.166") is also accepted.
def support_matrix(dep, rng):
    if MC == "1.21.1" and dep == "minecraft": return in_range("1.21", rng)
    if MC == "1.21.1" and dep == "neoforge": return in_range("21.0.166", rng)
    return False

problems = 0
nf_floor = []
for owner, dep, rng, typ, side in reqs:
    if dep == "neoforge": nf_floor.append((rng, owner))
    if typ in ("required", "mandatory"):
        if dep not in mods:
            print(f"MISSING  {owner} requires {dep} {rng} (side={side})"); problems += 1
        elif not in_range(mods[dep][0], rng) and not support_matrix(dep, rng):
            print(f"VERSION  {owner} requires {dep} {rng} but found {mods[dep][0]} ({mods[dep][1]})"); problems += 1
    elif typ == "incompatible" and dep in mods and in_range(mods[dep][0], rng):
        print(f"INCOMPAT {owner} declares incompatible with {dep} {rng} (found {mods[dep][0]})"); problems += 1
    elif typ == "discouraged" and dep in mods and in_range(mods[dep][0], rng):
        print(f"DISCOURAGED {owner} vs {dep} {rng}")
print(f"\n{len(mods)} mod ids present; NeoForge {NEOFORGE}; required-dependency problems: {problems}")
print("NeoForge ranges declared:", sorted(set(r for r, _ in nf_floor)))
json.dump({k: v[0] for k, v in sorted(mods.items())}, open(os.environ.get("MODIDS_OUT", "/dev/null"), "w"), indent=1)
sys.exit(1 if problems else 0)
