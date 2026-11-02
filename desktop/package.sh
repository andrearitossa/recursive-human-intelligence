#!/usr/bin/env bash
# Builds a self-contained RHI with its own Java runtime. Requires JDK 17+ with jpackage.
# Usage: package.sh <output-archive-name-without-extension>
set -euo pipefail
cd "$(dirname "$0")"
NAME="${1:-RHI}"; WORK=$(pwd)/dist; rm -rf "$WORK"; mkdir -p "$WORK/classes" "$WORK/in" "$WORK/out"
javac -encoding UTF-8 -d "$WORK/classes" *.java
jar --create --file "$WORK/in/rhi.jar" --main-class Loop -C "$WORK/classes" . -C . fonts
jpackage --type app-image --name RHI --input "$WORK/in" --main-jar rhi.jar --main-class Loop --dest "$WORK/out" \
  --add-modules java.base,java.desktop,java.net.http,jdk.charsets,jdk.crypto.ec \
  --jlink-options "--strip-debug --no-header-files --no-man-pages --compress=zip-9"
cd "$WORK/out"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) powershell -NoProfile -Command "Compress-Archive -Path RHI -DestinationPath ../$NAME.zip" ;;
  *) tar -czf "../$NAME.tar.gz" RHI* ;;
esac
ls -la "$WORK"
