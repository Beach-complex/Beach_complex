# ADR-007: 푸시 알림 이벤트 브로커로 RabbitMQ 채택

## 상태

Rejected — ADR-008로 대체됨. 단계별 접근(Outbox + DB폴링워커 → Kafka)으로 방향 변경되어 RabbitMQ 단일 채택 근거가 유효하지 않음.

## 컨텍스트

본 시스템은 사용자에게 예약 확인 및 중요 공지 푸시 알림을 제공한다.
해당 알림은 단순 마케팅 알림과 달리, 유실 시 사용자 경험 및 비즈니스 신뢰도에 직접적인 영향을 미친다.

현재 시스템 특성 및 제약은 다음과 같다.

- 알림 트리거는 DB 트랜잭션(예약 확정, 공지 발행 등) 이후 발생
- 푸시 전송은 외부 시스템(FCM)에 의존 → 완전한 end-to-end 보장은 불가
- 내부 시스템 차원에서는 이벤트 유실 방지(at-least-once)가 필요
- 향후 메시지 브로커 확장 가능성(Kafka 도입)을 고려하되, 현재 팀 규모와 운영 복잡도를 감당할 수 있어야 함
- Spring 기반 백엔드 환경

현재 이메일 인증 발송은 `@Async` + `@Retryable` + `@Recover` 로 처리 중(PB-79)
이메일도 Outbox 패턴 대상으로 확장하는지 여부는 PB-82에서 검토한다.

현재 `@Async`는 스레드풀 큐(메모리)에만 의존하여 앱 크래시 시 이벤트가 유실되고,
트랜잭션 커밋과 이벤트 큐 등록 사이에 원자성이 보장되지 않는다.
이를 해결하기 위해 Outbox 패턴을 도입한다. Outbox는 비즈니스 엔티티와 이벤트 기록(outbox_events)을
같은 트랜잭션 내에서 저장하여, "커밋 성공 ↔ 이벤트 기록 완료"의 원자성을 보장한다.
단, 외부 시스템(FCM/SMTP)까지의 실제 전달은 원자성 보장 범위 밖이다.
Outbox 이후의 전달 구조로 "DB 폴링 워커(브로커 없음)"와 "메시지 브로커"를 검토했고,
최종적으로 메시지 브로커(RabbitMQ)를 채택했다. 배제된 대안의 근거는 아래 대안 섹션을 참조한다.

## 결정

푸시 알림 이벤트 전달을 위한 메시지 브로커로 RabbitMQ를 채택한다.

- Outbox 테이블에 이벤트를 DB 트랜잭션 내에서 기록
- 별도 퍼블리셔가 Outbox 이벤트를 읽어 RabbitMQ에 publish
- 컨슈머는 manual ACK 기반으로 at-least-once 전달을 보장하고
ACK 미응답 시 재전달될 수 있으므로 멱등 처리가 필요
- 푸시 발송 실패 시 재시도 / 지연 / DLQ 전략을 적용 가능하도록 설계

## 근거

### RabbitMQ 채택 근거

- ACK 기반 소비 모델 → 메시지 처리 실패 시 재전송 가능
- Durable Queue + Persistent Message → 브로커 장애 시에도 메시지 보존
- 지연 큐 / DLQ 패턴 → 실패 이벤트 격리 및 재처리 가능
- Spring AMQP 기반으로 구현 난이도 및 학습 비용이 낮음
- 추후 Kafka로 확장 시에도 "이벤트 발행"이라는 개념적 모델은 유지 가능

## 결과

### 긍정적 영향

- 예약/공지 알림 이벤트의 내부 유실 방지
- 장애 상황에서도 재시도 및 운영 대응 가능
- Outbox 패턴과 결합하여 이중 쓰기(dual-write) 문제 제거
- 푸시 실패와 이벤트 실패를 명확히 분리 가능

### 부정적 영향

- RabbitMQ 인프라 추가로 운영 복잡도 증가
- 로컬 개발 시 RabbitMQ 컨테이너 필요 (Testcontainers로 해결 가능)
- Outbox 퍼블리셔 스케줄러 등 추가 구현 비용 발생

### 구현/운영 지침

- Consumer는 eventId 기준 중복 체크로 멱등 처리를 보장 (DB unique 제약조건 활용, 추후에 redis 캐시 활용 고려)
- Exchange 타입: topic (향후 라우팅 확장 대응)
- Queue: durable (큐 내구성 보장)
- Message: persistent (브로커 장애 시 메시지 보존)
- Publisher Confirm: `spring.rabbitmq.template.confirms=true`로 활성화하고
OutboxPublisher는 Confirm ACK를 받은 후에만 OutboxEvent 상태를 PUBLISHED로 업데이트 (Confirm 미수신 시 PENDING 유지 → 다음 폴링에서 재시도)
- Publisher Return: `spring.rabbitmq.template.mandatory=true`로 활성화하고  
라우팅된 큐가 없거나 큐가 가득 차는 경우 메시지를 반환받아 로그 기록 후 PENDING 유지
- Consumer: manual ack로 at-least-once 전달 보장. ACK 미응답 시 재전달될 수 있으므로 중복 전달이 발생할 수 있다 (멱등 처리는 위항 참조)
- 재시도: DLQ + 지연 큐 패턴을 활용하고
  Consumer 처리 실패 시 basicNack( requeue=false) → DLQ → 지연 큐(exponential backoff) → 원래 큐로 재전달
- Consumer 코드에서 재시도 루프를 직접 구현하지 않고, DLX와 지연 큐 토폴로지를 설정하여 재전달 처리
- 최대 횟수 정책: Consumer에서 `x-delivery-count`를 확인하여 최대 4회(1회 처리 + 재시도 3회) 초과 시 DLQ에 유지하고
  이후 모니터링 후 수동 재처리 또는 폐기
- Outbox 폴링 간격: 1초 (DB 부하 및 지연 최소화)
- Outbox 폴링 동시성: `SELECT ... FOR UPDATE SKIP LOCKED`로 동일 이벤트의 중복 폴링 방지 (scale-out 시 여러 인스턴스가 동시에 폴링하는 경우 대응)
- Consumer prefetch: `spring.rabbitmq.listener.simple.prefetch=10` (한 번에 가져오는 메시지 수 제한, 공평한 부하 분배 및 메모리 과부하 방지)
- 푸시 알림은 "전달 보장 대상"이 아닌 "전달 시도 대상"
- 실제 사용자 보장은 Notification DB(알림함)를 통해 해결

### 추가 작업

- Outbox 퍼블리셔 스케줄러 구현
- 실패 이벤트 모니터링 메트릭 추가 (관측 스프린트때 구현)
- DLQ 메시지 수동 재처리 도구(관리 API 또는 스크립트)

## 대안

### Outbox + DB 폴링 워커 (브로커 없음)

- 장점: 추가 인프라 불필요, 아키텍처 단순. Outbox만으로 at-least-once 보장 가능
- 단점:
  - 재시도/백오프/DLQ를 워커 내에서 직접 구현해야 함.
  - 폴링과 처리가 같은 프로세스(App 인스턴스) 내에 결합되어 있어, 처리량을 늘리려면 App 인스턴스 자체를 스케일해야 한다. 
  반면 RabbitMQ는 Publisher와 Consumer가 분리되어 Consumer만 독립적으로 스케일 가능하다.
- 결론: 현재 규모에서는 충분하지만, 재시도 로직의 안정성과 향후 확장성 면에서 브로커 도입이 적합

### Redis Pub/Sub

- 장점: 단순, 빠름 (현재 캐시는 Caffeine 사용 중이므로 Redis 도입도 별도로 필요)
- 단점: at-most-once 전달 모델로 Subscriber 다운/네트워크 단절 시 메시지 즉시 유실. 재시도, ACK, DLQ 개념 부재
- 결론: 예약 확인/공지 알림이라는 유실 불가 도메인과 부합하지 않음. 마케팅성/실시간 힌트성 알림에만 적합

### Apache Kafka

- 장점: 고처리량, 이벤트 재사용, 리플레이(재처리 및 로그 아카이빙) 가능
- 단점: 대규모 스트리밍/이벤트 재사용에 최적화된 구조로, 현재 단일 목적(푸시 알림)과 낮은 처리량 규모에 맞지 않음. 복잡한 운영/클러스터 관리 비용과 오버엔지니어링 위험 존재
- 결론: 이벤트가 다목적 자산이 되는 시점에 재검토

## 참고

### 공식 문서 링크

- RabbitMQ Reliability: https://www.rabbitmq.com/docs/reliability
- Spring AMQP Reference: https://docs.spring.io/spring-amqp/reference/
- Redis Pub/Sub Delivery Semantics: https://redis.io/docs/latest/develop/pubsub/

### 관련 이슈 / PR

- PB-79: 이메일/푸시 알림 비동기 발송 구조 설계
- PB-82: Notification Outbox 패턴 도입

## 작성일자

2026-02-02