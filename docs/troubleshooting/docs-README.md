# docs/troubleshooting/docs-README.md

이 폴더는 프로젝트 개발/운영 중에 생긴 이슈를 **재현 가능하게 기록**하고, **원인/해결/검증/재발 방지**까지 남겨 팀의 문제 해결 비용을 줄이기 위한 문서 모음입니다.

---

## 빠른 시작

1. 템플릿을 복사해서 새 문서를 만듭니다.
   - 템플릿: [`template.md`](./template.md)

2. 새 트러블슈팅 문서를 `docs/troubleshooting/` 아래에 추가합니다.
   - 파일명 규칙: 의미가 드러나는 slug(`kebab-case`) 권장
   - `troubleshooting-` 접두어/날짜 접두어는 쓰지 않습니다.
   - 예: `db-migration-failure.md`

3. 문서 메타 정보를 채웁니다.
   - 예: `**컴포넌트:** api, auth, db, infra`
   - 필수: `**작성일:** 2026-01-21`
   - 선택: `**해결 날짜:** 2026-01-21`
4. 인덱스를 자동 갱신합니다.
   - `powershell -ExecutionPolicy Bypass -File ./scripts/update-troubleshooting-index.ps1`

---

## 디렉토리 구조(권장)

- `docs/troubleshooting/docs-README.md` : 이 문서(인덱스/가이드)
- `docs/troubleshooting/template.md` : 트러블슈팅 템플릿(원본)
- `docs/troubleshooting/` : 실제 트러블슈팅 기록(케이스 모음)
  - `*.md` (의미 기반 slug 권장)

---

## 작성 규칙(최소 기준)

트러블슈팅 문서는 “기록”이 아니라 **재사용 가능한 해결 절차**여야 합니다. 아래 3가지는 최소 기준입니다.

- **재현 절차가 있어야 함**: 다른 사람이 그대로 따라했을 때 같은 문제가 나와야 함
- **원인이 근거로 확정되어야 함**: 로그/코드/설정/DB 상태 등 근거 포함
- **검증이 있어야 함**: 해결 후 동일 절차로 더 이상 발생하지 않는다는 확인

---

## 검색 팁 (GitHub Search)

GitHub 검색창에서 아래처럼 검색하면 빨리 찾을 수 있습니다.

- 특정 에러 메시지:
  - `path:docs/troubleshooting "LazyInitializationException"`
- 특정 컴포넌트:
  - `path:docs/troubleshooting "컴포넌트: auth"`
- 특정 환경:
  - `path:docs/troubleshooting "환경: local"`
- 특정 키워드:
  - `path:docs/troubleshooting "Flyway"`
  - `path:docs/troubleshooting "Testcontainers"`

---

## 인덱스 자동 갱신 사용법

1. 각 문서 상단에 `**컴포넌트:**` 메타를 넣습니다.
   - 예: `**컴포넌트:** api` 또는 `**컴포넌트:** db, infra`
2. 날짜 메타 규칙:
   - `작성일`은 필수입니다.
   - `해결 날짜`는 선택입니다(해결된 케이스에서 권장).
3. 날짜 정렬은 `해결 날짜`를 우선 사용하고, 없으면 `작성일`을 사용합니다.
   - 날짜 포맷: `YYYY-MM-DD` 권장 (`YYYY/M/D`, `YYYY.MM.DD`도 인식)
4. 인덱스 표기 규칙:
   - 날짜가 있으면 `[YYYY-MM-DD] [제목]`
   - 날짜가 없으면 `[제목]`
5. 아래 스크립트를 실행하면 **최신 케이스/컴포넌트별 인덱스**가 자동 갱신됩니다.
   - `powershell -ExecutionPolicy Bypass -File ./scripts/update-troubleshooting-index.ps1`
   - 작성일 누락 문서는 경고로 표시됩니다.
   - 엄격 모드(누락 시 실패): `TS_INDEX_STRICT_CREATED_DATE=1`

지원 컴포넌트 키워드(자동 분류):
- `api`, `web` -> API / Web
- `auth` -> Auth
- `db`, `database`, `migration` -> DB / Migration
- `infra`, `devx`, `docker`, `ci`, `tool`, `tooling`, `hook` -> Infra / DevX

---

## Troubleshooting Index

> 새 문서를 추가한 뒤 스크립트를 실행하면 아래 목록이 자동 갱신됩니다.  
> 정렬 규칙: 기본은 **최신 날짜가 위**.

### 최신 케이스(Recent)

<!-- INDEX:RECENT:START -->
- [2026-01-26] [Windows Testcontainers 통합테스트 실패 (InvalidPathException / DockerClientProviderStrategy)](./testcontainers-windows-path-docker.md)
- [2026-01-26] [라인엔딩 변경으로 파일이 수정된 것처럼 보이는 문제](./line-endings-autocrlf.md)
- [2026-01-26] [예약 동시성 중복 예약이 400으로 내려오는 문제](./reservation-concurrency-duplicate-409.md)
- [2026-01-26] [통합 테스트 401 계약 불일치 + ProblemDetail 검증 기준 정리](./integration-test-401-problemdetail-contract.md)
- [2026-01-20] [Windows Git Hooks + gitleaks 설치/실행 트러블슈팅](./git-hooks-gitleaks-windows.md)
- [2026-01-16] [\[Troubleshooting\] toggleFavorite 내부 호출 시 @CacheEvict 미적용으로 캐시 stale 발생](./cache-evict-not-applied-internal-call.md)
- [2026-01-16] [동시 찜 추가 요청 시 DataIntegrityViolationException 발생 문제](./concurrent-favorite-dataintegrity-exception.md)
- [2026-01-16] [통합 테스트 UNIQUE 제약 충돌 해결](./integration-test-unique-constraint-conflict.md)
- [2026-01-15] [GitHub Actions CI 통합테스트 실패 완전 해결 가이드](./ci-emailsender-context-failure-complete.md)
- [2026-01-14] [ExecutorService.submit() 예외 처리 문제 해결](./executorservice-exception-handling.md)
- [2026-01-12] [통합 테스트 동시성 검증 실패 트러블슈팅 (PB-64)](./integration-test-transaction-isolation.md)
- [2026-01-08] [트러블슈팅: UTF-8 with BOM로 인한 충돌/오류 대응 기록](./incoding.md)
- [2025-12-30] [트러블슈팅: 찜 목록이 프론트엔드에 표시되지 않는 문제](./favorite-not-showing.md)
- [2025-12-30] [트러블슈팅: 찜하기 동시성 문제 (Race Condition)](./favorite-concurrency.md)
- [2024-12-29] [Git Merge Conflict 트러블슈팅 가이드 - PB-42 Favorite 기능](./merge-conflict-PB-42.md)
<!-- INDEX:RECENT:END -->

### 컴포넌트별(선택)

> 케이스가 많아지면 아래처럼 컴포넌트별로도 모읍니다.

#### API / Web
<!-- INDEX:API_WEB:START -->
- [2026-01-16] [\[Troubleshooting\] toggleFavorite 내부 호출 시 @CacheEvict 미적용으로 캐시 stale 발생](./cache-evict-not-applied-internal-call.md)
- [2026-01-14] [ExecutorService.submit() 예외 처리 문제 해결](./executorservice-exception-handling.md)
- [2025-12-30] [트러블슈팅: 찜 목록이 프론트엔드에 표시되지 않는 문제](./favorite-not-showing.md)
- [2025-12-30] [트러블슈팅: 찜하기 동시성 문제 (Race Condition)](./favorite-concurrency.md)
<!-- INDEX:API_WEB:END -->

#### Auth
<!-- INDEX:AUTH:START -->
- (없음)
<!-- INDEX:AUTH:END -->

#### DB / Migration
<!-- INDEX:DB_MIGRATION:START -->
- [2026-01-16] [동시 찜 추가 요청 시 DataIntegrityViolationException 발생 문제](./concurrent-favorite-dataintegrity-exception.md)
- [2026-01-16] [통합 테스트 UNIQUE 제약 충돌 해결](./integration-test-unique-constraint-conflict.md)
- [2026-01-12] [통합 테스트 동시성 검증 실패 트러블슈팅 (PB-64)](./integration-test-transaction-isolation.md)
<!-- INDEX:DB_MIGRATION:END -->

#### Infra / DevX (Docker, CI, tooling)
<!-- INDEX:INFRA_DEVX:START -->
- [2026-01-20] [Windows Git Hooks + gitleaks 설치/실행 트러블슈팅](./git-hooks-gitleaks-windows.md)
- [2026-01-15] [GitHub Actions CI 통합테스트 실패 완전 해결 가이드](./ci-emailsender-context-failure-complete.md)
- [2026-01-08] [트러블슈팅: UTF-8 with BOM로 인한 충돌/오류 대응 기록](./incoding.md)
- [2024-12-29] [Git Merge Conflict 트러블슈팅 가이드 - PB-42 Favorite 기능](./merge-conflict-PB-42.md)
<!-- INDEX:INFRA_DEVX:END -->

---

## 문서 품질 체크(작성 후 30초 점검)

- [ ] 첫 3줄 요약만 읽어도 “무슨 문제/원인/해결”이 이해된다
- [ ] 재현 방법이 복사-실행 가능한 수준으로 적혀 있다
- [ ] 원인에 “결정적 근거”가 포함되어 있다
- [ ] 해결 후 검증(테스트/커맨드/재현 불가)이 적혀 있다
- [ ] 재발 방지 액션(테스트/가드/로깅/문서화)이 최소 1개 이상 있다

---

## 유지보수 룰(추천)

- 템플릿 수정은 신중하게: 템플릿은 “작성 부담”이 늘면 바로 안 쓰게 됩니다.
- 케이스 문서는 삭제하지 않기: 오래된 케이스도 검색 가치가 있습니다.
- 케이스가 많아지면(예: 30개 이상) 컴포넌트별 인덱스를 “필수”로 운영합니다.



