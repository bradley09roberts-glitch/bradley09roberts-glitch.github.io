# shellcheck shell=bash
# Shared by the scheduled jobs in deploy/jobs/ (docs/server.md, "Operator"). Each job:
#   source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"; job_start <name> [--all-locks]
#   step <name> <command...>        (runs it, logs to the journal, notes a failure, continues)
#   sync_records                    (before publishing: takes and imports records lines the
#                                    remote has and this server lacks, e.g. after a restore)
#   push_records                    (syncs, then commits and pushes new records lines, if any)
#   job_finish                      (exit 1, and a push notification, if a step failed)
# Everything below can be overridden from the environment (the systemd units read
# ~/.config/footy/jobs.env), which is how deploy/test-jobs.sh runs the jobs on a copy.
set -Eeuo pipefail

FOOTY_JOBS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
FOOTY_PROJECT=${FOOTY_PROJECT:-$(cd "$FOOTY_JOBS_DIR/../.." && pwd)}
FOOTY_ENV_FILE=${FOOTY_ENV_FILE:-$HOME/.config/footy/jobs.env}
if [[ -r $FOOTY_ENV_FILE ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$FOOTY_ENV_FILE"
  set +a
fi
FOOTY_REPO=${FOOTY_REPO:-$(git -C "$FOOTY_PROJECT" rev-parse --show-toplevel)}
FOOTY_STATE_DIR=${FOOTY_STATE_DIR:-$HOME/.local/state/footy}
FOOTY_BACKUP_DIR=${FOOTY_BACKUP_DIR:-$HOME/backups}
FOOTY_SITE_DIR=${FOOTY_SITE_DIR:-/srv/footy-site}
FOOTY_DB=${FOOTY_DB:-$FOOTY_PROJECT/data/footy.sqlite3}
FOOTY_RECORDS_DIR=${FOOTY_RECORDS_DIR:-$FOOTY_PROJECT/records/predictions}
FOOTY_REMOTE=${FOOTY_REMOTE:-origin}
FOOTY_BRANCH=${FOOTY_BRANCH:-main}
FOOTY_RECORDS_REF=${FOOTY_RECORDS_REF:-refs/remotes/$FOOTY_REMOTE/$FOOTY_BRANCH}
FOOTY_PUSH_RECORDS=${FOOTY_PUSH_RECORDS:-1}
FOOTY_DIVISIONS=${FOOTY_DIVISIONS:-E0,SP1,I1,D1,F1,E1}
FOOTY_LOCK_WAIT=${FOOTY_LOCK_WAIT:-5400}   # seconds a job waits for another to finish
FOOTY_JOBS_OFFLINE=${FOOTY_JOBS_OFFLINE:-0} # 1: no requests to outside sources (tests only)
FOOTY_SYSTEMCTL=${FOOTY_SYSTEMCTL:-sudo -n systemctl}
NTFY_URL=${NTFY_URL:-https://ntfy.sh}
NTFY_TOPIC=${NTFY_TOPIC:-}
export FOOTY_STATE_DIR FOOTY_RECORDS_REF
export PATH="$HOME/.local/bin:$PATH"
FOOTY_UV=${FOOTY_UV:-$(command -v uv || echo "$HOME/.local/bin/uv")}
# where fixtures.py looks for the openfootball schedules (/home/user/openfootball, linked to
# ~/openfootball by setup.sh) and where euro-update looks for champions-league
FOOTY_OPENFOOTBALL=${FOOTY_OPENFOOTBALL:-$(cd "$FOOTY_REPO/.." && pwd)/openfootball}

JOB=""
JOB_SKIPPED=0
JOB_FAILED=()
JOB_NOTES=()
JOB_TMP=""
LAST_FAILED_FILE=""
JOB_NOTIFY_EVERY=0 # seconds between failure notifications for this job (0: every failure)

log() { printf '[%s] %s\n' "${JOB:-footy}" "$*"; }
warn() { printf '[%s] WARNING: %s\n' "${JOB:-footy}" "$*" >&2; }

uk_date() { echo "${FOOTY_TODAY:-$(TZ=Europe/London date "+%F")}"; } # FOOTY_TODAY: tests
now_utc() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# --- notifications (ntfy.sh: free, no account; the topic name is the password) ------------

notify() { # notify <title> <message> [priority 1-5] [tags]
  local title=$1 message=$2 priority=${3:-3} tags=${4:-}
  log "notify: $title: ${message//$'\n'/ | }"
  if [[ -z $NTFY_TOPIC ]]; then
    warn "NTFY_TOPIC is not set: notification not sent"
    return 0
  fi
  local host headers=(-H "Title: $title" -H "Priority: $priority")
  [[ -n $tags ]] && headers+=(-H "Tags: $tags")
  host=$(hostname -s 2>/dev/null || echo server)
  curl -fsS -m 20 -o /dev/null "${headers[@]}" \
    --data-binary "$message"$'\n'"($host, $(TZ=Europe/London date '+%a %d %b %H:%M') UK)" \
    "$NTFY_URL/$NTFY_TOPIC" || warn "notification failed (ntfy unreachable)"
}

# --- locks, state and the end of a job -----------------------------------------------------

job_start() { # job_start <name> [--all-locks]
  JOB=$1
  mkdir -p "$FOOTY_STATE_DIR/locks"
  JOB_TMP=$(mktemp -d "${TMPDIR:-/tmp}/footy-$JOB.XXXXXX")
  trap '_job_exit $?' EXIT
  trap 'exit 143' TERM INT
  # Every job but the odds capture takes the jobs lock, so they run one at a time; the
  # capture has its own, so a 25-minute publishing run doesn't stop it; the update takes both.
  local locks=(jobs)
  [[ $JOB == capture-odds ]] && locks=(capture)
  [[ ${2:-} == --all-locks ]] && locks=(jobs capture)
  local name wait=$FOOTY_LOCK_WAIT
  [[ $JOB == capture-odds ]] && wait=${FOOTY_CAPTURE_LOCK_WAIT:-60}
  for name in "${locks[@]}"; do
    exec {lock_fd}>"$FOOTY_STATE_DIR/locks/$name.lock"
    if ! flock -w "$wait" "$lock_fd"; then
      if [[ $JOB == capture-odds ]]; then
        # only the weekly update holds this lock (minutes, at 04:00 on Wednesdays): this run
        # is skipped, not failed; `footy doctor` reports a capture that stays away
        log "skipped: the weekly update holds the $name lock"
        JOB_SKIPPED=1
        exit 0
      fi
      JOB_FAILED+=("lock: another job held the $name lock for more than ${wait}s")
      exit 1
    fi
  done
  date -u +%Y-%m-%dT%H:%M:%SZ >"$FOOTY_STATE_DIR/last-start-$JOB"
  log "start ($(TZ=Europe/London date '+%a %d %b %H:%M') UK) in $FOOTY_PROJECT"
}

_job_exit() {
  local rc=$1
  trap - EXIT
  if [[ $JOB_SKIPPED == 1 && $rc -eq 0 ]]; then
    : # neither a success nor a failure
  elif [[ $rc -eq 0 && ${#JOB_FAILED[@]} -eq 0 ]]; then
    now_utc >"$FOOTY_STATE_DIR/last-success-$JOB"
    log "done"
  else
    [[ $rc -eq 0 ]] && rc=1
    now_utc >"$FOOTY_STATE_DIR/last-failure-$JOB"
    local body="" f
    for f in "${JOB_FAILED[@]}"; do body+="$f"$'\n'; done
    [[ -n $body ]] || body="stopped with exit code $rc"$'\n'
    body+=$(_failure_tail || true)
    local stamp="$FOOTY_STATE_DIR/failure-notified-$JOB"
    if [[ $JOB_NOTIFY_EVERY -gt 0 && -e $stamp &&
      $(($(date +%s) - $(stat -c %Y "$stamp"))) -lt $JOB_NOTIFY_EVERY ]]; then
      log "failure already notified in the last ${JOB_NOTIFY_EVERY}s"
    else
      notify "Football server: $JOB failed" "$body" 4 warning
      : >"$stamp"
    fi
    # the systemd OnFailure handler (notify-failure.sh) sees this and doesn't repeat it
    [[ -n ${INVOCATION_ID:-} ]] && : >"$FOOTY_STATE_DIR/notified-$INVOCATION_ID"
    log "failed (exit $rc): ${JOB_FAILED[*]:-}"
  fi
  [[ -n $JOB_TMP ]] && rm -rf "$JOB_TMP"
  exit "$rc"
}

_failure_tail() { # the last lines the first failed step wrote, for the notification
  local f
  for f in "$JOB_TMP"/*.failed; do
    [[ -e $f ]] || continue
    printf '\n%s\n' "$(tail -n 8 "$f" | cut -c1-200)"
    return 0 # explicit: a bare return in an EXIT trap returns the status the job exited with
  done
  return 0
}

job_finish() { # exit 1 (and notify) if a step failed
  if [[ ${#JOB_FAILED[@]} -gt 0 ]]; then exit 1; fi
  exit 0
}

note() { JOB_NOTES+=("$*"); log "$*"; }

# --- running commands ------------------------------------------------------------------------

footy() { # the project's CLI, through uv, from the project folder
  (cd "$FOOTY_PROJECT" && "$FOOTY_UV" run --frozen --quiet footy "$@")
}

# step <name> <command...>: run it; stdout is kept in $JOB_TMP/<name>.out (for jq), stdout and
# stderr both go to the journal. A failure is noted and the job carries on (the job decides
# what depends on what); returns the command's exit code.
step() {
  local name=$1
  shift
  local out="$JOB_TMP/$name.out" err="$JOB_TMP/$name.err" rc=0
  log "== $name: $*"
  local started=$SECONDS
  {
    "$@" 2> >(tee "$err" >&2) | tee "$out"
    rc=${PIPESTATUS[0]}
  } || true
  if [[ $rc -ne 0 ]]; then
    JOB_FAILED+=("$name (exit $rc)")
    LAST_FAILED_FILE="$JOB_TMP/$(printf '%03d' ${#JOB_FAILED[@]})-$name.failed"
    cat "$out" "$err" >"$LAST_FAILED_FILE" 2>/dev/null || true
    warn "$name failed (exit $rc) after $((SECONDS - started))s"
  else
    log "== $name ok ($((SECONDS - started))s)"
  fi
  return "$rc"
}

# retry_step <name> <command...>: step, and once more if it fails; a retry that succeeds
# clears the first failure (publish-shadows: "if the run exits 1 or is cut short, run it again")
retry_step() {
  local name=$1
  shift
  step "$name" "$@" && return 0
  log "trying $name once more"
  unset 'JOB_FAILED[-1]'
  rm -f "$LAST_FAILED_FILE"
  step "$name-again" "$@"
}

# forgive <prefix>: drop the failures whose step name starts with it (an expected failure)
forgive() {
  local kept=() f
  for f in "${JOB_FAILED[@]}"; do [[ $f == "$1"* ]] || kept+=("$f"); done
  JOB_FAILED=("${kept[@]}")
}

# the JSON a footy command printed (its last JSON value; lines before it are progress)
json_of() { sed -n '/^[[{]/,$p' "$JOB_TMP/$1.out" | jq -s 'last' 2>/dev/null || echo null; }

# --- models from the reference files (primary_models.json, shadow_models.json) -------------

load_plan() { # sets DOMESTIC_MARKET, DOMESTIC_FALLBACK, UEFA_MODELS, SHADOWS_PURE, ...
  local plan="$JOB_TMP/plan.env"
  (cd "$FOOTY_PROJECT" && "$FOOTY_UV" run --frozen --quiet python "$FOOTY_JOBS_DIR/plan.py") \
    >"$plan" || {
    JOB_FAILED+=("plan: can't read the models of record")
    return 1
  }
  if grep -qvE '^[A-Z_]+=[A-Za-z0-9_.,-]*$' "$plan"; then
    JOB_FAILED+=("plan: unexpected output from plan.py")
    return 1
  fi
  # shellcheck source=/dev/null
  source "$plan"
  log "plan: domestic market ${DOMESTIC_MARKET:-none}, fallback ${DOMESTIC_FALLBACK:-none}," \
    "UEFA ${UEFA_MODELS:-none}; shadows: ${SHADOWS_PURE//,/ } | priced: ${SHADOWS_PRICED//,/ }"
}

# --- sources ---------------------------------------------------------------------------------

footy_python() { # footy_python <code>: Python in the project's environment
  (cd "$FOOTY_PROJECT" && "$FOOTY_UV" run --frozen --quiet python -c "$1")
}

fd_fixtures() { # fd_fixtures [--force]: this week's prices file; offline, the stored one
  if [[ $FOOTY_JOBS_OFFLINE == 1 ]]; then
    step fd-fixtures footy_python 'import json
from footy.db import get_engine
from footy.ingest.fd_fixtures import latest_snapshot
s = latest_snapshot(get_engine())
print(json.dumps({"offline": True, "published_at": None if s is None else s.attrs["published_at"]}))'
    return
  fi
  step fd-fixtures footy fd-fixtures "$@"
}

fd_update() { # fd_update <step name> [args...]
  local name=$1
  shift
  if [[ $FOOTY_JOBS_OFFLINE == 1 ]]; then set -- "$@" --no-download; fi
  step "$name" footy fd-update "$@"
}

pull_openfootball() { # refresh the league schedules (CC0); a failed pull keeps the old ones
  [[ $FOOTY_JOBS_OFFLINE == 1 ]] && { log "offline: openfootball not pulled"; return 0; }
  local d
  for d in "$FOOTY_OPENFOOTBALL"/*/; do
    [[ -d $d/.git ]] || continue
    if git -C "$d" fetch --quiet --depth 1 origin HEAD; then
      git -C "$d" reset --quiet --hard FETCH_HEAD
    else
      warn "openfootball fetch failed in $d: the stored schedule stays"
    fi
  done
}

# UEFA results from Wikipedia without the risk of a partial rebuild: fetch first, loading
# the stored CSV as it is; rebuild the CSV from the pages only when no page was refused or
# failed (a page Wikipedia doesn't have yet, "not_found", is fine). A rebuild after a refused
# fetch would use the same stored pages, so nothing is lost by skipping it.
euro_update() {
  if [[ $FOOTY_JOBS_OFFLINE == 1 ]]; then
    step euro-update footy euro-update --no-rebuild || true
    return
  fi
  step euro-fetch footy euro-update --fetch-wikipedia --no-rebuild || true
  local outcomes
  outcomes=$(json_of euro-fetch | jq -r '[(.wikipedia.fetch_wikipedia // {}),
      (.wikipedia_gap.fetch // {})] | add | keys | join(",")' 2>/dev/null || true)
  if [[ -n $outcomes ]] && ! grep -qE '(^|,)(error|skipped)(,|$)' <<<"$outcomes"; then
    if step euro-rebuild footy euro-update; then
      date -u +%Y-%m-%dT%H:%M:%SZ >"$FOOTY_STATE_DIR/wikipedia-ok"
      rm -f "$FOOTY_STATE_DIR/warn-wikipedia"
    fi
  else
    note "Wikipedia fetch incomplete (${outcomes:-no answer}): UEFA CSV not rebuilt"
  fi
}

# --- records: commit and push without touching the working tree's commit -------------------

# The jobs never pull code: the weekly update does that (with its migration guard). So the
# records commit is built on top of the remote branch with git plumbing, from the records
# files as they are here, and pushed; HEAD and the rest of the working tree stay as they are.
# Records are append-only: before publishing or pushing, sync_records takes the lines the
# remote has beyond this server's (a disk restored from an older backup, a clone made before
# the last push) and imports them into the database, so no record is published twice or
# under an id already used; a file that differs both ways stops publishing and pushing.

# sync_records: returns 0 when this server holds every pushed line (taking and importing any
# it lacked) or the remote can't be read (the push retries later), 1 when publishing must
# not go ahead (a records file differs from the remote's both ways, or the import failed).
sync_records() {
  [[ $FOOTY_PUSH_RECORDS == 1 ]] || return 0
  local git=(git -C "$FOOTY_REPO") remote_ref="refs/remotes/$FOOTY_REMOTE/$FOOTY_BRANCH"
  local rel base p name f theirs ours rsize lsize taken=() added=0
  rel=$(realpath --relative-to="$FOOTY_REPO" "$FOOTY_RECORDS_DIR")
  if ! "${git[@]}" fetch --quiet "$FOOTY_REMOTE" "+refs/heads/$FOOTY_BRANCH:$remote_ref" ||
    ! base=$("${git[@]}" rev-parse --verify --quiet "$remote_ref^{commit}"); then
    warn "records sync: can't read $FOOTY_BRANCH; going on with this server's records"
    return 0
  fi
  mkdir -p "$JOB_TMP/sync"
  while IFS= read -r p; do
    [[ $p == *.jsonl ]] || continue
    name=$(basename "$p")
    f=$FOOTY_RECORDS_DIR/$name
    theirs=$JOB_TMP/sync/$name
    "${git[@]}" cat-file blob "$base:$p" >"$theirs" || return 1
    rsize=$(stat -c %s "$theirs")
    if [[ -e $f ]]; then
      lsize=$(stat -c %s "$f")
      if ((lsize >= rsize)) && cmp -s -n "$rsize" "$f" "$theirs"; then
        continue # this server's copy is the remote's, or extends it (lines not pushed yet)
      fi
      if ! ((lsize < rsize)) || ! cmp -s -n "$lsize" "$f" "$theirs"; then
        JOB_FAILED+=("records: $rel/$name differs from $FOOTY_BRANCH's both ways (records published on two machines?); nothing published or pushed until it is resolved")
        return 1
      fi
      ours=$(grep -c . "$f" || true)
    else
      ours=0
    fi
    added=$((added + $(grep -c . "$theirs" || true) - ours))
    # replaced in one step; the temporary name doesn't end in .jsonl (Python's glob would
    # read a leftover dot file as a records file)
    cp "$theirs" "$FOOTY_RECORDS_DIR/.sync-$name.part" &&
      mv -f "$FOOTY_RECORDS_DIR/.sync-$name.part" "$f" || return 1
    taken+=("$name")
  done < <("${git[@]}" ls-tree --name-only "$base" -- "$rel/")
  local pending=$FOOTY_STATE_DIR/records-import-pending
  if [[ ${#taken[@]} -gt 0 ]]; then
    note "records sync: took $added line(s) already on $FOOTY_BRANCH that this server lacked (${taken[*]})"
    : >"$pending" # until the import succeeds (a later run tries again)
  fi
  [[ -e $pending ]] || return 0
  step import-records footy import-records || return 1
  rm -f "$pending"
}

push_records() {
  [[ $FOOTY_PUSH_RECORDS == 1 ]] || { log "records push switched off"; return 0; }
  local rel attempt rc
  rel=$(realpath --relative-to="$FOOTY_REPO" "$FOOTY_RECORDS_DIR")
  if ! sync_records; then
    JOB_FAILED+=("push records")
    return 1
  fi
  for attempt in 1 2 3; do
    rc=0
    _push_records_once "$rel" || rc=$?
    [[ $rc -eq 0 ]] && return 0
    [[ $rc -eq 2 ]] && break # refused: trying again won't help
    sleep $((attempt * 10))
  done
  JOB_FAILED+=("push records")
  return 1
}

# Returns 0 when pushed or nothing to push, 1 to try again, 2 when refused. Every git
# command is checked: a commit id that isn't one is never pushed.
_push_records_once() {
  local rel=$1 git=(git -C "$FOOTY_REPO") remote_ref="refs/remotes/$FOOTY_REMOTE/$FOOTY_BRANCH"
  "${git[@]}" fetch --quiet "$FOOTY_REMOTE" "+refs/heads/$FOOTY_BRANCH:$remote_ref" ||
    { warn "git fetch failed"; return 1; }
  local base base_tree
  if ! base=$("${git[@]}" rev-parse --verify --quiet "$remote_ref^{commit}") ||
    ! base_tree=$("${git[@]}" rev-parse --verify --quiet "$base^{tree}"); then
    warn "no $remote_ref to build on"
    return 2
  fi
  local idx="$JOB_TMP/records.index" f name blob size added=0
  rm -f "$idx"
  GIT_INDEX_FILE=$idx "${git[@]}" read-tree "$base" || return 1
  local files=()
  shopt -s nullglob
  files=("$FOOTY_RECORDS_DIR"/*.jsonl)
  shopt -u nullglob
  for f in "${files[@]}"; do
    name=$(basename "$f")
    # append-only: the remote's copy must be the start of ours
    if size=$("${git[@]}" cat-file -s "$base:$rel/$name" 2>/dev/null); then
      blob=$("${git[@]}" rev-parse --verify --quiet "$base:$rel/$name") || return 1
      if [[ $(head -c "$size" "$f" | "${git[@]}" hash-object --stdin) != "$blob" ]]; then
        warn "refused: $rel/$name on $FOOTY_BRANCH isn't the start of this server's copy"
        return 2
      fi
      added=$((added + $(tail -c +"$((size + 1))" "$f" | grep -c . || true)))
    else
      added=$((added + $(grep -c . "$f" || true)))
    fi
    blob=$("${git[@]}" hash-object -w -- "$f") || return 1
    GIT_INDEX_FILE=$idx "${git[@]}" update-index --add --cacheinfo "100644,$blob,$rel/$name" ||
      return 1
  done
  local p missing=()
  while IFS= read -r p; do
    [[ $p == *.jsonl && ! -e $FOOTY_RECORDS_DIR/$(basename "$p") ]] && missing+=("$p")
  done < <("${git[@]}" ls-tree --name-only "$base" -- "$rel/")
  if [[ ${#missing[@]} -gt 0 ]]; then
    warn "refused: $FOOTY_BRANCH has records files this server lacks: ${missing[*]}"
    return 2
  fi
  local tree commit
  tree=$(GIT_INDEX_FILE=$idx "${git[@]}" write-tree) || return 1
  if [[ $tree == "$base_tree" ]]; then
    log "records: nothing new to push"
    return 0
  fi
  commit=$("${git[@]}" commit-tree "$tree" -p "$base" \
    -m "Records: $added new genuine pre-match line(s) from the server ($JOB, $(now_utc))") ||
    return 1
  [[ $commit =~ ^[0-9a-f]{40}$ ]] || { warn "commit-tree gave '$commit'"; return 1; }
  if "${git[@]}" push --quiet "$FOOTY_REMOTE" "$commit:refs/heads/$FOOTY_BRANCH"; then
    "${git[@]}" update-ref "$remote_ref" "$commit"
    printf '%s %s\n' "$(now_utc)" "$commit" >"$FOOTY_STATE_DIR/last-records-push"
    log "records: pushed $added line(s) as ${commit:0:12}"
    return 0
  fi
  warn "git push failed: will fetch and try again"
  return 1
}

# --- health ----------------------------------------------------------------------------------

run_doctor() { # footy doctor; a push notification when it finds a problem (once a day each)
  local out="$JOB_TMP/doctor.txt" rc=0
  footy doctor --quiet "$@" >"$out" 2>&1 || rc=$?
  cat "$out"
  if [[ $rc -ne 0 ]]; then
    local key
    key=$(grep -E '^PROBLEM' "$out" | sha256sum | cut -c1-16)
    local seen="$FOOTY_STATE_DIR/doctor-notified-$key"
    if [[ ! -e $seen || -n $(find "$seen" -mmin +1200 2>/dev/null) ]]; then
      notify "Football server: health check" "$(grep -E '^(PROBLEM|WARN)' "$out" | cut -c1-200)" 4 stethoscope
      : >"$seen"
    else
      log "doctor: same problems as already notified today"
    fi
  fi
  return 0
}

# --- database backup -------------------------------------------------------------------------

db_backup() { # db_backup <destination .sqlite3.gz>: an online, consistent copy, checked
  local dest=$1 tmp
  tmp="$(dirname "$dest")/.$(basename "$dest" .gz).tmp"
  mkdir -p "$(dirname "$dest")"
  rm -f "$tmp"
  if command -v sqlite3 >/dev/null; then
    sqlite3 "$FOOTY_DB" ".backup '$tmp'"
    [[ $(sqlite3 "$tmp" 'PRAGMA quick_check;') == ok ]] || { warn "backup failed its check"; return 1; }
  else
    python3 -I - "$FOOTY_DB" "$tmp" <<'PY'
import sqlite3, sys
src, dst = sqlite3.connect(sys.argv[1]), sqlite3.connect(sys.argv[2])
src.backup(dst)
assert dst.execute("PRAGMA quick_check").fetchone()[0] == "ok", "backup failed its check"
PY
  fi
  gzip -6 -c "$tmp" >"$dest.part" && mv "$dest.part" "$dest" && rm -f "$tmp"
  log "backup: $dest ($(du -h "$dest" | cut -f1))"
}
