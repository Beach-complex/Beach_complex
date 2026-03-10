package com.beachcheck.integration;

import static com.beachcheck.fixture.BeachTestFixtures.createBeachWithLocation;
import static com.beachcheck.fixture.UniqueTestFixtures.uniqueBeachCode;
import static com.beachcheck.fixture.UniqueTestFixtures.uniqueCode;
import static com.beachcheck.fixture.UniqueTestFixtures.uniqueEmail;
import static com.beachcheck.fixture.UserFavoriteTestFixtures.createFavorite;
import static com.beachcheck.fixture.UserTestFixtures.createUser;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beachcheck.base.ApiTest;
import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.fixture.ApiErrorTestFixtures;
import com.beachcheck.fixture.ApiRoutes;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.UserFavoriteRepository;
import com.beachcheck.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("BeachController 통합 테스트")
class BeachControllerIntegrationTest extends ApiTest {

  @Autowired private BeachRepository beachRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserFavoriteRepository userFavoriteRepository;

  private User user;
  private Beach favoriteBeach;
  private Beach otherBeach;
  private Beach nearbyBeach;
  private Beach farBeach;
  private String searchCodePrefix;
  private String searchTag;

  @BeforeEach
  void setUp() {
    user =
        userRepository.save(createUser(uniqueEmail("beach-controller"), "Beach Controller User"));

    searchCodePrefix = uniqueCode("BEACH_API");
    searchTag = uniqueCode("tag");

    favoriteBeach =
        saveBeach(searchCodePrefix + "_A", "Favorite Beach", 129.1603, 35.1587, searchTag, "busy");
    otherBeach =
        saveBeach(searchCodePrefix + "_B", "Other Beach", 129.1608, 35.1592, "other-tag", "normal");
    nearbyBeach =
        saveBeach(uniqueBeachCode(), "Nearby Beach", 10.0010, 10.0010, "nearby", "normal");
    farBeach = saveBeach(uniqueBeachCode(), "Far Beach", 30.0000, 30.0000, "far", "normal");

    userFavoriteRepository.save(createFavorite(user, favoriteBeach));
  }

  @Test
  @DisplayName("TC1: 기본 목록 조회는 anonymous 요청에도 응답한다")
  void searchBeaches_defaultList_anonymous_returnsBeaches() throws Exception {
    mockMvc
        .perform(get(ApiRoutes.BEACHES))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[?(@.id=='" + favoriteBeach.getId() + "')].id",
                hasItem(favoriteBeach.getId().toString())))
        .andExpect(
            jsonPath("$[?(@.id=='" + favoriteBeach.getId() + "')].isFavorite", hasItem(false)));
  }

  @Test
  @DisplayName("TC2: 기본 목록 조회는 인증 사용자의 찜 여부를 반영한다")
  void searchBeaches_defaultList_authenticated_reflectsFavorite() throws Exception {
    mockMvc
        .perform(get(ApiRoutes.BEACHES).header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.id=='" + favoriteBeach.getId() + "')].isFavorite", hasItem(true)))
        .andExpect(jsonPath("$[?(@.id=='" + otherBeach.getId() + "')].isFavorite", hasItem(false)));
  }

  @Test
  @DisplayName("TC3: 검색 요청은 q/tag 분기로 필터링되고 인증 사용자의 찜 여부를 반영한다")
  void searchBeaches_searchBranch_authenticated_reflectsFavorite() throws Exception {
    mockMvc
        .perform(
            get(ApiRoutes.BEACHES)
                .param("q", searchCodePrefix)
                .param("tag", searchTag)
                .header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(favoriteBeach.getId().toString()))
        .andExpect(jsonPath("$[0].code").value(favoriteBeach.getCode()))
        .andExpect(jsonPath("$[0].tag").value(searchTag))
        .andExpect(jsonPath("$[0].isFavorite").value(true));
  }

  @Test
  @DisplayName("TC4: 반경 검색은 nearby 분기로 라우팅되어 반경 내 해변만 반환한다")
  void searchBeaches_radiusBranch_returnsNearbyBeaches() throws Exception {
    mockMvc
        .perform(
            get(ApiRoutes.BEACHES).param("lat", "10.0").param("lon", "10.0").param("radiusKm", "5"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$[?(@.id=='" + nearbyBeach.getId() + "')].id",
                hasItem(nearbyBeach.getId().toString())))
        .andExpect(jsonPath("$[?(@.id=='" + farBeach.getId() + "')]").isEmpty());
  }

  @Test
  @DisplayName("TC5: 반경 검색 파라미터가 일부만 오면 400 ProblemDetail을 반환한다")
  void searchBeaches_partialRadiusParams_returnsBadRequestProblemDetail() throws Exception {
    mockMvc
        .perform(get(ApiRoutes.BEACHES).param("lat", "35.1587").param("lon", "129.1603"))
        .andExpect(status().isBadRequest())
        .andExpect(ApiErrorTestFixtures.problemDetailStatus(objectMapper, 400))
        .andExpect(jsonPath("$.title").value("Invalid Request"))
        .andExpect(
            jsonPath("$.detail")
                .value("Radius search requires all three parameters: lat, lon, radiusKm"));
  }

  @Test
  @DisplayName("TC6: 위도 검증 실패는 400 Validation Error 계약으로 고정된다")
  void searchBeaches_invalidLatitude_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get(ApiRoutes.BEACHES)
                .param("lat", "91")
                .param("lon", "129.1603")
                .param("radiusKm", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(ApiErrorTestFixtures.problemDetailStatus(objectMapper, 400))
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.errors.lat").value("Latitude must be between -90 and 90"));
  }

  private Beach saveBeach(
      String code, String name, double lon, double lat, String tag, String status) {
    Beach beach = createBeachWithLocation(code, name, lon, lat);
    beach.setTag(tag);
    beach.setStatus(status);
    return beachRepository.save(beach);
  }
}
