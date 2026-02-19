package com.beachcheck.service;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import com.beachcheck.repository.NotificationRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  // TODO [관측 스프린트] JSON 로깅 + MDC로 userId, notificationId 등을 자동 포함시키는 구조 적용

  private final NotificationRepository notificationRepository;
  private final NotificationStatusWriter statusWriter;
  private final FirebaseMessaging firebaseMessaging;

  public NotificationService(
      NotificationRepository notificationRepository,
      NotificationStatusWriter statusWriter,
      FirebaseMessaging firebaseMessaging) {
    this.notificationRepository = notificationRepository;
    this.statusWriter = statusWriter;
    this.firebaseMessaging = firebaseMessaging;
  }

  /**
   * FCM 푸시 알림 발송 (비동기)
   *
   * <p>Why: Firebase Cloud Messaging을 통해 사용자에게 실시간 푸시 알림을 전송하기 위함. FCM 전송과 DB 상태 업데이트를 분리하여 독립
   * 트랜잭션으로 처리함으로써 전송 성공/실패를 정확히 기록.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>비동기 처리로 메인 스레드 블로킹 방지
   *   <li>Idempotency 보장: 이미 처리된 알림은 재전송 방지
   *   <li>FCM 전송과 DB 상태 업데이트를 독립 트랜잭션으로 분리 (Propagation.REQUIRES_NEW)
   *   <li>FCM 전송 성공 후 DB 업데이트 실패 시, FAILED로 덮어쓰지 않음 (운영 알람 대상)
   * </ul>
   *
   * <p>Contract(Input):
   *
   * <ul>
   *   <li>notificationId: NULL 불가, 이미 저장된 알림 ID
   *   <li>알림 엔티티는 컨트롤러에서 생성/저장된 상태여야 함
   * </ul>
   *
   * <p>Contract(Output):
   *
   * <ul>
   *   <li>조회 실패 시: 로그만 남기고 종료 (FAILED 기록 불가)
   *   <li>이미 처리된 알림: 스킵
   *   <li>FCM 전송 성공: SENT 상태로 DB 업데이트 (독립 트랜잭션)
   *   <li>FCM 전송 실패: FAILED 상태 및 에러 메시지 저장 (독립 트랜잭션)
   * </ul>
   */
  @Async("notificationTaskExecutor")
  public void sendPushNotification(UUID notificationId) {
    Notification notification;
    try {
      notification = findNotification(notificationId);
    } catch (Exception e) {
      // 조회 실패는 FAILED로 남길 엔티티 자체가 없으므로 로그만
      log.warn("알림을 찾을 수 없습니다. 발송 건너뜀. notificationId={}", notificationId, e);
      return;
    }

    // 멱등성 보장: 이미 처리된 건 스킵
    if (notification.getStatus() == NotificationStatus.SENT
        || notification.getStatus() == NotificationStatus.FAILED) {
      log.info(
          "이미 처리된 알림입니다. 발송 건너뜀. notificationId={}, status={}",
          notificationId,
          notification.getStatus());
      return;
    }

    // FCM 전송은 트랜잭션 밖에서 수행
    final String response;
    try {
      response = sendToFcm(notification);
    } catch (Exception sendEx) {
      // 전송 실패만 FAILED로 기록 (독립 트랜잭션)
      statusWriter.markAsFailure(notificationId, sendEx);
      return;
    }

    // 3) 전송 성공은 SENT로 기록 (독립 트랜잭션)
    // 여기서 DB 실패가 나도 "전송 실패"로 오판해서 FAILED로 덮어쓰면 안 됨
    try {
      statusWriter.markAsSuccess(notificationId, response);
    } catch (Exception dbEx) {
      log.error(
          "FCM 발송됨 그러나 DB 업데이트 실패. notificationId={}, response={}", notificationId, response, dbEx);
      // 운영적으로는 별도 알람/보정 대상. 여기서 FAILED로 바꾸지 않는다.
    }
  }

  /**
   * Notification 엔티티 조회
   *
   * <p>Why: 발송할 알림 정보를 DB에서 조회하기 위함.
   *
   * <p>Contract(Input):
   *
   * <ul>
   *   <li>notificationId: NULL 불가
   * </ul>
   *
   * <p>Contract(Output):
   *
   * <ul>
   *   <li>알림 엔티티 반환 (findById에 @Transactional(readOnly = true) 적용됨)
   *   <li>존재하지 않으면 IllegalArgumentException 발생
   * </ul>
   */
  protected Notification findNotification(UUID notificationId) {
    return notificationRepository
        .findById(notificationId) // findById 안에 @Transactional(readOnly = true) 적용됨
        .orElseThrow(() -> new IllegalArgumentException("Notification을 찾을 수 없습니다."));
  }

  /**
   * FCM 메시지 전송
   *
   * <p>Why: Firebase Cloud Messaging 서버로 실제 푸시 알림 발송.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>동기 호출 (외부 I/O, 네트워크 지연 발생 가능)
   *   <li>FCM 토큰이 유효하지 않거나 네트워크 실패 시 FirebaseMessagingException 발생
   * </ul>
   *
   * <p>Contract(Input):
   *
   * <ul>
   *   <li>notification: NULL 불가, toFcmMessage() 가능한 엔티티
   * </ul>
   *
   * <p>Contract(Output):
   *
   * <ul>
   *   <li>FCM 서버 응답 메시지 ID 반환
   *   <li>실패 시 FirebaseMessagingException throw
   * </ul>
   */
  private String sendToFcm(Notification notification) throws FirebaseMessagingException {
    Message message = notification.toFcmMessage();
    return firebaseMessaging.send(message);
  }
}
