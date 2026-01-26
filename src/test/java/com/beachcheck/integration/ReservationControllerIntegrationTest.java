package com.beachcheck.integration;

import static com.beachcheck.fixture.FavoriteTestFixtures.createBeachWithLocation;
import static com.beachcheck.fixture.FavoriteTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beachcheck.base.ApiTest;
import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.fixture.ReservationTestFixtures;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.ReservationRepository;
import com.beachcheck.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Why: SSOT Core-4(예약 생성) 통합테스트로 예약 API 계약을 검증하기 위해.
 *
 * <p>Policy: 고정 Clock으로 시간 의존 테스트를 결정적으로 만들고, ProblemDetail 필드를 확인한다.
 *
 * <p>Contract(Input): 인증 사용자, beachId, reservedAtUtc(ISO-8601 UTC), eventId(optional).
 *
 * <p>Contract(Output): 201 응답 필드 또는 명시된 에러 코드(RESERVATION_* / BEACH_NOT_FOUND / 401).
 */
class ReservationControllerIntegrationTest extends ApiTest {

  @Autowired private UserRepository userRepository;

  @Autowired private BeachRepository beachRepository;
  @Autowired private ReservationRepository reservationRepository;

  @MockBean private Clock clock;

  private static final Instant FIXED_NOW = Instant.parse("2026-01-17T00:00:00Z");

  private User user;
  private User otherUser;
  private Beach beach;

  @BeforeEach
  void setUp() {
    // Why: 삭제된 유저 토큰은 인증 필터에서 사용자 조회 실패로 401 처리된다.
    // Policy: FIXED_NOW 기준으로만 비교한다.
    // Contract(Output): service layer에서 Instant.now(clock) 결과가 FIXED_NOW가 된다.
    when(clock.instant()).thenReturn(FIXED_NOW);
    when(clock.getZone()).thenReturn(ZoneOffset.UTC);

    String uniqueCode = "TEST_BEACH_" + UUID.randomUUID().toString().substring(0, 8);
    String uniqueEmail = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

    beach =
        beachRepository.save(createBeachWithLocation(uniqueCode, "Test Beach", 129.1603, 35.1587));
    user = userRepository.save(createUser(uniqueEmail, "Test User"));
    otherUser =
        userRepository.save(
            createUser(
                "other_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com", "Other"));
  }

  @Test
  @DisplayName("P0-01: 정상 예약 생성 - 인증 사용자/유효한 beachId/미래 reservedAtUtc")
  void createReservation_success() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 1);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, "EVENT-123");

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
  @DisplayName("P0-02: 과거 시간 예약 실패 - RESERVATION_PAST_TIME")
  void createReservation_pastTime_returnsBadRequest() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.pastReservedAtUtc(FIXED_NOW, 1);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 400, "RESERVATION_PAST_TIME", "RESERVATION_PAST_TIME"));
  }

  @Test
  @DisplayName("P0-03: 중복 예약 실패 - RESERVATION_DUPLICATE")
  void createReservation_duplicate_returnsConflict() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 2);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

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
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 409, "RESERVATION_DUPLICATE", "RESERVATION_DUPLICATE"));
  }

  @Test
  @DisplayName("P0-04: 해변 없음 - BEACH_NOT_FOUND")
  void createReservation_beachNotFound_returnsNotFound() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 3);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", UUID.randomUUID())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNotFound())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 404, "BEACH_NOT_FOUND", "BEACH_NOT_FOUND"));
  }

  @Test
  @DisplayName("P0-05: 인증 없음 - 401")
  void createReservation_missingAuth_returnsUnauthorized() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 4);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 401, "UNAUTHORIZED", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("P0-06: reservedAtUtc 누락 400")
  void createReservation_missingReservedAtUtc_returnsBadRequest() throws Exception {
    String requestBody = objectMapper.writeValueAsString(java.util.Map.of("eventId", "EVENT-ONLY"));

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(ReservationTestFixtures.problemDetailStatus(objectMapper, 400));
  }

  @Test
  @DisplayName("P0-07: reservedAtUtc 빈값 400")
  void createReservation_blankReservedAtUtc_returnsBadRequest() throws Exception {
    String requestBody = ReservationTestFixtures.buildCreateRequestBody(objectMapper, "", null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(ReservationTestFixtures.problemDetailStatus(objectMapper, 400));
  }

  @Test
  @DisplayName("P0-08: reservedAtUtc 형식 오류 400 + RESERVATION_INVALID_TIME")
  void createReservation_invalidReservedAtUtc_returnsBadRequest() throws Exception {
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, "2026-01-17T25:00:00Z", null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 400, "RESERVATION_INVALID_TIME", "RESERVATION_INVALID_TIME"));
  }

  @Test
  @DisplayName("P0-09: reservedAtUtc == now 허용")
  void createReservation_reservedAtNow_returnsCreated() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.atUtc(FIXED_NOW);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, "EVENT-NOW");

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reservedAtUtc").value(reservedAtUtc));
  }

  @Test
  @DisplayName("P0-10: 잘못된 beachId UUID 400")
  void createReservation_invalidBeachId_returnsBadRequest() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 1);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", "not-a-uuid")
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(ReservationTestFixtures.problemDetailStatus(objectMapper, 400));
  }

  @Test
  @DisplayName("P0-11: 잘못된 reservationId UUID 400")
  void cancelReservation_invalidReservationId_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    "not-a-uuid")
                .header("Authorization", authHeader(user)))
        .andExpect(status().isBadRequest())
        .andExpect(ReservationTestFixtures.problemDetailStatus(objectMapper, 400));
  }

  @Test
  @DisplayName("P0-12: eventId 길이 128 허용")
  void createReservation_eventIdMaxLength_returnsCreated() throws Exception {
    String eventId = "a".repeat(128);
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 1);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, eventId);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.eventId").value(eventId));
  }

  @Test
  @DisplayName("P0-13: 예약 취소 재시도 - 404")
  void cancelReservation_afterDelete_returnsNotFound() throws Exception {
    // Why: 삭제 후 재요청이 404로 떨어지는지 확인해 멱등성/방어 동작을 보장한다.
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 11);
    String reservationId =
        ReservationTestFixtures.createReservationAndGetId(
            mockMvc, objectMapper, authHeader(user), beach.getId(), reservedAtUtc, null);

    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    reservationId)
                .header("Authorization", authHeader(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    reservationId)
                .header("Authorization", authHeader(user)))
        .andExpect(status().isNotFound())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 404, "RESOURCE_NOT_FOUND", "RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("P0-14: 내 예약 목록 응답 필드 확인")
  void getMyReservations_responseFields_present() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 12);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/beaches/reservations").header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reservationId").isNotEmpty())
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$[0].createdAtUtc").isNotEmpty());
  }

  @Test
  @DisplayName("P0-15: eventId 길이 초과 400")
  void createReservation_eventIdTooLong_returnsBadRequest() throws Exception {
    String longEventId = "a".repeat(129);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(
            objectMapper, ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 1), longEventId);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(ReservationTestFixtures.problemDetailStatus(objectMapper, 400));
  }

  @Test
  @DisplayName("P0-16: 예약 생성 후 내 예약 목록 반영")
  void createReservation_reflectsInMyReservations() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 5);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, "EVENT-999");

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/beaches/reservations").header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].reservedAtUtc").value(reservedAtUtc))
        .andExpect(jsonPath("$[0].beachId").value(beach.getId().toString()));
  }

  @Test
  @DisplayName("P0-17: 예약 생성 DB 저장 확인")
  void createReservation_persistsToDatabase() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 6);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, "EVENT-DB");

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated());

    var reservations = reservationRepository.findByUserId(user.getId());
    assertThat(reservations).hasSize(1);
    assertThat(reservations.get(0).getBeach().getId()).isEqualTo(beach.getId());
    assertThat(reservations.get(0).getReservedAt()).isEqualTo(Instant.parse(reservedAtUtc));
  }

  @Test
  @DisplayName("P0-18: 내 예약 목록 조회 - 인증 없음 401")
  void getMyReservations_missingAuth_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/beaches/reservations"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 401, "UNAUTHORIZED", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("P0-19: 내 예약 목록 조회 - 예약 없음 빈 배열")
  void getMyReservations_empty_returnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/beaches/reservations").header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("P0-20: 예약 취소 성공 - 204")
  void cancelReservation_success_returnsNoContent() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 7);
    String reservationId =
        ReservationTestFixtures.createReservationAndGetId(
            mockMvc, objectMapper, authHeader(user), beach.getId(), reservedAtUtc, null);

    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    reservationId)
                .header("Authorization", authHeader(user)))
        .andExpect(status().isNoContent());

    assertThat(
            reservationRepository.findByIdAndUserIdAndBeachId(
                UUID.fromString(reservationId), user.getId(), beach.getId()))
        .isEmpty();
  }

  @Test
  @DisplayName("P0-21: 예약 취소 실패 - 예약 없음 404")
  void cancelReservation_notFound_returnsNotFound() throws Exception {
    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    UUID.randomUUID())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isNotFound())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 404, "RESOURCE_NOT_FOUND", "RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("P0-22: 예약 취소 실패 - 인증 없음 401")
  void cancelReservation_missingAuth_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(
            delete(
                "/api/beaches/{beachId}/reservations/{reservationId}",
                beach.getId(),
                UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 401, "UNAUTHORIZED", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("P0-23: 예약 취소 실패 - 타 사용자 404")
  void cancelReservation_otherUser_returnsNotFound() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 8);
    String reservationId =
        ReservationTestFixtures.createReservationAndGetId(
            mockMvc, objectMapper, authHeader(user), beach.getId(), reservedAtUtc, null);

    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    reservationId)
                .header("Authorization", authHeader(otherUser)))
        .andExpect(status().isNotFound())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 404, "RESOURCE_NOT_FOUND", "RESOURCE_NOT_FOUND"));
  }

  @Test
  @DisplayName("P0-24: 예약 취소 후 목록에서 제거됨")
  void cancelReservation_removedFromMyReservations() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 14);
    String reservationId =
        ReservationTestFixtures.createReservationAndGetId(
            mockMvc, objectMapper, authHeader(user), beach.getId(), reservedAtUtc, null);

    mockMvc
        .perform(get("/api/beaches/reservations").header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    reservationId)
                .header("Authorization", authHeader(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/beaches/reservations").header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("P0-25: 예약 취소 후 동일 조건 재예약 가능")
  void cancelReservation_allowsRecreateWithSameTime() throws Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 15);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated());

    String reservationId =
        reservationRepository.findByUserId(user.getId()).get(0).getId().toString();

    mockMvc
        .perform(
            delete(
                    "/api/beaches/{beachId}/reservations/{reservationId}",
                    beach.getId(),
                    reservationId)
                .header("Authorization", authHeader(user)))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("P0-26: 예약 생성 실패 - 사용자 없음 401")
  void createReservation_userNotFound_returnsUnauthorized() throws Exception {
    // Why: 삭제된 유저 토큰은 인증 필터에서 사용자 조회 실패로 401 처리된다.
    userRepository.delete(user);

    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 9);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(
            objectMapper, reservedAtUtc, "EVENT-USER-MISSING");

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ReservationTestFixtures.problemDetail(
                objectMapper, 401, "UNAUTHORIZED", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("P0-27: eventId 공백은 null로 정규화")
  void createReservation_eventIdBlank_normalizesToNull() throws Exception {
    // Why: eventId는 공백만 있을 경우 null로 정규화해 저장한다.
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 10);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, "   ");

    mockMvc
        .perform(
            post("/api/beaches/{beachId}/reservations", beach.getId())
                .header("Authorization", authHeader(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isCreated());

    var reservations = reservationRepository.findByUserId(user.getId());
    assertThat(reservations).hasSize(1);
    assertThat(reservations.get(0).getEventId()).isNull();
  }

  /**
   * Why: 동일 user/beach/reservedAtUtc 동시 요청 시 중복 예약이 하나만 생성되는지 검증.
   *
   * <p>Policy: 동시에 요청되더라도 1건만 성공(201)하고 나머지는 409로 처리.
   */
  @Test
  @DisplayName("P2-01: 예약 생성 동시 요청 - 하나만 성공")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void createReservation_concurrentRequests_onlyOneSuccess()
      throws InterruptedException,
          TimeoutException,
          java.util.concurrent.ExecutionException,
          Exception {
    String reservedAtUtc = ReservationTestFixtures.futureReservedAtUtc(FIXED_NOW, 13);
    String requestBody =
        ReservationTestFixtures.buildCreateRequestBody(objectMapper, reservedAtUtc, null);

    String localBeachCode = "TEST_BEACH_" + UUID.randomUUID().toString().substring(0, 8);
    String localUserEmail = "user_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    Beach localBeach =
        beachRepository.save(
            createBeachWithLocation(localBeachCode, "Test Beach", 129.1603, 35.1587));

    User localUser = userRepository.save(createUser(localUserEmail, "Test User"));
    User localOtherUser =
        userRepository.save(
            createUser(
                "other_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com", "Other"));

    int threadCount = 10;
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
    try {
      AtomicInteger successCount = new AtomicInteger(0);
      AtomicInteger conflictCount = new AtomicInteger(0);
      AtomicInteger unexpectedCount = new AtomicInteger(0);

      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            CompletableFuture.runAsync(
                () -> {
                  try {
                    var mvcResult =
                        mockMvc
                            .perform(
                                post("/api/beaches/{beachId}/reservations", localBeach.getId())
                                    .header("Authorization", authHeader(localUser))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                            .andReturn();
                    int status = mvcResult.getResponse().getStatus();

                    if (status == 201) {
                      successCount.incrementAndGet();
                    } else if (status == 409) {
                      conflictCount.incrementAndGet();
                      String responseBody = mvcResult.getResponse().getContentAsString();
                      String contentType = mvcResult.getResponse().getContentType();

                      if (contentType == null
                          || !MediaType.parseMediaType(contentType)
                              .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)) {
                        throw new AssertionError("Unexpected content type: " + contentType);
                      }

                      var json = objectMapper.readTree(responseBody);
                      if (!"RESERVATION_DUPLICATE".equals(json.path("title").asText())
                          || !"RESERVATION_DUPLICATE".equals(json.path("code").asText())) {
                        throw new AssertionError("Unexpected problem detail: " + responseBody);
                      }
                    } else {
                      String responseBody = mvcResult.getResponse().getContentAsString();
                      String snippet =
                          responseBody.length() > 300
                              ? responseBody.substring(0, 300) + "..."
                              : responseBody;
                      unexpectedCount.incrementAndGet();
                      throw new AssertionError(
                          "Unexpected status: " + status + ", body: " + snippet);
                    }
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                },
                executorService));
      }

      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

      assertThat(successCount.get()).isEqualTo(1);
      assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
      assertThat(unexpectedCount.get()).isZero();

      var reservations = reservationRepository.findByUserId(localUser.getId());
      assertThat(reservations).hasSize(1);
      assertThat(reservations.get(0).getReservedAt()).isEqualTo(Instant.parse(reservedAtUtc));
    } finally {
      reservationRepository.deleteAll(reservationRepository.findByUserId(localUser.getId()));
      userRepository.deleteById(localUser.getId());
      userRepository.deleteById(localOtherUser.getId());
      beachRepository.deleteById(localBeach.getId());
      executorService.shutdown();
      if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
      }
    }
  }
}
