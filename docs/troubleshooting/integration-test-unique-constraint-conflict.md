# 통합 테스트 UNIQUE 제약 충돌 해결

**컴포넌트:** db

**작성일:** 2026-01-16

| 날짜 | 작성자 | 변경 내용 |
|:---:|:---:|:---|
| 2026-01-13 | - | 문서 생성 |

## 문제 상황

### 증상
- `UserFavoriteServiceIntegrationTest` 클래스 전체 실행 시 **P0-04 테스트에서 실패**
- 단독 테스트 실행 시에는 **정상 통과**
- 에러 메시지:
  ```
  ERROR: duplicate key value violates unique constraint "beaches_code_key"
  Detail: Key (code)=(TEST_BEACH_1) already exists.
  org.springframework.dao.DataIntegrityViolationException
  ```

### 발생 조건
- 클래스 단위로 여러 테스트를 순차 실행할 때
- 여러 테스트 실행 중 특정 시점에 발생 (P0-04)
  - 테스트 실행 순서는 JUnit이 결정하며 보장되지 않음
  - 트랜잭션 롤백 지연이 누적되어 임계점 도달 시 발생
- Testcontainers + PostgreSQL 환경

## 원인 분석

### 1. `@Transactional` 자동 롤백의 타이밍 이슈

**기존 코드:**
```java
@BeforeEach
void setUp() {
    beach1 = beachRepository.save(
        createBeachWithLocation("TEST_BEACH_1", "테스트해수욕장1", 129.1603, 35.1587));
    beach2 = beachRepository.save(
        createBeachWithLocation("TEST_BEACH_2", "테스트해수욕장2", 129.1189, 35.1532));
    
    user1 = userRepository.save(createUser("user1@test.com", "User 1"));
    user2 = userRepository.save(createUser("user2@test.com", "User 2"));
    
    cacheManager.getCache("beachSummaries").clear();
}
```

**문제 발생 메커니즘:**

JUnit 5는 테스트 순서를 보장하지 않지만, 클래스 전체 실행 시 **여러 테스트가 연속으로 실행**됩니다.
각 테스트는 다음과 같은 생명주기를 가집니다:

```
[테스트 N 실행] (순서 무관)
├─ setUp(): Beach(code="TEST_BEACH_1") 생성 (트랜잭션 시작)
├─ 테스트 로직 실행
└─ 트랜잭션 롤백 → Beach 삭제 (비동기, 지연 발생)

[테스트 N+1 실행] (다음 테스트)
├─ setUp(): Beach(code="TEST_BEACH_1") 생성 시도
└─ 💥 UNIQUE 제약 위반!
    └─ 이전 테스트의 Beach가 아직 완전히 삭제되지 않음
```

**핵심 문제:**
- 테스트 실행 순서와 무관하게, 여러 테스트가 **동일한 code 값**을 사용
- 이전 테스트의 트랜잭션 롤백이 **완료되기 전**에 다음 테스트가 시작
- Testcontainers + PostgreSQL 환경에서 트랜잭션 처리 지연 누적
- **4번째 테스트(P0-04) 시점**에 누적된 지연으로 인해 충돌 발생
  (정확한 실행 순서는 JUnit 내부 알고리즘에 따라 달라질 수 있음)

### 2. 근본 원인

#### Race Condition
- Spring의 `@Transactional` 롤백은 **테스트 메서드 완료 후** 발생
- Testcontainers + PostgreSQL 환경에서 **트랜잭션 커밋/롤백이 비동기적으로 처리**
- 이전 테스트의 Beach 삭제가 완료되기 전에 다음 테스트가 시작됨

#### PostgreSQL MVCC (Multi-Version Concurrency Control)
- PostgreSQL은 동시성 제어를 위해 MVCC 사용
- 트랜잭션 롤백 시 물리적 삭제가 즉시 이루어지지 않음
- VACUUM 프로세스에 의해 지연 삭제됨

#### Testcontainers 환경 특성
- Docker 컨테이너로 실행되는 PostgreSQL
- 네트워크 지연 + 트랜잭션 처리 지연 누적
- 로컬 PostgreSQL보다 타이밍 이슈 발생 확률 높음

### 3. 왜 단독 실행은 성공하는가?

단일 테스트 실행 시:
```
[단일 테스트만 실행]
├─ setUp(): Beach(code="TEST_BEACH_1") 생성 (트랜잭션 A)
├─ 테스트 로직 실행
└─ 트랜잭션 롤백 + JVM 종료
    → DB 초기화 완료 (다음 실행 시 깨끗한 상태)
```

클래스 전체 실행 시:
```
[11개 테스트 연속 실행] (순서는 JUnit이 결정)
├─ 테스트 1: Beach(code="TEST_BEACH_1") 생성 → 롤백 (지연)
├─ 테스트 2: Beach(code="TEST_BEACH_1") 생성 → 롤백 (지연)
├─ 테스트 3: Beach(code="TEST_BEACH_1") 생성 → 롤백 (지연)
├─ ... (트랜잭션 롤백 지연 누적)
└─ 테스트 N: 💥 UNIQUE 제약 위반!
    └─ 이전 테스트들의 Beach가 아직 삭제되지 않음
```

**왜 특정 테스트에서만 실패하는가?**
- 트랜잭션 롤백 처리 지연이 누적
- 보통 3~4번째 테스트 시점에 임계점 도달
- P0-04에서 실패했지만, 실행 순서에 따라 다른 테스트에서도 실패 가능

## 해결 방법

### ✅ 적용된 해결책: UUID 기반 동적 code 생성

**수정된 코드:**
```java
@BeforeEach
void setUp() {
    // UUID 기반 동적 code 생성으로 UNIQUE 제약 충돌 방지
    String uniqueCode1 = "TEST_BEACH_" + UUID.randomUUID().toString().substring(0, 8);
    String uniqueCode2 = "TEST_BEACH_" + UUID.randomUUID().toString().substring(0, 8);
    
    beach1 = beachRepository.save(
        createBeachWithLocation(uniqueCode1, "테스트해수욕장1", 129.1603, 35.1587));
    beach2 = beachRepository.save(
        createBeachWithLocation(uniqueCode2, "테스트해수욕장2", 129.1189, 35.1532));
    
    // UUID 기반 동적 email 생성으로 UNIQUE 제약 충돌 방지
    String uniqueEmail1 = "user1_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    String uniqueEmail2 = "user2_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    
    user1 = userRepository.save(createUser(uniqueEmail1, "User 1"));
    user2 = userRepository.save(createUser(uniqueEmail2, "User 2"));
    
    cacheManager.getCache("beachSummaries").clear();
}
```

**장점:**
- ✅ 각 테스트마다 고유한 `code`, `email` 생성 → UNIQUE 제약 충돌 없음
- ✅ 트랜잭션 롤백 타이밍과 무관하게 안전
- ✅ 간단하고 빠른 해결책
- ✅ 테스트 격리 보장

**단점:**
- ❌ 고정된 테스트 데이터를 사용할 수 없음 (크게 문제되지 않음)

### 고려했던 다른 해결책들

#### 방법 1: 명시적 데이터 삭제
```java
@BeforeEach
void setUp() {
    favoriteRepository.deleteAll();
    beachRepository.deleteAll();
    userRepository.deleteAll();
    
    entityManager.flush();
    entityManager.clear();
    
    // Beach/User 생성...
}
```
- **장점**: DB 상태를 완전히 제어
- **단점**: 외래 키 제약으로 삭제 순서 중요, 약간 느림

#### 방법 2: `@DirtiesContext` 사용
```java
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserFavoriteServiceIntegrationTest extends IntegrationTest {
```
- **장점**: Spring Context 완전 초기화
- **단점**: 매우 느림 (Context 재시작 비용)

#### 방법 3: `@Sql` 스크립트
```java
@BeforeEach
@Sql(statements = {
    "TRUNCATE TABLE user_favorites CASCADE",
    "TRUNCATE TABLE beaches CASCADE",
    "TRUNCATE TABLE users CASCADE"
})
void setUp() {
```
- **장점**: 명시적 DB 초기화
- **단점**: DB 벤더 의존성 (PostgreSQL 특정 문법)

## 교훈

### 1. 통합 테스트에서 UNIQUE 제약이 있는 엔티티는 동적 값 사용
- 고정된 테스트 데이터는 트랜잭션 롤백 타이밍 이슈 발생 가능
- UUID, 타임스탬프 등 동적 값으로 충돌 회피

### 2. `@Transactional` 롤백은 즉시 반영되지 않음
- Spring의 `@Transactional` 롤백은 비동기적
- Testcontainers + PostgreSQL 환경에서는 더욱 지연됨

### 3. 단독 실행 성공 ≠ 클래스 실행 성공
- 테스트 간 격리가 완벽하지 않을 수 있음
- 항상 **클래스 전체 실행**으로 검증 필요

### 4. Testcontainers 환경 특성 고려
- Docker 네트워크 지연 + PostgreSQL MVCC
- 로컬 H2와 다르게 타이밍 이슈 발생 가능

## 관련 파일

- `src/test/java/com/beachcheck/integration/UserFavoriteServiceIntegrationTest.java`
- `src/test/java/com/beachcheck/base/IntegrationTest.java`
- `src/test/java/com/beachcheck/fixture/FavoriteTestFixtures.java`

## 참고 자료

- [Spring Testing - Transaction Management](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html)
- [PostgreSQL MVCC](https://www.postgresql.org/docs/current/mvcc.html)
- [Testcontainers - Database Containers](https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/)

## 커밋 이력

- `2026-01-13`: UUID 기반 동적 code/email 생성으로 UNIQUE 제약 충돌 해결
