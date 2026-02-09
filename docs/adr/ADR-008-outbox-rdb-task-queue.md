# ADR-008: Outbox + RDB 기반 Task Queue 도입

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
`@Async`는 스레드풀 큐(메모리)에만 의존하여 앱 크래시 시 이벤트가 유실되고 (durable하지 않음),
트랜잭션 커밋과 이벤트 큐 등록 사이에 원자성이 보장되지 않는다.

원자성 보장 문제를 해결하기 위해 Outbox 패턴을 도입하고,
앱 크래시 시 이벤트 유실 방지를 위해 RDB 기반 Task Queue를 도입한다.
Outbox는 비즈니스 엔티티와 이벤트 기록(outbox_events)을
같은 트랜잭션 내에서 저장하여, "커밋 성공 ↔ 이벤트 기록 완료"의 원자성을 보장한다.
단, 외부 시스템(FCM)까지의 실제 전달은 원자성 보장 범위 밖이다.

## 결정

Outbox 패턴과 RDB 기반 Task Queue를 도입하여 알림 이벤트를 전달한다.

- Outbox 테이블에 FCM 푸시 알림 이벤트를 DB 트랜잭션 내에서 기록
- RDB 기반 Task Queue가 이벤트를 주기적으로 폴링하여 FCM으로 발송
- 재시도: exponential backoff 기반, 초기 1회 + 재시도 3회 (총 4회 시도)
- 실패 분류:
  - 일시 실패 (Retriable): 재시도 대상
  - 영구 실패 (Permanent): 재시도 제외 (관리자 알림/모니터링으로 처리)
- 멱등 처리: OutboxEvent.id 기반, at-least-once 보장

## 근거

- Outbox만으로 at-least-once 보장 가능 → 추가 인프라(브로커) 불필요
- 아키텍처 단순: 폴링 + 발송 + 재시도가 단일 컴포넌트 내에서 처리
- 현재 단일 목적(푸시 알림 발송) 규모에서 메시지 브로커 도입은 운영 복잡도만 추가

## 결과

### 긍정적 영향

- 예약/공지 알림 이벤트의 내부 유실 방지
- 트랜잭션 커밋과 이벤트 기록 간 원자성 보장
- 추가 인프라 없이 안정적인 이벤트 처리

### 부정적 영향

- 재시도/백오프 로직을 워커 내에서 직접 구현해야 함
- 다중 소비자 환경에서 확장성 제한 (현재 단일 인스턴스 운영 중이므로 영향 미미)
- 이벤트 재처리(리플레이) 불가 (현재 단일 목적 운영이므로 영향 미미)
- FCM 전송 후 상태 갱신 실패 시 중복 전송 가능 (at-least-once 특성)

## 대안

### RabbitMQ (검토한 후 배제 — ADR-007)

- 장점: ACK 기반 소비 모델, DLQ/지연 큐 패턴, Spring AMQP 학습 비용 낮음
- 단점: 현재 단일 목적(푸시 알림 발송) 규모에서 추가 인프라와 운영 복잡도가 비용대비 효과 불균형
- 결론: RDB 기반 Task Queue로 충분하며, 브로커 도입은 현재 단계에서 불필요

### Redis Pub/Sub

- 장점: 단순, 빠름
- 단점: at-most-once 전달 모델로 Subscriber 다운/네트워크 단절 시 메시지 즉시 유실. 재시도, ACK, DLQ 개념 부재
- 결론: 예약 확인/공지 알림이라는 유실 불가 도메인과 부합하지 않음

### Redis Stream

- 장점: Pub/Sub 대비 메시지 영속성 보장, Consumer Group 및 ACK 메커니즘 지원, RabbitMQ/Kafka 대비 경량
- 단점: 인메모리 기반으로 완전한 durability 보장 어려움, Spring 생태계 미성숙, 클러스터 운영 복잡도
- 결론: RabbitMQ와 Kafka의 중간 수준 대안이나, 현재 단일 목적 규모에서는 RDB 기반 Task Queue로 충분

### Kafka (현재 단계에서 미적용 — 향후 검토 대상)

- 장점: 다중 독립 소비자 (Consumer Group), 리플레이 (offset 조작), 고처리량
- 단점: 현재 단일 목적(푸시 알림 발송) 규모에서 클러스터 운영 복잡도가 비용대비 효과 불균형
- 결론: 현재 단계에서는 RDB 기반 Task Queue로 충분하며, 다중 소비자 요구사항이 구체화되는 시점에 재검토

## 참고

### 테크 아티클

- 우아한형제들 “장시간 비동기 작업, Kafka 대신 RDB 기반 Task Queue로 해결하기”: https://techblog.woowahan.com/23625/

### 공식 문서 링크

- Transactional Outbox Pattern (Chris Richardson): https://microservices.io/patterns/data/transactional-outbox.html

### 관련 문서

- ADR-007: [RabbitMQ 채택 (Rejected)](ADR-007-rabbitmq-push-notification-broker.md)

## 작성일자

2026-02-05
