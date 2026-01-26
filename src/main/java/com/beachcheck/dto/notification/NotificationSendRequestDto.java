package com.beachcheck.dto.notification;

import com.beachcheck.domain.Notification.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 알림 발송 요청 DTO
 *
 * <p>Why: 관리자가 수동으로 알림을 발송할 때 사용
 *
 * @param userId 사용자 ID
 * @param type 알림 유형
 * @param title 알림 제목
 * @param message 알림 내용
 */
public record NotificationSendRequestDto(
    @NotNull(message = "사용자 ID는 필수입니다.") UUID userId,
    @NotNull(message = "알림 유형은 필수입니다.") NotificationType type,
    @NotBlank(message = "알림 제목은 필수입니다.") @Size(max = 500, message = "제목은 최대 500자입니다.") String title,
    @NotBlank(message = "알림 내용은 필수입니다.") @Size(max = 1000, message = "내용은 최대 1000자입니다.")
        String message) {}
