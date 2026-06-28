#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$PROJECT_ROOT/target/openapi-verification"
SPEC_OUT="$OUT_DIR/generated-openapi.yaml"
REPORT_OUT="$OUT_DIR/spectral-report.json"
RULESET="$PROJECT_ROOT/.spectral.yaml"

cd "$PROJECT_ROOT"
mkdir -p "$OUT_DIR"

# ── 1. Build ─────────────────────────────────────────────────────────────────
echo "==> Building fat jar (bootstrap/assembly)..."
sbt "bootstrap/assembly"

JAR=$(find target/out -name "bootstrap-assembly-*.jar" 2>/dev/null | sort | tail -1)
if [ -z "$JAR" ]; then
  echo "ERROR: assembly jar not found under target/out" >&2
  exit 1
fi
echo "==> Jar: $JAR"

# ── 2. Generate spec (OpenApiGenerator writes YAML to stdout) ─────────────
echo "==> Generating OpenAPI spec..."
java -jar "$JAR" > "$SPEC_OUT"
echo "==> Spec written to: $SPEC_OUT"

# ── 3. Lint ───────────────────────────────────────────────────────────────
echo "==> Running Spectral..."
SPECTRAL_EXIT=0
npx -y @stoplight/spectral-cli lint "$SPEC_OUT" \
  --ruleset "$RULESET" \
  --format json \
  --output "$REPORT_OUT" || SPECTRAL_EXIT=$?

# ── 4. Summarize ─────────────────────────────────────────────────────────
echo ""
echo "==> Results (report: $REPORT_OUT)"

if ! command -v jq &>/dev/null; then
  echo "  (install jq for structured output)"
  [ "$SPECTRAL_EXIT" -eq 0 ] && echo "PASSED" || { echo "FAILED"; exit 1; }
fi

ERRORS=$(jq '[.[] | select(.severity == 0)] | length' "$REPORT_OUT")
WARNINGS=$(jq '[.[] | select(.severity == 1)] | length' "$REPORT_OUT")
TOTAL=$(jq 'length' "$REPORT_OUT")

echo "  Total issues : $TOTAL"
echo "  Errors       : $ERRORS"
echo "  Warnings     : $WARNINGS"

if [ "$ERRORS" -gt 0 ]; then
  echo ""
  echo "==> Errors:"
  jq -r '.[] | select(.severity == 0) | "  [\(.code)] \(.message)  @  /\(.path | join("/"))"' "$REPORT_OUT"
  echo ""
  echo "FAILED: $ERRORS error(s) must be fixed."
  exit 1
fi

echo ""
echo "PASSED: no errors found."
