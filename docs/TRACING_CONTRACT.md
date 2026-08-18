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

- 전파 형식은 W3C Trace Context(`traceparent`, `tracestate`)로 통일한다.
- baggage 전파는 비활성화하고 W3C Trace Context 필드만 소비·생성한다.
- HTTP method, 정규화된 route, HTTP status와 고정된 결과 값처럼 낮은 cardinality 값만 사용한다.
- `service.name`은 `spring.application.name`의 `beach-complex`, 배포 환경은 `APP_ENVIRONMENT` 값으로 기록한다.
- URL query string, SQL parameter와 모든 private method를 span/attribute로 수집하지 않는다.
- `requestId`, `userId`, `notificationId`, `outboxEventId` 같은 고유값을 span attribute나 Loki label로 승격하지 않는다.

## HTTP ingress 계약

- Spring MVC의 `http.server.requests` 자동 observation을 HTTP server span으로 사용한다. 애플리케이션 필터에서 중복 server span을 만들지 않는다.
- 유효한 `traceparent`가 들어오면 같은 trace의 자식 server span을 만들고, 없거나 유효하지 않으면 새 trace를 시작한다.
- `MdcRequestFilter`는 HTTP observation filter 안쪽에서 실행해 요청 처리 동안 기존 `requestId`와 실제 `traceId`/`spanId`를 함께 유지한다.
- 성공, 인증 실패, 클라이언트 오류와 서버 오류는 응답 status와 `outcome`으로 구분한다.
- route는 템플릿 경로를 우선 사용한다. Security filter에서 handler mapping 전에 종료된 요청처럼 route를 알 수 없는 경우에는 `UNKNOWN`을 허용한다.
- handler에서 처리되어 응답으로 변환된 예외는 status와 `outcome`을 최소 보장한다. exception attribute나 event는 예외가 observation까지 전달된 경우에만 기록한다.

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
