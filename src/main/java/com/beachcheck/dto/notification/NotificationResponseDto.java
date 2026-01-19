package com.beachcheck.dto.notification;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import com.beachcheck.domain.Notification.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * 알림 응답 DTO
 *
 * <p>Why: 프론트엔드에게 알림 정보를 전달하기 위한 DTO
 *
 * @param id 알림 ID
 * @param type 알림 유형
 * @param title 알림 제목
 * @param message 알림 내용
 * @param status 알림 상태
 * @param sentAt 발송 시각
 * @param createdAt 생성 시각
 */
public record NotificationResponseDto(
    UUID id,
    NotificationType type,
    String title,
    String message,
    NotificationStatus status,
    Instant sentAt,
    Instant createdAt) {

  public static NotificationResponseDto from(Notification notification) {
    return new NotificationResponseDto(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getMessage(),
        notification.getStatus(),
        notification.getSentAt(),
        notification.getCreatedAt());
  }
}
