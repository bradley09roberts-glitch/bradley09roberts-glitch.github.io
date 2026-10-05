#!/usr/bin/env bash
# Full NPC-only tournament smoke test on the dev server: registration -> six games -> final winner, at an accelerated tick rate.
#   tools/smoke.sh [npcs=40] [difficulty=normal]
# Env: SMOKE_TPS (target tick rate, default 100), SMOKE_TIMEOUT (seconds to wait for the end, default 900),
#      SMOKE_NO_RESTART=1 (reuse the running dev server; otherwise it is restarted on the freshly built jar),
#      SQUID_RCON_PORT / SQUID_SERVER_DIR as for tools/devserver.sh.
# Prints the phase and result lines and fails (exit 1) on a server error, an exception or a missing tournament end.
set -euo pipefail
cd "$(dirname "$0")/.."
NPCS="${1:-40}"
DIFF="${2:-normal}"
TPS="${SMOKE_TPS:-100}"
TIMEOUT="${SMOKE_TIMEOUT:-900}"
DIR="${SQUID_SERVER_DIR:-$(pwd)/run-server}"
LOG="$DIR/server.log"
rcon() { python3 tools/rcon.py "$@" | tail -1; }

if [ "${SMOKE_NO_RESTART:-0}" != "1" ]; then
  tools/devserver.sh restart | grep -v "Picked up" | tail -1
fi
rcon "squid config debug false" >/dev/null
rcon "squid reset" >/dev/null
START_LINE=$(wc -l < "$LOG")
rcon "tick rate $TPS" >/dev/null
rcon "squid debug simulate $NPCS $DIFF" >/dev/null
echo "simulating a tournament with $NPCS NPCs ($DIFF) at $TPS ticks/s ..."
END=$((SECONDS + TIMEOUT))
until tail -n +"$((START_LINE + 1))" "$LOG" | grep -qE "Tournament phase -> (FINAL_WINNER|RESTART)"; do
  if [ "$SECONDS" -ge "$END" ]; then echo "FAIL: the tournament did not finish within ${TIMEOUT}s"; rcon "tick rate 20" >/dev/null; exit 1; fi
  sleep 3
done
rcon "tick rate 20" >/dev/null
NEW=$(tail -n +"$((START_LINE + 1))" "$LOG")
echo "$NEW" | grep -E "concluded|Tournament phase -> (REGISTRATION|FINAL_WINNER)" | sed -E 's/^\[[0-9:]+\] \[[^]]*\]: //'
BAD=$(echo "$NEW" | grep -E "\[(Server thread|main)/(ERROR|FATAL)\]|Exception|Tournament tick failed" | grep -v "No data fixer registered" || true)
if [ -n "$BAD" ]; then
  echo "FAIL: errors during the run:"; echo "$BAD" | head -20; exit 1
fi
WINNER=$(echo "$NEW" | grep -E "winner|Winner" | head -2 || true)
[ -n "$WINNER" ] && echo "$WINNER" | sed -E 's/^\[[0-9:]+\] \[[^]]*\]: //'
rcon "squid reset" >/dev/null
echo "OK: the tournament ran to the end without server errors"
