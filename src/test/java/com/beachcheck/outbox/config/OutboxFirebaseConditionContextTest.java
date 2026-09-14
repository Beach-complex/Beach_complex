package com.beachcheck.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.beachcheck.global.config.FirebaseConfig;
import com.beachcheck.notification.repository.NotificationRepository;
import com.beachcheck.outbox.repository.OutboxEventRepository;
import com.beachcheck.outbox.service.OutboxEventDispatcher;
import com.beachcheck.outbox.service.OutboxPublisher;
import com.beachcheck.outbox.tracing.OutboxTracing;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@DisplayName("Firebase 기반 Outbox 조건부 빈 컨텍스트 테스트")
class OutboxFirebaseConditionContextTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues("app.outbox.polling.enabled=true", "app.outbox.polling.batch-size=10")
          .withBean(OutboxEventRepository.class, () -> mock(OutboxEventRepository.class))
          .withBean(NotificationRepository.class, () -> mock(NotificationRepository.class))
          .withBean(OutboxTracing.class, () -> mock(OutboxTracing.class));

  @Test
  @DisplayName("Firebase가 비활성화되면 Outbox 관련 빈 없이도 컨텍스트가 정상 기동된다")
  void whenFirebaseDisabled_thenContextLoadsWithoutOutboxBeans() {
    contextRunner
        .withUserConfiguration(OutboxBeansConfig.class)
        .withPropertyValues("app.firebase.enabled=false")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(OutboxEventDispatcher.class);
              assertThat(context).doesNotHaveBean(OutboxPublisher.class);
              assertThat(context).doesNotHaveBean(OutboxSchedulingConfig.class);
            });
  }

  @Test
  @DisplayName("Firebase가 활성화되고 FirebaseMessaging 의존성이 있으면 Outbox 관련 빈을 등록한다")
  void whenFirebaseEnabledWithMessaging_thenRegisterOutboxBeans() {
    contextRunner
        .withUserConfiguration(OutboxBeansConfig.class)
        .withPropertyValues("app.firebase.enabled=true")
        .withBean(FirebaseMessaging.class, () -> mock(FirebaseMessaging.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(OutboxEventDispatcher.class);
              assertThat(context).hasSingleBean(OutboxPublisher.class);
              assertThat(context).hasSingleBean(OutboxSchedulingConfig.class);
            });
  }

  @Test
  @DisplayName("Outbox 폴링이 비활성화되면 전송 빈은 유지하고 스케줄러만 등록하지 않는다")
  void whenOutboxPollingDisabled_thenRegisterDispatchBeansWithoutScheduler() {
    contextRunner
        .withUserConfiguration(OutboxBeansConfig.class)
        .withPropertyValues("app.firebase.enabled=true", "app.outbox.polling.enabled=false")
        .withBean(FirebaseMessaging.class, () -> mock(FirebaseMessaging.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(OutboxEventDispatcher.class);
              assertThat(context).hasSingleBean(OutboxPublisher.class);
              assertThat(context).doesNotHaveBean(OutboxSchedulingConfig.class);
            });
  }

  @Test
  @DisplayName("운영 Firebase와 Outbox 설정을 함께 로드하면 처리 순서와 관계없이 관련 빈을 등록한다")
  void whenProductionConfigurationsLoad_thenRegisterFirebaseAndOutboxBeans() {
    FirebaseApp firebaseApp = mock(FirebaseApp.class);
    FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);

    try (MockedStatic<GoogleCredentials> googleCredentials = mockStatic(GoogleCredentials.class);
        MockedStatic<FirebaseApp> firebaseApps = mockStatic(FirebaseApp.class);
        MockedStatic<FirebaseMessaging> firebaseMessagingInstances =
            mockStatic(FirebaseMessaging.class)) {
      googleCredentials
          .when(() -> GoogleCredentials.fromStream(any(InputStream.class)))
          .thenReturn(mock(GoogleCredentials.class));
      firebaseApps.when(FirebaseApp::getApps).thenReturn(java.util.List.of());
      firebaseApps
          .when(() -> FirebaseApp.initializeApp(any(FirebaseOptions.class)))
          .thenReturn(firebaseApp);
      firebaseMessagingInstances
          .when(() -> FirebaseMessaging.getInstance(firebaseApp))
          .thenReturn(firebaseMessaging);

      contextRunner
          .withInitializer(context -> context.getEnvironment().setActiveProfiles("test"))
          .withUserConfiguration(ProductionBeansConfig.class)
          .withPropertyValues(
              "app.firebase.enabled=true", "app.firebase.credentials-json-base64=e30=")
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(FirebaseApp.class);
                assertThat(context).hasSingleBean(FirebaseMessaging.class);
                assertThat(context).hasSingleBean(OutboxEventDispatcher.class);
                assertThat(context).hasSingleBean(OutboxPublisher.class);
                assertThat(context).hasSingleBean(OutboxSchedulingConfig.class);
              });
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import({OutboxSchedulingConfig.class, OutboxFirebaseConfig.class})
  static class OutboxBeansConfig {}

  @Configuration(proxyBeanMethods = false)
  @Import({OutboxSchedulingConfig.class, OutboxFirebaseConfig.class, FirebaseConfig.class})
  static class ProductionBeansConfig {}
}
