package com.beachcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(MockitoExtension.class)
@DisplayName("비동기 이메일 서비스 단위 테스트")
class AsyncEmailServiceTest {

  private static final String FROM_EMAIL = "no-reply@test.com";
  private static final String USER_EMAIL = "user@test.com";
  private static final String VERIFICATION_LINK = "https://example.com/verify?token=abc";
  private static final String SUBJECT = "이메일 인증";
  private static final String EXPIRATION_TEXT = "30분";
  private static final int EXPIRATION_MINUTES = 30;

  @Mock private EmailSender emailSender;
  @Captor private ArgumentCaptor<String> fromCaptor;
  @Captor private ArgumentCaptor<String> toCaptor;
  @Captor private ArgumentCaptor<String> subjectCaptor;
  @Captor private ArgumentCaptor<String> bodyCaptor;

  @Test
  @DisplayName("인증 메일 payload를 조립해 전송 위임")
  void sendVerificationEmailAsync_success() {
    AsyncEmailService service = newService();

    service.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

    then(emailSender)
        .should()
        .send(fromCaptor.capture(), toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());
    assertThat(fromCaptor.getValue()).isEqualTo(FROM_EMAIL);
    assertThat(toCaptor.getValue()).isEqualTo(USER_EMAIL);
    assertThat(subjectCaptor.getValue()).isEqualTo(SUBJECT);
    assertThat(bodyCaptor.getValue()).contains(VERIFICATION_LINK);
    assertThat(bodyCaptor.getValue()).contains(EXPIRATION_TEXT);
  }

  @Test
  @DisplayName("전송 예외는 단위 테스트에서 그대로 전파")
  void sendVerificationEmailAsync_mailSendException_propagates() {
    AsyncEmailService service = newService();
    doThrow(new MailSendException("smtp down"))
        .when(emailSender)
        .send(eq(FROM_EMAIL), eq(USER_EMAIL), eq(SUBJECT), anyString());

    assertThatThrownBy(() -> service.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK))
        .isInstanceOf(MailSendException.class);
  }

  @Test
  @DisplayName("recover 메서드는 예외 없이 종료")
  void recoverFromEmailFailure_noThrow() {
    AsyncEmailService service = newService();

    assertDoesNotThrow(
        () ->
            service.recoverFromEmailFailure(
                new MailSendException("final failure"), USER_EMAIL, VERIFICATION_LINK));
  }

  private AsyncEmailService newService() {
    return new AsyncEmailService(emailSender, FROM_EMAIL, EXPIRATION_MINUTES);
  }
}
