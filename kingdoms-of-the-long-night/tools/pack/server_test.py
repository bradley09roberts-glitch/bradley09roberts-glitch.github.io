#!/usr/bin/env python3
"""Boot-test a preset of the pack on a real NeoForge dedicated server (Python 3 stdlib + Java 21).

Steps: install NeoForge server (installer cached) -> download every server-side file of the
preset from cdn.modrinth.com (sha512/sha1 verified) -> add our longnight jar if
mod/build/libs/longnight-*.jar exists -> copy pack config overrides -> write eula/server.properties
(TEST ENVIRONMENT ONLY: eula=true, online-mode=false) -> start with a FRESH world -> wait for
"Done (" -> optionally force-generate far chunks and /place every modded structure -> stop ->
scan logs for missing deps, mod-loading errors, mixin failures, world-gen errors and exceptions.

Usage:
    python3 tools/pack/server_test.py [--preset performance|rich] [--workdir DIR] [--port 25599]
        [--xmx 4G] [--with-longnight auto|yes|no] [--no-worldgen-probe] [--timeout 900]
        [--seed S] [--extra-command "locate structure longnight:..."]... [--keep-running SECONDS]
        [--manifest other_manifest.json]

Prints PASS/FAIL, boot time, loaded mods and the log path, and writes
<workdir>/<preset>/server_test_summary.json. Exit code 0 = PASS. Default workdir:
$KOTLN_CACHE/server-test (KOTLN_CACHE defaults to $TMPDIR/kotln-pack-cache).
"""
import argparse
import json
import os
import queue
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import packlib  # noqa: E402

FATAL_PATTERNS = [
    (r"Missing or unsupported mandatory dependencies", "missing dependency"),
    (r"Mod .* requires .* (or above|version)", "dependency version"),
    (r"Mixin apply (for mod \S+ )?failed", "mixin apply failed"),
    (r"MixinApplyError|MixinTransformerError|InvalidMixinException|InjectionError", "mixin error"),
    (r"Failed to (create|load) mod|ModLoadingException|LoadingFailedException", "mod loading error"),
    (r"Error loading registry data|Registry loading errors|Failed to load registries", "registry/datapack error"),
    (r"Feature order cycle", "worldgen feature order cycle"),
    (r"Failed to (parse|load|decode) .*(worldgen|structure|biome|feature|noise|density)", "worldgen parse error"),
    (r"Unknown registry key|Unbound values in registry", "unknown registry reference"),
    (r"Exception in server tick loop|Encountered an unexpected exception", "server crash"),
    (r"This crash report has been saved to", "crash report"),
    (r"Detected setBlock in a far chunk|Recursive call|Chunk generation failed|Failed to generate chunk", "chunk generation error"),
]
# ERROR lines that were investigated and are known to be cosmetic (reported as KNOWN, do not fail the test)
KNOWN_ERRORS = [
    (r"Block-attached entity at invalid position",
     "vanilla 1.21 logs this when a structure template stores an item frame/painting with absolute TileX/Y/Z "
     "from the author's world (seen with YUNG's Better Desert Temples); the structure still generates"),
]
# lines that look scary but are known-benign on a vanilla/NeoForge server
BENIGN_PATTERNS = [
    r"Failed to load properties from file: server\.properties",
    r"Failed to load eula\.txt",
    r"Ambiguity between arguments",
    r"Can't keep up!",
    r"Configuration file .* is not correct\. Correcting",
    r"Incorrect key .* was corrected",
    r"Exception loading blockstate definition",  # never on a server; listed for completeness
]


def http_get(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": packlib.UA})
    with urllib.request.urlopen(req, timeout=300) as r, open(dest + ".part", "wb") as out:
        shutil.copyfileobj(r, out)
    os.replace(dest + ".part", dest)


def install_server(neo_version, server_dir, cache):
    inst_dir = os.path.join(cache, "neoforge")
    os.makedirs(inst_dir, exist_ok=True)
    installer = os.path.join(inst_dir, f"neoforge-{neo_version}-installer.jar")
    if not os.path.exists(installer):
        url = (f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{neo_version}/"
               f"neoforge-{neo_version}-installer.jar")
        print("downloading", url)
        http_get(url, installer)
    with zipfile.ZipFile(installer) as z:
        z.testzip()
    marker = os.path.join(server_dir, "libraries", "net", "neoforged", "neoforge", neo_version)
    if os.path.isdir(marker) and os.path.exists(os.path.join(server_dir, "run.sh")):
        print("NeoForge server already installed in", server_dir)
        return
    os.makedirs(server_dir, exist_ok=True)
    print(f"installing NeoForge {neo_version} server into {server_dir} ...")
    log = os.path.join(server_dir, "installer-output.log")
    with open(log, "w") as fh:
        rc = subprocess.call(["java", "-jar", installer, "--installServer", server_dir],
                             cwd=server_dir, stdout=fh, stderr=subprocess.STDOUT)
    if rc != 0:
        raise SystemExit(f"NeoForge installer failed (rc={rc}), see {log}")


def server_java_cmd(server_dir, xmx):
    """Equivalent of run.sh: java @user_jvm_args.txt @libraries/.../unix_args.txt nogui"""
    args_files = []
    for root, _, files in os.walk(os.path.join(server_dir, "libraries", "net", "neoforged", "neoforge")):
        if "unix_args.txt" in files:
            args_files.append(os.path.relpath(os.path.join(root, "unix_args.txt"), server_dir))
    if len(args_files) != 1:
        raise SystemExit(f"expected exactly one unix_args.txt, found {args_files}")
    with open(os.path.join(server_dir, "user_jvm_args.txt"), "w") as fh:
        fh.write(f"-Xmx{xmx}\n-Xms1G\n")
    return ["java", "@user_jvm_args.txt", "@" + args_files[0], "nogui"]


def structure_ids(jar_paths):
    ids = set()
    for p in jar_paths:
        with zipfile.ZipFile(p) as z:
            for n in z.namelist():
                m = re.match(r"data/([a-z0-9_.-]+)/worldgen/structure/([a-z0-9_./-]+)\.json$", n)
                if m and m.group(1) != "minecraft":
                    ids.add(f"{m.group(1)}:{m.group(2)}")
    return sorted(ids)


class Server:
    def __init__(self, cmd, cwd, console_log):
        self.t0 = time.monotonic()
        self.proc = subprocess.Popen(cmd, cwd=cwd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, bufsize=1, errors="replace")
        self.lines = queue.Queue()
        self.console = open(console_log, "w")
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for line in self.proc.stdout:
            self.console.write(line)
            self.console.flush()
            self.lines.put(line)
        self.lines.put(None)

    def wait_for(self, regex, timeout):
        rx = re.compile(regex)
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            try:
                line = self.lines.get(timeout=1)
            except queue.Empty:
                if self.proc.poll() is not None and self.lines.empty():
                    return None
                continue
            if line is None:
                return None
            if rx.search(line):
                return line
        return None

    def send(self, cmd):
        if self.proc.poll() is None:
            self.proc.stdin.write(cmd + "\n")
            self.proc.stdin.flush()


def scan_logs(server_dir):
    findings = []
    logs = [os.path.join(server_dir, "logs", "latest.log")]
    for lp in logs:
        if not os.path.exists(lp):
            findings.append(("FATAL", "no logs/latest.log", lp))
            continue
        with open(lp, errors="replace") as fh:
            lines = fh.readlines()
        for i, line in enumerate(lines):
            if any(re.search(b, line) for b in BENIGN_PATTERNS):
                continue
            hit = False
            for rx, what in FATAL_PATTERNS:
                if re.search(rx, line):
                    findings.append(("FATAL", what, line.strip()[:400]))
                    hit = True
                    break
            if hit:
                continue
            if re.search(r"/(ERROR|FATAL)\]", line):
                known = next((why for rx, why in KNOWN_ERRORS if re.search(rx, line)), None)
                if known:
                    findings.append(("KNOWN", known, line.strip()[:400]))
                else:
                    findings.append(("ERROR", "error log line", line.strip()[:400]))
            elif re.search(r"^\s*(at |Caused by:)|Exception:|Exception$|\bException\b in", line) and "WARN" not in line:
                findings.append(("EXC", "exception text", line.strip()[:300]))
            elif re.search(r"/WARN\]", line):
                findings.append(("WARN", "warning", line.strip()[:300]))
    crash_dir = os.path.join(server_dir, "crash-reports")
    if os.path.isdir(crash_dir) and os.listdir(crash_dir):
        for f in os.listdir(crash_dir):
            findings.append(("FATAL", "crash report", os.path.join(crash_dir, f)))
    return findings


def warning_kinds(warns):
    """Group WARN lines by logger + message shape (numbers stripped), most frequent first."""
    counts = {}
    for _, _, line in warns:
        body = re.sub(r"^\[[^]]*\] \[[^]]*\] ", "", line)
        body = re.sub(r"\d+", "N", body)[:150]
        counts[body] = counts.get(body, 0) + 1
    return sorted(((n, t) for t, n in counts.items()), reverse=True)


def loaded_mods(server_dir):
    """Parse FML's 'Mod List:' table (name version (modid)) from logs/latest.log."""
    mods = {}
    p = os.path.join(server_dir, "logs", "latest.log")
    if not os.path.exists(p):
        return mods
    in_table = False
    with open(p, errors="replace") as fh:
        for line in fh:
            if "Mod List:" in line:
                in_table = True
                continue
            if in_table:
                if line.startswith("["):
                    break
                m = re.match(r"^\s+(.+?) (\S+) \(([a-z0-9_.-]+)\)\s*$", line)
                if m:
                    mods[m.group(3)] = m.group(2)
    return mods


def worldgen_probe(srv, ids):
    """Force-generate two far 16x16-chunk areas (terrain/biomes/features far from spawn), then
    /place every modded structure in the middle of the first area."""
    out = {"structures_placed": [], "structures_skipped": [], "structures_failed": []}
    print(f"world-gen probe: force-generating 2 x 256 far chunks, then /place {len(ids)} modded structures")
    t0 = time.monotonic()
    # forceload loads chunks synchronously inside one server tick, so go in 8x8-chunk batches (one
    # command per tick, waiting for each reply) to stay far below the 60 s watchdog (max-tick-time).
    for x0, z0 in ((4000, 4000), (-4000, -4000)):
        for dx in (0, 128):
            for dz in (0, 128):
                x, z = x0 + dx, z0 + dz
                srv.send(f"forceload add {x} {z} {x + 127} {z + 127}")
                if not srv.wait_for(r"Marked \d+ chunks|No chunks were marked|too many chunks", 300):
                    out["structures_failed"].append(("<forceload>", f"no reply to forceload at {x},{z}"))
                    return out
    ready = None
    while time.monotonic() - t0 < 900:
        srv.send("execute if loaded 4000 0 4000 if loaded 4255 0 4255 if loaded 4128 0 4128 "
                 "if loaded -4000 0 -4000 if loaded -3745 0 -3745 run say KOTLN_AREA_READY")
        ready = srv.wait_for(r"KOTLN_AREA_READY", 20)
        if ready:
            break
    out["far_chunks_seconds"] = round(time.monotonic() - t0, 1) if ready else None
    print(f"  far chunks loaded: {bool(ready)} after {time.monotonic() - t0:.0f}s")
    if not ready:
        out["structures_failed"].append(("<area>", "forceloaded chunks never finished loading"))
        return out
    rx = (r"Generated structure|Failed to place structure|That position is not loaded|Unknown|"
          r"Incorrect argument|Invalid|Can't find|not a valid|Exception")
    for sid in ids:
        srv.send(f"place structure {sid} 4128 80 4128")
        line = srv.wait_for(rx, 180)
        msg = (line or "timeout (no response in 180s)").strip()[-220:]
        if line and "Generated structure" in line:
            out["structures_placed"].append(sid)
        elif line and ("not loaded" in line or "Failed to place structure" in line):
            # bigger than the probe area / placement rules not met at this spot: not an error
            out["structures_skipped"].append((sid, msg))
        else:
            out["structures_failed"].append((sid, msg))
    srv.send("forceload remove all")
    srv.send("save-all flush")
    srv.wait_for(r"Saved the game", 300)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--manifest", default=packlib.MANIFEST)
    ap.add_argument("--preset", default="performance")
    ap.add_argument("--workdir", default=os.path.join(packlib.DEFAULT_CACHE, "server-test"))
    ap.add_argument("--cache", default=packlib.DEFAULT_CACHE)
    ap.add_argument("--port", type=int, default=25599)
    ap.add_argument("--xmx", default="4G")
    ap.add_argument("--timeout", type=int, default=900, help="seconds to wait for 'Done ('")
    ap.add_argument("--with-longnight", choices=["auto", "yes", "no"], default="auto")
    ap.add_argument("--no-worldgen-probe", action="store_true",
                    help="skip far-chunk generation and /place structure probes")
    ap.add_argument("--seed", default="", help="level-seed (default: random)")
    ap.add_argument("--extra-command", action="append", default=[],
                    help="console command to run after Done (repeatable); its reply is recorded")
    ap.add_argument("--keep-running", type=int, default=0, metavar="SECONDS",
                    help="keep the server ticking this long after the probes before stopping")
    args = ap.parse_args()

    man = packlib.load_manifest(args.manifest)
    neo = man["pack"]["loader_version"]
    server_dir = os.path.abspath(os.path.join(args.workdir, args.preset))
    install_server(neo, server_dir, args.cache)

    # --- mods
    mods_dir = os.path.join(server_dir, "mods")
    shutil.rmtree(mods_dir, ignore_errors=True)
    os.makedirs(mods_dir)
    files = [f for f in packlib.preset_files(man, args.preset, "server") if f["path"].startswith("mods/")]
    jar_paths = []
    for f in files:
        p = packlib.download(f, args.cache)
        shutil.copy2(p, os.path.join(mods_dir, f["filename"]))
        jar_paths.append(p)
    ln = None
    if args.with_longnight != "no":
        ln = packlib.find_longnight_jar()
        if ln:
            shutil.copy2(ln, os.path.join(mods_dir, os.path.basename(ln)))
            jar_paths.append(ln)
        elif args.with_longnight == "yes":
            raise SystemExit("FAIL: --with-longnight yes but mod/build/libs/longnight-*.jar not found")
        else:
            print("WARNING: mod/build/libs/longnight-*.jar not found - testing without the longnight core mod")
    print(f"installed {len(files)} mods from manifest" + (f" + {os.path.basename(ln)}" if ln else ""))

    # --- configs, fresh world, eula, properties
    for d in ("config", "defaultconfigs", "world", "logs", "crash-reports"):
        shutil.rmtree(os.path.join(server_dir, d), ignore_errors=True)
    copied = packlib.write_overrides(man, args.preset, server_dir, only_prefixes=("config/", "defaultconfigs/"))
    with open(os.path.join(server_dir, "eula.txt"), "w") as fh:
        fh.write("# TEST ENVIRONMENT ONLY - automated boot test of the modpack\neula=true\n")
    props = {
        "online-mode": "false", "server-port": str(args.port), "level-name": "world",
        "level-seed": args.seed, "motd": f"KotLN boot test ({args.preset})", "max-players": "2",
        "view-distance": "8", "simulation-distance": "8", "spawn-protection": "0",
        "enable-command-block": "false", "sync-chunk-writes": "true", "difficulty": "hard",
        "hardcore": "true", "gamemode": "survival",
    }
    with open(os.path.join(server_dir, "server.properties"), "w") as fh:
        fh.write("# written by tools/pack/server_test.py (test environment)\n")
        for k in sorted(props):
            fh.write(f"{k}={props[k]}\n")

    cmd = server_java_cmd(server_dir, args.xmx)
    console_log = os.path.join(server_dir, "console.log")
    print("starting:", " ".join(cmd), f"(cwd {server_dir})")
    srv = Server(cmd, server_dir, console_log)
    done = srv.wait_for(r"\]: Done \(", args.timeout)
    boot_wall = time.monotonic() - srv.t0
    probe = {}
    if done:
        m = re.search(r"Done \(([\d.]+)s\)", done)
        probe["reported_done_s"] = float(m.group(1)) if m else None
        print(f"server reached Done after {boot_wall:.1f}s wall ({done.strip()})")
        if not args.no_worldgen_probe:
            probe.update(worldgen_probe(srv, structure_ids(jar_paths)))
        replies = []
        for c in ["locate structure terralith:fortified_village"] + args.extra_command:
            srv.send(c)
            line = srv.wait_for(r"\[minecraft/MinecraftServer\]: (?!Saving|Saved|Can't keep up)", 300)
            replies.append((c, (line or "no reply").strip()[-300:]))
            print(f"  > {c}\n    {replies[-1][1]}")
        probe["command_replies"] = replies
        if args.keep_running:
            time.sleep(args.keep_running)
    else:
        print(f"server did NOT reach Done within {args.timeout}s (process rc={srv.proc.poll()})")
    srv.send("stop")
    try:
        rc = srv.proc.wait(timeout=300)
    except subprocess.TimeoutExpired:
        srv.proc.kill()
        rc = "killed"
    time.sleep(1)

    findings = scan_logs(server_dir)
    mods = loaded_mods(server_dir)
    region_dir = os.path.join(server_dir, "world", "region")
    regions = sorted(os.listdir(region_dir)) if os.path.isdir(region_dir) else []
    fatal = [f for f in findings if f[0] == "FATAL"]
    errors = [f for f in findings if f[0] == "ERROR"]
    exc = [f for f in findings if f[0] == "EXC"]
    warns = [f for f in findings if f[0] == "WARN"]
    known = [f for f in findings if f[0] == "KNOWN"]
    struct_failed = probe.get("structures_failed", [])
    passed = bool(done) and rc == 0 and not fatal and not errors and not struct_failed

    summary = {
        "preset": args.preset, "result": "PASS" if passed else "FAIL",
        "boot_wall_seconds": round(boot_wall, 1), "reported_done_seconds": probe.get("reported_done_s"),
        "exit_code": rc, "mods_from_manifest": len(files), "longnight_jar": ln,
        "loaded_mod_count": len(mods), "loaded_mods": mods, "configs_copied": copied,
        "region_files": regions, "far_chunks_seconds": probe.get("far_chunks_seconds"),
        "structures_placed": probe.get("structures_placed"),
        "structures_skipped": probe.get("structures_skipped"), "structures_failed": struct_failed,
        "command_replies": probe.get("command_replies"), "fatal": fatal, "errors": errors,
        "exception_lines": len(exc), "warnings": len(warns), "known_errors": known,
        "warning_kinds": warning_kinds(warns),
        "log": os.path.join(server_dir, "logs", "latest.log"), "console": console_log,
    }
    with open(os.path.join(server_dir, "server_test_summary.json"), "w") as fh:
        json.dump(summary, fh, indent=2, default=str)

    print("\n" + "=" * 72)
    print(f" SERVER TEST {summary['result']}  preset={args.preset}  NeoForge {neo}")
    print(f" boot: {boot_wall:.1f}s wall to 'Done', server-reported {probe.get('reported_done_s')}s; exit code {rc}")
    print(f" mods: {len(files)} from manifest{' + longnight' if ln else ''}; FML loaded {len(mods)} mod ids")
    print(f" world: {len(regions)} overworld region files; far-chunk probe {probe.get('far_chunks_seconds')}s; "
          f"structures placed {len(probe.get('structures_placed') or [])}, skipped "
          f"{len(probe.get('structures_skipped') or [])} (too big for probe area / spot rejected), failed {len(struct_failed)}")
    for sid, why in struct_failed:
        print(f"   structure failed: {sid}: {why}")
    print(f" findings: {len(fatal)} fatal, {len(errors)} ERROR lines, {len(known)} known-cosmetic ERROR lines, "
          f"{len(exc)} exception lines, {len(warns)} WARN lines")
    for kind, what, line in (fatal + errors)[:40]:
        print(f"   [{kind}] {what}: {line}")
    for kind, what, line in known[:5]:
        print(f"   [KNOWN] {line.split(']: ', 1)[-1][:140]}\n           ({what})")
    for n, text in summary["warning_kinds"][:12]:
        print(f"   WARN x{n}: {text}")
    print(f" log: {summary['log']}")
    print(f" summary: {os.path.join(server_dir, 'server_test_summary.json')}")
    print("=" * 72)
    sys.exit(0 if passed else 1)


if __name__ == "__main__":
    main()
