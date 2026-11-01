# Beach Complex

**부산 해수욕장 7곳의 시설·혼잡도를 조회하고, 즐겨찾기와 방문 예약을 관리하는 서비스입니다.**

[![CI](https://github.com/Beach-complex/Beach_complex/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Beach-complex/Beach_complex/actions/workflows/ci.yml)
[![JaCoCo line coverage 89.8% — 2026-10-01 snapshot](./assets/badges/coverage.svg)](#품질과-배포)

Spring Boot 백엔드와 배포·모니터링 설정을 담은 저장소입니다.

[프론트엔드](https://github.com/Beach-complex/beach_complex_client) · [주요 구현](#주요-구현) · [문제 해결](#문제-해결) · [로컬 실행](#로컬-실행) · [문서](#문서)

## 기술 스택

![Java 21](https://img.shields.io/badge/Java-21-437291)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3-6DB33F)
![PostgreSQL 16 · PostGIS 3.4](https://img.shields.io/badge/PostgreSQL_16-PostGIS_3.4-4169E1)
![Caffeine](https://img.shields.io/badge/Cache-Caffeine-8A684B)
![OpenTelemetry](https://img.shields.io/badge/Tracing-OpenTelemetry-425CC7)
![Grafana](https://img.shields.io/badge/Observability-Grafana-F46800)
![Docker Compose](https://img.shields.io/badge/Docker-Compose-2496ED)
![Terraform](https://img.shields.io/badge/IaC-Terraform-844FBA)

| 영역 | 기술 |
| --- | --- |
| 프론트엔드 | React (별도 저장소) |
| 백엔드 | Java 21, Gradle, Spring Boot 3.3, Spring Security, JWT, Spring Data JPA / Hibernate |
| 데이터베이스·캐시 | PostgreSQL 16 / PostGIS 3.4, Flyway, Caffeine, Redis 7 (로컬 개발용) |
| 외부 연동 | Firebase Cloud Messaging(FCM), AWS Lambda Function URL / SigV4, Spring Mail / Gmail SMTP |
| 메트릭·로그·Trace 계측 | Spring Boot Actuator, Micrometer, OpenTelemetry, Logback |
| 모니터링 | Prometheus, node_exporter, Grafana Alloy, Loki, Tempo, Grafana |
| 인프라 | AWS EC2, EBS, Security Group, IAM role, Docker / Docker Compose, Terraform, cloud-init |
| CI/CD | GitHub Actions, GitHub Container Registry(GHCR), SSH 배포 |
| 품질 | JUnit 5, Testcontainers, JaCoCo, Checkstyle, Spotless, Gitleaks |

## 시스템 구성

[![Beach Complex 시스템 구성도 — 클라이언트, 앱 EC2, 관측 EC2, 외부 연동, CI/CD](./assets/images/system-architecture.png)](./assets/images/system-architecture.png)

앱 서버는 Spring Boot와 PostgreSQL/PostGIS를 Docker Compose로 실행합니다. React 클라이언트는 REST API를 호출하고, 백엔드는 혼잡도 조회에 AWS Lambda, 이메일 인증에 Gmail SMTP, 푸시 알림에 FCM을 사용합니다.

모니터링은 별도 EC2에 Prometheus·Tempo·Loki·Grafana를 두는 구성입니다. 앱의 Trace는 Tempo로, Alloy가 수집한 Docker 로그는 Loki로 보냅니다. Grafana에서 메트릭, 로그, Trace를 조회합니다.

## 주요 구현

### 반경 검색과 조회 캐시

위도·경도와 반경을 입력하면 가까운 해수욕장부터 반환합니다. PostGIS의 `ST_DWithin`으로 검색 범위를 제한하고 `ST_Distance`로 정렬합니다. 반경은 API에서 km로 받아 DB 조회 시 m로 변환합니다.

시설·상태 조회에는 Caffeine 캐시를 적용했습니다. 기존 메모리 맵에 만료 시간과 크기 제한이 없어 교체했으며, 별도 서버가 필요 없는 로컬 캐시를 선택했습니다. TTL은 10분, 캐시별 최대 항목은 1,000개입니다.

[공간 쿼리](./src/main/java/com/beachcheck/beach/repository/BeachRepository.java) · [조회 API 통합 테스트](./src/test/java/com/beachcheck/beach/integration/BeachControllerIntegrationTest.java) · [Caffeine 선택 배경과 대안](https://github.com/Beach-complex/beach_complex_docs/blob/main/adr/001-cache-migration-caffeine.md)

### Outbox 기반 푸시 알림

알림과 전송할 이벤트를 하나의 DB 트랜잭션으로 저장합니다. API는 저장 직후 응답하고, 워커가 이벤트를 읽어 FCM으로 푸시를 보냅니다. 전송 결과와 재시도할 작업을 DB에 남겨 별도 메시지 브로커 없이 처리합니다.

테스트 푸시 API의 처리 흐름은 다음과 같습니다.

```mermaid
sequenceDiagram
    participant client
    participant api
    participant database
    participant worker
    participant fcm
    client->>api: POST /api/notifications/test
    api->>database: 알림과 Outbox, producer traceparent 저장
    database-->>api: 단일 트랜잭션 커밋
    api-->>client: 202 Accepted
    worker->>database: 처리 대상 Outbox 조회 후 조회 트랜잭션 종료
    database-->>worker: 이벤트 목록
    worker->>database: 이벤트별 새 트랜잭션에서 알림 상태 확인
    worker->>fcm: 푸시 전송
    fcm-->>worker: 전송 결과
    worker->>database: 성공 또는 재시도 또는 영구 실패 상태 저장
```

워커는 전송을 시도할 때마다 새 Trace를 만들고, 이벤트에 저장된 `traceparent`를 Span Link로 연결합니다. 이를 통해 재시도한 작업에서도 최초 요청을 찾을 수 있습니다. FCM 전송 후 DB 커밋에 실패하면 같은 알림이 다시 전송될 수 있습니다.

[Outbox 설계와 대안](https://github.com/Beach-complex/beach_complex_docs/blob/main/adr/ADR-008-outbox-rdb-task-queue.md) · [전송·재시도 테스트](./src/test/java/com/beachcheck/outbox/service/OutboxEventDispatcherTest.java) · [Trace 연결 테스트](./src/test/java/com/beachcheck/outbox/tracing/OutboxTracingIntegrationTest.java)

### 로그와 Trace 조회

Grafana에서 로그의 `traceId`를 누르면 해당 요청의 Trace를 볼 수 있습니다. Trace에서도 같은 요청의 로그를 조회할 수 있도록 Loki와 Tempo 데이터소스를 연결했습니다.

로그 수집은 Alloy가 맡습니다. 앱이 표준 출력으로 남긴 로그를 Docker 파일에서 읽어 Loki로 보내므로, 앱에 로그 전송 코드를 넣거나 Docker socket을 열 필요가 없습니다. 로그 파일의 회전 한도를 넘기거나 저장 볼륨에 장애가 생기면 로그가 유실될 수 있습니다.

JWT·FCM 토큰·SQL 바인딩 값은 Trace에 기록하지 않습니다. `traceId`는 로그 본문에만 남기고 Loki 인덱스 라벨에서는 제외했습니다.

[Loki 선택 배경](https://github.com/Beach-complex/beach_complex_docs/blob/main/adr/ADR-014-logging-storage-backend.md) · [Alloy 선택 배경](https://github.com/Beach-complex/beach_complex_docs/blob/main/adr/ADR-015-log-collector.md) · [Trace 수집 계약](./docs/TRACING_CONTRACT.md)

## 문제 해결

### Outbox 트랜잭션 간 잠금 충돌

폴링 트랜잭션이 잡은 행 잠금 때문에 전송 트랜잭션의 갱신이 멈추는 문제가 있었습니다. 폴링 루프에 `NOT_SUPPORTED`를 적용하고, 조회 트랜잭션이 끝난 뒤 이벤트별 전송 트랜잭션을 시작하도록 바꿨습니다. 조회 메서드의 read-only 설정과 쓰기 잠금이 충돌하는 문제도 함께 수정했습니다.

통합 테스트에서는 준비 데이터를 먼저 커밋한 뒤 전송과 상태 변경을 확인합니다. 이미 전송한 알림을 건너뛰는 경우도 검사합니다.

[재현·원인·수정 기록](https://github.com/Beach-complex/beach_complex_docs/blob/main/backend/troubleshooting/outbox-integration-requires-new-lock-readOnly-deadlock.md) · [통합 테스트](./src/test/java/com/beachcheck/outbox/integration/OutboxPublisherIntegrationTest.java)

### Docker 로그 분할·회전 처리

긴 JSON 로그가 여러 Docker 레코드로 나뉘고, Alloy가 멈춘 동안 로그 파일이 회전하면서 일부 기록이 누락되는 문제가 있었습니다.

Alloy가 컨테이너와 출력 스트림별로 로그 조각을 합치도록 수정했습니다. 파일 지문과 읽기 위치도 저장해 재시작 후 이어 읽습니다. CI에서는 출력이 섞인 40KB JSON, 수집 중지 중 회전한 로그 110건, 파일 경계를 넘은 90KB JSON이 원문대로 복구되는지 확인합니다.

[CI 검증 결과](./assets/reports/log-pipeline-20261001.json) · [검증 스크립트](./deploy/observability/scripts/verify-log-pipeline.py) · [운영 제약](./deploy/observability/README.md#운영-제약)

그 밖의 기록: [Docker Engine과 Testcontainers의 API 버전 불일치 해결](https://github.com/Beach-complex/beach_complex_docs/blob/main/backend/troubleshooting/troubleshooting-testcontainers-docker-api-version-400.md)

## 품질과 배포

`main`·`develop` 대상 PR과 push에서 [CI](./.github/workflows/ci.yml)를 실행합니다.

| 검증 대상 | 검사 내용 |
| --- | --- |
| 백엔드 | UTF-8, 컴파일·Checkstyle·Spotless, 단위·통합 테스트, 빌드, JaCoCo |
| 관측 환경 | Terraform·cloud-init·Compose 설정, 합성 로그·Trace 전송, 회전·재시작·Loki 장애 복구 |

DB 쿼리와 API 테스트는 Testcontainers의 PostgreSQL/PostGIS에서 실행합니다. Trace 테스트에서는 [SQL 바인딩 값이 수집되지 않는지](./src/test/java/com/beachcheck/global/tracing/JdbcTracingIntegrationTest.java), [외부 호출까지 Trace가 이어지는지](./src/test/java/com/beachcheck/external/congestion/CongestionClientTracingIntegrationTest.java) 검사합니다.

커버리지 배지는 2026-10-01 커밋 `af6dceda` 기준이며 자동 갱신되지 않습니다. 라인 커버리지는 89.8% (1,617 / 1,801)입니다. [CI 결과](https://github.com/Beach-complex/Beach_complex/actions/runs/36867196523) · [JaCoCo 보고서](./assets/reports/jacoco-20261001.xml)

관측 스택은 [Prometheus·Tempo·Grafana의 EC2 실행과 연동](https://github.com/Beach-complex/Beach_complex/pull/238)을 확인했습니다. [Loki·Alloy 로그 파이프라인](https://github.com/Beach-complex/Beach_complex/pull/264)은 로컬 Docker에서 합성 로그와 Trace로 테스트했습니다.

- **비밀정보 검사:** 설치한 로컬 [Git 훅](./.githooks/pre-commit)이 [Gitleaks](./scripts/secret-scan.sh)로 스테이징된 추가 줄을 검사합니다.
- **배포:** `main`의 백엔드 관련 파일이 바뀌면 이미지를 빌드해 GHCR에 올리고, SSH로 EC2의 Compose 서비스를 갱신합니다. [CD 수동 실행](./.github/workflows/backend-cd.yml)도 지원합니다.

## 로컬 실행

JDK 21, Docker Compose, Git, OpenSSL이 필요합니다. 아래 Bash 명령은 이메일·푸시 발송을 끈 상태로 API를 실행합니다.

```bash
git clone https://github.com/Beach-complex/Beach_complex.git
cd Beach_complex
docker compose up -d --wait postgres redis

export SPRING_PROFILES_ACTIVE=dev
export APP_FIREBASE_ENABLED=false
export APP_OUTBOX_POLLING_ENABLED=false
export APP_MAIL_ENABLED=false
export MANAGEMENT_HEALTH_MAIL_ENABLED=false
export CONGESTION_SIGV4_ENABLED=false
export JWT_SECRET=$(openssl rand -hex 32)

./gradlew bootRun
```

DB 마이그레이션과 초기 데이터 입력은 앱 시작 시 Flyway가 처리합니다. 외부 혼잡도 API를 연결하지 않으면 30분마다 실행되는 갱신 작업에서 연결 실패 로그가 남을 수 있습니다.

[Swagger UI](http://localhost:8080/swagger-ui.html) · [Health](http://localhost:8081/actuator/health)

```bash
# 해운대 좌표 기준 반경 5km 검색
curl 'http://localhost:8080/api/beaches?lat=35.1587&lon=129.1599&radiusKm=5'

# Docker 실행 상태에서 테스트·빌드·커버리지 확인
./gradlew test assemble jacocoTestReport
```

PowerShell 실행 방법, JWT 키 변경 시 주의사항, Redis 실행 이유와 외부 서비스 설정은 [로컬 개발 가이드](./docs/LOCAL_DEVELOPMENT.md)를 참고하세요.

## 프로젝트 구조

```text
Beach_complex/
├── src/main/java/com/beachcheck/
│   ├── auth/            # JWT, 이메일 인증·발송
│   ├── beach/           # 검색, 시설·상태, 혼잡도 갱신 스케줄러
│   ├── external/        # 혼잡도 API, AWS SigV4
│   ├── global/          # 보안, 캐시, 예외, 로그·Trace 설정
│   ├── notification/    # 알림 생성·설정
│   ├── outbox/          # 이벤트 저장·폴링·FCM 전송
│   ├── reservation/     # 방문 예약
│   └── user/            # 사용자, 즐겨찾기
├── src/main/resources/
│   ├── db/migration/    # Flyway 마이그레이션
│   └── application*.yml
├── src/test/            # 단위·통합 테스트, 공통 fixture
├── deploy/
│   └── observability/   # Terraform, Compose, 관측 파이프라인 검증
├── docs/               # 로컬 개발 가이드, Trace 공통 계약
├── assets/             # README 구성도·CI 검증 기록
├── .github/workflows/   # CI, 컨테이너 빌드·배포
└── docker-compose.yml   # 로컬 PostgreSQL/PostGIS, Redis
```

## 문서

이 저장소에는 공개용 개발 가이드와 일부 기술 문서를 두고 있습니다. 상세 설계와 내부 협업·운영 기록은 비공개 문서 저장소에서 관리합니다. 본문에 연결된 ADR과 문제 해결 기록도 해당 저장소의 접근 권한이 필요합니다.

- [로컬 개발 가이드](./docs/LOCAL_DEVELOPMENT.md)
- [문서 저장소 · 비공개](https://github.com/Beach-complex/beach_complex_docs) · [ADR 목록 · 비공개](https://github.com/Beach-complex/beach_complex_docs/tree/main/adr)
- [문제 해결 기록 · 비공개](https://github.com/Beach-complex/beach_complex_docs/tree/main/backend/troubleshooting)
- [관측 환경 가이드](./deploy/observability/README.md) · [Trace 공통 계약](./docs/TRACING_CONTRACT.md)
- [프론트엔드](https://github.com/Beach-complex/beach_complex_client)

## 팀 & 역할

| 역할 | 이름 | GitHub |
| --- | --- | --- |
| Backend / Infra | 박재홍 | [@PHJ2000](https://github.com/PHJ2000) |
| Backend / Infra | 박건우 | [@geonusp](https://github.com/geonusp) |
| Frontend / PM | 정도경 | [@DoGyeong888](https://github.com/DoGyeong888) |

## 라이선스

[MIT License](./LICENSE)

### 인증 이메일 작업 복구

인증 토큰과 `email_jobs`는 같은 DB 트랜잭션에서 저장한다. 워커는 시작 시와 기본 1초 주기로
`PENDING`, 재시도 시각이 지난 `RETRY_WAIT`, lease가 지난 `SENDING`을 조회한다.
`FOR UPDATE SKIP LOCKED`로 선점·시도 횟수 증가·시도 이력 생성을 커밋한 다음 SMTP를 호출한다.
공용 스케줄러는 실행 요청만 전달하고, 기존 `emailTaskExecutor`에서 인스턴스당 최대 3개의 소비 작업을
실행한다. 주기 실행은 소비 작업 하나만 요청하며, 실제 발송할 작업을 발견했을 때 나머지 슬롯을 채운다.
따라서 유휴 상태에서는 매 주기마다 소비 작업 3개가 빈 큐를 조회하지 않는다.
실행기 대기 중에는 DB 작업을 선점하지 않는다. 소비 작업 하나는 무효 작업 정리와 발송을
합쳐 최대 20건 처리하며, 만료 작업을 정리한 뒤에도 배치 한도 안에서 다음 후보를 계속 조회한다.
완료 기록은 현재 시도 번호가 일치할 때만 허용한다. 상태는
`PENDING → SENDING → SUCCEEDED / RETRY_WAIT / FAILED`이며 `email_job_attempts`에 각 시도를 남긴다.

최대 4회는 최초 시도를 포함하며, 재시도 간격은 5→10→20초다. 네트워크/SMTP 4xx 오류는 재시도하고,
SMTP 5xx·인증·주소/메시지 오류는 즉시 실패한다. 코드를 알 수 없는 `MailSendException`은 한도 내에서
재시도한다. 자동 재시도는 같은 토큰/작업을 사용해 3분 수동 재전송 제한을 갱신하거나 우회하지 않는다.
만료·사용·재전송으로 무효화된 토큰의 대기 작업은 발송 전에 `FAILED`로 종료한다.
선점 이후 토큰이 무효화되는 경쟁에서는 이미 시작한 SMTP 전송을 취소할 수 없다.

SMTP가 수락한 뒤 DB 성공 기록 전에 종료되면 발송 여부를 확정할 수 없다.
해당 시도는 복구 시 `UNKNOWN`으로 남고 다시 보낼 수 있으므로 **exactly-once를 보장하지 않는다**.
유효한 lease 안에서는 한 워커만 작업을 가져가지만, SMTP 지연이나 JVM 중단이 lease를 넘으면
이전 워커와 복구 워커의 전송이 겹칠 수도 있다. 작업 ID·시도 이력·완료 조건부 갱신은 중복을 줄이고
조사를 돕지만 수신자의 중복 수신을 제거하지 못한다. `SUCCEEDED`는 SMTP 수락이며 받은편지함 도착이 아니다.
4번째 시도가 불명 상태로 끝나면 추가 발송 없이 `FAILED`/`ATTEMPTS_EXHAUSTED_UNKNOWN`으로 남긴다.

`APP_MAIL_ENABLED=false`이면 기존 개발용 Noop 동작을 유지한다. 인증 토큰은 생성하지만
메일 작업을 새로 만들거나 기존 작업을 처리하지 않으며 암호화 키도 요구하지 않는다.
실제 메일을 활성화하는 인스턴스에는 `APP_EMAIL_JOBS_PAYLOAD_KEY`를 동일하게 설정해야 한다.
`openssl rand -base64 32`로 생성한 32바이트 키를 Secret으로 보관하며 미설정/잘못된 길이는 시작을 차단한다.
인증 링크는 AES-GCM으로 암호화해 저장하고 종료된 작업에서는 삭제한다.
단, 복호화 실패는 SMTP에 도달하지 않은 `FAILED/PAYLOAD_DECRYPT_FAILED`로 구분하고 암호문을 보존한다.
자동 재발송은 하지 않으며 키·데이터를 확인한 뒤 별도 복구 판단이 필요하다. 이 처리 시도도 DB 횟수에 남는다.
키를 변경하기 전에는
미완료 작업을 처리하거나 기존 키로 복호화 후 새 키로 재암호화해야 한다. 수신 주소와 시도 이력은 DB에
남으므로 접근을 제한하며, 이 변경에는 자동 보존기간 삭제 작업을 포함하지 않는다.
주소·링크·제목·본문·SMTP 예외 원문은 로그/Trace에 기록하지 않는다.
작업 ID는 로그와 DB에만 남기고, 각 시도의 새 Trace는 저장된 생산 Trace에 Span Link로 연결한다.

설정은 [배포 환경 예시](deploy/backend-ec2.env.example)를 참고한다. 기본 lease는 300초(최소 60초),
SMTP 연결/읽기/쓰기 timeout은 각각 5초다. timeout은 전체 전송 시간의 엄밀한 상한은 아니다.
`APP_EMAIL_JOBS_POLLING_ENABLED=false`는 작업 생성을 유지하면서 시작 복구와 주기 실행만 끈다.
두 설정 모두 적용 전에 이미 시작된 전송을 취소하지 않는다. 롤백 전 워커를 정지하고 진행 중 요청을 종료한 뒤 미완료 작업을 확인한다.
구버전은 DB 작업을 처리하지 못하며, 기존 메모리 작업을 새 테이블로 자동 이전하지 않는다.
배포 전 구버전 요청을 drain해야 하며 운영 배포와 실제 SMTP 발송 검증은 PR 테스트 범위와 별개다.
