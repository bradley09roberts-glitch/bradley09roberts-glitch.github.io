#!/usr/bin/env bash
# Runs ./gradlew with a machine-wide lock so concurrent builds (several worktrees / agents) queue instead of fighting for
# CPU and memory. Usage: tools/gradle.sh <gradle args...>   e.g. tools/gradle.sh build -x test -PskipArenas
cd "$(dirname "$0")/.."
exec flock /tmp/squid-gradle.lock ./gradlew --offline "$@"
