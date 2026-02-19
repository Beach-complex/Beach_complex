package com.beachcheck.config;

import com.beachcheck.service.OutboxPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 스케줄링 설정
 *
 * <p>Why: 스케줄링 관련 설정을 중앙화하여 관심사 분리 (비즈니스 로직과 스케줄링 분리)
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>모든 @Scheduled 메서드는 이 클래스에 모음
 *   <li>비즈니스 로직 클래스는 순수 메서드만 제공
 *   <li>스케줄 변경 시 이 클래스만 수정
 * </ul>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

  private final OutboxPublisher outboxPublisher;

  public SchedulingConfig(OutboxPublisher outboxPublisher) {
    this.outboxPublisher = outboxPublisher;
  }

  /**
   * Outbox 이벤트 폴링 스케줄러
   *
   * <p>Why: PENDING/FAILED_RETRIABLE 상태의 OutboxEvent를 주기적으로 폴링하여 FCM 전송
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>fixedDelay: application.yml의 outbox.polling.fixed-delay 값 사용 (기본 1초)
   *   <li>enabled: application.yml의 outbox.polling.enabled가 true일 때만 실행
   * </ul>
   */
  @Scheduled(fixedDelayString = "${outbox.polling.fixed-delay:1000}")
  public void scheduleOutboxPolling() {
    outboxPublisher.processPendingOutboxEvents();
  }
}
