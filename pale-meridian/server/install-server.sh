#!/usr/bin/env bash
# Pale Meridian - dedicated server installer (Linux/macOS).
#
# Downloads the pinned Fabric server launcher and server-side mods listed in server-files.tsv,
# verifies every file's hash, and prepares a server folder. Safe to re-run: it never touches
# world/, server.properties, whitelist.json, ops.json or an existing eula.txt.
#
# It does NOT accept the Minecraft EULA for you: eula.txt is created with eula=false and you must
# read https://aka.ms/MinecraftEULA and change it yourself before the server will start.
#
# Usage: ./install-server.sh [--with-tools]     (--with-tools also installs Chunky and spark)
set -euo pipefail
cd "$(dirname "$0")"

WITH_TOOLS=0
for a in "$@"; do
  case "$a" in
    --with-tools) WITH_TOOLS=1 ;;
    *) echo "Unknown option: $a"; exit 2 ;;
  esac
done

need() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: '$1' is required but not installed."; exit 1; }; }
need java
need curl
if command -v sha512sum >/dev/null 2>&1; then SHA512="sha512sum"; else SHA512="shasum -a 512"; fi
if command -v sha256sum >/dev/null 2>&1; then SHA256="sha256sum"; else SHA256="shasum -a 256"; fi

# Java 25 or newer
# (take the line with 'version "': JAVA_TOOL_OPTIONS and similar make Java print extra lines first)
JLINE=$(java -version 2>&1 | grep -m1 'version "' || true)
JV=$(printf '%s' "$JLINE" | sed -E 's/.*version "([0-9]+).*/\1/')
if ! [[ "$JV" =~ ^[0-9]+$ ]] || [ "$JV" -lt 25 ]; then
  echo "ERROR: Java 25 or newer is required (found: ${JLINE:-no java version line})."
  echo "       Install a Java 25 runtime, e.g. Eclipse Temurin 25, and try again."
  exit 1
fi

UA="PaleMeridian-installer/1.0"
fetch() {  # url dest hash-algo expected
  local url="$1" dest="$2" algo="$3" want="$4" tmp got
  if [ -f "$dest" ]; then
    got=$($([ "$algo" = sha512 ] && echo "$SHA512" || echo "$SHA256") "$dest" | cut -d' ' -f1)
    if [ "$got" = "$want" ]; then echo "  ok (already present) $(basename "$dest")"; return 0; fi
    echo "  replacing $(basename "$dest") (hash mismatch)"
  fi
  tmp="$dest.part"
  for attempt in 1 2 3; do
    if curl -fsSL -A "$UA" -o "$tmp" "$url"; then break; fi
    echo "  download failed (attempt $attempt), retrying..."; sleep $((attempt * 2))
    [ "$attempt" = 3 ] && { echo "ERROR: could not download $url"; rm -f "$tmp"; exit 1; }
  done
  got=$($([ "$algo" = sha512 ] && echo "$SHA512" || echo "$SHA256") "$tmp" | cut -d' ' -f1)
  if [ "$got" != "$want" ]; then
    rm -f "$tmp"
    echo "ERROR: hash mismatch for $url"
    echo "       expected $algo $want"
    echo "       got          $got"
    echo "       Nothing was installed for this file. Do not bypass this check."
    exit 1
  fi
  mv "$tmp" "$dest"
  echo "  verified $(basename "$dest")"
}

mkdir -p mods
echo "Pale Meridian server install"
while IFS=$'\t' read -r kind name url algo hash; do
  case "$kind" in
    ""|\#*) continue ;;
    launcher) fetch "$url" "$name" "$algo" "$hash" ;;
    mod) fetch "$url" "mods/$name" "$algo" "$hash" ;;
    tool) if [ "$WITH_TOOLS" = 1 ]; then fetch "$url" "mods/$name" "$algo" "$hash"; fi ;;
  esac
done < server-files.tsv

# The Pale Meridian mod itself ships in this package (it is this project's own code).
[ -f mods/palemeridian-1.0.0.jar ] || { echo "ERROR: mods/palemeridian-1.0.0.jar is missing from the package."; exit 1; }

if [ ! -f server.properties ]; then cp server.properties.template server.properties; echo "  created server.properties (private: whitelist on)"; fi
if [ ! -f eula.txt ]; then
  printf '# Read the Minecraft EULA at https://aka.ms/MinecraftEULA\n# Change the next line to eula=true ONLY if you agree to it.\neula=false\n' > eula.txt
  echo "  created eula.txt (eula=false)"
fi
chmod +x start-server.sh backup-world.sh update-server.sh 2>/dev/null || true

cat <<'MSG'

Install complete. Before the first start:
  1. Read the Minecraft EULA: https://aka.ms/MinecraftEULA
     If you agree, edit eula.txt and change eula=false to eula=true yourself.
  2. Start the server once with ./start-server.sh, then add your players:
       whitelist add <PlayerName>      (typed in the server console)
  3. Players connect with the Pale Meridian client pack (same version).
See SERVER_GUIDE.md for ports, backups, updates and troubleshooting.
MSG
