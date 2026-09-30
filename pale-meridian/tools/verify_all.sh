#!/usr/bin/env bash
# Runs every automated check that does NOT start Minecraft, and writes evidence logs to docs/test-evidence/.
#
#   tools/verify_all.sh              offline checks only
#   tools/verify_all.sh --network    also re-verify the lock online, download every client file, and
#                                    exercise the server installers (downloads only; never starts a server)
#
# Needs: Python 3.11+ with Pillow, JDK 25 (JAVA_HOME or java on PATH), curl, unzip. Optional: pwsh.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EV="$ROOT/docs/test-evidence"
mkdir -p "$EV"
NET=0; [ "${1:-}" = "--network" ] && NET=1
WORK="$(mktemp -d "${TMPDIR:-/tmp}/pm-verify.XXXXXX")"
PASS=0; FAIL=0
result() { if [ "$1" = 0 ]; then PASS=$((PASS+1)); echo "PASS  $2"; else FAIL=$((FAIL+1)); echo "FAIL  $2"; fi; }
clean() { grep -v "JAVA_TOOL_OPTIONS\|^WARNING: \|^$" ; }

cd "$ROOT"
treehash() { (cd "$ROOT" && find mod/src/main/resources tools/generated -type f -print0 | sort -z | xargs -0 sha256sum | sha256sum | cut -d' ' -f1); }
python3 tools/gen_all.py > "$EV/01-generate.log" 2>&1; result $? "generate data pack, sites, art (01-generate.log)"
G1=$(treehash); python3 tools/gen_all.py > /dev/null 2>&1; G2=$(treehash)
echo "first run $G1 / second run $G2" >> "$EV/01-generate.log"
[ "$G1" = "$G2" ]; result $? "two generator runs produce byte-identical output (01-generate.log)"
python3 tools/simulate.py > "$EV/02-simulate.log" 2>&1; result $? "logic simulation of the campaign (02-simulate.log)"

cd "$ROOT/mod"
./gradlew --no-daemon -q offlineCheck -PcheckMode=validate 2>&1 | clean | grep "\[check\]" > "$EV/03-offline-validate.log"
grep -q "RESULT: PASS" "$EV/03-offline-validate.log"; result $? "game loaders accept every file, offline (03-offline-validate.log)"
./gradlew --no-daemon -q offlineCheck -PcheckMode=sites 2>&1 | clean | grep "\[check\]" > "$EV/04-worldgen-probe.log"
grep -q "RESULT: PASS" "$EV/04-worldgen-probe.log"; result $? "terrain probe on 3 seeds (04-worldgen-probe.log)"
./gradlew --no-daemon build --console=plain 2>&1 | clean > "$EV/05-build.log"
grep -q "BUILD SUCCESSFUL" "$EV/05-build.log" && grep -q "runGameTest SKIPPED" "$EV/05-build.log"; result $? "mod builds; build does not launch the game (05-build.log)"
H1=$(sha256sum build/libs/palemeridian-1.0.0.jar | cut -d' ' -f1)
./gradlew --no-daemon -q clean build >/dev/null 2>&1
H2=$(sha256sum build/libs/palemeridian-1.0.0.jar | cut -d' ' -f1)
echo "first $H1 / clean rebuild $H2" > "$EV/06-reproducible-jar.log"
[ "$H1" = "$H2" ]; result $? "clean rebuild produces a byte-identical jar (06-reproducible-jar.log)"

cd "$ROOT"
python3 tools/build_dist.py > "$EV/07-dist.log" 2>&1; result $? "release files built (07-dist.log)"

if [ "$NET" = 1 ]; then
  python3 tools/lock_pack.py --check > "$EV/08-lock-check.log" 2>&1; result $? "lock matches live Modrinth/Fabric metadata (08-lock-check.log)"
  python3 - > "$EV/09-mrpack-downloads.log" 2>&1 <<'EOF'
import hashlib, json, urllib.request, zipfile, sys
z = zipfile.ZipFile('dist/PaleMeridian-1.0.0.mrpack'); idx = json.loads(z.read('modrinth.index.json')); ok = 0
for f in idx['files']:
    d = urllib.request.urlopen(urllib.request.Request(f['downloads'][0], headers={"User-Agent": "pale-meridian-verify/1.0"}), timeout=120).read()
    g = len(d) == f['fileSize'] and hashlib.sha512(d).hexdigest() == f['hashes']['sha512'] and hashlib.sha1(d).hexdigest() == f['hashes']['sha1']
    ok += g; print(('PASS' if g else 'FAIL'), f['path'])
print(f"{ok}/{len(idx['files'])} verified"); sys.exit(0 if ok == len(idx['files']) else 1)
EOF
  result $? "every client pack download matches size, SHA-1 and SHA-512 (09-mrpack-downloads.log)"
  S="$WORK/linux"; mkdir -p "$S" && unzip -q dist/PaleMeridian-Server-1.0.0.zip -d "$S"; D="$S/PaleMeridian-Server-1.0.0"
  { (cd "$D" && ./install-server.sh); echo "install exit=$?"; (cd "$D" && ./start-server.sh); echo "start exit=$?"; cat "$D/eula.txt"; } > "$EV/10-server-install-linux.log" 2>&1
  grep -q "install exit=0" "$EV/10-server-install-linux.log" && grep -q "start exit=1" "$EV/10-server-install-linux.log" && grep -q "^eula=false" "$EV/10-server-install-linux.log"
  result $? "Linux installer verifies every file; start refuses while eula=false (10-server-install-linux.log)"
  { cp "$D/server-files.tsv" "$WORK/tsv"; sed -i 's/\(ferritecore[^\t]*\t[^\t]*\tsha512\t\)[0-9a-f]\{8\}/\1deadbeef/' "$D/server-files.tsv"; rm -f "$D/mods/ferritecore-9.0.0-fabric.jar";
    (cd "$D" && ./install-server.sh); echo "tampered-pin exit=$?"; ls "$D/mods"; cp "$WORK/tsv" "$D/server-files.tsv"; } > "$EV/11-server-tamper.log" 2>&1
  grep -q "tampered-pin exit=1" "$EV/11-server-tamper.log" && ! grep -q "^ferritecore" "$EV/11-server-tamper.log"
  result $? "a wrong hash stops the installer and installs nothing for that file (11-server-tamper.log)"
  if command -v pwsh >/dev/null 2>&1; then
    W="$WORK/win"; mkdir -p "$W" && unzip -q dist/PaleMeridian-Server-1.0.0.zip -d "$W"; mkdir -p "$WORK/shim"
    printf '#!/usr/bin/env bash\n[ "$1" = "/c" ] && shift\nexec bash -c "$*"\n' > "$WORK/shim/cmd"; chmod +x "$WORK/shim/cmd"
    (cd "$W/PaleMeridian-Server-1.0.0" && PATH="$WORK/shim:$PATH" pwsh -NoProfile -File install-server.ps1) > "$EV/12-server-install-pwsh.log" 2>&1
    result $? "Windows PowerShell installer logic under pwsh (12-server-install-pwsh.log)"
  else
    echo "NOT RUN  Windows PowerShell installer (pwsh not installed)"
  fi
fi
echo
echo "passed: $PASS  failed: $FAIL  (evidence in docs/test-evidence/)"
[ "$FAIL" = 0 ]
