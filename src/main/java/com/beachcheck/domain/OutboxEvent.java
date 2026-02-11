package com.beachcheck.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  public void setRetryCount(int i) {
    this.retryCount = i;
  }

  public void setNextRetryAt(Instant customRetryAt) {
    this.nextRetryAt = customRetryAt;
  }

  public enum OutboxEventStatus {
    PENDING, // 처리 대기 중
    SENT, // 전송 완료
    FAILED_RETRIABLE, // 일시 실패 (재시도 대상)
    FAILED_PERMANENT // 영구 실패 (재시도 제외)
  }

  public enum OutboxEventType {
    PUSH_NOTIFICATION // 푸시 알림 이벤트
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long notificationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxEventStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OutboxEventType eventType;

  @Column(columnDefinition = "TEXT")
  private String payload;

  @Column(nullable = false)
  private Integer retryCount = 0;

  @Column private Instant nextRetryAt;

  @Column private Instant processedAt;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    if (nextRetryAt == null) {
      nextRetryAt = createdAt; // 즉시 처리 대상
    }
  }

  // 상태 전이 메서드
  public void markAsSent() {
    this.status = OutboxEventStatus.SENT;
    this.processedAt = Instant.now();
  }

  public void markAsFailedRetriable(Duration nextRetryDelay) {
    this.status = OutboxEventStatus.FAILED_RETRIABLE;
    this.retryCount++;
    this.nextRetryAt = Instant.now().plus(nextRetryDelay);
  }

  public void markAsFailedPermanent() {
    this.status = OutboxEventStatus.FAILED_PERMANENT;
    this.processedAt = Instant.now();
  }

  // Getters and Setters

  public Long getId() {
    return id;
  }

  public Long getNotificationId() {
    return notificationId;
  }

  public void setNotificationId(Long notificationId) {
    this.notificationId = notificationId;
  }

  public OutboxEventStatus getStatus() {
    return status;
  }

  public void setStatus(OutboxEventStatus status) {
    this.status = status;
  }

  public OutboxEventType getEventType() {
    return eventType;
  }

  public void setEventType(OutboxEventType eventType) {
    this.eventType = eventType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public Instant getNextRetryAt() {
    return nextRetryAt;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
