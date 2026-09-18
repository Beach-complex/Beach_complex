package com.beachcheck.outbox.service;

import static com.beachcheck.notification.domain.Notification.NotificationStatus;
import static com.beachcheck.notification.domain.Notification.NotificationType;
import static com.beachcheck.outbox.domain.OutboxEvent.OutboxEventStatus;
import static com.beachcheck.outbox.domain.OutboxEvent.OutboxEventType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;
import static org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK;

import com.beachcheck.notification.domain.Notification;
import com.beachcheck.notification.repository.NotificationRepository;
import com.beachcheck.outbox.domain.OutboxEvent;
import com.beachcheck.outbox.repository.OutboxEventRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

/**
 * Why: OutboxEventDispatcher.dispatch()의 FCM 전송 및 상태 전이 로직 검증
 *
 * <p>Policy: BDDMockito 스타일, Given-When-Then 구조
 *
 * <p>상태 전이 정책: retriable 실패(FAILED_RETRIABLE) 시 Notification은 PENDING을 유지하고, permanent
 * 실패(FAILED_PERMANENT)로 확정될 때만 Notification을 FAILED로 전이한다.
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
  @Mock private Tracer tracer;
  @Mock private Span span;
  @Mock private Span.Builder spanBuilder;
  @Mock private TraceContext traceContext;
  @Mock private Tracer.SpanInScope spanInScope;

  private OutboxEventDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    given(tracer.nextSpan()).willReturn(span);
    given(span.name(any())).willReturn(span);
    given(span.start()).willReturn(span);
    given(tracer.withSpan(any())).willReturn(spanInScope);
    dispatcher =
        new OutboxEventDispatcher(
            outboxEventRepository, notificationRepository, firebaseMessaging, tracer);
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
      givenClientSpan();

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
    @DisplayName("TC1-1 - DB 커밋 전에는 성공 결과를 기록하지 않음")
    void shouldRecordSuccessOnlyAfterTransactionCommit() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId);

      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willReturn("message-id-12345");
      givenClientSpan();
      TransactionSynchronizationManager.initSynchronization();

      try {
        // When
        dispatcher.dispatch(event);

        // Then: dispatch() 반환 시점에는 아직 커밋 전이므로 성공 결과를 기록하지 않음
        then(span).should(never()).tag("outbox.item.outcome", "success");

        // When: 트랜잭션 커밋 완료 콜백 실행
        TransactionSynchronizationUtils.triggerAfterCommit();

        // Then
        then(span).should().tag("outbox.item.outcome", "success");
        TransactionSynchronizationUtils.triggerAfterCompletion(STATUS_COMMITTED);
      } finally {
        TransactionSynchronizationManager.clearSynchronization();
      }
    }

    @Test
    @DisplayName("TC1-2 - DB 롤백 시 성공 대신 오류 결과를 기록")
    void shouldRecordErrorWhenTransactionRollsBack() throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId);

      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willReturn("message-id-12345");
      givenClientSpan();
      TransactionSynchronizationManager.initSynchronization();

      try {
        // When
        dispatcher.dispatch(event);
        TransactionSynchronizationUtils.triggerAfterCompletion(STATUS_ROLLED_BACK);

        // Then
        then(span).should().tag("outbox.item.outcome", "error");
        then(span).should().error(any(IllegalStateException.class));
        then(span).should(never()).tag("outbox.item.outcome", "success");
      } finally {
        TransactionSynchronizationManager.clearSynchronization();
      }
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
      then(span).should().tag("outbox.item.outcome", "error");
      then(span).should().tag("outbox.failure.class", IllegalArgumentException.class.getName());
      then(span).should().error(any(IllegalStateException.class));
      then(spanInScope).should(atLeast(2)).close();
      then(span).should(atLeast(2)).end();
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
      givenClientSpan();

      // When
      Instant before = Instant.now();
      dispatcher.dispatch(event);
      Instant after = Instant.now();

      // Then
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_RETRIABLE);
      assertThat(event.getRetryCount()).isEqualTo(1);
      assertThat(event.getNextRetryAt()).isBetween(before.plusSeconds(1), after.plusSeconds(1));
      assertThat(notification.getStatus())
          .isEqualTo(NotificationStatus.PENDING); // retriable → Notification은 PENDING 유지
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
      givenClientSpan();

      // When
      dispatcher.dispatch(event);

      // Then
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_PERMANENT);
      assertThat(event.getProcessedAt()).isNotNull();
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(notification.getErrorMessage()).isNotNull();
      then(outboxEventRepository).should().save(event);
    }

    @Test
    @DisplayName("TC6 - 2번째 실패 시 backoff 2초 (retryCount=1 → 1<<1)")
    void shouldApplyDoubledBackoff_whenRetryCountIsOne() throws FirebaseMessagingException {
      // Given: retryCount=1인 이벤트 (1차 실패 이후 상태)
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId);
      event.setRetryCount(1);

      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class)))
          .willThrow(mock(FirebaseMessagingException.class));
      givenClientSpan();

      // When
      Instant before = Instant.now();
      dispatcher.dispatch(event);
      Instant after = Instant.now();

      // Then: retryCount=2, nextRetryAt ≈ now + 2s (1<<1 = 2)
      assertThat(event.getRetryCount()).isEqualTo(2);
      assertThat(event.getNextRetryAt()).isBetween(before.plusSeconds(2), after.plusSeconds(2));
      assertThat(notification.getStatus())
          .isEqualTo(NotificationStatus.PENDING); // retriable → Notification은 PENDING 유지
    }

    @Test
    @DisplayName("TC7 - UNREGISTERED 에러 코드는 retryCount 무관 즉시 FAILED_PERMANENT")
    void shouldMarkAsFailedPermanent_whenFcmErrorIsUnregistered()
        throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId); // retryCount = 0

      FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
      given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.UNREGISTERED);
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willThrow(exception);
      givenClientSpan();

      // When
      dispatcher.dispatch(event);

      // Then: retryCount=0인데도 FAILED_PERMANENT (backoff 없이 즉시 종료)
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_PERMANENT);
      assertThat(event.getRetryCount()).isEqualTo(0);
      assertThat(event.getProcessedAt()).isNotNull();
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(notification.getErrorMessage()).isNotNull();
      then(outboxEventRepository).should().save(event);
    }

    @Test
    @DisplayName("TC8 - INVALID_ARGUMENT 에러 코드는 retryCount 무관 즉시 FAILED_PERMANENT")
    void shouldMarkAsFailedPermanent_whenFcmErrorIsInvalidArgument()
        throws FirebaseMessagingException {
      // Given
      UUID notificationId = UUID.randomUUID();
      Notification notification = createNotification(notificationId, NotificationStatus.PENDING);
      OutboxEvent event = createPendingEvent(notificationId); // retryCount = 0

      FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
      given(exception.getMessagingErrorCode()).willReturn(MessagingErrorCode.INVALID_ARGUMENT);
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));
      given(firebaseMessaging.send(any(Message.class))).willThrow(exception);
      givenClientSpan();

      // When
      dispatcher.dispatch(event);

      // Then
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED_PERMANENT);
      assertThat(event.getRetryCount()).isEqualTo(0);
      assertThat(event.getProcessedAt()).isNotNull();
      assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(notification.getErrorMessage()).isNotNull();
      then(outboxEventRepository).should().save(event);
    }
  }

  private void givenClientSpan() {
    given(span.context()).willReturn(traceContext);
    given(tracer.currentSpan()).willReturn(span);
    given(tracer.spanBuilder()).willReturn(spanBuilder);
    given(spanBuilder.setParent(any())).willReturn(spanBuilder);
    given(spanBuilder.name(any())).willReturn(spanBuilder);
    given(spanBuilder.kind(any())).willReturn(spanBuilder);
    given(spanBuilder.start()).willReturn(span);
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
    return OutboxEvent.createPending(notificationId, OutboxEventType.PUSH_NOTIFICATION, null, null);
  }
}
