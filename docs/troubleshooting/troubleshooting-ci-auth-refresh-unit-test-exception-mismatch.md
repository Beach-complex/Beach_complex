# 2026-02-05 CI 실패: Auth refresh 단위테스트 예외 타입 불일치 (400 정책)

## 0) 메타 정보

- **Mode:** `DEV`
- **Status:** `Resolved`
- **작성자:** @jae
- **작성일:** 2026-02-05
- **컴포넌트:** auth, ci
- **환경:** GitHub Actions (ubuntu-latest), local
- **관련 이슈/PR:** PB-81, PB-80(#171)
- **키워드:** `:test FAILED`, `There were failing tests`, `AssertionError`, `ApiException`, `INVALID_REQUEST`, `refresh token`

---

## 1) 요약 (3줄)

- **무슨 문제였나:** GitHub Actions에서 `./gradlew test`가 `:test FAILED`로 종료(테스트 3개 실패).
- **원인:** `AuthService.refresh()`는 refresh 실패를 `ApiException(INVALID_REQUEST, 400)`로 통일하는데, 단위테스트가 `EntityNotFoundException`/`IllegalStateException`을 기대하고 있어 예외 타입 검증이 깨짐.
- **해결:** `AuthServiceTest`의 refresh 실패 케이스 기대값을 `ApiException + ErrorCode.INVALID_REQUEST`로 정합화.

## 1-1) 학습 포인트

- **Fast checks (3):** (1) Log: `:test FAILED` + `82 tests completed, 3 failed`  (2) 테스트명: `... > 토큰 갱신 > ... FAILED`  (3) Root: `Expecting ... IllegalStateException but was ApiException`
- **Rule of thumb:** API 계약(예: refresh 실패는 400 통일)을 정하면 “런타임 코드”뿐 아니라 “단위테스트의 예외 기대값”도 함께 고정해야 한다.

---

## 2) 증상 (Symptom)

### 관측된 현상
- CI에서 Gradle task `:test` 실패
- “인증 서비스 단위 테스트 > 토큰 갱신” 그룹에서 실패 케이스 발생

### 에러 메시지 / 스택트레이스 (핵심만)
```text
Execution failed for task ':test'.
> There were failing tests.
...
인증 서비스 단위 테스트 > 토큰 갱신 > 만료된 토큰 예외 FAILED
  Expecting actual throwable to be an instance of:
    java.lang.IllegalStateException
  but was:
    com.beachcheck.exception.ApiException: Invalid refresh token
      at com.beachcheck.service.AuthService.invalidRefreshToken(AuthService.java:132)
```

### 발생 조건 / 빈도
- **언제:** PR 브랜치가 `main`을 머지/리베이스하여 단위테스트(PB-80)가 유입된 직후
- **빈도:** 항상 (해당 테스트가 포함되면 재현됨)

---

## 3) 영향 범위

- **영향받는 기능:** CI 파이프라인에서 `test` 단계
- **심각도(개발 단계):** `High` (PR 머지/검증이 막힘)

---

## 4) 재현 방법 (Reproduction)

### 전제 조건
- 브랜치가 `main` 최신을 포함해야 함(단위테스트가 있는 상태)

### 재현 절차
1. `./gradlew test`
2. 실패 시 특정 테스트만 재현: `./gradlew test --tests com.beachcheck.service.AuthServiceTest`

---

## 5) 원인 분석 (Root Cause)

### 근거 (로그/코드)
- 로그에서 `:test FAILED` + `82 tests completed, 3 failed` 확인
- `TestEventLogger`의 `... 토큰 갱신 ... FAILED` 라인에서 “기대 예외 타입 vs 실제 예외 타입”이 바로 드러남
- 런타임 코드는 refresh 실패 시 `ApiException(INVALID_REQUEST)`를 던지도록 구현되어 있음:
  - `src/main/java/com/beachcheck/service/AuthService.java`의 `refresh()` / `invalidRefreshToken()`

### 최종 원인 (One-liner)
- refresh 실패 정책을 400(`ApiException(INVALID_REQUEST)`)로 통일한 구현과, 예전 예외 타입(404/409 성격)의 단위테스트 기대값이 충돌함.

---

## 6) 해결 (Fix)

### 해결 전략
- **유형:** `Test fix`
- **접근:** refresh 실패 케이스(토큰 없음/취소/만료)를 모두 `ApiException + ErrorCode.INVALID_REQUEST`로 기대하도록 테스트를 수정

### 변경 사항
- `src/test/java/com/beachcheck/service/AuthServiceTest.java`의 “토큰 갱신” 실패 케이스 3개 수정
- Commit: `7625fdb`

---

## 7) 검증 (Verification)

- [x] 동일 재현 절차에서 더 이상 실패하지 않음(해당 브랜치 기준)
- [x] CI에 수정 커밋이 반영됨(원격 브랜치에 push)

### 실행한 커맨드/테스트
```bash
./gradlew test
./gradlew test --tests com.beachcheck.service.AuthServiceTest
```

---

## 8) 재발 방지 (Prevention)

- [x] **테스트 라벨링:** 테스트 이름에 `(400 유지)`를 포함해 계약(정책)을 명시
- [ ] **CI 로그 최소화(선택):** 필요할 때만 `--debug` 사용, 기본은 `--stacktrace` 수준 유지
- [ ] **문서화:** 이 문서로 “로그에서 원인 찾는 키워드”와 “정책 정합화 기준”을 팀에 공유

