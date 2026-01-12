package com.beachcheck.service;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.Reservation;
import com.beachcheck.domain.ReservationStatus;
import com.beachcheck.domain.User;
import com.beachcheck.dto.reservation.ReservationCreateRequest;
import com.beachcheck.exception.ApiException;
import com.beachcheck.exception.ErrorCode;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.ReservationRepository;
import com.beachcheck.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock ReservationRepository reservationRepository;
    @Mock BeachRepository beachRepository;
    @Mock UserRepository userRepository;
    Clock clock;

    ReservationService reservationService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC);
        reservationService = new ReservationService(
                reservationRepository,
                beachRepository,
                userRepository,
                clock
        );
    }

    @Test
    @DisplayName("예약 생성 성공")
    void createReservation_success() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");
        givenUserAndBeach(userId, beachId);
        givenNoDuplicate(userId, beachId, reservedAt);
        stubSaveReservation();

        ReservationCreateRequest req = req("2025-01-01T01:00:00Z", "  EVENT-1  ");

        // When
        var response = reservationService.createReservation(userId, beachId, req);

        // Then
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED.name());
        assertThat(response.eventId()).isEqualTo("EVENT-1");
        assertThat(response.reservedAtUtc()).isEqualTo(reservedAt);
        assertThat(response.beachId()).isEqualTo(beachId);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        then(reservationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(captor.getValue().getReservedAt()).isEqualTo(reservedAt);
    }

    @Test
    @DisplayName("예약 생성 실패 - 과거 시각")
    void createReservation_pastTime() {
        ReservationCreateRequest req = req("2024-12-31T23:59:00Z", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(UUID.randomUUID(), UUID.randomUUID(), req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_PAST_TIME);
    }

    @Test
    @DisplayName("예약 생성 실패 - 시간 형식 오류")
    void createReservation_invalidTimeFormat() {
        ReservationCreateRequest req = req("2025-01-01 01:00:00", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(UUID.randomUUID(), UUID.randomUUID(), req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_INVALID_TIME);
    }

    @Test
    @DisplayName("예약 생성 실패 - 중복 예약")
    void createReservation_duplicate() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        givenBeach(beachId);
        givenDuplicate();

        ReservationCreateRequest req = req("2025-01-01T01:00:00Z", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(userId, beachId, req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DUPLICATE);
        then(reservationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 실패 - 해변 없음")
    void createReservation_beachNotFound() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        givenBeachNotFound(beachId);

        ReservationCreateRequest req = req("2025-01-01T01:00:00Z", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(userId, beachId, req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BEACH_NOT_FOUND);
        then(reservationRepository).should(never()).save(any());
        then(userRepository).should(never()).findById(any());
    }

    @Test
    @DisplayName("예약 생성 실패 - 유저 없음")
    void createReservation_userNotFound() {
        /**
         * Why: 해변은 존재하지만 유저가 없는 경우의 예외 흐름을 명확히 보장.
         * Policy: 유저 미존재는 EntityNotFoundException으로 처리된다.
         * Contract(Input): 유효한 beachId, 존재하지 않는 userId.
         * Contract(Output): EntityNotFoundException 발생, 저장 없음.
         */
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        givenBeach(beachId);
        given(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(any(), any(), any()))
                .willReturn(false);
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        ReservationCreateRequest req = req("2025-01-01T01:00:00Z", null);

        var ex = catchThrowableOfType(
                () -> reservationService.createReservation(userId, beachId, req),
                jakarta.persistence.EntityNotFoundException.class
        );

        assertThat(ex.getMessage()).isEqualTo("User not found");
        then(reservationRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 - 이벤트 ID 공백은 null 처리")
    void createReservation_eventIdBlank_normalizesToNull() {
        /**
         * Why: 공백 eventId가 의미 없는 값으로 저장되는 것을 방지.
         * Policy: 공백/빈 문자열은 null로 정규화한다.
         * Contract(Input): eventId 공백 문자열.
         * Contract(Output): 응답 eventId는 null.
         */
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");

        givenUserAndBeach(userId, beachId);
        givenNoDuplicate(userId, beachId, reservedAt);
        stubSaveReservation();

        ReservationCreateRequest req = req("2025-01-01T01:00:00Z", "   ");

        var response = reservationService.createReservation(userId, beachId, req);

        assertThat(response.eventId()).isNull();
    }

    @Test
    @DisplayName("예약 생성 - 이벤트 ID null 유지")
    void createReservation_eventIdNull_staysNull() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");

        givenUserAndBeach(userId, beachId);
        givenNoDuplicate(userId, beachId, reservedAt);
        stubSaveReservation();

        ReservationCreateRequest req = req("2025-01-01T01:00:00Z", null);

        var response = reservationService.createReservation(userId, beachId, req);

        assertThat(response.eventId()).isNull();
    }

    @Test
    @DisplayName("예약 생성 - 현재 시각 예약 허용")
    void createReservation_nowIsAllowed() {
        /**
         * Why: 경계값(now)에서 허용/거부 정책을 고정.
         * Policy: reservedAt == now(UTC)는 허용된다.
         * Contract(Input): reservedAtUtc가 현재(고정 Clock)와 동일.
         * Contract(Output): 성공(RESERVATION_PAST_TIME 아님).
         */
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Instant reservedAt = Instant.parse("2025-01-01T00:00:00Z");

        givenUserAndBeach(userId, beachId);
        givenNoDuplicate(userId, beachId, reservedAt);
        stubSaveReservation();

        ReservationCreateRequest req = req("2025-01-01T00:00:00Z", "EVENT-2");

        var response = reservationService.createReservation(userId, beachId, req);

        assertThat(response.reservedAtUtc()).isEqualTo(reservedAt);
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED.name());
    }

    @Test
    @DisplayName("예약 생성 - 유저/해변/이벤트 ID 설정")
    void createReservation_setsUserBeachAndEventId() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");

        givenUserAndBeach(userId, beachId);
        givenNoDuplicate(userId, beachId, reservedAt);
        stubSaveReservation();

        ReservationCreateRequest req = req("2025-01-01T01:00:00Z", "  EVENT-3  ");

        // When
        reservationService.createReservation(userId, beachId, req);

        // Then
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        then(reservationRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUser().getId()).isEqualTo(userId);
        assertThat(captor.getValue().getBeach().getId()).isEqualTo(beachId);
        assertThat(captor.getValue().getEventId()).isEqualTo("EVENT-3");
    }

    @Test
    @DisplayName("예약 취소 실패 - 리소스 없음")
    void cancelReservation_notFound() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        given(reservationRepository.findByIdAndUserIdAndBeachId(reservationId, userId, beachId))
                .willReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(
                () -> reservationService.cancelReservation(userId, beachId, reservationId),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("예약 취소 성공")
    void cancelReservation_success() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);

        given(reservationRepository.findByIdAndUserIdAndBeachId(reservationId, userId, beachId))
                .willReturn(Optional.of(reservation));

        reservationService.cancelReservation(userId, beachId, reservationId);

        then(reservationRepository).should().delete(reservation);
    }

    @Test
    @DisplayName("내 예약 조회 - 응답 매핑")
    void getMyReservations_mapsResponses() {
        // Given
        /**
         * Why: 도메인 -> 응답 DTO 매핑이 정확해야 UI/API가 안전하다.
         * Policy: ReservationResponse.from 매핑 규칙을 그대로 따른다.
         * Contract(Input): 예약 1건 반환.
         * Contract(Output): 모든 필드가 Reservation과 일치.
         */
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setReservedAt(Instant.parse("2025-01-01T01:00:00Z"));
        reservation.setBeach(beach);
        reservation.setEventId("EVENT-4");
        reservation.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));

        given(reservationRepository.findByUserId(userId)).willReturn(List.of(reservation));

        // When
        var responses = reservationService.getMyReservations(userId);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).reservationId()).isEqualTo(reservationId);
        assertThat(responses.get(0).status()).isEqualTo(ReservationStatus.CONFIRMED.name());
        assertThat(responses.get(0).reservedAtUtc()).isEqualTo(reservation.getReservedAt());
        assertThat(responses.get(0).beachId()).isEqualTo(beachId);
        assertThat(responses.get(0).eventId()).isEqualTo("EVENT-4");
        assertThat(responses.get(0).createdAtUtc()).isEqualTo(reservation.getCreatedAt());
    }

    private ReservationCreateRequest req(String reservedAtUtc, String eventId) {
        return new ReservationCreateRequest(reservedAtUtc, eventId);
    }

    private void givenUserAndBeach(UUID userId, UUID beachId) {
        givenBeach(beachId);
        givenUser(userId);
    }

    private void givenBeach(UUID beachId) {
        given(beachRepository.findById(beachId)).willReturn(Optional.of(beach(beachId)));
    }

    private void givenBeachNotFound(UUID beachId) {
        given(beachRepository.findById(beachId)).willReturn(Optional.empty());
    }

    private void givenUser(UUID userId) {
        given(userRepository.findById(userId)).willReturn(Optional.of(user(userId)));
    }

    private void givenNoDuplicate(UUID userId, UUID beachId, Instant reservedAt) {
        given(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(userId, beachId, reservedAt))
                .willReturn(false);
    }

    private void givenDuplicate() {
        given(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(any(), any(), any()))
                .willReturn(true);
    }

    private void stubSaveReservation() {
        given(reservationRepository.save(any(Reservation.class))).willAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private Beach beach(UUID beachId) {
        Beach beach = new Beach();
        beach.setId(beachId);
        return beach;
    }

    private User user(UUID userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
