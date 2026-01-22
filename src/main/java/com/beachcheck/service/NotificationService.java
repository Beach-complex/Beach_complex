package com.beachcheck.service;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import com.beachcheck.repository.NotificationRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.time.Instant;
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
   * FCM 푸시 알림 발송 (비동기)
   *
   * <p>Why: Firebase Cloud Messaging을 통해 사용자에게 실시간 푸시 알림을 전송하기 위함 (메시지 큐 도입 대비)
   *
   * <p>Policy: - 알림 발송은 비동기로 처리되어 메인 스레드를 블로킹하지 않음 - 발송 성공/실패 여부를 DB에 기록하여 추적 가능 - FCM 토큰이 유효하지 않으면
   * FAILED 상태로 기록 - 알림 엔티티는 컨트롤러에서 이미 생성/저장된 상태
   *
   * <p>Contract(Input): - notificationId: NULL 불가, 이미 저장된 알림 ID
   *
   * <p>Contract(Output): - 발송 성공 시 SENT 상태로 DB 업데이트 - 발송 실패 시 FAILED 상태 및 에러 메시지 저장
   */
  @Async("notificationTaskExecutor")
  public void sendPushNotification(UUID notificationId) {

    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

    try {
      // 1. FCM 메시지 구성 및 발송
      Message message = notification.toFcmMessage();
      String response = FirebaseMessaging.getInstance().send(message);

      // 2. 발송 성공 기록
      notification.setStatus(NotificationStatus.SENT);
      notification.setSentAt(Instant.now());
      notificationRepository.save(notification);

      log.info(
          "FCM Delivery success: userId={}, type={}, response={}",
          notification.getUserId(),
          notification.getType(),
          response);

    } catch (FirebaseMessagingException e) {
      // 3. 발송 실패 기록
      notification.setStatus(NotificationStatus.FAILED);
      notification.setErrorMessage(e.getMessage());
      notificationRepository.save(notification);

      log.error(
          "FCM Delivery fail: userId={}, type={}, error={}, errorCode={}",
          notification.getUserId(),
          notification.getType(),
          e.getMessage(),
          e.getErrorCode());
    }
  }
}
