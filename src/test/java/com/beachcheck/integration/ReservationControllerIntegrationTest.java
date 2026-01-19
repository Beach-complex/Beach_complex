package com.beachcheck.integration;

import static com.beachcheck.fixture.FavoriteTestFixtures.createBeachWithLocation;
import static com.beachcheck.fixture.FavoriteTestFixtures.createUser;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beachcheck.base.ApiTest;
import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

/**
 * Why: SSOT Core-4(예약 생성) API 계약을 통합테스트로 고정하여 회귀를 방지한다.
 *
 * <p>Policy: 고정 Clock 기반 시간 판단, ProblemDetail 코드/상태 검증을 유지한다.
 *
 * <p>Contract(Input): 인증 사용자, beachId, reservedAtUtc(ISO-8601 UTC), eventId(optional).
 *
 * <p>Contract(Output): 201 응답과 필드 반환 또는 명시된 에러 코드(RESERVATION_* / BEACH_NOT_FOUND / 401).
 */
class ReservationControllerIntegrationTest extends ApiTest {

  @Autowired private UserRepository userRepository;

  @Autowired private BeachRepository beachRepository;

  @MockBean private Clock clock;

  private static final Instant FIXED_NOW = Instant.parse("2026-01-17T00:00:00Z");

  private User user;
  private Beach beach;

  @BeforeEach
  void setUp() {
    // Why: 시간 의존 테스트의 플래키 방지를 위해 고정 Clock을 사용한다.
    // Policy: 테스트는 FIXED_NOW 기준으로만 비교한다.
    // Contract(Output): service layer의 Instant.now(clock) 결과가 FIXED_NOW로 고정된다.
    when(clock.instant()).thenReturn(FIXED_NOW);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);

    String uniqueCode = "TEST_BEACH_" + UUID.randomUUID().toString().substring(0, 8);
    String uniqueEmail = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

    beach =
        beachRepository.save(createBeachWithLocation(uniqueCode, "Test Beach", 129.1603, 35.1587));
    user = userRepository.save(createUser(uniqueEmail, "Test User"));
  }

  @Test
  @DisplayName("P0-01: Create reservation succeeds with auth, beachId, future time")
  void createReservation_success() throws Exception {
    String reservedAtUtc =
        FIXED_NOW.plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    String requestBody = createRequestBody(reservedAtUtc, "EVENT-123");

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reservationId").isNotEmpty())
        .andExpect(jsonPath("$.status").value("CONFIRMED"))
        .andExpect(jsonPath("$.reservedAtUtc").value(reservedAtUtc))
        .andExpect(jsonPath("$.beachId").value(beach.getId().toString()))
        .andExpect(jsonPath("$.eventId").value("EVENT-123"))
        .andExpect(jsonPath("$.createdAtUtc").isNotEmpty());
  }

  @Test
  @DisplayName("P0-02: Past time reservation fails with RESERVATION_PAST_TIME")
  void createReservation_pastTime_returnsBadRequest() throws Exception {
    String reservedAtUtc =
        FIXED_NOW.minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    String requestBody = createRequestBody(reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("RESERVATION_PAST_TIME"))
        .andExpect(jsonPath("$.code").value("RESERVATION_PAST_TIME"));
  }

  @Test
  @DisplayName("P0-03: Duplicate reservation fails with RESERVATION_DUPLICATE")
  void createReservation_duplicate_returnsConflict() throws Exception {
    String reservedAtUtc =
        FIXED_NOW.plus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    String requestBody = createRequestBody(reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("RESERVATION_DUPLICATE"))
        .andExpect(jsonPath("$.code").value("RESERVATION_DUPLICATE"));
  }

  @Test
  @DisplayName("P0-04: Missing beach returns BEACH_NOT_FOUND")
  void createReservation_beachNotFound_returnsNotFound() throws Exception {
    String reservedAtUtc =
        FIXED_NOW.plus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    String requestBody = createRequestBody(reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", UUID.randomUUID())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("BEACH_NOT_FOUND"))
        .andExpect(jsonPath("$.code").value("BEACH_NOT_FOUND"));
  }

  @Test
  @DisplayName("P0-05: Missing auth returns 401")
  void createReservation_missingAuth_returnsUnauthorized() throws Exception {
    String reservedAtUtc =
        FIXED_NOW.plus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
    String requestBody = createRequestBody(reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized());
  }

  private String createRequestBody(String reservedAtUtc, String eventId) throws Exception {
    Map<String, Object> payload = new HashMap<>();
    payload.put("reservedAtUtc", reservedAtUtc);
    if (eventId != null) {
      payload.put("eventId", eventId);
    }
    return objectMapper.writeValueAsString(payload);
  }
}
