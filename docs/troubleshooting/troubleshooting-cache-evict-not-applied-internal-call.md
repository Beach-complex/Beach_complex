# [Troubleshooting] toggleFavorite 내부 호출 시 @CacheEvict 미적용으로 캐시 stale 발생

**컴포넌트:** api

## ✅ 상태: 해결됨

**해결 날짜:** 2026-01-16

---

## 📌 요약

**문제:** `toggleFavorite()` 호출 시 캐시가 무효화되지 않아 사용자에게 stale 데이터 반환

**원인:** Spring AOP는 프록시 기반이라 같은 클래스 내부 메서드 호출 시 `@CacheEvict`가 작동하지 않음

**영향:** 찜 토글 API 사용 시 100% 발생, UX 저하 (사용자가 찜 추가/제거했는데 목록에 반영 안 됨)

**해결:** `toggleFavorite()` 메서드에 `@CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")` 직접 추가

---

## 🚨 문제 상황

### 증상

`UserFavoriteService.toggleFavorite()` 메서드 호출 시 **캐시가 무효화되지 않아** 사용자에게 stale 데이터가 반환되는 문제가 발생했습니다.

**예상 동작:**
- `toggleFavorite()` 호출 → 캐시 무효화 → 이후 조회 시 최신 데이터 반환

**실제 동작:**
- `toggleFavorite()` 호출 → **캐시 유지** → 이후 조회 시 **이전 캐시 반환**

### 재현 방법

```bash
# 전제: 로그인된 사용자 (인증 토큰 필요)

# 1. 찜 목록 조회 (캐시 생성)
GET /api/favorites
Authorization: Bearer {token}
→ Response: [
  { "id": "beach-id-1", "name": "해운대해수욕장", ... },
  { "id": "beach-id-2", "name": "광안리해수욕장", ... }
]

# 2. beach-id-3 찜 추가 (토글)
PUT /api/favorites/beach-id-3/toggle
Authorization: Bearer {token}
→ Response: { "message": "찜 목록에 추가되었습니다.", "isFavorite": true }
→ DB에는 추가됨, 캐시는 그대로 (문제!)

# 3. 다시 찜 목록 조회
GET /api/favorites
Authorization: Bearer {token}
→ Response: [
  { "id": "beach-id-1", "name": "해운대해수욕장", ... },
  { "id": "beach-id-2", "name": "광안리해수욕장", ... }
]
← beach-id-3이 없음 (stale 캐시 반환)
```

### 영향 범위

| 항목 | 내용 |
|------|------|
| **발생 빈도** | `toggleFavorite()` API 사용 시 100% 발생 |
| **영향 범위** | 찜 토글 기능 사용 시 캐시 정합성 문제 |
| **비즈니스 영향** | 사용자에게 잘못된 찜 목록 표시, UX 저하 |
| **발생 환경** | Local / Dev / Staging / Prod 전체 |

---

## 🔍 원인 분석

### 문제 코드

**파일:** `src/main/java/com/beachcheck/service/UserFavoriteService.java:61-78`

```java
/** 찜 토글 (추가/제거) */
@Transactional
public boolean toggleFavorite(User user, UUID beachId) {
    if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
        removeFavorite(user, beachId);  // ← @CacheEvict 미적용 (내부 호출)
        return false;
    } else {
        addFavorite(user, beachId);     // ← @CacheEvict 미적용 (내부 호출)
        return true;
    }
}

@Transactional
@CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")
public UserFavorite addFavorite(User user, UUID beachId) { ... }

@Transactional
@CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")
public void removeFavorite(User user, UUID beachId) { ... }
```

### 근본 원인 (Root Cause)

**Spring AOP 프록시 제약 사항**

Spring AOP는 **프록시 기반**으로 동작하기 때문에 내부 메서드 호출 시 AOP가 적용되지 않습니다.

```
✅ 외부 호출 (정상):
Client → [Proxy] → @CacheEvict 처리 → Target Object
         ↑ AOP 적용됨

❌ 내부 호출 (문제):
Target Object → this.method() → Target Object
                ↑ Proxy를 거치지 않아 AOP 미적용
```

### 동작 비교표

| 시나리오 | 예상 동작 | 실제 동작 | 원인 |
|---------|----------|----------|------|
| `toggleFavorite()` 호출 | ✅ 캐시 무효화 | ❌ 캐시 유지 (stale) | 내부 호출로 AOP 미적용 |
| 직접 `addFavorite()` 호출 | ✅ 캐시 무효화 | ✅ 캐시 무효화 | 외부 호출로 AOP 적용 |
| 직접 `removeFavorite()` 호출 | ✅ 캐시 무효화 | ✅ 캐시 무효화 | 외부 호출로 AOP 적용 |

### 문제 발생 메커니즘

1. `toggleFavorite()`이 같은 클래스 내부에서 `addFavorite()` / `removeFavorite()` 호출
2. **this 참조**를 통한 호출로 **Proxy를 거치지 않음**
3. `@CacheEvict` 어노테이션이 처리되지 않음
4. 캐시가 무효화되지 않고 **stale 상태로 유지**

---

## ✅ 해결 방법

### 선택된 방법: toggleFavorite에 @CacheEvict 직접 추가 ⭐ (추천)

```java
@Transactional
@CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")  // ← 추가
public boolean toggleFavorite(User user, UUID beachId) {
    if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
        removeFavorite(user, beachId);
        return false;
    } else {
        addFavorite(user, beachId);
        return true;
    }
}
```

**선택 이유:**
- ✅ 가장 간단하고 명확
- ✅ 최소한의 코드 변경
- ✅ 모든 경로에서 캐시 무효화 보장
- ✅ 팀원들이 이해하기 쉬움

**Trade-off:**
- 어노테이션 중복 발생 (toggleFavorite, addFavorite, removeFavorite 모두에 존재)
- → 명확성과 안전성을 위한 수용 가능한 트레이드오프

---

### 대안 1: 캐시 무효화 로직 분리 (캐시 매니저)

```java
@Service
@RequiredArgsConstructor
public class UserFavoriteService {
    
    private final UserFavoriteCacheManager cacheManager;
    
    @Transactional
    public boolean toggleFavorite(User user, UUID beachId) {
        if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
            removeFavorite(user, beachId);
            cacheManager.evictUserFavorites(user.getId());  // ← 명시적 무효화
            return false;
        } else {
            addFavorite(user, beachId);
            cacheManager.evictUserFavorites(user.getId());  // ← 명시적 무효화
            return true;
        }
    }
}

@Component
public class UserFavoriteCacheManager {
    
    @CacheEvict(value = "beachSummaries", key = "'user:' + #userId")
    public void evictUserFavorites(UUID userId) {
        // Spring이 캐시 무효화 처리
    }
}
```

**장점:**
- ✅ 캐시 로직 중앙화
- ✅ 테스트 용이

**단점:**
- ⚠️ 추가 클래스 필요 (복잡도 증가)
- ⚠️ 오버엔지니어링 가능성

### 대안 2: Facade 패턴으로 외부 호출 보장

```java
@Service
public class UserFavoriteFacade {
    
    private final UserFavoriteService favoriteService;
    
    public UserFavoriteFacade(UserFavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }
    
    /**
     * Facade를 통한 토글 (외부 호출로 AOP 적용)
     */
    @Transactional
    public boolean toggleFavorite(User user, UUID beachId) {
        if (favoriteService.isFavorite(user, beachId)) {
            favoriteService.removeFavorite(user, beachId);  // ← 외부 호출 (AOP 적용)
            return false;
        } else {
            favoriteService.addFavorite(user, beachId);      // ← 외부 호출 (AOP 적용)
            return true;
        }
    }
}

// Controller에서 Facade 사용
@RestController
@RequestMapping("/api/favorites")
public class UserFavoriteController {
    
    private final UserFavoriteFacade favoriteFacade;
    
    @PutMapping("/{beachId}/toggle")
    public ResponseEntity<?> toggleFavorite(
            @AuthenticationPrincipal User user, 
            @PathVariable UUID beachId) {
        boolean isFavorite = favoriteFacade.toggleFavorite(user, beachId);
        return ResponseEntity.ok(Map.of("isFavorite", isFavorite));
    }
}
```

**장점:**
- ✅ 기존 Service 메서드 재사용
- ✅ 외부 호출로 AOP 정상 적용
- ✅ 복잡한 비즈니스 로직 조율에 유리
- ✅ 계층 분리 명확 (Controller ↔ Facade ↔ Service)

**단점:**
- ⚠️ 추가 클래스 필요
- ⚠️ 간단한 경우 오버엔지니어링 가능성

**적용 시점:**
- 여러 서비스를 조율해야 할 때
- 복잡한 비즈니스 로직이 필요할 때
- 트랜잭션 경계를 명확히 분리하고 싶을 때

### 대안 3: @Autowired로 자기 자신 주입받기 (비추천)

```java
@Service
public class UserFavoriteService {
    
    @Autowired
    private UserFavoriteService self;  // ← 자기 자신 주입
    
    @Transactional
    public boolean toggleFavorite(User user, UUID beachId) {
        if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
            self.removeFavorite(user, beachId);  // ← 프록시를 통한 자기 호출
            return false;
        } else {
            self.addFavorite(user, beachId);
            return true;
        }
    }
}
```

**장점:**
- ✅ 기존 `@CacheEvict` 그대로 활용
- ✅ 프록시를 통해 AOP 적용 가능

**단점:**
- ❌ 안티패턴으로 간주됨
- ❌ 순환 참조 위험 (Spring이 런타임에 해결하긴 함)
- ❌ 코드 가독성 저하 (self 참조가 혼란스러움)
- ❌ 유지보수성 떨어짐

---

## 🧪 검증 방법

### 추가할 통합 테스트

**파일:** `src/test/java/com/beachcheck/integration/UserFavoriteServiceIntegrationTest.java`

```java
/**
 * P1-05: 토글 후 캐시가 무효화되어야 함
 * 
 * Why: toggleFavorite() 내부 호출 시 @CacheEvict가 작동하지 않는 문제 검증
 * 
 * Policy: toggleFavorite() 호출 시 beachSummaries 캐시 무효화되어야 함
 * 
 * Contract(Input): 찜하지 않은 상태에서 토글
 * Contract(Output): 캐시에서 해당 사용자 키가 삭제됨
 */
@Test
@DisplayName("P1-05: 토글 후 캐시가 무효화되어야 함")
void toggleFavorite_shouldEvictCache() {
    // given: 캐시 워밍업 (getFavoriteBeaches 호출로 캐시 생성)
    List<Beach> firstResult = favoriteService.getFavoriteBeaches(user1);
    
    Cache cache = cacheManager.getCache("beachSummaries");
    String cacheKey = "user:" + user1.getId();
    
    // 캐시에 데이터가 있는지 확인
    assertThat(cache.get(cacheKey)).isNotNull();
    
    // when: 토글 실행 (찜 추가)
    boolean result = favoriteService.toggleFavorite(user1, beach1.getId());
    
    // then: 추가 성공
    assertThat(result).isTrue();
    
    // then: 캐시가 무효화되어야 함
    assertThat(cache.get(cacheKey)).isNull();  // ❌ 현재는 실패 (내부 호출로 AOP 미적용)
}

/**
 * P1-06: 직접 addFavorite 호출 시 캐시 무효화 (비교군)
 * 
 * Why: 직접 호출 시 @CacheEvict가 정상 작동하는지 비교 검증
 * 
 * Policy: addFavorite() 직접 호출 시 beachSummaries 캐시 무효화
 * 
 * Contract(Input): 캐시가 있는 상태에서 addFavorite 호출
 * Contract(Output): 캐시에서 해당 사용자 키가 삭제됨
 */
@Test
@DisplayName("P1-06: 직접 addFavorite 호출 시 캐시 무효화 (비교군)")
void addFavorite_shouldEvictCache() {
    // given: 캐시 워밍업
    List<Beach> firstResult = favoriteService.getFavoriteBeaches(user1);
    
    Cache cache = cacheManager.getCache("beachSummaries");
    String cacheKey = "user:" + user1.getId();
    assertThat(cache.get(cacheKey)).isNotNull();
    
    // when: 직접 addFavorite 호출
    favoriteService.addFavorite(user1, beach2.getId());
    
    // then: 캐시 무효화됨 (✅ 정상 동작)
    assertThat(cache.get(cacheKey)).isNull();
}
```

**테스트 목적:**
- P1-05: toggleFavorite() 호출 시 캐시 무효화 실패 재현 (현재 버그 상태)
- P1-06: 직접 호출 시 정상 동작 확인 (비교군)
- 해결 후 P1-05 테스트 통과 확인

**테스트 위치:**
- 기존 `UserFavoriteServiceIntegrationTest` 클래스에 추가
- `@Autowired CacheManager cacheManager` 이미 존재
- `user1`, `beach1`, `beach2` 픽스처 재사용

---

## 💡 학습 포인트

### 1. Spring AOP Self-Invocation 제약 이해

**핵심 개념:**
- Spring AOP는 **프록시 패턴**으로 구현됨
- 내부 메서드 호출(`this.method()`)은 프록시를 거치지 않음
- 따라서 `@Transactional`, `@CacheEvict`, `@Async` 등 모든 AOP 기능이 작동하지 않음

**일반화:**
```java
// ❌ 작동하지 않는 패턴
@Service
public class MyService {
    
    public void publicMethod() {
        internalMethod();  // ← AOP 미적용 (this 참조)
    }
    
    @CacheEvict  // 또는 @Transactional, @Async 등
    private void internalMethod() {
        // ...
    }
}
```

### 2. 해결 방법 선택 기준

| 방법 | 적용 시점 | 복잡도 | 유지보수성 |
|------|----------|--------|-----------|
| **직접 어노테이션 추가** | 간단한 경우, 호출 경로가 적을 때 | 낮음 | 높음 |
| **캐시 매니저 분리** | 캐시 로직이 복잡하거나 재사용이 많을 때 | 중간 | 중간 |
| **Facade 패턴** | 여러 서비스 조율이 필요하거나 복잡한 비즈니스 로직일 때 | 중간 | 높음 |
| **Self-Injection** | 레거시 코드 또는 제약 상황 | 낮음 | 낮음 (비추천) |

**선택 기준:**
- 간단한 경우: 직접 어노테이션 추가 (명확성 우선)
- 캐시 로직 중앙화 필요: 캐시 매니저 분리 (재사용성 우선)
- 복잡한 비즈니스 로직: Facade 패턴 (책임 분리 우선)
- Self-Injection은 마지막 수단으로만 고려

### 3. 캐시 정합성 문제의 심각성

**문제 특성:**
- DB는 정상 업데이트되지만 캐시만 stale 상태
- **사용자는 실패했다고 착각** (찜이 반영 안 된 것처럼 보임)
- 여러 번 시도하면 DB 중복 에러 발생 가능
- 캐시 TTL 만료 전까지 지속 → 장시간 UX 저하

**교훈:**
- 캐시 무효화는 **쓰기 작업의 필수 요소**
- 테스트 시 캐시 동작도 함께 검증 필요
- 통합 테스트로 AOP 동작 확인 필수

### 4. 프록시 기반 AOP 한계 인지

**Self-Invocation 문제가 발생하는 모든 Spring AOP 기능**

| 어노테이션 | 미적용 시 문제                           | 심각도 |
|-----------|------------------------------------|--------|
| `@Transactional` | 트랜잭션이 시작되지 않음 → 롤백 안 됨, 데이터 정합성 문제 | 🔴 Critical |
| `@CacheEvict` / `@Cacheable` | 캐시 무효화/조회 실패 → Stale 데이터 반환        | 🟡 High |
| `@Async` | 비동기 실행 안 됨 → 동기로 실행되어 성능 저하        | 🟡 High |
| `@PreAuthorize` / `@Secured` | 권한 체크 건너뜀 → **보안 취약점**             | 🔴 Critical |
| `@Aspect` (커스텀 AOP) | 로깅, 모니터링 등 커스텀 로직 미실행              | 🟢 Medium |

**예시: @Transactional 문제**
```java
@Service
public class OrderService {
    
    // ❌ 트랜잭션이 시작되지 않음
    public void processOrder(Order order) {
        saveOrder(order);  // ← 내부 호출
        // 예외 발생 시 롤백 안 됨!
    }
    
    @Transactional
    private void saveOrder(Order order) {
        orderRepository.save(order);
        paymentRepository.save(order.getPayment());
    }
}
```

**예시: @PreAuthorize 보안 문제**
```java
@Service
public class AdminService {
    
    // ❌ 권한 체크 안 됨 (보안 취약점!)
    public void updateUser(Long userId) {
        deleteUserData(userId);  // ← 내부 호출, 권한 체크 건너뜀
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    private void deleteUserData(Long userId) {
        userRepository.deleteById(userId);
    }
}
```

---

**실무 대응 방안**

| 단계 | 방법 | 구체적 액션 |
|------|------|------------|
| **설계** | AOP가 필요한 메서드는 public으로 노출 | - 내부 호출 최소화<br>- 필요하면 별도 클래스 분리 |
| **구현** | Self-Invocation 패턴 회피 | - 직접 어노테이션 추가<br>- Facade 패턴 적용 |
| **테스트** | 통합 테스트로 AOP 동작 검증 | - 실제 Spring Context 사용<br>- Mock 의존 줄이기 |
| **리뷰** | 내부 호출 패턴 체크리스트 적용 | - `this.method()` 패턴 찾기<br>- AOP 어노테이션 확인 |

### 5. 테스트 전략

**단위 테스트만으로는 부족:**
- Mock을 사용하면 AOP 문제를 놓칠 수 있음
- 실제 Spring Context가 필요한 통합 테스트 필수

**효과적인 테스트 구성:**
```java
// ✅ 통합 테스트로 캐시 동작 검증
@SpringBootTest
class CacheIntegrationTest {
    @Autowired CacheManager cacheManager;
    @Autowired MyService service;
    
    @Test
    void shouldEvictCache() {
        // given: 캐시 생성
        // when: 메서드 호출
        // then: 실제 캐시 확인
    }
}
```

## 📚 참고 자료

### Spring AOP 공식 문서
- [Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)

---

## 🔗 관련 이슈/PR

- **발견:** PR 코드 리뷰 중 발견
- **우선순위:** Medium
- **이슈 타입:** `bug` (캐시 정합성 이슈)

---

## 🔄 변경 이력

| 날짜 | 변경 내용 |
|:---:|:---|
| 2026-01-16 | 초기 문서 작성 - toggleFavorite 캐시 무효화 이슈 |

---

