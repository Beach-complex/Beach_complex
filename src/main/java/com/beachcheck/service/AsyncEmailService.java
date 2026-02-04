package com.beachcheck.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Why: 이메일 발송을 비동기로 처리하며 SMTP 장애 시 자동 재시도를 수행한다.
 *
 * <p>Policy: 기술 계층의 장애 복구 메커니즘으로, MailSendException 발생 시 지수 백오프 전략으로 최대 3회 자동 재시도한다.
 *
 * <p>Contract(Input/Output): 이메일 발송 메서드를 제공한다.
 *
 * <p>Note: EmailVerificationService에서 분리한 이유는 @Async가 같은 클래스 내부 호출에서는 작동하지 않기 때문이다. Spring AOP 프록시를
 * 통해야 @Async/@Retryable이 작동하므로 별도 서비스로 분리했다.
 */
@Service
public class AsyncEmailService {

  private final Logger log = LoggerFactory.getLogger(AsyncEmailService.class);

  private final EmailSender emailSender;
  private final String fromAddress;
  private final long tokenExpirationMinutes;

  public AsyncEmailService(
      EmailSender emailSender,
      @Value("${app.email-verification.from-address:}") String fromAddress,
      @Value("${app.email-verification.token-expiration-minutes:30}") long tokenExpirationMinutes) {
    this.emailSender = emailSender;
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
   * <p>Contract(Output): 이메일 발송 성공 시 로그 기록. 3회 재시도 후 실패 시 {@link #recoverFromEmailFailure}가 호출된다.
   *
   * @see org.springframework.scheduling.annotation.Async
   * @see org.springframework.retry.annotation.Retryable
   */
  @Async("emailTaskExecutor")
  @Retryable(
      retryFor = {MailSendException.class},
      maxAttempts = 4, // 최초 1회 + 재시도 3회 = 총 4회
      backoff = @Backoff(delay = 5000, multiplier = 2) // 지수 백오프 (5초, 10초, 20초)
      )
  public void sendVerificationEmailAsync(String to, String verificationLink) {

    String subject = "이메일 인증";
    String body =
        """
        아래 링크를 클릭하여 이메일을 인증해주세요:

        %s

        이 링크는 %d분 후에 만료됩니다.
        """
            .formatted(verificationLink, tokenExpirationMinutes);

    log.info("[{}] 이메일 발송 시도 - to: {}", Thread.currentThread().getName(), to);
    emailSender.send(fromAddress, to, subject, body);
    log.info("[{}] 이메일 발송 성공 - to: {}", Thread.currentThread().getName(), to);
  }

  @Recover
  public void recoverFromEmailFailure(MailSendException e, String to, String verificationLink) {
    log.error("[{}] 이메일 발송 최종 실패 (3회 재시도 완료): to={}", Thread.currentThread().getName(), to, e);

    // TODO: 향후 관리자 알림 또는 재발송 큐 추가 가능
  }
}
