package com.beachcheck.dto.reservation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
/**
 * Why: 예약 생성 입력을 검증 가능한 형태로 고정해 경계에서 오류를 조기에 차단하기 위해.
 * Policy: 입력 DTO는 Bean Validation 제약을 통해 유효성 규칙을 선언한다.
 * Contract(Input): reservedAtUtc는 UTC ISO-8601(Z) 형식을 만족한다.
 * Contract(Output): eventId는 128자 이하면 검증을 통과한다.
 */
public record ReservationCreateRequest(
        @Pattern(                                                                                               
        regexp = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z$",
        message = "reservedAtUtc must be ISO-8601 UTC (e.g. 2025-10-24T12:00:00Z)"
        )
        @NotBlank(message = "reservedAtUtc is required")
        String reservedAtUtc,

        @Size(max = 128, message = "eventId must be at most 128 characters")
        String eventId
) {
}
