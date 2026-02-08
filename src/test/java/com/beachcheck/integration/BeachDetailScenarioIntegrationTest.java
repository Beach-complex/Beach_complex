package com.beachcheck.integration;

import static com.beachcheck.fixture.FavoriteTestFixtures.createBeachWithLocation;
import static com.beachcheck.fixture.UserTestFixtures.createUser;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beachcheck.base.ApiTest;
import com.beachcheck.domain.Beach;
import com.beachcheck.domain.BeachCondition;
import com.beachcheck.domain.BeachFacility;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;
import com.beachcheck.fixture.ApiRoutes;
import com.beachcheck.repository.BeachConditionRepository;
import com.beachcheck.repository.BeachFacilityRepository;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.UserFavoriteRepository;
import com.beachcheck.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BeachDetailScenarioIntegrationTest extends ApiTest {
  // TODO(OAuth): OAuth 인증 도입 시 Authorization 헤더 생성/인증 경로 테스트 시나리오 확장.

  @Autowired private BeachRepository beachRepository;
  @Autowired private BeachConditionRepository beachConditionRepository;
  @Autowired private BeachFacilityRepository beachFacilityRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserFavoriteRepository userFavoriteRepository;

  @Test
  @DisplayName("P0-01: 상세 조회 정상 응답 - beaches/conditions/facilities 계약 확인")
  void detailScenario_happyPath() throws Exception {
    // Given: 해수욕장, 조건, 시설 데이터가 존재한다.
    Beach beach = createBeach("Test Beach", "busy");
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Instant older = now.minusSeconds(3600);
    Instant newer = now.minusSeconds(1800);
    createCondition(beach, older);
    createCondition(beach, newer);
    createFacility(beach, "A Shower", "SHOWER");
    createFacility(beach, "B Toilet", "TOILET");

    // When: 상세 화면에 필요한 API들을 호출한다.
    // Then: 각 API가 필수 필드를 포함해 정상 응답한다.
    mockMvc
        .perform(get(ApiRoutes.BEACHES).param("q", beach.getCode()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(beach.getId().toString()))
        .andExpect(jsonPath("$[0].status").value("busy"))
        .andExpect(jsonPath("$[0].latitude", closeTo(beach.getLocation().getY(), 0.000001)))
        .andExpect(jsonPath("$[0].longitude", closeTo(beach.getLocation().getX(), 0.000001)));

    mockMvc
        .perform(get(ApiRoutes.BEACH_CONDITIONS_RECENT, beach.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].beachId").value(beach.getId().toString()))
        .andExpect(jsonPath("$[0].observedAt").value(newer.toString()))
        .andExpect(jsonPath("$[0].weatherSummary").isNotEmpty())
        .andExpect(jsonPath("$[0].latitude", closeTo(beach.getLocation().getY(), 0.000001)))
        .andExpect(jsonPath("$[0].longitude", closeTo(beach.getLocation().getX(), 0.000001)))
        .andExpect(jsonPath("$[1].observedAt").value(older.toString()));

    mockMvc
        .perform(get(ApiRoutes.BEACH_FACILITIES, beach.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].beachId").value(beach.getId().toString()))
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].name").value("A Shower"))
        .andExpect(jsonPath("$[0].category").value("SHOWER"))
        .andExpect(jsonPath("$[1].name").value("B Toilet"))
        .andExpect(jsonPath("$[1].category").value("TOILET"))
        .andExpect(jsonPath("$[0].latitude", closeTo(beach.getLocation().getY(), 0.000001)))
        .andExpect(jsonPath("$[0].longitude", closeTo(beach.getLocation().getX(), 0.000001)));
  }

  @Test
  @DisplayName("P0-02: conditions 최근 24시간 필터 적용")
  void detailScenario_conditionsFilter_recentOnly() throws Exception {
    // Given: 최근 1시간 데이터와 25시간 전 데이터를 함께 저장한다.
    Beach beach = createBeach("Filter Beach", "normal");
    createCondition(beach, Instant.now().minusSeconds(3600));
    createCondition(beach, Instant.now().minusSeconds(60 * 60 * 25));

    // When: 최근 조건 조회 API를 호출한다.
    // Then: 최근 24시간 이내 데이터만 반환된다.
    mockMvc
        .perform(get(ApiRoutes.BEACH_CONDITIONS_RECENT, beach.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].beachId").value(beach.getId().toString()));
  }

  @Test
  @DisplayName("P0-03: 존재하지 않는 beachId는 빈 배열 반환")
  void detailScenario_missingBeach_returnsEmptyList() throws Exception {
    // Given: 존재하지 않는 해수욕장 ID가 있다.
    UUID missingBeachId = UUID.randomUUID();

    // When: 조건/시설 조회 API를 호출한다.
    // Then: 빈 배열이 반환된다.
    mockMvc
        .perform(get(ApiRoutes.BEACH_CONDITIONS_RECENT, missingBeachId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    mockMvc
        .perform(get(ApiRoutes.BEACH_FACILITIES, missingBeachId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("P0-04: 로그인 사용자 찜 여부(isFavorite) 반영")
  void detailScenario_isFavorite_reflectsUserFavorites() throws Exception {
    // Given: 로그인 사용자와 찜한 해수욕장, 찜하지 않은 해수욕장이 있다.
    String uniqueEmail = "fav_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    User user = userRepository.save(createUser(uniqueEmail, "Favorite User"));

    String codePrefix = "FAV_GROUP_" + UUID.randomUUID().toString().substring(0, 8);
    Beach favoriteBeach =
        createBeachWithLocation(codePrefix + "_A", "Favorite Beach", 129.1603, 35.1587);
    favoriteBeach.setStatus("normal");
    favoriteBeach = beachRepository.save(favoriteBeach);

    Beach otherBeach = createBeachWithLocation(codePrefix + "_B", "Other Beach", 129.1603, 35.1587);
    otherBeach.setStatus("normal");
    otherBeach = beachRepository.save(otherBeach);

    userFavoriteRepository.save(new UserFavorite(user, favoriteBeach));

    // When: 인증된 상태로 beaches 조회 API를 호출한다.
    // Then: 해당 해수욕장이 isFavorite=true, 찜하지 않은 해수욕장은 false로 반환된다.
    mockMvc
        .perform(
            get(ApiRoutes.BEACHES).param("q", codePrefix).header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(
            jsonPath(
                "$[*].id",
                containsInAnyOrder(
                    favoriteBeach.getId().toString(), otherBeach.getId().toString())))
        .andExpect(
            jsonPath(
                "$[?(@.id=='" + favoriteBeach.getId().toString() + "')].isFavorite", hasItem(true)))
        .andExpect(
            jsonPath(
                "$[?(@.id=='" + otherBeach.getId().toString() + "')].isFavorite", hasItem(false)));
  }

  @Test
  @DisplayName("P0-05: 잘못된 UUID는 400 반환 (conditions/facilities)")
  void detailScenario_invalidUuid_returnsBadRequest() throws Exception {
    // Given: 잘못된 UUID 문자열이 있다.
    // When: 조건/시설 조회 API를 호출한다.
    // Then: 400 응답을 반환한다.
    mockMvc
        .perform(get(ApiRoutes.BEACH_CONDITIONS_RECENT, "not-a-uuid"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get(ApiRoutes.BEACH_FACILITIES, "not-a-uuid"))
        .andExpect(status().isBadRequest());
  }

  private Beach createBeach(String name, String status) {
    String code = "TEST_BEACH_" + UUID.randomUUID().toString().substring(0, 8);
    Beach beach = createBeachWithLocation(code, name, 129.1603, 35.1587);
    beach.setStatus(status);
    return beachRepository.save(beach);
  }

  private void createCondition(Beach beach, Instant observedAt) {
    BeachCondition condition = new BeachCondition();
    condition.setBeach(beach);
    condition.setObservedAt(observedAt);
    condition.setWaterTemperatureCelsius(20.5);
    condition.setWaveHeightMeters(0.8);
    condition.setWeatherSummary("temp:20.5C, rain:0.0mm, wind:1.2m/s");
    condition.setObservationPoint(beach.getLocation());
    beachConditionRepository.save(condition);
  }

  private void createFacility(Beach beach, String name, String category) {
    BeachFacility facility = new BeachFacility();
    facility.setBeach(beach);
    facility.setName(name);
    facility.setCategory(category);
    facility.setLocation(beach.getLocation());
    beachFacilityRepository.save(facility);
  }
}
