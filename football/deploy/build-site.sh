#!/usr/bin/env bash
# Build the website (frontend/, `npm ci && npm run build`) and copy it to the folder Caddy
# serves ($FOOTY_SITE_DIR, default /srv/footy-site). Run by setup.sh and by the weekly update
# when frontend/ changed. The copy replaces the old site only once the build has succeeded.
set -Eeuo pipefail
here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project=$(cd "$here/.." && pwd)
site=${FOOTY_SITE_DIR:-/srv/footy-site}
cd "$project/frontend"
npm ci --no-audit --no-fund --loglevel=error
npm run build
[[ -s dist/index.html ]] || { echo "build produced no dist/index.html" >&2; exit 1; }
mkdir -p "$site"
rsync -a --delete --delay-updates dist/ "$site/"
echo "site: $(find "$site" -type f | wc -l) files in $site"
