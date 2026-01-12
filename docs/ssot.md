# Beach Complex - Single Source of Truth (SSOT)

> **프로젝트의 핵심 정보를 한 곳에 모아둔 문서입니다.**
>
> 이 문서는 마일스톤, 협업 규칙, 온보딩 가이드 등 프로젝트의 중요한 정보를 통합하여 제공합니다.

## 목차
- [1. 프로젝트 마일스톤](#1-프로젝트-마일스톤)
- [핵심 API (Core APIs)](#핵심-api-core-apis)
- [V1 8주 로드맵](#v1-8주-로드맵-2주-스프린트-x-4)
- [2. 협업 규칙](#2-협업-규칙)
- [3. 온보딩 가이드](#3-온보딩-가이드)
- [4. 주요 문서 링크](#4-주요-문서-링크)

---

## 1. 프로젝트 마일스톤

### 프로젝트 현황
**Current Phase: V1 Development - 8-Week Roadmap**

- **2025.09 ~ 2025.10**: MVP(최소 기능 제품) 개발 완료
- **2025.11 ~ 현재**: V1 아키텍처 고도화 및 8주 로드맵 진행 중
- **목표**: 2월 말까지 운영 가능한 시스템 구축 (테스트 자동화 + 성능 최적화 + 관측 체계)

### Definition of Done (DoD)

모든 Jira 티켓은 다음 조건을 충족해야 완료로 간주됩니다:

- [ ] PR 머지 + CI 통과
- [ ] PR/커밋/브랜치에 Jira 키 포함
- [ ] 필요 시 문서 업데이트 (`docs/...`)
- [ ] 작업자 `docs/contrib/<name>/YYYY-MM-DD.md` 작성 + `index.md`에 1줄 추가

---

## 핵심 API (Core APIs)

> **핵심 = 트래픽/성능/캐시/DB 병목이 잘 드러나는 것**
>
> 테스트, 성능 측정, 최적화의 기준이 되는 4개 API

### Core-1: 리스트 조회 (검색/필터/정렬)
- **엔드포인트**: `GET /api/beaches?query=&region=&sort=`
- **이유**: DB 쿼리/인덱스/페이징/N+1이 터지는 지점
- **주요 포인트**:
  - 검색 쿼리 최적화
  - 인덱스 전략
  - 페이징 성능

### Core-2: 상세 조회 (+연관 데이터)
- **엔드포인트**: `GET /api/beaches/{id}`
- **응답 포함**: 리뷰 요약, 평점, 이미지, 날씨 정보
- **이유**: 조인/캐시/정합성 포인트 생성
- **주요 포인트**:
  - N+1 문제 (연관 데이터 로딩)
  - 캐시 전략 (Redis)
  - 데이터 정합성

### Core-3: 쓰기 (리뷰 작성/수정)
- **엔드포인트**: `POST /api/beaches/{id}/reviews`
- **이유**: 트랜잭션/락/동시성/무결성, 이후 조회 성능에도 영향
- **주요 포인트**:
  - 트랜잭션 관리
  - 동시성 제어
  - 작성 후 조회 결과 반영

### Core-4: 예약 생성 (경쟁 조건)
- **엔드포인트**: `POST /api/beaches/{beachId}/reservations`
- **입력**: `reservedAtUtc`(ISO-8601 UTC), `eventId`(optional, max 128)
- **출력**: `reservationId`, `status(CONFIRMED|REJECTED)`, `reservedAtUtc`, `beachId`, `eventId`,
`createdAtUtc`
- **이유**: 예약은 시간 제약/중복 방지 검증이 필요한 쓰기 API이므로 실패 케이스를 명확히 정의한다.
- **주요 포인트**:
  - 과거 시간 예약 금지
  - 시간 포맷 검증(UTC ISO-8601)
  - 동일 사용자/해변/시간 중복 예약 방지
  - 해수욕장/사용자 존재 확인

---

### 이미 완료된 작업 (MVP 단계)
- [x] **핵심 도메인**: 해수욕장(Beach), 날씨(BeachCondition) CRUD 및 기본 API 구현
- [x] **DB 고도화**: Flyway 도입을 통한 스키마 형상 관리 및 마이그레이션 자동화
- [x] **인증/인가**: Spring Security + JWT 기반 로그인/회원가입 구현
- [x] **공간 검색**: PostGIS를 활용한 위치 기반 검색 기능

---

## 2. 협업 규칙

### 2.1 Git 브랜치 전략
- **기본 브랜치**:
  - `main`: 배포 가능한 안정 버전
  - `develop`: 기능 개발 통합 브랜치 (필요 시)
- **기능 브랜치 네이밍**:
  - **형식**: `<type>/PB-번호-짧은-설명`
  - **Type 종류**: `feature`, `fix`, `docs`, `refactor`, `chore`, `test`
  - **예시**:
    - `feature/PB-026-add-ssot-contrib`
    - `docs/PB-026-add-ssot-contrib`
    - `fix/PB-024-redis-cache-bug`
    - `refactor/PB-023-service-layer`
- **머지 원칙**:
  - 기능 브랜치 -> `main` (또는 `develop`) PR 후 리뷰 최소 1명 승인
  - 승인 없이는 병합하지 않습니다
  - PR 제목에 Jira 이슈 키를 포함합니다

### 2.2 커밋 메시지 규칙
**포맷**: `<type>: <내용>`

**타입 종류**:
- `feat`: 기능 추가
- `fix`: 버그 수정
- `chore`: 빌드/설정/기타
- `docs`: 문서 수정, 주석 추가
- `refactor`: 코드 리팩토링
- `test`: 테스트 추가/수정
- `style`: 코드 포맷팅
- `ci`: CI 관련 변경사항
- `cd`: CD 관련 변경사항

**예시**:
```
feat: 로그인 실패 시 에러 메시지 표시
fix: 회원가입 시 닉네임 중복 검사 수정
docs: PB-26 SSOT 문서 작성 및 contrib 템플릿 추가
ci: PB-27 GitHub Actions 워크플로우 추가
cd: AWS 배포 스크립트 작성
```

**Jira 이슈 키 포함 (권장)**:
- 커밋 메시지에 Jira 이슈 키를 포함하면 자동으로 연동됩니다
- 예: `docs: PB-26 SSOT 문서 작성`

### 2.3 PR 규칙
- **PR 제목**: `[타입] PB-번호 작업 요약`
  - Jira 이슈 키를 반드시 포함합니다
  - **예시**:
    - `[docs] PB-26 개발 규칙 문서(SSOT/Contrib) 반영`
    - `[feat] PB-24 Redis 캐싱 레이어 구현`
    - `[fix] PB-23 로그인 실패 시 500 에러 수정`
- **PR 내용에 포함**:
  - 작업 내용 요약
  - 테스트 여부
  - 관련 Jira 이슈 링크
- **리뷰 원칙**:
  - 최소 1명 이상 승인 후 머지
  - 팀원 간 상호 피드백을 통해 코드 품질 향상
  - 현재까지 약 70+ Commits, 22+ Pull Requests 진행

### 2.4 이슈 관리

#### Jira 연동
- 프로젝트는 **Jira와 GitHub을 연동**하여 사용합니다(Github issue -> Jira issue)
- Jira 이슈 키를 기반으로 작업을 관리합니다
- GitHub Actions를 통해 자동으로 Jira와 동기화됩니다

#### 이슈 제목 규칙
- **형식**: `PB-번호 작업 설명`
- **예시**:
  - `PB-26 개발 규칙 문서(SSOT/Contrib) 반영`
  - `PB-25 데모 시나리오 4개 정의(기획)`
  - `PB-24 Redis 캐싱 레이어 구현`

#### 브랜치 네이밍과 이슈 연동
- 브랜치명에 Jira 이슈 키를 포함합니다
- **형식**: `feature/PB-번호-짧은-설명`
- **예시**:
  - `feature/PB-026-add-ssot-contrib`
  - `docs/PB-026-add-ssot-contrib`
  - `fix/PB-024-redis-cache-bug`

#### 라벨 활용
- `docs`: 문서 작업
- `feature`: 새로운 기능
- `bug`: 버그 수정
- `enhancement`: 기능 개선
- `refactor`: 리팩토링
- `infra`: 인프라/DevOps 작업

#### 작업 플로우
1. **Jira에서 이슈 생성** 또는 할당받기
2. **브랜치 생성** (Jira 이슈 키 포함)
3. **작업 진행** (커밋 메시지에 이슈 키 포함 권장)
4. **PR 생성** (PR 제목에 이슈 키 포함)
5. **코드 리뷰** (최소 1명 승인)
6. **승인 후 머지**
7. **Jira 이슈 자동 업데이트** (GitHub Actions를 통해)

### 2.5 코드 주석 목적 및 규칙
- 목적: 코드의 의도와 복잡한 로직 설명
- 규칙:
  - **Why**: 해당 코드가 왜 필요한지 설명
  - **Policy**: 비즈니스 규칙, 보안, 트랜잭션, 캐싱 등 로직의 실행 정책
  - **Contract(Input/Output)**: 입력값과 출력값에 대한 사전 조건 및 후 조건 명시
  - **Todo**: 향후 개선 사항이나 리팩토링 예정 사항 기록(Intellij TODO 태그 활용 권장)

예시 코드
``` java
/**
* Why: 회원 정보 수정 시 이메일 변경은 별도 인증 프로세스가 필요하므로, 이 메서드에서는 닉네임과 프로필 이미지 수정만 허용한다.
* Policy: 트랜잭션 내에서 처리하며, 실패 시 전체 롤백한다.
* Contract(Input): nickname은 욕설 필터링을 통과해야 한다. (FilterService 참조)
* Contract(Output): 수정된 User 객체를 반환하되, password 필드는 마스킹 처리된다.
* Todo: 현재 동기(Synchronous) 방식인 프로필 변경 알림 메일 발송을 추후 Kafka 이벤트 발행(비동기)으로 변경하여 응답 속도를 개선할 것.
    */
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
    // 1. 유저 조회
    User user = userRepository.findById(userId)
    .orElseThrow(() -> new NotFoundException("User not found"));

// 2. 닉네임 검증 (Contract 이행)
filterService.validateNickname(request.getNickname());

// 3. 정보 업데이트
user.updateProfile(request.getNickname(), request.getProfileImageUrl());

// ... (이하 로직)
}
```

---

## 3. 온보딩 가이드

### 3.1 필수 준비 사항

#### 개발 환경 요구사항
- **JDK 21**
- **Node.js 20+**
- **Docker Desktop** (데이터베이스 실행용)
- **Git**
- **IDE**: IntelliJ IDEA (권장) 또는 VS Code

#### 설치 및 설정

1. **레포지토리 클론**
```bash
git clone https://github.com/PHJ2000/Beach_complex.git
cd Beach_complex
```

2. **Docker Compose로 데이터베이스 실행**
```bash
docker-compose up -d
```

3. **백엔드 실행**
```bash
./gradlew bootRun
```

4. **프론트엔드 실행**
```bash
cd front
npm install
npm run dev
```

5. **포맷 자동화**
```bash
./gradlew spotlessCheck
./gradlew spotlessApply
``` 

7. **브라우저에서 확인**
- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- API Docs (Swagger): http://localhost:8080/swagger-ui.html

### 3.2 첫 기여하기

#### Step 1: Jira 이슈 확인
- **Jira 보드**에서 작업할 이슈를 선택하거나 할당받습니다
- 이슈 키(예: PB-26)를 확인합니다
- GitHub Issues에도 자동으로 동기화됩니다

#### Step 2: 브랜치 생성
- Jira 이슈 키를 포함하여 브랜치를 생성합니다
```bash
# 예시: PB-26 이슈의 경우
git checkout -b docs/PB-026-add-ssot-contrib

# 또는 기능 개발의 경우
git checkout -b feature/PB-024-redis-cache
```

#### Step 3: 코드 작성
- 기존 코드 스타일과 아키텍처를 따릅니다
- 커밋 메시지 규칙을 준수합니다 (예: `docs: SSOT 문서 작성`)

#### Step 4: PR 생성
- 작업 완료 후 PR을 생성합니다
- **PR 제목에 Jira 이슈 키를 포함**합니다 (예: `[Docs] PB-26 개발 규칙 문서 반영`)
- PR 템플릿에 맞춰 내용을 작성합니다
- 최소 1명의 리뷰어를 지정합니다

#### Step 5: 코드 리뷰 및 머지
- 리뷰어의 피드백을 반영합니다
- 승인 후 머지합니다
- Jira 이슈가 자동으로 업데이트됩니다 (GitHub Actions를 통해)

### 3.3 팀 문화

**우리는 코드를 기록하고, 리뷰하며 성장합니다.**

- **코드 품질 우선**: 기능 구현 속도보다 코드의 품질과 팀원 간의 싱크(Sync)를 최우선으로 합니다
- **투명한 커뮤니케이션**: GitHub Issues를 활용해 할 일을 관리하고 진행 상황을 투명하게 공유합니다
- **상호 존중**: 모든 피드백은 건설적이고 존중하는 태도로 진행합니다
- **지속적 학습**: 새로운 기술과 패턴을 함께 학습하고 공유합니다

### 3.4 기여자별 역할

| 역할 | 이름 | GitHub | 담당 업무 |
|:---:|:---:|:---:|:---|
| **BE (Infra/Lead)** | 박재홍 (jaehong) | [@PHJ2000](https://github.com/PHJ2000) | 핵심 비즈니스 로직 구현, 아키텍처 설계, 코드 리팩토링 |
| **BE (Feature)** | 박건우 (gunwoo) | [@GunwooPar](https://github.com/GunwooPar) | 핵심 비즈니스 로직 구현, API 개발, 코드 리팩토링 |
| **FE (PM)** | 정도경 | [@DoGyeong888](https://github.com/DoGyeong888) | UI/UX 설계, 프론트엔드 개발 |

각 기여자의 상세한 기여 내역은 [contrib](./contrib/) 폴더를 참고하세요.

---

## 4. 주요 문서 링크

### 협업 관련
- [협업 규칙 상세](./process/collaboration-rules.md)
- [문서 작성 가이드](./guides/writing-guide.md)

### 기술 문서
- [기술 결정 기록 (ADR)](./adr/README.md)
- [프로젝트 기능 명세](./FEATURES.md)

### 기여자 문서
- [박건우 (gunwoo) 기여 내역](./contrib/gunwoo/index.md)
- [박재홍 (jaehong) 기여 내역](./contrib/jaehong/index.md)

### 개발 환경
- [AI Beach Congestion API](./ai-beach-congestion-api.md)

---

## 문서 변경 이력

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2025-12-26 | - | SSOT 문서 초안 작성 |
| 2025-12-26 | - | V1 8주 로드맵 반영, DoD 추가 |
| 2025-12-26 | - | 핵심 API 4개 정의 추가, Sprint 1 통합테스트에 Core-4 추가 |

---

**Last Updated**: 2026-01-01