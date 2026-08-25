package com.beachcheck.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/** 비동기 이메일 작업 span 안에서 기존 SMTP 재시도 정책을 수행한다. */
@Service
public class RetryingEmailDeliveryService {

  private static final Logger log = LoggerFactory.getLogger(RetryingEmailDeliveryService.class);

  private final EmailSender emailSender;

  public RetryingEmailDeliveryService(EmailSender emailSender) {
    this.emailSender = emailSender;
  }

  @Retryable(
      retryFor = MailSendException.class,
      maxAttemptsExpression = "${app.email.retry.max-attempts:4}",
      backoff =
          @Backoff(
              delayExpression = "${app.email.retry.delay-ms:5000}",
              multiplierExpression = "${app.email.retry.multiplier:2}"))
  public void sendVerificationEmail(String from, String to, String subject, String body) {
    log.info("[{}] 이메일 발송 시도 - to: {}", Thread.currentThread().getName(), to);
    emailSender.send(from, to, subject, body);
    log.info("[{}] 이메일 발송 성공 - to: {}", Thread.currentThread().getName(), to);
  }

  @Recover
  public void recoverFromEmailFailure(
      MailSendException exception, String from, String to, String subject, String body) {
    log.error(
        "[{}] 이메일 발송 최종 실패 (3회 재시도 완료): to={}", Thread.currentThread().getName(), to, exception);

    // TODO: 향후 관리자 알림 또는 재발송 큐 추가 가능
  }
}
