package com.beachcheck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.beachcheck.service.OutboxEventDispatcher;
import com.beachcheck.service.OutboxPublisher;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@DisplayName("Firebase 기반 Outbox 조건부 빈 컨텍스트 테스트")
class OutboxFirebaseConditionContextTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(OutboxBeansConfig.class)
          .withPropertyValues("app.outbox.polling.enabled=true", "app.outbox.polling.batch-size=10")
          .withBean(OutboxEventRepository.class, () -> mock(OutboxEventRepository.class))
          .withBean(NotificationRepository.class, () -> mock(NotificationRepository.class));

  @Test
  @DisplayName("FirebaseMessaging 빈이 없으면 Outbox 관련 빈 없이도 컨텍스트가 정상 기동된다")
  void whenFirebaseMessagingMissing_thenContextLoadsWithoutOutboxBeans() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(OutboxEventDispatcher.class);
          assertThat(context).doesNotHaveBean(OutboxPublisher.class);
          assertThat(context).doesNotHaveBean(OutboxSchedulingConfig.class);
        });
  }

  @Test
  @DisplayName("FirebaseMessaging 빈이 있으면 Outbox 관련 빈을 함께 등록한다")
  void whenFirebaseMessagingPresent_thenRegisterOutboxBeans() {
    contextRunner
        .withBean(FirebaseMessaging.class, () -> mock(FirebaseMessaging.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(OutboxEventDispatcher.class);
              assertThat(context).hasSingleBean(OutboxPublisher.class);
              assertThat(context).hasSingleBean(OutboxSchedulingConfig.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @Import({OutboxFirebaseConfig.class, OutboxSchedulingConfig.class})
  static class OutboxBeansConfig {}
}
