# 🏖 Beach Complex

공공데이터를 기반으로 전국 해수욕장 정보를 제공하는 서비스입니다.  
[V1 목표: 운영 가능한 백엔드(데이터 일관성/조회 성능/재현 가능한 실행 환경)를 갖춘 API 제공]

> Status: Local runnable / CI: No / Deploy: No

---

## Quick Links
- Swagger (Local): http://localhost:8080/swagger-ui.html
- API Base URL (Local): http://localhost:8080
- Docs: docs/README.md
- ERD: TBD
- Architecture: TBD
- CI: N/A

---

## Problem
해수욕장 정보는 여러 공공데이터에 흩어져 있어 사용자가 **지금 어디가 붐비는지 / 날씨는 어떤지 / 내 위치에서 얼마나 가까운지**를 한 번에 확인하기 어렵습니다.  
시즌 트래픽을 고려하면 반복 조회로 DB 부하가 커질 수 있어, 운영 관점(데이터 정합성/성능/재현성)을 함께 설계해야 합니다.

---

## Goals (V1)
- 공공데이터 기반 해수욕장 정보를 API로 제공한다.
- 날씨 정보를 조회/제공한다. (데이터 소스: AI congestion service `/congestion/current`)
- 좌표 기반 거리/근처 검색을 제공한다. (PostGIS 활용)
- DB 스키마 변경을 안전하게 관리한다. (Flyway)
- [인증/인가] 기본 로그인/회원가입을 제공한다. (옵션)
- 즐겨찾기/예약 API 제공

### Success Criteria (V1)
- TBD (정의 필요)
- TBD (정의 필요)
- TBD (정의 필요)

---

## Out of Scope (V1)
- 결제
- ML 기반 추천
- 소셜 로그인
- TBD

---

## Scope Summary (V1)
### Implemented
- [x] 해수욕장 목록/검색/반경 조회 API (PostGIS)
- [x] 해수욕장 시설/컨디션 조회 + 스케줄러 수집
- [x] JWT 로그인/회원가입 + 즐겨찾기/예약 API

### Planned
- [ ] Redis 캐시 전환 (ADR-004)
- [ ] 조건 실시간 스트리밍(SSE) 제공
- [ ] CI/CD 파이프라인

---

## Architecture
### Components
- Frontend: React (`front/`)
- Backend: Spring Boot API
- Database: PostgreSQL (+ PostGIS)
- Migration: Flyway
- Cache: Redis (Planned)
- Data Ingestion: Scheduler (Applied)
- CI: None

### Request Flow (Read)
1. Client -> API
2. API -> Caffeine cache (hit) -> Response
3. API -> DB query (miss) -> Caffeine set/TTL -> Response

> Cache Strategy (if applied/planned):
- Cache 대상: beach list, beach facilities, condition snapshots (Caffeine)
- TTL: 10m
- Invalidation: TTL only

### Data Flow (Ingestion/Update)
1. AI congestion service (`/congestion/current`) -> BeachConditionScheduler
2. Ingestion -> DB upsert/update
3. (Optional) Cache invalidate/refresh -> TTL only

---

## Key Features
> 아래 기능은 무엇을 제공하는지 뿐 아니라 백엔드 관점에서 무엇을 보장하는지를 함께 적습니다.

- 해수욕장 목록/상세 조회 API  
  - 보장: 필터(q/tag)·반경 검색(lat/lon/radiusKm), 페이징/정렬 없음, ProblemDetail 에러 응답, Caffeine 캐시(beachSummaries)
- 날씨 조회 API  
  - 보장: 외부 congestion API 실패 시 스킵(스케줄러 로그) + 기존 데이터 유지, conditionSnapshots 캐시(10m)
- 좌표 기반 근처 해수욕장 검색(PostGIS)  
  - 보장: 반경 검색(lat/lon/radiusKm), ST_DWithin/ST_Distance(geography, meters), GIST 인덱스(beaches.location)
- DB 스키마 변경 이력 관리(Flyway)  
  - 보장: Flyway 마이그레이션(V1~V8)으로 환경 간 스키마 일치, 앱 시작 시 자동 적용
- 예외 처리/에러 응답 규격  
  - 보장: ProblemDetail 포맷, ErrorCode/ApiException code+details, 검증 실패 시 field 에러 맵 반환
- (Optional) 로그인/회원가입  
  - 보장: JWT access/refresh(1h/30d), 역할 USER/ADMIN, `/api/auth/refresh` 재발급
- (Optional) 축제 조회/예약/캘린더 등록  
  - 보장: 연동 범위는 해수욕장 예약(`/api/beaches/{id}/reservations`), 중복 예약 방지(userId+beachId+reservedAt), 과거 시간 요청 차단

---

## Tech Decisions (Why)
### Spring Boot
- Reason: 팀의 Java/Spring 경험과 REST API 중심 요구사항에 적합 (ADR-001)
- Alternatives: Node.js/NestJS, Django
- Trade-offs: JVM 리소스/스타트업 비용, 프레임워크 러닝커브

### PostgreSQL + PostGIS
- Reason: 반경/거리 기반 검색을 위한 공간 질의와 관계형 모델에 적합
- Alternatives: MySQL + Spatial, MongoDB Geo
- Trade-offs: PostGIS 확장/공간 인덱스 운영 필요

### Flyway
- Reason: 스키마 변경 이력 관리와 환경 간 정합성 보장
- Alternatives: Liquibase
- Trade-offs: 마이그레이션 작성/롤백 운영 부담

### Redis (Planned)
- Reason: L2 캐시 전환 대비(현재는 Caffeine) (ADR-004)
- Alternatives: Caffeine-only 유지
- Trade-offs: 운영 비용/네트워크 지연/관리 복잡도

### QueryDSL / Security / JWT / Testing(Mockito 등)
- Reason: Security+JWT로 무상태 인증, 테스트는 JUnit5+Mockito 기반; QueryDSL은 미적용(TBD)
- Alternatives: 세션 기반 인증/OAuth, 통합 테스트 중심
- Trade-offs: 토큰 폐기/갱신 관리 필요, 모킹 유지보수 비용

---

## Project Structure
```text
Beach_complex/
 src/main/java/com/beachcheck/
    config/
    controller/
    service/
    repository/
    domain/
    dto/
    exception/
    scheduler/
 src/main/resources/
    db/migration/
    application.yml
    application-dev.yml
 front/
 docs/
 docker-compose.yml
 README.md
```

---

## Getting Started (Local)

### Prerequisites

* JDK 21
* Node.js 20+
* Docker

### Environment Variables

설정 위치: application.yml + OS env override

필수 환경 변수:

* `SPRING_DATASOURCE_URL` = `jdbc:postgresql://localhost:5432/beach_complex`
* `SPRING_DATASOURCE_USERNAME` = `beach`
* `SPRING_DATASOURCE_PASSWORD` = `beach`
* `JWT_SECRET` = `your-256-bit-secret-key-here-change-in-production`
* `PUBLIC_DATA_API_KEY` = 미사용 (현재 코드에서 사용하지 않음)

### Run (Backend)

```bash
docker-compose up -d postgres redis
./gradlew bootRun
```

### DB Migration (Flyway)

* 적용 방식: 앱 시작 시 자동
* 파일 경로: `src/main/resources/db/migration`
* 확인 방법: `flyway_schema_history` 테이블 확인

### Run Tests

```bash
./gradlew test
```

### Run (Frontend)

```bash
cd front
npm install
npm run dev
```

### Local Links

* Frontend: [http://localhost:5173](http://localhost:5173)
* Backend: [http://localhost:8080](http://localhost:8080)
* Swagger: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### Sanity Check (Expected)

* [ ] Swagger 접속이 된다.
* [ ] `GET /api/beaches` 호출 시 200 응답을 받는다.
* [ ] `GET /api/beaches?lat=35.1587&lon=129.1599&radiusKm=10` 호출 시 200 응답을 받는다.

---

## Collaboration

* Branch strategy: `main` + `develop`, `feature/{issue}-{slug}`
* PR/Review: PR 1+ 리뷰 승인 후 병합, 제목은 `[type] 요약`
* Issue tracking: GitHub Issues

---

## License

MIT
