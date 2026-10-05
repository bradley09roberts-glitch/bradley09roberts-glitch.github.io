#!/usr/bin/env bash
# Blocks until a regex appears in the dev server log (lines written after this call started), or the timeout (seconds) hits.
#   tools/wait-log.sh '<regex>' [timeout-seconds=300] [server-dir=run-server]
# Prints the matching line. Exit code 1 on timeout.
PATTERN="$1"; TIMEOUT="${2:-300}"; DIR="${3:-run-server}"
cd "$(dirname "$0")/.."
LOG="$DIR/server.log"
START=$(wc -l < "$LOG")
END=$((SECONDS + TIMEOUT))
while [ $SECONDS -lt $END ]; do
  LINE=$(tail -n +"$((START + 1))" "$LOG" | grep -E -m1 -- "$PATTERN")
  if [ -n "$LINE" ]; then echo "$LINE"; exit 0; fi
  sleep 1
done
echo "timeout waiting for: $PATTERN" >&2
exit 1
