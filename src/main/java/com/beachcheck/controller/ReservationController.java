package com.beachcheck.controller;

import com.beachcheck.domain.User;
import com.beachcheck.dto.reservation.ReservationCreateRequest;
import com.beachcheck.dto.reservation.ReservationResponse;
import com.beachcheck.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/beaches")
@Validated
public class ReservationController {

  private final ReservationService reservationService;

  public ReservationController(ReservationService reservationService) {
    this.reservationService = reservationService;
  }

    @PostMapping("/{beachId}/reservations")
    public ResponseEntity<ReservationResponse> createReservation(
            @AuthenticationPrincipal User user,
            @PathVariable @NotNull UUID beachId,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        ReservationResponse response =
                reservationService.createReservation(user.getId(), beachId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    ReservationResponse response =
        reservationService.createReservation(user.getId(), beachId, request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/reservations")
  public ResponseEntity<List<ReservationResponse>> getMyReservations(
      @AuthenticationPrincipal User user) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    return ResponseEntity.ok(reservationService.getMyReservations(user.getId()));
  }

  @DeleteMapping("/{beachId}/reservations/{reservationId}")
  public ResponseEntity<Void> cancelReservation(
      @AuthenticationPrincipal User user,
      @PathVariable @NotNull UUID beachId,
      @PathVariable @NotNull UUID reservationId) {
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    reservationService.cancelReservation(user.getId(), beachId, reservationId);
    return ResponseEntity.noContent().build();
  }
}
