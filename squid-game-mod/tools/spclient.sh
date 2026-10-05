#!/usr/bin/env bash
# Single-player verification client: opens the world run-spclient/saves/$SQUID_WORLD (default SquidSP) in the integrated server
# of a headless client (Xvfb + llvmpipe), exactly like a player would after "Singleplayer". The world is created on first use by a
# throwaway dedicated server (a plain, never-entered world; the mod then has to generate its complex on /squid enter).
# Usage: tools/spclient.sh [display-number]    Env: SQUID_WORLD, SQUID_USER, SQUID_FRESH=1 (recreate the world)
# Needs the same toolchain as tools/devserver.sh (run-server/ is used as a template for the launcher and libraries) and, for the
# throwaway server that creates the world, SQUID_ACCEPT_EULA=1 (that is YOU accepting https://aka.ms/MinecraftEULA).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
WORLD="${SQUID_WORLD:-SquidSP}"
RUNDIR="${SQUID_RUNDIR:-run-spclient}"
SAVE="$ROOT/$RUNDIR/saves/$WORLD"
if [ "${SQUID_FRESH:-0}" = "1" ]; then rm -rf "$SAVE"; fi
if [ ! -f "$SAVE/level.dat" ]; then
  SEED="$ROOT/run-sp-seed"
  echo "creating the single-player world $WORLD with a throwaway dedicated server ..."
  rm -rf "$SEED"
  export SQUID_SERVER_DIR="$SEED" SQUID_PORT=25690 SQUID_RCON_PORT=25691 SQUID_TEMPLATE_DIR="$ROOT/run-server"
  tools/devserver.sh start
  SQUID_RCON_PORT=25691 python3 tools/rcon.py "save-all flush" >/dev/null || true
  SQUID_RCON_PORT=25691 python3 tools/rcon.py "stop" >/dev/null || true
  for _ in $(seq 1 30); do pgrep -f "squid.server.dir=$SEED " >/dev/null || break; sleep 1; done
  mkdir -p "$ROOT/$RUNDIR/saves"
  rm -rf "$SAVE" && cp -r "$SEED/world" "$SAVE"
  rm -rf "$SEED"
  unset SQUID_SERVER_DIR SQUID_PORT SQUID_RCON_PORT SQUID_TEMPLATE_DIR
fi
export SQUID_TASK=runSpClient SQUID_RUNDIR="$RUNDIR" SQUID_WORLD="$WORLD"
exec tools/xvfb-client.sh "${1:-98}"
