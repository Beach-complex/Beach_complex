package com.beachcheck.integration;

import static com.beachcheck.domain.Notification.NotificationStatus;
import static com.beachcheck.domain.Notification.NotificationType;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus;
import static com.beachcheck.domain.OutboxEvent.OutboxEventType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.domain.Notification;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.domain.User;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Why: createAndSchedule()이 단일 트랜잭션에서 Notification + OutboxEvent를 원자적으로 저장하는지 검증 (AC1)
 *
 * <p>Policy:
 *
 * <ul>
 *   <li>IntegrationTest 상속 → @Transactional 자동 롤백, Testcontainers PostgreSQL 사용
 *   <li>OutboxPublisher 실행 없음 — FCM 전송 없이 DB 저장 원자성만 검증
 *   <li>entityManager.flush() + clear() 패턴으로 1차 캐시 무효화 후 실제 DB 조회
 * </ul>
 *
 * <p>Contract(Input): 각 테스트 전 고유 이메일 User 생성
 *
 * <p>Contract(Output): 각 테스트는 독립된 트랜잭션 롤백으로 DB 상태 격리
 */
class NotificationServiceIntegrationTest extends IntegrationTest {

  @Autowired private NotificationService notificationService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private OutboxEventRepository outboxEventRepository;
  @Autowired private UserRepository userRepository;
  @SpyBean private OutboxEventRepository outboxEventRepositorySpy;

  private User savedUser;

  @BeforeEach
  void setUp() {
    // UUID 기반 랜덤 이메일 → unique constraint 충돌 방지
    String randomEmail = "notify-" + UUID.randomUUID() + "@test.com";
    savedUser = userRepository.save(User.create(randomEmail, "password123", "테스트유저"));
  }

  @Test
  @DisplayName("TC1 - createAndSchedule() 호출 시 Notification + OutboxEvent 각 1건 저장 (AC1)")
  void shouldSaveNotificationAndOutboxEventAtomically() {
    // Given
    UUID userId = savedUser.getId();

    // When
    notificationService.createAndSchedule(
        userId, NotificationType.TEST, "테스트 알림", "알림 내용", "fcm-token-123");

    entityManager.flush(); // INSERT SQL을 현재 트랜잭션 내 DB에 전송 (auto-flush 의존 대신 명시적 보장)
    entityManager.clear(); // 1차 캐시 무효화 → 이후 조회가 캐시가 아닌 DB에서 직접 읽힘

    // Then
    Notification savedNotification = notificationRepository.findByUserId(userId).getFirst();
    OutboxEvent savedOutboxEvent =
        outboxEventRepository
            .findByNotificationId(savedNotification.getId())
            .orElseThrow(() -> new AssertionError("OutboxEvent가 저장되지 않았습니다"));

    assertThat(savedNotification).isNotNull();
    assertThat(savedOutboxEvent).isNotNull();
  }

  @Test
  @DisplayName("TC2 - OutboxEvent.notificationId == Notification.id (단일 트랜잭션 원자적 연결)")
  void shouldLinkOutboxEventToNotification() {
    // Given
    UUID userId = savedUser.getId();

    // When
    notificationService.createAndSchedule(
        userId, NotificationType.TEST, "연결 검증", "내용", "fcm-token-link");

    entityManager.flush(); // INSERT SQL을 현재 트랜잭션 내 DB에 전송 (auto-flush 의존 대신 명시적 보장)
    entityManager.clear(); // 1차 캐시 무효화 → 이후 조회가 캐시가 아닌 DB에서 직접 읽힘

    // Then
    Notification savedNotification = notificationRepository.findByUserId(userId).getFirst();
    OutboxEvent savedOutboxEvent =
        outboxEventRepository
            .findByNotificationId(savedNotification.getId())
            .orElseThrow(() -> new AssertionError("OutboxEvent가 저장되지 않았습니다"));

    assertThat(savedOutboxEvent.getNotificationId()).isEqualTo(savedNotification.getId());
  }

  @Test
  @DisplayName(
      "TC3 - 저장된 Notification 필드값 검증 (userId, title, message, recipientToken, type, status)")
  void shouldPersistNotificationWithCorrectFields() {
    // Given
    UUID userId = savedUser.getId();
    String title = "해변 예약 알림";
    String message = "내일 오전 10시 예약이 확정되었습니다.";
    String fcmToken = "fcm-token-field-check";

    // When
    notificationService.createAndSchedule(userId, NotificationType.TEST, title, message, fcmToken);

    entityManager.flush(); // INSERT SQL을 현재 트랜잭션 내 DB에 전송 (auto-flush 의존 대신 명시적 보장)
    entityManager.clear(); // 1차 캐시 무효화 → 이후 조회가 캐시가 아닌 DB에서 직접 읽힘

    // Then
    Notification saved = notificationRepository.findByUserId(userId).getFirst();

    assertThat(saved.getUserId()).isEqualTo(userId);
    assertThat(saved.getType()).isEqualTo(NotificationType.TEST);
    assertThat(saved.getTitle()).isEqualTo(title);
    assertThat(saved.getMessage()).isEqualTo(message);
    assertThat(saved.getRecipientToken()).isEqualTo(fcmToken);
    assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getSentAt()).isNull(); // OutboxPublisher 실행 전이므로 미발송
  }

  @Test
  @DisplayName("TC4 - 저장된 OutboxEvent 초기 상태 검증 (PENDING, retryCount=0, processedAt=null)")
  void shouldInitializeOutboxEventWithCorrectState() {
    // Given
    UUID userId = savedUser.getId();

    // When
    notificationService.createAndSchedule(
        userId, NotificationType.TEST, "상태 검증", "내용", "fcm-token-state");

    entityManager.flush(); // INSERT SQL을 현재 트랜잭션 내 DB에 전송 (auto-flush 의존 대신 명시적 보장)
    entityManager.clear(); // 1차 캐시 무효화 → 이후 조회가 캐시가 아닌 DB에서 직접 읽힘

    // Then
    Notification savedNotification = notificationRepository.findByUserId(userId).getFirst();
    OutboxEvent saved =
        outboxEventRepository
            .findByNotificationId(savedNotification.getId())
            .orElseThrow(() -> new AssertionError("OutboxEvent가 저장되지 않았습니다"));

    assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(saved.getEventType()).isEqualTo(OutboxEventType.PUSH_NOTIFICATION);
    assertThat(saved.getRetryCount()).isEqualTo(0);
    assertThat(saved.getNextRetryAt()).isNotNull(); // @PrePersist에서 createdAt 기준으로 설정
    assertThat(saved.getProcessedAt()).isNull(); // OutboxPublisher가 아직 처리하지 않은 상태
    assertThat(saved.getCreatedAt()).isNotNull();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("TC5 - OutboxEvent 저장 실패 시 Notification도 함께 롤백 (원자성 보장)")
  void shouldRollbackNotificationWhenOutboxEventSaveFails() {
    // Given
    UUID userId = savedUser.getId();
    doThrow(new RuntimeException("강제 저장 실패"))
        .when(outboxEventRepositorySpy)
        .save(any(OutboxEvent.class));

    // When: OutboxEvent 저장 실패 → 트랜잭션 전체 롤백
    assertThatThrownBy(
            () ->
                notificationService.createAndSchedule(
                    userId, NotificationType.TEST, "롤백 테스트", "내용", "fcm-token-rollback"))
        .isInstanceOf(RuntimeException.class);

    // Then: Notification도 저장되지 않음 (원자성 보장)
    assertThat(notificationRepository.findByUserId(userId)).isEmpty();
  }
}
