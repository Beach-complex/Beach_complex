# ADR-005: 예약하기 통합테스트 전략

## 상태

Proposed

## 컨텍스트
예약 API는 인증/인가, 시간 검증, 중복 제약, 존재성 검사 등 다층 로직이 얽혀 있다.
또한 오류 응답은 ProblemDetail 계약을 유지해야 하며,
시간 의존 로직은 테스트에서 결정적으로 재현되어야 한다.

현재 목표는 다음을 보장하는 통합테스트 전략을 선택하는 것이다.
- API 흐름(컨트롤러-서비스-DB)까지 검증
- 문제 발생 시 오류 계약(ProblemDetail)을 일관되게 검증
- 시간 의존 로직을 안정적으로 테스트

## 결정
예약하기 통합테스트는 다음 구성으로 진행한다.
- Spring Boot 통합 테스트 + MockMvc 사용
- Testcontainers 기반 실제 DB 사용
- `Clock` 고정(MockBean)으로 시간 결정성 확보
- 오류 응답은 계약 수준으로 검증(ProblemDetail 기준 분리)

## 근거
- MockMvc는 컨트롤러/필터/예외 핸들러까지 포함하면서도 E2E보다 빠르고 안정적이다.
- Testcontainers는 실제 DB 제약과 동작을 그대로 검증할 수 있다.
- 고정 Clock은 현재 시간 의존 테스트의 플래키를 줄인다.
- ProblemDetail 검증 기준을 분리하면 계약은 지키되 불필요한 결합을 피할 수 있다.

## 결과
- 장점: API 계약 + DB 반영 + 보안 응답 계약까지 함께 검증 가능
- 단점: Docker 의존성 증가, 단위 테스트 대비 실행 시간 증가

## 대안
- 단위 테스트 중심(컨트롤러/서비스 분리)
  - 장점: 빠르고 범위가 좁음
  - 단점: 실제 HTTP 계약, 보안 필터, DB 제약 검증이 약함
- 완전 E2E(실 서버 + HTTP 클라이언트)
  - 장점: 현실에 가장 근접
  - 단점: 느리고 불안정, 디버깅 난이도 증가
- H2/인메모리 DB
  - 장점: 실행 속도 향상
  - 단점: 실제 DB 제약/행동과 차이가 발생할 수 있음

## 참고
- `src/test/java/com/beachcheck/integration/ReservationControllerIntegrationTest.java`
- `src/test/java/com/beachcheck/config/TestcontainersConfig.java`
- `src/main/java/com/beachcheck/config/SecurityConfig.java`

## 작성일자
2026-01-20
