package com.beachcheck.service;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service // 서비스 컴포넌트
public class OutboxPublisher {

  private final OutboxEventRepository outboxEventRepository;
  private final NotificationRepository notificationRepository;
  private final FirebaseMessaging firebaseMessaging;
  private final int batchSize;

  public OutboxPublisher(
      OutboxEventRepository outboxEventRepository,
      NotificationRepository notificationRepository,
      FirebaseMessaging firebaseMessaging,
      @Value("${app.outbox.batch-size:10}") int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.notificationRepository = notificationRepository;
    this.firebaseMessaging = firebaseMessaging;
    this.batchSize = batchSize;
  }

  @Transactional(readOnly = true)
  public void processPendingOutboxEvents() {
    // 폴링 로직
    Instant now = Instant.now();
    List<OutboxEvent> pendingEvents =
        outboxEventRepository.findPendingEvents(now, PageRequest.of(0, batchSize));

    for (OutboxEvent event : pendingEvents) {
      dispatchAndUpdateOutboxEvent(event);
    }
  }

  /**
   * OutboxEvent를 FCM으로 발송하고 상태를 업데이트
   *
   * <p>Why: 각 이벤트마다 별도 트랜잭션으로 격리하여 FCM 전송 시 커넥션 풀을 오래 잡지 않도록 함
   *
   * <p>Policy: REQUIRES_NEW로 독립적인 트랜잭션 생성 - 실패한 이벤트가 다른 이벤트에 영향 없음
   *
   * <p>TODO(관측 스프린트 후): 처리량 증가가 필요한 경우 @Async 비동기 처리 고려
   *
   * <ul>
   *   <li>현재는 배치 10개, 1초 간격이라 동기 처리로 충분
   *   <li>관측 스프린트에서 성능 병목 측정 후 비동기 도입 검토
   * </ul>
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  protected void dispatchAndUpdateOutboxEvent(
      OutboxEvent event) { // Spring AOP 프록시 적용을 위해 protected로 변경
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
      // TODO: 예외 처리는 Refactor 단계 또는 PR#3(재시도 로직)에서 구현
      throw new RuntimeException("FCM 전송 실패", e);
    }
  }
}
