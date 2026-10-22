#!/usr/bin/env bash
# Weekly, Wednesday 04:00 UK (or `sudo systemctl start footy-update`): bring the code up to
# date with the repository's main branch, safely.
#  1. Push any records not pushed yet, so the remote holds every line this server has.
#  2. Refuse if the working tree has other local changes (they would be lost).
#  3. Fast-forward to the remote branch (git pull), `uv sync`.
#  4. Migration guard (audit M5): if the code expects a newer database schema, back up the
#     database first, then `footy migrate`.
#  5. Rebuild the website if frontend/ changed; restart the API.
# Holds both locks, so no other job (not even the odds capture) runs meanwhile. If unit files
# or the Caddy template changed, it says so: those need `sudo deploy/setup.sh` (root).
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
job_start update --all-locks
git=(git -C "$FOOTY_REPO")
remote_ref="refs/remotes/$FOOTY_REMOTE/$FOOTY_BRANCH"

push_records || job_finish
step fetch "${git[@]}" fetch --quiet "$FOOTY_REMOTE" "+refs/heads/$FOOTY_BRANCH:$remote_ref" ||
  job_finish
old=$("${git[@]}" rev-parse HEAD)
new=$("${git[@]}" rev-parse "$remote_ref")
if [[ $old == "$new" ]]; then
  log "already at ${new:0:12}"
  run_doctor
  job_finish
fi
if ! "${git[@]}" merge-base --is-ancestor "$old" "$new"; then
  JOB_FAILED+=("update: HEAD ${old:0:12} is not an ancestor of $FOOTY_BRANCH ${new:0:12}; resolve by hand")
  job_finish
fi

# Local changes: only the records files may differ from HEAD, and they must equal the
# remote's (pushed above), so moving to the remote commit loses nothing.
rel=$(realpath --relative-to="$FOOTY_REPO" "$FOOTY_RECORDS_DIR")
others=$("${git[@]}" status --porcelain --untracked-files=all | grep -vE "^.. \"?$rel/" || true)
if [[ -n $others ]]; then
  JOB_FAILED+=("update: local changes outside $rel would be lost:"$'\n'"$(head -n 10 <<<"$others")")
  job_finish
fi
shopt -s nullglob
for f in "$FOOTY_RECORDS_DIR"/*.jsonl; do
  theirs=$("${git[@]}" rev-parse --verify --quiet "$new:$rel/$(basename "$f")" || true)
  if [[ $("${git[@]}" hash-object -- "$f") != "$theirs" ]]; then
    JOB_FAILED+=("update: $rel/$(basename "$f") differs from $FOOTY_BRANCH after the push")
    job_finish
  fi
done
shopt -u nullglob

changed=$("${git[@]}" diff --name-only "$old" "$new")
if ! grep -qv "^$rel/" <<<"$changed"; then
  # only the records this server pushed itself: catch up, nothing to rebuild or restart
  step checkout "${git[@]}" reset --quiet --hard "$new" || job_finish
  log "only records changed: now at ${new:0:12}"
  run_doctor
  job_finish
fi
note "updating ${old:0:12} -> ${new:0:12} ($("${git[@]}" rev-list --count "$old..$new") commits)"
step checkout "${git[@]}" reset --quiet --hard "$new" || job_finish

rollback() { # back to the old commit's code, keeping the records files as they are now
  warn "rolling back to ${old:0:12}"
  "${git[@]}" reset --quiet --hard "$old"
  # the old commit's records files are older than this server's (its pushes since are on the
  # remote branch only); put back the ones checked above, equal to the remote's
  "${git[@]}" checkout "$new" -- "$rel" && "${git[@]}" reset --quiet -- "$rel" ||
    JOB_FAILED+=("rollback: records files not restored from ${new:0:12}: run git checkout $FOOTY_BRANCH -- $rel")
  (cd "$FOOTY_PROJECT" && "$FOOTY_UV" sync --frozen --quiet) || true
  job_finish
}
# shellcheck disable=SC2016 # $1 and $2 are the inner script's arguments
step uv-sync bash -c 'cd "$1" && "$2" sync --frozen' _ "$FOOTY_PROJECT" "$FOOTY_UV" || rollback

schema=$(cd "$FOOTY_PROJECT" && "$FOOTY_UV" run --frozen --quiet python -c \
  'from footy.db import current_revision, get_engine, head_revision; print(current_revision(get_engine()), head_revision())')
read -r current head <<<"$schema"
if [[ $current != "$head" ]]; then
  note "migration needed: database at $current, code expects $head"
  step pre-migrate-backup db_backup "$FOOTY_BACKUP_DIR/pre-migrate-$(date -u +%Y%m%dT%H%M%SZ)-$current.sqlite3.gz" ||
    rollback
  step migrate footy migrate || {
    notify "Football server: MIGRATION FAILED" \
      "footy migrate failed after a backup. Restore with the operator steps in docs/server.md (Restore)." 5 rotating_light
    job_finish
  }
fi

if grep -q "^${FOOTY_PROJECT#"$FOOTY_REPO"/}/frontend/" <<<"$changed" || [[ ! -e $FOOTY_SITE_DIR/index.html ]]; then
  step frontend bash "$FOOTY_JOBS_DIR/../build-site.sh" || true
fi
read -ra systemctl <<<"$FOOTY_SYSTEMCTL"
step restart-api "${systemctl[@]}" restart footy-api.service || true

if grep -qE "/deploy/(systemd/|Caddyfile|setup\.sh|sudoers)" <<<"$changed"; then
  notify "Football server: setup changed" \
    "The update changed the server set-up files. Run once as root: sudo /home/footy/football-model/football/deploy/setup.sh" \
    3 wrench
fi
sleep 5
run_doctor --web
notify "Football server: updated" "Code updated to ${new:0:12}${current:+ (schema $head)}." 2 arrow_up
job_finish
