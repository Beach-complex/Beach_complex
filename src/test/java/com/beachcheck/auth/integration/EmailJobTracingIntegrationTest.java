package com.beachcheck.auth.integration;

import static com.beachcheck.support.tracing.SpanTestSupport.awaitSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beachcheck.global.config.AsyncConfig;
import com.beachcheck.global.logging.MdcTaskDecorator;
import com.beachcheck.support.tracing.RecordingSpanExporter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("비동기 이메일 Trace 통합 계약")
class EmailJobTracingIntegrationTest {

  private static final String USER_EMAIL = "user@test.com";
  private static final String VERIFICATION_LINK = "https://example.com/verify?token=test-token";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ObservationAutoConfiguration.class,
                  org.springframework.boot.actuate.autoconfigure.opentelemetry
                      .OpenTelemetryAutoConfiguration.class,
                  OpenTelemetryAutoConfiguration.class,
                  MicrometerTracingAutoConfiguration.class))
          .withUserConfiguration(AsyncConfig.class, EmailTraceTestConfiguration.class)
          .withPropertyValues("management.tracing.sampling.probability=1.0");

  @Test
  @DisplayName("DB에서 복구한 이메일 시도는 생산 Trace에 Link를 연결한다")
  void recoveredJobLinksProducerAndDoesNotLeakSensitiveData() {
    contextRunner.run(
        context -> {
          Tracer tracer = context.getBean(Tracer.class);
          var store = org.mockito.Mockito.mock(com.beachcheck.auth.repository.EmailJobStore.class);
          var cipher =
              new com.beachcheck.auth.service.EmailPayloadCipher(
                  "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
          Span parent = tracer.nextSpan().name("http.parent.test").start();
          String traceparent =
              "00-" + parent.context().traceId() + "-" + parent.context().spanId() + "-01";
          parent.end();
          var job =
              new com.beachcheck.auth.repository.EmailJobStore.Job(
                  java.util.UUID.randomUUID(),
                  USER_EMAIL,
                  cipher.encrypt(VERIFICATION_LINK),
                  3,
                  traceparent);
          org.mockito.Mockito.when(store.claim())
              .thenReturn(
                  new com.beachcheck.auth.repository.EmailJobStore.ClaimResult(
                      java.util.Optional.of(job), false),
                  new com.beachcheck.auth.repository.EmailJobStore.ClaimResult(
                      java.util.Optional.empty(), true));
          org.mockito.Mockito.when(
                  store.finish(
                      org.mockito.ArgumentMatchers.any(),
                      org.mockito.ArgumentMatchers.anyString(),
                      org.mockito.ArgumentMatchers.anyString(),
                      org.mockito.ArgumentMatchers.anyLong()))
              .thenReturn(true);
          var worker =
              new com.beachcheck.auth.service.EmailJobWorker(
                  store,
                  (from, to, subject, body) -> {
                    throw new org.springframework.mail.MailSendException(VERIFICATION_LINK);
                  },
                  cipher,
                  new com.beachcheck.auth.service.SmtpFailureClassifier(),
                  tracer,
                  "sender@example.com");
          worker.poll();
          var spans =
              awaitSpans(
                  context.getBean(RecordingSpanExporter.class),
                  context.getBean(SdkTracerProvider.class),
                  3);
          var work = onlySpanNamed(spans, "email.verification.send");
          var smtp = onlySpanNamed(spans, "email.smtp.send");
          assertThat(work.getParentSpanContext().isValid()).isFalse();
          assertThat(work.getLinks()).hasSize(1);
          assertThat(work.getLinks().getFirst().getSpanContext().getTraceId())
              .isEqualTo(parent.context().traceId());
          assertThat(smtp.getParentSpanId()).isEqualTo(work.getSpanId());
          assertThat(attribute(smtp, "email.retry.attempt")).isEqualTo("3");
          assertThat(attribute(work, "email.delivery.outcome")).isEqualTo("retry_wait");
          assertThat(spans.toString()).doesNotContain(USER_EMAIL, VERIFICATION_LINK, "?token=");
          assertThat(tracer.currentSpan()).isNull();
        });
  }

  @Test
  @DisplayName("정상 종료 후 동일 worker에 Trace와 MDC가 남지 않는다")
  void reusedWorker_afterSuccess_doesNotLeakTraceOrMdc() {
    contextRunner.run(
        context -> {
          Tracer tracer = context.getBean(Tracer.class);
          ThreadPoolTaskExecutor executor = singleWorkerExecutor(tracer);
          try {
            AtomicReference<String> firstTraceId = new AtomicReference<>();
            AtomicReference<String> firstRequestId = new AtomicReference<>();
            AtomicReference<String> firstUserId = new AtomicReference<>();
            Span parent = tracer.nextSpan().name("async.success.parent.test").start();
            MDC.put("requestId", "request-success");
            MDC.put("userId", "user-success");
            Future<?> first;
            try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
              first =
                  executor.submit(
                      () -> {
                        firstTraceId.set(tracer.currentSpan().context().traceId());
                        firstRequestId.set(MDC.get("requestId"));
                        firstUserId.set(MDC.get("userId"));
                      });
            } finally {
              parent.end();
              MDC.clear();
            }
            first.get(2, TimeUnit.SECONDS);

            AtomicReference<Span> secondSpan = new AtomicReference<>();
            AtomicReference<String> secondRequestId = new AtomicReference<>();
            AtomicReference<String> secondUserId = new AtomicReference<>();
            executor
                .submit(
                    () -> {
                      secondSpan.set(tracer.currentSpan());
                      secondRequestId.set(MDC.get("requestId"));
                      secondUserId.set(MDC.get("userId"));
                    })
                .get(2, TimeUnit.SECONDS);

            assertThat(firstTraceId.get()).isEqualTo(parent.context().traceId());
            assertThat(firstRequestId.get()).isEqualTo("request-success");
            assertThat(firstUserId.get()).isEqualTo("user-success");
            assertThat(secondSpan.get()).isNull();
            assertThat(secondRequestId.get()).isNull();
            assertThat(secondUserId.get()).isNull();
          } finally {
            executor.shutdown();
          }
        });
  }

  @Test
  @DisplayName("예외로 종료해도 동일 worker에 Trace와 MDC가 남지 않는다")
  void reusedWorker_afterFailure_doesNotLeakTraceOrMdc() {
    contextRunner.run(
        context -> {
          Tracer tracer = context.getBean(Tracer.class);
          ThreadPoolTaskExecutor executor = singleWorkerExecutor(tracer);
          try {
            AtomicReference<String> firstTraceId = new AtomicReference<>();
            AtomicReference<String> firstRequestId = new AtomicReference<>();
            AtomicReference<String> firstUserId = new AtomicReference<>();
            Span parent = tracer.nextSpan().name("async.parent.test").start();
            MDC.put("requestId", "request-first");
            MDC.put("userId", "user-first");
            Future<?> first;
            try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
              first =
                  executor.submit(
                      () -> {
                        firstTraceId.set(tracer.currentSpan().context().traceId());
                        firstRequestId.set(MDC.get("requestId"));
                        firstUserId.set(MDC.get("userId"));
                        throw new IllegalStateException("의도한 테스트 예외");
                      });
            } finally {
              parent.end();
              MDC.clear();
            }

            assertThatThrownBy(() -> first.get(2, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);

            AtomicReference<Span> secondSpan = new AtomicReference<>();
            AtomicReference<String> secondRequestId = new AtomicReference<>();
            AtomicReference<String> secondUserId = new AtomicReference<>();
            executor
                .submit(
                    () -> {
                      secondSpan.set(tracer.currentSpan());
                      secondRequestId.set(MDC.get("requestId"));
                      secondUserId.set(MDC.get("userId"));
                    })
                .get(2, TimeUnit.SECONDS);

            assertThat(firstTraceId.get()).isEqualTo(parent.context().traceId());
            assertThat(firstRequestId.get()).isEqualTo("request-first");
            assertThat(firstUserId.get()).isEqualTo("user-first");
            assertThat(secondSpan.get()).isNull();
            assertThat(secondRequestId.get()).isNull();
            assertThat(secondUserId.get()).isNull();
          } finally {
            executor.shutdown();
          }
        });
  }

  private ThreadPoolTaskExecutor singleWorkerExecutor(Tracer tracer) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(2);
    executor.setTaskDecorator(new MdcTaskDecorator(tracer));
    executor.initialize();
    return executor;
  }

  private SpanData onlySpanNamed(List<SpanData> spans, String name) {
    List<SpanData> matching = spans.stream().filter(span -> span.getName().equals(name)).toList();
    assertThat(matching).hasSize(1);
    return matching.getFirst();
  }

  private String attribute(SpanData span, String key) {
    return span.getAttributes().get(AttributeKey.stringKey(key));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class EmailTraceTestConfiguration {

    @Bean
    RecordingSpanExporter recordingSpanExporter() {
      return new RecordingSpanExporter();
    }
  }
}
