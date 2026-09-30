#!/usr/bin/env bash
# Makes a timestamped backup of the world (and the settings that matter) into backups/.
# Stop the server first: a copy taken while it is writing can be inconsistent.
# Usage: ./backup-world.sh [--keep N]   (default keeps the newest 10 backups)
set -euo pipefail
cd "$(dirname "$0")"
KEEP=10
if [ "${1:-}" = "--keep" ]; then KEEP="${2:?number}"; fi
if [ -f .server-running ]; then
  echo "The server appears to be running (.server-running exists). Stop it first (type 'stop' in the console)."
  echo "If it crashed, delete .server-running and try again."
  exit 1
fi
LEVEL=$(grep -E '^level-name=' server.properties 2>/dev/null | cut -d= -f2- || true); LEVEL="${LEVEL:-world}"
[ -d "$LEVEL" ] || { echo "No world folder '$LEVEL' yet."; exit 1; }
mkdir -p backups
STAMP=$(date +%Y%m%d-%H%M%S)
OUT="backups/${LEVEL}-${STAMP}.tar.gz"
tar -czf "$OUT" "$LEVEL" server.properties $( [ -f whitelist.json ] && echo whitelist.json ) $( [ -f ops.json ] && echo ops.json )
echo "Backup written: $OUT"
ls -1t backups/"${LEVEL}"-*.tar.gz 2>/dev/null | tail -n +$((KEEP + 1)) | xargs -r rm -f
echo "Kept the newest $KEEP backups."
