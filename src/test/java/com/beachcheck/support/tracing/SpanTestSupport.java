package com.beachcheck.support.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SpanTestSupport {

  private SpanTestSupport() {}

  public static List<SpanData> awaitSpans(
      RecordingSpanExporter exporter, SdkTracerProvider tracerProvider, int expectedCount) {
    for (int attempt = 0; attempt < 100; attempt++) {
      flushSpans(tracerProvider);
      if (exporter.spans().size() >= expectedCount) {
        return exporter.spans();
      }
      try {
        Thread.sleep(10);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Span 내보내기 대기가 중단되었습니다.", exception);
      }
    }
    return exporter.spans();
  }

  public static void flushSpans(SdkTracerProvider tracerProvider) {
    CompletableResultCode flush = tracerProvider.forceFlush();
    flush.join(2, TimeUnit.SECONDS);
    assertThat(flush.isSuccess()).isTrue();
  }
}
