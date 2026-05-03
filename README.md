# Beach Complex

공공데이터를 바탕으로 전국 해수욕장 정보를 조회하고, 상태 정보, 즐겨찾기, 예약, 알림 기능까지 함께 제공하는 해수욕장 서비스 프로젝트입니다.  
Spring Boot 백엔드와 React 프론트엔드로 구성된 팀 프로젝트이며, 실제 코드 기준으로 인증, 조회, 예약, 알림 흐름이 연결되어 있습니다.

## 프로젝트 개요

- 프로젝트 성격: 팀 프로젝트
- 목적: 해수욕장 관련 공공데이터와 사용자 기능을 한 서비스 안에서 제공
- 주요 기술: Spring Boot, React, PostgreSQL(PostGIS), Flyway, Docker Compose
- 현재 구현 중심 흐름: 인증, 해수욕장 조회, 상태 정보 조회, 즐겨찾기, 예약, 알림

## 현재 상태

- 백엔드와 프론트엔드가 함께 구성된 저장소입니다.
- 인증, 해수욕장 조회, 즐겨찾기, 예약, 알림 관련 API와 화면 구성이 존재합니다.
- DB 마이그레이션은 Flyway로 관리합니다.
- 테스트와 CI 설정을 포함하며, 협업/운영 문서는 별도 docs 레포에서 관리합니다.

## 주요 기능

### 사용자 기능
- 회원가입, 로그인, JWT 기반 인증
- 사용자별 즐겨찾기 등록, 삭제, 토글, 조회

### 해수욕장 정보
- 해수욕장 목록 조회
- 해수욕장 상세 조회
- 해수욕장 시설 정보 조회
- 해수욕장 상태 정보 조회

### 예약 및 알림
- 예약 생성, 조회, 취소
- 알림 설정 및 FCM 토큰 등록
- 알림 테스트 API 제공

## 기술 스택

### Backend
- Java 21
- Spring Boot 3.3
- Spring Security
- Spring Validation
- Spring Data JPA
- Spring Data Redis
- Spring Retry
- Spring Mail
- JWT
- Swagger UI

### Frontend
- React 18
- TypeScript
- Vite
- Tailwind CSS
- Radix UI
- Firebase
- Leaflet

### Database / Infra
- PostgreSQL
- PostGIS
- Flyway
- Redis 설정
- Caffeine Cache
- Docker Compose

### Quality
- JUnit 5
- Testcontainers
- JaCoCo
- Checkstyle
- Spotless
- GitHub Actions

## 프로젝트 구조

```text
Beach_complex/
├── src/main/java/com/beachcheck/
│   ├── client/        # 외부 API 클라이언트
│   ├── config/        # 보안, 캐시, 애플리케이션 설정
│   ├── controller/    # REST API 엔드포인트
│   ├── db/            # DB 설정
│   ├── domain/        # 엔티티
│   ├── dto/           # 요청/응답 DTO
│   ├── exception/     # 예외 처리
│   ├── repository/    # 데이터 접근 계층
│   ├── scheduler/     # 스케줄링 작업
│   ├── security/      # 인증/인가
│   ├── service/       # 비즈니스 로직
│   └── util/          # 공용 유틸
├── src/test/java/com/beachcheck/
│   ├── integration/   # 통합 테스트
│   ├── service/       # 서비스 테스트
│   ├── client/        # 외부 연동 테스트
│   └── ...            # 도메인/DTO/유틸 테스트
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/
│   └── firebase-service-account.json.example
├── front/             # React 프론트엔드
├── docs/              # 문서 레포 진입점
├── .github/workflows/ # CI 및 자동화
├── docker-compose.yml
└── build.gradle
```

## API 영역

현재 코드 기준으로 아래 컨트롤러가 구성되어 있습니다.

- `AuthController`
- `BeachController`
- `BeachConditionController`
- `BeachFacilityController`
- `ReservationController`
- `UserFavoriteController`
- `NotificationController`

## 테스트와 협업

- 단위 테스트와 통합 테스트를 함께 운영합니다.
- Testcontainers를 사용해 데이터베이스 연동 테스트를 검증합니다.
- GitHub Actions에서 빌드, 테스트, 커버리지 리포트 업로드를 수행합니다.
- 협업 규칙, 기술 결정 기록, 트러블슈팅 문서는 `Beach-complex/docs` 레포에서 관리합니다.
- `.github/ISSUE_TEMPLATE`, `.github/PULL_REQUEST_TEMPLATE`, `.github/pull_request_template.md`로 이슈와 PR 양식을 관리합니다.

## 실행 방법

### 요구 사항
- JDK 21
- Node.js 20+
- Docker

### 1. 저장소 클론

```bash
git clone https://github.com/Beach-complex/Beach_complex.git
cd Beach_complex
```

### 2. 인프라 실행

```bash
docker-compose up -d postgres redis
```

### 3. 백엔드 실행

```bash
./gradlew bootRun
```

Windows PowerShell에서는 아래 명령을 사용합니다.

```powershell
.\gradlew.bat bootRun
```

### 4. 프론트엔드 실행

```bash
cd front
npm install
npm run dev
```

### 5. 접속 주소

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## 문서

- 프로젝트 문서 홈: [Beach-complex/docs](https://github.com/Beach-complex/docs)
- 로컬 문서 진입점: [`docs/README.md`](./docs/README.md)
- SSOT 문서: [docs/ssot.md](https://github.com/Beach-complex/docs/blob/main/ssot.md)
- ADR: [docs/adr](https://github.com/Beach-complex/docs/tree/main/adr)
- 트러블슈팅 문서: [docs/troubleshooting/docs-README.md](https://github.com/Beach-complex/docs/blob/main/troubleshooting/docs-README.md)
- 프론트엔드 문서: [`front/README.md`](./front/README.md)

## 팀

| 역할 | 이름 | GitHub |
| --- | --- | --- |
| Backend / Infra | 박재홍 | [@PHJ2000](https://github.com/PHJ2000) |
| Backend | 박건우 | [@GunwooPar](https://github.com/GunwooPar) |
| Frontend / PM | 정도경 | [@DoGyeong888](https://github.com/DoGyeong888) |

## 라이선스

이 프로젝트는 [MIT](./LICENSE) 라이선스를 따릅니다.
