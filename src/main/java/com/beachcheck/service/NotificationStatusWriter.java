package com.beachcheck.service;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import com.beachcheck.repository.NotificationRepository;
import com.google.firebase.messaging.FirebaseMessagingException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationStatusWriter {

  private static final Logger log = LoggerFactory.getLogger(NotificationStatusWriter.class);

  private final NotificationRepository notificationRepository;

  public NotificationStatusWriter(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
  }

  /**
   * FCM 발송 성공 상태 기록 (독립 트랜잭션)
   *
   * <p>Why: FCM 전송 성공을 DB에 기록하여 발송 이력 추적 및 중복 전송 방지. 독립 트랜잭션으로 처리하여 외부 트랜잭션 상태와 무관하게 커밋 보장.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>Propagation.REQUIRES_NEW로 항상 새로운 트랜잭션 시작
   *   <li>SENT 상태, sentAt 타임스탬프, errorMessage 초기화
   *   <li>FCM response는 200자로 truncate하여 로그 기록
   * </ul>
   *
   * <p>Contract(Input):
   *
   * <ul>
   *   <li>notificationId: NULL 불가, 존재하는 알림 ID
   *   <li>response: FCM 발송 응답 메시지
   * </ul>
   *
   * <p>Contract(Output):
   *
   * <ul>
   *   <li>알림 상태를 SENT로 업데이트하고 발송 시간 기록
   *   <li>성공 로그 출력 (userId, type, response)
   * </ul>
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markAsSuccess(UUID notificationId, String response) {
    Notification notification = notificationRepository.findById(notificationId).orElseThrow();

    notification.setStatus(NotificationStatus.SENT);
    notification.setSentAt(Instant.now());
    notification.setErrorMessage(null);

    // managed 엔티티면 더티체킹때문에 save 없어도 되지만, 명시적으로 유지하려면 OK
    notificationRepository.save(notification);

    log.info(
        "FCM 발송 성공: userId={}, type={}, response={}",
        notification.getUserId(),
        notification.getType(),
        truncate(response, 200));
  }

  /**
   * FCM 발송 실패 상태 기록 (독립 트랜잭션)
   *
   * <p>Why: FCM 전송 실패를 DB에 기록하여 재시도 대상 식별 및 장애 분석. 독립 트랜잭션으로 처리하여 외부 트랜잭션 상태와 무관하게 커밋 보장.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>Propagation.REQUIRES_NEW로 항상 새로운 트랜잭션 시작
   *   <li>FAILED 상태 및 에러 메시지(500자 제한) 저장
   *   <li>FirebaseMessagingException의 경우 errorCode도 로그에 기록
   *   <li>알림 엔티티가 없을 경우 로그만 남기고 종료
   * </ul>
   *
   * <p>Contract(Input):
   *
   * <ul>
   *   <li>notificationId: NULL 불가
   *   <li>e: 발생한 예외 (FirebaseMessagingException 또는 기타 Exception)
   * </ul>
   *
   * <p>Contract(Output):
   *
   * <ul>
   *   <li>알림 상태를 FAILED로 업데이트하고 에러 메시지 저장
   *   <li>실패 로그 출력 (userId, type, errorCode, 스택 트레이스)
   * </ul>
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW) // 알림 저장이 안되었어도 저장 실패 기록 저장은 해야 하므로
  public void markAsFailure(UUID notificationId, Exception e) {
    Notification notification = notificationRepository.findById(notificationId).orElse(null);
    if (notification == null) {
      log.warn("실패 상태 기록 중 알림을 찾을 수 없음. notificationId={}", notificationId, e);
      return;
    }

    notification.setStatus(NotificationStatus.FAILED);
    notification.setErrorMessage(truncate(e.getMessage(), 500));
    // managed 엔티티면 더티체킹때문에 save 없어도 되지만, 명시적으로 유지하려면 OK
    notificationRepository.save(notification);

    // FirebaseMessagingException도 스택트레이스 남겨야 운영에서 원인 분석 가능
    if (e instanceof FirebaseMessagingException fme) {
      log.error(
          "FCM 발송 실패: userId={}, type={}, errorCode={}",
          notification.getUserId(),
          notification.getType(),
          fme.getErrorCode(),
          fme);
    } else {
      log.error(
          "FCM 발송 실패: userId={}, type={}", notification.getUserId(), notification.getType(), e);
    }
  }

  /**
   * 문자열 길이 제한 유틸리티
   *
   * <p>Why: DB 컬럼 길이 제한(VARCHAR)으로 인한 저장 실패 방지. 에러 메시지나 FCM 응답이 과도하게 길 경우 truncate.
   *
   * <p>Policy:
   *
   * <ul>
   *   <li>null은 null 반환
   *   <li>maxLength 이하는 원본 반환
   *   <li>초과 시 앞부분만 잘라서 반환
   * </ul>
   */
  private String truncate(String message, int maxLength) {
    if (message == null) return null;
    return message.length() > maxLength ? message.substring(0, maxLength) : message;
  }
}
