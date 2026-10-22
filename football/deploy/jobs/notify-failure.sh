#!/usr/bin/env bash
# OnFailure handler (footy-failure@.service): a job unit failed without its own notification
# (killed at its time limit, crashed before job_start, out of memory). Sends one, with the
# unit's last journal lines.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
unit=${1:?unit name}
id=$(systemctl show -p InvocationID --value "$unit" 2>/dev/null || true)
if [[ -n $id && -e $FOOTY_STATE_DIR/notified-$id ]]; then
  echo "already notified by the job itself"
  exit 0
fi
result=$(systemctl show -p Result --value "$unit" 2>/dev/null || echo unknown)
tail=$(journalctl -u "$unit" -n 12 --no-pager -o cat 2>/dev/null | cut -c1-200 || true)
notify "Football server: $unit failed ($result)" "${tail:-no journal lines}" 4 warning
