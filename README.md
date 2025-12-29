# 🏖️ Beach Complex

> **공공데이터 기반, 전국 해수욕장 실시간 혼잡도 관리 시스템**
>
> *"MVP(Prototype) 개발 경험을 바탕으로, 대규모 트래픽을 고려한 성능 최적화(Redis)와 데이터 무결성(Flyway)을 갖춘 V1을 구축하고 있습니다."*

![Project Status](https://img.shields.io/badge/Status-V1_Development-blueviolet?style=flat-square)
![Build](https://img.shields.io/badge/Build-Passing-blue?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)

---

## 📅 프로젝트 현황 (Project Status)

> **Current Phase: V1 Refactoring & Performance Tuning 🚀**
>
> *2025.09 ~ 2025.10: MVP(최소 기능 제품) 개발 완료* <br>
> *2025.11 ~ 현재: V1 아키텍처 고도화 및 인프라 설계 진행 중*

초기 MVP 모델을 검증한 후, 현재는 **실제 운영 가능한 수준의 안정성**을 확보하기 위해 기술 스택을 확장하고 있습니다. 특히 데이터 일관성(Flyway)과 응답 속도 개선(Redis)에 집중하고 있습니다.

### ✅ V1 마일스톤 (Milestone)
- [x] **MVP 단계**: 핵심 도메인(해수욕장, 날씨) CRUD 및 기본 API 구현 완료
- [x] **DB 고도화**: **Flyway** 도입을 통한 스키마 형상 관리 및 마이그레이션 자동화
- [x] **성능 최적화**: **Redis** 캐싱 적용을 통한 조회 성능 개선 (진행 중)
- [ ] **CI/CD 구축**: Jenkins vs GitHub Actions 비교 분석 및 파이프라인 설계 단계
- [ ] **확장 기능**: 사용자 리뷰 시스템 및 위치 기반 추천 알고리즘 탑재

---

## 기술적 도전 (Why V1?)

단순 구현을 넘어, **데이터의 신뢰성**과 **시스템 성능**을 높이기 위해 다음과 같은 기술적 도전을 진행 중입니다.

### 1. Database Reliability & Consistency (데이터 신뢰성)
- **Problem**: 로컬과 배포 환경 간의 DB 스키마 불일치 문제 발생
- **Solution**: **Flyway**를 도입하여 DB 변경 이력을 코드로 관리(Version Control)하고, 환경 간 스키마 싱크를 100% 일치시켜 배포 안정성을 확보했습니다.

### 2. Performance & Caching (성능 최적화)
- **Problem**: 반복적인 기상 데이터 조회로 인한 DB 부하 및 응답 지연 우려
- **Solution**: **Redis**를 활용한 캐싱 전략(Global Cache)을 수립하여, 자주 조회되는 데이터의 응답 속도를 획기적으로 단축하고 DB 부하를 분산시켰습니다.

### 3. Architecture Improvements (구조 개선)
- **V1 Refactoring**: 유지보수성을 위해 **도메인형 디렉토리 구조**로 재설계하고, **QueryDSL**을 도입해 동적 쿼리의 타입 안정성을 확보했습니다.

---

## 🛠 기술 스택 (Tech Stack)

### Backend
![Spring](https://img.shields.io/badge/Spring_Boot_3.3-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Java](https://img.shields.io/badge/Java_21-007396?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)

### Frontend
![React](https://img.shields.io/badge/React_18-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![TailwindCSS](https://img.shields.io/badge/Tailwind_CSS-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)


### Database & Caching
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![PostGIS](https://img.shields.io/badge/PostGIS-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Flyway](https://img.shields.io/badge/Flyway-CC0200?style=for-the-badge&logo=flyway&logoColor=white)

### DevOps (In Progress)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazon-aws&logoColor=white)

### Tools & Libraries
![Git](https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white)
![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white)


---


### 프로젝트 구조 (Project Structure)

```text
Beach_complex/
├── 📁 src/main/java/com/beachcheck/
│   ├── 📁 config/              # 설정 파일 (Security, Redis, Cache, JWT)
│   ├── 📁 controller/          # REST API 엔드포인트
│   ├── 📁 service/             # 비즈니스 로직
│   ├── 📁 repository/          # JPA 레포지토리
│   ├── 📁 domain/              # 엔티티 클래스
│   ├── 📁 dto/                 # DTO (Request/Response)
│   ├── 📁 security/            # JWT 인증 필터
│   ├── 📁 exception/           # 예외 핸들러
│   ├── 📁 util/                # 유틸리티 클래스
│   └── 📁 scheduler/           # 스케줄러 (데이터 수집 등)
│
├── 📁 src/main/resources/
│   ├── 📁 db/migration/        # Flyway 마이그레이션 스크립트
│   ├── 📄 application.yml      # 애플리케이션 설정
│   └── 📄 application-dev.yml  # 개발 환경 설정
│
├── 📁 front/
│   ├── 📁 src/
│   │   ├── 📁 api/             # API 호출 로직
│   │   ├── 📁 components/      # React 컴포넌트
│   │   ├── 📁 hooks/           # Custom Hooks
│   │   ├── 📁 types/           # TypeScript 타입 정의
│   │   ├── 📁 utils/           # 유틸리티 함수
│   │   ├── 📁 constants/       # 상수 정의
│   │   ├── 📁 data/            # 정적 데이터
│   │   ├── 📁 assets/          # 이미지, 폰트 등
│   │   ├── 📄 App.tsx          # 메인 앱 컴포넌트
│   │   └── 📄 main.tsx         # 엔트리 포인트
│   ├── 📄 package.json         # 프론트엔드 의존성
│   └── 📄 vite.config.ts       # Vite 설정
│
├── 📁 docs/                    # 프로젝트 문서
├── 📄 build.gradle             # Gradle 빌드 설정
├── 📄 docker-compose.yml       # Docker 설정
└── 📄 README.md
```



---

## 🤝 협업 문화 (Collaboration)

> **"우리는 코드를 기록하고, 리뷰하며 성장합니다."**

저희 팀은 기능 구현 속도보다 코드의 품질과 팀원 간의 싱크(Sync)를 최우선으로 합니다.

- **GitHub Flow**: `main` 브랜치를 보호하고, 모든 기능은 개별 브랜치에서 개발합니다.
- **Code Review**: 현재 **약 70+ Commits, 22+ Pull Requests**를 통해 팀원 간 상호 피드백을 진행했으며, 승인(Approve) 없이는 병합하지 않습니다.
- **Issue Tracking**: GitHub Issues를 활용해 할 일을 관리하고 진행 상황을 투명하게 공유합니다.

---

## 시작하기 (Getting Started)

이 프로젝트를 로컬 환경에서 실행하는 방법을 안내합니다.

### Prerequisites
- JDK 21
- Node.js 20+
- Docker Desktop (DB 실행용)

### Installation

#### 1. 레포지토리를 클론합니다.
```bash
git clone https://github.com/PHJ2000/Beach_complex.git
cd Beach_complex
```

### 2. 로컬에서 백엔드 실행 (DB/Redis만 Docker)                                                                                                     
docker-compose up -d postgres redis                                                                                 
./gradlew bootRun                                                                                                   
# Windows PowerShell이면:                                                                                           
# .\gradlew.bat bootRun

#### 3. 프론트엔드를 실행합니다.
```bash
cd front
npm install
npm run dev
```

#### 4. 브라우저에서 접속합니다.
- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- API Docs (Swagger): `http://localhost:8080/swagger-ui.html`

---

## 👥 팀원 (Team)

| 역할 | 이름 | GitHub | 담당 업무                           |
| :---: | :---: | :---: |:--------------------------------|
| **BE (Infra/Lead)** | **[박재홍]** | [@PHJ2000](https://github.com/PHJ2000) | 핵심 비즈니스 로직 구현, 아키텍처 설계, 코드 리팩토링 |
| **BE (Feature)** | **[박건우]** | [@GunwooPar](https://github.com/GunwooPar) | 핵심 비즈니스 로직 구현, API 개발, 코드 리팩토링  |
| **FE (PM)** | **[정도경]** | [@DoGyeong888](https://github.com/DoGyeong888) | UI/UX 설계, 프론트엔드 개발              |

---

## 라이선스 (License)

이 프로젝트는 [MIT](LICENSE.md) 라이선스를 따릅니다.
