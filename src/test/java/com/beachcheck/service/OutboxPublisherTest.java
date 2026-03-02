package com.beachcheck.service;

import static com.beachcheck.domain.OutboxEvent.OutboxEventType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

/**
 * Why: OutboxPublisher의 폴링-위임 루프만 검증. 발송 로직은 OutboxEventDispatcherTest에서 검증.
 *
 * <p>Policy: BDDMockito 스타일, Given-When-Then 구조
 *
 * <p>Contract(Input): Mock 객체 (OutboxEventRepository, OutboxEventDispatcher)
 *
 * <p>Contract(Output): dispatcher.dispatch() 호출 여부 및 횟수
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Mock private OutboxEventRepository outboxEventRepository;

  @Mock private OutboxEventDispatcher outboxEventDispatcher;

  private OutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new OutboxPublisher(outboxEventRepository, outboxEventDispatcher, 10);
  }

  @Nested
  @DisplayName("processPendingOutboxEvents()")
  class ProcessPendingOutboxEventsTests {

    @Test
    @DisplayName("TC1 - PENDING 이벤트를 폴링하여 dispatcher에 위임")
    void shouldDelegateToDispatcher_whenPendingEventExists() {
      // Given
      OutboxEvent event = createPendingEvent(UUID.randomUUID());

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event));

      // When
      publisher.processPendingOutboxEvents();

      // Then
      then(outboxEventDispatcher).should().dispatch(event);
    }

    @Test
    @DisplayName("TC2 - PENDING 이벤트가 없으면 dispatcher 호출 안 함")
    void shouldNotCallDispatcher_whenNoPendingEvents() {
      // Given
      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of());

      // When
      publisher.processPendingOutboxEvents();

      // Then
      then(outboxEventDispatcher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("TC3 - 배치 사이즈 10개 제한으로 폴링")
    void shouldQueryWithBatchSizeLimit() {
      // Given
      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of());

      // When
      publisher.processPendingOutboxEvents();

      // Then
      then(outboxEventRepository)
          .should()
          .findPendingEvents(any(Instant.class), eq(PageRequest.of(0, 10)));
    }
  }

  @Nested
  @DisplayName("재시도 로직 (Exponential Backoff)")
  class RetryTests {

    @Test
    @DisplayName("TC6 - FCM 실패 시 FAILED_RETRIABLE 전이 + retryCount 증가 + nextRetryAt 설정")
    void shouldMarkAsFailedRetriable_whenFcmThrows() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId); // retryCount = 0

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event));
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willThrow(FirebaseMessagingException.class);

      // When
      publisher.processPendingOutboxEvents();

      // Then
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_RETRIABLE);
      assertThat(event.getRetryCount()).isEqualTo(1);
      assertThat(event.getNextRetryAt()).isAfter(Instant.now());
      then(outboxEventRepository).should().save(event);
    }

    @Test
    @DisplayName("TC7 - retryCount >= 3 시 FCM 실패하면 FAILED_PERMANENT 전이")
    void shouldMarkAsFailedPermanent_whenRetryCountExceedsMax() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId);
      event.setRetryCount(3); // 이미 3번 실패한 상태

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event));
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willThrow(FirebaseMessagingException.class);

      // When
      publisher.processPendingOutboxEvents();

      // Then
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_PERMANENT);
      then(outboxEventRepository).should().save(event);
    }
  }

  // 테스트 헬퍼 메서드

  private Notification createNotification(UUID notificationId, NotificationStatus status) {
    Notification notification =
        Notification.createPending(
            UUID.randomUUID(), NotificationType.TEST, "테스트 알림", "테스트 메시지", "fcm-token-12345");
    notification.setId(notificationId);
    notification.setStatus(status);
    return notification;
  }

  private OutboxEvent createPendingEvent(UUID notificationId) {
    return OutboxEvent.createPending(notificationId, OutboxEventType.PUSH_NOTIFICATION, null);
  }
}
