#!/bin/sh
set -e

git config core.hooksPath .githooks

git update-index --chmod=+x .githooks/pre-commit .githooks/prepare-commit-msg .githooks/commit-msg || true
chmod +x .githooks/pre-commit .githooks/prepare-commit-msg .githooks/commit-msg scripts/secret-scan.sh || true

echo "Git hooks installed (core.hooksPath=.githooks)."
