package com.beachcheck.global.logging;

import io.micrometer.tracing.Tracer;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Why: {@code @Async} 경계에서 MDC가 끊기는 문제 해결 — MDC는 ThreadLocal 기반이라 작업이 worker thread로 넘어가면 부모 요청
 * 스레드의 {@code requestId}/{@code userId}와 Trace 컨텍스트가 사라진다. 작업 제출 시점의 MDC와 현재 span을 worker thread로
 * 전파해, 비동기 작업 로그와 자식 span을 동일 요청에 연결한다.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>작업 제출 시점(부모 스레드)의 상관관계 MDC를 복사해 worker thread 실행 직전 주입
 *   <li>{@code traceId}/{@code spanId} 문자열은 복사하지 않고 실제 Micrometer Trace span을 scope로 복원
 *   <li>부모 MDC가 비어 있으면 worker thread MDC를 {@code clear} (이전 작업 컨텍스트 누수 차단)
 *   <li>실행 후 {@code finally}에서 worker thread의 원래 MDC를 복원 — 스레드풀 재사용 시 다음 작업에 누수 방지
 *   <li>새 로그 키는 만들지 않는다. MDC1에서 정한 표준 키만 그대로 전파한다.
 * </ul>
 *
 * <p>Contract(Input): 비동기로 제출되는 임의의 {@link Runnable}.
 *
 * <p>Contract(Output): 부모 MDC가 적용된 상태로 실행되는 {@link Runnable}. 실행 종료(정상/예외 무관) 후 worker thread MDC는
 * 실행 직전 상태로 복원됨.
 *
 * <p>TODO: executor가 늘어나면 모든 애플리케이션 executor에 동일 데코레이터를 적용한다.
 */
public class MdcTaskDecorator implements TaskDecorator {

  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";

  private final Tracer tracer;

  public MdcTaskDecorator(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> callerContext = MDC.getCopyOfContextMap();
    Runnable traceAwareRunnable = tracer.currentTraceContext().wrap(runnable);
    if (callerContext != null) {
      callerContext.remove(TRACE_ID);
      callerContext.remove(SPAN_ID);
    }

    return () -> {
      Map<String, String> previousContext = MDC.getCopyOfContextMap();
      try {
        if (callerContext == null || callerContext.isEmpty()) {
          MDC.clear();
        } else {
          MDC.setContextMap(callerContext);
        }
        traceAwareRunnable.run();
      } finally {
        if (previousContext == null || previousContext.isEmpty()) {
          MDC.clear();
        } else {
          MDC.setContextMap(previousContext);
        }
      }
    };
  }
}
