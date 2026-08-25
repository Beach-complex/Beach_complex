package com.beachcheck.auth.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;

/** 비동기 이메일 작업 span 안에서 기존 SMTP 재시도 정책을 수행한다. */
@Service
public class RetryingEmailDeliveryService {

  private static final String EMAIL_OPERATION = "verification";
  private static final String SMTP_SPAN_NAME = "email.smtp.send";
  private static final String SMTP_FAILURE_MESSAGE = "SMTP 이메일 전송 실패";

  private static final Logger log = LoggerFactory.getLogger(RetryingEmailDeliveryService.class);

  private final EmailSender emailSender;
  private final Tracer tracer;

  public RetryingEmailDeliveryService(EmailSender emailSender, Tracer tracer) {
    this.emailSender = emailSender;
    this.tracer = tracer;
  }

  @Retryable(
      retryFor = MailSendException.class,
      maxAttemptsExpression = "${app.email.retry.max-attempts:4}",
      backoff =
          @Backoff(
              delayExpression = "${app.email.retry.delay-ms:5000}",
              multiplierExpression = "${app.email.retry.multiplier:2}"))
  public DeliveryOutcome sendVerificationEmail(
      String from, String to, String subject, String body) {
    int attempt = currentAttempt();
    Span smtpSpan =
        tracer
            .nextSpan()
            .name(SMTP_SPAN_NAME)
            .tag("email.operation", EMAIL_OPERATION)
            .tag("email.retry.attempt", String.valueOf(attempt))
            .start();

    try (Tracer.SpanInScope ignored = tracer.withSpan(smtpSpan)) {
      log.info("[{}] 이메일 발송 시도", Thread.currentThread().getName());
      emailSender.send(from, to, subject, body);
      smtpSpan.tag("email.delivery.outcome", DeliveryOutcome.SUCCESS.value());
      log.info("[{}] 이메일 발송 성공", Thread.currentThread().getName());
      return DeliveryOutcome.SUCCESS;
    } catch (RuntimeException exception) {
      smtpSpan.tag("email.delivery.outcome", "error");
      smtpSpan.tag("error.type", exception.getClass().getName());
      // 원본 SMTP 예외 메시지는 수신자 주소 등 민감정보를 포함할 수 있으므로 기록하지 않는다.
      smtpSpan.error(new IllegalStateException(SMTP_FAILURE_MESSAGE));
      throw exception;
    } finally {
      smtpSpan.end();
    }
  }

  @Recover
  public DeliveryOutcome recoverFromEmailFailure(
      MailSendException exception, String from, String to, String subject, String body) {
    log.error("[{}] 이메일 발송 최종 실패 (재시도 한도 소진)", Thread.currentThread().getName());

    // TODO: 향후 관리자 알림 또는 재발송 큐 추가 가능
    return DeliveryOutcome.RETRIES_EXHAUSTED;
  }

  private int currentAttempt() {
    var context = RetrySynchronizationManager.getContext();
    return context == null ? 1 : context.getRetryCount() + 1;
  }

  public enum DeliveryOutcome {
    SUCCESS("success"),
    RETRIES_EXHAUSTED("retries_exhausted");

    private final String value;

    DeliveryOutcome(String value) {
      this.value = value;
    }

    public String value() {
      return value;
    }
  }
}
