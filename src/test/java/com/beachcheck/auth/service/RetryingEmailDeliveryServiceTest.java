package com.beachcheck.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
@DisplayName("재시도 이메일 전송 서비스 단위 테스트")
class RetryingEmailDeliveryServiceTest {

  private static final String FROM = "from@test.com";
  private static final String TO = "to@test.com";
  private static final String SUBJECT = "subject";
  private static final String BODY = "body";

  @Mock private EmailSender emailSender;

  @Test
  @DisplayName("SMTP 전송을 기존 EmailSender에 위임한다")
  void sendVerificationEmail_delegatesToSender() {
    RetryingEmailDeliveryService service = newService();

    service.sendVerificationEmail(FROM, TO, SUBJECT, BODY);

    then(emailSender).should().send(FROM, TO, SUBJECT, BODY);
  }

  @Test
  @DisplayName("프록시 밖 단위 호출에서는 SMTP 예외를 그대로 전파한다")
  void sendVerificationEmail_failure_propagates() {
    RetryingEmailDeliveryService service = newService();
    doThrow(new MailSendException("smtp down")).when(emailSender).send(FROM, TO, SUBJECT, BODY);

    assertThatThrownBy(() -> service.sendVerificationEmail(FROM, TO, SUBJECT, BODY))
        .isInstanceOf(MailSendException.class);
  }

  @Test
  @DisplayName("재시도 소진 recover는 예외 없이 종료한다")
  void recoverFromEmailFailure_completesNormally() {
    RetryingEmailDeliveryService service = newService();

    assertDoesNotThrow(
        () ->
            service.recoverFromEmailFailure(
                new MailSendException("smtp down"), FROM, TO, SUBJECT, BODY));
  }

  private RetryingEmailDeliveryService newService() {
    return new RetryingEmailDeliveryService(emailSender);
  }
}
