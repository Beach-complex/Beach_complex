package com.beachcheck.fixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

public final class ReservationTestFixtures {

  private ReservationTestFixtures() {}

  /**
   * 예약을 생성하고 reservationId를 반환한다.
   *
   * <p>비고: 이 헬퍼는 상태 코드를 함께 검증하므로, 기대 상태가 명확할 때만 사용한다.
   */
  public static String createReservationAndGetId(
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
                post("/api/beaches/{beachId}/reservations", beachId)
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
  public static String createReservationAndGetId(
      MockMvc mockMvc,
      ObjectMapper objectMapper,
      String authHeader,
      UUID beachId,
      String reservedAtUtc,
      String eventId)
      throws Exception {
    return createReservationAndGetId(
        mockMvc, objectMapper, authHeader, beachId, reservedAtUtc, eventId, status().isCreated());
  }

  public static String buildCreateRequestBody(
      ObjectMapper objectMapper, String reservedAtUtc, String eventId) throws Exception {
    Map<String, Object> payload = new HashMap<>();
    payload.put("reservedAtUtc", reservedAtUtc);
    if (eventId != null) {
      payload.put("eventId", eventId);
    }
    return objectMapper.writeValueAsString(payload);
  }

  public static ResultMatcher problemDetailStatus(ObjectMapper objectMapper, int expectedStatus) {
    return result -> {
      assertProblemDetailContentType(result);
      JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
      assertThat(json.path("status").asInt()).isEqualTo(expectedStatus);
    };
  }

  public static ResultMatcher problemDetail(
      ObjectMapper objectMapper, int expectedStatus, String expectedTitle, String expectedCode) {
    return result -> {
      assertProblemDetailContentType(result);
      JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
      assertThat(json.path("status").asInt()).isEqualTo(expectedStatus);
      assertThat(json.path("title").asText()).isEqualTo(expectedTitle);
      assertThat(json.path("code").asText()).isEqualTo(expectedCode);
    };
  }

  private static void assertProblemDetailContentType(MvcResult result) {
    String contentType = result.getResponse().getContentType();
    if (contentType == null
        || !MediaType.parseMediaType(contentType)
            .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)) {
      throw new AssertionError("Unexpected content type: " + contentType);
    }
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
