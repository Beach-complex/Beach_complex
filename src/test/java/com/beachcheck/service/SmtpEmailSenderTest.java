package com.beachcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@DisplayName("SMTP 이메일 전송기 단위 테스트")
class SmtpEmailSenderTest {

  @Mock private JavaMailSender mailSender;
  @Captor private ArgumentCaptor<SimpleMailMessage> messageCaptor;

  @Test
  @DisplayName("default-from 미설정이면 생성자 예외")
  void constructor_blankDefaultFrom_throws() {
    assertThatThrownBy(() -> new SmtpEmailSender(mailSender, " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("app.mail.default-from");
  }

  @Test
  @DisplayName("from이 null이면 default-from 사용")
  void send_nullFrom_usesDefaultFrom() {
    SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@test.com");

    sender.send(null, "to@test.com", "subject", "body");

    then(mailSender).should().send(messageCaptor.capture());
    SimpleMailMessage message = messageCaptor.getValue();
    assertThat(message.getFrom()).isEqualTo("no-reply@test.com");
    assertThat(message.getTo()).containsExactly("to@test.com");
    assertThat(message.getSubject()).isEqualTo("subject");
    assertThat(message.getText()).isEqualTo("body");
  }

  @Test
  @DisplayName("from이 공백이면 default-from 사용")
  void send_blankFrom_usesDefaultFrom() {
    SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@test.com");

    sender.send("   ", "to@test.com", "subject", "body");

    then(mailSender).should().send(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getFrom()).isEqualTo("no-reply@test.com");
  }

  @Test
  @DisplayName("from이 있으면 입력값 그대로 사용")
  void send_withFrom_usesProvidedFrom() {
    SmtpEmailSender sender = new SmtpEmailSender(mailSender, "no-reply@test.com");

    sender.send("sender@test.com", "to@test.com", "subject", "body");

    then(mailSender).should().send(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getFrom()).isEqualTo("sender@test.com");
  }
}
