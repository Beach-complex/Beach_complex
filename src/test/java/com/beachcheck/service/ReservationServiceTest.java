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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    void createReservation_success() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        User user = new User();
        user.setId(userId);

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");

        when(beachRepository.findById(beachId)).thenReturn(Optional.of(beach));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(userId, beachId, reservedAt))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T01:00:00Z", "  EVENT-1  ");

        var response = reservationService.createReservation(userId, beachId, req);

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED.name());
        assertThat(response.eventId()).isEqualTo("EVENT-1");
        assertThat(response.reservedAtUtc()).isEqualTo(reservedAt);
        assertThat(response.beachId()).isEqualTo(beachId);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(captor.getValue().getReservedAt()).isEqualTo(reservedAt);
    }

    @Test
    void createReservation_pastTime() {
        ReservationCreateRequest req =
                new ReservationCreateRequest("2024-12-31T23:59:00Z", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(UUID.randomUUID(), UUID.randomUUID(), req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_PAST_TIME);
    }

    @Test
    void createReservation_invalidTimeFormat() {
        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01 01:00:00", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(UUID.randomUUID(), UUID.randomUUID(), req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_INVALID_TIME);
    }

    @Test
    void createReservation_duplicate() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        when(beachRepository.findById(beachId)).thenReturn(Optional.of(beach));
        when(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(any(), any(), any()))
                .thenReturn(true);

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T01:00:00Z", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(userId, beachId, req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DUPLICATE);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_beachNotFound() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        when(beachRepository.findById(beachId)).thenReturn(Optional.empty());

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T01:00:00Z", null);

        ApiException ex = catchThrowableOfType(
                () -> reservationService.createReservation(userId, beachId, req),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BEACH_NOT_FOUND);
        verify(reservationRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void createReservation_userNotFound() {
        /**
         * Why: 해변은 존재하지만 유저가 없는 경우의 예외 흐름을 명확히 보장.
         * Policy: 유저 미존재는 EntityNotFoundException으로 처리된다.
         * Contract(Input): 유효한 beachId, 존재하지 않는 userId.
         * Contract(Output): EntityNotFoundException 발생, 저장 없음.
         */
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        when(beachRepository.findById(beachId)).thenReturn(Optional.of(beach));
        when(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(any(), any(), any()))
                .thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T01:00:00Z", null);

        var ex = catchThrowableOfType(
                () -> reservationService.createReservation(userId, beachId, req),
                jakarta.persistence.EntityNotFoundException.class
        );

        assertThat(ex.getMessage()).isEqualTo("User not found");
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_eventIdBlank_normalizesToNull() {
        /**
         * Why: 공백 eventId가 의미 없는 값으로 저장되는 것을 방지.
         * Policy: 공백/빈 문자열은 null로 정규화한다.
         * Contract(Input): eventId 공백 문자열.
         * Contract(Output): 응답 eventId는 null.
         */
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        User user = new User();
        user.setId(userId);

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");

        when(beachRepository.findById(beachId)).thenReturn(Optional.of(beach));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(userId, beachId, reservedAt))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T01:00:00Z", "   ");

        var response = reservationService.createReservation(userId, beachId, req);

        assertThat(response.eventId()).isNull();
    }

    @Test
    void createReservation_eventIdNull_staysNull() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        User user = new User();
        user.setId(userId);

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");

        when(beachRepository.findById(beachId)).thenReturn(Optional.of(beach));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(userId, beachId, reservedAt))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T01:00:00Z", null);

        var response = reservationService.createReservation(userId, beachId, req);

        assertThat(response.eventId()).isNull();
    }

    @Test
    void createReservation_nowIsAllowed() {
        /**
         * Why: 경계값(now)에서 허용/거부 정책을 고정.
         * Policy: reservedAt == now(UTC)는 허용된다.
         * Contract(Input): reservedAtUtc가 현재(고정 Clock)와 동일.
         * Contract(Output): 성공(RESERVATION_PAST_TIME 아님).
         */
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        User user = new User();
        user.setId(userId);

        Instant reservedAt = Instant.parse("2025-01-01T00:00:00Z");

        when(beachRepository.findById(beachId)).thenReturn(Optional.of(beach));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(userId, beachId, reservedAt))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T00:00:00Z", "EVENT-2");

        var response = reservationService.createReservation(userId, beachId, req);

        assertThat(response.reservedAtUtc()).isEqualTo(reservedAt);
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED.name());
    }

    @Test
    void createReservation_setsUserBeachAndEventId() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();

        Beach beach = new Beach();
        beach.setId(beachId);

        User user = new User();
        user.setId(userId);

        Instant reservedAt = Instant.parse("2025-01-01T01:00:00Z");

        when(beachRepository.findById(beachId)).thenReturn(Optional.of(beach));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(reservationRepository.existsByUserIdAndBeachIdAndReservedAt(userId, beachId, reservedAt))
                .thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReservationCreateRequest req =
                new ReservationCreateRequest("2025-01-01T01:00:00Z", "  EVENT-3  ");

        reservationService.createReservation(userId, beachId, req);

        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
        assertThat(captor.getValue().getBeach()).isEqualTo(beach);
        assertThat(captor.getValue().getEventId()).isEqualTo("EVENT-3");
    }

    @Test
    void cancelReservation_notFound() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        when(reservationRepository.findByIdAndUserIdAndBeachId(reservationId, userId, beachId))
                .thenReturn(Optional.empty());

        ApiException ex = catchThrowableOfType(
                () -> reservationService.cancelReservation(userId, beachId, reservationId),
                ApiException.class
        );

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void cancelReservation_success() {
        UUID userId = UUID.randomUUID();
        UUID beachId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Reservation reservation = new Reservation();
        reservation.setId(reservationId);

        when(reservationRepository.findByIdAndUserIdAndBeachId(reservationId, userId, beachId))
                .thenReturn(Optional.of(reservation));

        reservationService.cancelReservation(userId, beachId, reservationId);

        verify(reservationRepository).delete(reservation);
    }

    @Test
    void getMyReservations_mapsResponses() {
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

        when(reservationRepository.findByUserId(userId)).thenReturn(List.of(reservation));

        var responses = reservationService.getMyReservations(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).reservationId()).isEqualTo(reservationId);
        assertThat(responses.get(0).status()).isEqualTo(ReservationStatus.CONFIRMED.name());
        assertThat(responses.get(0).reservedAtUtc()).isEqualTo(reservation.getReservedAt());
        assertThat(responses.get(0).beachId()).isEqualTo(beachId);
        assertThat(responses.get(0).eventId()).isEqualTo("EVENT-4");
        assertThat(responses.get(0).createdAtUtc()).isEqualTo(reservation.getCreatedAt());
    }
}
