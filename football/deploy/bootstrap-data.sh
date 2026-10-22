#!/usr/bin/env bash
# Rebuild data/ on a new server (docs/server.md, "Bootstrap data"). Run as the footy user by
# setup.sh; safe to run again: every step is idempotent, the archive is unpacked once, and a
# finished bootstrap is not repeated (~/.local/state/footy/bootstrap.done) unless --again.
#
#  1. `footy migrate` on the new database.
#  2. The openfootball clones (league schedules and UEFA history, CC0) beside the repository.
#  3. The bootstrap archive (backtest ledgers, reports, lab tables, the 2026/27 UEFA CSV)
#     from the private repository's `bootstrap-data` branch: parts checked, joined, checked,
#     unpacked without overwriting anything, every file checked.
#  4. `footy fd-update`: every football-data season (polite, about 15 minutes).
#  5. UEFA matches: openfootball and Wikipedia (if Wikimedia answers this server; otherwise
#     the archived 2026/27 CSV, and a warning until a fetch succeeds).
#  6. Team pictures from Wikimedia Commons, if it answers (resumable; Monday retries).
#  7. `footy import-records`: every published record back, with its id (audit H4); then
#     `footy settle`.
source "$(dirname "${BASH_SOURCE[0]}")/jobs/lib.sh"

again=0
[[ ${1:-} == --again ]] && again=1
mkdir -p "$FOOTY_STATE_DIR"
if [[ -e $FOOTY_STATE_DIR/bootstrap.done && $again == 0 ]]; then
  echo "Bootstrap already done ($(cat "$FOOTY_STATE_DIR/bootstrap.done")); --again to repeat it"
  exit 0
fi
job_start bootstrap
data=$FOOTY_PROJECT/data
mkdir -p "$data"

# 0. The records import must exist before anything is fetched (it was added for audit H4).
if ! footy import-records --help >/dev/null 2>&1; then
  JOB_FAILED+=("this code has no \`footy import-records\`: the published records can't be restored, so the server must not publish. Update the repository (git pull) and run setup.sh again.")
  job_finish
fi

step migrate footy migrate || job_finish

# 2. openfootball (fixtures.py and euro-update read these clones)
mkdir -p "$FOOTY_OPENFOOTBALL"
for repo in champions-league england espana deutschland italy france; do
  dest=$FOOTY_OPENFOOTBALL/$repo
  if [[ -d $dest/.git ]]; then
    continue
  fi
  step "clone-$repo" git clone --quiet --depth 1 "https://github.com/openfootball/$repo.git" "$dest" ||
    job_finish
done

# 3. The archive
if [[ ! -e $FOOTY_STATE_DIR/bootstrap-archive.done ]]; then
  ref=refs/remotes/$FOOTY_REMOTE/bootstrap-data
  # the whole branch (one commit): `--depth 1` would make the repository shallow
  step fetch-archive git -C "$FOOTY_REPO" fetch --quiet "$FOOTY_REMOTE" \
    "+refs/heads/bootstrap-data:$ref" || {
    JOB_FAILED+=("the repository has no bootstrap-data branch yet (deploy/make-bootstrap-archive.sh makes it)")
    job_finish
  }
  parts=$JOB_TMP/parts
  stage=$data/.bootstrap-stage
  rm -rf "$parts" "$stage"
  mkdir -p "$parts" "$stage"
  git -C "$FOOTY_REPO" archive "$ref" | tar -x -C "$parts"
  if ! (cd "$parts" && sha256sum -c --quiet --ignore-missing SHA256SUMS); then
    JOB_FAILED+=("bootstrap archive: a part doesn't match SHA256SUMS")
    job_finish
  fi
  cat "$parts"/bootstrap-data.tar.xz.part-* >"$parts/bootstrap-data.tar.xz"
  (cd "$parts" && sha256sum -c --quiet SHA256SUMS) || {
    JOB_FAILED+=("bootstrap archive: the joined archive doesn't match SHA256SUMS")
    job_finish
  }
  rm "$parts"/bootstrap-data.tar.xz.part-*
  tar -xJf "$parts/bootstrap-data.tar.xz" -C "$stage" --no-same-owner
  (cd "$stage" && sha256sum -c --quiet "$parts/FILES.sha256") || {
    JOB_FAILED+=("bootstrap archive: unpacked files don't match FILES.sha256")
    job_finish
  }
  for d in "$stage"/*/; do
    name=$(basename "$d")
    if [[ ! -e $data/$name ]]; then
      mv "$d" "$data/$name"
    else # never overwrite what this server already has (a rebuilt UEFA CSV, newer ledgers)
      cp -a --no-clobber "$d." "$data/$name/"
    fi
  done
  cp "$parts/MANIFEST.json" "$FOOTY_STATE_DIR/bootstrap-manifest.json"
  rm -rf "$stage" "$parts"
  note "bootstrap archive unpacked: $(jq -c '.contents' "$FOOTY_STATE_DIR/bootstrap-manifest.json")"
  date -u +%Y-%m-%dT%H:%M:%SZ >"$FOOTY_STATE_DIR/bootstrap-archive.done"
fi

# 4. football-data: every season and division (download problems are retried)
for attempt in 1 2 3; do
  if fd_update "fd-update-$attempt"; then
    break
  fi
  if [[ $attempt == 3 ]]; then
    job_finish
  fi
  unset 'JOB_FAILED[-1]'
  sleep 60
done

# 5. UEFA
euro_update
if [[ ! -e $FOOTY_STATE_DIR/wikipedia-ok ]]; then
  echo "Wikipedia didn't answer this server during the bootstrap: the 2026/27 UEFA matches come from the archived CSV, and the 2025/26 Europa and Conference League history may be missing until a fetch succeeds (the UEFA night and Monday jobs retry)." \
    >"$FOOTY_STATE_DIR/warn-wikipedia"
  forgive euro-
fi

# 6. Team pictures (free licences only); not essential
if [[ $FOOTY_JOBS_OFFLINE != 1 ]]; then
  rc=0
  step team-media footy team-media --download || rc=$?
  if [[ $rc -ne 0 ]]; then
    forgive team-media
    echo "Team pictures not (fully) downloaded: Wikimedia Commons refused or failed; the site draws badges meanwhile. The Monday job retries." \
      >"$FOOTY_STATE_DIR/warn-media"
  else
    rm -f "$FOOTY_STATE_DIR/warn-media"
  fi
fi

# 7. The published records, then settlement
step import-records footy import-records || job_finish
step settle footy settle || true

# the report only (no notification, no web check: the API and the web server start after
# the bootstrap, so they can't answer yet)
(
  unset FOOTY_SITE_URL
  footy doctor --quiet
) || true
if [[ ${#JOB_FAILED[@]} -eq 0 ]]; then
  date -u +%Y-%m-%dT%H:%M:%SZ >"$FOOTY_STATE_DIR/bootstrap.done"
fi
job_finish
