package com.beachcheck.controller;

import com.beachcheck.domain.User;
import com.beachcheck.dto.reservation.ReservationCreateRequest;
import com.beachcheck.dto.reservation.ReservationResponse;
import com.beachcheck.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;
@RestController
@RequestMapping("/api/beaches")
@Validated
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

   /**
    * Why: 예약 주체를 클라이언트가 아니라 서버가 확정해 위조를 막기 위해.
    * Policy: userId는 요청 데이터가 아니라 보안 컨텍스트에서만 취득한다.
    * Contract(Input): 인증되지 않으면 401로 거부한다.
    * Contract(Output): 인증되면 userId로 예약을 생성해 201로 반환한다.
    */

    @PostMapping("/{beachId}/reservations")
    public ResponseEntity<ReservationResponse> createReservation(
            @AuthenticationPrincipal User user,
            @PathVariable @NotNull UUID beachId,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        if(user == null){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
        }
        ReservationResponse response =
                reservationService.createReservation(user.getId(), beachId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/reservations")
    public ResponseEntity<List<ReservationResponse>> getMyReservations(
            @AuthenticationPrincipal User user
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        return ResponseEntity.ok(reservationService.getMyReservations(user.getId()));
    }


    @DeleteMapping("/{beachId}/reservations/{reservationId}")
    public ResponseEntity<Void> cancelReservation(
            @AuthenticationPrincipal User user,
            @PathVariable @NotNull UUID beachId,
            @PathVariable @NotNull UUID reservationId
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        reservationService.cancelReservation(user.getId(), beachId, reservationId);
        return ResponseEntity.noContent().build();
    }

}
