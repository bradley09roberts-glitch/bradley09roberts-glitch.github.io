#!/usr/bin/env bash
# Dry run of the scheduled jobs against a COPY, for checking the deploy kit on any machine
# (docs/server.md, "Testing the jobs"). Builds, inside <work dir> only:
#   remote.git                  a bare repository standing in for GitHub (holds this working
#                               tree as one commit, records included)
#   home/                       a fake HOME for the footy user, with the clone in
#                               home/football-model and the jobs' state, backups and config
#   home/football-model/<project>/data   a copy of the database, raw store and UEFA CSVs
#   ntfy.jsonl                  every notification, caught by a local stand-in for ntfy.sh
# then runs every job (and the failure, lock, refusal and migration-guard cases) with
# FOOTY_JOBS_OFFLINE=1: no request leaves the machine, nothing is pushed anywhere real, and
# the real data/ and records/ are only read.
#
#   deploy/test-jobs.sh <empty work dir> [--keep-going]
set -Eeuo pipefail

src_project=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
src_repo=$(git -C "$src_project" rev-parse --show-toplevel)
rel=$(realpath --relative-to="$src_repo" "$src_project")
[[ $# -ge 1 ]] || { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
mkdir -p "$1"
W=$(cd "$1" && pwd)
[[ -z $(ls -A "$W") ]] || { echo "use an empty work folder ($W isn't)"; exit 2; }
H=$W/home
R=$H/football-model
P=$R/$rel
REMOTE=$W/remote.git
STATE=$H/.local/state/footy
NTFY_PORT=${NTFY_PORT:-18765}
UV_BIN=$(command -v uv)
UV_CACHE=$(uv cache dir 2>/dev/null)
passes=0 failures=0
results=()

say() { printf '\n=== %s\n' "$*"; }
check() { # check <description> <command...>
  local what=$1
  shift
  if "$@" >/dev/null 2>&1; then
    passes=$((passes + 1)) && results+=("PASS  $what")
  else
    failures=$((failures + 1)) && results+=("FAIL  $what")
  fi
  printf '%s\n' "${results[-1]}"
}

say "Seed repository and fake remote"
seed=$W/seed
git init -q -b main "$seed"
mkdir -p "$seed/$rel"
tar -C "$src_project" --exclude=./data --exclude=./.venv --exclude=node_modules \
  --exclude=./frontend/dist --exclude=./frontend/dist-snapshot --exclude=./.env \
  --exclude=.pytest_cache --exclude=.ruff_cache --exclude=__pycache__ \
  --exclude='.publish-pending-*' -cf - . | tar -C "$seed/$rel" -xf -
git -C "$seed" add -A
git -C "$seed" -c user.name=test -c user.email=test@example.invalid commit -q -m "test snapshot"
git clone -q --bare "$seed" "$REMOTE"
rm -rf "$seed"
mkdir -p "$H"
git clone -q "$REMOTE" "$R"
git -C "$R" config user.name "football-model server (test)"
git -C "$R" config user.email "footy@test.invalid"
seed_head=$(git -C "$R" rev-parse HEAD)

say "Copy of the data (database by SQLite's backup API, raw store, UEFA CSVs)"
mkdir -p "$P/data"
python3 -I - "$src_project/data/footy.sqlite3" "$P/data/footy.sqlite3" <<'PY'
import sqlite3, sys
src = sqlite3.connect(f"file:{sys.argv[1]}?mode=ro", uri=True)
dst = sqlite3.connect(sys.argv[2])
src.backup(dst)
dst.close()
PY
tar -C "$src_project/data" -cf - raw europe | tar -C "$P/data" -xf -

say "Python environment for the copy"
run_env=(env -i "HOME=$H" "PATH=$(dirname "$UV_BIN"):/usr/local/bin:/usr/bin:/bin"
  "UV_CACHE_DIR=$UV_CACHE" "UV_OFFLINE=1" "LANG=C.UTF-8" "TMPDIR=$W/tmp"
  "NO_PROXY=127.0.0.1,localhost" "no_proxy=127.0.0.1,localhost")
mkdir -p "$W/tmp"
# with this shell's environment (its proxy settings), in case the cache lacks a package
(cd "$P" && HOME=$H UV_CACHE_DIR=$UV_CACHE "$UV_BIN" sync --frozen --quiet)
root=$(cd "$P" && "${run_env[@]}" "$UV_BIN" run --frozen --quiet python -c \
  'from footy.config import PROJECT_ROOT; print(PROJECT_ROOT)')
if [[ $root != "$P" ]]; then
  echo "STOP: the copy's footy would write to $root, not $P"
  exit 1
fi
echo "the copy's footy works in $root"

say "Local stand-in for ntfy.sh on port $NTFY_PORT"
cat >"$W/ntfy.py" <<'PY'
import http.server, json, sys
out, port = sys.argv[1], int(sys.argv[2])
class H(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0))).decode()
        with open(out, "a") as f:
            f.write(json.dumps({"topic": self.path.strip("/"), "title": self.headers.get("Title"),
                                "priority": self.headers.get("Priority"),
                                "tags": self.headers.get("Tags"), "body": body}) + "\n")
        self.send_response(200); self.end_headers(); self.wfile.write(b"{}")
    def log_message(self, *a): pass
http.server.HTTPServer(("127.0.0.1", port), H).serve_forever()
PY
python3 -I "$W/ntfy.py" "$W/ntfy.jsonl" "$NTFY_PORT" &
ntfy_pid=$!
holder_pid=""
trap 'kill $ntfy_pid ${holder_pid:-} 2>/dev/null || true' EXIT
sleep 1

mkdir -p "$H/.config/footy"
cat >"$H/.config/footy/jobs.env" <<EOF
NTFY_URL=http://127.0.0.1:$NTFY_PORT
NTFY_TOPIC=footy-test
FOOTY_JOBS_OFFLINE=1
FOOTY_SYSTEMCTL='echo would-run systemctl'
FOOTY_SITE_DIR=$W/site
FOOTY_STATE_DIR=$STATE
FOOTY_BACKUP_DIR=$H/backups
FOOTY_OPENFOOTBALL=$W/openfootball
FOOTY_SHADOWS_PURE=baseline_frequency,euro_frequency
FOOTY_SHADOWS_DOMESTIC=baseline_frequency,market_tilt_dc
EOF
mkdir -p "$W/site" && echo "built site stand-in" >"$W/site/index.html" # skip npm unless frontend/ changes

job() { # job <name> [VAR=value ...]: run deploy/jobs/<name>.sh in the copy; log in $W/logs
  local name=$1
  shift
  mkdir -p "$W/logs"
  local log="$W/logs/$name${TAG:+-$TAG}.log" rc=0
  local started=$SECONDS
  "${run_env[@]}" "$@" bash "$P/deploy/jobs/$name.sh" >"$log" 2>&1 || rc=$?
  echo "$name${TAG:+ ($TAG)}: exit $rc in $((SECONDS - started))s (log $log)"
  return "$rc"
}
copy_py() { (cd "$P" && "${run_env[@]}" "$UV_BIN" run --frozen --quiet python -c "$1"); }
ntfy_has() { grep -q -- "$1" "$W/ntfy.jsonl" 2>/dev/null; }
remote_matches_local() { # every records file pushed: the remote's blob equals ours
  local f
  for f in "$P"/records/predictions/*.jsonl; do
    [[ $(git -C "$REMOTE" rev-parse "main:$rel/records/predictions/$(basename "$f")" 2>/dev/null) == \
      $(git hash-object "$f") ]] || return 1
  done
}

say "capture-odds (no provider key in the copy: does nothing)"
job capture-odds && check "capture-odds succeeds without a provider" test -e "$STATE/last-success-capture-odds"

say "daily (results, settle, fallback and UEFA records, two pure shadow models, push, doctor)"
# what a backup taken now holds (database and records files), for the restore case below
python3 -I - "$P/data/footy.sqlite3" "$W/pre-daily.sqlite3" <<'PY'
import sqlite3, sys
src, dst = sqlite3.connect(sys.argv[1]), sqlite3.connect(sys.argv[2])
src.backup(dst)
dst.close()
PY
tar -C "$P/records/predictions" -cf "$W/pre-daily-records.tar" .
lines_before=$(cat "$P"/records/predictions/*.jsonl | wc -l)
job daily || true
lines_after=$(cat "$P"/records/predictions/*.jsonl | wc -l)
echo "records lines: $lines_before -> $lines_after"
check "daily succeeds" test -e "$STATE/last-success-daily"
check "daily's records are on the remote" remote_matches_local
check "HEAD of the clone did not move (records pushed with plumbing)" \
  test "$(git -C "$R" rev-parse HEAD)" = "$seed_head"

say "a disk restored from an older backup: the next job takes the records already pushed first"
cp "$W/pre-daily.sqlite3" "$P/data/footy.sqlite3"
rm -f "$P/data/footy.sqlite3-wal" "$P/data/footy.sqlite3-shm" "$W/pre-daily.sqlite3"
find "$P/records/predictions" -maxdepth 1 -name '*.jsonl' -delete
tar -C "$P/records/predictions" -xf "$W/pre-daily-records.tar"
TAG=restored job daily || true
check "after a restore, the records already pushed come back (and nothing conflicts)" \
  remote_matches_local
check "... none of them is published twice (one record per model and fixture, here and on the remote)" copy_py "
import collections, json, pathlib, subprocess
remote, rel = '$REMOTE', '$rel/records/predictions'
git = lambda *a: subprocess.run(['git', '-C', remote, *a], capture_output=True, text=True, check=True).stdout
lines = set()
for p in pathlib.Path('records/predictions').glob('*.jsonl'):
    lines |= set(p.read_text().splitlines())
for name in git('ls-tree', '--name-only', 'main', rel + '/').split():
    lines |= set(git('show', 'main:' + name).splitlines())
recs = [json.loads(x) for x in lines if x.strip()]
n = collections.Counter((r['fixture_ref'], r['model_name']) for r in recs if r['record_type'] == 'genuine_prematch')
ids = collections.Counter(r['id'] for r in recs)
assert not [k for k, c in n.items() if c > 1] and not [k for k, c in ids.items() if c > 1]"
check "... and the database holds every record again" copy_py '
from footy.db import get_engine
from footy.publish import check_records
check_records(get_engine())'

say "update: uv sync fails (a network error), so it rolls back; the records stay as pushed"
dev0=$W/dev0
git clone -q "$REMOTE" "$dev0"
echo "rollback test" >>"$dev0/$rel/docs/rollback-test.md"
git -C "$dev0" add -A
git -C "$dev0" -c user.name=lead -c user.email=lead@test.invalid commit -qm "test: rollback"
git -C "$dev0" push -q origin main
cat >"$W/uv-no-sync" <<EOF
#!/usr/bin/env bash
if [[ \$1 == sync ]]; then echo "uv sync: simulated network failure" >&2; exit 1; fi
exec "$UV_BIN" "\$@"
EOF
chmod +x "$W/uv-no-sync"
TAG=rollback job update "FOOTY_UV=$W/uv-no-sync" || true
check "a failed uv sync puts the code back" test "$(git -C "$R" rev-parse HEAD)" = "$seed_head"
check "... keeps every records line already pushed" remote_matches_local
check "... and notifies" ntfy_has "uv-sync (exit 1)"
git -C "$dev0" -c user.name=lead -c user.email=lead@test.invalid revert --no-edit HEAD >/dev/null
git -C "$dev0" push -q origin main
rm -rf "$dev0"

say "prices: success path (the stored fixtures.csv's publication day as 'today')"
published=$(cd "$P" && "${run_env[@]}" "$UV_BIN" run --frozen --quiet python -c '
from footy.db import get_engine
from footy.ingest.fd_fixtures import latest_snapshot
s = latest_snapshot(get_engine())
print(s.attrs["published_at"] if s is not None else "")')
pub_day=$(TZ=Europe/London date -d "$published" +%F)
echo "stored fixtures.csv published $published (UK day $pub_day)"
TAG=success job prices "FOOTY_TODAY=$pub_day" || true
check "prices success path finishes the day" test -e "$STATE/prices-$pub_day.done"
check "prices success path notifies" ntfy_has "prices in"
check "prices records pushed" remote_matches_local
TAG=again job prices "FOOTY_TODAY=$pub_day" || true
check "a later attempt the same day does nothing" grep -q "already handled today" "$W/logs/prices-again.log"

say "prices: the file never came (one try allowed)"
TAG=giveup job prices "FOOTY_TODAY=2030-01-01" "FOOTY_PRICES_TRIES=1" || true
check "prices gives up and notifies" ntfy_has "no prices today"
TAG=retry job prices "FOOTY_TODAY=2030-01-02" "FOOTY_PRICES_TRIES=3" || true
check "an early try waits for the next one" grep -q "next try later" "$W/logs/prices-retry.log"

say "uefa-night and monday"
job uefa-night || true
check "uefa-night succeeds" test -e "$STATE/last-success-uefa-night"
job monday || true
check "monday succeeds" test -e "$STATE/last-success-monday"
check "monday sends the weekly summary" ntfy_has "weekly summary"

say "backup"
job backup || true
backup_file=$(compgen -G "$H/backups/footy-*.sqlite3.gz" | head -n 1 || true)
check "backup written" test -s "$backup_file"
check "backup is a sound database" bash -c "gunzip -c '$backup_file' > '$W/tmp/restore.sqlite3' &&
  python3 -I -c \"import sqlite3,sys; c=sqlite3.connect(sys.argv[1]); assert c.execute('PRAGMA quick_check').fetchone()[0]=='ok'; print(c.execute('select count(*) from predictions').fetchone())\" '$W/tmp/restore.sqlite3'"

say "update: only the server's own records commits are new"
TAG=same job update || true
check "update with only records catches up quietly" grep -qE "already at|only records changed" \
  "$W/logs/update-same.log"
check "... and leaves the API alone" bash -c "! grep -q 'would-run systemctl' '$W/logs/update-same.log'"

say "update: a new commit with a (no-op) migration: the guard backs up, then migrates"
dev=$W/dev
git clone -q "$REMOTE" "$dev"
cat >"$dev/$rel/migrations/versions/0099_test_noop.py" <<'PY'
"""Test-only no-op migration (deploy/test-jobs.sh): exercises the update's migration guard."""

revision = "0099"
down_revision = "0006"
branch_labels = None
depends_on = None


def upgrade() -> None:
    pass


def downgrade() -> None:
    pass
PY
head_before=$(cd "$P" && "${run_env[@]}" "$UV_BIN" run --frozen --quiet python -c \
  'from footy.db import head_revision; print(head_revision())')
if [[ $head_before == 0006 ]]; then
  git -C "$dev" add -A
  git -C "$dev" -c user.name=lead -c user.email=lead@test.invalid commit -q -m "test: no-op migration"
  git -C "$dev" push -q origin main
  TAG=migrate job update || true
  check "update moved HEAD to the remote" test "$(git -C "$R" rev-parse HEAD)" = "$(git -C "$REMOTE" rev-parse main)"
  check "update backed up before migrating" compgen -G "$H/backups/pre-migrate-*-0006.sqlite3.gz"
  check "update migrated the copy to 0099" copy_py \
    'from footy.db import current_revision, get_engine; assert current_revision(get_engine()) == "0099"'
  check "update restarted the API (stand-in)" grep -q "would-run systemctl restart footy-api.service" "$W/logs/update-migrate.log"
else
  echo "(skipped: the code's head is $head_before, not 0006; the test migration would not apply)"
fi

say "update refuses when the working tree has other local changes"
echo "local edit" >"$P/src/footy/local-change.txt"
git -C "$dev" pull -q
echo "test" >>"$dev/$rel/docs/test-note.md"
git -C "$dev" add -A
git -C "$dev" -c user.name=lead -c user.email=lead@test.invalid commit -qm "test: docs"
git -C "$dev" push -q origin main
TAG=dirty job update || true
check "update refused (local changes)" grep -q "local changes outside" "$W/logs/update-dirty.log"
check "the refusal was notified" ntfy_has "local changes outside"
rm "$P/src/footy/local-change.txt"

say "records push refuses when the remote's records aren't the start of ours"
git -C "$dev" pull -q
f=$(compgen -G "$dev/$rel/records/predictions/*.jsonl" | head -n 1)
sed -i '1s/^/{"forged": true}\n/' "$f"
git -C "$dev" -c user.name=lead -c user.email=lead@test.invalid commit -qam "test: forged line"
git -C "$dev" push -q origin main
TAG=refused job backup || true
check "push refused (the records differ both ways)" \
  grep -qE "differs from main's both ways|isn't the start of this server's copy" "$W/logs/backup-refused.log"
check "the refused push was notified" ntfy_has "backup failed"
git -C "$dev" -c user.name=lead -c user.email=lead@test.invalid revert --no-edit HEAD >/dev/null
git -C "$dev" push -q origin main

say "failures, rate limit and locks"
: >"$W/ntfy.before" && cp "$W/ntfy.jsonl" "$W/ntfy.before"
TAG=fail job capture-odds FOOTY_UV=/bin/false || true
TAG=fail2 job capture-odds FOOTY_UV=/bin/false || true
n=$(grep -c "capture-odds failed" "$W/ntfy.jsonl" || true)
check "a capture failure is notified once in 6 hours (got $n)" test "$n" -eq 1
flock "$STATE/locks/jobs.lock" sleep 30 &
holder_pid=$!
sleep 1
TAG=locked job backup FOOTY_LOCK_WAIT=2 || true
check "a job waits for the jobs lock, then gives up" grep -q "held the jobs lock" "$W/logs/backup-locked.log"
TAG=parallel job capture-odds || true
check "the odds capture doesn't wait for the jobs lock" grep -q "\[capture-odds\] done" "$W/logs/capture-odds-parallel.log"
kill "$holder_pid" 2>/dev/null || true
flock -o "$STATE/locks/capture.lock" sleep 20 &
holder_pid=$!
sleep 1
rc=0
TAG=busy job capture-odds FOOTY_CAPTURE_LOCK_WAIT=2 || rc=$?
check "the odds capture skips quietly while the update holds its lock (exit $rc)" \
  bash -c "[[ $rc == 0 ]] && grep -q 'skipped' '$W/logs/capture-odds-busy.log'"
kill "$holder_pid" 2>/dev/null || true
"${run_env[@]}" bash "$P/deploy/jobs/notify-failure.sh" footy-test.service >"$W/logs/notify-failure.log" 2>&1 || true
check "the OnFailure handler notifies" ntfy_has "footy-test.service failed"

say "footy doctor on the copy (state folder, records against the remote)"
git -C "$R" fetch -q origin "+refs/heads/main:refs/remotes/origin/main"
(cd "$P" && "${run_env[@]}" "FOOTY_STATE_DIR=$STATE" "FOOTY_RECORDS_REF=refs/remotes/origin/main" \
  "$UV_BIN" run --frozen --quiet footy doctor) | tee "$W/logs/doctor.log" || true
check "doctor lists the scheduled jobs" grep -q "timers" "$W/logs/doctor.log"

say "Summary"
printf '%s\n' "${results[@]}"
echo "notifications caught: $(wc -l <"$W/ntfy.jsonl")  (in $W/ntfy.jsonl)"
echo "$passes passed, $failures failed"
[[ $failures -eq 0 ]]
