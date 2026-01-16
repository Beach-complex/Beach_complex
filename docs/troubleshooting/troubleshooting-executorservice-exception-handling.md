# ExecutorService.submit() 예외 처리 문제 해결

## 문제 상황

동시성 테스트에서 `ExecutorService.submit()`을 사용했을 때, 워커 스레드에서 발생한 예외가 메인 스레드로 전달되지 않는 문제가 발생했습니다.

### 문제 코드

```java
@Test
void concurrentAddFavorite_handlesCorrectly() throws InterruptedException {
  int threadCount = 10;
  ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
  CountDownLatch latch = new CountDownLatch(threadCount);
  
  AtomicInteger successCount = new AtomicInteger(0);
  AtomicInteger failCount = new AtomicInteger(0);
  
  for (int i = 0; i < threadCount; i++) {
    executorService.submit(() -> {  // ⚠️ Future를 무시함
      try {
        favoriteService.addFavorite(user1, beach1.getId());
        successCount.incrementAndGet();
      } catch (IllegalStateException | DataIntegrityViolationException e) {
        failCount.incrementAndGet();
      } catch (Exception e) {
        // ❌ 이 예외는 Future에만 저장되고 메인 스레드로 전달되지 않음
        throw new RuntimeException("Unexpected exception", e);
      } finally {
        latch.countDown();
      }
    });
  }
  
  latch.await();
  executorService.shutdown();
  
  // successCount/failCount만 검증, 실제 예외는 확인 불가
}
```

### 문제 원인

1. **`submit()`은 예외를 Future에 캡처**
   - `ExecutorService.submit()`은 `Future<T>` 객체를 반환
   - 워커 스레드에서 발생한 예외는 `Future` 내부에만 저장됨
   - `Future.get()`을 호출하지 않으면 예외를 확인할 수 없음

2. **예외가 조용히 무시됨**
   - 예상치 못한 예외(예: NullPointerException)가 발생해도
   - `successCount`/`failCount` 둘 다 증가하지 않고
   - 테스트가 통과해버릴 수 있음

3. **디버깅 어려움**
   - 실제 예외 스택트레이스를 볼 수 없음
   - 테스트 실패 원인 파악 불가

## 해결 방법

### 방법 1: CompletableFuture.allOf() + join() ⭐ (추천)

가장 현대적이고 간결한 방법입니다.

```java
@Test
void concurrentAddFavorite_handlesCorrectly() throws InterruptedException {
  int threadCount = 10;
  ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
  
  AtomicInteger successCount = new AtomicInteger(0);
  AtomicInteger failCount = new AtomicInteger(0);
  
  // CompletableFuture 리스트 수집
  List<CompletableFuture<Void>> futures = new ArrayList<>();
  
  for (int i = 0; i < threadCount; i++) {
    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
      try {
        favoriteService.addFavorite(user1, beach1.getId());
        successCount.incrementAndGet();
      } catch (IllegalStateException | DataIntegrityViolationException e) {
        failCount.incrementAndGet();
      }
      // ✅ 예상치 못한 예외는 CompletableFuture에 캡처되어 join()에서 던져짐
    }, executorService);
    
    futures.add(future);
  }
  
  // ✅ 모든 작업 완료 대기 + 예외 즉시 전파
  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  
  executorService.shutdown();
  
  assertThat(successCount.get()).isEqualTo(1);
  assertThat(failCount.get()).isEqualTo(9);
}
```

**장점:**
- ✅ 예외가 `join()`에서 즉시 던져짐 (CompletionException으로 래핑)
- ✅ CountDownLatch 불필요
- ✅ 가장 현대적이고 간결한 코드
- ✅ Java 8+ 표준 API

### 방법 2: Future 수집 + get()

전통적인 방법으로, 명시적으로 Future를 수집합니다.

```java
@Test
void concurrentAddFavorite_handlesCorrectly() throws Exception {
  int threadCount = 10;
  ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
  CountDownLatch latch = new CountDownLatch(threadCount);
  
  AtomicInteger successCount = new AtomicInteger(0);
  AtomicInteger failCount = new AtomicInteger(0);
  
  // Future 리스트 수집
  List<Future<?>> futures = new ArrayList<>();
  
  for (int i = 0; i < threadCount; i++) {
    Future<?> future = executorService.submit(() -> {
      try {
        favoriteService.addFavorite(user1, beach1.getId());
        successCount.incrementAndGet();
      } catch (IllegalStateException | DataIntegrityViolationException e) {
        failCount.incrementAndGet();
      } finally {
        latch.countDown();
      }
    });
    
    futures.add(future);
  }
  
  latch.await();
  
  // ✅ 모든 Future의 예외 확인
  for (Future<?> future : futures) {
    future.get(); // 예외가 있으면 여기서 ExecutionException으로 던져짐
  }
  
  executorService.shutdown();
  
  assertThat(successCount.get()).isEqualTo(1);
  assertThat(failCount.get()).isEqualTo(9);
}
```

**장점:**
- ✅ 예외가 `ExecutionException`으로 래핑되어 전파
- ✅ timeout 설정 가능: `future.get(5, TimeUnit.SECONDS)`

**단점:**
- ❌ CountDownLatch 여전히 필요
- ❌ 보일러플레이트 코드 많음

### 방법 3: invokeAll()

Callable 기반으로 작업을 제출합니다.

```java
@Test
void concurrentAddFavorite_handlesCorrectly() throws Exception {
  int threadCount = 10;
  ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
  
  AtomicInteger successCount = new AtomicInteger(0);
  AtomicInteger failCount = new AtomicInteger(0);
  
  // Callable 리스트 생성
  List<Callable<Void>> tasks = new ArrayList<>();
  for (int i = 0; i < threadCount; i++) {
    tasks.add(() -> {
      try {
        favoriteService.addFavorite(user1, beach1.getId());
        successCount.incrementAndGet();
      } catch (IllegalStateException | DataIntegrityViolationException e) {
        failCount.incrementAndGet();
      }
      return null;
    });
  }
  
  // ✅ 모든 작업 실행 및 Future 리스트 반환
  List<Future<Void>> futures = executorService.invokeAll(tasks);
  
  // ✅ 예외 확인
  for (Future<Void> future : futures) {
    future.get();
  }
  
  executorService.shutdown();
  
  assertThat(successCount.get()).isEqualTo(1);
  assertThat(failCount.get()).isEqualTo(9);
}
```

**장점:**
- ✅ CountDownLatch 불필요 (invokeAll이 모든 작업 완료까지 블로킹)
- ✅ timeout 설정 가능: `invokeAll(tasks, 10, TimeUnit.SECONDS)`

**단점:**
- ❌ Callable로 변환 필요 (return null 보일러플레이트)

## 추가 고려 사항

### 1. CountDownLatch timeout 설정

**문제:**
```java
latch.await(); // ⚠️ 무한 대기 위험
```

- `countDown()` 누락 시 무한 대기
- 스레드가 멈추면 CI가 영원히 대기
- 테스트 타임아웃으로만 감지 가능 (느리고 불명확)

**해결:**
```java
// ✅ timeout 설정
boolean completed = latch.await(10, TimeUnit.SECONDS);
if (!completed) {
  fail("Some threads did not complete within timeout");
}
```

### 2. ExecutorService 정리 보장

**문제:**
```java
executorService.shutdown(); // ⚠️ 예외 발생 시 실행 안됨
```

- 테스트 실패 시 스레드 풀이 정리되지 않음
- 스레드 누수 발생
- 다른 테스트에 영향 (플래키 테스트 원인)

**해결:**
```java
try {
  // 테스트 로직
} finally {
  executorService.shutdown();
  if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
    executorService.shutdownNow(); // 강제 종료
  }
}
```

### 3. CompletableFuture는 이 문제들을 자동 해결 ⭐

**CompletableFuture.allOf()의 장점:**
```java
// ✅ timeout 자동 처리
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .get(10, TimeUnit.SECONDS); // TimeoutException 발생

// ✅ ExecutorService 정리도 간결
try {
  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
} finally {
  executorService.shutdown();
  executorService.awaitTermination(5, TimeUnit.SECONDS);
}
```

## 최종 추천

**CompletableFuture.allOf() + join() 방식을 추천합니다.**

- 가장 현대적이고 간결
- 예외 처리가 자동으로 처리됨
- CountDownLatch 불필요 (타임아웃 문제 해결)
- Java 8+ 프로젝트에 적합

## 적용 결과

### 최종 권장 코드 (모든 문제 해결)

```java
@Test
@DisplayName("P2-01: 동시 찜 추가 요청 처리 (동시성)")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
void concurrentAddFavorite_handlesCorrectly() throws Exception {
  int threadCount = 10;
  ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
  
  try {
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);
    
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    
    for (int i = 0; i < threadCount; i++) {
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        try {
          favoriteService.addFavorite(user1, beach1.getId());
          successCount.incrementAndGet();
        } catch (IllegalStateException | DataIntegrityViolationException e) {
          failCount.incrementAndGet();
        }
        // ✅ 예상치 못한 예외는 CompletableFuture에 캡처되어 get()에서 던져짐
      }, executorService);
      
      futures.add(future);
    }
    
    // ✅ 모든 작업 완료 대기 + 예외 표면화 + timeout 설정
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .get(10, TimeUnit.SECONDS); // TimeoutException 발생 시 테스트 실패
    
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(failCount.get()).isEqualTo(9);
    
    List<UserFavorite> favorites = favoriteRepository.findByUserId(user1.getId());
    assertThat(favorites).hasSize(1);
    
  } finally {
    // ✅ ExecutorService 정리 보장
    executorService.shutdown();
    if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
      executorService.shutdownNow(); // 강제 종료
    }
  }
}
```

### Before vs After 비교

#### Before (문제 있는 코드)
```java
// ❌ Future 무시
executorService.submit(() -> { ... });

// ❌ 무한 대기 위험
latch.await();

// ❌ 예외 시 정리 안됨
executorService.shutdown();
```

#### After (개선된 코드)
```java
// ✅ CompletableFuture로 예외 캡처
CompletableFuture<Void> future = CompletableFuture.runAsync(() -> { ... }, executorService);

// ✅ timeout 설정
CompletableFuture.allOf(futures).get(10, TimeUnit.SECONDS);

// ✅ finally로 정리 보장
finally {
  executorService.shutdown();
  executorService.awaitTermination(5, TimeUnit.SECONDS);
}
```

## 참고 자료

- [Java CompletableFuture 공식 문서](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)
- [ExecutorService.submit() vs execute()](https://stackoverflow.com/questions/18730290/what-is-the-difference-between-executorservice-submit-and-executorservice-execu)

## 관련 이슈

- **작성일**: 2026-01-14
- **관련 PR**: (PR 번호 추가 예정)
- **리뷰어 피드백**: ExecutorService.submit()은 예외를 Future에 캡처하므로 Future.get()으로 표면화 필요

