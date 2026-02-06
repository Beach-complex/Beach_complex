# Windows Git Hooks + gitleaks 설치/실행 트러블슈팅

**컴포넌트:** infra

**작성일:** 2026-01-20

## 문제 상황

### 증상
- `git commit` 실행 시 gitleaks 훅이 실패
- `installGitHooks` 실행 중 gitleaks 다운로드 실패
- Docker fallback 실행 중 오류 발생

### 발생 로그 (요약)

```
unable to load gitleaks config, err: open C:/Program Files/Git/path/.gitleaks.toml: no such file or directory
```

```
docker: Error response from daemon: invalid mode: \Program Files\Git\path;ro
```

```
Invoke-WebRequest : Not Found
```

---

## 근본 원인

### 원인 1: 훅 실행 위치가 레포 루트가 아님
- 훅 실행 시 작업 디렉터리가 `C:\Program Files\Git\path`로 잡힘
- `scripts/secret-scan.sh`가 상대 경로 `.gitleaks.toml`을 찾지 못함

### 원인 2: gitleaks 미설치로 Docker fallback 실행
- 로컬 `gitleaks`가 PATH에 없음
- Docker fallback 경로에 공백이 포함되어 `-v` 옵션 오류 발생

### 원인 3: Windows에서 `.sh` 스크립트가 실행됨
- `installGitHooks`가 `bash.exe`로 `install-githooks.sh`를 실행
- mac/linux용 tarball을 받으려다 gzip 오류 발생

### 원인 4: gitleaks URL 생성 오류
- PowerShell 스크립트에서 버전 문자열이 `v8.18.4`로 저장되어
  `.../download/vv8.18.4/...` 형태가 됨

---

## 해결 방법

### 원인-해결 매핑

| 근본 원인 | 해결책 |
| --- | --- |
| 원인 1: 훅 실행 위치가 레포 루트가 아님 | 1) gitleaks config 경로 고정 |
| 원인 3: Windows에서 `.sh` 스크립트가 실행됨 | 2) Windows는 `.ps1`로 훅 설치 |
| 원인 2: gitleaks 미설치로 Docker fallback 실행 | 3) gitleaks 자동 설치 추가 |
| 원인 4: gitleaks URL 생성 오류 | 4) PowerShell 설치 스크립트 버전 문자열 수정 |

### 1) gitleaks config 경로 고정

`scripts/secret-scan.sh`에서 레포 루트 기준으로 경로를 고정한다.

```sh
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
```

```sh
gitleaks detect ... --config "$REPO_ROOT/.gitleaks.toml"
```

```sh
-v "$REPO_ROOT":/path:ro \
```

### 2) Windows는 `.ps1`로 훅 설치

`build.gradle`의 `installGitHooks` 태스크를 Windows에서 PowerShell로 실행하도록 변경한다.

```gradle
if (osName.contains('windows')) {
  commandLine "powershell.exe", "-ExecutionPolicy", "Bypass", "-File", "./scripts/install-githooks.ps1"
}
```

### 3) gitleaks 자동 설치 추가

`scripts/install-githooks.ps1`에 다운로드/설치 로직을 추가한다.

```powershell
$version = "8.18.4"
$zipName = "gitleaks_${version}_windows_x64.zip"
$url = "https://github.com/gitleaks/gitleaks/releases/download/v$version/$zipName"
```

### 4) PowerShell 설치 스크립트 버전 문자열 수정

`scripts/install-githooks.ps1`에서 버전 문자열의 `v` 접두어를 제거한다.

수정 전:
```powershell
$version = "v8.18.4"
```

수정 후:
```powershell
$version = "8.18.4"
```

---

## 실제 시도 명령어/로그 (전체 타임라인)

### 1) 훅 설치 실행

```
.\gradlew.bat installGitHooks
```

결과:
```
Git hooks installed (core.hooksPath=.githooks).
```

### 2) 커밋 시도 → gitleaks config 경로 오류

```
git commit -m "test: PB-71 예약 생성 통합테스트 추가 및 인증 실패 응답 보강"
```

결과:
```
FTL unable to load gitleaks config, err: open C:/Program Files/Git/path/.gitleaks.toml: no such file or directory
```

### 2-1) Docker fallback 실행 오류 (gitleaks 미설치 시)

```
docker: Error response from daemon: invalid mode: \Program Files\Git\path;ro
```

### 3) PATH 확인/환경 점검

```
git log --oneline --decorate -n 3
```

### 4) gitleaks 설치 시도 (winget 없음)

```
winget install -e --id Gitleaks.Gitleaks
```

결과:
```
winget : 'winget' 용어가 cmdlet, 함수, 스크립트 파일 또는 실행할 수 있는 프로그램 이름으로 인식되지 않습니다.
```

### 5) gitleaks 다운로드 URL 확인 (404)

```
$version = "v8.18.4"
$zipName = "gitleaks_${version.Substring(1)}_windows_x64.zip"
$url = "https://github.com/gitleaks/gitleaks/releases/download/$version/$zipName"
$url
```

출력:
```
https://github.com/gitleaks/gitleaks/releases/download/v8.18.4/gitleaks__windows_x64.zip
```

```
curl.exe -I $url
```

결과:
```
HTTP/1.1 404 Not Found
```

### 6) 훅 설치 태스크 Windows 실행 방식 오류

```
.\gradlew.bat installGitHooks
```

결과:
```
./scripts/install-githooks.ps1: line 1: =: command not found
./scripts/install-githooks.ps1: line 10: syntax error near unexpected token `Test-Path'
```

원인:
```
installGitHooks가 bash.exe로 .ps1을 실행
```

### 7) installGitHooks를 PowerShell 실행으로 변경

```
.\gradlew.bat installGitHooks
```

결과:
```
Invoke-WebRequest : Not Found
```

### 7-1) Docker Desktop 미실행 상태에서 fallback 실패

```
docker: error during connect: Head "http://%2F%2F.%2Fpipe%2FdockerDesktopLinuxEngine/_ping": open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

### 8) PowerShell 설치 스크립트 버전 문자열 수정

수정 전:
```
$version = "v8.18.4"
```

수정 후:
```
$version = "8.18.4"
```

### 9) 재설치 성공

```
.\gradlew.bat installGitHooks
```

결과:
```
BUILD SUCCESSFUL
```

### 10) gitleaks PATH 반영 확인 (새 터미널 필요)

```
gitleaks version
```

결과:
```
gitleaks : 'gitleaks' 용어가 cmdlet... (PATH 미반영)
```

해결:
- 새 터미널 열기

### 11) 최종 커밋 성공

```
git commit -m "test: PB-71 예약 생성 통합테스트 추가 및 인증 실패 응답 보강"
```

결과:
```
INF scan completed in 2.07ms
INF no leaks found
```

---

## 추가 상세 로그

### Docker fallback 경로 오류

```
docker: Error response from daemon: invalid mode: \Program Files\Git\path;ro
```

### Docker 데몬 미실행 오류

```
docker: error during connect: Head "http://%2F%2F.%2Fpipe%2FdockerDesktopLinuxEngine/_ping": open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified.
```

### gitleaks config 경로 오류

```
FTL unable to load gitleaks config, err: open C:/Program Files/Git/path/.gitleaks.toml: no such file or directory
```

---

## 확인

```
.\gradlew.bat installGitHooks
gitleaks version
git commit -m "..."
```

정상 로그:
```
INF scan completed
INF no leaks found
```

---

## 학습 포인트

### 1) 훅 실행 디렉터리가 레포 루트가 아닐 수 있음
- 상대 경로 대신 레포 루트 기준 경로를 사용해야 함.

### 2) Windows에서는 `.ps1`로 설치 스크립트를 실행
- Git Bash로 `.ps1` 실행 시 구문 오류 발생.

### 3) 자동 설치는 버전 문자열 규칙이 중요
- `v` 접두어 위치가 잘못되면 404로 실패.

해결 날짜 : 2026/1/20
