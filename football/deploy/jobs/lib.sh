# shellcheck shell=bash
# Shared by the scheduled jobs in deploy/jobs/ (docs/server.md, "Operator"). Each job:
#   source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"; job_start <name> [--all-locks]
#   step <name> <command...>        (runs it, logs to the journal, notes a failure, continues)
#   push_records                    (commits and pushes new records lines, if any)
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
JOB_FAILED=()
JOB_NOTES=()
JOB_TMP=""

log() { printf '[%s] %s\n' "${JOB:-footy}" "$*"; }
warn() { printf '[%s] WARNING: %s\n' "${JOB:-footy}" "$*" >&2; }

uk_date() { TZ=Europe/London date "+%F"; }
now_utc() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# --- notifications (ntfy.sh: free, no account; the topic name is the password) ------------

notify() { # notify <title> <message> [priority 1-5] [tags]
  local title=$1 message=$2 priority=${3:-3} tags=${4:-}
  log "notify: $title: ${message//$'\n'/ | }"
  if [[ -z $NTFY_TOPIC ]]; then
    warn "NTFY_TOPIC is not set: notification not sent"
    return 0
  fi
  local host
  host=$(hostname -s 2>/dev/null || echo server)
  curl -fsS -m 20 -o /dev/null \
    -H "Title: $title" -H "Priority: $priority" ${tags:+-H "Tags: $tags"} \
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
  [[ $JOB == capture-odds ]] && wait=60
  for name in "${locks[@]}"; do
    exec {lock_fd}>"$FOOTY_STATE_DIR/locks/$name.lock"
    if ! flock -w "$wait" "$lock_fd"; then
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
  if [[ $rc -eq 0 && ${#JOB_FAILED[@]} -eq 0 ]]; then
    now_utc >"$FOOTY_STATE_DIR/last-success-$JOB"
    log "done"
  else
    [[ $rc -eq 0 ]] && rc=1
    now_utc >"$FOOTY_STATE_DIR/last-failure-$JOB"
    local body="" f
    for f in "${JOB_FAILED[@]}"; do body+="$f"$'\n'; done
    [[ -n $body ]] || body="stopped with exit code $rc"$'\n'
    body+=$(_failure_tail)
    notify "Football server: $JOB failed" "$body" 4 warning
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
    return
  done
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
    cat "$out" "$err" >"$JOB_TMP/$(printf '%03d' ${#JOB_FAILED[@]})-$name.failed" 2>/dev/null || true
    warn "$name failed (exit $rc) after $((SECONDS - started))s"
  else
    log "== $name ok ($((SECONDS - started))s)"
  fi
  return "$rc"
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
    git -C "$d" pull --quiet --ff-only --depth 1 2>&1 || warn "openfootball pull failed in $d"
  done
}

# UEFA results from Wikipedia without the risk of a partial rebuild: fetch first, loading
# the stored CSV as it is; rebuild the CSV from the pages only when every page came back.
# (A rebuild after a refused fetch would use the same stored pages, so nothing is lost.)
euro_update() {
  if [[ $FOOTY_JOBS_OFFLINE == 1 ]]; then
    step euro-update footy euro-update --no-rebuild || true
    return
  fi
  local rc=0
  step euro-fetch footy euro-update --fetch-wikipedia --no-rebuild || rc=$?
  local outcomes
  outcomes=$(json_of euro-fetch | jq -r '[(.wikipedia.fetch_wikipedia // {}), (.wikipedia_gap.fetch // {})]
      | add | keys | join(",")' 2>/dev/null || echo "?")
  if [[ $rc -eq 0 && -n $outcomes && $outcomes != "?" ]] &&
    ! grep -qvE '^(new_revision|unchanged|not_modified|cached)$' <<<"${outcomes//,/$'\n'}"; then
    step euro-rebuild footy euro-update || true
  else
    note "Wikipedia fetch incomplete (${outcomes:-no answer}): UEFA CSV not rebuilt"
  fi
}

# --- records: commit and push without touching the working tree's commit -------------------

# The jobs never pull code: the weekly update does that (with its migration guard). So the
# records commit is built on top of the remote branch with git plumbing, from the records
# files as they are here, and pushed; HEAD and the rest of the working tree stay as they are.
# It refuses if the remote holds a records line this server lacks (records are append-only).
push_records() {
  [[ $FOOTY_PUSH_RECORDS == 1 ]] || { log "records push switched off"; return 0; }
  local rel
  rel=$(realpath --relative-to="$FOOTY_REPO" "$FOOTY_RECORDS_DIR")
  local attempt
  for attempt in 1 2 3; do
    if _push_records_once "$rel"; then return 0; fi
    local rc=$?
    [[ $rc -eq 2 ]] && break # refused: retrying won't help
    sleep $((attempt * 10))
  done
  JOB_FAILED+=("push records")
  return 1
}

_push_records_once() {
  local rel=$1 git=(git -C "$FOOTY_REPO")
  if ! "${git[@]}" fetch --quiet "$FOOTY_REMOTE" \
    "+refs/heads/$FOOTY_BRANCH:refs/remotes/$FOOTY_REMOTE/$FOOTY_BRANCH"; then
    warn "git fetch failed"
    return 1
  fi
  local base
  base=$("${git[@]}" rev-parse "$FOOTY_RECORDS_REF")
  local idx="$JOB_TMP/records.index" f name blob size added=0
  rm -f "$idx"
  GIT_INDEX_FILE=$idx "${git[@]}" read-tree "$base"
  shopt -s nullglob
  for f in "$FOOTY_RECORDS_DIR"/*.jsonl; do
    name=$(basename "$f")
    # append-only: the remote's file must be a prefix of ours
    if size=$("${git[@]}" cat-file -s "$base:$rel/$name" 2>/dev/null); then
      blob=$("${git[@]}" rev-parse "$base:$rel/$name")
      if [[ $(head -c "$size" "$f" | "${git[@]}" hash-object --stdin) != "$blob" ]]; then
        warn "refused: $rel/$name on $FOOTY_BRANCH isn't the start of this server's copy"
        shopt -u nullglob
        return 2
      fi
      added=$((added + $(tail -c +"$((size + 1))" "$f" | grep -c . || true)))
    else
      added=$((added + $(grep -c . "$f" || true)))
    fi
    blob=$("${git[@]}" hash-object -w -- "$f")
    GIT_INDEX_FILE=$idx "${git[@]}" update-index --add --cacheinfo "100644,$blob,$rel/$name"
  done
  shopt -u nullglob
  local missing
  missing=$("${git[@]}" ls-tree --name-only "$base" -- "$rel/" | while read -r p; do
    [[ $p == *.jsonl && ! -e $FOOTY_RECORDS_DIR/$(basename "$p") ]] && echo "$p"
  done || true)
  if [[ -n $missing ]]; then
    warn "refused: $FOOTY_BRANCH has records files this server lacks: $missing"
    return 2
  fi
  local tree
  tree=$(GIT_INDEX_FILE=$idx "${git[@]}" write-tree)
  if [[ $tree == $("${git[@]}" rev-parse "$base^{tree}") ]]; then
    log "records: nothing new to push"
    return 0
  fi
  local commit
  commit=$("${git[@]}" commit-tree "$tree" -p "$base" \
    -m "Records: $added new genuine pre-match line(s) from the server ($JOB, $(now_utc))")
  if "${git[@]}" push --quiet "$FOOTY_REMOTE" "$commit:refs/heads/$FOOTY_BRANCH"; then
    "${git[@]}" update-ref "refs/remotes/$FOOTY_REMOTE/$FOOTY_BRANCH" "$commit"
    printf '%s %s\n' "$(now_utc)" "$commit" >"$FOOTY_STATE_DIR/last-records-push"
    log "records: pushed $added line(s) as ${commit:0:12}"
    return 0
  fi
  warn "git push failed (attempt): will fetch and try again"
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
