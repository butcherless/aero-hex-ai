#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="$SCRIPT_DIR/run"
PID_FILE="$RUN_DIR/app.pid"
MAIN_CLASS="dev.cmartin.aerohex.bootstrap.Main"

if [[ ! -f "$PID_FILE" ]]; then
  echo "No PID file found at $PID_FILE — is the app running? (started outside scripts/start.sh?)" >&2
  exit 1
fi

pid="$(cat "$PID_FILE")"

if ! ps -p "$pid" -o command= 2>/dev/null | grep -qF "$MAIN_CLASS"; then
  echo "No matching process found for PID $pid (stale or reused PID). Removing stale PID file." >&2
  rm -f "$PID_FILE"
  exit 1
fi

echo "Stopping app (PID $pid) ..."
kill -TERM "$pid"

for _ in $(seq 1 20); do
  if ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "App stopped."
    exit 0
  fi
  sleep 0.5
done

echo "App did not stop within 10s, sending SIGKILL ..." >&2
kill -KILL "$pid" 2>/dev/null || true
rm -f "$PID_FILE"
echo "App killed."
