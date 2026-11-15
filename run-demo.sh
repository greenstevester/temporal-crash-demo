#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v java >/dev/null || { echo 'Install Java 21 or newer before running this demo.' >&2; exit 1; }
java_major=$(java -version 2>&1 | awk -F'"' '/version/ { split($2, v, "."); print v[1]; exit }')
[ "${java_major:-0}" -ge 21 ] 2>/dev/null \
  || { echo "Java 21 or newer is required; found ${java_major:-unknown}." >&2; exit 1; }
command -v mvn >/dev/null || { echo 'Install Maven 3.9 or newer before running this demo.' >&2; exit 1; }
command -v temporal >/dev/null || { echo 'Install Temporal CLI 1.10.0 before running this demo.' >&2; exit 1; }
exec mvn --batch-mode verify "$@"
