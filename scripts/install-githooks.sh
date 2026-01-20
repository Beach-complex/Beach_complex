#!/bin/sh
set -e

git config core.hooksPath .githooks

git update-index --chmod=+x .githooks/pre-commit .githooks/prepare-commit-msg .githooks/commit-msg || true
chmod +x .githooks/pre-commit .githooks/prepare-commit-msg .githooks/commit-msg scripts/secret-scan.sh || true

echo "Git hooks installed (core.hooksPath=.githooks)."
# ---- gitleaks auto-install (mac/linux, no package manager) ----
GITLEAKS_DIR="$HOME/.local/bin"
GITLEAKS_BIN="$GITLEAKS_DIR/gitleaks"

if [ ! -x "$GITLEAKS_BIN" ]; then
  mkdir -p "$GITLEAKS_DIR"

  VERSION="8.18.4"
  OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
  ARCH="$(uname -m)"

  if [ "$ARCH" = "x86_64" ]; then
    ARCH="x64"
  elif [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
    ARCH="arm64"
  fi

  TARBALL="gitleaks_${VERSION}_${OS}_${ARCH}.tar.gz"
  URL="https://github.com/gitleaks/gitleaks/releases/download/v${VERSION}/${TARBALL}"

  curl -L "$URL" -o "/tmp/$TARBALL"
  tar -xzf "/tmp/$TARBALL" -C "$GITLEAKS_DIR"
  rm -f "/tmp/$TARBALL"
fi

# PATH 보강 (현재 쉘에서 사용)
export PATH="$GITLEAKS_DIR:$PATH"
# ------------------------------------------------------------
