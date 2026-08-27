package com.beachcheck.global.tracing;

import static com.beachcheck.support.tracing.SpanTestSupport.awaitSpans;
import static com.beachcheck.support.tracing.SpanTestSupport.flushSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.beachcheck.beach.repository.BeachRepository;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(TracingTestConfiguration.class)
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
@DisplayName("JPA/JDBC Trace 통합 계약")
class JdbcTracingIntegrationTest extends IntegrationTest {

  @Autowired private BeachRepository beachRepository;
  @Autowired private Tracer tracer;
  @Autowired private RecordingSpanExporter exporter;
  @Autowired private SdkTracerProvider tracerProvider;

  @BeforeEach
  void clearExportedSpans() {
    flushSpans(tracerProvider);
    exporter.clear();
  }

  @Test
  @DisplayName("Repository SELECT는 bind 값을 제외한 database client span 하나를 만든다")
  void repositorySelect_createsOneSanitizedDatabaseClientSpan() {
    // Given
    String sensitiveCode = "TRACE_BIND_" + UUID.randomUUID();
    Span parent = tracer.nextSpan().name("jdbc.parent.test").start();

    // When
    try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
      beachRepository.findByCode(sensitiveCode);
    } finally {
      parent.end();
    }

    // Then
    SpanData databaseSpan = onlyDatabaseClientSpan(awaitSpans(exporter, tracerProvider, 2));
    Map<AttributeKey<?>, Object> attributes = databaseSpan.getAttributes().asMap();
    assertThat(databaseSpan.getTraceId()).isEqualTo(parent.context().traceId());
    assertThat(databaseSpan.getParentSpanId()).isEqualTo(parent.context().spanId());
    assertThat(databaseSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.UNSET);
    assertThat(attributes)
        .containsEntry(AttributeKey.stringKey("db.system.name"), "postgresql")
        .containsEntry(AttributeKey.stringKey("db.operation.name"), "SELECT");
    assertThat(attributes.keySet())
        .extracting(AttributeKey::getKey)
        .noneMatch(key -> key.startsWith("jdbc.params"));
    assertThat(attributes.values().toString()).doesNotContain(sensitiveCode);
  }

  @Test
  @DisplayName("실패한 JDBC query는 예외를 유지하고 database client span을 ERROR로 기록한다")
  void failedQuery_propagatesExceptionAndRecordsErrorSpan() {
    // Given
    String sensitiveValue = "TRACE_DB_SECRET_" + UUID.randomUUID();
    Span parent = tracer.nextSpan().name("jdbc.parent.test").start();

    // When & Then
    try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
      assertThatThrownBy(
              () ->
                  entityManager
                      .createNativeQuery("select cast(:value as integer)")
                      .setParameter("value", sensitiveValue)
                      .getSingleResult())
          .isInstanceOf(RuntimeException.class);
    } finally {
      parent.end();
    }

    SpanData databaseSpan = onlyDatabaseClientSpan(awaitSpans(exporter, tracerProvider, 2));
    assertThat(databaseSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(databaseSpan.getStatus().getDescription()).isEqualTo("JDBC query failed");
    assertSpanDoesNotContain(databaseSpan, sensitiveValue);
  }

  private SpanData onlyDatabaseClientSpan(List<SpanData> spans) {
    List<SpanData> databaseSpans =
        spans.stream()
            .filter(span -> span.getKind() == SpanKind.CLIENT)
            .filter(
                span ->
                    "postgresql"
                        .equals(span.getAttributes().get(AttributeKey.stringKey("db.system.name"))))
            .toList();
    assertThat(databaseSpans).hasSize(1);
    return databaseSpans.getFirst();
  }

  private void assertSpanDoesNotContain(SpanData span, String forbiddenValue) {
    assertThat(span.getName()).doesNotContain(forbiddenValue);
    assertThat(span.getStatus().getDescription()).doesNotContain(forbiddenValue);
    assertThat(span.getAttributes().asMap().values().toString()).doesNotContain(forbiddenValue);
    assertThat(span.getEvents())
        .allSatisfy(
            event -> {
              assertThat(event.getName()).doesNotContain(forbiddenValue);
              assertThat(event.getAttributes().asMap().values().toString())
                  .doesNotContain(forbiddenValue);
            });
    assertThat(span.toString()).doesNotContain(forbiddenValue);
  }
}
