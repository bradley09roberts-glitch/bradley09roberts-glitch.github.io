#!/usr/bin/env bash
# Builds the plugin and zips it so BepInEx/plugins/ can be unpacked straight into the game folder.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION="0.1.0"
DOTNET="${DOTNET:-dotnet}"

"$DOTNET" build src/Fm26Moneyball.Plugin -c Release
OUT="src/Fm26Moneyball.Plugin/bin/Release/net472"
STAGE="$(mktemp -d)"
DEST="$STAGE/BepInEx/plugins/Fm26Moneyball"
mkdir -p "$DEST" dist

# Only our two DLLs: BepInEx already provides Harmony and the Unity references.
cp "$OUT/Fm26Moneyball.Plugin.dll" "$OUT/Fm26Moneyball.Core.dll" "$DEST/"

ZIP="$PWD/dist/Fm26Moneyball-$VERSION.zip"
rm -f "$ZIP"
(cd "$STAGE" && zip -qr "$ZIP" BepInEx)
rm -rf "$STAGE"
echo "Wrote $ZIP"
unzip -l "$ZIP"
