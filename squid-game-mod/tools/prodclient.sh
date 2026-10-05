#!/usr/bin/env bash
# Production-style single-player client: started the way a launcher starts it, not through Loom's development environment.
#   * Fabric Loader (default 0.19.5, the version of the launcher profile a player creates) is the main class and remaps the
#     OBFUSCATED vanilla client jar to intermediary at start-up, exactly as on a player's machine
#   * mods/ contains only the jars of dist/mods (the installable bundle; override with SQUID_MODS_DIR)
# Everything else (vanilla libraries, assets, LWJGL natives) is taken from the Gradle / Loom caches the build already filled.
# Headless like the other tools: Xvfb + llvmpipe (software GL), so it checks the production jar, class remapping and the
# launch path - not real-GPU behaviour.
#   tools/prodclient.sh [display-number]            (default 97)
# Env: SQUID_WORLD (default SquidProd), SQUID_RUNDIR (default run-prodclient), SQUID_LOADER (default 0.19.5),
#      SQUID_USER (default Contestant), SQUID_FRESH=1 (re-create the world from the never-entered seed world),
#      SQUID_SEED_WORLD (a world directory to copy, default run-spclient/saves/SquidSP made by tools/spclient.sh; the mod's own
#      data in it is stripped so the first /squid enter has to generate the complex), SQUID_HEAP (default 3G).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
D="${1:-97}"
LOADER="${SQUID_LOADER:-0.19.5}"
MC="$(sed -n 's/^minecraft_version=//p' gradle.properties)"
RUNDIR="${SQUID_RUNDIR:-run-prodclient}"
WORLD="${SQUID_WORLD:-SquidProd}"
MODS_SRC="${SQUID_MODS_DIR:-$ROOT/dist/mods}"
mkdir -p "$RUNDIR/mods" "$RUNDIR/saves" "$RUNDIR/fabric"
[ -d "$MODS_SRC" ] || { echo "$MODS_SRC is missing: run tools/package.sh first"; exit 1; }

# --- Fabric Loader, its Mixin and the intermediary mappings (what the launcher profile lists), from maven.fabricmc.net
fetch() { # file, url
  [ -s "$RUNDIR/fabric/$1" ] || curl -fsSL -o "$RUNDIR/fabric/$1" "$2"
}
PROFILE="$RUNDIR/fabric/profile-$MC-$LOADER.json"
[ -s "$PROFILE" ] || curl -fsSL -o "$PROFILE" "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/profile/json"
FABRIC_JARS=()
while IFS= read -r coord; do
  IFS=: read -r group artifact version <<< "$coord"
  file="$artifact-$version.jar"
  fetch "$file" "https://maven.fabricmc.net/${group//.//}/$artifact/$version/$file"
  FABRIC_JARS+=("$ROOT/$RUNDIR/fabric/$file")
done < <(python3 -c "import json,sys; [print(l['name']) for l in json.load(open(sys.argv[1]))['libraries']]" "$PROFILE")

# --- vanilla libraries (LWJGL and natives included), asked from Gradle once
LIBS="$RUNDIR/fabric/vanilla-libs.txt"
if [ ! -s "$LIBS" ]; then
  INIT="$RUNDIR/fabric/print-libs.init.gradle"
  cat > "$INIT" <<'EOF'
allprojects {
  afterEvaluate { p ->
    if (p.plugins.hasPlugin('fabric-loom')) {
      p.tasks.register('printVanillaLibs') {
        doLast {
          ['minecraftClientRuntimeLibraries', 'minecraftNatives'].each { n ->
            def c = p.configurations.findByName(n)
            if (c != null && c.canBeResolved) { c.resolve().each { println 'LIB ' + it.absolutePath } }
          }
        }
      }
    }
  }
}
EOF
  ./gradlew --offline -q -I "$INIT" printVanillaLibs | sed -n 's/^LIB //p' | sort -u > "$LIBS"
fi
CLIENT_JAR="$HOME/.gradle/caches/fabric-loom/$MC/minecraft-client.jar"
ASSETS="$HOME/.gradle/caches/fabric-loom/assets"
[ -f "$CLIENT_JAR" ] && [ -d "$ASSETS/indexes" ] || { echo "run ./gradlew build once so Loom has downloaded the game ($CLIENT_JAR)"; exit 1; }
unzip -l "$CLIENT_JAR" | grep -q "net/minecraft/client/Minecraft.class" && { echo "$CLIENT_JAR is not the original obfuscated client"; exit 1; }
ASSET_INDEX="$(basename "$(ls "$ASSETS"/indexes/*.json | head -1)" .json)"

# --- mods: only the bundle
find "$RUNDIR/mods" -maxdepth 1 -name '*.jar' -delete
cp "$MODS_SRC"/*.jar "$RUNDIR/mods/"
echo "mods:"; ls -1 "$RUNDIR/mods"

# --- world: a copy of the never-entered seed world
SAVE="$RUNDIR/saves/$WORLD"
if [ "${SQUID_FRESH:-0}" = "1" ]; then rm -rf "$SAVE"; fi
if [ ! -f "$SAVE/level.dat" ]; then
  SEED="${SQUID_SEED_WORLD:-$ROOT/run-spclient/saves/SquidSP}"
  [ -f "$SEED/level.dat" ] || { echo "no seed world at $SEED (run tools/spclient.sh once, or set SQUID_SEED_WORLD)"; exit 1; }
  cp -r "$SEED" "$SAVE"
  rm -f "$SAVE/session.lock" "$SAVE"/data/squidgame_*.dat
  rm -rf "$SAVE/dimensions/squidgame" "$SAVE/playerdata"
fi
[ -f "$RUNDIR/options.txt" ] || cp tools/testclient-options.txt "$RUNDIR/options.txt"

if ! pgrep -f "[X]vfb :$D" >/dev/null; then
  nohup Xvfb ":$D" -screen 0 1280x720x24 -ac +extension GLX +render -noreset >/dev/null 2>&1 &
  sleep 2
fi
export DISPLAY=":$D" LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460

CP="$(IFS=:; echo "${FABRIC_JARS[*]}"):$(paste -sd: "$LIBS"):$CLIENT_JAR"
cd "$RUNDIR"
exec java -Xmx"${SQUID_HEAP:-3G}" -Dsquid.prod.client=1 "-DFabricMcEmu= net.minecraft.client.main.Main " \
  -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --username "${SQUID_USER:-Contestant}" --version "$MC-fabric" --gameDir "$ROOT/$RUNDIR" --assetsDir "$ASSETS" \
  --assetIndex "$ASSET_INDEX" --accessToken 0 --userType legacy --width 1280 --height 720 \
  --quickPlaySingleplayer "$WORLD"
