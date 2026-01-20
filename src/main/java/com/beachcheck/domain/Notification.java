package com.beachcheck.domain;

import com.google.firebase.messaging.Message;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationType type; // PEAK_AVOID, DATE_REMINDER

  @Column(nullable = false, length = 500)
  private String title;

  @Column(nullable = false, length = 1000)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private NotificationStatus status; // PENDING, SENT, FAILED

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "error_message", length = 500)
  private String errorMessage;

  // 실제 알림 수신자 식별 정보(FCM 토큰 또는 이메일 주소 등)
  @Column(name = "recipient_token", length = 500)
  private String recipientToken;

  public enum NotificationType {
    PEAK_AVOID, // 피크 타임 회피 알림
    DATE_REMINDER, // 지정 날짜 알림
    FAVORITE_UPDATE, // 찜한 해변 정보 변경
    WEATHER_ALERT // 기상 특보
  }

  public enum NotificationStatus {
    PENDING, // 발송 대기
    SENT, // 발송 완료
    FAILED // 발송 실패
  }

  /**
   * PENDING 상태의 알림 생성
   *
   * <p>Why: 알림 발송 시도 전 PENDING 상태로 DB에 먼저 기록하여 발송 이력 추적 및 장애 복구를 가능하게 함
   *
   * <p>Policy: - 생성 시점에는 항상 PENDING 상태 - createdAt은 현재 시각으로 자동 설정 - 발송 후 SENT 또는 FAILED로 상태 전환
   *
   * @param userId 사용자 ID
   * @param type 알림 유형
   * @param title 알림 제목
   * @param message 알림 내용
   * @param recipientToken FCM 토큰 또는 수신자 식별 정보
   * @return PENDING 상태의 Notification 인스턴스
   */
  public static Notification createPending(
      UUID userId, NotificationType type, String title, String message, String recipientToken) {
    Notification notification = new Notification();
    notification.setUserId(userId);
    notification.setType(type);
    notification.setTitle(title);
    notification.setMessage(message);
    notification.setRecipientToken(recipientToken);
    notification.setStatus(NotificationStatus.PENDING);
    notification.setCreatedAt(Instant.now());
    return notification;
  }

  /**
   * FCM Message 객체로 변환
   *
   * <p>Why: FCM 발송을 위한 Message 객체 생성 로직을 도메인에 캡슐화하여 서비스 계층의 책임 분리
   *
   * <p>Policy: - FCM Notification에는 title, body만 포함 - 추가 데이터는 data 필드에 type, userId, timestamp 포함
   *
   * @return FCM Message 객체
   */
  public Message toFcmMessage() {
    return Message.builder()
        .setToken(this.recipientToken)
        .setNotification(
            com.google.firebase.messaging.Notification.builder()
                .setTitle(this.title)
                .setBody(this.message)
                .build())
        // 백그라운드 데이터 처리를 위한 추가 데이터 필드(사용자에게는 보이지 않음, 클라이언트 앱에서 활용 가능)
        .putData("type", this.type.name())
        .putData("userId", this.userId.toString())
        .putData("timestamp", Instant.now().toString())
        .build();
  }

  // Getters and Setters
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public NotificationType getType() {
    return type;
  }

  public void setType(NotificationType type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getRecipientToken() {
    return recipientToken;
  }

  public void setRecipientToken(String recipientToken) {
    this.recipientToken = recipientToken;
  }
}
