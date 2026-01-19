#!/bin/sh
set -e
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if command -v gitleaks >/dev/null 2>&1; then
  # Scan staged changes for secrets (pipe git diff) using local gitleaks.
  git diff --cached --no-color --text | gitleaks detect --pipe --redact --no-banner --config "$REPO_ROOT/.gitleaks.toml"
  exit 0
fi

if command -v docker >/dev/null 2>&1; then
  # Fallback: use gitleaks via Docker when local install is missing.
  git diff --cached --no-color --text | \
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
