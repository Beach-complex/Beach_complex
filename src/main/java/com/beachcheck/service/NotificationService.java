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

  /**
   * 혼잡도 알림 발송 (편의 메서드)
   *
   * <p>Why: 해변 혼잡도가 높을 때 찜한 사용자에게 알림을 보내기 위한 특화된 메서드
   *
   * @param userId 사용자 ID
   * @param beachName 해변 이름
   * @param congestion 혼잡도 (%)
   * @param fcmToken FCM 토큰
   */
  public void sendCongestionAlert(UUID userId, String beachName, int congestion, String fcmToken) {
    String title = "해변 혼잡 알림";
    String body =
        String.format(
            "%s이(가) 현재 혼잡합니다 (혼잡도: %d%%). 다른 시간대를 추천드립니다.", beachName, congestion); // % 문자 이스케이프 처리

    sendPushNotification(userId, title, body, fcmToken, NotificationType.PEAK_AVOID);
  }

  /**
   * 날짜 알림 발송 (편의 메서드)
   *
   * <p>Why: 사용자가 설정한 날짜에 알림을 보내기 위한 특화된 메서드
   *
   * @param userId 사용자 ID
   * @param beachName 해변 이름
   * @param date 방문 날짜
   * @param fcmToken FCM 토큰
   */
  public void sendDateReminder(UUID userId, String beachName, String date, String fcmToken) {
    String title = "방문 날짜 알림";
    String body = String.format("내일은 %s 방문 예정일입니다. 날씨 정보를 확인하세요!", beachName);

    sendPushNotification(userId, title, body, fcmToken, NotificationType.DATE_REMINDER);
  }

  /**
   * 찜한 해변 정보 변경 알림 (편의 메서드)
   *
   * <p>Why: 찜한 해변의 운영 정보가 변경되었을 때 사용자에게 알림
   *
   * @param userId 사용자 ID
   * @param beachName 해변 이름
   * @param changeInfo 변경 정보
   * @param fcmToken FCM 토큰
   */
  public void sendFavoriteUpdateAlert(
      UUID userId, String beachName, String changeInfo, String fcmToken) {
    String title = "찜한 해변 정보 업데이트";
    String body = String.format("%s의 %s 정보가 업데이트되었어요. 확인해보세요!", beachName, changeInfo);

    sendPushNotification(userId, title, body, fcmToken, NotificationType.FAVORITE_UPDATE);
  }

  /**
   * 기상 특보 알림 (편의 메서드)
   *
   * <p>Why: 찜한 해변 지역에 기상 특보가 발효되었을 때 사용자에게 경고
   *
   * @param userId 사용자 ID
   * @param beachName 해변 이름
   * @param weatherAlert 기상 특보 내용
   * @param fcmToken FCM 토큰
   */
  public void sendWeatherAlert(
      UUID userId, String beachName, String weatherAlert, String fcmToken) {
    String title = "기상 특보 알림";
    String body = String.format("%s 지역 %s 발효. 방문에 주의하세요.", beachName, weatherAlert);

    sendPushNotification(userId, title, body, fcmToken, NotificationType.WEATHER_ALERT);
  }

  /**
   * 사용자의 알림 이력 조회
   *
   * @param userId 사용자 ID
   * @return 알림 DTO 리스트
   */
  @Transactional(readOnly = true)
  public List<NotificationResponseDto> getUserNotifications(UUID userId) {
    return notificationRepository.findByUserId(userId).stream()
        .map(NotificationResponseDto::from)
        .toList();
  }

  /**
   * 실패한 알림 재발송 (관리자용)
   *
   * <p>Why: FCM 토큰 만료 등의 이유로 실패한 알림을 재발송하기 위함
   *
   * @param notificationId 재발송할 알림 ID
   */
  public void retryFailedNotification(UUID notificationId) {
    Notification notification =
        notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

    if (notification.getStatus() != NotificationStatus.FAILED) {
      throw new IllegalStateException("Only FAILED notifications can be retried");
    }

    sendPushNotification(
        notification.getUserId(),
        notification.getTitle(),
        notification.getMessage(),
        notification.getRecipientToken(),
        notification.getType());
  }
}
