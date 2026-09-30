#!/usr/bin/env bash
# Updates the server files from a newer Pale Meridian server package, with a rollback copy.
# 1. Stop the server.  2. Back up the world (./backup-world.sh).
# 3. Unpack the NEW package into a separate folder and run:  ./update-server.sh /path/to/new-package
# The world, server.properties, whitelist, ops and eula.txt are never replaced.
set -euo pipefail
cd "$(dirname "$0")"
NEW="${1:?usage: ./update-server.sh /path/to/unpacked/new/package [--with-tools]}"
shift
[ -f "$NEW/server-files.tsv" ] || { echo "$NEW does not look like a Pale Meridian server package."; exit 1; }
[ -f .server-running ] && { echo "Stop the server first."; exit 1; }
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p backups
cp -a mods "backups/mods-$STAMP"
cp -a server-files.tsv lock.json "backups/" 2>/dev/null || true
echo "Rollback copy of mods/: backups/mods-$STAMP"
rm -f mods/*.jar
for f in server-files.tsv lock.json install-server.sh start-server.sh backup-world.sh update-server.sh \
         install-server.ps1 start-server.bat backup-world.ps1 update-server.ps1 server.properties.template SERVER_GUIDE.md README-FIRST.txt; do
  [ -f "$NEW/$f" ] && cp -f "$NEW/$f" .
done
cp -f "$NEW"/mods/palemeridian-*.jar mods/
./install-server.sh "$@"
echo "Updated. To roll back: stop the server, replace mods/ with backups/mods-$STAMP, and restore the world backup if needed."
