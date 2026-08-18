package com.beachcheck.global.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.support.config.FirebaseTestConfig;
import com.beachcheck.support.config.TestcontainersConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"management.server.port=0", "management.tracing.sampling.probability=1.0"})
@Import({
  TestcontainersConfig.class,
  FirebaseTestConfig.class,
  HttpIngressTracingIntegrationTest.TraceTestConfiguration.class
})
@ActiveProfiles("test")
@DisplayName("HTTP ingress Trace 통합 계약")
class HttpIngressTracingIntegrationTest {

  private static final String TRACE_ENDPOINT = "/api/_debug/trace-test";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String INCOMING_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String INCOMING_PARENT_SPAN_ID = "00f067aa0ba902b7";

  @LocalServerPort private int serverPort;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RecordingSpanExporter spanExporter;
  @Autowired private SdkTracerProvider tracerProvider;
  @Autowired private Propagator propagator;

  @BeforeEach
  void clearExportedSpans() {
    flushSpans();
    spanExporter.clear();
  }

  @Test
  @DisplayName("traceparent가 없으면 server span을 만들고 requestId와 Trace MDC를 함께 유지한다")
  void withoutTraceparent_createsServerSpanAndKeepsRequestCorrelation() throws Exception {
    HttpResponse<String> response =
        get(TRACE_ENDPOINT + "/success", Map.of(REQUEST_ID_HEADER, "request-123"));
    Map<String, String> body = responseBody(response);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue(REQUEST_ID_HEADER)).contains("request-123");
    assertThat(body.get("requestId")).isEqualTo("request-123");
    String responseTraceId = body.get("traceId");
    String responseSpanId = body.get("spanId");
    SpanData serverSpan = onlyServerSpan();

    assertThat(responseTraceId).hasSize(32).isEqualTo(serverSpan.getTraceId());
    assertThat(responseSpanId).hasSize(16);
    assertThat(body.get("mdcTraceId")).isEqualTo(responseTraceId);
    assertThat(body.get("mdcSpanId")).isEqualTo(responseSpanId);
    assertThat(spanExporter.spans()).extracting(SpanData::getSpanId).contains(responseSpanId);
    assertHttpResult(serverSpan, "200", "SUCCESS");
  }

  @Test
  @DisplayName("유효한 W3C traceparent를 이어받고 query와 상관관계 ID는 span 속성에 넣지 않는다")
  void withTraceparent_continuesTraceWithoutSensitiveOrHighCardinalityAttributes()
      throws Exception {
    String traceparent = "00-" + INCOMING_TRACE_ID + "-" + INCOMING_PARENT_SPAN_ID + "-01";
    String secret = "do-not-export-this-query";
    String authorization = "Bearer do-not-export-this-token";

    HttpResponse<String> response =
        get(
            TRACE_ENDPOINT + "/success?ignored=" + secret,
            Map.of(
                REQUEST_ID_HEADER,
                "request-sensitive-123",
                "Authorization",
                authorization,
                "traceparent",
                traceparent));
    Map<String, String> body = responseBody(response);

    SpanData serverSpan = onlyServerSpan();
    List<Map.Entry<AttributeKey<?>, Object>> exportedAttributes =
        spanExporter.spans().stream()
            .flatMap(span -> span.getAttributes().asMap().entrySet().stream())
            .toList();
    String attributeValues =
        exportedAttributes.stream()
            .map(entry -> String.valueOf(entry.getValue()))
            .toList()
            .toString();

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.get("incomingTraceparent")).isEqualTo(traceparent);
    assertThat(propagator.fields()).containsExactlyInAnyOrder("traceparent", "tracestate");
    assertThat(body.get("traceId")).isEqualTo(serverSpan.getTraceId());
    assertThat(serverSpan.getTraceId())
        .withFailMessage("exported spans: %s", spanExporter.spans())
        .isEqualTo(INCOMING_TRACE_ID);
    assertThat(serverSpan.getParentSpanId()).isEqualTo(INCOMING_PARENT_SPAN_ID);
    assertThat(attributeValues)
        .doesNotContain(secret)
        .doesNotContain(authorization)
        .doesNotContain("request-sensitive-123");
    assertThat(exportedAttributes)
        .extracting(entry -> entry.getKey().getKey())
        .doesNotContain("requestId", "userId", "http.request.header.authorization");
  }

  @Test
  @DisplayName("인증 401과 handler 400/500을 status와 outcome으로 구분한다")
  void responseStatuses_areRecordedWithBoundedOutcomes() throws Exception {
    assertThat(get("/api/auth/me", Map.of()).statusCode()).isEqualTo(401);
    assertHttpResult(onlyServerSpan(), "401", "CLIENT_ERROR");

    spanExporter.clear();
    assertThat(get(TRACE_ENDPOINT + "/bad-request", Map.of()).statusCode()).isEqualTo(400);
    assertHttpResult(onlyServerSpan(), "400", "CLIENT_ERROR");

    spanExporter.clear();
    assertThat(get(TRACE_ENDPOINT + "/failure", Map.of()).statusCode()).isEqualTo(500);
    assertHttpResult(onlyServerSpan(), "500", "SERVER_ERROR");
  }

  private HttpResponse<String> get(String path, Map<String, String> headers) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + serverPort + path)).GET();
    headers.forEach(request::header);
    return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private Map<String, String> responseBody(HttpResponse<String> response) throws Exception {
    return objectMapper.readValue(response.body(), new TypeReference<>() {});
  }

  private SpanData onlyServerSpan() {
    flushSpans();
    List<SpanData> serverSpans =
        spanExporter.spans().stream().filter(span -> span.getKind() == SpanKind.SERVER).toList();
    assertThat(serverSpans).hasSize(1);
    return serverSpans.getFirst();
  }

  private void assertHttpResult(SpanData span, String status, String outcome) {
    Map<AttributeKey<?>, Object> attributes = span.getAttributes().asMap();
    assertThat(attributes).containsEntry(AttributeKey.stringKey("status"), status);
    assertThat(attributes).containsEntry(AttributeKey.stringKey("outcome"), outcome);
  }

  private void flushSpans() {
    CompletableResultCode flush = tracerProvider.forceFlush();
    flush.join(10, TimeUnit.SECONDS);
    assertThat(flush.isSuccess()).isTrue();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TraceTestConfiguration {

    @Bean
    RecordingSpanExporter recordingSpanExporter() {
      return new RecordingSpanExporter();
    }

    @Bean
    TraceTestController traceTestController(Tracer tracer) {
      return new TraceTestController(tracer);
    }
  }

  @RestController
  @RequestMapping(TRACE_ENDPOINT)
  static class TraceTestController {

    private final Tracer tracer;

    TraceTestController(Tracer tracer) {
      this.tracer = tracer;
    }

    @GetMapping("/success")
    Map<String, String> success(
        @RequestParam(required = false) String ignored,
        @RequestHeader(value = "traceparent", required = false) String incomingTraceparent) {
      Span currentSpan = tracer.currentSpan();
      assertThat(currentSpan).isNotNull();
      Map<String, String> response = new HashMap<>();
      response.put("requestId", MDC.get("requestId"));
      response.put("traceId", currentSpan.context().traceId());
      response.put("spanId", currentSpan.context().spanId());
      response.put("mdcTraceId", MDC.get("traceId"));
      response.put("mdcSpanId", MDC.get("spanId"));
      if (incomingTraceparent != null) {
        response.put("incomingTraceparent", incomingTraceparent);
      }
      return response;
    }

    @GetMapping("/bad-request")
    void badRequest() {
      throw new IllegalArgumentException("trace test bad request");
    }

    @GetMapping("/failure")
    void failure() {
      throw new TraceTestFailureException();
    }
  }

  static class TraceTestFailureException extends RuntimeException {

    private static final long serialVersionUID = 1L;
  }

  static class RecordingSpanExporter implements SpanExporter {

    private final List<SpanData> spans = new CopyOnWriteArrayList<>();

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      this.spans.addAll(spans);
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }

    List<SpanData> spans() {
      return new ArrayList<>(spans);
    }

    void clear() {
      spans.clear();
    }
  }
}
