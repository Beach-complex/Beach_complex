package com.beachcheck.config;

import com.beachcheck.service.OutboxPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Why: @ConditionalOnProperty를 클래스 레벨에 적용하여 app.outbox.polling.enabled=false 시 빈 자체가 등록되지 않도록 함.
 * 메서드 레벨 적용은 @Scheduled에 효과 없음.
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>app.outbox.polling.enabled=true 일 때만 빈 등록
 *   <li>fixedDelay: app.outbox.polling.fixed-delay 값 사용 (기본 1초)
 * </ul>
 */
@Configuration
@ConditionalOnProperty(
    prefix = "app.outbox.polling",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class OutboxSchedulingConfig {

  private final OutboxPublisher outboxPublisher;

  public OutboxSchedulingConfig(OutboxPublisher outboxPublisher) {
    this.outboxPublisher = outboxPublisher;
  }

  /**
   * Why: PENDING/FAILED_RETRIABLE 상태의 OutboxEvent를 주기적으로 폴링하여 FCM 전송
   *
   * <p>Policy: fixedDelay 방식으로 이전 실행 완료 후 delay만큼 대기 (동시 실행 방지)
   */
  @Scheduled(fixedDelayString = "${app.outbox.polling.fixed-delay:1000}")
  public void scheduleOutboxPolling() {
    outboxPublisher.processPendingOutboxEvents();
  }
}
