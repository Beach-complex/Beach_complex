package com.beachcheck.outbox.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import com.beachcheck.notification.domain.Notification;
import com.beachcheck.notification.domain.Notification.NotificationStatus;
import com.beachcheck.notification.repository.NotificationRepository;
import com.beachcheck.outbox.domain.OutboxEvent;
import com.beachcheck.outbox.repository.OutboxEventRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Why: OutboxPublisher의 self-invocation 문제로 @Transactional(REQUIRES_NEW)가 프록시를 우회하는 것을 방지하기 위해 별도
 * 빈으로 분리. Spring AOP는 외부 빈 호출에서만 프록시가 적용됨. Policy: REQUIRES_NEW로 이벤트별 독립 트랜잭션 보장 - 한 이벤트 실패가 다른
 * 이벤트에 영향 없음 TODO(관측 스프린트 후): 처리량 증가가 필요한 경우 @Async 비동기 처리 고려 - 현재는 배치 10개, 1초 간격이라 동기 처리로 충분 - 관측
 * 스프린트에서 성능 병목 측정 후 비동기 도입 검토
 */
public class OutboxEventDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxEventDispatcher.class);

  private final OutboxEventRepository outboxEventRepository;
  private final NotificationRepository notificationRepository;
  private final FirebaseMessaging firebaseMessaging;
  private final Tracer tracer;

  public OutboxEventDispatcher(
      OutboxEventRepository outboxEventRepository,
      NotificationRepository notificationRepository,
      FirebaseMessaging firebaseMessaging,
      Tracer tracer) {
    this.outboxEventRepository = outboxEventRepository;
    this.notificationRepository = notificationRepository;
    this.firebaseMessaging = firebaseMessaging;
    this.tracer = tracer;
  }

  /**
   * Why: 각 이벤트마다 별도 트랜잭션으로 격리하여 FCM 전송 시 커넥션 풀을 오래 잡지 않도록 함 Policy: REQUIRES_NEW - OutboxPublisher의
   * readOnly 트랜잭션과 분리된 독립 트랜잭션으로 실행 Contract(Input): OutboxEvent - PENDING 또는 재시도 대상 이벤트
   * Contract(Output): 성공 시 SENT, 재시도 가능 실패 시 FAILED_RETRIABLE, 영구 실패 시 FAILED_PERMANENT
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void dispatch(OutboxEvent event) {
    Span span = startSpan("outbox dispatch");
    AtomicBoolean outcomeRecorded = new AtomicBoolean(false);
    boolean transactionSynchronizationActive =
        TransactionSynchronizationManager.isSynchronizationActive();
    if (transactionSynchronizationActive) {
      registerCompletionCallback(span, outcomeRecorded, event);
    }
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      dispatchEvent(event, span, outcomeRecorded);
    } catch (RuntimeException | Error exception) {
      outcomeRecorded.set(true);
      span.tag("outbox.item.outcome", "error");
      span.tag("outbox.failure.class", exception.getClass().getName());
      span.error(new IllegalStateException("Outbox dispatch 실패"));
      throw exception;
    } finally {
      if (!transactionSynchronizationActive) {
        span.end();
      }
    }
  }

  private void dispatchEvent(OutboxEvent event, Span dispatchSpan, AtomicBoolean outcomeRecorded) {
    // 1. Notification 조회
    Notification notification =
        traceChild(
            "outbox notification lookup",
            () ->
                notificationRepository
                    .findById(event.getNotificationId())
                    .orElseThrow(() -> new IllegalArgumentException("Notification을 찾을 수 없습니다")));

    // 2. 멱등성: 이미 SENT 상태면 OutboxEvent만 SENT로 전이하고 스킵
    if (notification.getStatus() == NotificationStatus.SENT) {
      traceStateUpdate(
          () -> {
            event.markAsSent();
            outboxEventRepository.save(event);
          });
      recordAfterCommit(
          () -> {
            outcomeRecorded.set(true);
            dispatchSpan.tag("outbox.item.outcome", "skipped");
            dispatchSpan.tag("outbox.item.skip.reason", "already_sent");
            log.info(
                "Outbox 이벤트 멱등 스킵 (이미 발송됨)",
                kv("outboxEventId", event.getId()),
                kv("notificationId", event.getNotificationId()),
                kv("outboxEventType", event.getEventType()));
          });
      return;
    }

    // 3. FCM 전송
    try {
      Message message = notification.toFcmMessage();
      sendFcm(message, event);

      traceStateUpdate(
          () -> {
            // 4. Notification 상태 업데이트
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);

            // 5. OutboxEvent 상태 업데이트
            event.markAsSent();
            outboxEventRepository.save(event);
          });
      recordAfterCommit(
          () -> {
            outcomeRecorded.set(true);
            dispatchSpan.tag("outbox.item.outcome", "success");
            log.info(
                "Outbox 이벤트 발송 성공",
                kv("outboxEventId", event.getId()),
                kv("notificationId", event.getNotificationId()),
                kv("outboxEventType", event.getEventType()),
                kv("retryCount", event.getRetryCount()));
          });
    } catch (FirebaseMessagingException e) {
      // Exponential Backoff 재시도 로직
      if (isPermanentFcmError(e)) {
        traceStateUpdate(
            () -> {
              event.markAsFailedPermanent();
              notification.markAsFailed("errorCode: " + e.getMessagingErrorCode());
              notificationRepository.save(notification);
              outboxEventRepository.save(event);
            });
        tagFailureOutcome(
            dispatchSpan, outcomeRecorded, "permanent_failure", event.getRetryCount());
        log.warn(
            "Outbox 이벤트 발송 영구 실패",
            kv("outboxEventId", event.getId()),
            kv("notificationId", event.getNotificationId()),
            kv("messagingErrorCode", e.getMessagingErrorCode()));
        return;
      }
      Duration backoff = Duration.ofSeconds(1L << event.getRetryCount()); // 1s, 2s, 4s

      if (event.getRetryCount() >= 3) { // 최대 재시도 횟수 초과 시 영구 실패로 전이
        traceStateUpdate(
            () -> {
              event.markAsFailedPermanent();
              notification.markAsFailed("errorCode: " + e.getMessagingErrorCode());
              notificationRepository.save(notification);
              outboxEventRepository.save(event);
            });
        tagFailureOutcome(
            dispatchSpan, outcomeRecorded, "permanent_failure", event.getRetryCount());
        log.warn(
            "Outbox 이벤트 발송 영구 실패 (최대 재시도 초과)",
            kv("outboxEventId", event.getId()),
            kv("notificationId", event.getNotificationId()),
            kv("retryCount", event.getRetryCount()),
            kv("messagingErrorCode", e.getMessagingErrorCode()));
      } else {
        traceStateUpdate(
            () -> {
              event.markAsFailedRetriable(backoff);
              outboxEventRepository.save(event);
            });
        tagFailureOutcome(
            dispatchSpan, outcomeRecorded, "retryable_failure", event.getRetryCount());
        log.warn(
            "Outbox 이벤트 발송 실패 (재시도 예정)",
            kv("outboxEventId", event.getId()),
            kv("notificationId", event.getNotificationId()),
            kv("retryCount", event.getRetryCount()),
            kv("messagingErrorCode", e.getMessagingErrorCode()));
      }
    }
  }

  private void sendFcm(Message message, OutboxEvent event) throws FirebaseMessagingException {
    Span span = startClientSpan("outbox fcm send");
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      firebaseMessaging.send(message);
      span.tag("outbox.fcm.outcome", "success");
    } catch (FirebaseMessagingException exception) {
      String outcome =
          isPermanentFcmError(exception) || event.getRetryCount() >= 3
              ? "permanent_failure"
              : "retryable_failure";
      span.tag("outbox.fcm.outcome", outcome);
      span.error(new IllegalStateException("FCM 전송 실패"));
      throw exception;
    } finally {
      span.end();
    }
  }

  private void traceStateUpdate(Runnable action) {
    traceChild(
        "outbox state update",
        () -> {
          action.run();
          return null;
        });
  }

  private <T> T traceChild(String name, Supplier<T> action) {
    Span span = startSpan(name);
    try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
      return action.get();
    } finally {
      span.end();
    }
  }

  private Span startSpan(String name) {
    return tracer.nextSpan().name(name).start();
  }

  private Span startClientSpan(String name) {
    return tracer
        .spanBuilder()
        .setParent(tracer.currentSpan().context())
        .name(name)
        .kind(Span.Kind.CLIENT)
        .start();
  }

  private void tagFailureOutcome(
      Span span, AtomicBoolean outcomeRecorded, String outcome, int retryCount) {
    outcomeRecorded.set(true);
    span.tag("outbox.item.outcome", outcome);
    span.tag("outbox.retry.count", retryCount);
  }

  private void recordAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
      return;
    }
    action.run();
  }

  private void registerCompletionCallback(
      Span span, AtomicBoolean outcomeRecorded, OutboxEvent event) {
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != TransactionSynchronization.STATUS_COMMITTED
                && outcomeRecorded.compareAndSet(false, true)) {
              span.tag("outbox.item.outcome", "error");
              span.error(new IllegalStateException("Outbox dispatch 커밋 실패"));
              log.warn(
                  "Outbox 이벤트 상태 저장 커밋 실패",
                  kv("outboxEventId", event.getId()),
                  kv("notificationId", event.getNotificationId()));
            }
            span.end();
          }
        });
  }

  private boolean isPermanentFcmError(FirebaseMessagingException e) {
    MessagingErrorCode errorCode = e.getMessagingErrorCode();
    return errorCode == MessagingErrorCode.UNREGISTERED
        || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
  }
}
