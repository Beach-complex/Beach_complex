# Trace 공통 계약

이 문서는 애플리케이션 Trace의 공통 설정과 데이터 수집 경계를 정의한다.

## 역할

- `requestId`: 고객 문의와 단일 요청의 로그 검색에 사용한다. 기존 `X-Request-Id` 계약을 유지한다.
- `traceId`: 하나의 요청에서 파생된 전체 실행 흐름을 연결한다.
- `spanId`: Trace 안의 현재 실행 구간을 식별한다.
- `traceId`와 `spanId`는 Micrometer Tracing이 생성하고 관리한다. 애플리케이션 필터에서 직접 만들지 않는다.

## 환경변수

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `0.1` | root Trace sampling 비율 (`0.0`~`1.0`) |
| `MANAGEMENT_OTLP_TRACING_ENDPOINT` | 미설정 | OTLP/HTTP endpoint. 예: `http://observability:4318/v1/traces` |
| `MANAGEMENT_OTLP_TRACING_TIMEOUT` | `5s` | exporter 요청 제한 시간 |
| `APP_ENVIRONMENT` | `local` | `deployment.environment.name` resource attribute |

endpoint가 설정되지 않으면 OTLP exporter를 만들지 않는다. 로컬과 테스트가 수집기 없이 실행되는 기본 동작이다.
dev에서 수집 경로를 검증할 때 endpoint와 sampling `1.0`을 명시하고, 운영 sampling은 부하 검증 후 결정한다.

## 전파와 속성 정책

- 전파 형식은 W3C Trace Context(`traceparent`)로 통일한다.
- HTTP method, 정규화된 route, HTTP status와 고정된 결과 값처럼 낮은 cardinality 값만 사용한다.
- `service.name`은 `spring.application.name`의 `beach-complex`, 배포 환경은 `APP_ENVIRONMENT` 값으로 기록한다.
- URL query string, SQL parameter와 모든 private method를 span/attribute로 수집하지 않는다.
- `requestId`, `userId`, `notificationId`, `outboxEventId` 같은 고유값을 span attribute나 Loki label로 승격하지 않는다.

## 수집 금지 정보

- `Authorization`, JWT, access/refresh token
- 이메일 주소, 인증 링크와 인증 token, 이메일 제목·본문
- SMTP 자격증명
- FCM token과 payload
- Cookie, 비밀번호, AWS access key/secret

## 장애 격리

- OTLP endpoint 미설정 또는 수집기 장애가 API·메일·Scheduler의 업무 실패로 전파되어서는 안 된다.
- 긴 exporter 대기 대신 제한 시간 내 실패하고 업무 처리는 계속한다.
- 긴급 수집 중지는 OTLP endpoint를 제거하고 `MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.0`으로 설정한다.
- 이 설정에서도 애플리케이션의 `Tracer` API는 유지되지만 새 root span은 sampling/export되지 않는다.
