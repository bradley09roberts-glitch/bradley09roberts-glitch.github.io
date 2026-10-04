#!/usr/bin/env bash
# Starts a headless (Xvfb + llvmpipe) Minecraft test client that auto-joins localhost:25599.
# Usage: tools/xvfb-client.sh [display-number]   (default 98). Env: SQUID_PORT (server port, default 25599), SQUID_USER (name).
# Screenshots: DISPLAY=:98 import -window root out.png
set -euo pipefail
D="${1:-98}"
cd "$(dirname "$0")/.."
if ! pgrep -f "[X]vfb :$D" >/dev/null; then
  nohup Xvfb ":$D" -screen 0 1280x720x24 -ac +extension GLX +render -noreset >/dev/null 2>&1 &
  sleep 2
fi
mkdir -p run-testclient
[ -f run-testclient/options.txt ] || cp tools/testclient-options.txt run-testclient/options.txt
export DISPLAY=":$D" LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460
exec ./gradlew runTestClient --no-daemon --offline ${GRADLE_ARGS:--PskipArenas}
