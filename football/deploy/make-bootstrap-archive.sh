#!/usr/bin/env bash
# Build the bootstrap data archive for a new server (docs/server.md, "Bootstrap data").
#
#   deploy/make-bootstrap-archive.sh <output folder>
#
# What goes in: what the sources can't rebuild cheaply, from this machine's data/:
#   backtests/ and backtests_euro/  walk-forward ledgers (about a day of 4-core compute)
#   reports/ and lab/               selection, benchmark and profit reports (no generator)
#   europe/                         the 2026/27 UEFA CSVs built from Wikipedia, a fallback
#                                   while Wikimedia refuses a server
# Never: data/raw (football-data's files carry bookmakers' prices), the database, media,
# logs, or anything with a price column. The script refuses to build if any CSV or JSON
# ledger has a price-like column, if a file isn't one of the expected kinds, or if any file
# holds a secret from .env.
#
# Output: bootstrap-data.tar.xz split into parts under 50 MB (GitHub's warning size), with
# SHA256SUMS (parts and the whole archive), FILES.sha256 (every file, relative to data/),
# MANIFEST.json and README.md. The parts go on the orphan branch `bootstrap-data` of the
# private repository; deploy/bootstrap-data.sh fetches, checks and unpacks them.
set -Eeuo pipefail

here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project=$(cd "$here/.." && pwd)
data=${FOOTY_DATA_DIR:-$project/data}
part_size=${PART_SIZE:-45m}
dirs=(backtests backtests_euro reports lab europe)

if [[ $# -ne 1 || $1 == -h || $1 == --help ]]; then
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
fi
out=$1
mkdir -p "$out"
out=$(cd "$out" && pwd)
if compgen -G "$out/bootstrap-data.tar.xz.part-*" >/dev/null; then
  echo "Refused: $out already holds archive parts; use an empty folder" >&2
  exit 1
fi
for d in "${dirs[@]}"; do
  [[ -d $data/$d ]] || { echo "Refused: $data/$d is missing" >&2; exit 1; }
done

echo "Checking $data (${dirs[*]}) ..."
# Every check reads the files itself; the secret check reads .env without printing it or
# putting it on a command line.
python3 -I - "$data" "$project/.env" "${dirs[@]}" <<'PY'
import csv, json, pathlib, re, sys

data = pathlib.Path(sys.argv[1])
env = pathlib.Path(sys.argv[2])
dirs = sys.argv[3:]
ALLOWED = {".csv", ".json"}
# football-data's bookmaker columns (B365H, PSCH, AvgC>2.5, MaxH, BWA, ...) and generic names
BOOK = re.compile(
    r"^(B365|BW|IW|PS|WH|VC|Max|Avg|BF|BFE|BFD|1XB|LB|SJ|GB|BS|SB|SO|P|CL)(C)?"
    r"(H|D|A|>2\.5|<2\.5|AHH|AHA|CH|CD|CA)$"
)
WORDS = re.compile(r"price|odds|closing|implied|consensus|devig|bookmaker|overround", re.I)
problems, files, ledgers = [], 0, 0

def check_columns(cols, where):
    for c in cols:
        if BOOK.match(c) or WORDS.search(c):
            problems.append(f"{where}: price-like column {c!r}")

for d in dirs:
    for p in sorted((data / d).rglob("*")):
        if p.is_symlink():
            problems.append(f"{p}: symbolic link")
            continue
        if not p.is_file():
            continue
        files += 1
        rel = p.relative_to(data)
        if p.suffix not in ALLOWED:
            problems.append(f"{rel}: unexpected file type")
            continue
        if p.suffix == ".csv":
            with p.open(newline="", encoding="utf-8") as f:
                header = next(csv.reader(f), [])
            check_columns(header, str(rel))
            ledgers += p.name == "ledger.csv"
        elif p.name == "ledger.json":
            rows = json.loads(p.read_text(encoding="utf-8"))
            keys = set()
            for r in rows if isinstance(rows, list) else []:
                keys |= set(r)
            check_columns(sorted(keys), str(rel))

secrets = []
if env.is_file():
    for line in env.read_text(encoding="utf-8").splitlines():
        k, sep, v = line.partition("=")
        v = v.strip().strip('"').strip("'")
        if sep and re.search(r"TOKEN|KEY|SECRET|PASSWORD", k) and len(v) >= 8:
            secrets.append(v.encode())
if secrets:
    for d in dirs:
        for p in (data / d).rglob("*"):
            if p.is_file() and not p.is_symlink():
                blob = p.read_bytes()
                if any(s in blob for s in secrets):
                    problems.append(f"{p.relative_to(data)}: holds a secret from .env")

if problems:
    print("Refused, nothing written:", *problems[:40], sep="\n  ", file=sys.stderr)
    sys.exit(1)
print(f"  {files} files ({ledgers} ledgers): no price-like columns, no secrets, only CSV and JSON")
PY

stage=$(mktemp -d "$out/.stage.XXXXXX")
trap 'rm -rf "$stage"' EXIT

echo "Hashing every file ..."
(cd "$data" && find "${dirs[@]}" -type f -print0 | LC_ALL=C sort -z | xargs -0 sha256sum) \
  >"$stage/FILES.sha256"

echo "Compressing (xz) ..."
# Sorted names, no owner names: the same data gives the same tar stream.
tar --sort=name --owner=0 --group=0 --numeric-owner -C "$data" -cf - "${dirs[@]}" \
  | xz -T0 -6 >"$stage/bootstrap-data.tar.xz"
(cd "$stage" && split -b "$part_size" -d -a 3 bootstrap-data.tar.xz bootstrap-data.tar.xz.part-)

(
  cd "$stage"
  sha256sum bootstrap-data.tar.xz.part-* bootstrap-data.tar.xz >SHA256SUMS
)

commit=$(git -C "$project" rev-parse --short=12 HEAD 2>/dev/null || echo unknown)
python3 -I - "$stage" "$data" "$commit" "${dirs[@]}" <<'PY' >"$stage/MANIFEST.json"
import datetime, hashlib, json, pathlib, sys

stage, data, commit, dirs = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2]), sys.argv[3], sys.argv[4:]
sums = dict(reversed(line.split(maxsplit=1)) for line in (stage / "SHA256SUMS").read_text().splitlines())
parts = sorted(p for p in stage.iterdir() if ".part-" in p.name)
archive = stage / "bootstrap-data.tar.xz"

def tree(d):
    files = [p for p in (data / d).rglob("*") if p.is_file()]
    return {"files": len(files), "bytes": sum(p.stat().st_size for p in files)}

print(json.dumps({
    "format": "footy-bootstrap-data/1",
    "created_utc": datetime.datetime.now(datetime.UTC).replace(microsecond=0).isoformat(),
    "source_commit": commit,
    "unpack_into": "data/",
    "contents": {d: tree(d) for d in dirs},
    "archive": {"name": archive.name, "bytes": archive.stat().st_size, "sha256": sums[archive.name]},
    "parts": [{"name": p.name, "bytes": p.stat().st_size, "sha256": sums[p.name]} for p in parts],
    "checks": [
        "only .csv and .json files",
        "no price-like column in any CSV header or ledger.json record",
        "no secret from .env in any file",
        "no data/raw, no database, no media, no logs",
    ],
    "note": "The model + market ledgers (market_tilt_*, ml_online_market, ml_stack_market) hold "
            "model probabilities, which match by match are close to the bookmakers' de-vigged "
            "consensus (D-024): keep this branch in the private repository only.",
}, indent=2))
PY

cat >"$stage/README.md" <<'MD'
# Bootstrap data for a new server

Backtest ledgers, reports and lab tables from `data/` (no `data/raw`, no database, no
prices), for `football/deploy/bootstrap-data.sh`. The archive `bootstrap-data.tar.xz` is
split into parts under 50 MB; `SHA256SUMS` covers the parts and the whole archive,
`FILES.sha256` every file (paths relative to `data/`), and `MANIFEST.json` says what is in it.

Keep this branch (`bootstrap-data`) in the private repository only: the model + market
ledgers' probabilities are close to the bookmakers' de-vigged consensus match by match.

Restore by hand (the server's bootstrap does this):

    cat bootstrap-data.tar.xz.part-* > bootstrap-data.tar.xz
    sha256sum -c SHA256SUMS
    tar -xJf bootstrap-data.tar.xz -C <project>/data
    (cd <project>/data && sha256sum -c --quiet <this folder>/FILES.sha256)
MD

# Verify before handing over: parts join to the archive, which lists every hashed file.
(cd "$stage" && sha256sum -c --quiet SHA256SUMS)
listed=$(cat "$stage"/bootstrap-data.tar.xz.part-* | xz -dc | tar -tf - | grep -vc '/$' || true)
hashed=$(wc -l <"$stage/FILES.sha256")
if [[ $listed -ne $hashed ]]; then
  echo "Refused: the archive lists $listed files but $hashed were hashed" >&2
  exit 1
fi
rm "$stage/bootstrap-data.tar.xz"  # the parts are what is pushed (SHA256SUMS still checks the join)
mv "$stage"/* "$out"/
echo "Wrote $out:"
ls -l "$out"
