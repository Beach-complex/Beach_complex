package com.beachcheck.auth.integration;

import static com.beachcheck.support.fixture.EmailVerificationTestFixtures.emailUser;
import static com.beachcheck.support.fixture.UniqueTestFixtures.uniqueEmail;
import static com.beachcheck.support.tracing.SpanTestSupport.flushSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;

import com.beachcheck.auth.repository.EmailVerificationTokenCleanupRepository;
import com.beachcheck.auth.service.AsyncEmailService;
import com.beachcheck.auth.service.EmailSender;
import com.beachcheck.auth.service.EmailVerificationService;
import com.beachcheck.auth.service.RetryingEmailDeliveryService;
import com.beachcheck.support.base.IntegrationTest;
import com.beachcheck.support.tracing.RecordingSpanExporter;
import com.beachcheck.user.domain.User;
import com.beachcheck.user.repository.UserRepository;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailAuthenticationException;
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
      "app.email.retry.multiplier=1",
      "management.tracing.sampling.probability=1.0"
    })
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AsyncEmailRetryIntegrationTest.TraceTestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
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
  @Autowired private Tracer tracer;
  @Autowired private SdkTracerProvider tracerProvider;
  @Autowired private RecordingSpanExporter spanExporter;
  @LocalServerPort private int serverPort;

  @Value("${app.email.retry.max-attempts}")
  private int retryMaxAttempts;

  @Value("${app.email.retry.delay-ms}")
  private long retryDelayMs;

  @Autowired private AsyncEmailService asyncEmailService;
  @SpyBean private RetryingEmailDeliveryService retryingEmailDeliveryService;
  @MockBean private EmailSender emailSender;

  private final Set<UUID> createdUserIds = new HashSet<>();
  private TransactionTemplate transactionTemplate;
  private int asyncTimeoutMs;

  @BeforeEach
  void setUp() {
    transactionTemplate = new TransactionTemplate(transactionManager);
    asyncTimeoutMs = calculateAsyncTimeoutMs();
    flushSpans(tracerProvider);
    spanExporter.clear();
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
    assertDeliverySpans(
        awaitEmailSpans(FAIL_UNTIL_ATTEMPT + 1),
        retryMaxAttempts,
        "success",
        "error",
        "error",
        "success");
  }

  @Test
  @DisplayName("TC4-02: 최대 재시도 소진 시 recover 경로로 종료된다")
  void sendVerificationEmailAsync_retryExhausted_thenRecover(CapturedOutput output) {
    // given
    givenEmailSenderAlwaysFails();

    // when
    asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    // then
    assertRetriedSendCountTo(USER_EMAIL, retryMaxAttempts);
    assertRecoverCalledOnceFor(USER_EMAIL, VERIFICATION_LINK);
    assertThat(output.getOut())
        .contains("이메일 발송 최종 실패")
        .doesNotContain(USER_EMAIL)
        .doesNotContain(VERIFICATION_LINK)
        .doesNotContain("이메일 인증");
    assertDeliverySpans(
        awaitEmailSpans(retryMaxAttempts),
        retryMaxAttempts,
        "retries_exhausted",
        "error",
        "error",
        "error");
  }

  @Test
  @DisplayName("TC4-03: 비재시도 메일 예외도 원문 민감정보 없이 안전하게 기록한다")
  void sendVerificationEmailAsync_nonRetryableFailure_doesNotLeakSensitiveLog(
      CapturedOutput output) {
    String sensitiveExceptionMessage =
        "recipient=" + USER_EMAIL + ", verificationLink=" + VERIFICATION_LINK;
    doThrow(new MailAuthenticationException(sensitiveExceptionMessage))
        .when(emailSender)
        .send(anyString(), anyString(), anyString(), anyString());

    asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    assertRetriedSendCountTo(USER_EMAIL, 1);
    assertRecoverNotCalled();
    assertThat(output.getOut())
        .contains("비동기 작업 처리 실패")
        .contains("errorType=" + MailAuthenticationException.class.getName())
        .doesNotContain(sensitiveExceptionMessage)
        .doesNotContain(USER_EMAIL)
        .doesNotContain(VERIFICATION_LINK);
    assertNonRetryableFailureSpan(awaitEmailSpans(1));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("TC4-04: AFTER_COMMIT 이벤트 리스너 경로에서도 재시도 후 성공한다")
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

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("TC4-05: AFTER_COMMIT 부모 Trace가 비동기 이메일 작업 span으로 이어진다")
  void sendVerification_afterCommit_continuesTraceIntoAsyncEmail() {
    User user = saveUser();
    Span parent = tracer.nextSpan().name("email.after-commit.parent").start();

    try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
      transactionTemplate.executeWithoutResult(
          unused -> {
            User managedUser = userRepository.findById(user.getId()).orElseThrow();
            emailVerificationService.sendVerification(managedUser);
          });
    } finally {
      parent.end();
    }

    assertRetriedSendCountTo(user.getEmail(), 1);
    List<SpanData> spans = awaitEmailSpans(1);
    SpanData parentSpan = onlySpanNamed(spans, "email.after-commit.parent");
    SpanData workSpan = onlySpanNamed(spans, "email.verification.send");

    assertThat(workSpan.getTraceId()).isEqualTo(parentSpan.getTraceId());
    assertThat(workSpan.getParentSpanId()).isEqualTo(parentSpan.getSpanId());
    assertDeliverySpans(spans, 1, "success", "success");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("TC4-06: 실제 회원가입 HTTP Trace가 AFTER_COMMIT 비동기 이메일 span으로 이어진다")
  void signUpHttpRequest_afterCommit_continuesTraceIntoAsyncEmail() throws Exception {
    String email = uniqueEmail("trace-http-signup");
    String requestBody =
        """
        {"email":"%s","password":"Password1!","name":"테스트 사용자"}
        """
            .formatted(email);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + "/api/auth/signup"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    User savedUser = userRepository.findByEmail(email).orElseThrow();
    createdUserIds.add(savedUser.getId());
    assertThat(response.statusCode()).isEqualTo(201);
    assertRetriedSendCountTo(email, 1);

    List<SpanData> spans = awaitEmailSpans(1);
    SpanData workSpan = onlySpanNamed(spans, "email.verification.send");
    SpanData serverSpan = httpServerAncestorOf(spans, workSpan);

    assertThat(workSpan.getTraceId()).isEqualTo(serverSpan.getTraceId());
    assertDeliverySpans(spans, 1, "success", "success");
  }

  private void assertDeliverySpans(
      List<SpanData> spans, int expectedSmtpSpanCount, String workOutcome, String... smtpOutcomes) {
    SpanData workSpan = onlySpanNamed(spans, "email.verification.send");
    List<SpanData> smtpSpans =
        spans.stream()
            .filter(span -> span.getName().equals("email.smtp.send"))
            .sorted(
                (left, right) ->
                    Integer.compare(
                        Integer.parseInt(attribute(left, "email.retry.attempt")),
                        Integer.parseInt(attribute(right, "email.retry.attempt"))))
            .toList();

    assertThat(smtpSpans).hasSize(expectedSmtpSpanCount);
    assertThat(attribute(workSpan, "email.operation")).isEqualTo("verification");
    assertThat(attribute(workSpan, "email.delivery.outcome")).isEqualTo(workOutcome);
    List<String> expectedAttempts =
        IntStream.rangeClosed(1, expectedSmtpSpanCount).mapToObj(String::valueOf).toList();
    assertThat(smtpSpans)
        .extracting(span -> attribute(span, "email.retry.attempt"))
        .containsExactlyElementsOf(expectedAttempts);
    assertThat(smtpSpans)
        .extracting(span -> attribute(span, "email.delivery.outcome"))
        .containsExactly(smtpOutcomes);
    assertThat(smtpSpans)
        .allSatisfy(
            span -> {
              assertThat(span.getTraceId()).isEqualTo(workSpan.getTraceId());
              assertThat(span.getParentSpanId()).isEqualTo(workSpan.getSpanId());
              assertThat(attribute(span, "email.operation")).isEqualTo("verification");
            });

    List<SpanData> errorSpans =
        smtpSpans.stream()
            .filter(span -> attribute(span, "email.delivery.outcome").equals("error"))
            .toList();
    assertThat(errorSpans)
        .allSatisfy(
            span -> assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR));
    assertNoSensitiveData(spans);
  }

  private void assertNonRetryableFailureSpan(List<SpanData> spans) {
    SpanData workSpan = onlySpanNamed(spans, "email.verification.send");
    SpanData smtpSpan = onlySpanNamed(spans, "email.smtp.send");

    assertThat(smtpSpan.getTraceId()).isEqualTo(workSpan.getTraceId());
    assertThat(smtpSpan.getParentSpanId()).isEqualTo(workSpan.getSpanId());
    assertThat(attribute(smtpSpan, "email.retry.attempt")).isEqualTo("1");
    assertThat(attribute(smtpSpan, "email.delivery.outcome")).isEqualTo("error");
    assertThat(smtpSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertNoSensitiveData(spans);
  }

  private void assertNoSensitiveData(List<SpanData> spans) {
    String[] sensitiveValues = {USER_EMAIL, VERIFICATION_LINK, "이메일 인증", "request-id", "user-id"};
    assertThat(spans)
        .allSatisfy(
            span -> {
              String attributes = span.getAttributes().toString();
              assertThat(span.getName()).doesNotContain(sensitiveValues);
              assertThat(attributes).doesNotContain(sensitiveValues);
              assertThat(span.getEvents())
                  .allSatisfy(
                      event -> {
                        assertThat(event.getName()).doesNotContain(sensitiveValues);
                        assertThat(event.getAttributes().toString())
                            .doesNotContain(sensitiveValues);
                      });
            });
  }

  private List<SpanData> awaitEmailSpans(int expectedSmtpSpanCount) {
    for (int attempt = 0; attempt < 100; attempt++) {
      flushSpans(tracerProvider);
      List<SpanData> spans = spanExporter.spans();
      long smtpSpanCount =
          spans.stream().filter(span -> span.getName().equals("email.smtp.send")).count();
      boolean workSpanPresent =
          spans.stream().anyMatch(span -> span.getName().equals("email.verification.send"));
      if (workSpanPresent && smtpSpanCount >= expectedSmtpSpanCount) {
        return spans;
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("이메일 Trace 대기가 중단되었습니다.", exception);
      }
    }
    return spanExporter.spans();
  }

  private SpanData httpServerAncestorOf(List<SpanData> spans, SpanData descendant) {
    String parentSpanId = descendant.getParentSpanId();
    for (int depth = 0; depth < spans.size(); depth++) {
      String expectedSpanId = parentSpanId;
      SpanData parent =
          spans.stream()
              .filter(span -> span.getSpanId().equals(expectedSpanId))
              .findFirst()
              .orElseThrow();
      if (parent.getKind() == SpanKind.SERVER) {
        return parent;
      }
      parentSpanId = parent.getParentSpanId();
    }
    throw new AssertionError("이메일 작업 span의 상위에서 HTTP server span을 찾지 못했습니다.");
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
    then(retryingEmailDeliveryService)
        .should(timeout(asyncTimeoutMs).times(1))
        .recoverFromEmailFailure(
            any(MailSendException.class),
            anyString(),
            eq(recipientEmail),
            anyString(),
            anyString());
    then(retryingEmailDeliveryService)
        .should(after(asyncTimeoutMs).times(1))
        .recoverFromEmailFailure(
            any(MailSendException.class),
            anyString(),
            eq(recipientEmail),
            anyString(),
            anyString());
  }

  private void assertRecoverNotCalled() {
    then(retryingEmailDeliveryService)
        .should(after(asyncTimeoutMs).never())
        .recoverFromEmailFailure(
            any(MailSendException.class), anyString(), anyString(), anyString(), anyString());
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

  private SpanData onlySpanNamed(List<SpanData> spans, String name) {
    List<SpanData> matching = spans.stream().filter(span -> span.getName().equals(name)).toList();
    assertThat(matching).hasSize(1);
    return matching.getFirst();
  }

  private String attribute(SpanData span, String key) {
    return span.getAttributes().get(AttributeKey.stringKey(key));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TraceTestConfiguration {

    @Bean
    RecordingSpanExporter recordingSpanExporter() {
      return new RecordingSpanExporter();
    }
  }
}
