#!/usr/bin/env bash

# Run the structural quality gate. The Gradle tasks always write XML reports;
# quality_report.py compares them with config/quality/baseline.json and fails
# only when a file/rule gets worse. Use --update-baseline after an intentional
# refactor that reduces or reshapes the recorded debt.

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="${QUALITY_LOG_DIR:-/tmp/opencode}"
LOG="$LOG_DIR/quality-$(date +%Y%m%d-%H%M%S).log"
MODE=check

if [[ "${1:-}" == "--update-baseline" ]]; then
    MODE=update
    shift
fi
if (($# > 0)); then
    printf 'Usage: %s [--update-baseline]\n' "$0" >&2
    exit 2
fi

mkdir -p "$LOG_DIR"
cd "$ROOT"

printf 'Running Checkstyle and PMD...\n'
if ! ./gradlew checkstyleMain pmdMain --console=plain >"$LOG" 2>&1; then
    printf 'Gradle quality tasks failed. Full log: %s\n\n' "$LOG" >&2
    tail -n 40 "$LOG" >&2
    exit 1
fi

args=(
    --checkstyle "$ROOT/build/reports/checkstyle/main.xml"
    --pmd "$ROOT/build/reports/pmd/main.xml"
    --baseline "$ROOT/config/quality/baseline.json"
    --root "$ROOT"
)
if [[ "$MODE" == update ]]; then
    args+=(--update-baseline)
fi

python3 "$ROOT/scripts/quality_report.py" "${args[@]}"
