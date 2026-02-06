# 통합 테스트 401 계약 불일치 + ProblemDetail 검증 기준 정리

**작성일:** 2026-01-26

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2026-01-20 | - | 문서 생성 |

## 요약
- 401 응답이 `UNAUTHORIZED` 계약을 따르지 않고 `Unauthorized`로 내려와 테스트가 실패했다.
- 원인은 `GET /api/beaches/reservations`가 `/api/beaches/**`의 `permitAll()`에 포함되어
  보안 필터를 거치지 않고 컨트롤러에서 401을 던진 것.
- 해결은 보안 경로 분리 + ProblemDetail 검증 기준 정리.

## 문제 상황

### 증상
- `ReservationControllerIntegrationTest`의 P0-12(인증 없음 401) 실패.
- 실패 메시지:
  ```
  ReservationControllerIntegrationTest > P0-12: 내 예약 목록 조회 - 인증 없음 401 FAILED
  java.lang.AssertionError: JSON path "$.title" expected:<UNAUTHORIZED> but was:<Unauthorized>
  at ReservationControllerIntegrationTest.java:430
  ```

### 재현 방법
```
.\gradlew.bat test --tests com.beachcheck.integration.ReservationControllerIntegrationTest
```

### 기대/실제 차이
- 기대: `title=UNAUTHORIZED`, `code=UNAUTHORIZED`
- 실제: `title=Unauthorized`

## 조사 과정 (실제 진행 순서)

### 1) 실패 지점 확인
실패 위치는 `ReservationControllerIntegrationTest.java:430`.
해당 테스트는 인증 없이 요청한 경우 `UNAUTHORIZED` 계약을 기대한다.

판단 근거:
- `SecurityConfig`의 `authenticationEntryPoint`에서 `ProblemDetail`의
  `title/code`를 `UNAUTHORIZED`로 고정하고 있음.

### 2) 실제 응답의 출처 추정
실제 `title` 값이 `Unauthorized`인 점을 근거로
`ResponseStatusException` 기본 처리 경로를 의심했다.

판단 근거:
- Spring은 `ResponseStatusException`을 기본 처리할 때
  `title`을 HTTP reason phrase로 설정함.
- 401의 reason phrase는 `Unauthorized`.

### 3) 보안 경로 매칭 확인
`SecurityConfig`의 `authorizeHttpRequests`를 확인했다.

핵심 구문:
```java
.requestMatchers(HttpMethod.GET, "/api/beaches/**")
.permitAll()
```

이 설정 때문에 `/api/beaches/reservations`도 인증 없이 통과한다.
그 결과, 컨트롤러에서 `@AuthenticationPrincipal`이 `null`이 되고,
`ResponseStatusException(401)`이 발생했다.

정리:
- 보안 필터 경유: `UNAUTHORIZED` 계약
- 컨트롤러 예외: `Unauthorized` (기본 처리)

## 원인

### 1) 401 응답 계약 불일치
`/api/beaches/reservations`가 `/api/beaches/**`의 `permitAll()`에 포함되어
`authenticationEntryPoint`가 동작하지 않았다.

### 2) ProblemDetail 검증 기준 부재
테스트에서 어떤 경우에 `title/code`까지 검증할지 기준이 없어
프레임워크 기본 응답과 계약 응답이 섞였다.

## 해결 과정

### 1) 보안 경로 분리
`/api/beaches/reservations`를 별도로 `authenticated()`로 분리했다.

```java
.requestMatchers(HttpMethod.GET, "/api/beaches/reservations")
.authenticated()
.requestMatchers(HttpMethod.GET, "/api/beaches/**")
.permitAll()
```

결과:
인증 없는 요청이 항상 `authenticationEntryPoint`로 처리되어
`UNAUTHORIZED` 계약이 유지된다.

### 2) ProblemDetail 검증 기준 정리
테스트에서 검증 강도를 다음과 같이 구분했다.

#### 기준 요약표

| 유형 | 대상 | 검증 항목 | 이유 |
|---|---|---|---|
| A | 비즈니스/도메인 오류 (ApiException) | `status`, `title`, `code` | 클라이언트가 오류 코드를 기준으로 분기 |
| B | 인증/인가 실패 (SecurityConfig) | `status`, `title=UNAUTHORIZED`, `code=UNAUTHORIZED` | 보안 계층에서 계약을 명시적으로 고정 |
| C | 프레임워크 기본 400 계열 | `status`, `contentType=application/problem+json` | Spring 기본 `title/detail`은 버전에 따라 변동 |

#### 적용 예시
```java
.andExpect(status().isBadRequest())
.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
.andExpect(jsonPath("$.status").value(400))
```

```java
.andExpect(status().isUnauthorized())
.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
.andExpect(jsonPath("$.status").value(401))
.andExpect(jsonPath("$.title").value("UNAUTHORIZED"))
.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
```

## 로그 및 시도 기록

### 1) 실패 로그
```
ReservationControllerIntegrationTest > P0-12: 내 예약 목록 조회 - 인증 없음 401 FAILED
java.lang.AssertionError: JSON path "$.title" expected:<UNAUTHORIZED> but was:<Unauthorized>
at ReservationControllerIntegrationTest.java:430
```

### 2) 원인 확인 체크리스트
- `SecurityConfig`에서 `/api/beaches/**`가 `permitAll()`인지 확인
- `ReservationController`에서 `ResponseStatusException(401)` 사용 확인
- `authenticationEntryPoint`의 ProblemDetail 계약 확인

### 3) 수정 후 재검증 로그
```
> Task :test
BUILD SUCCESSFUL in 33s
```

## 결과 검증
```
.\gradlew.bat test --tests com.beachcheck.integration.ReservationControllerIntegrationTest
```

결과: **BUILD SUCCESSFUL**

## 교훈 및 재발 방지 체크리스트

### 1) 보안 경로는 `permitAll` 범위를 명확히 분리
- 공통 prefix(`/api/beaches/**`) 아래에 인증 필요 경로가 있으면
  구체 경로를 먼저 선언해 우선순위를 확보한다.

### 2) ProblemDetail 검증 기준을 명확히 구분
- 도메인 오류: `title/code`까지 검증
- 프레임워크 오류: `status + contentType`만 검증
- 보안 오류: `UNAUTHORIZED` 계약 유지

## 관련 파일
- `src/main/java/com/beachcheck/config/SecurityConfig.java`
- `src/main/java/com/beachcheck/controller/ReservationController.java`
- `src/test/java/com/beachcheck/integration/ReservationControllerIntegrationTest.java`

## 변경 이력
- 2026-01-20: `/api/beaches/reservations` 인증 경로 분리, ProblemDetail 검증 기준 정리
