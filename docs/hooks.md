# Git Hooks

This repository uses shared git hooks from `.githooks`.

## Install

- macOS/Linux:
  ```bash
  ./scripts/install-githooks.sh
  ```

- Windows (PowerShell):
  ```powershell
  .\scripts\install-githooks.ps1
  ```

## Behavior

- pre-commit: runs `./gradlew spotlessApply` and scans staged changes for secrets.
- pre-push: scans staged changes for secrets before push (defense-in-depth).
- prepare-commit-msg: inserts `PB-123` from the branch name when missing.
- commit-msg: blocks commits without a `PB-<number>` key.

## Secret Scanning (gitleaks)

This repo uses `gitleaks` for secret detection in hooks.
Custom rules live in `.gitleaks.toml`.

Install (choose one):

- Windows (winget):
  ```powershell
  winget install -e --id Gitleaks.Gitleaks
  ```
- Windows (choco):
  ```powershell
  choco install gitleaks
  ```
- Windows (scoop):
  ```powershell
  scoop install gitleaks
  ```
- macOS (Homebrew):
  ```bash
  brew install gitleaks
  ```
- Linux (apt via GitHub release):
  - Download the release binary and add to `PATH`.
