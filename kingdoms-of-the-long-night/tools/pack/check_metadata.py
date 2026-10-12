#!/usr/bin/env python3
"""Offline loader-level check of a preset: reads every jar's META-INF/neoforge.mods.toml
(including jar-in-jar mods under META-INF/jarjar/) and verifies, the way NeoForge FML does,
that for the client set and the server set of the preset:

  * every required dependency is present and inside its versionRange,
  * optional dependencies that ARE present are inside their versionRange,
  * no 'incompatible' dependency is present inside its range ('discouraged' -> warning),
  * no mod id is provided twice by different top-level jars,
  * loaderVersion ranges accept the FML version shipped with NeoForge.

Version ranges are evaluated with a Python port of Maven's ComparableVersion/VersionRange
(the library FML uses). This catches what Modrinth dependency metadata misses (e.g. Ars Nouveau
does not declare GeckoLib/Curios on Modrinth), and is the only check client-only mods get
besides a real client launch.

Usage: python3 tools/pack/check_metadata.py [--preset performance|rich|all] [--with-longnight auto|yes|no]
Exit code 0 = no errors.
"""
import argparse
import io
import json
import os
import re
import sys
import tomllib
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import packlib  # noqa: E402

FML_VERSION = "4.0.45"   # net.neoforged.fancymodloader:loader for NeoForge 21.1.259

# (mod, dependency) pairs whose declared range is violated on paper but which were verified to load
# on the real game; reported as KNOWN instead of ERROR. Re-verify with client_test.py when versions change.
KNOWN_EXCEPTIONS = {
    ("iris", "minecraft"): "Iris 1.8.x NeoForge builds declare minecraft [1.21,1.21.1) (excludes 1.21.1 by Maven "
                           "rules) but NeoForge 21.1.259 loads it and renders shaders - verified by "
                           "tools/pack/client_test.py --preset rich (see modpack/VERIFICATION.md).",
}


# ---------------------------------------------------------------- Maven ComparableVersion port
QUALIFIERS = ["alpha", "beta", "milestone", "rc", "snapshot", "", "sp"]
ALIASES = {"ga": "", "final": "", "release": "", "cr": "rc"}
RELEASE_INDEX = str(QUALIFIERS.index(""))


def _cmp(a, b):
    return (a > b) - (a < b)


class IntItem:
    def __init__(self, v): self.v = int(v)
    def is_null(self): return self.v == 0
    def compare(self, other):
        if other is None: return 0 if self.v == 0 else 1
        if isinstance(other, IntItem): return _cmp(self.v, other.v)
        return 1  # int > string, int > list


class StringItem:
    def __init__(self, v, followed_by_digit):
        if followed_by_digit and len(v) == 1:
            v = {"a": "alpha", "b": "beta", "m": "milestone"}.get(v, v)
        self.v = ALIASES.get(v, v)
    def is_null(self): return self._cq(self.v) == RELEASE_INDEX
    @staticmethod
    def _cq(q):
        return str(QUALIFIERS.index(q)) if q in QUALIFIERS else f"{len(QUALIFIERS)}-{q}"
    def compare(self, other):
        if other is None: return _cmp(self._cq(self.v), RELEASE_INDEX)
        if isinstance(other, IntItem): return -1
        if isinstance(other, StringItem): return _cmp(self._cq(self.v), self._cq(other.v))
        return -1  # string < list


class ListItem(list):
    def is_null(self): return len(self) == 0
    def normalize(self):
        for i in range(len(self) - 1, -1, -1):
            last = self[i]
            if last.is_null():
                del self[i]
            elif not isinstance(last, ListItem):
                break
    def compare(self, other):
        if other is None:
            return 0 if len(self) == 0 else self[0].compare(None)
        if isinstance(other, IntItem): return -1
        if isinstance(other, StringItem): return 1
        for i in range(max(len(self), len(other))):
            l = self[i] if i < len(self) else None
            r = other[i] if i < len(other) else None
            if l is None:
                res = 0 if r is None else -1 * r.compare(l)
            else:
                res = l.compare(r)
            if res != 0:
                return res
        return 0


def _parse_item(is_digit, buf):
    return IntItem(buf) if is_digit else StringItem(buf, False)


def parse_version(version):
    version = version.lower()
    root = ListItem()

    lst = root
    stack = [lst]
    is_digit = False
    start = 0
    for i, c in enumerate(version):
        if c == ".":
            lst.append(IntItem(0) if i == start else _parse_item(is_digit, version[start:i]))
            start = i + 1
        elif c == "-":
            lst.append(IntItem(0) if i == start else _parse_item(is_digit, version[start:i]))
            start = i + 1
            new = ListItem(); lst.append(new); lst = new; stack.append(lst)
        elif c.isdigit():
            if not is_digit and i > start:
                lst.append(StringItem(version[start:i], True))
                start = i
                new = ListItem(); lst.append(new); lst = new; stack.append(lst)
            is_digit = True
        else:
            if is_digit and i > start:
                lst.append(_parse_item(True, version[start:i]))
                start = i
                new = ListItem(); lst.append(new); lst = new; stack.append(lst)
            is_digit = False
    if len(version) > start:
        lst.append(_parse_item(is_digit, version[start:]))
    while stack:
        stack.pop().normalize()
    return root


def vcmp(a, b):
    return parse_version(a).compare(parse_version(b))


class VersionRange:
    """Maven VersionRange. A bare version (no brackets) is a soft requirement = any version."""
    def __init__(self, spec):
        self.spec = spec = (spec or "").strip()
        self.restrictions = []  # (low, low_incl, high, high_incl)
        if spec in ("", "*"):
            self.restrictions = [(None, False, None, False)]
            return
        if spec[0] not in "[(":
            self.restrictions = [(None, False, None, False)]  # soft requirement
            return
        for part in re.findall(r"[\[\(][^\]\)]*[\]\)]", spec):
            lo_incl = part[0] == "["
            hi_incl = part[-1] == "]"
            body = part[1:-1]
            if "," not in body:
                v = body.strip()
                self.restrictions.append((v, True, v, True))
                continue
            lo, hi = (x.strip() for x in body.split(",", 1))
            self.restrictions.append((lo or None, lo_incl, hi or None, hi_incl))

    def contains(self, version):
        for lo, lo_i, hi, hi_i in self.restrictions:
            ok = True
            if lo is not None:
                c = vcmp(lo, version)
                if (c == 0 and not lo_i) or c > 0:
                    ok = False
            if ok and hi is not None:
                c = vcmp(hi, version)
                if (c == 0 and not hi_i) or c < 0:
                    ok = False
            if ok:
                return True
        return False


# ---------------------------------------------------------------- jar metadata
def _jar_version(z, raw):
    if raw and "${file.jarVersion}" in raw:
        try:
            mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
            m = re.search(r"^Implementation-Version:\s*(\S+)", mf, re.M)
            if m:
                return raw.replace("${file.jarVersion}", m.group(1))
        except KeyError:
            pass
    return raw


def read_mods(z, label, depth=0):
    """Return list of mod dicts from an open ZipFile (recursing into jarjar once)."""
    mods = []
    try:
        toml_text = z.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace")
    except KeyError:
        toml_text = None
    if toml_text:
        try:
            d = tomllib.loads(toml_text)
        except tomllib.TOMLDecodeError as e:
            mods.append({"error": f"{label}: invalid neoforge.mods.toml: {e}"})
            d = {}
        loader_range = d.get("loaderVersion", "")
        deps = d.get("dependencies", {})
        for m in d.get("mods", []):
            mid = m.get("modId")
            mods.append({
                "modId": mid,
                "version": _jar_version(z, str(m.get("version", "0"))),
                "loaderVersion": loader_range,
                "deps": deps.get(mid, []),
                "source": label,
                "nested": depth > 0,
            })
    if depth == 0:
        for name in z.namelist():
            if name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                try:
                    inner = zipfile.ZipFile(io.BytesIO(z.read(name)))
                except zipfile.BadZipFile:
                    continue
                mods.extend(read_mods(inner, f"{label}!{name.split('/')[-1]}", depth + 1))
    return mods


def dep_type(dep):
    if "type" in dep:
        return str(dep["type"]).lower()
    if "mandatory" in dep:
        return "required" if dep["mandatory"] else "optional"
    return "required"


def check_side(jars, side, mc, neo):
    """jars: list of (label, path). Returns (errors, warnings, modmap)."""
    errors, warnings = [], []
    available = {"minecraft": (mc, "builtin", False), "neoforge": (neo, "builtin", False),
                 "fml": (FML_VERSION, "builtin", False), "java": ("21", "builtin", False)}
    top_level_ids = {}
    all_mods = []
    for label, path in jars:
        with zipfile.ZipFile(path) as z:
            mods = read_mods(z, label)
        for m in mods:
            if "error" in m:
                errors.append(m["error"]); continue
            all_mods.append(m)
            mid = m["modId"]
            if not m["nested"]:
                if mid in top_level_ids and top_level_ids[mid] != label:
                    errors.append(f"duplicate mod id '{mid}' in {top_level_ids[mid]} and {label}")
                top_level_ids[mid] = label
            cur = available.get(mid)
            if cur is not None and cur[1] == "builtin":
                continue
            if not m["nested"]:
                available[mid] = (m["version"], m["source"], False)        # top-level jar wins
            elif cur is None or (cur[2] and vcmp(m["version"], cur[0]) > 0):
                available[mid] = (m["version"], m["source"], True)         # highest jar-in-jar copy
    for m in all_mods:
        if available.get(m["modId"], (None, None, None))[1] != m["source"]:
            continue  # a different copy won
        if m["loaderVersion"] and not VersionRange(str(m["loaderVersion"])).contains(FML_VERSION):
            errors.append(f"{m['modId']} ({m['source']}) loaderVersion {m['loaderVersion']} rejects FML {FML_VERSION}")
        for dep in m["deps"]:
            dside = str(dep.get("side", "BOTH")).upper()
            if dside not in ("BOTH", side.upper()):
                continue
            did = dep.get("modId")
            rng = VersionRange(str(dep.get("versionRange", "")))
            t = dep_type(dep)
            have = available.get(did)
            where = f"{m['modId']} {m['version']} ({m['source']})"
            if t == "required":
                if have is None:
                    errors.append(f"[{side}] {where} REQUIRES '{did}' {rng.spec} - missing")
                elif not rng.contains(have[0]):
                    msg = f"[{side}] {where} requires '{did}' {rng.spec} but {have[0]} is present"
                    if (m["modId"], did) in KNOWN_EXCEPTIONS:
                        warnings.append("KNOWN: " + msg + " -- " + KNOWN_EXCEPTIONS[(m["modId"], did)])
                    else:
                        errors.append(msg)
            elif t == "optional":
                if have is not None and not rng.contains(have[0]):
                    errors.append(f"[{side}] {where} supports optional '{did}' only in {rng.spec}, but {have[0]} is present")
            elif t == "incompatible":
                if have is not None and rng.contains(have[0]):
                    errors.append(f"[{side}] {where} is INCOMPATIBLE with '{did}' {rng.spec} ({have[0]} present): {dep.get('reason', '')}")
            elif t == "discouraged":
                if have is not None and rng.contains(have[0]):
                    warnings.append(f"[{side}] {where} discourages '{did}' {rng.spec} ({have[0]} present): {dep.get('reason', '')}")
    return errors, warnings, available


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--manifest", default=packlib.MANIFEST)
    ap.add_argument("--preset", default="all")
    ap.add_argument("--cache", default=packlib.DEFAULT_CACHE)
    ap.add_argument("--with-longnight", choices=["auto", "yes", "no"], default="auto")
    ap.add_argument("--json", help="write the per-preset mod id lists to this file")
    args = ap.parse_args()

    man = packlib.load_manifest(args.manifest)
    mc, neo = man["pack"]["minecraft"], man["pack"]["loader_version"]
    presets = list(man["presets"]) if args.preset == "all" else [args.preset]
    ln = None if args.with_longnight == "no" else packlib.find_longnight_jar()
    if args.with_longnight == "yes" and not ln:
        raise SystemExit("--with-longnight yes but no mod/build/libs/longnight-*.jar")
    total_err = 0
    report = {}
    for preset in presets:
        for side in ("client", "server"):
            files = [f for f in packlib.preset_files(man, preset, side) if f["path"].startswith("mods/")]
            jars = [(f["filename"], packlib.download(f, args.cache)) for f in files]
            if ln:
                jars.append((os.path.basename(ln), ln))
            errors, warnings, available = check_side(jars, side, mc, neo)
            ids = sorted(k for k, v in available.items() if v[1] != "builtin")
            report[f"{preset}/{side}"] = {k: available[k][0] for k in ids}
            status = "OK" if not errors else "FAIL"
            print(f"== {preset:12} {side:6} {len(jars):3} jars, {len(ids):3} mod ids: {status}")
            for e in errors:
                print("   ERROR  ", e)
            for w in warnings:
                print("   WARN   ", w)
            total_err += len(errors)
    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(report, fh, indent=2, sort_keys=True)
    if ln:
        print("included longnight jar:", ln)
    else:
        print("note: no longnight jar included (mod/build/libs/longnight-*.jar not found or disabled)")
    sys.exit(1 if total_err else 0)


if __name__ == "__main__":
    main()
