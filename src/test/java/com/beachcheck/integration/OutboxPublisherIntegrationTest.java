package com.beachcheck.integration;

import static com.beachcheck.domain.Notification.NotificationStatus;
import static com.beachcheck.domain.Notification.NotificationType;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus;
import static com.beachcheck.domain.OutboxEvent.OutboxEventType;
import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.domain.Notification;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.domain.User;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.service.OutboxPublisher;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * OutboxPublisher 통합 테스트
 *
 * <p>Why: 실제 DB, 실제 Bean을 사용하여 OutboxPublisher의 전체 플로우를 검증
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>IntegrationTest 상속: Testcontainers + 트랜잭션 롤백 자동 설정
 *   <li>FirebaseMessaging만 Mock (실제 FCM 호출 방지)
 * </ul>
 */
class OutboxPublisherIntegrationTest extends IntegrationTest {

  @Autowired private OutboxPublisher outboxPublisher;

  @Autowired private OutboxEventRepository outboxEventRepository;

  @Autowired private NotificationRepository notificationRepository;

  @Autowired private UserRepository userRepository;

  @MockBean private FirebaseMessaging firebaseMessaging; // 외부 서비스는 Mock 처리 (E2E 테스트에서는 실제 호출)

  @BeforeEach
  void setUp() throws FirebaseMessagingException {
    // 테스트 전에 데이터 정리 (FK 제약 조건 순서 고려)
    outboxEventRepository.deleteAll();
    notificationRepository.deleteAll();
    userRepository.deleteAll();

    // FirebaseMessaging Mock 기본 동작 설정
    given(firebaseMessaging.send(any(Message.class))).willReturn("mock-message-id");
  }

  @Test
  @DisplayName("TC1 - PENDING 이벤트를 폴링하여 FCM 전송 후 SENT 상태로 전이")
  void shouldProcessPendingEventAndMarkAsSent() throws FirebaseMessagingException {
    // Given: 실제 DB에 Notification + OutboxEvent 저장
    Notification notification = createAndSaveNotification(NotificationStatus.PENDING);
    OutboxEvent event = createAndSaveOutboxEvent(notification.getId());

    // When: OutboxPublisher 수동 실행
    outboxPublisher.processPendingOutboxEvents();

    // Then: 이벤트 상태 확인
    OutboxEvent processedEvent =
        outboxEventRepository
            .findById(event.getId())
            .orElseThrow(() -> new IllegalStateException("OutboxEvent를 찾을 수 없습니다"));
    assertThat(processedEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    assertThat(processedEvent.getProcessedAt()).isNotNull();

    // Then: Notification 상태 확인
    Notification processedNotification =
        notificationRepository
            .findById(notification.getId())
            .orElseThrow(() -> new IllegalStateException("Notification을 찾을 수 없습니다"));
    assertThat(processedNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(processedNotification.getSentAt()).isNotNull();

    // Then: FCM 전송 확인
    then(firebaseMessaging).should().send(any(Message.class));
  }

  @Test
  @DisplayName("TC2 - 여러 PENDING 이벤트를 배치 처리")
  void shouldProcessMultiplePendingEvents() throws FirebaseMessagingException {
    // Given: 3개의 PENDING 이벤트 생성
    Notification notification1 = createAndSaveNotification(NotificationStatus.PENDING);
    Notification notification2 = createAndSaveNotification(NotificationStatus.PENDING);
    Notification notification3 = createAndSaveNotification(NotificationStatus.PENDING);

    createAndSaveOutboxEvent(notification1.getId());
    createAndSaveOutboxEvent(notification2.getId());
    createAndSaveOutboxEvent(notification3.getId());

    // When: OutboxPublisher 실행
    outboxPublisher.processPendingOutboxEvents();

    // Then: 모든 이벤트가 SENT 상태
    List<OutboxEvent> allEvents = outboxEventRepository.findAll();
    assertThat(allEvents).hasSize(3).allMatch(e -> e.getStatus() == OutboxEventStatus.SENT);

    // Then: 모든 Notification이 SENT 상태
    List<Notification> allNotifications = notificationRepository.findAll();
    assertThat(allNotifications).hasSize(3).allMatch(n -> n.getStatus() == NotificationStatus.SENT);
  }

  @Test
  @DisplayName("TC3 - 멱등성: 이미 SENT 상태면 FCM 전송 스킵")
  void shouldSkipAlreadySentNotification() throws FirebaseMessagingException {
    // Given: SENT 상태의 Notification + PENDING OutboxEvent
    Notification notification = createAndSaveNotification(NotificationStatus.SENT);
    OutboxEvent event = createAndSaveOutboxEvent(notification.getId());

    // When: OutboxPublisher 실행
    outboxPublisher.processPendingOutboxEvents();

    // Then: OutboxEvent는 SENT로 전이
    OutboxEvent processedEvent =
        outboxEventRepository
            .findById(event.getId())
            .orElseThrow(() -> new IllegalStateException("OutboxEvent를 찾을 수 없습니다"));
    assertThat(processedEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);

    // Then: FCM 전송 안 됨
    then(firebaseMessaging).should(never()).send(any(Message.class));
  }

  // TODO(PR#3 재시도 로직): Notification 조회 실패 시 에러 처리 테스트 추가
  // - FAILED_PERMANENT 상태로 전이하여 무한 재시도 방지
  // - 재시도 카운트 관리
  // - Dead Letter Queue 패턴

  // ==========================
  // 테스트 헬퍼 메서드
  // ==========================

  /**
   * 랜덤 이메일을 가진 User 생성 및 저장
   *
   * <p>Why: Email unique constraint 회피를 위해 UUID 기반 랜덤 이메일 생성
   */
  private User createAndSaveUser() {
    String randomEmail = "test-" + UUID.randomUUID() + "@example.com";
    User user = User.create(randomEmail, "password123", "테스트유저");
    return userRepository.save(user);
  }

  private Notification createAndSaveNotification(NotificationStatus status) {
    // FK 제약 조건: User 먼저 생성
    User user = createAndSaveUser();

    Notification notification =
        Notification.createPending(
            user.getId(), // 실제 존재하는 User ID
            NotificationType.TEST,
            "테스트 알림",
            "테스트 메시지",
            "fcm-token-12345");
    notification.setStatus(status);

    if (status == NotificationStatus.SENT) {
      notification.setSentAt(now());
    }

    return notificationRepository.save(notification);
  }

  private OutboxEvent createAndSaveOutboxEvent(UUID notificationId) {
    OutboxEvent event =
        OutboxEvent.createPending(notificationId, OutboxEventType.PUSH_NOTIFICATION, null);
    return outboxEventRepository.save(event);
  }
}
