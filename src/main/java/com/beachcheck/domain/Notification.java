package com.beachcheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
