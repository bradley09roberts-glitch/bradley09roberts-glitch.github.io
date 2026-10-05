#!/usr/bin/env bash
# Clean-install test of the bundle in dist/: a dedicated server directory that contains ONLY the three jars of dist/mods
# (plus the Fabric launcher), booted and checked over RCON.
#   SQUID_ACCEPT_EULA=1 tools/dist-test.sh
# The server cannot start without eula.txt; SQUID_ACCEPT_EULA=1 writes it, which means YOU accept the Minecraft EULA
# (https://aka.ms/MinecraftEULA). Env: SQUID_SERVER_DIR (default run-dist-test), SQUID_PORT / SQUID_RCON_PORT (25700 / 25701),
# SQUID_TEMPLATE_DIR (copy launcher and libraries from another server directory instead of downloading them).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
[ -d dist/mods ] || { echo "dist/mods is missing: run tools/package.sh first"; exit 1; }
export SQUID_SERVER_DIR="${SQUID_SERVER_DIR:-$ROOT/run-dist-test}"
export SQUID_PORT="${SQUID_PORT:-25700}" SQUID_RCON_PORT="${SQUID_RCON_PORT:-25701}"
DIR="$SQUID_SERVER_DIR"
LOG="$DIR/server.log"

tools/devserver.sh stop >/dev/null 2>&1 || true
tools/devserver.sh bootstrap
# only the bundle's jars may be in mods/ (devserver.sh bootstrap fetched its own copies of the dependencies)
find "$DIR/mods" -maxdepth 1 -name '*.jar' -delete
cp dist/mods/*.jar "$DIR/mods/"
echo "mods installed from dist/mods:"; ls -1 "$DIR/mods"
[ -f "$DIR/eula.txt" ] || { echo "eula.txt missing: re-run with SQUID_ACCEPT_EULA=1 (you accept https://aka.ms/MinecraftEULA)"; exit 3; }

# start the server the same way devserver.sh does, but without deploying a freshly built jar over the bundle
( cd "$DIR" && exec setsid nohup java -Xmx1500M -Dsquid.server.dir="$DIR" -jar fabric-server-launch.jar nogui < /dev/null > server.log 2>&1 ) > /dev/null 2>&1 < /dev/null &
for _ in $(seq 1 120); do
  grep -q "Done (" "$LOG" 2>/dev/null && break
  sleep 1
done
grep -q "Done (" "$LOG" || { echo "FAIL: the server did not finish starting"; tail -30 "$LOG"; exit 1; }
rcon() { python3 tools/rcon.py "$@" | tail -1; }
FAILED=0
grep -q "squidgame 1.0.0" "$LOG" || { echo "FAIL: the mod is not in the loaded mod list"; FAILED=1; }
grep -q "Squid Game Tournament initialised" "$LOG" || { echo "FAIL: the mod did not initialise"; FAILED=1; }
echo "builders: $(python3 tools/rcon.py "squid debug builders" | tr '\n' ' ')"
python3 tools/rcon.py "squid debug builders" | grep -q placeholder && { echo "FAIL: a placeholder arena builder is in use"; FAILED=1; }
BAD=$(grep -E "\[(Server thread|main)/(ERROR|FATAL)\]|Exception" "$LOG" | grep -v "No data fixer registered" || true)
[ -n "$BAD" ] && { echo "FAIL: errors in the log:"; echo "$BAD" | head; FAILED=1; }
rcon "stop" >/dev/null || true
[ "$FAILED" = 0 ] && echo "OK: the bundle in dist/ installs and boots on a clean server" || exit 1
