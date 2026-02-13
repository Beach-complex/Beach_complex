package com.beachcheck.domain;

import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.FAILED_PERMANENT;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.FAILED_RETRIABLE;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.PENDING;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** OutboxEvent 엔티티 상태 전이 메서드 테스트 */
@DisplayName("OutboxEvent 상태 전이 테스트")
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class OutboxEventTest {

  @Test
  @DisplayName("전송 성공 시 SENT 상태 전이 및 processedAt 기록")
  void shouldTransitionToSentAndRecordProcessedAt() {
    // Given
    OutboxEvent event = new OutboxEvent();
    event.setStatus(PENDING);

    // When
    event.markAsSent();

    // Then
    Instant processedAt = event.getProcessedAt();
    assertThat(event.getStatus()).isEqualTo(SENT);
    assertThat(processedAt).isNotNull();
    assertThat(processedAt).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @DisplayName("첫 시도 실패 시 retryCount 증가 및 nextRetryAt 갱신")
  void shouldIncrementRetryCountAndUpdateNextRetryAt() {
    // Given
    OutboxEvent event = new OutboxEvent();
    event.setStatus(PENDING);
    event.setRetryCount(0);
    Duration nextRetryDelay = Duration.ofSeconds(2);

    Instant beforeCall = Instant.now();

    // When
    event.markAsFailedRetriable(nextRetryDelay);

    // Then
    assertThat(event.getStatus()).isEqualTo(FAILED_RETRIABLE);
    assertThat(event.getRetryCount()).isEqualTo(1);
    assertThat(event.getNextRetryAt()).isNotNull();
    assertThat(event.getNextRetryAt())
        .isCloseTo(beforeCall.plus(nextRetryDelay), within(100, ChronoUnit.MILLIS));
  }

  @Test
  @DisplayName("재시도 실패 시 기존 retryCount에서 증가")
  void shouldIncrementFromExistingRetryCount() {
    // Given
    OutboxEvent event = new OutboxEvent();
    event.setStatus(FAILED_RETRIABLE);
    event.setRetryCount(2); // 이미 2번 재시도함

    // When
    event.markAsFailedRetriable(Duration.ofSeconds(8));

    // Then
    assertThat(event.getRetryCount()).isEqualTo(3);
    assertThat(event.getStatus()).isEqualTo(FAILED_RETRIABLE);
  }

  @Test
  @DisplayName("exponential backoff 간격 정확히 적용")
  void shouldApplyExponentialBackoffIntervals() {
    // Given
    OutboxEvent event1 = new OutboxEvent();
    event1.setRetryCount(0);

    OutboxEvent event2 = new OutboxEvent();
    event2.setRetryCount(1);

    OutboxEvent event3 = new OutboxEvent();
    event3.setRetryCount(2);

    Instant now = Instant.now();

    // When
    event1.markAsFailedRetriable(Duration.ofSeconds(2)); // 첫 재시도: 2초
    event2.markAsFailedRetriable(Duration.ofSeconds(4)); // 두 번째 재시도: 4초
    event3.markAsFailedRetriable(Duration.ofSeconds(8)); // 세 번째 재시도: 8초

    // Then
    // 각각 2초, 4초, 8초 후로 설정되어야 함
    assertThat(event1.getNextRetryAt())
        .isCloseTo(now.plusSeconds(2), within(100, ChronoUnit.MILLIS));
    assertThat(event2.getNextRetryAt())
        .isCloseTo(now.plusSeconds(4), within(100, ChronoUnit.MILLIS));
    assertThat(event3.getNextRetryAt())
        .isCloseTo(now.plusSeconds(8), within(100, ChronoUnit.MILLIS));
  }

  @Test
  @DisplayName("영구 실패 시 FAILED_PERMANENT 전이 및 processedAt 기록")
  void shouldTransitionToFailedPermanentAndRecordProcessedAt() {
    // Given
    OutboxEvent event = new OutboxEvent();
    event.setStatus(PENDING);
    event.setRetryCount(3);

    // When
    event.markAsFailedPermanent();

    // Then
    assertThat(event.getStatus()).isEqualTo(FAILED_PERMANENT);
    assertThat(event.getProcessedAt()).isNotNull();
    assertThat(event.getProcessedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @DisplayName("영구 실패 시 retryCount 증가하지 않음")
  void shouldNotIncrementRetryCountOnPermanentFailure() {
    // Given
    OutboxEvent event = new OutboxEvent();
    event.setRetryCount(3);

    // When
    event.markAsFailedPermanent();

    // Then
    assertThat(event.getRetryCount()).isEqualTo(3); // 그대로 유지
  }

  @Test
  @DisplayName("엔티티 생성 시 createdAt 자동 설정")
  void shouldSetCreatedAtOnCreate() {
    // Given
    OutboxEvent event = new OutboxEvent();
    Instant beforeCreate = Instant.now().minusMillis(100);

    // When
    event.onCreate(); // @PrePersist 시뮬레이션

    // Then
    assertThat(event.getCreatedAt()).isNotNull();
    assertThat(event.getCreatedAt()).isAfter(beforeCreate);
    assertThat(event.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @DisplayName("엔티티 생성 시 nextRetryAt 현재 시간으로 설정 (즉시 처리)")
  void shouldSetNextRetryAtToNowOnCreate() {
    // Given
    OutboxEvent event = new OutboxEvent();
    Instant beforeCreate = Instant.now().minusMillis(100);

    // When
    event.onCreate(); // 단위 테스트에서는 수동으로 @PrePersist 호출

    // Then
    assertThat(event.getNextRetryAt()).isNotNull();
    assertThat(event.getNextRetryAt()).isAfter(beforeCreate);
    assertThat(event.getNextRetryAt()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  @DisplayName("nextRetryAt 이미 설정된 경우 onCreate에서 덮어쓰지 않음")
  void shouldPreserveExistingNextRetryAtOnCreate() {
    // Given
    OutboxEvent event = new OutboxEvent();
    Instant customRetryAt = Instant.now().plusSeconds(10);
    event.setNextRetryAt(customRetryAt);

    // When
    event.onCreate();

    // Then
    assertThat(event.getNextRetryAt()).isEqualTo(customRetryAt); // 기존 값 유지
  }

  @Test
  @DisplayName("PENDING에서 SENT로 정상 전이")
  void shouldTransitionFromPendingToSent() {
    // Given
    OutboxEvent event = new OutboxEvent();
    event.setStatus(PENDING);

    // When
    event.markAsSent();

    // Then
    assertThat(event.getStatus()).isEqualTo(SENT);
  }

  @Test
  @DisplayName("FAILED_RETRIABLE에서 SENT로 정상 전이 (재시도 성공)")
  void shouldTransitionFromFailedRetriableToSent() {
    // Given
    OutboxEvent event = new OutboxEvent();
    event.setStatus(FAILED_RETRIABLE);
    event.setRetryCount(2);

    // When
    event.markAsSent();

    // Then
    assertThat(event.getStatus()).isEqualTo(SENT);
    assertThat(event.getRetryCount()).isEqualTo(2); // retryCount는 그대로 (성공 시 초기화 안 함)
  }
}
