package com.beachcheck.outbox.tracing;

import io.micrometer.tracing.Link;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Why: Outbox 생산 trace와 지연·재시도 가능한 소비 시도 trace의 인과관계를 Span Link로 보존한다.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>소비 시도는 항상 독립된 CONSUMER root span으로 기록한다.
 *   <li>저장된 traceparent가 없거나 유효하지 않으면 Link 없이 업무 처리를 계속한다.
 *   <li>trace context 원문은 로그나 span attribute에 기록하지 않는다.
 * </ul>
 *
 * <p>Contract(Input): producerTraceparent는 W3C version 00 형식 또는 null일 수 있다.
 *
 * <p>Contract(Output): action을 처리 span scope에서 실행하고 원래 예외를 그대로 전파한다.
 */
@Component
public class OutboxTracing {

  private static final String PROCESS_SPAN_NAME = "outbox process";
  private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
  private static final String ZERO_SPAN_ID = "0000000000000000";
  private static final Pattern TRACEPARENT_PATTERN =
      Pattern.compile("^00-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

  private final Tracer tracer;

  public OutboxTracing(Tracer tracer) {
    this.tracer = tracer;
  }

  public String captureCurrentTraceparent() {
    Span currentSpan = tracer.currentSpan();
    if (currentSpan == null) {
      return null;
    }

    TraceContext context = currentSpan.context();
    String flags = Boolean.TRUE.equals(context.sampled()) ? "01" : "00";
    return "00-" + context.traceId() + "-" + context.spanId() + "-" + flags;
  }

  public void runLinked(String producerTraceparent, Runnable action) {
    Span.Builder builder =
        tracer.spanBuilder().setNoParent().name(PROCESS_SPAN_NAME).kind(Span.Kind.CONSUMER);
    parseTraceparent(producerTraceparent).ifPresent(context -> builder.addLink(new Link(context)));

    Span span = builder.start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      action.run();
    } catch (RuntimeException | Error exception) {
      span.error(exception);
      throw exception;
    } finally {
      span.end();
    }
  }

  private Optional<TraceContext> parseTraceparent(String traceparent) {
    if (traceparent == null) {
      return Optional.empty();
    }

    Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent);
    if (!matcher.matches()) {
      return Optional.empty();
    }

    String traceId = matcher.group(1);
    String spanId = matcher.group(2);
    if (ZERO_TRACE_ID.equals(traceId) || ZERO_SPAN_ID.equals(spanId)) {
      return Optional.empty();
    }

    boolean sampled = (Integer.parseInt(matcher.group(3), 16) & 1) == 1;
    TraceContext context =
        tracer.traceContextBuilder().traceId(traceId).spanId(spanId).sampled(sampled).build();
    return Optional.of(context);
  }
}
