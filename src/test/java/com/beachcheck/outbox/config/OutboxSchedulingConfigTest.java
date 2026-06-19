package com.beachcheck.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.beachcheck.outbox.service.OutboxPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

/**
 * Why: 스케줄러 진입점이 OutboxPublisher에 위임하고, schedulerName/jobId MDC scope가 실행 후 누수되지 않는지 검증.
 *
 * <p>Policy: BDDMockito 스타일, Given-When-Then 구조
 *
 * <p>Contract(Input): Mock OutboxPublisher
 *
 * <p>Contract(Output): processPendingOutboxEvents() 호출 여부, 실행 후 MDC 정리 상태
 */
@ExtendWith(MockitoExtension.class)
class OutboxSchedulingConfigTest {

  @Mock private OutboxPublisher outboxPublisher;

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("TC1 - scheduleOutboxPolling은 OutboxPublisher에 위임한다")
  void shouldDelegateToOutboxPublisher() {
    // Given
    OutboxSchedulingConfig config = new OutboxSchedulingConfig(outboxPublisher);

    // When
    config.scheduleOutboxPolling();

    // Then
    then(outboxPublisher).should().processPendingOutboxEvents();
  }

  @Test
  @DisplayName("TC2 - 실행 종료 후 schedulerName/jobId MDC가 남지 않는다")
  void shouldClearSchedulerMdcAfterExecution() {
    // Given
    OutboxSchedulingConfig config = new OutboxSchedulingConfig(outboxPublisher);

    // When
    config.scheduleOutboxPolling();

    // Then
    assertThat(MDC.get("schedulerName")).isNull();
    assertThat(MDC.get("jobId")).isNull();
  }
}
