#!/usr/bin/env python3
"""
Scripted dedicated-server test for the longnight mod (development environment).

Boots `./gradlew runServer` on a FRESH hardcore test world, drives it over RCON:
world generation -> inspect settlements -> observe one (materialize + bodies + work)
-> simulate days -> raid -> validate invariants -> save -> restart -> validate again.
Writes a transcript to tools/test/results/<name>.txt. Exit code 0 only if every check passed.

Usage: python3 -I tools/test/server_scenario.py [--days N] [--keep-world]
"""
import argparse
import os
import re
import shutil
import signal
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rcon  # noqa: E402

MOD = os.path.normpath(os.path.join(HERE, "..", "..", "mod"))
RUN = os.path.join(MOD, "run")
RESULTS = os.path.join(HERE, "results")


def prepare(seed, keep):
    os.makedirs(RUN, exist_ok=True)
    with open(os.path.join(RUN, "eula.txt"), "w") as f:
        f.write("# automated test environment\neula=true\n")
    props = {
        "online-mode": "false", "enable-rcon": "true", "rcon.port": "25575", "rcon.password": "longnight-test",
        "server-port": "25566", "level-name": "longnight-test-world", "level-seed": str(seed), "hardcore": "true",
        "difficulty": "hard", "spawn-protection": "0", "view-distance": "6", "simulation-distance": "6",
        "sync-chunk-writes": "false",  # default watchdog (60 s) stays ON: a long tick is a failure
    }
    with open(os.path.join(RUN, "server.properties"), "w") as f:
        for k, v in props.items():
            f.write(f"{k}={v}\n")
    world = os.path.join(RUN, "longnight-test-world")
    if not keep and os.path.isdir(world):
        shutil.rmtree(world)


def start(log_path):
    log = open(log_path, "w")
    p = subprocess.Popen(["./gradlew", "runServer", "--console=plain"], cwd=MOD, stdout=log, stderr=subprocess.STDOUT,
                         stdin=subprocess.DEVNULL, preexec_fn=os.setsid)
    return p, log


def wait_done(log_path, proc, timeout=900):
    t0 = time.time()
    while time.time() - t0 < timeout:
        if proc.poll() is not None:
            return False, "server exited early"
        try:
            text = open(log_path, errors="replace").read()
        except FileNotFoundError:
            text = ""
        if re.search(r"Done \(\d", text):
            return True, f"{time.time() - t0:.0f}s"
        time.sleep(3)
    return False, "timeout"


def stop(proc, r):
    try:
        if r:
            r.cmd("stop")
    except Exception:
        pass
    try:
        proc.wait(timeout=180)
    except subprocess.TimeoutExpired:
        os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        proc.wait(timeout=60)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--days", type=int, default=6)
    ap.add_argument("--seed", type=int, default=424242)
    ap.add_argument("--name", default="scenario")
    ap.add_argument("--keep-world", action="store_true")
    a = ap.parse_args()
    os.makedirs(RESULTS, exist_ok=True)
    out = open(os.path.join(RESULTS, a.name + ".txt"), "w")
    checks = []

    def note(s):
        print(s)
        out.write(s + "\n")
        out.flush()

    def check(name, ok, detail=""):
        checks.append((name, ok))
        note(("PASS " if ok else "FAIL ") + name + (" - " + detail if detail else ""))

    prepare(a.seed, a.keep_world)
    log1 = os.path.join(RESULTS, a.name + "-server1.log")
    proc, log = start(log1)
    ok, why = wait_done(log1, proc)
    check("server boots with longnight loaded", ok, why)
    if not ok:
        note(open(log1, errors="replace").read()[-4000:])
        stop(proc, None)
        return 1
    r = rcon.connect()

    def run(c):
        t = time.time()
        res = r.cmd(c)
        took = time.time() - t
        note(f"> {c}" + (f"   [{took:.1f} s: the server thread was busy]" if took > 1.0 else "") + f"\n{res}")
        return res

    # the world's history is written on a background thread; wait for it
    t0 = time.time()
    status0 = run("longnight admin status")
    while "settlements=0 " in status0 and time.time() - t0 < 300:
        time.sleep(5)
        status0 = r.cmd("longnight admin status")
    note(f"history ready after {time.time() - t0:.0f}s: {status0}")
    check("world history generated", "settlements=" in status0 and not re.search(r"settlements=0\b", status0), status0)
    listing = run("longnight admin list")
    v = run("longnight admin validate")
    check("invariants hold after generation", "VALIDATE OK" in v)
    names = re.findall(r"SETTLEMENT \w+ (.+?) pop=", listing)
    target = names[0] if names else None
    check("at least one living settlement", target is not None, str(names))
    if target:
        run(f"longnight admin observe {target}")
        run("time set 1000")  # morning, so people work
        # let the server run a while in real time: materialize, spawn bodies, place catch-up blocks, work
        for i in range(12):
            time.sleep(10)
        insp = run(f"longnight admin inspect {target}")
        check("observed settlement materialized", "materialized=true" in insp)
        bodies = insp.count("[body]")
        check("people have bodies near an observer", bodies > 0, f"{bodies} bodies")
        placed = re.findall(r"paid=(\d+) placed=(\d+)/(\d+)", insp)
        complete = sum(1 for p, pl, t in placed if int(pl) == int(t) and int(t) > 0)
        check("pre-existing buildings raised in the world", complete > 0, f"{complete} buildings fully placed")
        st = run("longnight admin status")
        v = run("longnight admin validate")
        check("invariants hold with bodies in the world", "VALIDATE OK" in v)
        # performance with a town embodied: vanilla's tick statistics over the last 100 ticks
        time.sleep(6)
        tq = run("tick query")
        m = re.search(r"Average time per tick: ([\d.]+)\s*ms", tq)
        pct = re.search(r"P50: ([\d.]+)\s*ms.*?P95: ([\d.]+)\s*ms.*?P99: ([\d.]+)\s*ms", tq, re.S)
        mspt = float(m.group(1)) if m else None
        detail = f"avg {mspt} ms/tick" + (f", P50 {pct.group(1)} / P95 {pct.group(2)} / P99 {pct.group(3)} ms" if pct else "") + f", {bodies} bodies"
        own = re.search(r"longnight per tick.*", run("longnight admin perf"))
        if own:
            detail += "; " + own.group(0).strip()
        check("server holds 20 TPS with a town embodied (avg tick < 50 ms)", mspt is not None and mspt < 50, detail)
    day_costs = []
    for d in range(a.days):
        t0 = time.time()
        run("longnight admin skip 1")
        day_costs.append(time.time() - t0)
        time.sleep(2)
    if day_costs:
        check("a simulated day of the whole world costs under 5 s", max(day_costs) < 5,
              "wall time per skipped day incl. RCON: " + ", ".join(f"{c:.2f} s" for c in day_costs))
    st1 = run("longnight admin status")
    v = run("longnight admin validate")
    check(f"invariants hold after {a.days} simulated days", "VALIDATE OK" in v)
    if target:
        run(f"longnight admin raid {target}")
        run("time set 12500")  # dusk: raid strikes
        time.sleep(25)
        run(f"longnight admin inspect {target}")
        run("time set 23500")  # dawn: raid ends
        time.sleep(8)
        v = run("longnight admin validate")
        check("invariants hold after a raid", "VALIDATE OK" in v)
    run("longnight admin history")
    before = run("longnight admin status")
    run("save-all flush")
    time.sleep(3)
    stop(proc, r)
    r.close()
    log.close()
    # ---- restart: save data must reload consistently
    log2 = os.path.join(RESULTS, a.name + "-server2.log")
    proc, log = start(log2)
    ok, why = wait_done(log2, proc)
    check("server restarts on the saved world", ok, why)
    if ok:
        r = rcon.connect()
        after = run("longnight admin status")
        def counts(s):
            m = re.search(r"people alive=(\d+) dead=(\d+) settlements=(\d+)", s)
            return m.groups() if m else None
        check("people/deaths/settlements identical after reload", counts(before) == counts(after) and counts(after) is not None,
              f"{counts(before)} vs {counts(after)}")
        v = run("longnight admin validate")
        check("invariants hold after reload", "VALIDATE OK" in v)
        stop(proc, r)
        r.close()
    log.close()
    for lp in (log1, log2):
        if os.path.exists(lp):
            text = open(lp, errors="replace").read()
            errs = [l for l in text.splitlines() if ("ERROR" in l or "Exception" in l) and "longnight" in l.lower()]
            check(f"no longnight errors in {os.path.basename(lp)}", not errs, "; ".join(errs[:5]))
    failed = [n for n, ok in checks if not ok]
    note(f"\nSUMMARY: {len(checks) - len(failed)}/{len(checks)} checks passed")
    for n in failed:
        note("  failed: " + n)
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
