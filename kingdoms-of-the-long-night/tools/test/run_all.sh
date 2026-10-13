#!/bin/bash
# Runs every automated test in sequence: JUnit, GameTests, dedicated-server scenario. Usage: tools/test/run_all.sh [name]
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
MOD="$HERE/../../mod"
NAME="${1:-all}"
OUT="$HERE/results/$NAME-summary.txt"
mkdir -p "$HERE/results"
: > "$OUT"
cd "$MOD"
./gradlew test --console=plain > "$HERE/results/$NAME-junit.log" 2>&1 && echo "JUNIT PASS" >> "$OUT" || echo "JUNIT FAIL" >> "$OUT"
./gradlew runGameTestServer --console=plain > "$HERE/results/$NAME-gametest.log" 2>&1 && echo "GAMETEST PASS" >> "$OUT" || echo "GAMETEST FAIL" >> "$OUT"
grep -E "required tests|tests passed" "$HERE/results/$NAME-gametest.log" >> "$OUT"
python3 -I "$HERE/server_scenario.py" --days 4 --name "$NAME-scenario" > /dev/null 2>&1 && echo "SCENARIO PASS" >> "$OUT" || echo "SCENARIO FAIL" >> "$OUT"
grep -E "SUMMARY|failed:" "$HERE/results/$NAME-scenario.txt" >> "$OUT"
cat "$OUT"
