package com.beachcheck.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.beachcheck.auth.repository.EmailJobStore;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmailJobConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(EmailJobStore.class, () -> mock(EmailJobStore.class))
          .withBean(Tracer.class, () -> Tracer.NOOP)
          .withBean(EmailSender.class, () -> mock(EmailSender.class))
          .withBean("emailTaskExecutor", Executor.class, () -> Runnable::run)
          .withUserConfiguration(
              EmailPayloadCipher.class,
              EmailJobService.class,
              EmailJobWorker.class,
              EmailEventListener.class,
              EmailJobScheduler.class,
              SmtpFailureClassifier.class)
          .withPropertyValues("app.email.jobs.polling-enabled=true");

  @Test
  void mailDisabledDoesNotRequireKeyOrRegisterAnyJobProducerOrConsumer() {
    runner
        .withPropertyValues("app.mail.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context)
                  .doesNotHaveBean(EmailPayloadCipher.class)
                  .doesNotHaveBean(EmailEventListener.class)
                  .doesNotHaveBean(EmailJobService.class)
                  .doesNotHaveBean(EmailJobWorker.class)
                  .doesNotHaveBean(EmailJobScheduler.class);
            });
  }

  @Test
  void missingMailSettingAlsoPreservesNoopMode() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context)
              .doesNotHaveBean(EmailEventListener.class)
              .doesNotHaveBean(EmailJobWorker.class)
              .doesNotHaveBean(EmailPayloadCipher.class);
        });
  }

  @Test
  void actualMailStillFailsFastWithoutKey() {
    runner
        .withPropertyValues("app.mail.enabled=true")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .rootCause()
                  .hasMessageContaining("APP_EMAIL_JOBS_PAYLOAD_KEY");
            });
  }

  @Test
  void pollingDisabledStillAllowsDurableEnqueue() {
    runner
        .withPropertyValues(
            "app.mail.enabled=true",
            "app.email.jobs.polling-enabled=false",
            "app.email.jobs.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(EmailJobService.class)
                  .hasSingleBean(EmailEventListener.class)
                  .doesNotHaveBean(EmailJobScheduler.class);
            });
  }

  @Test
  void mailEnabledRegistersSchedulerUsingExistingExecutor() {
    runner
        .withPropertyValues(
            "app.mail.enabled=true",
            "app.email.jobs.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        .run(context -> assertThat(context).hasNotFailed().hasSingleBean(EmailJobScheduler.class));
  }
}
