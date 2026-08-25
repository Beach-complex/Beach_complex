package com.beachcheck.global.logging.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.global.config.AsyncConfig;
import io.micrometer.tracing.Tracer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("AsyncConfig 통합 테스트 — emailTaskExecutor MDC 전파 설정 검증")
class AsyncConfigIntegrationTest {

  private static final String REQUEST_ID = "requestId";
  private static final String USER_ID = "userId";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(AsyncConfig.class, TestAsyncBeanConfig.class);

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName(
      "emailTaskExecutor로 제출한 작업이 부모 스레드의 requestId/userId를 worker thread에서 읽고, 제출 스레드 MDC는 유지된다")
  void givenCallerMdc_whenSubmittedToExecutor_thenPropagatedToWorkerAndCallerPreserved() {
    contextRunner.run(
        context -> {
          // given: 실제 등록된 emailTaskExecutor 빈을 꺼내고 제출 스레드에 MDC를 세팅
          ThreadPoolTaskExecutor executor =
              context.getBean("emailTaskExecutor", ThreadPoolTaskExecutor.class);
          MDC.put(REQUEST_ID, "req-async");
          MDC.put(USER_ID, "user-async");

          AtomicReference<String> requestIdInWorker = new AtomicReference<>();
          AtomicReference<String> userIdInWorker = new AtomicReference<>();
          AtomicReference<String> workerThreadName = new AtomicReference<>();

          // when: executor로 작업을 제출하고 완료를 기다린다
          Future<?> future =
              executor.submit(
                  () -> {
                    requestIdInWorker.set(MDC.get(REQUEST_ID));
                    userIdInWorker.set(MDC.get(USER_ID));
                    workerThreadName.set(Thread.currentThread().getName());
                  });
          future.get(2, TimeUnit.SECONDS);

          // then: 별도 worker thread에서 실행되었고 부모 MDC가 전파됨
          assertThat(workerThreadName.get()).startsWith("email-");
          assertThat(requestIdInWorker.get()).isEqualTo("req-async");
          assertThat(userIdInWorker.get()).isEqualTo("user-async");

          // then: 제출 스레드의 MDC는 작업 제출/완료 후에도 그대로 유지됨
          assertThat(MDC.get(REQUEST_ID)).isEqualTo("req-async");
          assertThat(MDC.get(USER_ID)).isEqualTo("user-async");
        });
  }

  @Test
  @DisplayName("@Async(\"emailTaskExecutor\") Bean 메서드 호출 시 부모 MDC가 worker thread로 전파된다")
  void givenCallerMdc_whenAsyncBeanMethodCalled_thenMdcPropagatedThroughSpringAsyncProxy() {
    contextRunner.run(
        context -> {
          AsyncProbe asyncProbe = context.getBean(AsyncProbe.class);
          MDC.put(REQUEST_ID, "req-async-proxy");
          MDC.put(USER_ID, "user-async-proxy");

          ObservedMdc observed = asyncProbe.captureMdc().get(2, TimeUnit.SECONDS);

          assertThat(observed.threadName()).startsWith("email-");
          assertThat(observed.requestId()).isEqualTo("req-async-proxy");
          assertThat(observed.userId()).isEqualTo("user-async-proxy");
          assertThat(MDC.get(REQUEST_ID)).isEqualTo("req-async-proxy");
          assertThat(MDC.get(USER_ID)).isEqualTo("user-async-proxy");
        });
  }

  // 테스트용 @Async Bean과 관련 DTO (사이즈가 작아서 별도 파일로 분리하지 않음)

  @TestConfiguration
  static class TestAsyncBeanConfig {

    @Bean
    Tracer tracer() {
      return Tracer.NOOP;
    }

    @Bean
    AsyncProbe asyncProbe() {
      return new AsyncProbe();
    }
  }

  // 호출 클래스와 클래스를 분리하여 실제 @Async 프록시가 적용된 상태로 테스트
  static class AsyncProbe {

    @Async("emailTaskExecutor")
    public CompletableFuture<ObservedMdc> captureMdc() {
      return CompletableFuture.completedFuture(
          new ObservedMdc(MDC.get(REQUEST_ID), MDC.get(USER_ID), Thread.currentThread().getName()));
    }
  }

  record ObservedMdc(String requestId, String userId, String threadName) {}
}
