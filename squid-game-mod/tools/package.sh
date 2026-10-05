#!/usr/bin/env bash
# Builds the mod and assembles an installable bundle in dist/:
#   dist/mods/squidgame-<version>.jar            the mod (client and server)
#   dist/mods/fabric-api-<version>.jar           required dependency (Apache-2.0, https://modrinth.com/mod/fabric-api)
#   dist/mods/geckolib-fabric-1.21.1-<v>.jar     required dependency (MIT, https://modrinth.com/mod/geckolib)
#   dist/SHA256SUMS, dist/INSTALL.txt, dist/README.md, dist/LICENSE
# Usage: tools/package.sh            (runs the full build with the unit tests first; SKIP_BUILD=1 reuses build/libs)
# The dependency jars are taken from the Gradle cache that the build already filled (they are the exact files the mod was
# compiled and tested against); if the cache is empty they are downloaded from the official Maven repositories.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="$(sed -n 's/^mod_version=//p' gradle.properties)"
FAPI="$(sed -n 's/^fabric_version=//p' gradle.properties)"
GECKO="$(sed -n 's/^geckolib_version=//p' gradle.properties)"
MC="$(sed -n 's/^minecraft_version=//p' gradle.properties)"
[ -n "$VERSION" ] && [ -n "$FAPI" ] && [ -n "$GECKO" ] && [ -n "$MC" ] || { echo "gradle.properties is missing a version key"; exit 1; }

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  ./gradlew build --offline -q
fi
MOD_JAR="build/libs/squidgame-$VERSION.jar"
[ -f "$MOD_JAR" ] || { echo "missing $MOD_JAR (run ./gradlew build)"; exit 1; }

rm -rf dist
mkdir -p dist/mods
cp "$MOD_JAR" "dist/mods/"

fetch() { # name, cache pattern, url
  local name="$1" pattern="$2" url="$3" found
  found="$(find "$HOME/.gradle/caches/modules-2" -name "$pattern" -not -name '*-sources.jar' 2>/dev/null | head -1 || true)"
  if [ -n "$found" ]; then
    cp "$found" "dist/mods/$name"
  else
    curl -fsSL -o "dist/mods/$name" "$url"
  fi
}
fetch "fabric-api-$FAPI.jar" "fabric-api-$FAPI.jar" \
  "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FAPI/fabric-api-$FAPI.jar"
fetch "geckolib-fabric-$MC-$GECKO.jar" "geckolib-fabric-$MC-$GECKO.jar" \
  "https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/software/bernie/geckolib/geckolib-fabric-$MC/$GECKO/geckolib-fabric-$MC-$GECKO.jar"

cp README.md LICENSE dist/
( cd dist && sha256sum mods/*.jar > SHA256SUMS )

cat > dist/INSTALL.txt <<EOF
Squid Game: The Tournament $VERSION - installation (Minecraft Java Edition $MC, Fabric)

1. Install Java 21 and Fabric Loader 0.16.10 or newer for Minecraft $MC (https://fabricmc.net/use/).
2. Copy the three files of mods/ into the "mods" folder of your game (single-player / client) AND of your server.
   Every player on a server needs the same three files.
     squidgame-$VERSION.jar                  this mod
     fabric-api-$FAPI.jar    Fabric API (Apache-2.0)
     geckolib-fabric-$MC-$GECKO.jar        GeckoLib (MIT)
3. Start the game or the server. A dedicated server needs eula=true in eula.txt - you have to accept the Minecraft EULA
   (https://aka.ms/MinecraftEULA) yourself; nothing in this bundle does that for you.
4. In any world run  /squid enter  (no permissions needed) or craft a Recruiter's Card (paper + pink dye + black dye).
   The complex is generated the first time (about a minute, with progress messages).

Check the files with:  sha256sum -c SHA256SUMS
Full documentation (commands, controls, configuration, game rules): README.md and docs/ in the source repository.
EOF

echo "dist/ ready:"
ls -l dist/mods
cat dist/SHA256SUMS
