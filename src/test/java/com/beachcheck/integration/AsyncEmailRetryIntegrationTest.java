package com.beachcheck.integration;

import static com.beachcheck.fixture.EmailVerificationTestFixtures.emailUser;
import static com.beachcheck.fixture.UniqueTestFixtures.uniqueEmail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.domain.User;
import com.beachcheck.repository.EmailVerificationTokenCleanupRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.service.AsyncEmailService;
import com.beachcheck.service.EmailSender;
import com.beachcheck.service.EmailVerificationService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
    properties = {
      "app.email.retry.max-attempts=3",
      "app.email.retry.delay-ms=10",
      "app.email.retry.multiplier=1"
    })
@DisplayName("TC4: AsyncEmailService 재시도 통합 테스트")
class AsyncEmailRetryIntegrationTest extends IntegrationTest {

  // 비동기 실행 지연을 고려해 경험적으로 잡은 여유 버퍼
  private static final int ASYNC_TIMEOUT_BUFFER_MS = 2500;
  private static final int FAIL_UNTIL_ATTEMPT = 2;

  private static final String USER_EMAIL = "retry@test.com";
  private static final String VERIFICATION_LINK =
      "http://localhost:8080/api/auth/verify?token=retry";

  @Autowired private EmailVerificationService emailVerificationService;
  @Autowired private EmailVerificationTokenCleanupRepository tokenCleanupRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @Value("${app.email.retry.max-attempts}")
  private int retryMaxAttempts;

  @Value("${app.email.retry.delay-ms}")
  private long retryDelayMs;

  @SpyBean private AsyncEmailService asyncEmailService;
  @MockBean private EmailSender emailSender;

  private final Set<UUID> createdUserIds = new HashSet<>();
  private TransactionTemplate transactionTemplate;
  private int asyncTimeoutMs;

  @BeforeEach
  void setUp() {
    transactionTemplate = new TransactionTemplate(transactionManager);
    asyncTimeoutMs = calculateAsyncTimeoutMs();
  }

  @Test
  @DisplayName("TC4-01: 메일 전송이 일시 실패하면 재시도 후 성공한다")
  void sendVerificationEmailAsync_retryThenSuccess() {
    // given
    givenEmailSenderFailsThenSucceeds();

    // when
    asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    // then
    assertRetriedSendCountTo(USER_EMAIL, retryMaxAttempts);
    assertRecoverNotCalled();
  }

  @Test
  @DisplayName("TC4-02: 최대 재시도 소진 시 recover 경로로 종료된다")
  void sendVerificationEmailAsync_retryExhausted_thenRecover() {
    // given
    givenEmailSenderAlwaysFails();

    // when
    asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    // then
    assertRetriedSendCountTo(USER_EMAIL, retryMaxAttempts);
    assertRecoverCalledOnceFor(USER_EMAIL, VERIFICATION_LINK);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("TC4-03: AFTER_COMMIT 이벤트 리스너 경로에서도 재시도 후 성공한다")
  void sendVerification_viaEventListener_retryThenSuccess() {
    // given
    User user = saveUser();
    givenEmailSenderFailsThenSucceeds();

    // when
    transactionTemplate.executeWithoutResult(
        unused -> {
          User managedUser = userRepository.findById(user.getId()).orElseThrow();
          emailVerificationService.sendVerification(managedUser);
        });

    // then
    assertRetriedSendCountTo(user.getEmail(), retryMaxAttempts);
    assertRecoverNotCalled();
  }

  private void assertRetriedSendCountTo(String recipientEmail, int expectedCount) {
    then(emailSender)
        .should(timeout(asyncTimeoutMs).times(expectedCount))
        .send(anyString(), eq(recipientEmail), anyString(), anyString());
    then(emailSender)
        .should(after(asyncTimeoutMs).times(expectedCount))
        .send(anyString(), eq(recipientEmail), anyString(), anyString());
  }

  private void assertRecoverCalledOnceFor(String recipientEmail, String verificationLink) {
    then(asyncEmailService)
        .should(timeout(asyncTimeoutMs).times(1))
        .recoverFromEmailFailure(
            any(MailSendException.class), eq(recipientEmail), eq(verificationLink));
    then(asyncEmailService)
        .should(after(asyncTimeoutMs).times(1))
        .recoverFromEmailFailure(
            any(MailSendException.class), eq(recipientEmail), eq(verificationLink));
  }

  private void assertRecoverNotCalled() {
    then(asyncEmailService)
        .should(after(asyncTimeoutMs).never())
        .recoverFromEmailFailure(any(MailSendException.class), anyString(), anyString());
  }

  private void givenEmailSenderFailsThenSucceeds() {
    AtomicInteger attempts = new AtomicInteger(0);
    doAnswer(
            invocation -> {
              if (attempts.incrementAndGet() <= FAIL_UNTIL_ATTEMPT) {
                throw new MailSendException("일시적 SMTP 전송 실패");
              }
              return null;
            })
        .when(emailSender)
        .send(anyString(), anyString(), anyString(), anyString());
  }

  private void givenEmailSenderAlwaysFails() {
    doThrow(new MailSendException("지속적 SMTP 전송 실패"))
        .when(emailSender)
        .send(anyString(), anyString(), anyString(), anyString());
  }

  private User saveUser() {
    User user = emailUser(uniqueEmail("retry-listener"), false);
    User saved = userRepository.save(user);
    createdUserIds.add(saved.getId());
    return saved;
  }

  @AfterEach
  void cleanUp() {
    if (createdUserIds.isEmpty()) {
      return;
    }

    transactionTemplate.executeWithoutResult(
        unused -> {
          entityManager.clear();
          tokenCleanupRepository.deleteAllByUserIds(createdUserIds);
          userRepository.deleteAllByIdInBatch(createdUserIds);
        });
    createdUserIds.clear();
  }

  private int calculateAsyncTimeoutMs() {
    return (retryMaxAttempts - 1) * (int) retryDelayMs + ASYNC_TIMEOUT_BUFFER_MS;
  }
}
