#!/bin/sh
set -e

if ! command -v gitleaks >/dev/null 2>&1; then
  echo "gitleaks is not installed. Install it to enable secret scanning."
  echo "See docs/hooks.md for setup."
  exit 1
fi

# Scan staged changes for secrets (pipe git diff)
git diff --cached --no-color --text | gitleaks detect --pipe --redact --no-banner --config .gitleaks.toml
