package com.beachcheck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.service.AsyncEmailService;
import com.beachcheck.service.EmailSender;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
    properties = {
      "app.email.retry.max-attempts=3",
      "app.email.retry.delay-ms=10",
      "app.email.retry.multiplier=1"
    })
@DisplayName("AsyncEmailService 재시도 통합 테스트")
class AsyncEmailRetryIntegrationTest extends IntegrationTest {

  private static final String USER_EMAIL = "retry@test.com";
  private static final String VERIFICATION_LINK =
      "http://localhost:8080/api/auth/verify?token=retry";

  @Autowired private AsyncEmailService asyncEmailService;

  @MockBean private EmailSender emailSender;

  @Test
  @DisplayName("메일 전송이 일시 실패하면 재시도 후 성공한다")
  void sendVerificationEmailAsync_retryThenSuccess() {
    int[] attempts = {0};
    doAnswer(
            invocation -> {
              attempts[0]++;
              if (attempts[0] < 3) {
                throw new MailSendException("temporary smtp failure");
              }
              return null;
            })
        .when(emailSender)
        .send(anyString(), anyString(), anyString(), anyString());

    asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    then(emailSender)
        .should(timeout(3000).times(3))
        .send(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  @DisplayName("최대 재시도 소진 시 recover 경로로 종료된다")
  void sendVerificationEmailAsync_retryExhausted_thenRecover(CapturedOutput output) {
    doThrow(new MailSendException("persistent smtp failure"))
        .when(emailSender)
        .send(anyString(), anyString(), anyString(), anyString());

    asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    then(emailSender)
        .should(timeout(3000).times(3))
        .send(anyString(), anyString(), anyString(), anyString());

    waitUntil(() -> output.toString().contains("이메일 발송 최종 실패"), 3000L);
    assertThat(output.toString()).contains("이메일 발송 최종 실패");
  }

  private void waitUntil(BooleanSupplier condition, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (condition.getAsBoolean()) {
        return;
      }
      try {
        Thread.sleep(20L);
      } catch (InterruptedException interruptedException) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("테스트 대기 중 인터럽트 발생", interruptedException);
      }
    }
    throw new AssertionError("조건 대기 시간 초과: " + timeoutMs + "ms");
  }
}
