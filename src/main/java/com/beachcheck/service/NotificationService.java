package com.beachcheck.service;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.domain.OutboxEvent.OutboxEventType;
import com.beachcheck.repository.NotificationRepository;
import com.beachcheck.repository.OutboxEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  // TODO [관측 스프린트] JSON 로깅 + MDC로 userId, notificationId 등을 자동 포함시키는 구조 적용

  private final NotificationRepository notificationRepository;
  private final OutboxEventRepository outboxEventRepository;

  public NotificationService(
      NotificationRepository notificationRepository, OutboxEventRepository outboxEventRepository) {
    this.notificationRepository = notificationRepository;
    this.outboxEventRepository = outboxEventRepository;
  }

  @Transactional
  public void createAndSchedule(
      UUID userId,
      Notification.NotificationType type,
      String title,
      String message,
      String fcmToken) {
    // 1. Notification 엔티티 생성 및 저장
    Notification notification = Notification.createPending(userId, type, title, message, fcmToken);
    notificationRepository.save(notification);

    OutboxEvent event =
        OutboxEvent.createPending(notification.getId(), OutboxEventType.PUSH_NOTIFICATION, null);
    outboxEventRepository.save(event);
  }
}
