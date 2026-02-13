package com.beachcheck.integration;

import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.FAILED_PERMANENT;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.FAILED_RETRIABLE;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.PENDING;
import static com.beachcheck.domain.OutboxEvent.OutboxEventStatus.SENT;
import static com.beachcheck.domain.OutboxEvent.OutboxEventType.PUSH_NOTIFICATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.domain.Notification;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.domain.OutboxEvent.OutboxEventStatus;
import com.beachcheck.domain.User;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.beachcheck.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/** OutboxEvent 통합 테스트 (Repository + 추후 Service/트랜잭션 테스트 확장 가능) */
@DisplayName("OutboxEvent 통합 테스트")
class OutboxEventIntegrationTest extends IntegrationTest {

  @Autowired private OutboxEventRepository outboxEventRepository;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private UserRepository userRepository;

  // 공통 헬퍼 메서드

  private User createUser() {
    User user = User.create("test-" + UUID.randomUUID() + "@example.com", "password123", "테스트 사용자");
    return userRepository.save(user);
  }

  private Notification createNotification() {
    User user = createUser();
    Notification notification =
        Notification.createPending(
            user.getId(), Notification.NotificationType.TEST, "테스트 알림", "테스트 내용", "test-fcm-token");
    return notificationRepository.save(notification);
  }

  private OutboxEvent createEvent(OutboxEventStatus status, Instant nextRetryAt) {
    Notification notification = createNotification();
    OutboxEvent event =
        OutboxEvent.createPending(
            notification.getId(), PUSH_NOTIFICATION, "{\"title\":\"테스트\",\"body\":\"내용\"}");
    event.setStatus(status); // 테스트를 위해 상태 변경
    if (nextRetryAt != null) {
      event.setNextRetryAt(nextRetryAt); // 테스트를 위해 재시도 시간 변경
    }
    return outboxEventRepository.save(event); // JPA가 @PrePersist 자동 호출
  }

  @Nested
  @DisplayName("Repository 테스트")
  class RepositoryTest {

    @Nested
    @DisplayName("findPendingEvents()")
    class FindPendingEventsTest {

      @Test
      @DisplayName("PENDING 상태이고 재시도 시간 도달한 이벤트 조회")
      void shouldFindPendingEventsWithReachedRetryTime() {
        // Given
        Instant now = Instant.now();
        OutboxEvent pendingReached = createEvent(PENDING, now.minusSeconds(10)); // 10초 전
        OutboxEvent pendingFuture = createEvent(PENDING, now.plusSeconds(10)); // 10초 후

        // When
        List<OutboxEvent> result =
            outboxEventRepository.findPendingEvents(now, PageRequest.of(0, 10));

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(pendingReached.getId());
      }

      @Test
      @DisplayName("FAILED_RETRIABLE 상태이고 재시도 시간 도달한 이벤트 조회")
      void shouldFindFailedRetriableEventsWithReachedRetryTime() {
        // Given
        Instant now = Instant.now();
        OutboxEvent retriableReached = createEvent(FAILED_RETRIABLE, now.minusSeconds(10)); // 10초 전
        OutboxEvent retriableFuture = createEvent(FAILED_RETRIABLE, now.plusSeconds(10)); // 10초 후

        // When
        List<OutboxEvent> result =
            outboxEventRepository.findPendingEvents(now, PageRequest.of(0, 10));

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(retriableReached.getId());
      }

      @Test
      @DisplayName("SENT 상태는 조회되지 않음")
      void shouldNotFindSentEvents() {
        // Given
        Instant now = Instant.now();
        createEvent(SENT, now.minusSeconds(10));
        createEvent(PENDING, now.minusSeconds(10));

        // When
        List<OutboxEvent> result =
            outboxEventRepository.findPendingEvents(now, PageRequest.of(0, 10));

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PENDING);
      }

      @Test
      @DisplayName("FAILED_PERMANENT 상태는 조회되지 않음")
      void shouldNotFindFailedPermanentEvents() {
        // Given
        Instant now = Instant.now();
        createEvent(FAILED_PERMANENT, now.minusSeconds(10));
        createEvent(PENDING, now.minusSeconds(10));

        // When
        List<OutboxEvent> result =
            outboxEventRepository.findPendingEvents(now, PageRequest.of(0, 10));

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(PENDING);
      }

      @Test
      @DisplayName("createdAt 오름차순 정렬 (먼저 생성된 것부터)")
      void shouldOrderByCreatedAtAscending() throws InterruptedException {
        // Given
        Instant now = Instant.now();
        OutboxEvent event1 = createEvent(PENDING, now.minusSeconds(10));
        Thread.sleep(10); // createdAt 차이를 만들기 위해
        OutboxEvent event2 = createEvent(PENDING, now.minusSeconds(10));
        Thread.sleep(10);
        OutboxEvent event3 = createEvent(PENDING, now.minusSeconds(10));

        // When
        List<OutboxEvent> result =
            outboxEventRepository.findPendingEvents(now, PageRequest.of(0, 10));

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(event1.getId());
        assertThat(result.get(1).getId()).isEqualTo(event2.getId());
        assertThat(result.get(2).getId()).isEqualTo(event3.getId());
      }

      @Test
      @DisplayName("Pageable 적용 (배치 크기 제한)")
      void shouldApplyPageable() {
        // Given
        Instant now = Instant.now();
        createEvent(PENDING, now.minusSeconds(10));
        createEvent(PENDING, now.minusSeconds(10));
        createEvent(PENDING, now.minusSeconds(10));

        // When - 페이지 크기 2로 제한
        List<OutboxEvent> result =
            outboxEventRepository.findPendingEvents(now, PageRequest.of(0, 2));

        // Then
        assertThat(result).hasSize(2);
      }

      @Test
      @DisplayName("PENDING과 FAILED_RETRIABLE 모두 조회")
      void shouldFindBothPendingAndFailedRetriable() {
        // Given
        Instant now = Instant.now();
        createEvent(PENDING, now.minusSeconds(10));
        createEvent(FAILED_RETRIABLE, now.minusSeconds(10));
        createEvent(SENT, now.minusSeconds(10));

        // When
        List<OutboxEvent> result =
            outboxEventRepository.findPendingEvents(now, PageRequest.of(0, 10));

        // Then
        assertThat(result).hasSize(2);
        assertThat(result)
            .extracting(OutboxEvent::getStatus)
            .containsExactlyInAnyOrder(PENDING, FAILED_RETRIABLE);
      }
    }

    @Nested
    @DisplayName("countByStatus()")
    class CountByStatusTest {

      @Test
      @DisplayName("상태별 이벤트 개수 조회")
      void shouldCountByStatus() {
        // Given
        Instant now = Instant.now();
        createEvent(PENDING, now);
        createEvent(PENDING, now);
        createEvent(SENT, now);
        createEvent(FAILED_RETRIABLE, now);
        createEvent(FAILED_PERMANENT, now);

        // When & Then
        assertThat(outboxEventRepository.countByStatus(PENDING)).isEqualTo(2);
        assertThat(outboxEventRepository.countByStatus(SENT)).isEqualTo(1);
        assertThat(outboxEventRepository.countByStatus(FAILED_RETRIABLE)).isEqualTo(1);
        assertThat(outboxEventRepository.countByStatus(FAILED_PERMANENT)).isEqualTo(1);
      }

      @Test
      @DisplayName("해당 상태 이벤트가 없으면 0 반환")
      void shouldReturnZeroWhenNoEventsWithStatus() {
        // Given - 아무 데이터 없음

        // When & Then
        assertThat(outboxEventRepository.countByStatus(PENDING)).isEqualTo(0);
      }
    }
  }
}
