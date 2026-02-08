# ADR-008: Outbox + DB폴링워커 도입

## 상태

Proposed

## 컨텍스트

본 시스템은 사용자에게 예약 확인 및 중요 공지 푸시 알림을 제공한다.
해당 알림은 단순 마케팅 알림과 달리, 유실 시 사용자 경험 및 비즈니스 신뢰도에 직접적인 영향을 미친다.

현재 시스템 특성 및 제약은 다음과 같다.

- 알림 트리거는 DB 트랜잭션(예약 확정, 공지 발행 등) 이후 발생
- 푸시 전송은 외부 시스템(FCM)에 의존 → 완전한 end-to-end 보장은 불가
- 내부 시스템 차원에서는 이벤트 유실 방지(at-least-once)가 필요
- Spring 기반 백엔드 환경
- 이메일 발송(회원가입 / 비밀번호 찾기)은 별도 auth 플로우로 유지하며, 본 ADR 대상에서는 제외한다

현재 FCM 푸시는 `@Async`로 처리 중이다.
`@Async`는 스레드풀 큐(메모리)에만 의존하여 앱 크래시 시 이벤트가 유실되고,
트랜잭션 커밋과 이벤트 큐 등록 사이에 원자성이 보장되지 않는다.

이를 해결하기 위해 Outbox 패턴과 DB폴링워커를 도입한다.
Outbox는 비즈니스 엔티티와 이벤트 기록(outbox_events)을
같은 트랜잭션 내에서 저장하여, "커밋 성공 ↔ 이벤트 기록 완료"의 원자성을 보장한다.
단, 외부 시스템(FCM)까지의 실제 전달은 원자성 보장 범위 밖이다.

## 결정

Outbox 패턴과 DB폴링워커(OutboxPublisher)를 도입하여 알림 이벤트를 전달한다.

- Outbox 테이블에 FCM 푸시 알림 이벤트를 DB 트랜잭션 내에서 기록
- DB폴링워커(OutboxPublisher)가 PENDING 이벤트를 주기적으로 폴링하여 직접 FCM으로 발송 (DB를 메시지 브로커 대용으로 사용, 별도 브로커 불필요)
- 재시도는 워커 내에서 exponential backoff 기반으로 구현 (`next_retry_at` 필드 활용)
- 최대 재시도 횟수(3회) 초과 시 FAILED 상태로 변경 및 ERROR 레벨 로그 기록
- 멱등 처리: Notification status 체크로 중복 발송 시 상태 업데이트 스킵 (이미 SENT/FAILED이면 무작업)

## 근거

- Outbox만으로 at-least-once 보장 가능 → 추가 인프라(브로커) 불필요
- 아키텍처 단순: 폴링 + 발송 + 재시도가 단일 컴포넌트 내에서 처리
- 현재 단일 목적(푸시 알림 발송) 규모에서 메시지 브로커 도입은 운영 복잡도만 추가

## 결과

### 긍정적 영향

- 예약/공지 알림 이벤트의 내부 유실 방지 (Outbox)
- 추가 인프라 없이 안정적인 이벤트 처리

### 부정적 영향

- 재시도/백오프 로직을 워커 내에서 직접 구현해야 함
- 다중 소비자 환경에서 확장성 제한 (현재 단일 인스턴스 운영 중이므로 영향 미미)
- 이벤트 재처리(리플레이) 불가: 장애 후 특정 이벤트를 다시 처리하려면 OutboxEvent 테이블을 직접 조회·재실행해야 함 (현재 단일 목적 운영이므로 영향 미미)

### 구현/운영 지침

- OutboxEvent에 `next_retry_at` 필드를 활용하여 백오프 간격 관리
- 폴링 쿼리: `WHERE status = 'PENDING' AND next_retry_at <= NOW()` (백오프 중인 이벤트는 폴링 대상에서 제외)
- 폴링 동시성: `SELECT ... FOR UPDATE SKIP LOCKED`로 동일 이벤트의 중복 폴링 방지
- 폴링 간격: 1초 (커넥션 풀 및 DB 부하 고려, UX 요구사항에 따라 조정 가능)
- 재시도: exponential backoff (1초 → 2초 → 4초), 최대 3회 재시도
- 최대 재시도 초과: FAILED 상태로 변경 + ERROR 레벨 로그
- 재시도 루프를 직접 구현하지 않고, OutboxEvent 상태와 `next_retry_at`를 활용한 폴링 기반 재시도
- 푸시 알림은 "전달 보장 대상"이 아닌 "전달 시도 대상"
- 실제 사용자 보장은 Notification DB(알림함)를 통해 해결

## 대안

### RabbitMQ (검토한 후 배제 — ADR-007)

- 장점: ACK 기반 소비 모델, DLQ/지연 큐 패턴, Spring AMQP 학습 비용 낮음
- 단점: 현재 단일 목적(푸시 알림 발송) 규모에서 추가 인프라와 운영 복잡도가 비용대비 효과 불균형
- 결론: DB폴링워커로 충분하며, 브로커 도입은 현재 단계에서 불필요

### Redis Pub/Sub

- 장점: 단순, 빠름
- 단점: at-most-once 전달 모델로 Subscriber 다운/네트워크 단절 시 메시지 즉시 유실. 재시도, ACK, DLQ 개념 부재
- 결론: 예약 확인/공지 알림이라는 유실 불가 도메인과 부합하지 않음

### Redis Stream

- 장점: Pub/Sub 대비 메시지 영속성 보장, Consumer Group 및 ACK 메커니즘 지원, Pending Entries List(PEL)를 통한 재시도 가능, RabbitMQ/Kafka 대비 경량
- 단점: 인메모리 기반으로 RDB/AOF persistence 설정 필요 (완전한 durability 보장 어려움, RabbitMQ/Kafka는 기본적으로 디스크 저장), 클러스터 환경에서 데이터 분산 및 복제 복잡도, Kafka 대비 리플레이/다중 소비자 처리 기능 부족, Spring 통합 시 직접 구현 필요 (Spring Data Redis Streams 활용 가능하나 AMQP/Kafka 대비 생태계 미성숙)
- 결론: RabbitMQ와 Kafka의 중간 수준 대안이나, 현재 단일 목적 규모에서는 DB폴링워커로 충분. Redis를 이미 캐시로 사용 중이라면 향후 검토 대상

### Kafka (현재 단계에서 미적용 — 향후 검토 대상)

- 장점: 다중 독립 소비자 (Consumer Group), 리플레이 (offset 조작), 고처리량
- 단점: 현재 단일 목적(푸시 알림 발송) 규모에서 클러스터 운영 복잡도가 비용대비 효과 불균형
- 결론: 현재 단계에서는 폴링워커로 충분하며, 다중 소비자 요구사항이 구체화되는 시점에 재검토

## 참고

### 공식 문서 링크

- Transactional Outbox Pattern (Chris Richardson): https://microservices.io/patterns/data/transactional-outbox.html

### 관련 문서

- ADR-007: [RabbitMQ 채택 (Rejected)](ADR-007-rabbitmq-push-notification-broker.md)

## 작성일자

2026-02-05
