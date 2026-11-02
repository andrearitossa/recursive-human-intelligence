#!/usr/bin/env bash
# Compiles Loop and one driver with the Windows JDK; runs it against an isolated, throwaway ~/.loop.
set -euo pipefail
cd "$(dirname "$0")/.."
JDK="${JDK:-/mnt/c/Program Files/Eclipse Adoptium/jdk-25.0.5.8-hotspot/bin}"
"$JDK/javac.exe" -encoding UTF-8 -d build *.java "ui-test/$1.java"
"$JDK/java.exe" -Djava.awt.headless=true -cp build Checks
TEMP_WIN=$(cmd.exe /c "echo %TEMP%" 2>/dev/null | tr -d '\r' | tail -1)
"$JDK/java.exe" -Duser.language=en -Duser.home="$TEMP_WIN\\loop-test-home" -cp build "$1" "$(wslpath -w ui-test)" "${@:2}"
