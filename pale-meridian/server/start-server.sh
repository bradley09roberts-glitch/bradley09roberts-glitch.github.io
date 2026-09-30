#!/usr/bin/env bash
# Starts the Pale Meridian server. Refuses to start until YOU have accepted the Minecraft EULA in eula.txt.
set -euo pipefail
cd "$(dirname "$0")"
LAUNCHER=$(ls fabric-server-mc.*-launcher.*.jar 2>/dev/null | head -n1 || true)
[ -n "$LAUNCHER" ] || { echo "The Fabric server launcher is missing. Run ./install-server.sh first."; exit 1; }
if ! grep -qi '^eula=true' eula.txt 2>/dev/null; then
  echo "The Minecraft EULA has not been accepted in eula.txt."
  echo "Read https://aka.ms/MinecraftEULA and, if you agree, set eula=true in eula.txt yourself."
  exit 1
fi
MIN_RAM="${PM_MIN_RAM:-2G}"
MAX_RAM="${PM_MAX_RAM:-4G}"
touch .server-running
trap 'rm -f .server-running' EXIT
java -Xms"$MIN_RAM" -Xmx"$MAX_RAM" -jar "$LAUNCHER" nogui
