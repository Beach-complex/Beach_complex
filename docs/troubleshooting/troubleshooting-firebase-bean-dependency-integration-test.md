# Troubleshooting: Firebase Bean Dependency in Integration Tests

> 목적: Firebase가 필요하지 않은 통합 테스트에서 FirebaseMessaging 빈 의존성 문제로 인한 Spring Context 로딩 실패 기록

---

## 0) 메타 정보

- **Mode:** `DEV`
- **Status:** `Resolved`
- **작성자:** 박건우(@geonusp)
- **작성일:** 2026-02-20
- **컴포넌트:** test, firebase, spring-boot, dependency-injection
- **환경:** local (test profile)
- **관련 이슈/PR:** PB-88 (OutboxPublisher 구현)
- **키워드:** NoSuchBeanDefinitionException, FirebaseMessaging, @ConditionalOnBean, @SpringBootTest, ApplicationContext

---

## 1) 요약 (3줄)

- **무슨 문제였나:** FirebaseMessaging을 사용하지 않는 통합 테스트 55개가 NoSuchBeanDefinitionException으로 실패
- **원인:** SchedulingConfig → OutboxPublisher → FirebaseMessaging 의존성 체인으로 인해 test 환경에서 ApplicationContext 로딩 실패
- **해결:** FirebaseTestConfig(@TestConfiguration)를 생성하여 IntegrationTest/ApiTest 베이스 클래스에 Import, 모든 테스트에 FirebaseMessaging Mock 자동 제공

## 1-1) 학습 포인트

- **Fast checks (3):**
  1. Log: `NoSuchBeanDefinitionException` 스택트레이스에서 어떤 빈을 요구하는지 확인
  2. Config/Env: `application-test.yml`에서 `app.firebase.enabled=false` 확인
  3. Infra/Dependency: `@ConditionalOnBean`의 빈 생성 순서 및 평가 시점 이해 필요
- **Rule of thumb (필수):** @SpringBootTest는 전체 ApplicationContext를 로드하므로, 특정 빈을 사용하지 않는 테스트라도 해당 빈의 의존성을 만족해야 Context 로딩이 성공한다. 조건부 빈 생성 시 의존성 체인 전체를 고려해야 함.
- **Anti-pattern:** FirebaseMessaging을 사용하는 테스트에만 `@MockBean FirebaseMessaging`을 추가하는 것으로 충분하다고 생각하는 것. 실제로는 해당 빈을 직접 사용하지 않아도 ApplicationContext 로딩 시 간접 의존성으로 인해 필요할 수 있음.

---

## 2) 증상 (Symptom)

### 관측된 현상
- 전체 통합 테스트 58개 중 55개 실패, 3개만 성공
- `OutboxPublisherIntegrationTest`는 성공 (이 테스트는 `@MockBean FirebaseMessaging` 보유)
- `OutboxEventIntegrationTest`, `BeachDetailScenarioIntegrationTest`, `ReservationControllerIntegrationTest`, `UserFavoriteServiceIntegrationTest` 모두 실패
- 실패한 테스트들은 모두 Spring Context 로딩 실패 (`NoSuchBeanDefinitionException`)
- Firebase와 무관한 테스트들도 모두 실패 (OutboxPublisher를 직접 사용하지 않아도 실패)

### 에러 메시지 / 스택트레이스 (필수)
```text
Exception encountered during context initialization - cancelling refresh attempt:
org.springframework.beans.factory.UnsatisfiedDependencyException:
Error creating bean with name 'schedulingConfig' defined in file [...\SchedulingConfig.class]:
Unsatisfied dependency expressed through constructor parameter 0:
Error creating bean with name 'outboxPublisher' defined in file [...\OutboxPublisher.class]:
Unsatisfied dependency expressed through constructor parameter 2:
No qualifying bean of type 'com.google.firebase.messaging.FirebaseMessaging' available:
expected at least 1 bean which qualifies as autowire candidate. Dependency annotations: {}
```

**의존성 체인:**
```
SchedulingConfig (생성자 parameter 0)
  → OutboxPublisher (생성자 parameter 2)
    → FirebaseMessaging ❌ (빈이 없음)
```

**테스트 실패 예시:**
```text
UserFavoriteServiceIntegrationTest > P1-01: 토글로 찜 추가 성공 FAILED
    java.lang.IllegalStateException at DefaultCacheAwareContextLoaderDelegate.java:180
        Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException

OutboxEvent 통합 테스트 > Repository 테스트 > countByStatus() > 상태별 이벤트 개수 조회 FAILED
    java.lang.IllegalStateException at DefaultCacheAwareContextLoaderDelegate.java:145

(... 총 55개 테스트 모두 동일한 패턴으로 실패)
```

### 발생 조건 / 빈도
- **언제:** `OutboxEventIntegrationTest` 실행 시 (또는 FirebaseMessaging 빈이 없는 모든 통합 테스트)
- **빈도:** 항상 (100% 재현)
- **환경:** `@ActiveProfiles("test")` 환경에서 `app.firebase.enabled=false`일 때

---

## 3) 영향 범위 (DEV 기준 최소 작성)

- **영향받는 기능:**
  - 전체 통합 테스트 58개 중 55개 실패
  - OutboxEvent Repository 테스트 (9개 실패)
  - BeachDetailScenarioIntegrationTest (실패)
  - ReservationControllerIntegrationTest (15개 실패)
  - UserFavoriteServiceIntegrationTest (16개 실패)
  - Firebase/Notification과 완전히 무관한 기능들도 모두 영향받음
- **영향받는 사용자/데이터:** 개발자 로컬 테스트 환경, CI/CD 테스트 파이프라인
- **심각도(개발 단계):** `High`
  - 전체 통합 테스트의 95% (55/58)가 실행되지 않아 기능 개발/테스트 진행이 완전 차단됨
  - 모든 개발자가 같은 문제를 겪을 것으로 예상

---

## 4) 재현 방법 (Reproduction)

### 전제 조건
- **브랜치/커밋:** feat/PB-88-outbox-task-queue-02-publisher-scheduler-polling
- **의존성/버전:**
  - Java 21
  - Spring Boot 3.x
  - Gradle 8.7
  - PostgreSQL (Testcontainers)
- **환경변수/설정:**
  - `SPRING_PROFILES_ACTIVE=test`
  - `application-test.yml`에 `app.firebase.enabled: false`
  - `application-test.yml`에 `app.outbox.polling.enabled: false`

### 재현 절차
1. 브랜치 체크아웃: `git checkout feat/PB-88-outbox-task-queue-02-publisher-scheduler-polling`
2. 통합 테스트 실행: `./gradlew.bat test --tests "com.beachcheck.integration.*"`
3. 결과 확인: 58개 중 55개 실패 (`NoSuchBeanDefinitionException`)

### 재현 입력/데이터
```bash
cd C:/Users/pro/Documents/GitHub/Beach_complex
./gradlew.bat clean test --tests "com.beachcheck.integration.*"
```

---

## 5) 원인 분석 (Root Cause)

### 가설 목록
- [x] 가설 1: OutboxPublisher가 FirebaseMessaging을 요구해서 실패 → `@ConditionalOnBean` 추가로 해결 시도
- [x] 가설 2: NotificationService가 FirebaseMessaging을 요구해서 실패 → `@ConditionalOnBean` 추가로 해결 시도
- [x] 가설 3: NotificationController가 NotificationService를 요구해서 실패 → `@ConditionalOnBean` 추가로 해결 시도
- [x] 가설 4: SchedulingConfig가 OutboxPublisher를 요구해서 실패 → `@Autowired(required=false)` + null 체크로 해결 시도
- [ ] 가설 5: 다른 빈(SecurityConfig, AsyncConfig 등)이 간접적으로 FirebaseMessaging 의존성을 가지고 있음 (미확인)
- [ ] 가설 6: `@ConditionalOnBean`의 빈 생성 순서 문제로 조건 평가 시점에 FirebaseMessaging 존재 여부를 정확히 판단하지 못함 (미확인)

### 근거 (로그/코드/설정/DB 상태)
- **로그/지표:**
  - `NoSuchBeanDefinitionException: No qualifying bean of type 'com.google.firebase.messaging.FirebaseMessaging' available`
  - `UnsatisfiedDependencyException` 중첩:
    1. `Error creating bean with name 'schedulingConfig'`
    2. → `Unsatisfied dependency expressed through constructor parameter 0`
    3. → `Error creating bean with name 'outboxPublisher'`
    4. → `Unsatisfied dependency expressed through constructor parameter 2`
    5. → `FirebaseMessaging` 빈 없음
  - 의존성 체인: `SchedulingConfig` → `OutboxPublisher` → `FirebaseMessaging`

- **코드 포인트:**
  - `FirebaseConfig.java:17-21`: `@ConditionalOnProperty(prefix = "app.firebase", name = "enabled", havingValue = "true")`
  - `OutboxPublisher.java:19`: `@ConditionalOnBean(FirebaseMessaging.class)` 추가
  - `NotificationService.java:15-16`: `@ConditionalOnBean(FirebaseMessaging.class)` 추가
  - `NotificationController.java:20-21`: `@ConditionalOnBean(NotificationService.class)` 추가
  - `SchedulingConfig.java:36`: `@Autowired(required=false) OutboxPublisher`

- **설정 포인트:**
  - `application-test.yml:68-69`: `app.firebase.enabled: false`
  - `application-test.yml:71-73`: `app.outbox.polling.enabled: false`

- **테스트 설정:**
  - `OutboxEventIntegrationTest`: `@MockBean FirebaseMessaging` 없음
  - `OutboxPublisherIntegrationTest`: `@MockBean FirebaseMessaging` 있음 → 성공
  - 둘 다 `IntegrationTest` 베이스 클래스 상속

### 최종 원인 (One-liner)
- `@SpringBootTest`가 전체 ApplicationContext를 로드할 때, `SchedulingConfig` → `OutboxPublisher` → `FirebaseMessaging` 의존성 체인으로 인해 test 환경(`app.firebase.enabled=false`)에서 존재하지 않는 `FirebaseMessaging` 빈을 요구하여 Context 로딩이 실패함. 모든 통합 테스트가 동일한 ApplicationContext를 공유하므로, Firebase를 사용하지 않는 테스트들도 모두 실패.

---

## 6) 해결 (Fix)

### 시도한 해결책들 (실패)

#### 시도 1: OutboxPublisher에 @ConditionalOnBean 추가
```java
@Service
@ConditionalOnBean(FirebaseMessaging.class)  // FirebaseMessaging 빈이 있을 때만 생성
public class OutboxPublisher {
    private final FirebaseMessaging firebaseMessaging;
    // ...
}
```
**변경 범위:** OutboxPublisher.java만

**기대 효과:** FirebaseMessaging 빈이 없으면 OutboxPublisher가 생성되지 않아 의존성 문제 해결

**실제 결과:**
- 테스트: OutboxEventIntegrationTest 9개 테스트 모두 실패
- 에러: `NoSuchBeanDefinitionException: No qualifying bean of type 'com.google.firebase.messaging.FirebaseMessaging'`
- **실패 원인:** SchedulingConfig가 생성자에서 OutboxPublisher를 필수 주입으로 요구 → Spring이 OutboxPublisher 빈 생성 시도 → FirebaseMessaging 빈 필요 → 빈이 없어서 실패. `@ConditionalOnBean`은 "다른 빈이 이 빈을 요구하지 않을 때"만 작동하지만, SchedulingConfig가 명시적으로 요구하므로 조건이 무시됨.

#### 시도 2: SchedulingConfig에도 @ConditionalOnBean 추가
```java
@Configuration
@EnableScheduling
@ConditionalOnBean(OutboxPublisher.class)  // 추가
public class SchedulingConfig {
    private final OutboxPublisher outboxPublisher;
    // ...
}
```
**변경 범위:** 시도 1 + SchedulingConfig.java 추가

**기대 효과:** OutboxPublisher가 없으면 SchedulingConfig도 생성되지 않아 의존성 문제 해결

**실제 결과:**
- 테스트: OutboxEventIntegrationTest 9개 테스트 모두 실패
- 에러: `NoSuchBeanDefinitionException: No qualifying bean of type 'com.google.firebase.messaging.FirebaseMessaging'`
- **실패 원인:** `@ConditionalOnBean(OutboxPublisher.class)`는 "OutboxPublisher 빈이 존재할 때만" SchedulingConfig를 생성한다는 의미인데, Spring은 OutboxPublisher 빈을 생성하려고 시도하고 → FirebaseMessaging이 없어서 실패. 즉, SchedulingConfig 생성 여부와 무관하게 OutboxPublisher 빈 생성 시도 자체는 발생함.

#### 시도 3: SchedulingConfig에 @Autowired(required=false) 적용
```java
@Configuration
@EnableScheduling
@ConditionalOnBean(OutboxPublisher.class)
public class SchedulingConfig {
    private final OutboxPublisher outboxPublisher;

    public SchedulingConfig(@Autowired(required = false) OutboxPublisher outboxPublisher) {  // 추가
        this.outboxPublisher = outboxPublisher;
    }

    @Scheduled(...)
    @ConditionalOnProperty(...)
    public void scheduleOutboxPolling() {
        if (outboxPublisher != null) {  // null 체크 추가
            outboxPublisher.processPendingOutboxEvents();
        }
    }
}
```
**변경 범위:** 시도 1+2 + SchedulingConfig 생성자에 @Autowired(required=false) 및 null 체크 추가

**기대 효과:** OutboxPublisher 빈이 없어도 null로 주입되어 SchedulingConfig 생성 가능

**실제 결과:**
- 테스트: OutboxEventIntegrationTest 9개 테스트 모두 실패
- 에러: `NoSuchBeanDefinitionException: No qualifying bean of type 'com.google.firebase.messaging.FirebaseMessaging'`
- **실패 원인:** `@Autowired(required=false)`는 "빈이 없으면 null을 주입"하는 것이지, "빈 생성 시도를 하지 않는 것"이 아님. Spring은 여전히 OutboxPublisher 빈을 생성하려고 시도 → FirebaseMessaging 없음 → 실패. 즉, 조건부 주입과 조건부 빈 생성은 다른 개념.

#### 시도 4: NotificationService에 @ConditionalOnBean 추가
```java
@Service
@ConditionalOnBean(FirebaseMessaging.class)  // 추가
public class NotificationService {
    private final FirebaseMessaging firebaseMessaging;
    // ...
}
```
**변경 범위:** 시도 1+2+3 + NotificationService.java 추가

**기대 효과:** NotificationService도 조건부로 생성하여 FirebaseMessaging 없을 때 제외

**실제 결과:**
- 테스트: OutboxEventIntegrationTest 9개 테스트 모두 실패
- 에러: `NoSuchBeanDefinitionException: No qualifying bean of type 'com.google.firebase.messaging.FirebaseMessaging'`
- **실패 원인:** NotificationService는 OutboxPublisher와 독립적인 빈이므로, 이를 조건부로 만들어도 OutboxPublisher → FirebaseMessaging 의존성 문제는 해결되지 않음. 즉, 의존성 체인의 잘못된 지점에 조건을 적용.

#### 시도 5: NotificationController에 @ConditionalOnBean 추가
```java
@RestController
@RequestMapping("/api/notifications")
@ConditionalOnBean(NotificationService.class)  // 추가
public class NotificationController {
    private final NotificationService notificationService;
    // ...
}
```
**변경 범위:** 시도 1+2+3+4 + NotificationController.java 추가

**기대 효과:** 모든 Firebase 의존 컴포넌트를 조건부로 만들어 완전 격리

**실제 결과:**
- 테스트: OutboxEventIntegrationTest 9개 테스트 모두 실패
- 에러: `NoSuchBeanDefinitionException: No qualifying bean of type 'com.google.firebase.messaging.FirebaseMessaging'`
- **실패 원인:** NotificationController는 SchedulingConfig → OutboxPublisher 의존성 체인과 무관하므로, 이를 조건부로 만들어도 근본적인 문제는 해결되지 않음. 핵심 의존성 체인(SchedulingConfig → OutboxPublisher → FirebaseMessaging)에서 FirebaseMessaging이 없으면 OutboxPublisher 생성 자체가 실패하므로, 주변 컴포넌트를 조건부로 만드는 것은 효과 없음.

### 해결 전략 (후보)

#### 후보 1: FirebaseTestConfig 생성 + 베이스 클래스에 Import (✅ 최종 선택)
- **접근:**
  1. `@TestConfiguration`으로 FirebaseMessaging Mock 빈을 제공하는 설정 클래스 생성
  2. ApiTest, IntegrationTest 베이스 클래스에 `@Import(FirebaseTestConfig.class)` 추가
  3. 모든 하위 테스트에 자동으로 FirebaseMessaging Mock 제공
- **예상 효과:**
  - 베이스 클래스 2개만 수정하면 모든 통합/API 테스트에 적용
  - 개별 테스트마다 `@MockBean` 중복 불필요
  - OutboxPublisher/NotificationService/NotificationController의 `@ConditionalOnBean` 제거 가능 (유지 보수성 향상)
  - Spring Context가 로드될 때 FirebaseMessaging 빈이 있으므로 모든 의존성 만족
- **리스크:**
  - Firebase 안 쓰는 테스트에도 Mock이 포함되지만, Mock 객체 생성 오버헤드는 거의 없음 (마이크로초 단위, Spring Context 로딩 시간에 비해 거의 영향 없음)
  
- **구현 예시:**
```java
// src/test/java/com/beachcheck/config/FirebaseTestConfig.java
@TestConfiguration
public class FirebaseTestConfig {
    @Bean
    @Primary
    public FirebaseMessaging firebaseMessaging() {
        return Mockito.mock(FirebaseMessaging.class);
    }
}

// ApiTest.java, IntegrationTest.java
@Import({TestcontainersConfig.class, FirebaseTestConfig.class})
```

#### 후보 2: @ConditionalOnBean으로 조건부 빈 생성 (❌ 시도했으나 실패)
- **접근:**
  1. OutboxPublisher, NotificationService, NotificationController에 `@ConditionalOnBean(FirebaseMessaging.class)` 추가
  2. SchedulingConfig에서 OutboxPublisher를 `@Autowired(required=false)`로 조건부 주입
  3. FirebaseMessaging 빈이 없으면 해당 빈들이 생성되지 않도록 조건 설정
- **예상 효과:**
  - test 환경에서 FirebaseMessaging이 없으면 관련 빈들이 자동으로 제외됨
  - 프로덕션 환경에서는 정상 동작
- **실패 원인:**
  - SchedulingConfig가 OutboxPublisher를 생성자에서 주입받으면서 Spring이 OutboxPublisher 빈 생성을 시도
  - `@ConditionalOnBean`의 평가 시점 문제: SchedulingConfig 생성 시점에 FirebaseMessaging 존재 여부를 판단하지만, 이미 OutboxPublisher 생성이 요구됨
  - `@Autowired(required=false)`로 처리해도 여전히 빈 생성 시도는 일어남 (빈이 없으면 null 주입되지만, 빈 생성 자체는 시도됨)
- **리스크:**
  - 빈 생성 순서와 조건 평가 시점을 정확히 제어하기 어려움
  - 의존성 체인이 복잡할 경우 예측 불가능한 동작
- **개선 가능성:**
  - SchedulingConfig를 완전히 제거하고 OutboxPublisher 내부에서 `@Scheduled` 직접 사용
  - 또는 SchedulingConfig 자체에도 `@ConditionalOnBean(OutboxPublisher.class)` 추가 (하지만 순환 참조 위험)

#### 후보 3: @SpringBootTest(classes = ...) 사용
- **접근:** 테스트별로 필요한 빈만 명시적으로 로드
- **예상 효과:** 불필요한 빈 로딩 방지, 의존성 문제 회피
- **리스크:**
  - 모든 통합 테스트마다 classes 명시 필요 (유지보수 부담)
  - 누락된 빈이 있으면 테스트 실패
  - IntegrationTest 베이스 클래스 패턴과 충돌 가능

#### 후보 4: Slice Tests 사용 (@DataJpaTest 등)
- **접근:** Repository 테스트는 @DataJpaTest로 JPA 관련 빈만 로드
- **예상 효과:** 전체 ApplicationContext 로딩 회피
- **리스크:**
  - 통합 테스트 범위 축소 (실제 환경과 차이)
  - Testcontainers와의 통합 재설정 필요
  - IntegrationTest 베이스 클래스 사용 불가

#### 후보 5: @ConditionalOnProperty 대신 @Profile 사용
- **접근:** Firebase 관련 빈들을 `@Profile("!test")`로 test 환경에서 제외
- **예상 효과:** 프로파일 기반으로 명확한 빈 제외
- **리스크:**
  - OutboxPublisher를 테스트하는 통합 테스트에서는 여전히 Mock 필요
  - 프로덕션 환경에서도 profile 관리 필요



### 변경 사항
- **신규 파일:**
  - `src/test/java/com/beachcheck/config/FirebaseTestConfig.java`: @TestConfiguration으로 FirebaseMessaging Mock 빈 제공
- **수정 파일:**
  - `src/test/java/com/beachcheck/base/IntegrationTest.java`: @Import에 FirebaseTestConfig 추가
  - `src/test/java/com/beachcheck/base/ApiTest.java`: @Import에 FirebaseTestConfig 추가
- **커밋:** `fix: PB-88 통합 테스트 FirebaseMessaging 의존성 문제 해결`

### 주의/부작용
- Firebase를 사용하지 않는 테스트에도 Mock 객체가 포함되지만, Mockito.mock() 생성 오버헤드는 무시할 수준 (마이크로초 단위, Spring Context 로딩 시간에 비해 거의 영향 없음)
- 개별 테스트에서 특정 동작 설정이 필요한 경우 @MockBean으로 override 가능 (예: OutboxPublisherIntegrationTest에서 given/when 설정)
- @Primary 설정으로 실제 FirebaseConfig보다 우선순위 높게 설정되어 충돌 없음

---

## 7) 검증 (Verification)

### 해결 확인
- [x] 동일 재현 절차로 더 이상 발생하지 않음
- [x] OutboxEventIntegrationTest 전체 테스트 통과 (9개)
- [x] OutboxPublisherIntegrationTest 여전히 통과 (3개, 회귀 없음)
- [x] 전체 통합 테스트 58개 모두 통과
- [x] 로컬 실행/빌드 정상

### 실행한 커맨드/테스트
```bash
cd C:/Users/pro/Documents/GitHub/Beach_complex
./gradlew.bat clean test --tests "com.beachcheck.integration.*"

# 결과:
# BUILD SUCCESSFUL in 49s
# 58 tests completed (이전: 55 failed, 3 passed → 현재: 58 passed)
```

### 추가 확인(선택)
- 다른 통합 테스트들도 정상 동작하는지 확인
- CI/CD 파이프라인에서도 정상 통과하는지 확인

---

## 8) 재발 방지 (Prevention)

### 방지 조치 체크리스트
- [x] **테스트 추가**: 통합 테스트 58개 모두 정상 통과 확인
- [x] **문서화**: 이 트러블슈팅 문서 작성 완료
- [ ] **가드레일**: 새로운 외부 의존성 추가 시 test 환경에서 Mock 제공 여부 확인 (체크리스트 추가 필요)
- [ ] **로깅 개선**: ApplicationContext 로딩 시 조건부 빈 생성 여부를 DEBUG 로그로 확인 가능하도록 개선 (선택 사항)
- [ ] **CI/CD 개선**: 통합 테스트를 CI/CD 파이프라인에 추가하여 조기 감지 (후속 작업)

### 남은 작업(Action Items)
- [x] TODO 1: 정확한 의존성 체인 파악 완료 (SchedulingConfig → OutboxPublisher → FirebaseMessaging)
- [x] TODO 2: 해결책 선택 및 적용 완료 (FirebaseTestConfig 방식)
- [x] TODO 3: 통합 테스트 전체 실행 완료 (58개 모두 통과)
- [x] TODO 4: 트러블슈팅 문서화 완료
- [ ] TODO 5: CI/CD 파이프라인에 통합 테스트 추가 (후속 PR에서 진행)

---

## 추가 컨텍스트 (Additional Context)

### 관련 파일 목록
```
src/main/java/com/beachcheck/
├── config/
│   ├── FirebaseConfig.java          (app.firebase.enabled 조건부 빈 생성)
│   └── SchedulingConfig.java        (OutboxPublisher 의존, @Autowired(required=false))
├── controller/
│   └── NotificationController.java  (NotificationService 의존, @ConditionalOnBean 추가)
├── service/
│   ├── OutboxPublisher.java         (FirebaseMessaging 의존, @ConditionalOnBean 추가)
│   └── NotificationService.java     (FirebaseMessaging 의존, @ConditionalOnBean 추가)

src/test/java/com/beachcheck/
├── base/
│   ├── IntegrationTest.java         (통합 테스트 베이스 클래스, FirebaseTestConfig Import)
│   └── ApiTest.java                 (API 테스트 베이스 클래스, FirebaseTestConfig Import)
├── config/
│   └── FirebaseTestConfig.java      (신규 추가: FirebaseMessaging Mock 제공)
└── integration/
    ├── OutboxEventIntegrationTest.java       (해결 후 성공 - 베이스 클래스에서 Mock 자동 제공)
    └── OutboxPublisherIntegrationTest.java   (성공 유지 - @MockBean FirebaseMessaging 보유)

src/test/resources/
└── application-test.yml             (app.firebase.enabled: false)
```

### 의존성 체인 (확정)
**문제 발생 시:**
```
ApplicationContext 로딩
└─ SchedulingConfig (@Configuration)
   └─ OutboxPublisher (생성자 주입)
      └─ FirebaseMessaging (생성자 주입) ❌ 빈이 없음! → Context 로딩 실패
```

**해결 후:**
```
ApplicationContext 로딩 (test 환경)
├─ FirebaseTestConfig (@TestConfiguration)
│  └─ FirebaseMessaging (Mock 빈 생성) ✅ @Primary로 제공
└─ SchedulingConfig (@Configuration)
   └─ OutboxPublisher (생성자 주입)
      └─ FirebaseMessaging (생성자 주입) ✅ Mock 빈 주입 성공!
```

### 성공 케이스 vs 실패 케이스 비교
| 항목 | OutboxPublisherIntegrationTest (성공) | OutboxEventIntegrationTest (실패) |
|------|--------------------------------------|-----------------------------------|
| FirebaseMessaging Mock | ✅ `@MockBean` 있음 | ❌ 없음 |
| OutboxPublisher 사용 | ✅ 직접 사용 (`@Autowired`) | ❌ 사용 안함 |
| Spring Context 로딩 | ✅ 성공 | ❌ 실패 (NoSuchBeanDefinitionException) |
| 테스트 대상 | OutboxPublisher 비즈니스 로직 | OutboxEventRepository 쿼리 메서드 |

---

## 참고 자료 (References)

- Spring Boot Conditional Annotations: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.condition-annotations
- Reflectoring - Spring Boot Conditionals: https://reflectoring.io/spring-boot-conditionals/
- Spring Boot Slice Tests: https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.testing.slice-tests
- Mockito Mock Creation Performance: https://github.com/mockito/mockito/issues/1797
- Faster Spring Boot Tests (Mock vs Context Reloading): https://saile.it/quick-tip-faster-spring-boot-tests/
