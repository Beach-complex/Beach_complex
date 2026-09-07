package com.beachcheck.beach.scheduler;

import static com.beachcheck.support.fixture.BeachTestFixtures.createBeachWithLocation;
import static com.beachcheck.support.tracing.SpanTestSupport.flushSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.beachcheck.beach.domain.Beach;
import com.beachcheck.beach.repository.BeachConditionRepository;
import com.beachcheck.beach.repository.BeachRepository;
import com.beachcheck.external.congestion.CongestionClient;
import com.beachcheck.external.congestion.CongestionCurrentResponse;
import com.beachcheck.external.congestion.CongestionInterceptor;
import com.beachcheck.support.config.FirebaseTestConfig;
import com.beachcheck.support.config.TestcontainersConfig;
import com.beachcheck.support.tracing.RecordingSpanExporter;
import com.beachcheck.support.tracing.TracingTestConfiguration;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = {"management.tracing.sampling.probability=1.0"})
@Import({TestcontainersConfig.class, FirebaseTestConfig.class, TracingTestConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "app.congestion.sigv4-enabled=false")
@DisplayName("BeachConditionScheduler Trace 통합 계약")
class BeachConditionSchedulerTracingIntegrationTest {

  private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-03-06T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIMESTAMP, ZoneOffset.UTC);
  private static final String BASE_URL = "https://scheduler-trace.example.com";

  @Autowired private BeachRepository beachRepository;
  @Autowired private BeachConditionRepository beachConditionRepository;
  @Autowired private Tracer tracer;
  @Autowired private RestClient.Builder restClientBuilder;
  @Autowired private CongestionInterceptor congestionInterceptor;
  @Autowired private RecordingSpanExporter exporter;
  @Autowired private SdkTracerProvider tracerProvider;

  @BeforeEach
  void clearState() {
    MDC.clear();
    beachConditionRepository.deleteAllInBatch();
    beachRepository.deleteAllInBatch();
    flushSpans(tracerProvider);
    exporter.clear();
  }

  @AfterEach
  void cleanUp() {
    MDC.clear();
    beachConditionRepository.deleteAllInBatch();
    beachRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("job root에서 item과 HTTP/JDBC 계층을 만들고 MDC와 민감정보를 정리한다")
  void success_createsNestedSpansAndCleansContext() {
    // Given
    String forbiddenCode = "TRACE_SCHEDULER_" + UUID.randomUUID();
    String forbiddenName = "Sensitive Beach Name";
    Beach beach = createBeachWithLocation(forbiddenCode, forbiddenName, 129.1603, 35.1587);
    beachRepository.saveAndFlush(beach);
    flushSpans(tracerProvider);
    exporter.clear();

    MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
    CongestionClient congestionClient =
        new CongestionClient(BASE_URL, restClientBuilder, congestionInterceptor);
    AtomicReference<String> traceIdDuringRequest = new AtomicReference<>();
    AtomicReference<String> spanIdDuringRequest = new AtomicReference<>();
    AtomicReference<String> schedulerNameDuringRequest = new AtomicReference<>();
    AtomicReference<String> jobIdDuringRequest = new AtomicReference<>();
    AtomicReference<String> traceparentDuringRequest = new AtomicReference<>();
    server
        .expect(requestTo(BASE_URL + "/congestion/current?beach_id=" + forbiddenCode))
        .andExpect(method(GET))
        .andExpect(
            request -> {
              traceIdDuringRequest.set(MDC.get("traceId"));
              spanIdDuringRequest.set(MDC.get("spanId"));
              schedulerNameDuringRequest.set(MDC.get("schedulerName"));
              jobIdDuringRequest.set(MDC.get("jobId"));
              traceparentDuringRequest.set(request.getHeaders().getFirst("traceparent"));
            })
        .andRespond(
            withSuccess(
                """
                {
                  "beach_id": "external-id",
                  "beach_name": "external-name",
                  "input": {
                    "timestamp": "2026-03-06T00:00:00Z",
                    "weather": {"temp_c": 21.3, "rain_mm": 0.5, "wind_mps": 3.2}
                  },
                  "ai": {"score_raw": 10.0, "score_pct": 20.0, "level": "low", "model_version": "v1"}
                }
                """,
                MediaType.APPLICATION_JSON));
    BeachConditionScheduler scheduler =
        new BeachConditionScheduler(
            beachRepository, beachConditionRepository, congestionClient, FIXED_CLOCK, tracer, "ai");

    // When
    scheduler.refreshConditions();
    flushSpans(tracerProvider);
    server.verify();

    // Then
    List<SpanData> spans = exporter.spans();
    SpanData jobSpan = onlySpanNamed(spans, "scheduler.beach-condition.refresh");
    SpanData itemSpan = onlySpanNamed(spans, "scheduler.beach-condition.item");
    SpanData httpSpan = onlyHttpSpan(spans);
    List<SpanData> databaseSpans = databaseClientSpans(spans);
    SpanData initialSelect = onlyDatabaseSpanWithParent(databaseSpans, jobSpan.getSpanId());

    assertThat(jobSpan.getKind()).isEqualTo(SpanKind.INTERNAL);
    assertThat(jobSpan.getParentSpanContext().isValid()).isFalse();
    assertThat(attribute(jobSpan, "scheduler.name")).isEqualTo("beachConditionRefresh");
    assertThat(attribute(jobSpan, "scheduler.job.outcome")).isEqualTo("success");
    assertThat(itemSpan.getKind()).isEqualTo(SpanKind.INTERNAL);
    assertThat(itemSpan.getParentSpanId()).isEqualTo(jobSpan.getSpanId());
    assertThat(attribute(itemSpan, "scheduler.item.outcome")).isEqualTo("success");
    assertThat(attribute(itemSpan, "scheduler.item.skip.reason")).isNull();
    assertThat(httpSpan.getTraceId()).isEqualTo(jobSpan.getTraceId());
    assertThat(httpSpan.getKind()).isEqualTo(SpanKind.CLIENT);
    assertThat(httpSpan.getParentSpanId()).isEqualTo(itemSpan.getSpanId());
    assertThat(traceparentDuringRequest.get())
        .isEqualTo("00-" + httpSpan.getTraceId() + "-" + httpSpan.getSpanId() + "-01");
    assertThat(initialSelect.getParentSpanId()).isEqualTo(jobSpan.getSpanId());
    assertThat(databaseSpans)
        .allSatisfy(
            span ->
                assertThat(List.of(jobSpan.getSpanId(), itemSpan.getSpanId()))
                    .contains(span.getParentSpanId()));
    assertThat(traceIdDuringRequest.get()).isEqualTo(jobSpan.getTraceId());
    assertThat(spanIdDuringRequest.get()).isNotBlank();
    assertThat(schedulerNameDuringRequest.get()).isEqualTo("beachConditionRefresh");
    assertThat(jobIdDuringRequest.get()).isNotBlank();
    assertThat(MDC.get("traceId")).isNull();
    assertThat(MDC.get("spanId")).isNull();
    assertThat(MDC.get("schedulerName")).isNull();
    assertThat(MDC.get("jobId")).isNull();

    assertThat(spans)
        .allSatisfy(span -> assertSpanDoesNotContain(span, forbiddenCode, forbiddenName));
  }

  @Test
  @DisplayName("코드 누락과 null 응답을 각각 skipped로 기록하고 다음 item을 처리한다")
  void skips_recordBoundedReasonsAndContinue() {
    // Given
    BeachRepository beachRepository = mock(BeachRepository.class);
    BeachConditionRepository beachConditionRepository = mock(BeachConditionRepository.class);
    CongestionClient congestionClient = mock(CongestionClient.class);
    Beach missingCode = createBeachWithLocation(null, "missing", 129.1, 35.1);
    Beach noResponse = createBeachWithLocation("NO_RESPONSE_CODE", "no-response", 129.2, 35.2);
    given(beachRepository.findAll()).willReturn(List.of(missingCode, noResponse));
    given(congestionClient.fetchCurrent("NO_RESPONSE_CODE")).willReturn(null);
    BeachConditionScheduler scheduler =
        new BeachConditionScheduler(
            beachRepository, beachConditionRepository, congestionClient, FIXED_CLOCK, tracer, "ai");

    // When
    scheduler.refreshConditions();
    flushSpans(tracerProvider);

    // Then
    List<SpanData> spans = exporter.spans();
    SpanData jobSpan = onlySpanNamed(spans, "scheduler.beach-condition.refresh");
    List<SpanData> itemSpans =
        spans.stream()
            .filter(span -> span.getName().equals("scheduler.beach-condition.item"))
            .toList();
    assertThat(itemSpans).hasSize(2);
    assertThat(itemSpans)
        .allSatisfy(
            span -> {
              assertThat(span.getParentSpanId()).isEqualTo(jobSpan.getSpanId());
              assertThat(attribute(span, "scheduler.item.outcome")).isEqualTo("skipped");
            });
    assertThat(itemSpans)
        .extracting(span -> attribute(span, "scheduler.item.skip.reason"))
        .containsExactlyInAnyOrder("missing_code", "no_response");
    assertThat(attribute(jobSpan, "scheduler.job.outcome")).isEqualTo("success");
    then(beachConditionRepository).shouldHaveNoInteractions();
    then(congestionClient).should().fetchCurrent("NO_RESPONSE_CODE");
    verifyNoMoreInteractions(congestionClient);
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
  }

  @Test
  @DisplayName("예외는 원본을 전파하고 job/item에는 안전한 error 결과만 기록한다")
  void failure_recordsSafeErrorsAndRethrowsOriginalException() {
    // Given
    String forbiddenCode = "TRACE_SCHEDULER_ERROR_" + UUID.randomUUID();
    BeachRepository beachRepository = mock(BeachRepository.class);
    BeachConditionRepository beachConditionRepository = mock(BeachConditionRepository.class);
    CongestionClient congestionClient = mock(CongestionClient.class);
    Beach beach = createBeachWithLocation(forbiddenCode, "Sensitive Error Beach", 129.1, 35.1);
    RuntimeException failure = new IllegalStateException("database failed for " + forbiddenCode);
    given(beachRepository.findAll()).willReturn(List.of(beach));
    given(congestionClient.fetchCurrent(forbiddenCode)).willReturn(response());
    given(beachConditionRepository.save(any())).willThrow(failure);
    BeachConditionScheduler scheduler =
        new BeachConditionScheduler(
            beachRepository, beachConditionRepository, congestionClient, FIXED_CLOCK, tracer, "ai");

    // When & Then
    assertThatThrownBy(scheduler::refreshConditions).isSameAs(failure);
    flushSpans(tracerProvider);

    List<SpanData> spans = exporter.spans();
    SpanData jobSpan = onlySpanNamed(spans, "scheduler.beach-condition.refresh");
    SpanData itemSpan = onlySpanNamed(spans, "scheduler.beach-condition.item");
    assertThat(attribute(jobSpan, "scheduler.job.outcome")).isEqualTo("error");
    assertThat(attribute(itemSpan, "scheduler.item.outcome")).isEqualTo("error");
    assertThat(jobSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(itemSpan.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    assertThat(spans)
        .allSatisfy(span -> assertSpanDoesNotContain(span, forbiddenCode, "Sensitive Error Beach"));
  }

  private CongestionCurrentResponse response() {
    return new CongestionCurrentResponse(
        "external-id",
        "external-name",
        new CongestionCurrentResponse.InputContext(
            FIXED_TIMESTAMP,
            new CongestionCurrentResponse.WeatherInput(21.3, 0.5, 3.2),
            Boolean.FALSE),
        null,
        new CongestionCurrentResponse.OutputBlock(10.0, 20.0, "low", "v1"));
  }

  private SpanData onlySpanNamed(List<SpanData> spans, String name) {
    List<SpanData> matching = spans.stream().filter(span -> span.getName().equals(name)).toList();
    assertThat(matching).hasSize(1);
    return matching.getFirst();
  }

  private SpanData onlyHttpSpan(List<SpanData> spans) {
    List<SpanData> clientSpans =
        spans.stream()
            .filter(span -> span.getAttributes().get(AttributeKey.stringKey("http.url")) != null)
            .toList();
    assertThat(clientSpans).withFailMessage("exported spans: %s", spans).hasSize(1);
    return clientSpans.getFirst();
  }

  private List<SpanData> databaseClientSpans(List<SpanData> spans) {
    return spans.stream()
        .filter(span -> span.getKind() == SpanKind.CLIENT)
        .filter(span -> span.getAttributes().get(AttributeKey.stringKey("db.system.name")) != null)
        .toList();
  }

  private SpanData onlyDatabaseSpanWithParent(List<SpanData> spans, String parentSpanId) {
    List<SpanData> matching =
        spans.stream().filter(span -> span.getParentSpanId().equals(parentSpanId)).toList();
    assertThat(matching).hasSize(1);
    return matching.getFirst();
  }

  private String attribute(SpanData span, String key) {
    return span.getAttributes().get(AttributeKey.stringKey(key));
  }

  private void assertSpanDoesNotContain(SpanData span, String... forbiddenValues) {
    String serialized = span.toString();
    for (String forbiddenValue : forbiddenValues) {
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
      assertThat(serialized).doesNotContain(forbiddenValue);
    }
  }
}
