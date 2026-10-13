# 로컬 개발 가이드

[README로 돌아가기](../README.md)

## 1. 준비

- JDK 21
- Docker Engine 또는 Docker Desktop과 Docker Compose
- Git

백엔드는 저장소의 Gradle Wrapper로 실행합니다. Node.js는 프론트엔드를 함께 개발할 때 필요합니다.

```bash
git clone https://github.com/Beach-complex/Beach_complex.git
cd Beach_complex
docker compose up -d --wait postgres redis
```

기본 DB 접속 정보는 `localhost:15432`, 데이터베이스 `beach_complex`, 사용자·비밀번호 `beach`입니다. Redis 포트는 `6379`입니다.

## 2. 외부 인증정보 없이 백엔드 실행

기본 설정은 Firebase와 메일 발송을 활성화합니다. 로컬에서 API를 먼저 확인하려면 아래처럼 비활성화해 실행하세요.

```bash
export SPRING_PROFILES_ACTIVE=dev
export APP_FIREBASE_ENABLED=false
export APP_OUTBOX_POLLING_ENABLED=false
export APP_MAIL_ENABLED=false
export MANAGEMENT_HEALTH_MAIL_ENABLED=false
export CONGESTION_SIGV4_ENABLED=false
export JWT_SECRET=local-development-only-change-this-secret-before-deploy

./gradlew bootRun
```

### Windows PowerShell

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:APP_FIREBASE_ENABLED = "false"
$env:APP_OUTBOX_POLLING_ENABLED = "false"
$env:APP_MAIL_ENABLED = "false"
$env:MANAGEMENT_HEALTH_MAIL_ENABLED = "false"
$env:CONGESTION_SIGV4_ENABLED = "false"
# 로컬 실행용 임시 키 생성
$env:JWT_SECRET = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")

.\gradlew.bat bootRun
```

앱 시작 시 Flyway가 마이그레이션과 초기 데이터를 적용합니다. 위 설정에서는 이메일과 푸시가 실제로 발송되지 않습니다. 혼잡도 스케줄러는 계속 실행되므로 별도 API가 없으면 30분 주기의 갱신 시 연결 실패 로그가 발생할 수 있습니다. `CONGESTION_SIGV4_ENABLED=false`는 서명만 끄며 혼잡도 API를 제공하거나 스케줄러를 중지하지 않습니다.

## 3. 동작 확인

| 용도 | 기본 주소 |
| --- | --- |
| Backend API | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/api/docs> |
| Health | <http://localhost:8081/actuator/health> |

```bash
# 해수욕장 목록
curl http://localhost:8080/api/beaches

# 해운대 좌표 기준 반경 5km 검색
curl 'http://localhost:8080/api/beaches?lat=35.1587&lon=129.1599&radiusKm=5'
```

프론트엔드를 연결할 때 현재 Spring Security의 CORS 허용 출처는 `http://localhost:3000`과 `http://localhost:8080`입니다. 다른 포트를 사용한다면 [SecurityConfig](../src/main/java/com/beachcheck/global/security/SecurityConfig.java)의 설정을 맞춰야 합니다.

## 4. 외부 서비스 연결

| 연동 | 설정 |
| --- | --- |
| PostgreSQL | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| 이메일 | `APP_MAIL_ENABLED=true`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`; 메일 상태 점검 시 `MANAGEMENT_HEALTH_MAIL_ENABLED=true` |
| 이메일 인증 링크 | `APP_EMAIL_VERIFICATION_BASE_URL` — 기본값 `http://localhost:8080/api/auth/verify-email` |
| Firebase FCM | `APP_FIREBASE_ENABLED=true`, `APP_OUTBOX_POLLING_ENABLED=true`, 서비스 계정 설정 |
| Firebase 서비스 계정 | `APP_FIREBASE_CREDENTIALS_JSON_BASE64` 또는 `APP_FIREBASE_CREDENTIALS_PATH` |
| 혼잡도 API | `CONGESTION_BASE_URL`; AWS 연동 시 `CONGESTION_SIGV4_ENABLED=true`, `AWS_REGION`, 실행 환경의 AWS 자격증명 또는 `AWS_PROFILE` |
| Trace 전송 | `MANAGEMENT_OTLP_TRACING_ENDPOINT` — 예: `http://localhost:4318/v1/traces` |
| Trace 샘플링 | `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` — 기본값 `0.1` |

Trace endpoint를 지정하지 않으면 수집기 없이 실행할 수 있습니다. 외부 연동 값은 실행 환경에 주입하며, 로컬 예제의 JWT 키는 배포 환경에서 별도 비밀값으로 교체합니다. 전체 기본값은 [application.yml](../src/main/resources/application.yml), 배포 변수 예시는 [backend-ec2.env.example](../deploy/backend-ec2.env.example)에 있습니다.

## 5. 테스트와 Git 훅

통합 테스트에는 실행 중인 Docker 데몬이 필요합니다.

```bash
./gradlew compileJava checkstyleMain spotlessCheck
./gradlew test assemble jacocoTestReport

# 선택: 로컬 Git 훅 설치
./gradlew installGitHooks
```

테스트 결과는 `build/reports/tests/test/index.html`, 커버리지는 `build/reports/jacoco/test/html/index.html`에 생성됩니다. 관측 테스트 도구와 실행 조건은 [검증 가이드](../deploy/observability/README.md#로컬-검증)를 참고하세요.

## 6. API 참고

실행 후 [Swagger UI](http://localhost:8080/swagger-ui.html)에서 요청·응답 스키마를 확인할 수 있습니다. 인증이 필요한 API에는 `Authorization: Bearer <access-token>` 헤더를 보냅니다. 반경 검색은 `lat`, `lon`, `radiusKm`을 모두 전달해야 합니다.

| 영역 | 대표 경로 | 인증 |
| --- | --- | --- |
| 회원가입·로그인·토큰 갱신 | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh` | 불필요 |
| 내 정보·로그아웃 | `GET /api/auth/me`, `POST /api/auth/logout` | JWT |
| 이메일 인증 | `GET /api/auth/verify-email`, `POST /api/auth/verify-email/confirm` | 인증 링크의 토큰 |
| 해수욕장 검색 | `GET /api/beaches` | 불필요 |
| 시설 조회 | `GET /api/beaches/{beachId}/facilities` | 불필요 |
| 최근 상태 조회 | `GET /api/beaches/{beachId}/conditions/recent` | 불필요 |
| 즐겨찾기 | `GET /api/favorites`, `POST /api/favorites/{beachId}` | JWT |
| 예약 생성·목록 | `POST /api/beaches/{beachId}/reservations`, `GET /api/beaches/reservations` | JWT |
| 알림 설정 저장 | `POST /api/notifications/fcm-token`, `PUT /api/notifications/settings` | JWT |
| 테스트 푸시 발송 | `POST /api/notifications/test` | JWT |
