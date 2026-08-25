package com.beachcheck.support.tracing;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingSpanExporter implements SpanExporter {

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

  public List<SpanData> spans() {
    return new ArrayList<>(spans);
  }

  public void clear() {
    spans.clear();
  }
}
