# GitHub Actions CI 통합테스트 실패 완전 해결 가이드

**컴포넌트:** infra

## ✅ 상태: 해결됨

**해결 날짜:** 2026-01-15

---

## 📌 요약

### 핵심 문제
**CI에서만 통합테스트 실패: EmailSenderService 빈 생성(설정 누락)으로 ApplicationContext 로딩 실패**

### 근본 원인
실패는 테스트 로직 문제가 아니라 **Spring ApplicationContext 초기화 실패**였다.

**원인 체인:**
```
AuthController → AuthService → EmailVerificationService → EmailSenderService(근본 원인)
```

CI 환경에서 `app.mail.default-from`이 비어있어 EmailSenderService 생성자가 fail-fast로 `IllegalArgumentException` 발생:
```
java.lang.IllegalArgumentException: app.mail.default-from must be configured
```

### 임시 해결
테스트 환경에 `app.mail.default-from` 더미 값을 추가하여 CI 통과

### 추후 근본 해결
- `enabled=false`일 때 메일 Sender 빈이 생성되지 않도록 **조건부 빈** 구성
- 또는 **Null Object Pattern**으로 분리

---

## 🚨 증상 (Symptoms)

### 환경
- **플랫폼:** GitHub Actions CI
- **테스트:** `UserFavoriteServiceIntegrationTest` (통합 테스트)
- **결과:** 로컬 통과 / CI 실패
- **실행 시간:** 약 45초 부근에서 실패

### 대표 에러 (요약)
```
java.lang.IllegalStateException at DefaultCacheAwareContextLoaderDelegate
  Caused by: UnsatisfiedDependencyException (연쇄)
  Caused by: IllegalArgumentException (Assert.java 계열)
```

### GitHub Actions 타임라인 (2026-01-14~15)

```
✅ CI #59 (2:51 PM) - [test] PB-64 - 통과 ← 마지막 정상
❌ CI #60 (3:03 PM) - [feat] PB-65 (이메일 인증) - 실패 시작
❌ CI #61 (3:09 PM) - [feat] PB-65 - 실패
✅ CI #62 (3:50 PM) - Merge PR #144 to main - 통과 (?)
❌ CI #63 (5:03 PM) - [test] PB-64 - 실패 (main 동기화 후)
❌ CI #64 (5:10 PM) - [test] PB-64 - 실패
... (계속 실패)
```

---

## 🔍 관찰된 로그 (Key Log Evidence)

### 문제 1: 기본 요약 로그의 한계
기본 요약 로그에서는 **원인 빈 이름이 잘려서** 확인이 어려웠다.

```
UserFavoriteServiceIntegrationTest > P1-04: 찜 제거 시 캐시 무효화 FAILED
    java.lang.IllegalStateException at DefaultCacheAwareContextLoaderDelegate.java:180
        Caused by: org.springframework.beans.factory.UnsatisfiedDependencyException at ConstructorResolver.java:795
            (여러 중첩...)
            Caused by: java.lang.IllegalArgumentException at Assert.java:240
```

### 문제 2: 상세 로그에서 진실 발견

`./gradlew test --stacktrace --debug` 실행 후:

```
Error creating bean with name 'authController'
  -> required bean 'authService'
  -> required bean 'emailVerificationService'
  -> required bean 'emailSenderService'
  -> Failed to instantiate [EmailSenderService]: Constructor threw exception
```

### 문제 3: Root Cause 확정

```
java.lang.IllegalArgumentException: app.mail.default-from must be configured
    at org.springframework.util.Assert.hasText(Assert.java:xxx)
    at com.beachcheck.service.EmailSenderService.<init>(EmailSenderService.java:25)
```

**결론:** 통합테스트 실패가 아니라, **컨텍스트 초기화 단계**에서 메일 관련 빈 생성이 실패하여 테스트가 실행되기 전에 중단됨.

---

## 💡 원인 분석 (Root Cause Analysis)

### 1. 왜 "찜 통합테스트"가 메일 때문에 죽었나?

**통합테스트는 전체 ApplicationContext를 로딩한다**
- `@SpringBootTest` 기반으로 전체(또는 대부분) 애플리케이션 컨텍스트를 로딩
- 테스트가 직접 `EmailSenderService`를 호출하지 않아도, 컨텍스트 로딩 과정에서 `@Controller`/`@Service` 빈들이 생성되며 의존성이 연쇄적으로 주입됨

**의존성 체인:**
```
[Spring Context 초기화]
  └─ AuthController Bean 생성
      └─ AuthService 필요
          └─ EmailVerificationService 필요
              └─ EmailSenderService 필요
                  └─ 생성자: app.mail.default-from 검증
                      └─ ❌ 값 없음!
                      └─ IllegalArgumentException 발생
                      └─ Context 로딩 실패
                      └─ 모든 테스트 실행 불가
```

### 2. 로컬 OK / CI FAIL의 이유

| 환경 | 이메일 기능 | app.mail.default-from | 결과 |
|------|------------|----------------------|------|
| **로컬** | 구현 안 됨 | 필요 없음 | ✅ 통과 |
| **CI (초기)** | 구현 안 됨 | 필요 없음 | ✅ 통과 |
| **CI (머지 후)** | 구현됨 | 설정 없음 (빈 문자열) | ❌ 실패 |

**상세 설명:**

1. **로컬에서 통과하는 이유:**
   - 로컬 코드베이스에는 아직 **이메일 인증 기능이 구현되지 않음**
   - `EmailSenderService` 클래스 자체가 존재하지 않음
   - 따라서 `app.mail.default-from` 설정이 필요 없음
   - ✅ 테스트 통과

2. **CI에서 실패하는 이유:**
   - PR #144 (이메일 인증 기능)가 main에 머지됨
   - `EmailSenderService`가 추가되었고, 생성자에서 `app.mail.default-from` **필수 검증**
   - PR #142가 main을 동기화하면서 새로운 코드를 가져옴
   - 하지만 `application-test.yml`에는 메일 설정이 없음
   - ❌ Bean 생성 실패 → Context 로딩 실패

### 3. 왜 갑자기 CI가 실패했는가?

#### 🎯 확정된 시나리오: 팀원의 이메일 인증 플로우 머지가 원인

```
[main 브랜치]
  └─ PR #144 (PB-65 이메일 인증 플로우) 머지 (3:50 PM)
      └─ EmailSenderService 클래스 추가
          └─ 생성자에서 app.mail.default-from 필수 검증
      └─ application.yml 업데이트: app.mail.* ✅
      └─ application-test.yml 미업데이트 ❌

[PR #142 브랜치 (PB-64)]
  └─ main에서 rebase/merge (5:00 PM 전후)
      └─ EmailSenderService 변경사항 가져옴
      └─ BUT: application-test.yml은 여전히 누락
      
  └─ 통합 테스트 수정 & 실행
      └─ Spring Context 로딩 시도
      └─ EmailSenderService Bean 생성 시도
      └─ application-test.yml에 app.mail.* 없음!
      └─ ❌ IllegalArgumentException
```

#### 변경 사항 추적

**이전 상태 (CI #59 통과):**
- 이메일 인증 기능 없음
- EmailSenderService 존재하지 않음
- ✅ 정상 동작

**PR #144 추가:**
- EmailSenderService 클래스 도입
- 생성자에서 `app.mail.default-from` **필수 검증** 추가
- application.yml: `app.mail.*` 추가 ✅
- application-test.yml: 업데이트 누락 ❌

**PR #142 main 동기화:**
- EmailSenderService를 포함한 변경사항 가져옴
- 통합 테스트 수정 → Spring Context 전체 로딩
- EmailSenderService Bean 생성 시도
- ❌ `app.mail.default-from` 없음 → 즉시 실패

---

## 🛠️ 해결 과정 (Resolution)

### 시도 1: JWT 설정 문제 추적 ❌

**가설:** `JwtProperties` 설정 누락이 원인

**시도한 것:**
```yaml
# application-test.yml 수정
jwt:  # ❌ 잘못됨
  secret: ...

# 수정
app:
  jwt:  # ✅ 올바름
    secret: ...
```

**결과:** JWT 문제는 해결했지만, **여전히 CI 실패**

**교훈:** 
- JWT 설정 문제도 있었지만 주 원인은 아니었음
- 하나의 에러가 다른 에러를 가릴 수 있음

### 문제 구체화: CI 로그 레벨 상향으로 진짜 원인 발견 🔍

**문제 상황:**
- 기본 로그에서는 `IllegalArgumentException at Assert.java:240`만 보임
- 어떤 Bean이 문제인지 알 수 없음

**취한 조치:**
```yaml
# .github/workflows/ci.yml
- name: Build and Test
  run: ./gradlew test --stacktrace --debug  # ← --debug 추가
```

**결과:** **드디어 진짜 원인 발견!**

```
Error creating bean with name 'emailSenderService'
  -> required bean 'authService'
  -> required bean 'emailVerificationService'
  -> required bean 'emailSenderService'
...
Caused by: java.lang.IllegalArgumentException: 
  app.mail.default-from must be configured
```

**핵심:**
- `--debug` 플래그로 Bean 생성 과정 전체를 추적
- EmailSenderService가 메일 설정 누락으로 실패하는 것을 확인
- 이제 정확한 해결 방향 수립 가능

### 시도 2-1: enabled=false 설정 ❌

**가설:** `enabled=false` 설정하면 빈 생성이 자동으로 막힐 것

**시도한 것:**
```yaml
# src/test/resources/application-test.yml
app:
  mail:
    enabled: false  # ← 빈 생성 차단 시도
```

**결과:** **여전히 CI 실패**

**원인:**
- `enabled=false`는 단순한 설정값일 뿐
- 코드에서 `@ConditionalOnProperty`로 조건부 빈 등록을 구현하지 않으면 빈 생성이 막히지 않음
- EmailSenderService는 여전히 생성 시도됨 → `app.mail.default-from` 필수 검증 실패

**교훈:**
- 설정 값만으로는 빈 생성을 막을 수 없음
- 조건부 빈 등록은 코드 레벨에서 구현 필요

### 시도 2-2: default-from 더미 값 추가 ✅

**상황:**
- 로컬에서는 이메일 기능이 구현되지 않아 문제 재현 불가
- CI 환경에서만 발생하는 문제라 직접 확인이 어려움

**적용한 해결책:**
```yaml
# src/test/resources/application-test.yml
app:
  mail:
    enabled: false
    default-from: no-reply@test.com  # ← 임시 더미 값 추가
```

**결과:** ✅ **CI 통과!**

**의도:**
- EmailSenderService 생성자의 필수 검증을 통과시키기 위한 임시 조치
- 실제 메일 발송은 `enabled=false`로 의도적으로 비활성화 (단, 코드 수정 필요)

**한계:**
- 근본 해결이 아닌 임시방편
- `enabled=false`가 실제로 동작하려면 코드 수정 필요 (조건부 빈 등록)

---

## ✅ 최종 해결 방법

### 1. 임시 조치 (빠른 CI 복구) - 적용됨

**파일:** `src/test/resources/application-test.yml`

```yaml
app:
  mail:
    enabled: false
    default-from: no-reply@test.com
```

**장점:**
- ✅ 즉시 CI 복구
- ✅ 최소한의 변경

**단점:**
- ❌ 근본 해결 아님
- ❌ enabled=false가 실제로 빈 생성을 막지는 않음

### 2. 근본 해결 - 향후 적용

#### 방법 A: 조건부 빈 등록

```java
@ConditionalOnProperty(
    prefix = "app.mail", 
    name = "enabled", 
    havingValue = "true"
)
@Service
public class EmailSenderService {
    private final JavaMailSender mailSender;
    private final String defaultFrom;
    
    public EmailSenderService(
            JavaMailSender mailSender,
            @Value("${app.mail.default-from}") String defaultFrom) {
        Assert.hasText(defaultFrom, "app.mail.default-from must be configured");
        this.mailSender = mailSender;
        this.defaultFrom = defaultFrom;
    }
    // ...
}
```

**테스트 설정:**
```yaml
app:
  mail:
    enabled: false  # ← 빈 생성 자체가 안 됨
    default-from: ${APP_MAIL_DEFAULT_FROM:${SPRING_MAIL_USERNAME:no-reply@test.com}}
```

**문제점:**
- `EmailVerificationService`가 `EmailSenderService`를 필수 의존하면 DI가 깨짐
- 해결책: Null Object Pattern 필요

#### 방법 B: 인터페이스 + Null Object Pattern (추천) ⭐

**1단계: 인터페이스 도입 (범용 이메일 전송)**

```java
public interface EmailSender {
    void send(String from, String to, String subject, String body);
}
```

**2단계: 실제 구현 (SMTP)**

```java
@Service
@ConditionalOnProperty(
    prefix = "app.mail",
    name = "enabled",
    havingValue = "true"
)
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailSender;
    private final String defaultFrom;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.default-from}") String defaultFrom) {
        Assert.hasText(defaultFrom, "app.mail.default-from must be configured");
        this.mailSender = mailSender;
        this.defaultFrom = defaultFrom;
    }

    @Override
    public void send(String from, String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        String resolvedFrom = (from == null || from.isBlank()) ? defaultFrom : from;
        message.setFrom(resolvedFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
```

**3단계: Null Object 구현 (테스트/개발 환경용)**

```java
@Service
@ConditionalOnProperty(
    prefix = "app.mail",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true  // 설정 없으면 기본적으로 Null Object 사용
)
public class NoopEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(NoopEmailSender.class);

    @Override
    public void send(String from, String to, String subject, String body) {
        log.info("[NOOP] Would send email - from: {}, to: {}, subject: {}",
                 from, to, subject);
    }
}
```

**4단계: EmailVerificationService 수정**

```java
@Service
@Transactional
public class EmailVerificationService {

    private final EmailSender emailSender;  // ← 인터페이스 의존 (타입만 변경)
    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

    // 생성자 및 기타 메서드는 동일

    private void sendEmail(String to, String token) {
        String link = baseUrl + "?token=" + token;
        String subject = "이메일 인증";
        String body = """
            아래 링크를 클릭하여 이메일을 인증해주세요:

            %s

            이 링크는 %d분 후에 만료됩니다.
            """.formatted(link, tokenExpirationMinutes);

        emailSender.send(fromAddress, to, subject, body);  // ← 메서드 호출 동일
    }
}
```

**장점:**
- ✅ 테스트 환경에서 메일 관련 설정 불필요
- ✅ 운영 환경에서만 필수 설정 검증
- ✅ 외부 의존성이 통합테스트 안정성을 깨지 않음
- ✅ 로그로 메일 발송 의도 확인 가능 (Null Object Pattern)

#### 방법 C: 테스트 Mock 처리 (대안)

```java
@TestConfiguration
public class TestEmailConfig {
    
    @Bean
    @Primary
    public EmailSender mockEmailSender() {
        return Mockito.mock(EmailSender.class);
    }
}
```

**단점:**
- Mock 설정을 모든 테스트에 적용해야 함
- 테스트 복잡도 증가
- 로컬에서 email 기능 구현이 안되어 있음

---

## 🎓 학습 포인트

### 1. 통합테스트는 전체 Context를 로딩한다

**착각:**
> "UserFavoriteServiceIntegrationTest는 찜 기능만 테스트하니까 메일 빈은 생성안되겠지?"

**현실:**
```
@SpringBootTest
  └─ 전체 ApplicationContext 로딩
      └─ 모든 @Controller, @Service, @Repository 빈 생성
          └─ 의존성 주입 체인을 따라 필요한 빈들이 연쇄적으로 생성됨
              └─ 하나라도 실패하면 전체 실패
```

**교훈:**
- 통합테스트는 애플리케이션 전체의 건강성을 검증하는 것
- "내가 안 쓰는 빈"도 영향을 줄 수 있음

### 2. Fail-Fast 원칙의 양날의 검

**EmailSenderService 생성자:**
```java
public EmailSenderService(..., String defaultFrom) {
    Assert.hasText(defaultFrom, "app.mail.default-from must be configured");
    // ...
}
```

**장점:**
- ✅ 잘못된 설정으로 애플리케이션이 시작되는 것을 방지
- ✅ 운영 환경에서 메일 발송 실패를 조기 발견

**단점:**
- ❌ 테스트 환경에서도 동일한 검증 적용됨
- ❌ 외부 의존성이 테스트를 깨트릴 수 있음

**해결책:**
- 조건부 빈 등록 (`@ConditionalOnProperty`)
- Null Object Pattern으로 외부 의존성 분리

### 3. 로컬 vs CI 환경 차이의 중요성

| 특성 | 로컬 환경 | GitHub Actions CI |
|------|-----------|-------------------|
| 코드베이스 | 이메일 기능 구현 전 | main 머지 후 (이메일 기능 포함) |
| EmailSenderService | 존재하지 않음 | 존재하며 필수 검증 수행 |
| 설정 요구사항 | app.mail.* 불필요 | app.mail.default-from 필수 |
| 테스트 결과 | ✅ 통과 | ❌ 실패 (설정 누락) |

**교훈:**
- **로컬과 CI의 코드베이스가 다를 수 있다** - 로컬에서 최신 main을 pull하지 않으면 차이 발생
- CI가 "더 까다로운" 것이 아니라 **main 브랜치의 최신 상태를 반영**하는 것
- 로컬에서 통과한다고 해서 CI에서도 통과하는 것은 아님

### 4. 에러 로그 레벨의 중요성

**기본 로그 (--stacktrace):**
```
Caused by: java.lang.IllegalArgumentException at Assert.java:240
```
→ **원인 불명확**

**상세 로그 (--debug):**
```
Error creating bean with name 'emailSenderService'
...
Caused by: java.lang.IllegalArgumentException: 
  app.mail.default-from must be configured
```
→ **원인 명확**

**교훈:**
- CI 실패 시 `--debug` 옵션 활용
- 로그를 아티팩트로 저장하여 사후 분석

---

## 🔧 로깅/디버깅 팁

### 1. Gradle 옵션으로 상세 스택 출력

```bash
# 기본
./gradlew test

# 스택 트레이스 출력
./gradlew test --stacktrace

# 상세 정보 출력
./gradlew test --stacktrace --info

# 디버그 레벨 (최상세)
./gradlew test --stacktrace --debug
```

### 2. CI에서 원인 빈 이름이 안 보일 때

**CI 워크플로우 수정:**

```yaml
# .github/workflows/ci.yml
- name: Build and Test
  run: ./gradlew test --stacktrace --debug  # ← --debug 추가
  
# 또는 실패 시에만 상세 로그
- name: Build and Test
  run: ./gradlew test --stacktrace --info
  
- name: Re-run with debug on failure
  if: failure()
  run: ./gradlew test --stacktrace --debug
```

### 3. 테스트 리포트 업로드 (Artifacts)

```yaml
- name: Upload test reports on failure
  if: failure()
  uses: actions/upload-artifact@v3
  with:
    name: test-reports
    path: |
      build/reports/tests/test/
      build/test-results/test/
    retention-days: 7
```

**장점:**
- 실패한 테스트의 HTML 리포트 확인
- 스택 트레이스 전문 확인
- 팀원들과 공유 가능

### 4. Bean 생성 과정 추적

**로깅 레벨 추가:**

```yaml
# application-test.yml
logging:
  level:
    org.springframework.beans: DEBUG  # Bean 생성 과정
    org.springframework.context: DEBUG  # Context 초기화
```

---

## 🚀 재현 방법 (How to Reproduce)

### 로컬에서 CI 환경 재현

```bash
# 1. 클린 빌드
./gradlew clean

# 2. 테스트 전용 프로파일로 실행
./gradlew test --no-daemon --stacktrace --info

# 3. 환경 변수 없이 실행 (CI 환경 모방)
# 리눅스 
# 모든 환경 변수를 비우기 때문에 주의
env -i ./gradlew test --stacktrace
# PATH(기본 명령어 경로)와 JAVA_HOME(자바 위치)은 살려두고 나머지만 비우기
env -i PATH=$PATH JAVA_HOME=$JAVA_HOME ./gradlew test --stacktrace
# 윈도우 (PowerShell)
# 특정 환경 변수(예: JAVA_TOOL_OPTIONS)를 비우고(;) 이어서 테스트 실행
$env:JAVA_TOOL_OPTIONS=""; ./gradlew test --stacktrace

# 4. 특정 테스트만 실행 (예: UserFavoriteServiceIntegrationTest)
./gradlew test --tests "UserFavoriteServiceIntegrationTest" --stacktrace --debug
```

### PR 머지 결과 기준으로 재현

```bash
# main 브랜치 최신 상태로 동기화
git fetch origin
git checkout <branch-name>
git rebase origin/main  # 또는 merge

# 테스트 실행
./gradlew clean test --stacktrace --info
```

---

## 📊 시도 이력 요약

| 단계 | 활동 | 결과 | 교훈 |
|------|------|------|------|
| **시도 1** | JWT 설정 누락 수정 | ⚠️ 부분 해결 | JWT 문제도 있었지만 주 원인 아님. 하나의 에러가 다른 에러를 가릴 수 있음 |
| **디버깅** | CI 로그 레벨 상향 (--debug) | ✅ 원인 발견 | EmailSenderService의 메일 설정 누락 확인 |
| **시도 2-1** | enabled=false 설정 | ❌ 실패 | 설정값만으로는 빈 생성을 막을 수 없음. 코드에서 조건부 빈 등록 필요 |
| **시도 2-2** | default-from 더미 값 추가 | ✅ **임시 해결** | CI 복구 성공. 로컬에서 재현 불가로 임시방편 선택 |
| **향후 계획** | 조건부 빈/Noop 구현 | 📋 예정 | 외부 의존성을 테스트 환경과 분리 |

---

## 🎯 체크리스트 (Post-Merge)

### 즉시 확인 사항
- [x] `src/test/resources/application-test.yml`에 테스트 전용 더미 설정 추가
- [x] JWT 설정 프리픽스 수정 (`jwt` → `app.jwt`)
- [x] CI에서 `--debug` 플래그 추가 (디버깅 개선)
- [x] 로컬에서 `./gradlew clean test` 실행 확인
- [x] CI 통과 확인

### 향후 개선 사항
- [ ] `enabled=false`가 실제로 빈 생성을 막도록 조건부 빈 + Null Object Pattern 설계 반영 (PR 분리 권장)
- [ ] 메일 관련 설정의 "필수 검증"은 prod에서만 강제되도록 위치 조정
- [ ] CI에서 테스트 리포트 자동 업로드 설정
- [ ] 설정 파일 검증 테스트 추가

---

## 💡 예방 가이드

### 외부 의존성 추가 시 체크리스트

#### 1. 빈 생성 전략 결정

```
새로운 외부 의존성 추가 (메일, SMS, 결제 등)
  ↓
  질문: 테스트 환경에서도 필수인가?
  ↓
  NO → 조건부 빈 + Null Object Pattern
  YES → 테스트 설정 파일 업데이트
```

#### 2. 조건부 빈 패턴 적용

```java
// 실제 구현
@Service
@ConditionalOnProperty(prefix="app.mail", name="enabled", havingValue="true")
public class SmtpEmailSender implements EmailSender { }

// Null Object 구현
@Service
@ConditionalOnProperty(prefix="app.mail", name="enabled", havingValue="false", matchIfMissing=true)
public class NoopEmailSender implements EmailSender { }
```

#### 3. 모든 프로파일 설정 동기화

**체크리스트:**
- [ ] `application.yml` (운영) 업데이트
- [ ] `application-test.yml` (테스트) 업데이트
- [ ] `application-local.yml` (로컬 개발) 업데이트 (있다면)
- [ ] `docker-compose.yml` 환경 변수 추가

#### 4. PR 리뷰 시 확인사항

**코드 변경 시:**
- [ ] 새로운 `@ConfigurationProperties` 추가?
- [ ] 생성자에서 `Assert` 검증 사용?
- [ ] 외부 API/서비스 호출?

**설정 파일 변경 시:**
- [ ] 모든 프로파일에 반영되었는가?
- [ ] 필수 값이 누락되지 않았는가?
- [ ] CI 환경에서도 동작하는가?

---

## 🔗 관련 자료

### Spring 공식 문서
- [Conditional Bean Registration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.condition-annotations)
- [Configuration Properties](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config.typesafe-configuration-properties)
- [Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)

### 관련 파일
- `src/main/java/com/beachcheck/service/EmailSenderService.java` - 메일 발송 서비스
- `src/main/java/com/beachcheck/service/EmailVerificationService.java` - 이메일 인증 서비스
- `src/test/resources/application-test.yml` - 테스트 설정 파일
- `.github/workflows/ci.yml` - CI 워크플로우

---

## 📝 최종 요약

### 문제의 본질
- **표면:** 찜 통합테스트 실패
- **실제:** EmailSenderService 빈 생성 실패로 ApplicationContext 초기화 실패
- **근본:** 외부 의존성(메일)이 테스트 환경 설정 없이 필수 검증을 수행

### 해결의 핵심
1. **임시:** 테스트 설정에 더미 값 추가 → CI 복구
2. **근본:** 조건부 빈 + Null Object Pattern → 외부 의존성 분리

### 교훈
- ✅ 통합테스트는 전체 Context를 로딩한다 - "내가 안 쓰는 빈"도 영향
- ✅ 외부 의존성은 조건부 빈으로 관리하라 - enabled=false 시 생성 차단
- ✅ CI가 더 엄격한 것이 아니라 더 정확한 것이다
- ✅ --debug 플래그는 문제 해결의 열쇠

---

## 📅 작성 정보

- **작성일:** 2026-01-15
- **상태:** ✅ **임시 해결 완료 / 근본 해결 계획 중**
- **관련 이슈:** CI 테스트 실패 (EmailSenderService Bean 생성 실패)
- **영향받은 테스트:** UserFavoriteServiceIntegrationTest 및 모든 통합 테스트
- **수정 파일:**
  - `src/test/resources/application-test.yml` (임시 해결)
  - `.github/workflows/ci.yml` (디버깅 개선)

---

## 🔄 변경 이력

| 날짜 | 변경 내용 |
|:---:|:---|
| 2026-01-15 | 통합 문서 작성 - 모든 시도 이력 및 최종 해결 방법 정리 |
| 2026-01-15 | 임시 해결 완료 - application-test.yml에 메일 더미 설정 추가 |
| 2026-01-15 | CI 디버깅 개선 - --debug 플래그 추가 |

---

