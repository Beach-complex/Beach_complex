#!/bin/sh
set -e

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
DIFF_FILE=$(mktemp)
ADDED_LINES_FILE=$(mktemp)

cleanup() {
  rm -f "$DIFF_FILE" "$ADDED_LINES_FILE"
}
trap cleanup EXIT INT TERM

# Scan only added lines from staged diff.
# This prevents false positives from deleted lines while still scanning newly introduced content.
git -C "$REPO_ROOT" diff --cached --no-color --text --unified=0 > "$DIFF_FILE"
awk '
  /^\+\+\+/ {next}
  /^\+/ {
    sub(/^\+/, "");
    print;
  }
' "$DIFF_FILE" > "$ADDED_LINES_FILE"

if [ ! -s "$ADDED_LINES_FILE" ]; then
  echo "No added lines to scan; skipping secret scan."
  exit 0
fi

if command -v gitleaks >/dev/null 2>&1; then
  # Scan added staged lines for secrets using local gitleaks.
  cat "$ADDED_LINES_FILE" | gitleaks detect --pipe --redact --no-banner --config "$REPO_ROOT/.gitleaks.toml"
  exit 0
fi

if command -v docker >/dev/null 2>&1; then
  # Fallback: use gitleaks via Docker when local install is missing.
  cat "$ADDED_LINES_FILE" | \
    docker run --rm -i \
      -v "$REPO_ROOT":/path:ro \
      zricethezav/gitleaks:v8.18.4 \
      detect --pipe --redact --no-banner --config /path/.gitleaks.toml
  exit 0
fi

echo "gitleaks is not installed and Docker is not available."
echo "Install gitleaks or Docker to enable secret scanning."
echo "See docs/hooks.md for setup."
exit 1
