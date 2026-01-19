package com.beachcheck.service;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import com.beachcheck.domain.Notification.NotificationType;
import com.beachcheck.dto.notification.NotificationResponseDto;
import com.beachcheck.repository.NotificationRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  /**
   * FCM 푸시 알림 발송
   *
   * <p>Why: Firebase Cloud Messaging을 통해 사용자에게 실시간 푸시 알림을 전송하기 위함.
   *
   * <p>Policy: - 알림 발송은 비동기로 처리되어 메인 스레드를 블로킹하지 않음 - 발송 성공/실패 여부를 DB에 기록하여 추적 가능 - FCM 토큰이 유효하지 않으면
   * FAILED 상태로 기록
   *
   * <p>Contract(Input): - userId: NULL 불가 - title: NULL 불가, 최대 500자 - body: NULL 불가, 최대 1000자 -
   * fcmToken: NULL 불가, 유효한 FCM 토큰 - type: NULL 불가, NotificationType Enum 값
   *
   * <p>Contract(Output): - 발송 성공 시 SENT 상태로 DB 저장 - 발송 실패 시 FAILED 상태 및 에러 메시지 저장
   */
  @Async("notificationTaskExecutor")
  public void sendPushNotification(
      UUID userId, String title, String body, String fcmToken, NotificationType type) {

    // 1. 알림 엔티티 생성 및 저장 (PENDING 상태)
    Notification notification = Notification.createPending(userId, type, title, body, fcmToken);
    notification = notificationRepository.save(notification); // ID 생성 위해 저장

    try {
      // 2. FCM 메시지 구성 및 발송
      Message message = notification.toFcmMessage();
      String response = FirebaseMessaging.getInstance().send(message);

      // 3. 발송 성공 기록
      notification.setStatus(NotificationStatus.SENT);
      notification.setSentAt(Instant.now());
      notificationRepository.save(notification);

      log.info("FCM Delivery success: userId={}, type={}, response={}", userId, type, response);

    } catch (FirebaseMessagingException e) {
      // 4. 발송 실패 기록
      notification.setStatus(NotificationStatus.FAILED);
      notification.setErrorMessage(e.getMessage());
      notificationRepository.save(notification);

      log.error(
          "FCM Delivery fail: userId={}, type={}, error={}, errorCode={}",
          userId,
          type,
          e.getMessage(),
          e.getErrorCode());
    }
  }
}
