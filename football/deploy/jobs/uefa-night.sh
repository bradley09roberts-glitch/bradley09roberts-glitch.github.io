#!/usr/bin/env bash
# UEFA match nights (Tuesday to Thursday) 23:30 UK: the night's results from Wikipedia, then
# settlement. A refused fetch fails the job, so it is notified (audit M3); results from news
# reports can then be entered with `footy euro-import-results` (DECISIONS.md D-029).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
job_start uefa-night
euro_update
step settle footy settle || true
job_finish
