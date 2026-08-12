package com.beachcheck.global.logging;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Why: {@code @Async} 경계에서 MDC가 끊기는 문제 해결 — MDC는 ThreadLocal 기반이라 작업이 worker thread로 넘어가면 부모 요청
 * 스레드의 {@code requestId}/{@code userId}가 사라진다. 작업 제출 시점의 MDC를 worker thread로 복사해, 비동기 작업 로그도 동일
 * 요청으로 추적할 수 있게 한다.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>작업 제출 시점(부모 스레드)의 상관관계 MDC를 복사해 worker thread 실행 직전 주입
 *   <li>{@code traceId}/{@code spanId}는 실제 Trace Context 없이 문자열만 복사하지 않음
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
 * <p>TODO: executor가 늘어나면 모든 애플리케이션 executor에 동일 데코레이터를 적용한다. traceId/spanId는 트레이싱 SDK가 자동 주입할
 * 예정이므로 본 데코레이터에서 다루지 않는다.
 */
public class MdcTaskDecorator implements TaskDecorator {

  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";

  @Override
  public Runnable decorate(Runnable runnable) {
    Map<String, String> callerContext = MDC.getCopyOfContextMap();
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
        runnable.run();
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
