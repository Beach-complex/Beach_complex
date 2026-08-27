package com.beachcheck.external.congestion;

import static com.beachcheck.support.tracing.SpanTestSupport.awaitSpans;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.beachcheck.global.tracing.QuerylessClientRequestObservationConvention;
import com.beachcheck.global.tracing.W3cTracePropagationConfig;
import com.beachcheck.support.tracing.RecordingSpanExporter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@DisplayName("CongestionClient Trace 통합 계약")
class CongestionClientTracingIntegrationTest {

  private static final String BASE_URL = "https://example.com";
  private static final String BEACH_CODE = "GYEONGPO";
  private static final String REQUEST_URL = BASE_URL + "/congestion/current?beach_id=" + BEACH_CODE;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TraceTestConfiguration.class)
          .withPropertyValues("management.tracing.sampling.probability=1.0");

  @Test
  @DisplayName("RestClient가 부모 Trace를 전파하고 traceparent를 SigV4 서명에 포함한다")
  void fetchCurrent_propagatesParentTraceAndSignsTraceparent() {
    contextRunner.run(
        context -> {
          // Given
          TraceClientFixture fixture = context.getBean(TraceClientFixture.class);
          Tracer tracer = context.getBean(Tracer.class);
          RecordingSpanExporter exporter = context.getBean(RecordingSpanExporter.class);
          SdkTracerProvider tracerProvider = context.getBean(SdkTracerProvider.class);
          AtomicReference<String> traceparent = new AtomicReference<>();
          AtomicReference<String> authorization = new AtomicReference<>();
          fixture
              .server()
              .expect(requestTo(REQUEST_URL))
              .andExpect(method(GET))
              .andExpect(
                  request -> {
                    traceparent.set(request.getHeaders().getFirst("traceparent"));
                    authorization.set(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
                  })
              .andRespond(
                  withSuccess(
                      """
                                                    {
                                                      "beach_id": "GYEONGPO",
                                                      "beach_name": "Gyeongpo Beach"
                                                    }
                                                    """,
                      MediaType.APPLICATION_JSON));
          Span parent = tracer.nextSpan().name("congestion.parent.test").start();

          // When
          CongestionCurrentResponse response;
          try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            response = fixture.client().fetchCurrent(BEACH_CODE);
          } finally {
            parent.end();
          }

          // Then
          List<SpanData> spans = awaitSpans(exporter, tracerProvider, 2);
          SpanData clientSpan = onlyClientSpan(spans);
          assertThat(response).isNotNull();
          assertThat(clientSpan.getTraceId()).isEqualTo(parent.context().traceId());
          assertThat(clientSpan.getParentSpanId()).isEqualTo(parent.context().spanId());
          assertThat(traceparent.get()).isNotBlank();
          assertThat(traceparent.get().split("-"))
              .containsExactly("00", clientSpan.getTraceId(), clientSpan.getSpanId(), "01");
          assertThat(signedHeaders(authorization.get())).contains("traceparent");
          assertThat(clientSpan.getAttributes().get(AttributeKey.stringKey("http.url")))
              .isEqualTo(BASE_URL + "/congestion/current");
          assertThat(clientSpan.getAttributes().asMap().values())
              .map(Object::toString)
              .allSatisfy(
                  value ->
                      assertThat(value)
                          .doesNotContain(
                              BEACH_CODE,
                              authorization.get(),
                              "test-access-key",
                              "test-secret-key",
                              "Signature="));
          fixture.server().verify();
        });
  }

  @Test
  @DisplayName("500 응답은 기존 null 반환을 유지하고 HTTP client span에 실패 결과를 기록한다")
  void fetchCurrent_serverErrorReturnsNullAndRecordsFailureResult() {
    contextRunner.run(
        context -> {
          // Given
          TraceClientFixture fixture = context.getBean(TraceClientFixture.class);
          Tracer tracer = context.getBean(Tracer.class);
          RecordingSpanExporter exporter = context.getBean(RecordingSpanExporter.class);
          SdkTracerProvider tracerProvider = context.getBean(SdkTracerProvider.class);
          fixture
              .server()
              .expect(requestTo(REQUEST_URL))
              .andExpect(method(GET))
              .andRespond(withServerError());
          Span parent = tracer.nextSpan().name("congestion.parent.test").start();

          // When
          CongestionCurrentResponse response;
          try (Tracer.SpanInScope ignored = tracer.withSpan(parent)) {
            response = fixture.client().fetchCurrent(BEACH_CODE);
          } finally {
            parent.end();
          }

          // Then
          SpanData clientSpan = onlyClientSpan(awaitSpans(exporter, tracerProvider, 2));
          assertThat(response).isNull();
          assertThat(clientSpan.getAttributes().get(AttributeKey.stringKey("status")))
              .isEqualTo("500");
          assertThat(clientSpan.getAttributes().get(AttributeKey.stringKey("outcome")))
              .isEqualTo("SERVER_ERROR");
          assertThat(clientSpan.getParentSpanId()).isEqualTo(parent.context().spanId());
          fixture.server().verify();
        });
  }

  private SpanData onlyClientSpan(List<SpanData> spans) {
    List<SpanData> clientSpans =
        spans.stream().filter(span -> span.getKind() == SpanKind.CLIENT).toList();
    assertThat(clientSpans).hasSize(1);
    return clientSpans.getFirst();
  }

  private String signedHeaders(String authorization) {
    assertThat(authorization).contains("SignedHeaders=");
    return authorization.substring(
        authorization.indexOf("SignedHeaders=") + "SignedHeaders=".length(),
        authorization.indexOf(',', authorization.indexOf("SignedHeaders=")));
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
      })
  @Import({W3cTracePropagationConfig.class, QuerylessClientRequestObservationConvention.class})
  static class TraceTestConfiguration {

    @Bean
    RecordingSpanExporter recordingSpanExporter() {
      return new RecordingSpanExporter();
    }

    @Bean
    AwsCredentialsProvider awsCredentialsProvider() {
      return StaticCredentialsProvider.create(
          AwsBasicCredentials.create("test-access-key", "test-secret-key"));
    }

    @Bean
    CongestionInterceptor congestionInterceptor(AwsCredentialsProvider credentialsProvider) {
      return new AwsSigV4Interceptor(credentialsProvider, "us-east-1");
    }

    @Bean
    TraceClientFixture traceClientFixture(
        RestClient.Builder builder, CongestionInterceptor congestionInterceptor) {
      MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
      CongestionClient client = new CongestionClient(BASE_URL, builder, congestionInterceptor);
      return new TraceClientFixture(client, server);
    }
  }

  private record TraceClientFixture(CongestionClient client, MockRestServiceServer server) {}
}
