# 박건우 (Gunwoo Park) - 기여 기록

> **Backend Developer (Feature)**
>
> 핵심 비즈니스 로직 구현, API 개발, 코드 리팩토링

## 기본 정보

| 항목 | 내용 |
|:---:|:---|
| **이름** | 박건우 (Gunwoo Park) |
| **GitHub** | [@GunwooPar](https://github.com/GunwooPar) |
| **역할** | Backend Developer (Feature) |
| **참여 기간** | 2025.09 ~ 현재 |
| **주요 담당** | 핵심 비즈니스 로직 구현, API 개발, 코드 리팩토링 |

---

## 주요 기여 내역

### 1. 핵심 기능 개발

#### Beach Domain
- 해수욕장 관련 CRUD API 구현
- 위치 기반 검색 기능 개발
- 데이터 모델링 및 엔티티 설계

#### Weather Integration
- 기상청 API 연동
- 날씨 데이터 수집 스케줄러 구현
- 캐싱 전략 수립

### 2. API 개발
- RESTful API 설계 및 구현
- DTO 패턴 적용
- 응답 포맷 표준화

### 3. 코드 리팩토링
- 도메인형 디렉토리 구조로 재설계
- Service Layer 최적화
- 코드 중복 제거 및 재사용성 향상

---

## Pull Requests

### 주요 PR 목록
<!-- 실제 PR을 작성할 때마다 아래에 추가해주세요 -->

| PR # | 제목 | 상태 | 링크 |
|:---:|:---|:---:|:---:|
| - | 예시: [feat] 해수욕장 검색 API 구현 | Merged | [#링크]() |
| - | 예시: [refactor] Service Layer 리팩토링 | Merged | [#링크]() |

### 코드 리뷰 기여
<!-- 리뷰한 PR을 기록해주세요 -->
- 총 리뷰 참여: X건
- 주요 피드백: 코드 품질, 성능 최적화, 보안

---

## 기술 스택

### Backend
- **Language**: Java 21
- **Framework**: Spring Boot 3.3
- **Security**: Spring Security, JWT
- **Database**: PostgreSQL, PostGIS
- **Cache**: Redis
- **ORM**: JPA/Hibernate, QueryDSL
- **Migration**: Flyway

### Tools
- **IDE**: IntelliJ IDEA
- **Version Control**: Git, GitHub
- **API Testing**: Postman, Swagger
- **Collaboration**: Notion, Jira

---

## 학습 및 성장 기록

### 기술적 성장
<!-- 프로젝트를 진행하며 배운 점, 어려웠던 점, 해결 방법 등을 기록해주세요 -->

#### Flyway를 통한 DB 형상 관리
- **배운 점**: 데이터베이스 마이그레이션의 중요성과 버전 관리 방법
- **어려웠던 점**: 로컬과 배포 환경 간의 스키마 불일치 문제
- **해결 방법**: Flyway를 도입하여 DB 변경 이력을 코드로 관리

#### Redis 캐싱 전략
- **배운 점**: 캐싱을 통한 성능 최적화 방법
- **어려웠던 점**: 캐시 무효화 전략 수립
- **해결 방법**: TTL 설정 및 이벤트 기반 캐시 갱신

#### RESTful API 설계
- **배운 점**: API 설계 원칙과 표준화의 중요성
- **어려웠던 점**: 일관된 응답 포맷 유지
- **해결 방법**: DTO 패턴 적용 및 예외 처리 통일

### 협업 역량
- **코드 리뷰**: 팀원들과 활발한 코드 리뷰를 통해 코드 품질 향상
- **커뮤니케이션**: GitHub Issues를 활용한 투명한 작업 공유
- **문서화**: 작업 내용과 기술 결정을 문서로 남기는 습관 형성

---

## 개인 노트

### 프로젝트 회고
<!-- 프로젝트를 진행하며 느낀 점을 자유롭게 작성해주세요 -->

#### MVP 단계 (2025.09 ~ 2025.10)
- 첫 프로젝트로서 기본적인 CRUD 구현에 집중
- 팀원들과의 협업 방식 학습

#### V1 고도화 (2025.11 ~ 현재)
- 실제 운영 가능한 수준의 코드 작성에 집중
- 성능과 안정성을 고려한 아키텍처 설계

### 앞으로의 목표
<!-- 앞으로의 학습 목표나 개발 목표를 작성해주세요 -->
- [ ] CI/CD 파이프라인 구축 경험
- [ ] 테스트 코드 작성 역량 강화
- [ ] 클린 아키텍처 이해 및 적용
- [ ] 성능 모니터링 및 최적화 경험

---

## 관련 문서
- [프로젝트 SSOT](../../ssot.md)
- [협업 규칙](../../process/collaboration-rules.md)
- [기술 결정 기록](../../adr/README.md)

---

**Last Updated**: 2025-12-26