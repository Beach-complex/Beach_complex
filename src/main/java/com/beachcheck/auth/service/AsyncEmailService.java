package com.beachcheck.auth.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Why: 이메일 발송을 비동기로 처리하며 SMTP 장애 시 자동 재시도를 수행한다.
 *
 * <p>Policy: 기술 계층의 장애 복구 메커니즘으로, MailSendException 발생 시 지수 백오프 전략으로 최대 3회 자동 재시도한다.
 *
 * <p>Contract(Input/Output): 이메일 발송 메서드를 제공한다.
 *
 * <p>Note: EmailVerificationService에서 분리한 이유는 @Async가 같은 클래스 내부 호출에서는 작동하지 않기 때문이다. 비동기 작업 span이
 * SMTP 재시도 전체를 한 번만 감싸도록 재시도 경계는 {@link RetryingEmailDeliveryService}에 위임한다.
 */
@Service
public class AsyncEmailService {

  private static final String EMAIL_OPERATION = "verification";

  private final RetryingEmailDeliveryService emailDeliveryService;
  private final Tracer tracer;
  private final String fromAddress;
  private final long tokenExpirationMinutes;

  public AsyncEmailService(
      RetryingEmailDeliveryService emailDeliveryService,
      Tracer tracer,
      @Value("${app.email-verification.from-address:}") String fromAddress,
      @Value("${app.email-verification.token-expiration-minutes:30}") long tokenExpirationMinutes) {
    this.emailDeliveryService = emailDeliveryService;
    this.tracer = tracer;
    this.fromAddress = fromAddress;
    this.tokenExpirationMinutes = tokenExpirationMinutes;
  }

  /**
   * Why: 이메일 인증 메일을 비동기로 발송하며, SMTP 서버 장애 시 자동 재시도를 수행한다.
   *
   * <p>Policy: 기술 계층의 장애 복구 메커니즘으로, MailSendException 발생 시 지수 백오프 전략으로 최대 3회 자동 재시도한다. (5초 → 10초 →
   * 20초 대기). 회원가입 API는 즉시 응답하며, 이메일 발송은 백그라운드에서 처리된다. maxAttempts=4는 최초 시도 1회 + 재시도 3회를 의미한다.
   *
   * <p>Contract(Input): to는 수신자 이메일 주소, verificationLink는 인증 링크 전체 URL이다.
   *
   * <p>Contract(Output): 이메일 작업 전체를 {@code email.verification.send} span 하나로 기록한다.
   *
   * @see org.springframework.scheduling.annotation.Async
   * @see RetryingEmailDeliveryService
   */
  @Async("emailTaskExecutor")
  public void sendVerificationEmailAsync(String to, String verificationLink) {

    String subject = "이메일 인증";
    String body =
        """
        아래 링크를 클릭하여 이메일을 인증해주세요:

        %s

        이 링크는 %d분 후에 만료됩니다.
        """
            .formatted(verificationLink, tokenExpirationMinutes);

    Span emailSpan =
        tracer
            .nextSpan()
            .name("email.verification.send")
            .tag("email.operation", EMAIL_OPERATION)
            .start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(emailSpan)) {
      emailDeliveryService.sendVerificationEmail(fromAddress, to, subject, body);
    } finally {
      emailSpan.end();
    }
  }
}
