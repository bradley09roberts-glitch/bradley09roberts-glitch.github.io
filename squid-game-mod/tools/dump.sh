#!/usr/bin/env bash
# Fast, Gradle-free arena dump for builder development.
#   tools/dump.sh <red_light|dalgona|tug_of_war|marbles|glass_bridge|final|hub|all> [seed]
#   Hub parts are developed in parallel: SQUID_HUB_PARTS=DormitoryPart (comma list of DormitoryPart, CorridorPart,
#   StairwayPart, ControlRoomPart) builds and validates only those parts; their helper classes live in the sub-packages
#   build/arena/hub/{dorm,corridor,stairs,control}. Use SQUID_OUT=tools/out/<yourdir> to keep your dump separate.
# Compiles only the pure-Java packages (build, core) plus the requested arena's own sources and the shared
# prefabs, runs the builder into a virtual buffer, validates the required markers and writes tools/out/<id>.sqbuf.
# Render it with: python3 tools/preview.py tools/out/<id>.sqbuf --views top,iso,front [--clip ...]
set -euo pipefail
cd "$(dirname "$0")/.."
ARENA="${1:-all}"
SEED="${2:-1}"
FASTUTIL="$(find "$HOME/.gradle" -name 'fastutil-8.5.12.jar' 2>/dev/null | head -1)"
[ -n "$FASTUTIL" ] || { echo "fastutil jar not found (run ./gradlew build once)"; exit 2; }
OUT="${SQUID_OUT:-tools/out}"
CLS="$OUT/.classes/$ARENA"
mkdir -p "$CLS"
find "$CLS" -name '*.class' -delete
SRC=src/main/java/com/squidgame
FILES="$(find $SRC/build $SRC/core -maxdepth 3 -name '*.java' -not -path "$SRC/build/arena/*")"
FILES="$FILES $(find $SRC/build/arena/prefab -name '*.java' 2>/dev/null || true)"
add_arena() { # builder class file + helper package dir
  [ -f "$SRC/build/arena/$1.java" ] && FILES="$FILES $SRC/build/arena/$1.java"
  [ -d "$SRC/build/arena/$2" ] && FILES="$FILES $(find $SRC/build/arena/$2 -name '*.java')"
  return 0
}
case "$ARENA" in
  red_light) add_arena RedLightBuilder redlight ;;
  dalgona) add_arena DalgonaBuilder dalgona ;;
  tug_of_war) add_arena TugOfWarBuilder tug ;;
  marbles) add_arena MarblesBuilder marbles ;;
  glass_bridge) add_arena GlassBridgeBuilder bridge ;;
  final) add_arena FinalBuilder finale ;;
  hub)
    add_arena HubBuilder ""
    HUBDIR="$SRC/build/arena/hub"
    FILES="$FILES $HUBDIR/HubPart.java"
    for p in $(echo "${SQUID_HUB_PARTS:-DormitoryPart,CorridorPart,StairwayPart,ControlRoomPart}" | tr ',' ' '); do
      [ -f "$HUBDIR/$p.java" ] && FILES="$FILES $HUBDIR/$p.java"
      case "$p" in DormitoryPart) sub=dorm;; CorridorPart) sub=corridor;; StairwayPart) sub=stairs;; ControlRoomPart) sub=control;; *) sub=;; esac
      [ -n "$sub" ] && [ -d "$HUBDIR/$sub" ] && FILES="$FILES $(find "$HUBDIR/$sub" -name '*.java')"
    done
    ;;
  all) for pair in "RedLightBuilder redlight" "DalgonaBuilder dalgona" "TugOfWarBuilder tug" "MarblesBuilder marbles" "GlassBridgeBuilder bridge" "FinalBuilder finale" "HubBuilder hub"; do add_arena $pair; done ;;
  *) echo "unknown arena $ARENA"; exit 2 ;;
esac
javac -nowarn -proc:none -d "$CLS" -cp "$FASTUTIL" $FILES
JOPTS=""
[ -n "${SQUID_HUB_PARTS:-}" ] && JOPTS="-Dsquid.hub.parts=$SQUID_HUB_PARTS"
java $JOPTS -cp "$CLS:$FASTUTIL" com.squidgame.build.tools.DumpArena "$ARENA" "$OUT" "$SEED"
