# Git Hooks

이 저장소는 `.githooks`에 있는 공용 git hook을 사용합니다.

## 설치

한 번만 실행:
- `./gradlew installGitHooks`

- macOS/Linux:
  ```bash
  ./scripts/install-githooks.sh
  ```

- Windows (PowerShell):
  ```powershell
  .\scripts/install-githooks.ps1
  ```

## 동작

- pre-commit: `./gradlew spotlessApply` 실행 후 스테이징 변경을 비밀정보 스캔.
- pre-push: 푸시 전에 스테이징 변경을 비밀정보 스캔 (방어적 체크).
- prepare-commit-msg: 브랜치 이름에서 `PB-123` 키를 찾아 메시지에 삽입.
- commit-msg: `PB-<number>` 키가 없는 커밋을 차단.

참고: GUI Git 클라이언트는 PATH를 상속하지 못할 수 있습니다. pre-commit 훅은
macOS에서 `gitleaks`를 찾기 위해 `/usr/local/bin`, `/opt/homebrew/bin`을 PATH에 추가합니다.

## 비밀정보 스캔 (gitleaks)

이 저장소는 훅에서 `gitleaks`를 사용합니다.
커스텀 규칙은 `.gitleaks.toml`에 있습니다.
훅은 로컬 `gitleaks` 설치를 우선 사용하며, 가능하면 Docker를 대체 수단으로 사용합니다.

설치 (택1):

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
  - 릴리스 바이너리를 내려받아 `PATH`에 추가합니다.

Docker 대체 실행 (로컬 미설치 시):

```bash
docker run --rm -i -v "$PWD":/path:ro zricethezav/gitleaks:v8.18.4 \
  detect --pipe --redact --no-banner --config /path/.gitleaks.toml
```
