package com.beachcheck.service;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Why: OutboxPublisher의 self-invocation 문제로 @Transactional(REQUIRES_NEW)가 프록시를 우회하는 것을 방지하기 위해 별도
 * 빈으로 분리. Spring AOP는 외부 빈 호출에서만 프록시가 적용됨. Policy: REQUIRES_NEW로 이벤트별 독립 트랜잭션 보장 - 한 이벤트 실패가 다른
 * 이벤트에 영향 없음 TODO(관측 스프린트 후): 처리량 증가가 필요한 경우 @Async 비동기 처리 고려 - 현재는 배치 10개, 1초 간격이라 동기 처리로 충분 - 관측
 * 스프린트에서 성능 병목 측정 후 비동기 도입 검토
 */
@Service
public class OutboxEventDispatcher {

  private final OutboxEventRepository outboxEventRepository;
  private final NotificationRepository notificationRepository;
  private final FirebaseMessaging firebaseMessaging;

  public OutboxEventDispatcher(
      OutboxEventRepository outboxEventRepository,
      NotificationRepository notificationRepository,
      FirebaseMessaging firebaseMessaging) {
    this.outboxEventRepository = outboxEventRepository;
    this.notificationRepository = notificationRepository;
    this.firebaseMessaging = firebaseMessaging;
  }

  /**
   * Why: 각 이벤트마다 별도 트랜잭션으로 격리하여 FCM 전송 시 커넥션 풀을 오래 잡지 않도록 함 Policy: REQUIRES_NEW - OutboxPublisher의
   * readOnly 트랜잭션과 분리된 독립 트랜잭션으로 실행 Contract(Input): OutboxEvent - PENDING 또는 재시도 대상 이벤트
   * Contract(Output): 성공 시 SENT, 재시도 가능 실패 시 FAILED_RETRIABLE, 영구 실패 시 FAILED_PERMANENT
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void dispatch(OutboxEvent event) {
    // 1. Notification 조회
    Notification notification =
        notificationRepository
            .findById(event.getNotificationId())
            .orElseThrow(() -> new IllegalArgumentException("Notification을 찾을 수 없습니다"));

    // 2. 멱등성: 이미 SENT 상태면 OutboxEvent만 SENT로 전이하고 스킵
    if (notification.getStatus() == NotificationStatus.SENT) {
      event.markAsSent();
      outboxEventRepository.save(event);
      return;
    }

    // 3. FCM 전송
    try {
      Message message = notification.toFcmMessage();
      firebaseMessaging.send(message);

      // 4. Notification 상태 업데이트
      notification.setStatus(NotificationStatus.SENT);
      notification.setSentAt(Instant.now());
      notificationRepository.save(notification);

      // 5. OutboxEvent 상태 업데이트
      event.markAsSent();
      outboxEventRepository.save(event);
    } catch (FirebaseMessagingException e) {
      // Exponential Backoff 재시도 로직
      // TODO(PR#4): FirebaseMessagingException errorCode 기반 영구 실패 분류 도입
      // (예: UNREGISTERED, INVALID_ARGUMENT 등은 PENDING -> FAILED_PERMANENT 직행)
      // TODO(PR#5): FAILED_PERMANENT 전이 시 Notification.status(FAILED) 동기화 정책 확정 후 반영
      Duration backoff = Duration.ofSeconds(1L << event.getRetryCount()); // 1s, 2s, 4s

      if (event.getRetryCount() >= 3) { // 최대 재시도 횟수 초과 시 영구 실패로 전이
        event.markAsFailedPermanent();
        outboxEventRepository.save(event);
      } else {
        event.markAsFailedRetriable(backoff);
        outboxEventRepository.save(event);
      }
    }
  }
}
