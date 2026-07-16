#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: %s <server-jar>\n' "$0" >&2
  exit 1
fi

JAR=$(realpath "$1")
if [[ ! -f "$JAR" ]]; then
  printf 'Server JAR not found: %s\n' "$JAR" >&2
  exit 1
fi

WORK=$(mktemp -d)
PORT=${PDF_BATCH_SMOKE_PORT:-18081}
PID=
cleanup() {
  if [[ -n "$PID" ]] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
  rm -rf "$WORK"
}
trap cleanup EXIT

(
  cd "$WORK"
  PORT="$PORT" \
  SPRING_PROFILES_ACTIVE=local \
  PDF_BATCH_DATABASE_URL="jdbc:h2:file:$WORK/database;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE" \
  PDF_BATCH_STORAGE_ROOT="$WORK/jobs" \
  java -jar "$JAR" >"$WORK/server.log" 2>&1
) &
PID=$!

for _ in $(seq 1 30); do
  if curl --fail --silent "http://127.0.0.1:$PORT/actuator/health" | grep -q '"status":"UP"'; then
    curl --fail --silent "http://127.0.0.1:$PORT/" | grep -q 'PDF Batch Studio'
    printf 'Server release smoke test passed on port %s\n' "$PORT"
    exit 0
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

tail -n 100 "$WORK/server.log" >&2 || true
printf 'Server release did not become healthy.\n' >&2
exit 1
