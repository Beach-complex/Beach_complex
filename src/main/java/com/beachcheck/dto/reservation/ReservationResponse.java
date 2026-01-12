package com.beachcheck.dto.reservation;

import com.beachcheck.domain.Reservation;
import java.time.Instant;
import java.util.UUID;

/**
 * Why: 도메인 객체를 외부 응답 계약으로 변환해 노출 범위를 통제하기 위해. Policy: 응답 DTO는 도메인 상태를 필요한 필드로만 매핑한다.
 * Contract(Input): 응답은 record 필드 값으로 생성된다. Contract(Output): 외부에는 record에 정의된 필드만 노출된다.
 */
public record ReservationResponse(
    UUID reservationId,
    String status,
    Instant reservedAtUtc,
    UUID beachId,
    String eventId,
    Instant createdAtUtc) {

  /**
   * Why: 응답 생성 경로를 단일화해 매핑 규칙의 분산을 방지하기 위해. Policy: 변환 규칙은 이 정적 팩토리 메서드에 둔다. Contract(Input):
   * reservation이 null이면 예외가 발생한다. Contract(Output): reservationId는 reservation.getId()와 같다.
   */
  public static ReservationResponse from(Reservation reservation) {
    return new ReservationResponse(
        reservation.getId(),
        reservation.getStatus().name(),
        reservation.getReservedAt(),
        reservation.getBeach().getId(),
        reservation.getEventId(),
        reservation.getCreatedAt());
  }
}
