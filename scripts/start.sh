#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_DIR="$SCRIPT_DIR/run"
PID_FILE="$RUN_DIR/app.pid"
LOG_FILE="$RUN_DIR/app.log"
MAIN_CLASS="dev.cmartin.aerohex.bootstrap.Main"

is_app_process() {
  ps -p "$1" -o command= 2>/dev/null | grep -qF "$MAIN_CLASS"
}

mkdir -p "$RUN_DIR"

if [[ -f "$PID_FILE" ]]; then
  existing_pid="$(cat "$PID_FILE")"
  if is_app_process "$existing_pid"; then
    echo "App already running (PID $existing_pid). Use scripts/stop.sh first." >&2
    exit 1
  fi
  rm -f "$PID_FILE"
fi

cd "$ROOT_DIR"

JAR="$(find bootstrap/target -name 'bootstrap-assembly-*.jar' 2>/dev/null | sort | tail -1)"

if [[ -z "$JAR" || "${1:-}" == "--build" ]]; then
  echo "Building bootstrap assembly jar..."
  sbt ";clean;bootstrap/assembly"
  JAR="$(find bootstrap/target -name 'bootstrap-assembly-*.jar' | sort | tail -1)"
fi

if [[ -z "$JAR" ]]; then
  echo "Could not find or build bootstrap assembly jar." >&2
  exit 1
fi

echo "Starting app from $JAR ..."
nohup java -cp "$JAR" "$MAIN_CLASS" > "$LOG_FILE" 2>&1 &
app_pid=$!
echo "$app_pid" > "$PID_FILE"

sleep 2
if ! kill -0 "$app_pid" 2>/dev/null; then
  echo "App failed to start (PID $app_pid exited immediately). Last log lines:" >&2
  tail -n 20 "$LOG_FILE" >&2
  rm -f "$PID_FILE"
  exit 1
fi

echo "App started (PID $app_pid). Logs: $LOG_FILE"
