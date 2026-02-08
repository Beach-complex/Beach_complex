# 기술 결정 기록

## 1. 목적
- 프로젝트에서 사용하기로 한 주요 기술/라이브러리/아키텍처 결정과 그 이유를 기록합니다.
- 시간이 지나도 “왜 이렇게 했는지”를 문서를 보고 이해할 수 있도록 합니다.

## 2. 기록 대상
- 프레임워크 선택 (예: Spring Boot, Express)
- 데이터베이스 / 캐시 / 메시지 브로커 선택
- 인증/인가 방식 (JWT, 세션, OAuth)
- 로깅 / 모니터링 / 배포 방식

## 3. 기술 결정 목록

| ID | 주제 | 요약 결정                        | 상세 문서                                                                                               |
|------|------------------------|------------------------------|-----------------------------------------------------------------------------------------------------|
| ADR-001 | 백엔드 프레임워크 선택 | Spring Boot 사용               | [ADR-001-백엔드-프레임워크-선택](./ADR-001-backend-framework.md)                                              |
| ADR-002 | DB 선택 | PostgreSQL 사용                | (문서 추가 예정)                                                                                          |
| ADR-003 | 인증 방식 | JWT 기반 토큰 인증                 | (문서 추가 예정)                                                                                          |
| ADR-004 | 자동 코드 포맷팅 도입 | 자동 포맷팅 도구 도입                 | [ADR-004-Adopt-Automated-Code-Formatting](./ADR-004-Adopt-Automated-Code-Formatting.md)             |
| ADR-005 | 예약 통합 테스트 전략 | 예약 도메인 통합 테스트 전략 수립          | [ADR-005-reservation-integration-test-strategy](./ADR-005-reservation-integration-test-strategy.md) |
| ADR-006 | 작은 PR 리뷰·브랜치 전략 | Stacked PR 우선, 향후 Trunk+Flag | [adr-006-small-pr-review-branch-strategy](./adr-006-small-pr-review-branch-strategy.md)             |
| ADR-007 | 푸시 알림 이벤트 브로커 선택 | RabbitMQ (Rejected — ADR-008로 대체) | [ADR-007-rabbitmq-push-notification-broker](./ADR-007-rabbitmq-push-notification-broker.md)         |
| ADR-008 | Outbox + DB폴링워커 도입 | 알림 이벤트 유실 방지 패턴 적용 | [ADR-008-outbox-db-polling-worker](ADR-008-outbox-db-polling-worker.md)                             |

## 4. 기술 결정 문서 템플릿

새로운 기술 결정을 기록할 때는 아래 템플릿을 기준으로 문서를 작성합니다. (예: `ADR-00X-주제`)

~~~markdown
# ADR 번호: [제목]

## 상태

[Proposed | Accepted | Deprecated | Superseded | Rejected]

## 컨텍스트
이 결정을 하게 된 배경과 관련된 정보를 서술합니다.
현재 시스템 구조, 기술 상황, 팀의 제약 조건 등을 기술합니다.

## 결정
어떤 결정을 내렸는지 명확하게 서술합니다.
선택한 아키텍처나 접근 방식, 구성과 그 이유를 기술합니다.

## 근거
왜 이 결정을 내렸는지를 설명합니다.
다른 대안들과 비교했을 때의 장단점, 주요 고려 요소 등을 포함합니다.

## 결과
이 결정이 시스템에 미치는 영향이나, 이후 따라야 할 구현 및 운영 지침을 기술합니다.
추가로 생기는 작업이나 영향 범위도 포함합니다.

## 대안
검토했지만 선택하지 않은 대안과 그 이유를 요약합니다.

## 참고
### 테크 아티클
-
### 공식 문서 링크
- 
### 관련 이슈 / PR
- 

## 작성일자
YYYY-MM-DD

