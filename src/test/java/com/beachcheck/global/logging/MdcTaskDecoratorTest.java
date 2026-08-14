package com.beachcheck.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@DisplayName("MdcTaskDecorator 단위 테스트 — @Async 경계 MDC 전파/정리")
class MdcTaskDecoratorTest {

  private static final String REQUEST_ID = "requestId";
  private static final String USER_ID = "userId";
  private static final String TRACE_ID = "traceId";
  private static final String SPAN_ID = "spanId";

  private final MdcTaskDecorator decorator = new MdcTaskDecorator();

  @AfterEach
  void clearMdc() {
    // MDC는 ThreadLocal이므로 테스트 간 누수가 생기면 원인 추적이 어렵다.
    MDC.clear();
  }

  @Nested
  @DisplayName("부모 MDC 전파")
  class Propagation {

    @Test
    @DisplayName("부모 스레드 MDC를 작업 실행 중 worker thread로 복사하고 종료 후 정리한다")
    void givenCallerMdc_whenRun_thenVisibleDuringRunAndClearedAfter() {
      // given — 부모(제출 시점) MDC 세팅
      MDC.put(REQUEST_ID, "req-1");
      MDC.put(USER_ID, "user-1");
      AtomicReference<String> requestIdDuringRun = new AtomicReference<>();
      AtomicReference<String> userIdDuringRun = new AtomicReference<>();
      Runnable decorated =
          decorator.decorate(
              () -> {
                requestIdDuringRun.set(MDC.get(REQUEST_ID));
                userIdDuringRun.set(MDC.get(USER_ID));
              });

      // when — worker thread가 비어 있는 상태를 모사 후 실행
      MDC.clear();
      decorated.run();

      // then — 실행 중에는 부모 값이 보이고, 실행 후에는 정리됨
      assertThat(requestIdDuringRun.get()).isEqualTo("req-1");
      assertThat(userIdDuringRun.get()).isEqualTo("user-1");
      assertThat(MDC.get(REQUEST_ID)).isNull();
      assertThat(MDC.get(USER_ID)).isNull();
    }

    @Test
    @DisplayName("실제 Trace Context 없이 traceId와 spanId 문자열만 worker thread로 복사하지 않는다")
    void givenCallerTraceMdc_whenRun_thenTraceKeysAreNotPropagated() {
      // given — 부모 span에서 Micrometer가 MDC에 넣은 값을 모사
      MDC.put(REQUEST_ID, "req-1");
      MDC.put(TRACE_ID, "parent-trace");
      MDC.put(SPAN_ID, "parent-span");
      AtomicReference<String> requestIdDuringRun = new AtomicReference<>();
      AtomicReference<String> traceIdDuringRun = new AtomicReference<>();
      AtomicReference<String> spanIdDuringRun = new AtomicReference<>();
      Runnable decorated =
          decorator.decorate(
              () -> {
                requestIdDuringRun.set(MDC.get(REQUEST_ID));
                traceIdDuringRun.set(MDC.get(TRACE_ID));
                spanIdDuringRun.set(MDC.get(SPAN_ID));
              });

      // when — OTel Context가 전파되지 않은 worker thread에서 실행
      MDC.clear();
      decorated.run();

      // then — 기존 requestId만 유지하고, 가짜 Trace 상관관계는 만들지 않음
      assertThat(requestIdDuringRun.get()).isEqualTo("req-1");
      assertThat(traceIdDuringRun.get()).isNull();
      assertThat(spanIdDuringRun.get()).isNull();
    }

    @Test
    @DisplayName("부모 MDC가 비어 있으면 worker thread의 기존 값을 비우고 실행한다 (이전 작업 누수 차단)")
    void givenEmptyCallerMdc_whenRun_thenWorkerMdcClearedDuringRun() {
      // given — 부모 MDC가 빈 상태에서 데코레이트
      MDC.clear();
      AtomicReference<String> requestIdDuringRun = new AtomicReference<>();
      Runnable decorated = decorator.decorate(() -> requestIdDuringRun.set(MDC.get(REQUEST_ID)));

      // when — worker thread에 이전 작업의 값이 남아 있는 상태를 모사 후 실행
      MDC.put(REQUEST_ID, "leftover-from-previous-task");
      decorated.run();

      // then — 실행 중에는 부모(빈 값)가 적용되어 이전 값이 보이지 않음
      assertThat(requestIdDuringRun.get()).isNull();
    }
  }

  @Nested
  @DisplayName("worker thread MDC 복원/정리")
  class Restoration {

    @Test
    @DisplayName("worker thread에 기존 MDC가 있으면 실행 후 그 값으로 복원한다")
    void givenWorkerHadMdc_whenRun_thenRestoredAfter() {
      // given — 부모 값으로 데코레이트
      MDC.put(REQUEST_ID, "caller-req");
      AtomicReference<String> requestIdDuringRun = new AtomicReference<>();
      Runnable decorated = decorator.decorate(() -> requestIdDuringRun.set(MDC.get(REQUEST_ID)));

      // when — worker thread가 자체 값을 가진 상태를 모사 후 실행
      MDC.put(REQUEST_ID, "worker-existing");
      decorated.run();

      // then — 실행 중에는 부모 값, 실행 후에는 worker 원래 값으로 복원
      assertThat(requestIdDuringRun.get()).isEqualTo("caller-req");
      assertThat(MDC.get(REQUEST_ID)).isEqualTo("worker-existing");
    }

    @Test
    @DisplayName("runnable이 예외를 던져도 worker thread MDC를 정리한다 (try-finally 보장)")
    void givenRunnableThrows_whenRun_thenMdcStillCleared() {
      // given — 부모 값 세팅 후 예외를 던지는 작업
      MDC.put(REQUEST_ID, "req-1");
      Runnable decorated =
          decorator.decorate(
              () -> {
                throw new RuntimeException("boom");
              });

      // when — worker thread가 비어 있는 상태를 모사 후 실행
      MDC.clear();
      Throwable thrown = catchThrowable(decorated::run);

      // then — 예외는 그대로 전파되고 MDC는 정리됨
      assertThat(thrown).isInstanceOf(RuntimeException.class).hasMessage("boom");
      assertThat(MDC.get(REQUEST_ID)).isNull();
    }
  }
}
