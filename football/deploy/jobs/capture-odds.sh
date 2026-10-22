#!/usr/bin/env bash
# Every 5 minutes: take any due pre-match odds snapshots (48h / 24h / 2h / 15 min before
# kick-off; DECISIONS.md D-009, D-010). Without a provider key it does nothing. A failure is
# notified at most every 6 hours; `footy doctor` reports a capture that stopped.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
JOB_NOTIFY_EVERY=21600
job_start capture-odds
step capture footy capture-odds || true
job_finish
