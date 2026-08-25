package com.beachcheck.auth.integration;

import static com.beachcheck.support.tracing.SpanTestSupport.awaitSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beachcheck.auth.service.AsyncEmailService;
import com.beachcheck.auth.service.EmailSender;
import com.beachcheck.auth.service.RetryingEmailDeliveryService;
import com.beachcheck.global.config.AsyncConfig;
import com.beachcheck.global.config.RetryConfig;
import com.beachcheck.global.logging.MdcTaskDecorator;
import com.beachcheck.support.tracing.RecordingSpanExporter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
class AsyncEmailTracingIntegrationTest {

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
          .withUserConfiguration(
              AsyncConfig.class, RetryConfig.class, EmailTraceTestConfiguration.class)
          .withPropertyValues("management.tracing.sampling.probability=1.0");

  @Test
  @DisplayName("부모 Trace를 worker로 전달하고 이메일 작업 자식 span을 하나 만든다")
  void parentTrace_createsChildWorkSpan() {
    contextRunner.run(
        context -> {
          Tracer tracer = context.getBean(Tracer.class);
          AsyncEmailService asyncEmailService = context.getBean(AsyncEmailService.class);
          RecordingEmailSender emailSender = context.getBean(RecordingEmailSender.class);
          RecordingSpanExporter exporter = context.getBean(RecordingSpanExporter.class);
          SdkTracerProvider tracerProvider = context.getBean(SdkTracerProvider.class);
          emailSender.prepare();

          Span parent = tracer.nextSpan().name("http.parent.test").start();
          try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);
          } finally {
            parent.end();
          }

          assertThat(emailSender.await()).isTrue();
          List<SpanData> spans = awaitSpans(exporter, tracerProvider, 3);
          SpanData parentData = onlySpanNamed(spans, "http.parent.test");
          SpanData workSpan = onlySpanNamed(spans, "email.verification.send");
          SpanData smtpSpan = onlySpanNamed(spans, "email.smtp.send");

          assertThat(workSpan.getTraceId()).isEqualTo(parentData.getTraceId());
          assertThat(workSpan.getParentSpanId()).isEqualTo(parentData.getSpanId());
          assertThat(attribute(workSpan, "email.operation")).isEqualTo("verification");
          assertThat(attribute(workSpan, "email.delivery.outcome")).isEqualTo("success");
          assertThat(smtpSpan.getTraceId()).isEqualTo(workSpan.getTraceId());
          assertThat(smtpSpan.getParentSpanId()).isEqualTo(workSpan.getSpanId());
          assertThat(attribute(smtpSpan, "email.operation")).isEqualTo("verification");
          assertThat(attribute(smtpSpan, "email.retry.attempt")).isEqualTo("1");
          assertThat(attribute(smtpSpan, "email.delivery.outcome")).isEqualTo("success");
        });
  }

  @Test
  @DisplayName("부모 Trace가 없으면 이메일 작업을 새 루트 span으로 시작한다")
  void noParent_createsRootWorkSpan() {
    contextRunner.run(
        context -> {
          AsyncEmailService asyncEmailService = context.getBean(AsyncEmailService.class);
          RecordingEmailSender emailSender = context.getBean(RecordingEmailSender.class);
          RecordingSpanExporter exporter = context.getBean(RecordingSpanExporter.class);
          SdkTracerProvider tracerProvider = context.getBean(SdkTracerProvider.class);
          emailSender.prepare();

          asyncEmailService.sendVerificationEmailAsync(USER_EMAIL, VERIFICATION_LINK);

          assertThat(emailSender.await()).isTrue();
          List<SpanData> spans = awaitSpans(exporter, tracerProvider, 2);
          SpanData workSpan = onlySpanNamed(spans, "email.verification.send");
          SpanData smtpSpan = onlySpanNamed(spans, "email.smtp.send");

          assertThat(workSpan.getParentSpanContext().isValid()).isFalse();
          assertThat(attribute(workSpan, "email.delivery.outcome")).isEqualTo("success");
          assertThat(smtpSpan.getParentSpanId()).isEqualTo(workSpan.getSpanId());
          assertThat(attribute(smtpSpan, "email.retry.attempt")).isEqualTo("1");
          assertThat(attribute(smtpSpan, "email.delivery.outcome")).isEqualTo("success");
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

    @Bean
    RecordingEmailSender emailSender() {
      return new RecordingEmailSender();
    }

    @Bean
    RetryingEmailDeliveryService retryingEmailDeliveryService(
        EmailSender emailSender, Tracer tracer) {
      return new RetryingEmailDeliveryService(emailSender, tracer);
    }

    @Bean
    AsyncEmailService asyncEmailService(
        RetryingEmailDeliveryService emailDeliveryService, Tracer tracer) {
      return new AsyncEmailService(emailDeliveryService, tracer, "no-reply@test.com", 30);
    }
  }

  static class RecordingEmailSender implements EmailSender {

    private volatile CountDownLatch latch = new CountDownLatch(0);

    void prepare() {
      latch = new CountDownLatch(1);
    }

    @Override
    public void send(String from, String to, String subject, String body) {
      latch.countDown();
    }

    boolean await() {
      try {
        return latch.await(3, TimeUnit.SECONDS);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
  }
}
