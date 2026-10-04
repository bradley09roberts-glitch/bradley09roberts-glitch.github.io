#!/usr/bin/env bash
# Development dedicated server, bootstrapped from Maven so the setup is reproducible.
#   tools/devserver.sh start|stop|restart|status|log|bootstrap
# Env: SQUID_SERVER_DIR (default ./run-server), BUILD=1 to run `./gradlew build` first, GRADLE_ARGS (e.g. -PskipArenas),
#      SQUID_ACCEPT_EULA=1 writes eula.txt (YOU are accepting the Minecraft EULA https://aka.ms/MinecraftEULA),
#      SQUID_PORT (game, default 25599), SQUID_RCON_PORT (default 25598; tools/rcon.py reads the same variable),
#      SQUID_HEAP (default 3G), SQUID_TEMPLATE_DIR (copy launcher/libs/mods from another server dir instead of downloading).
# RCON password "squidtest" (use tools/rcon.py). Offline mode (for the headless test client). Several servers can run side by
# side as long as every one has its own SQUID_SERVER_DIR and ports.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
DIR="${SQUID_SERVER_DIR:-$ROOT/run-server}"
MC=1.21.1; LOADER=0.17.3; FAPI='0.116.17+1.21.1'; GECKO=4.9.3
mkdir -p "$DIR/mods"
PORT="${SQUID_PORT:-25599}"; RPORT="${SQUID_RCON_PORT:-25598}"
bootstrap() {
  if [ ! -f "$DIR/fabric-server-launch.jar" ] && [ -n "${SQUID_TEMPLATE_DIR:-}" ] && [ -f "$SQUID_TEMPLATE_DIR/fabric-server-launch.jar" ]; then
    ( cd "$SQUID_TEMPLATE_DIR" && tar cf - --exclude=./world --exclude=./logs --exclude=./crash-reports --exclude=./server.log \
        --exclude='./mods/squidgame-*' --exclude=./server.properties --exclude=./eula.txt . ) | ( cd "$DIR" && tar xf - )
  fi
  if [ ! -f "$DIR/fabric-server-launch.jar" ]; then
    INST=$(curl -fsS https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys;print(json.load(sys.stdin)[0]['version'])")
    curl -fsSL -o "$DIR/fabric-server-launch.jar" "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INST/server/jar"
  fi
  [ -f "$DIR/mods/fabric-api-$FAPI.jar" ] || curl -fsSL -o "$DIR/mods/fabric-api-$FAPI.jar" "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FAPI/fabric-api-$FAPI.jar"
  [ -f "$DIR/mods/geckolib-fabric-$MC-$GECKO.jar" ] || curl -fsSL -o "$DIR/mods/geckolib-fabric-$MC-$GECKO.jar" "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-fabric-$MC/$GECKO/geckolib-fabric-$MC-$GECKO.jar"
  if [ "${SQUID_ACCEPT_EULA:-0}" = "1" ]; then echo "eula=true" > "$DIR/eula.txt"; fi
  if [ ! -f "$DIR/server.properties" ]; then
    cat > "$DIR/server.properties" <<'PROPS'
online-mode=false
server-port=@PORT@
enable-rcon=true
rcon.port=@RPORT@
rcon.password=squidtest
view-distance=8
simulation-distance=8
spawn-protection=0
max-tick-time=-1
motd=Squid Game dev server
gamemode=creative
PROPS
  fi
  sed -i "s/@PORT@/$PORT/; s/@RPORT@/$RPORT/; s/^server-port=.*/server-port=$PORT/; s/^rcon.port=.*/rcon.port=$RPORT/" "$DIR/server.properties"
}
# every server is tagged with its directory so several can coexist
running() { pgrep -f "[s]quid.server.dir=$DIR " >/dev/null; }
stop_server() {
  if running; then SQUID_RCON_PORT="$RPORT" python3 "$ROOT/tools/rcon.py" stop >/dev/null 2>&1 || true; for i in $(seq 1 30); do running || break; sleep 1; done; fi
  if running; then pkill -f "[s]quid.server.dir=$DIR " || true; sleep 2; fi
}
deploy() {
  if [ "${BUILD:-0}" = "1" ]; then "$ROOT/tools/gradle.sh" build -x test ${GRADLE_ARGS:-} -q; fi
  JAR=$(ls -t build/libs/squidgame-*.jar | grep -v sources | head -1)
  find "$DIR/mods" -name 'squidgame-*.jar' -delete
  cp "$JAR" "$DIR/mods/"
  echo "deployed $JAR"
}
start_server() {
  bootstrap
  [ -f "$DIR/eula.txt" ] || { echo "eula.txt missing: read https://aka.ms/MinecraftEULA and re-run with SQUID_ACCEPT_EULA=1"; exit 3; }
  ( cd "$DIR" && setsid nohup java -Xmx"${SQUID_HEAP:-3G}" -Dsquid.server.dir="$DIR" -jar fabric-server-launch.jar nogui < /dev/null > server.log 2>&1 & )
  for i in $(seq 1 120); do
    if grep -q "Done (" "$DIR/server.log" 2>/dev/null; then echo "server up ($(grep -o 'Done ([0-9.]*s)' "$DIR/server.log" | tail -1))"; return 0; fi
    if ! running; then echo "server died:"; tail -30 "$DIR/server.log"; return 1; fi
    sleep 1
  done
  echo "timeout waiting for the server"; return 1
}
case "${1:-status}" in
  bootstrap) bootstrap ;;
  start) deploy; : > "$DIR/server.log"; start_server ;;
  stop) stop_server ;;
  restart) stop_server; deploy; : > "$DIR/server.log"; start_server ;;
  status) running && echo running || echo stopped ;;
  log) tail -n "${2:-60}" "$DIR/server.log" ;;
  *) echo "usage: $0 start|stop|restart|status|log|bootstrap"; exit 2 ;;
esac
