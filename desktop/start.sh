#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v javac >/dev/null || ! command -v java >/dev/null; then
  echo 'RHI requires a JDK 17 or newer. Install one, then run desktop/start.sh again.' >&2
  exit 1
fi
mkdir -p build
javac -encoding UTF-8 -d build *.java
exec java -cp build Loop
