package com.beachcheck.global.tracing;

import static com.beachcheck.support.fixture.BeachTestFixtures.createBeachWithLocation;
import static com.beachcheck.support.fixture.UniqueTestFixtures.uniqueBeachCode;
import static com.beachcheck.support.fixture.UniqueTestFixtures.uniqueEmail;
import static com.beachcheck.support.fixture.UserTestFixtures.createUser;
import static com.beachcheck.support.tracing.SpanTestSupport.awaitSpans;
import static com.beachcheck.support.tracing.SpanTestSupport.flushSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.beachcheck.beach.dto.BeachDto;
import com.beachcheck.beach.repository.BeachRepository;
import com.beachcheck.beach.service.BeachService;
import com.beachcheck.support.base.IntegrationTest;
import com.beachcheck.support.tracing.RecordingSpanExporter;
import com.beachcheck.support.tracing.TracingTestConfiguration;
import com.beachcheck.user.repository.UserRepository;
import com.beachcheck.user.service.UserFavoriteService;
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
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@Import(TracingTestConfiguration.class)
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
@DisplayName("JPA/JDBC Trace 통합 계약")
class JdbcTracingIntegrationTest extends IntegrationTest {

  private static final AttributeKey<String> CACHE_SYSTEM = AttributeKey.stringKey("cache.system");
  private static final AttributeKey<String> CACHE_OPERATION =
      AttributeKey.stringKey("cache.operation");
  private static final AttributeKey<String> CACHE_RESULT = AttributeKey.stringKey("cache.result");

  @Autowired private BeachRepository beachRepository;
  @Autowired private BeachService beachService;
  @Autowired private UserRepository userRepository;
  @Autowired private UserFavoriteService favoriteService;
  @Autowired private CacheManager cacheManager;
  @Autowired private Tracer tracer;
  @Autowired private RecordingSpanExporter exporter;
  @Autowired private SdkTracerProvider tracerProvider;

  @BeforeEach
  void clearExportedSpans() {
    cacheManager.getCache("beachSummaries").clear();
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
    assertThat(databaseSpan.getStatus().getDescription()).isEqualTo("JDBC 쿼리 실행 실패");
    assertSpanDoesNotContain(databaseSpan, sensitiveValue);
  }

  @Test
  void cacheMissThenHit_recordsCacheAndDatabaseRelationship() {
    Span missParent = tracer.nextSpan().name("cache.miss").start();
    List<BeachDto> missResult = inParent(missParent, () -> beachService.findAll(null));
    List<SpanData> missSpans = awaitSpans(exporter, tracerProvider, 4);
    List<SpanData> cacheSpans = cacheSpans(missSpans);

    assertThat(missResult).isNotEmpty();
    assertThat(cacheSpans)
        .extracting(
            span -> span.getAttributes().get(CACHE_OPERATION),
            span -> span.getAttributes().get(CACHE_RESULT))
        .containsExactly(tuple("get", "miss"), tuple("put", "success"));
    SpanData databaseSpan = onlyDatabaseClientSpan(missSpans);
    assertThat(cacheSpans.getFirst().getEndEpochNanos())
        .isLessThanOrEqualTo(databaseSpan.getStartEpochNanos());
    assertThat(databaseSpan.getEndEpochNanos())
        .isLessThanOrEqualTo(cacheSpans.getLast().getStartEpochNanos());
    assertThat(cacheSpans)
        .allSatisfy(
            span -> assertThat(span.getParentSpanId()).isEqualTo(missParent.context().spanId()))
        .allSatisfy(span -> assertThat(span.toString()).doesNotContain("user:anonymous"));

    exporter.clear();
    inParent(tracer.nextSpan().name("cache.hit").start(), () -> beachService.findAll(null));
    List<SpanData> hitSpans = awaitSpans(exporter, tracerProvider, 2);
    assertThat(cacheSpans(hitSpans))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(CACHE_RESULT)).isEqualTo("hit"));
    assertThat(hitSpans)
        .noneMatch(
            span ->
                "postgresql"
                    .equals(span.getAttributes().get(AttributeKey.stringKey("db.system.name"))));
  }

  @Test
  void cacheEvict_recordsSanitizedSpan() {
    var user = userRepository.save(createUser(uniqueEmail("trace-evict"), "Trace User"));
    var beach =
        beachRepository.save(
            createBeachWithLocation(uniqueBeachCode(), "Trace Beach", 129.1603, 35.1587));
    String key = "user:" + user.getId();
    cacheManager.getCache("beachSummaries").put(key, "cached");
    flushSpans(tracerProvider);
    exporter.clear();

    inParent(
        tracer.nextSpan().name("cache.evict").start(),
        () -> favoriteService.addFavorite(user, beach.getId()));

    assertThat(cacheSpans(awaitSpans(exporter, tracerProvider, 3)))
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(CACHE_OPERATION)).isEqualTo("evict");
              assertThat(span.getAttributes().get(CACHE_RESULT)).isEqualTo("success");
              assertThat(span.toString()).doesNotContain(key);
            });
    assertThat(cacheManager.getCache("beachSummaries").get(key)).isNull();
  }

  private <T> T inParent(Span parent, Supplier<T> action) {
    try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
      return action.get();
    } finally {
      parent.end();
    }
  }

  private List<SpanData> cacheSpans(List<SpanData> spans) {
    return spans.stream()
        .filter(span -> "caffeine".equals(span.getAttributes().get(CACHE_SYSTEM)))
        .toList();
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
