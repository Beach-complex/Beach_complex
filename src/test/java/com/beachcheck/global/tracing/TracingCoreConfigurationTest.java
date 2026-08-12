package com.beachcheck.global.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.TracingProperties;
import org.springframework.boot.actuate.autoconfigure.tracing.otlp.OtlpAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Micrometer/OpenTelemetry 공통 Trace 설정")
class TracingCoreConfigurationTest {

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
                  MicrometerTracingAutoConfiguration.class,
                  OtlpAutoConfiguration.class));

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("OTLP endpoint가 없어도 Tracer와 로그 상관관계가 동작하고 exporter는 만들지 않는다")
  void withoutOtlpEndpoint_createsTraceContextWithoutExporter() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(Tracer.class).doesNotHaveBean(SpanExporter.class);
          assertThat(
                  context.getEnvironment().getProperty("management.tracing.sampling.probability"))
              .isEqualTo("0.1");
          assertThat(
                  context
                      .getBean(Resource.class)
                      .getAttribute(AttributeKey.stringKey("service.name")))
              .isEqualTo("beach-complex");

          Tracer tracer = context.getBean(Tracer.class);
          Span span = tracer.nextSpan().name("trace-core-test").start();
          try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            assertThat(span.context().traceId()).isNotBlank().hasSize(32);
            assertThat(span.context().spanId()).isNotBlank().hasSize(16);
            assertThat(MDC.get("traceId")).isEqualTo(span.context().traceId());
            assertThat(MDC.get("spanId")).isEqualTo(span.context().spanId());
          } finally {
            span.end();
          }

          assertThat(MDC.get("traceId")).isNull();
          assertThat(MDC.get("spanId")).isNull();
        });
  }

  @Test
  @DisplayName("OTLP endpoint와 sampling 값은 환경 설정으로 주입된다")
  void withOtlpEndpoint_bindsExporterAndSamplingConfiguration() {
    contextRunner
        .withPropertyValues(
            "management.tracing.sampling.probability=0.25",
            "management.otlp.tracing.endpoint=http://127.0.0.1:4318/v1/traces",
            "management.otlp.tracing.timeout=5s")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SpanExporter.class);
              assertThat(context.getBean(TracingProperties.class).getSampling().getProbability())
                  .isEqualTo(0.25f);

              Tracer tracer = context.getBean(Tracer.class);
              assertThatCode(
                      () -> {
                        Span span = tracer.nextSpan().name("unavailable-exporter-test").start();
                        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                          // 업무 처리 구간: exporter endpoint 연결 여부와 독립적으로 완료되어야 한다.
                          assertThat(tracer.currentSpan()).isNotNull();
                          assertThat(tracer.currentSpan().context().traceId())
                              .isEqualTo(span.context().traceId());
                        } finally {
                          span.end();
                        }
                      })
                  .doesNotThrowAnyException();
            });
  }
}
