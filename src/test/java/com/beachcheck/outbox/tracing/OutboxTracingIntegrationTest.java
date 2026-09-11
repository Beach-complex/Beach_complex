package com.beachcheck.outbox.tracing;

import static com.beachcheck.support.tracing.SpanTestSupport.awaitSpans;
import static com.beachcheck.support.tracing.SpanTestSupport.flushSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beachcheck.support.base.IntegrationTest;
import com.beachcheck.support.tracing.RecordingSpanExporter;
import com.beachcheck.support.tracing.TracingTestConfiguration;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(TracingTestConfiguration.class)
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
@DisplayName("Outbox Span Link Trace 통합 계약")
class OutboxTracingIntegrationTest extends IntegrationTest {
  @Autowired private OutboxTracing outboxTracing;
  @Autowired private Tracer tracer;
  @Autowired private RecordingSpanExporter exporter;
  @Autowired private SdkTracerProvider tracerProvider;

  @BeforeEach
  void clearSpans() {
    flushSpans(tracerProvider);
    exporter.clear();
  }

  @Test
  @DisplayName("active span을 Link한 독립 CONSUMER root를 만들고 기존 scope 복원")
  void shouldCaptureAndLinkActiveProducerWithoutParentingConsumer() {
    // Given
    Span producer = tracer.nextSpan().name("producer").start();
    AtomicReference<String> actionSpanId = new AtomicReference<>();
    // When
    try (Tracer.SpanInScope ignored = tracer.withSpan(producer)) {
      String traceparent = outboxTracing.captureCurrentTraceparent();
      assertThat(traceparent)
          .isEqualTo(
              "00-%s-%s-01".formatted(producer.context().traceId(), producer.context().spanId()));
      outboxTracing.runLinked(
          traceparent, () -> actionSpanId.set(tracer.currentSpan().context().spanId()));
      assertThat(tracer.currentSpan().context().spanId()).isEqualTo(producer.context().spanId());
    } finally {
      producer.end();
    }
    assertThat(outboxTracing.captureCurrentTraceparent()).isNull();
    // Then
    SpanData processSpan = onlyProcessSpan(awaitSpans(exporter, tracerProvider, 2));
    assertThat(actionSpanId.get()).isEqualTo(processSpan.getSpanId());
    assertThat(processSpan.getKind()).isEqualTo(SpanKind.CONSUMER);
    assertThat(processSpan.getParentSpanContext().isValid()).isFalse();
    assertThat(processSpan.getTraceId()).isNotEqualTo(producer.context().traceId());
    assertThat(processSpan.getLinks())
        .singleElement()
        .satisfies(
            link -> {
              assertThat(link.getSpanContext().getTraceId())
                  .isEqualTo(producer.context().traceId());
              assertThat(link.getSpanContext().getSpanId()).isEqualTo(producer.context().spanId());
            });
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @NullAndEmptySource
  @ValueSource(
      strings = {
        " ",
        "malformed",
        "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
        "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01"
      })
  @DisplayName("context가 없거나 유효하지 않아도 Link 없이 실행")
  void shouldIgnoreMissingOrInvalidTraceparent(String traceparent) {
    outboxTracing.runLinked(traceparent, () -> assertThat(tracer.currentSpan()).isNotNull());
    SpanData processSpan = onlyProcessSpan(awaitSpans(exporter, tracerProvider, 1));
    assertThat(processSpan.getLinks()).isEmpty();
  }

  @Test
  @DisplayName("action 예외는 정제해 기록하고 동일 예외를 재전파")
  void shouldSanitizeAndRethrowActionFailure() {
    String sqlInput = "0199a001-0000-7000-8000-000000000003";
    IllegalStateException failure =
        new IllegalStateException(
            "FK violation for notificationId=" + sqlInput,
            new java.sql.SQLException("SQL input=" + sqlInput));
    assertThatThrownBy(
            () ->
                outboxTracing.runLinked(
                    null,
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    SpanData processSpan = onlyProcessSpan(awaitSpans(exporter, tracerProvider, 1));
    assertThat(processSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(processSpan.getStatus().getDescription()).isEqualTo("Outbox 처리 실패");
    assertThat(processSpan.getAttributes().get(AttributeKey.stringKey("error.type")))
        .isEqualTo(failure.getClass().getName());
    assertThat(processSpan.getAttributes().asMap().values().toString()).doesNotContain(sqlInput);
    assertThat(processSpan.getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getName()).isEqualTo("exception");
              assertThat(event.getAttributes().asMap().values().toString())
                  .doesNotContain(sqlInput);
              assertThat(event.getAttributes().get(AttributeKey.stringKey("exception.message")))
                  .isEqualTo("Outbox 처리 실패");
            });
  }

  private SpanData onlyProcessSpan(List<SpanData> spans) {
    List<SpanData> processSpans =
        spans.stream().filter(span -> span.getName().equals("outbox process")).toList();
    assertThat(processSpans).hasSize(1);
    return processSpans.getFirst();
  }
}
