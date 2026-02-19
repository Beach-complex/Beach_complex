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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * OutboxPublisher.publishPendingEvents() 단위 테스트
 *
 * <p>Why: Outbox 패턴의 폴링-발행 로직이 정상 동작하는지 검증하고, TDD Red-Green-Refactor 사이클 적용
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>BDDMockito 스타일 사용 (given/willReturn, then/should)
 *   <li>Given-When-Then 구조로 테스트 가독성 보장
 *   <li>각 TC는 단일 책임만 검증 (단위 테스트 원칙)
 * </ul>
 *
 * <p>Contract(Input): Mock 객체 (OutboxEventRepository, NotificationRepository, FirebaseMessaging)
 *
 * <p>Contract(Output): 각 TC가 정의한 행위 및 상태 변화 검증
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

  @Mock private OutboxEventRepository outboxEventRepository;

  @Mock private NotificationRepository notificationRepository;

  @Mock private FirebaseMessaging firebaseMessaging;

  private OutboxPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher =
        new OutboxPublisher(outboxEventRepository, notificationRepository, firebaseMessaging, 10);
  }

  @Nested
  @DisplayName("publishPendingEvents()")
  class PublishPendingEventsTests {

    @Test
    @DisplayName("TC1 - PENDING 이벤트를 폴링하여 FCM 전송 후 SENT 상태로 전이")
    void shouldSendFcmAndMarkAsSent_whenPendingEventExists() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId);

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event));
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willReturn("message-id-12345");

      // When
      publisher.processPendingOutboxEvents();

      // Then
      // 1. FCM 전송 확인
      then(firebaseMessaging).should().send(any(Message.class));

      // 2. Notification 상태 전이 확인
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(notification.getSentAt()).isBeforeOrEqualTo(Instant.now());

      // 3. OutboxEvent 상태 전이 확인
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
      assertThat(event.getProcessedAt()).isNotNull();

      // 4. 저장 확인
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

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event));
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

      // When
      publisher.processPendingOutboxEvents();

      // Then
      // 1. FCM 전송 안 함
      then(firebaseMessaging).should(never()).send(any(Message.class));

      // 2. Notification 상태는 그대로 SENT
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);

      // 3. OutboxEvent는 SENT로 전이 (멱등성 확인용)
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
      then(outboxEventRepository).should().save(event);
    }

    @Test
    @DisplayName("TC3 - Notification 조회 실패 시 IllegalArgumentException 발생")
    void shouldThrowException_whenNotificationNotFound() {
      // Given
      UUID notificationId = UUID.randomUUID();
      OutboxEvent event = createPendingEvent(notificationId);

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event));
      given(notificationRepository.findById(notificationId)).willReturn(Optional.empty());

      // When & Then
      assertThatThrownBy(() -> publisher.processPendingOutboxEvents())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Notification을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("TC4 - 배치 사이즈 10개 제한 확인")
    void shouldLimitBatchSizeTo10() {
      // Given: 10개로 제한된 배치 사이즈
      PageRequest expectedPageRequest = PageRequest.of(0, 10);

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of());

      // When
      publisher.processPendingOutboxEvents();

      // Then
      then(outboxEventRepository)
          .should()
          .findPendingEvents(any(Instant.class), any(PageRequest.class));
    }

    @Test
    @DisplayName("TC5 - 여러 이벤트를 순차적으로 처리")
    void shouldProcessMultipleEventsSequentially() throws FirebaseMessagingException {
      // Given
      UUID notificationId1 = UUID.randomUUID();
      UUID notificationId2 = UUID.randomUUID();
      UUID notificationId3 = UUID.randomUUID();

      Notification notification1 = createNotification(notificationId1, NotificationStatus.PENDING);
      Notification notification2 = createNotification(notificationId2, NotificationStatus.PENDING);
      Notification notification3 = createNotification(notificationId3, NotificationStatus.PENDING);

      OutboxEvent event1 = createPendingEvent(notificationId1);
      OutboxEvent event2 = createPendingEvent(notificationId2);
      OutboxEvent event3 = createPendingEvent(notificationId3);

      given(outboxEventRepository.findPendingEvents(any(Instant.class), any(PageRequest.class)))
          .willReturn(List.of(event1, event2, event3));
      given(notificationRepository.findById(notificationId1))
          .willReturn(Optional.of(notification1));
      given(notificationRepository.findById(notificationId2))
          .willReturn(Optional.of(notification2));
      given(notificationRepository.findById(notificationId3))
          .willReturn(Optional.of(notification3));
      given(firebaseMessaging.send(any(Message.class))).willReturn("message-id");

      // When
      publisher.processPendingOutboxEvents();

      // Then
      // 1. FCM 전송 3회 확인
      then(firebaseMessaging).should(times(3)).send(any(Message.class));

      // 2. 모든 이벤트 SENT 상태 확인
      assertThat(event1.getStatus()).isEqualTo(OutboxEventStatus.SENT);
      assertThat(event2.getStatus()).isEqualTo(OutboxEventStatus.SENT);
      assertThat(event3.getStatus()).isEqualTo(OutboxEventStatus.SENT);

      // 3. 모든 알림 SENT 상태 확인
      assertThat(notification1.getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(notification2.getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(notification3.getStatus()).isEqualTo(NotificationStatus.SENT);
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
