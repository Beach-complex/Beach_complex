# Troubleshooting: Mock 호출 횟수 누적으로 인한 테스트 실패

> 목적: 통합 테스트에서 공유 Mock 빈의 호출 기록이 테스트 간 격리되지 않아 발생한 검증 실패 기록

---

## 0) 메타 정보

- **Mode:** `DEV`
- **Status:** `Resolved`
- **작성자:** 박건우(@geonusp)
- **작성일:** 2026-02-24
- **컴포넌트:** test, mockito, integration-test
- **환경:** local (test profile)
- **관련 이슈/PR:** PB-88 (OutboxPublisher 구현)
- **키워드:** TooManyActualInvocations, MockBean, clearInvocations, test isolation

---

## 1) 요약 (3줄)

- **무슨 문제였나:** OutboxPublisherIntegrationTest에서 `firebaseMessaging.send()` 호출 횟수 검증 시 1번 기대했으나 4번으로 실패
- **원인:** FirebaseTestConfig에서 제공하는 Mock 빈이 모든 테스트 메서드 간 공유되어 호출 기록이 누적됨
- **해결:** @BeforeEach에 `clearInvocations(firebaseMessaging)` 추가하여 각 테스트 시작 전 호출 기록 초기화

## 1-1) 학습 포인트

- **Fast checks (3):**
  1. Log: TooManyActualInvocations 에러 메시지에서 wanted vs actual 횟수 확인
  2. Config/Env: @TestConfiguration으로 제공되는 Mock 빈은 싱글톤으로 공유됨
  3. Infra/Dependency: Mockito Mock의 invocation 기록은 명시적으로 초기화하지 않으면 유지됨
- **Rule of thumb (필수):** @TestConfiguration으로 제공하는 Mock 빈을 여러 테스트에서 검증할 때는 @BeforeEach에서 clearInvocations()로 호출 기록을 초기화해야 테스트 간 격리가 보장된다. Mockito는 clearInvocations() 사용을 권장하지 않지만("Try to avoid at all costs"), @MockBean 대신 @TestConfiguration을 선택했기에 불가피하게 사용한다. listener/callback 예외는 clearInvocations()가 아닌 verifyNoMoreInteractions()에 해당한다.
- **Anti-pattern:** @TestConfiguration으로 제공하는 싱글톤 Mock 빈을 테스트에서 검증할 때 @BeforeEach에 clearInvocations()를 추가하지 않는 것. Mock 빈은 테스트 간 공유되므로 invocation 기록이 누적되어 호출 횟수 검증이 오염된다.

---

## 2) 증상 (Symptom)

### 관측된 현상
- OutboxPublisherIntegrationTest 실행 시 TC1 실패
- 58개 통합 테스트 중 1개 실패 (OutboxPublisherIntegrationTest.shouldProcessPendingEventAndMarkAsSent)
- 다른 테스트들은 모두 성공

### 에러 메시지 / 스택트레이스 (필수)
```text
OutboxPublisherIntegrationTest > TC1 - PENDING 이벤트를 폴링하여 FCM 전송 후 SENT 상태로 전이 FAILED
    org.mockito.exceptions.verification.TooManyActualInvocations at OutboxPublisherIntegrationTest.java:94

org.mockito.exceptions.verification.TooManyActualInvocations:
firebaseMessaging.send(
    <any com.google.firebase.messaging.Message>
);
Wanted 1 time:
-> at com.google.firebase.messaging.FirebaseMessaging.send(FirebaseMessaging.java:91)
But was 4 times:
-> at com.beachcheck.service.OutboxPublisher.dispatchAndUpdateOutboxEvent(OutboxPublisher.java:87)
-> at com.beachcheck.service.OutboxPublisher.dispatchAndUpdateOutboxEvent(OutboxPublisher.java:87)
-> at com.beachcheck.service.OutboxPublisher.dispatchAndUpdateOutboxEvent(OutboxPublisher.java:87)
-> at com.beachcheck.service.OutboxPublisher.dispatchAndUpdateOutboxEvent(OutboxPublisher.java:87)
```

**호출 횟수 분석:**
- TC1 (shouldProcessPendingEventAndMarkAsSent): 1개 이벤트 처리 → 1번 호출
- TC2 (shouldProcessMultiplePendingEvents): 3개 이벤트 처리 → 3번 호출
- TC3 (shouldSkipAlreadySentNotification): FCM 호출 없음 → 0번 호출
- **총합: 1 + 3 + 0 = 4번**

### 발생 조건 / 빈도
- **언제:** OutboxPublisherIntegrationTest 전체 실행 시 (단일 테스트 실행 시에는 성공)
- **빈도:** 항상 (100% 재현)
- **환경:** @SpringBootTest + FirebaseTestConfig 사용하는 모든 통합 테스트

---

## 3) 영향 범위 (DEV 기준 최소 작성)

- **영향받는 기능:** OutboxPublisherIntegrationTest 1개 테스트 케이스
- **영향받는 사용자/데이터:** 개발자 로컬 테스트 환경, CI/CD
- **심각도(개발 단계):** `Medium`
  - 기능 개발은 가능하지만 통합 테스트 신뢰성 저하
  - CI/CD에서 빌드 실패 가능성

---

## 4) 재현 방법 (Reproduction)

### 전제 조건
- **브랜치/커밋:** test/pb-81-authn-authz-min-it (OutboxPublisher 구현 완료 상태)
- **의존성/버전:**
  - Java 21
  - Spring Boot 3.x
  - Mockito 5.x
  - Gradle 8.7
- **환경변수/설정:**
  - `SPRING_PROFILES_ACTIVE=test`
  - FirebaseTestConfig가 IntegrationTest에 @Import되어 있음

### 재현 절차
1. OutboxPublisherIntegrationTest 전체 실행
2. TC1이 TooManyActualInvocations로 실패 확인

### 재현 입력/데이터
```bash
./gradlew.bat test --tests "OutboxPublisherIntegrationTest"
```

---

## 5) 원인 분석 (Root Cause)

### 가설 목록
- [x] 가설 1: Mock 호출 기록이 테스트 간 격리되지 않음 → clearInvocations() 누락
- [ ] 가설 2: 스케줄러가 실행되어 추가 호출 발생 (배제: application-test.yml에서 polling.enabled=false)
- [ ] 가설 3: FirebaseTestConfig의 Mock 빈이 매번 새로 생성되지 않음 (배제: @TestConfiguration은 싱글톤 제공이 기본)

### 근거 (로그/코드/설정/DB 상태)
- **로그/지표:**
  - 에러 메시지: "Wanted 1 time: ... But was 4 times"
  - 4번 = TC1(1) + TC2(3) + TC3(0) → 테스트 간 누적 확인

- **코드 포인트:**
  - `FirebaseTestConfig.java`: `@Bean @Primary FirebaseMessaging` (싱글톤 Mock 제공)
  - `OutboxPublisherIntegrationTest.java:54`: `@Autowired FirebaseMessaging` (공유 Mock 주입)
  - `OutboxPublisherIntegrationTest.java:94`: `then(firebaseMessaging).should().send(any(Message.class))` (검증)
  - `OutboxPublisherIntegrationTest.java:56-65`: `@BeforeEach`에서 데이터는 초기화하지만 Mock 호출 기록은 초기화 안함

- **설정 포인트:**
  - `IntegrationTest.java`: `@Import({TestcontainersConfig.class, FirebaseTestConfig.class})`
  - 모든 IntegrationTest 하위 클래스가 동일한 FirebaseMessaging Mock 빈 공유

### 최종 원인 (One-liner)
- FirebaseTestConfig에서 제공하는 싱글톤 Mock 빈이 모든 테스트 메서드 간 공유되는데, @BeforeEach에서 Mock의 invocation 기록을 초기화하지 않아 호출 횟수가 누적되어 검증 실패

---

## 6) 해결 (Fix)

### 해결 전략
- **접근:** @BeforeEach에서 `clearInvocations(firebaseMessaging)` 호출하여 각 테스트 시작 전 Mock 호출 기록 초기화. reset()과 달리 Stubbing(given/willReturn)은 유지하면서 invocation만 클리어하므로 기존 테스트 로직 변경 불필요.

### 변경 사항
- **수정 파일:**
  - `src/test/java/com/beachcheck/integration/OutboxPublisherIntegrationTest.java`
    - import 추가: `import static org.mockito.Mockito.clearInvocations;`
    - @BeforeEach 첫 줄에 `clearInvocations(firebaseMessaging);` 추가

```diff
  @BeforeEach
  void setUp() throws FirebaseMessagingException {
+   // Mock 호출 기록 초기화 (테스트 간 격리)
+   clearInvocations(firebaseMessaging);
+
    // 테스트 전에 데이터 정리 (FK 제약 조건 순서 고려)
    outboxEventRepository.deleteAll();
    notificationRepository.deleteAll();
    userRepository.deleteAll();

    // FirebaseMessaging Mock 기본 동작 설정
    given(firebaseMessaging.send(any(Message.class))).willReturn("mock-message-id");
  }
```

### 주의/부작용
- clearInvocations()는 호출 기록만 클리어하고 stubbing은 유지하므로 부작용 없음
- 다른 IntegrationTest 하위 클래스에서도 동일한 패턴 적용 필요 시 참고

---

## 7) 검증 (Verification)

### 해결 확인
- [ ] 동일 재현 절차로 더 이상 발생하지 않음
- [ ] OutboxPublisherIntegrationTest 전체 테스트 통과 (3개)
- [ ] 전체 통합 테스트 58개 모두 통과
- [ ] 로컬 실행/빌드 정상

### 실행한 커맨드/테스트
```bash
./gradlew.bat test --tests "OutboxPublisherIntegrationTest"
# 결과: 3 tests completed (예상)

./gradlew.bat test --tests "com.beachcheck.integration.*"
# 결과: 58 tests completed (예상)
```

### 추가 확인(선택)
- 테스트 실행 순서 변경 시에도 정상 동작하는지 확인
- 다른 Mock 빈 사용하는 테스트에도 동일한 패턴 적용 검토

---

## 8) 재발 방지 (Prevention)

### 방지 조치 체크리스트
- [x] **테스트 추가**: 기존 테스트에 clearInvocations 추가로 해결
- [ ] **문서화**: 통합 테스트 작성 가이드에 "공유 Mock 사용 시 clearInvocations 필수" 추가
- [ ] **가드레일**: 새로운 IntegrationTest 작성 시 체크리스트에 Mock 격리 확인 항목 추가
- [ ] **코드 리뷰 체크리스트**: Mock 검증 사용하는 테스트에서 clearInvocations 확인

### 남은 작업(Action Items)
- [ ] TODO 1: 다른 IntegrationTest 하위 클래스에서 공유 Mock 사용 시 동일한 패턴 적용 여부 확인

---

## 참고 자료 (References)

- Stack Overflow - Correct alternative to resetting a mock: https://stackoverflow.com/questions/19441538/what-is-the-correct-alternative-to-resetting-a-mock-after-setup-in-mockito
  - Mockito는 clearInvocations() 사용을 권장하지 않음 ("Try to avoid at all costs" - Mockito javadoc)
  - listener/callback 예외는 clearInvocations()가 아닌 verifyNoMoreInteractions()에 해당: "method interactions are specifically a documented part of the API" (Jeff Bowman)
- @MockitoBean (Spring Framework 6.2.0): https://docs.spring.io/spring-framework/docs/6.2.0/javadoc-api/org/springframework/test/context/bean/override/mockito/MockitoBean.html
  - "The default is MockReset.AFTER meaning that mocks are automatically reset after each test method is invoked." (공식 문서)
  - MockReset은 @MockitoBean/@MockitoSpyBean에서만 동작함. @TestConfiguration Mock은 clearInvocations()로 직접 초기화 필요

 