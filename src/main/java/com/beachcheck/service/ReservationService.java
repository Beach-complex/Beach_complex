package com.beachcheck.service;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.Reservation;
import com.beachcheck.domain.ReservationStatus;
import com.beachcheck.domain.User;
import com.beachcheck.dto.reservation.ReservationCreateRequest;
import com.beachcheck.dto.reservation.ReservationResponse;
import com.beachcheck.exception.ApiException;
import com.beachcheck.exception.ErrorCode;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.ReservationRepository;
import com.beachcheck.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReservationService {

  private final ReservationRepository reservationRepository;
  private final BeachRepository beachRepository;
  private final UserRepository userRepository;
  private final Clock clock;

  public ReservationService(
      ReservationRepository reservationRepository,
      BeachRepository beachRepository,
      UserRepository userRepository,
      Clock clock) {
    this.reservationRepository = reservationRepository;
    this.beachRepository = beachRepository;
    this.userRepository = userRepository;
    this.clock = clock;
  }

  /**
   * Why: 예약 생성 규칙을 단일 진입점에서 강제해 일관된 결과를 보장하기 위해. Policy: 예약 시간은 현재 UTC 이후이며 동일 시각 중복 예약은 허용하지 않는다.
   * Contract(Input): userId 또는 beachId가 null이면 예외가 발생한다. Contract(Output): 성공 시 status는 CONFIRMED다.
   */
  public ReservationResponse createReservation(
      UUID userId, UUID beachId, ReservationCreateRequest request) {
    Instant reservedAt = parseReservedAtUtc(request.reservedAtUtc());

    if (reservedAt.isBefore(Instant.now(clock))) {
      throw new ApiException(
          ErrorCode.RESERVATION_PAST_TIME,
          "reservedAtUtc must be >= now(UTC)",
          Map.of("reservedAtUtc", request.reservedAtUtc()));
    }

    Beach beach =
        beachRepository
            .findById(beachId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.BEACH_NOT_FOUND,
                        "Beach not found",
                        Map.of("beachId", beachId.toString())));

    if (reservationRepository.existsByUserIdAndBeachIdAndReservedAt(userId, beachId, reservedAt)) {
      throw new ApiException(
          ErrorCode.RESERVATION_DUPLICATE,
          "Reservation already exists for this time",
          Map.of("reservedAtUtc", request.reservedAtUtc()));
    }

    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

    Reservation reservation = new Reservation();
    reservation.setUser(user);
    reservation.setBeach(beach);
    reservation.setReservedAt(reservedAt);
    reservation.setEventId(normalizeEventId(request.eventId()));
    reservation.setStatus(ReservationStatus.CONFIRMED);

    Reservation saved = reservationRepository.save(reservation);
    return ReservationResponse.from(saved);
  }

  /**
   * Why: 예약 삭제 시 소유자와 해변 스코프를 함께 검증해 타 사용자 삭제를 방지하기 위해. Policy: 삭제 대상은 reservationId+userId+beachId
   * 조합으로만 식별한다. Contract: Contract(Input): reservationId와 userId와 beachId로 조회한다.
   *
   * <p>Contract(Output): 해당 조합이 없으면 RESOURCE_NOT_FOUND로 실패한다.
   */
  public void cancelReservation(UUID userId, UUID beachId, UUID reservationId) {
    Reservation reservation =
        reservationRepository
            .findByIdAndUserIdAndBeachId(reservationId, userId, beachId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Reservation not found",
                        Map.of(
                            "reservationId", reservationId.toString(),
                            "beachId", beachId.toString())));

    reservationRepository.delete(reservation);
  }

  @Transactional(readOnly = true)
  public List<ReservationResponse> getMyReservations(UUID userId) {
    return reservationRepository.findByUserId(userId).stream()
        .map(ReservationResponse::from)
        .toList();
  }

  /**
   * Why: 시간 입력을 표준 형식으로 통일해 해석 차이를 제거하기 위해. Policy: 파싱 실패는 RESERVATION_INVALID_TIME으로 변환한다.
   * Contract(Input): reservedAtUtc는 ISO-8601 UTC 문자열이다. Contract(Output): 성공 시 Instant를 반환한다.
   */
  private Instant parseReservedAtUtc(String reservedAtUtc) {
    try {
      return Instant.parse(reservedAtUtc);
    } catch (DateTimeParseException ex) {
      throw new ApiException(
          ErrorCode.RESERVATION_INVALID_TIME,
          "reservedAtUtc must be ISO-8601 UTC (e.g. 2025-10-24T12:00:00Z)",
          Map.of("reservedAtUtc", reservedAtUtc));
    }
  }

  /**
   * Why: 이벤트 식별자 입력을 정규화해 의미 없는 값 저장을 방지하기 위해. Policy: 공백만 있는 값은 null로 정규화한다. Contract(Input):
   * eventId가 null일 수 있다. Contract(Output): 반환값은 null 또는 공백이 아닌 문자열이다.
   */
  private String normalizeEventId(String eventId) {
    if (eventId == null) {
      return null;
    }
    String trimmed = eventId.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
