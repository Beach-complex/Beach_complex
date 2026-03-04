package com.beachcheck.service;

import static com.beachcheck.domain.Notification.NotificationStatus;
import static com.beachcheck.domain.Notification.NotificationType;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus;
import static com.beachcheck.domain.OutboxEvent.OutboxEventType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Why: OutboxEventDispatcher.dispatch()의 FCM 전송 및 상태 전이 로직 검증
 *
 * <p>Policy: BDDMockito 스타일, Given-When-Then 구조
 *
 * <p>Contract(Input): Mock 객체 (OutboxEventRepository, NotificationRepository, FirebaseMessaging)
 *
 * <p>Contract(Output): 각 TC가 정의한 상태 전이 및 저장 호출 검증
 */
@ExtendWith(MockitoExtension.class)
class OutboxEventDispatcherTest {

  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private NotificationRepository notificationRepository;
  @Mock private FirebaseMessaging firebaseMessaging;

  private OutboxEventDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher =
        new OutboxEventDispatcher(outboxEventRepository, notificationRepository, firebaseMessaging);
  }

  @Nested
  @DisplayName("dispatch()")
  class DispatchTests {

    @Test
    @DisplayName("TC1 - FCM 전송 성공 후 SENT 상태로 전이")
    void shouldSendFcmAndMarkAsSent_whenPendingEventExists() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId);

      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willReturn("message-id-12345");

      // When
      dispatcher.dispatch(event);

      // Then
      then(firebaseMessaging).should().send(any(Message.class));
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(notification.getSentAt()).isBeforeOrEqualTo(Instant.now());
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
      assertThat(event.getProcessedAt()).isNotNull();
      then(notificationRepository).should().save(notification);
      then(outboxEventRepository).should().save(event);
    }

    @Test
    @DisplayName("TC2 - 멱등성: 이미 SENT 상태인 Notification은 FCM 전송 스킵")
    void shouldSkipFcmSend_whenNotificationAlreadySent() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.SENT);
      notification.setSentAt(Instant.now().minusSeconds(100));
      OutboxEvent event = createPendingEvent(notificationId);

      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

      // When
      dispatcher.dispatch(event);

      // Then
      then(firebaseMessaging).should(never()).send(any(Message.class));
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
      then(outboxEventRepository).should().save(event);
    }

    @Test
    @DisplayName("TC3 - Notification 조회 실패 시 IllegalArgumentException 발생")
    void shouldThrowException_whenNotificationNotFound() {
      // Given
      UUID notificationId = UUID.randomUUID();
      OutboxEvent event = createPendingEvent(notificationId);

      given(notificationRepository.findById(notificationId)).willReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(() -> dispatcher.dispatch(event))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Notification을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("TC4 - FCM 전송 실패 시 FAILED_RETRIABLE 전이 + Exponential Backoff")
    void shouldMarkAsFailedRetriable_whenFcmFails() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId); // retryCount = 0 → backoff = 1s

      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class)))
          .willThrow(mock(FirebaseMessagingException.class));

      // When
      Instant before = Instant.now();
      dispatcher.dispatch(event);
      Instant after = Instant.now();

      // Then
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_RETRIABLE);
      assertThat(event.getRetryCount()).isEqualTo(1);
      assertThat(event.getNextRetryAt()).isBetween(before.plusSeconds(1), after.plusSeconds(1));
      then(outboxEventRepository).should().save(event);
    }

    @Test
    @DisplayName("TC5 - 재시도 횟수 초과 시 FAILED_PERMANENT 전이")
    void shouldMarkAsFailedPermanent_whenRetryCountExceeded() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId);
      event.setRetryCount(3); // 최대 재시도 횟수 도달

      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class)))
          .willThrow(mock(FirebaseMessagingException.class));

      // When
      dispatcher.dispatch(event);

      // Then
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_PERMANENT);
      assertThat(event.getProcessedAt()).isNotNull();
      // TODO(PR#4/5): 영구 실패 분류/정책 확정 후 Notification.status 기대값 assert 추가
      // (FAILED로 동기화할지, PENDING 유지할지 정책에 따라 테스트 고정)
      then(outboxEventRepository).should().save(event);
    }
  }

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
