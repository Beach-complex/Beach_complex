# 통합 테스트 동시성 검증 실패 트러블슈팅 (PB-64)

## 📌 문제 상황

### 테스트 실패 증상
- **테스트:** `UserFavoriteServiceIntegrationTest.concurrentAddFavorite_handlesCorrectly()`
- **증상:** `successCount = 0`, `failCount = 0` (예상: 1개 성공, 9개 실패)
- **예외:** `IllegalArgumentException: 해수욕장을 찾을 수 없습니다.` (10개 스레드 모두)

### 예상 동작 vs 실제 동작

| 항목 | 유닛 테스트 | 통합 테스트 (실패) | 통합 테스트 (수정 후) |
|------|-------------|-------------------|---------------------|
| successCount | 1 | 0 | 1 |
| failCount | 9 | 0 | 9 |
| 발생 예외 | IllegalStateException | IllegalArgumentException | IllegalStateException |
| 예외 메시지 | "이미 찜한 해수욕장입니다." | "해수욕장을 찾을 수 없습니다." | "이미 찜한 해수욕장입니다." |

---

## ⚠️ 근본 원인

### 핵심 문제
**`@Transactional` 기반 IntegrationTest에서 `@BeforeEach`로 생성한 엔티티가 멀티스레드의 새 트랜잭션에서 보이지 않음**

### 상세 분석

#### 1️⃣ IntegrationTest 기반 클래스 구조

```java
@SpringBootTest
@ActiveProfiles("test")
@Transactional  // ← 모든 테스트를 트랜잭션으로 감쌈
public abstract class IntegrationTest {
    // ...
}
```

#### 2️⃣ 트랜잭션 격리로 인한 가시성 문제

```
[테스트 트랜잭션] (READ COMMITTED 격리 수준)
  └─ @BeforeEach: beach1, user1 저장
      └─ 아직 커밋 안 됨 (트랜잭션 진행 중)
  
  └─ 테스트 메서드 실행
      └─ 스레드 1~10 시작
          └─ @Transactional favoriteService.addFavorite()
              └─ [새로운 트랜잭션] beachRepository.findById(beach1.getId())
                  └─ ❌ 커밋되지 않은 데이터는 보이지 않음
                  └─ Optional.empty() 반환
                  └─ IllegalArgumentException 발생
```

#### 3️⃣ 타이밍 다이어그램

```
시간 →
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[테스트 트랜잭션]
  ├─ setUp() beach1 저장 (UNCOMMITTED)
  ├─ 테스트 메서드 실행
  │   ├─ [스레드1 트랜잭션] findById(beach1) → ❌ 없음
  │   ├─ [스레드2 트랜잭션] findById(beach1) → ❌ 없음
  │   └─ ...
  └─ 테스트 종료 후 ROLLBACK (자동)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

#### 4️⃣ 왜 유닛 테스트에서는 통과했나?

- **유닛 테스트:** Repository를 Mockito로 Mock → 트랜잭션 무관
- **통합 테스트:** 실제 PostgreSQL DB 사용 → 트랜잭션 격리 수준 적용

---

## ✅ 해결 방법

### Option 1: Propagation.NOT_SUPPORTED (채택) ⭐

```java
@Test
@DisplayName("P2-01: 동시 찜 추가 요청 처리 (동시성)")
@Transactional(propagation = Propagation.NOT_SUPPORTED)  // ← 추가
void concurrentAddFavorite_handlesCorrectly() throws InterruptedException {
    // ... 기존 코드
}
```

**동작 원리:**
- 테스트 메서드가 트랜잭션 밖에서 실행됨
- @BeforeEach에서 저장한 데이터가 즉시 커밋됨
- 스레드들이 커밋된 데이터를 정상적으로 조회 가능

**장점:**
- 가장 명확하고 안전한 해결 방법
- 다른 테스트에 영향 없음
- 동시성 테스트의 의도가 명확히 드러남

**단점:**
- 테스트 후 자동 롤백이 안 됨 (하지만 UUID 기반이라 충돌 없음)

---

### Option 2: TestEntityManager.flush() (비권장) ⚠️

```java
@BeforeEach
void setUp() {
    beach1 = beachRepository.save(...);
    user1 = userRepository.save(...);
    
    entityManager.flush();  // ← DB에 SQL 전송
}
```

**문제점:**
- flush()는 SQL을 보내지만 트랜잭션은 여전히 UNCOMMITTED
- READ COMMITTED 격리 수준에서는 여전히 보이지 않음
- 동작하지 않을 가능성 높음

---

### Option 3: IntegrationTest에서 @Transactional 제거 (과도함) ❌

```java
@SpringBootTest
@ActiveProfiles("test")
// @Transactional ← 제거
public abstract class IntegrationTest {
}
```

**문제점:**
- 모든 통합 테스트의 자동 롤백이 사라짐
- 테스트 간 데이터 오염 위험
- 각 테스트마다 수동으로 정리 필요
- 유지보수 부담 증가

---

## 🎓 학습 포인트

### 1. Spring @Transactional의 동작 방식

- 클래스 레벨 @Transactional은 모든 메서드에 전파됨
- 테스트 클래스의 @Transactional은 자동 롤백을 위한 것
- 새로운 스레드는 부모 트랜잭션을 상속하지 않음

### 2. 트랜잭션 격리 수준 (Isolation Level)

**READ COMMITTED (PostgreSQL 기본값):**
- 커밋된 데이터만 읽을 수 있음
- UNCOMMITTED 데이터는 다른 트랜잭션에서 보이지 않음

**Spring @Transactional의 기본값: PROPAGATION_REQUIRED**
- 이미 트랜잭션이 있으면 참여, 없으면 새로 생성
- 새 스레드는 항상 새 트랜잭션을 생성

### 3. 멀티스레드 환경에서의 트랜잭션

```java
@Transactional
void testMethod() {
    // TX1 시작
    
    new Thread(() -> {
        @Transactional
        void serviceMethod() {
            // TX2 시작 (TX1과 완전히 독립적)
            // TX1의 UNCOMMITTED 데이터는 보이지 않음
        }
    }).start();
}
```

### 4. Propagation 타입

- **REQUIRED (기본값):** 트랜잭션이 있으면 참여, 없으면 생성
- **REQUIRES_NEW:** 항상 새 트랜잭션 생성 (기존 트랜잭션 일시 중단)
- **NOT_SUPPORTED:** 트랜잭션 없이 실행 (기존 트랜잭션 일시 중단)
- **NEVER:** 트랜잭션이 있으면 예외 발생

---

## 🔍 디버깅 과정

### 1단계: 증상 확인

```
successCount = 0, failCount = 0
→ 예외가 catch 블록에 잡히지 않음
```

### 2단계: 예외 타입 확인

```java
catch (Exception e) {
    System.err.println("예외: " + e.getClass().getName());
    System.err.println("메시지: " + e.getMessage());
}

// 출력:
// 🔴 예외 발생: java.lang.IllegalArgumentException
// 🔴 메시지: 해수욕장을 찾을 수 없습니다.
```

### 3단계: Service 코드 분석

```java
Beach beach = beachRepository.findById(beachId)
    .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));
// → findById()가 Optional.empty() 반환
```

### 4단계: 트랜잭션 상태 확인

```java
@BeforeEach
void setUp() {
    beach1 = beachRepository.save(...);  // 저장됨 (TX 내)
}

// 하지만 테스트가 @Transactional 안에서 실행 중
// 새 스레드는 새 트랜잭션 → 커밋 안 된 데이터 보이지 않음
```

### 5단계: IntegrationTest 기반 클래스 확인

```java
@Transactional  // ← 여기가 원인!
public abstract class IntegrationTest {
}
```

---

## 📊 테스트 결과 비교

### 수정 전

```
🔴 예외 발생: java.lang.IllegalArgumentException (10개 모두)
🔴 메시지: 해수욕장을 찾을 수 없습니다.

최종 결과: success=0, fail=0
DB 저장 개수: 0개
테스트 실패 ❌
```

### 수정 후

```
✅ 스레드 0 성공
⚠️ 스레드 1 - IllegalStateException: 이미 찜한 해수욕장입니다.
⚠️ 스레드 2 - IllegalStateException: 이미 찜한 해수욕장입니다.
...
⚠️ 스레드 9 - IllegalStateException: 이미 찜한 해수욕장입니다.

최종 결과: success=1, fail=9
DB 저장 개수: 1개
테스트 통과 ✅
```

---

## 🔗 관련 자료

### Spring 공식 문서

- https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
- https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html

### 관련 코드

- `src/test/java/com/beachcheck/base/IntegrationTest.java:22` - @Transactional 설정
- `src/main/java/com/beachcheck/service/UserFavoriteService.java:30` - @Transactional 메서드

---

## 📝 체크리스트

- ✅ 문제 원인 파악: 트랜잭션 격리로 인한 데이터 가시성 문제
- ✅ 해결 방법 적용: @Transactional(propagation = Propagation.NOT_SUPPORTED)
- ✅ 테스트 통과 확인: success=1, fail=9, DB에 1개만 저장
- ✅ 다른 테스트에 영향 없음 확인
- ✅ 코드 리뷰 및 머지

---

## 💡 예방 가이드

### 동시성 테스트 작성 시 주의사항

#### 1. 멀티스레드 테스트는 트랜잭션 밖에서 실행

```java
@Test
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void concurrentTest() { }
```

#### 2. 테스트 데이터는 커밋된 상태여야 함
- @BeforeEach 데이터가 다른 트랜잭션에서 보여야 한다면 NOT_SUPPORTED 사용

#### 3. ExecutorService의 예외는 삼켜짐

```java
executorService.submit(() -> {
    try {
        // ...
    } catch (Exception e) {  // 모든 예외 캐치 필수
        e.printStackTrace();
    }
});
```

#### 4. CountDownLatch로 완료 대기

```java
latch.await();  // 모든 스레드 완료 대기
executorService.shutdown();  // 스레드풀 종료
```

---

## 📅 작성 정보

- **작성일:** 2026-01-12
- **관련 이슈:** PB-64
- **수정 파일:** UserFavoriteServiceIntegrationTest.java

---

  ---
