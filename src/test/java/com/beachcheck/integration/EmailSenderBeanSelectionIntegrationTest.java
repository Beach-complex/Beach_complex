package com.beachcheck.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.beachcheck.service.EmailSender;
import com.beachcheck.service.NoopEmailSender;
import com.beachcheck.service.SmtpEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

@DisplayName("EmailSender 조건부 빈 선택 통합 테스트")
class EmailSenderBeanSelectionIntegrationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(SmtpEmailSender.class, NoopEmailSender.class);

  @Test
  @DisplayName("enabled=false면 NoopEmailSender를 사용한다")
  void whenMailDisabled_thenUseNoopSender() {
    contextRunner
        .withPropertyValues("app.mail.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmailSender.class);
              assertThat(context.getBean(EmailSender.class)).isInstanceOf(NoopEmailSender.class);
            });
  }

  @Test
  @DisplayName("enabled=true이고 default-from이 유효하면 SmtpEmailSender를 사용한다")
  void whenMailEnabledAndDefaultFromValid_thenUseSmtpSender() {
    contextRunner
        .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
        .withPropertyValues("app.mail.enabled=true", "app.mail.default-from=noreply@beachcheck.com")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmailSender.class);
              assertThat(context.getBean(EmailSender.class)).isInstanceOf(SmtpEmailSender.class);
            });
  }

  @Test
  @DisplayName("enabled=true이고 default-from이 공백이면 컨텍스트 기동에 실패한다")
  void whenMailEnabledAndDefaultFromBlank_thenContextFailsFast() {
    contextRunner
        .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
        .withPropertyValues("app.mail.enabled=true", "app.mail.default-from=   ")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .rootCause()
                  .hasMessageContaining("app.mail.default-from이 설정되어야 합니다.");
            });
  }

  @Test
  @DisplayName("enabled 미설정이면 NoopEmailSender를 사용한다")
  void whenMailEnabledMissing_thenUseNoopSender() {
    contextRunner
        .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
        .withPropertyValues("app.mail.default-from=noreply@beachcheck.com")
        .run(
            context -> {
              assertThat(context).hasSingleBean(EmailSender.class);
              assertThat(context.getBean(EmailSender.class)).isInstanceOf(NoopEmailSender.class);
            });
  }
}
