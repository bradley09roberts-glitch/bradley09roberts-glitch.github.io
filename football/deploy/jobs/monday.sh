#!/usr/bin/env bash
# Monday 08:10 UK: the weekend's results (every division), the UEFA tables, settlement, and a
# weekly summary notification (the last 7 days, the headline record, the health check).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
job_start monday
fd_update fd-update || true
euro_update
step settle footy settle || true
push_records || true
if [[ -e $FOOTY_STATE_DIR/warn-media && $FOOTY_JOBS_OFFLINE != 1 ]]; then
  # pictures the bootstrap couldn't download (resumable; stops at the first refusal)
  if step team-media footy team-media --download; then
    rm -f "$FOOTY_STATE_DIR/warn-media"
  else
    forgive team-media
  fi
fi

week=$(footy doctor --summary --json 2>/dev/null || true)
lines=$(jq -r '.summary as $s | if $s == null then "no summary" else
  "Last 7 days: \($s.records_published) records published, \($s.settlements) settled; \($s.upcoming_fixtures_with_records) upcoming fixtures have records." end' \
  <<<"$week" 2>/dev/null || echo "summary unavailable")
health=$(jq -r '"Health: \(.problems) problems, \(.warnings) warnings" +
  ([.checks[] | select(.status == "problem") | " | \(.name): \(.summary)"] | join(""))' \
  <<<"$week" 2>/dev/null || echo "Health: doctor unavailable")
perf=$(curl -fsS -m 30 "${FOOTY_API_URL_BASE:-http://127.0.0.1:8000}/api/performance" 2>/dev/null |
  jq -r '"Record of record so far: \(.settled) settled of \(.published)" +
    (if .markets["1X2"].n then "; match result right \(.markets["1X2"].correct) of \(.markets["1X2"].n)" else "" end)' \
    2>/dev/null || echo "Record of record: the API did not answer")
notify "Football server: weekly summary" "$lines"$'\n'"$perf"$'\n'"$health" 2 calendar
job_finish
