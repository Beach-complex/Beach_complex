package com.beachcheck.fixture;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beachcheck.dto.reservation.ReservationCreateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

public final class ReservationTestFixtures {

  private ReservationTestFixtures() {}

  // TODO(OAuth): OAuth 인증 도입 시 Authorization 헤더 생성/포맷(Bearer) 및 인증 실패 케이스를 테스트 헬퍼에 반영.
  /**
   * 예약을 생성하고 reservationId를 반환한다.
   *
   * <p>비고: 이 헬퍼는 상태 코드를 함께 검증하므로, 기대 상태가 명확할 때만 사용한다.
   */
  private static String createReservationAndGetIdWithStatus(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      String authHeader,
      UUID beachId,
      String reservedAtUtc,
      String eventId,
      ResultMatcher statusMatcher)
      throws Exception {
    String requestBody = buildCreateRequestBody(objectMapper, reservedAtUtc, eventId);

    String responseBody =
        mockMvc
            .perform(
                post(ApiRoutes.BEACH_RESERVATIONS, beachId)
                    .header("Authorization", authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(statusMatcher)
            .andReturn()
            .getResponse()
            .getContentAsString();

    return objectMapper.readTree(responseBody).get("reservationId").asText();
  }

  /** 성공 경로(201 Created) 전용 헬퍼. */
  public static String createReservationAndGetIdSuccess(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      String authHeader,
      UUID beachId,
      String reservedAtUtc,
      String eventId)
      throws Exception {
    return createReservationAndGetIdWithStatus(
        mockMvc, objectMapper, authHeader, beachId, reservedAtUtc, eventId, status().isCreated());
  }

  public static String buildCreateRequestBody(
      ObjectMapper objectMapper, String reservedAtUtc, String eventId) throws Exception {
    ReservationCreateRequest payload = new ReservationCreateRequest(reservedAtUtc, eventId);
    return objectMapper.writeValueAsString(payload);
  }

  public static String atUtc(Instant instant) {
    return instant.truncatedTo(ChronoUnit.SECONDS).toString();
  }

  public static String futureReservedAtUtc(Instant base, long hours) {
    return atUtc(base.plus(hours, ChronoUnit.HOURS));
  }

  public static String pastReservedAtUtc(Instant base, long hours) {
    return atUtc(base.minus(hours, ChronoUnit.HOURS));
  }
}
