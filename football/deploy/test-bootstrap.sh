#!/usr/bin/env bash
# Rehearsal of a new server's data bootstrap (deploy/bootstrap-data.sh), on any machine:
# pushes the bootstrap archive to a local bare repository's orphan branch `bootstrap-data`
# exactly as docs/server.md tells the operator to, clones it into a fake HOME with an EMPTY
# data/, and runs the bootstrap: archive fetched, checked and unpacked, every published record
# imported, settled. Offline (FOOTY_JOBS_OFFLINE=1): football-data's files are put in the
# copy's inbox (data/inbox/football-data, from this machine's raw store) and the
# champions-league clone is read where it is, so `fd-update --no-download` and `euro-update`
# rebuild the matches without a download; a real server downloads them instead. Wikipedia
# isn't asked, as if it refused the server. Nothing outside <work dir> is changed; this
# machine's database, raw store and clone are only read.
#
#   deploy/test-bootstrap.sh <empty work dir> <folder made by make-bootstrap-archive.sh>
set -Eeuo pipefail

src_project=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
src_repo=$(git -C "$src_project" rev-parse --show-toplevel)
rel=$(realpath --relative-to="$src_repo" "$src_project")
[[ $# -eq 2 ]] || { sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
mkdir -p "$1"
W=$(cd "$1" && pwd)
ARCHIVE=$(cd "$2" && pwd)
[[ -z $(ls -A "$W") ]] || { echo "use an empty work folder ($W isn't)"; exit 2; }
H=$W/home
R=$H/football-model
P=$R/$rel
STATE=$H/.local/state/footy
UV_BIN=$(command -v uv)
UV_CACHE=$(uv cache dir 2>/dev/null)
fail=0
check() {
  local what=$1
  shift
  if "$@" >/dev/null 2>&1; then echo "PASS  $what"; else echo "FAIL  $what" && fail=1; fi
}

echo "=== remote with main (this working tree) and bootstrap-data (the archive)"
seed=$W/seed
mkdir -p "$seed/$rel"
git init -q -b main "$seed"
tar -C "$src_project" --exclude=./data --exclude=./.venv --exclude=node_modules \
  --exclude=./frontend/dist --exclude=./frontend/dist-snapshot --exclude=./.env \
  --exclude=.pytest_cache --exclude=.ruff_cache --exclude=__pycache__ \
  --exclude='.publish-pending-*' -cf - . | tar -C "$seed/$rel" -xf -
git -C "$seed" add -A
git -C "$seed" -c user.name=test -c user.email=test@example.invalid commit -q -m "test snapshot"
git init -q --bare -b main "$W/remote.git"
git -C "$seed" push -q "$W/remote.git" main
# the operator's steps (docs/server.md, "Bootstrap data"), on a copy of the archive folder
cp -a "$ARCHIVE" "$W/archive"
(
  cd "$W/archive"
  git init -q -b bootstrap-data
  git add -A
  git -c user.name=lead -c user.email=lead@test.invalid commit -qm "Bootstrap data"
  git push -q "$W/remote.git" bootstrap-data
)
mkdir -p "$H"
git clone -q "$W/remote.git" "$R"

echo "=== an empty data/, and football-data's files in its inbox (offline stand-in for the downloads)"
inbox=$P/data/inbox/football-data
mkdir -p "$inbox"
python3 -I - "$src_project/data/footy.sqlite3" "$src_project/data/raw" "$inbox" <<'PY'
import shutil, sqlite3, sys
from pathlib import Path
db, raw, inbox = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
c = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
rows = c.execute(
    "SELECT source, logical_key, storage_path FROM raw_files r WHERE source IN "
    "('football-data', 'football-data-extra') AND revision = (SELECT max(revision) "
    "FROM raw_files x WHERE x.source = r.source AND x.logical_key = r.logical_key)"
).fetchall()
for source, key, path in rows:
    a, b = key.split("/")
    name = f"{a}_{b}.csv" if source == "football-data" else f"new_{b}.csv"
    shutil.copyfile(raw / path, inbox / name)
print(f"{len(rows)} files in the inbox")
PY
for repo in champions-league england espana deutschland italy france; do
  git init -q "$W/openfootball/$repo" # present, so the bootstrap doesn't clone (offline)
done
mkdir -p "$H/.config/footy"
cat >"$H/.config/footy/jobs.env" <<EOF
FOOTY_JOBS_OFFLINE=1
FOOTY_STATE_DIR=$STATE
FOOTY_OPENFOOTBALL=$W/openfootball
FOOTY_OPENFOOTBALL_DIR=${OPENFOOTBALL_CL:-/home/user/openfootball/champions-league}
EOF
run_env=(env -i "HOME=$H" "PATH=$(dirname "$UV_BIN"):/usr/local/bin:/usr/bin:/bin"
  "UV_CACHE_DIR=$UV_CACHE" "UV_OFFLINE=1" "LANG=C.UTF-8" "TMPDIR=$W/tmp")
mkdir -p "$W/tmp"
(cd "$P" && HOME=$H UV_CACHE_DIR=$UV_CACHE "$UV_BIN" sync --frozen --quiet)
root=$(cd "$P" && "${run_env[@]}" "$UV_BIN" run --frozen --quiet python -c \
  'from footy.config import PROJECT_ROOT; print(PROJECT_ROOT)')
[[ $root == "$P" ]] || { echo "STOP: the copy's footy would write to $root"; exit 1; }

echo "=== bootstrap"
started=$SECONDS
rc=0
"${run_env[@]}" bash "$P/deploy/bootstrap-data.sh" >"$W/bootstrap.log" 2>&1 || rc=$?
echo "bootstrap-data.sh: exit $rc in $((SECONDS - started))s (log $W/bootstrap.log)"
grep -E '^\[bootstrap\]' "$W/bootstrap.log" | sed "s|$W|W|g"

# shellcheck disable=SC2329 # called through check
py() { (cd "$P" && "${run_env[@]}" "$UV_BIN" run --frozen --quiet python -c "$1"); }
lines=$(cat "$P"/records/predictions/*.jsonl | grep -c .)
check "bootstrap finished" test -e "$STATE/bootstrap.done"
check "archive unpacked: 237 backtest files" test "$(find "$P/data/backtests" -type f | wc -l)" -eq 237
check "every file matches FILES.sha256" bash -c "cd '$P/data' && sha256sum -c --quiet '$ARCHIVE/FILES.sha256'"
check "every published record imported ($lines)" py "
from sqlalchemy import text
from footy.db import get_engine
with get_engine().connect() as c:
    n, top = c.execute(text('select count(*), max(id) from predictions')).one()
assert n == $lines, n
print(n, top)"
check "the matches were rebuilt" py "
from sqlalchemy import text
from footy.db import get_engine
with get_engine().connect() as c:
    m = c.execute(text('select count(*) from matches')).scalar()
    e = c.execute(text('select count(*) from euro_matches')).scalar()
print(m, e)
assert m > 60000 and e > 3500, (m, e)"
rc2=0
"${run_env[@]}" bash "$P/deploy/bootstrap-data.sh" >"$W/bootstrap-again.log" 2>&1 || rc2=$?
check "a second run does nothing (exit $rc2)" grep -q "already done" "$W/bootstrap-again.log"
echo "=== doctor"
(cd "$P" && "${run_env[@]}" "FOOTY_STATE_DIR=$STATE" "$UV_BIN" run --frozen --quiet footy doctor --quiet) |
  sed "s|$W|W|g" || true
exit "$fail"
