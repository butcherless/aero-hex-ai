#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$PROJECT_ROOT/target/openapi-verification"
SPEC_OUT="$OUT_DIR/generated-openapi.yaml"
REDOCLY_REPORT_OUT="$OUT_DIR/redocly-report.json"
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

# ── 2. Generate spec (OpenApiGenerator writes YAML to stdout) ────────────────
echo "==> Generating OpenAPI spec..."
java -jar "$JAR" > "$SPEC_OUT"
echo "==> Spec written to: $SPEC_OUT"

# ── 3. Validate against official OAS 3.1 schema (Redocly) ────────────────────
echo "==> Validating against official OAS 3.1 schema (Redocly)..."
REDOCLY_EXIT=0
npx -y @redocly/cli@latest lint "$SPEC_OUT" \
  --config "$PROJECT_ROOT/redocly.yaml" \
  --format json \
  2>/dev/null > "$REDOCLY_REPORT_OUT" || REDOCLY_EXIT=$?

if [ ! -f "$REDOCLY_REPORT_OUT" ]; then
  echo "ERROR: Redocly did not produce a report — check npm/network" >&2
  exit 1
fi

REDOCLY_ERRORS=$(jq '.totals.errors' "$REDOCLY_REPORT_OUT")
REDOCLY_WARNINGS=$(jq '.totals.warnings' "$REDOCLY_REPORT_OUT")
echo "  Errors   : $REDOCLY_ERRORS"
echo "  Warnings : $REDOCLY_WARNINGS"

if [ "$REDOCLY_ERRORS" -gt 0 ]; then
  echo ""
  echo "==> Redocly Errors:"
  jq -r '.problems[] | select(.severity == "error") | "  [\(.ruleId)] \(.message)  @  \(.location[0].pointer // "/")"' "$REDOCLY_REPORT_OUT"
  echo ""
  echo "FAILED: $REDOCLY_ERRORS OAS schema error(s) must be fixed."
  exit 1
fi

# ── 4. Lint best practices (Spectral) ────────────────────────────────────────
echo "==> Running Spectral best-practice checks..."
SPECTRAL_EXIT=0
npx -y @stoplight/spectral-cli lint "$SPEC_OUT" \
  --ruleset "$RULESET" \
  --format json \
  --output "$REPORT_OUT" || SPECTRAL_EXIT=$?

# ── 5. Summarize ─────────────────────────────────────────────────────────────
echo ""
echo "==> Results (Spectral report: $REPORT_OUT)"

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
  echo "==> Spectral Errors:"
  jq -r '.[] | select(.severity == 0) | "  [\(.code)] \(.message)  @  /\(.path | join("/"))"' "$REPORT_OUT"
  echo ""
  echo "FAILED: $ERRORS Spectral error(s) must be fixed."
  exit 1
fi

echo ""
echo "PASSED: no errors found."
