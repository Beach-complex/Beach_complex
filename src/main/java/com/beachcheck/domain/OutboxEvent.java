package com.beachcheck.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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
  private UUID notificationId;

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

  /**
   * PENDING 상태의 OutboxEvent 생성
   *
   * <p>Why: OutboxEvent 생성 로직을 도메인에 캡슐화하여 일관된 초기 상태 보장
   *
   * <p>Policy:
   * <ul>
   *   <li>생성 시점에는 항상 PENDING 상태</li>
   *   <li>retryCount는 0으로 초기화</li>
   *   <li>nextRetryAt은 @PrePersist에서 현재 시각으로 설정 (즉시 처리)</li>
   * </ul>
   *
   * <p>Contract(Input): notificationId, eventType은 NULL 불가. payload는 NULL 가능.
   *
   * <p>Contract(Output): status=PENDING, retryCount=0인 OutboxEvent 인스턴스
   */
  public static OutboxEvent createPending(
      UUID notificationId, OutboxEventType eventType, String payload) {
    OutboxEvent event = new OutboxEvent();
    event.setNotificationId(notificationId);
    event.setStatus(OutboxEventStatus.PENDING);
    event.setEventType(eventType);
    event.setPayload(payload);
    event.setRetryCount(0);
    // nextRetryAt, createdAt은 @PrePersist에서 설정
    return event;
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

  public UUID getNotificationId() {
    return notificationId;
  }

  public void setNotificationId(UUID notificationId) {
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
