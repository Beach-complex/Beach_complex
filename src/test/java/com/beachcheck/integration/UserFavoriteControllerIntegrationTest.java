package com.beachcheck.integration;

import static com.beachcheck.fixture.BeachTestFixtures.createBeachWithLocation;
import static com.beachcheck.fixture.UniqueTestFixtures.uniqueBeachCode;
import static com.beachcheck.fixture.UniqueTestFixtures.uniqueEmail;
import static com.beachcheck.fixture.UserFavoriteTestFixtures.createFavorite;
import static com.beachcheck.fixture.UserTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("UserFavoriteController 통합 테스트")
class UserFavoriteControllerIntegrationTest extends ApiTest {

  @Autowired private UserRepository userRepository;
  @Autowired private BeachRepository beachRepository;
  @Autowired private UserFavoriteRepository userFavoriteRepository;

  private User user;
  private User otherUser;
  private Beach favoriteBeach;
  private Beach toggleBeach;

  @BeforeEach
  void setUp() {
    user =
        userRepository.save(
            createUser(uniqueEmail("favorite-controller"), "Favorite Controller User"));
    otherUser =
        userRepository.save(createUser(uniqueEmail("favorite-other"), "Other Favorite User"));
    favoriteBeach = saveBeach(uniqueBeachCode(), "Favorite Beach");
    toggleBeach = saveBeach(uniqueBeachCode(), "Toggle Beach");

    userFavoriteRepository.save(createFavorite(user, favoriteBeach));
    userFavoriteRepository.save(createFavorite(otherUser, toggleBeach));
  }

  @Test
  @DisplayName("TC1: 내 찜 목록 조회는 인증 사용자의 데이터만 반환한다")
  void getMyFavorites_authenticated_returnsOwnFavorites() throws Exception {
    mockMvc
        .perform(get(ApiRoutes.FAVORITES).header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(content().string(startsWith("[{\"id\":\"" + favoriteBeach.getId() + "\"")))
        .andExpect(content().string(containsString("\"code\":\"" + favoriteBeach.getCode() + "\"")))
        .andExpect(content().string(containsString("\"name\":\"Favorite Beach\"")))
        .andExpect(content().string(containsString("\"status\":\"OPEN\"")))
        .andExpect(content().string(not(containsString(toggleBeach.getCode()))));
  }

  @Test
  @DisplayName("TC2: 내 찜 목록 조회는 인증이 없으면 401 ProblemDetail을 반환한다")
  void getMyFavorites_missingAuth_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(get(ApiRoutes.FAVORITES))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ApiErrorTestFixtures.problemDetail(
                objectMapper, 401, "Authentication required", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("TC3: 찜 추가는 200과 message/isFavorite 계약을 반환하고 DB에 반영된다")
  void addFavorite_success_returnsContractAndPersists() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.FAVORITE_ITEM, toggleBeach.getId())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("찜 목록에 추가되었습니다."))
        .andExpect(jsonPath("$.isFavorite").value(true));

    assertThat(userFavoriteRepository.findByUserIdAndBeachId(user.getId(), toggleBeach.getId()))
        .isPresent();
  }

  @Test
  @DisplayName("TC4: 존재하지 않는 beachId로 찜 추가 시 400 ProblemDetail을 반환한다")
  void addFavorite_missingBeach_returnsBadRequestProblemDetail() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.FAVORITE_ITEM, UUID.randomUUID())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isBadRequest())
        .andExpect(ApiErrorTestFixtures.problemDetailStatus(objectMapper, 400))
        .andExpect(jsonPath("$.title").value("Invalid Request"))
        .andExpect(jsonPath("$.detail").value("해수욕장을 찾을 수 없습니다."));
  }

  @Test
  @DisplayName("TC5: 중복 찜 추가 시 409 ProblemDetail을 반환한다")
  void addFavorite_duplicate_returnsConflictProblemDetail() throws Exception {
    mockMvc
        .perform(
            post(ApiRoutes.FAVORITE_ITEM, favoriteBeach.getId())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isConflict())
        .andExpect(ApiErrorTestFixtures.problemDetailStatus(objectMapper, 409))
        .andExpect(jsonPath("$.title").value("Invalid State"))
        .andExpect(jsonPath("$.detail").value("이미 찜한 해수욕장입니다."));
  }

  @Test
  @DisplayName("TC6: 찜 제거는 200과 isFavorite=false 계약을 반환하고 DB에서 삭제한다")
  void removeFavorite_success_returnsContractAndDeletes() throws Exception {
    mockMvc
        .perform(
            delete(ApiRoutes.FAVORITE_ITEM, favoriteBeach.getId())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("찜 목록에서 제거되었습니다."))
        .andExpect(jsonPath("$.isFavorite").value(false));

    assertThat(userFavoriteRepository.findByUserIdAndBeachId(user.getId(), favoriteBeach.getId()))
        .isEmpty();
  }

  @Test
  @DisplayName("TC7: 찜 토글은 미찜 상태에서 추가 계약을 반환하고 DB에 반영된다")
  void toggleFavorite_addBranch_returnsAddedContractAndPersists() throws Exception {
    mockMvc
        .perform(
            put(ApiRoutes.FAVORITE_TOGGLE, toggleBeach.getId())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("찜 목록에 추가되었습니다."))
        .andExpect(jsonPath("$.isFavorite").value(true));

    assertThat(userFavoriteRepository.findByUserIdAndBeachId(user.getId(), toggleBeach.getId()))
        .isPresent();
  }

  @Test
  @DisplayName("TC8: 찜 토글은 이미 찜한 해변에서 제거 계약을 반환한다")
  void toggleFavorite_removeBranch_returnsRemovedContract() throws Exception {
    mockMvc
        .perform(
            put(ApiRoutes.FAVORITE_TOGGLE, favoriteBeach.getId())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("찜 목록에서 제거되었습니다."))
        .andExpect(jsonPath("$.isFavorite").value(false));

    assertThat(userFavoriteRepository.findByUserIdAndBeachId(user.getId(), favoriteBeach.getId()))
        .isEmpty();
  }

  @Test
  @DisplayName("TC9: 찜 여부 확인은 boolean 계약을 반환한다")
  void checkFavorite_returnsFavoriteState() throws Exception {
    mockMvc
        .perform(
            get(ApiRoutes.FAVORITE_CHECK, favoriteBeach.getId())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isFavorite").value(true));

    mockMvc
        .perform(
            get(ApiRoutes.FAVORITE_CHECK, toggleBeach.getId())
                .header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isFavorite").value(false));
  }

  @Test
  @DisplayName("TC10: 잘못된 UUID는 400 ProblemDetail을 반환한다")
  void favoriteEndpoint_invalidUuid_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            get(ApiRoutes.FAVORITE_CHECK, "not-a-uuid").header("Authorization", authHeader(user)))
        .andExpect(status().isBadRequest())
        .andExpect(ApiErrorTestFixtures.problemDetailStatus(objectMapper, 400));
  }

  @Test
  @DisplayName("TC11: 찜 추가는 인증이 없으면 401 ProblemDetail을 반환한다")
  void addFavorite_missingAuth_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(post(ApiRoutes.FAVORITE_ITEM, toggleBeach.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ApiErrorTestFixtures.problemDetail(
                objectMapper, 401, "Authentication required", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("TC12: 찜 제거는 인증이 없으면 401 ProblemDetail을 반환한다")
  void removeFavorite_missingAuth_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(delete(ApiRoutes.FAVORITE_ITEM, favoriteBeach.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ApiErrorTestFixtures.problemDetail(
                objectMapper, 401, "Authentication required", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("TC13: 찜 토글과 찜 여부 확인은 인증이 없으면 401 ProblemDetail을 반환한다")
  void toggleAndCheck_missingAuth_returnsUnauthorized() throws Exception {
    mockMvc
        .perform(put(ApiRoutes.FAVORITE_TOGGLE, favoriteBeach.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ApiErrorTestFixtures.problemDetail(
                objectMapper, 401, "Authentication required", "UNAUTHORIZED"));

    mockMvc
        .perform(get(ApiRoutes.FAVORITE_CHECK, favoriteBeach.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            ApiErrorTestFixtures.problemDetail(
                objectMapper, 401, "Authentication required", "UNAUTHORIZED"));
  }

  @Test
  @DisplayName("TC14: 찜 목록은 빈 결과를 허용한다")
  void getMyFavorites_empty_returnsEmptyArray() throws Exception {
    userFavoriteRepository.deleteAll(userFavoriteRepository.findByUserId(user.getId()));

    mockMvc
        .perform(get(ApiRoutes.FAVORITES).header("Authorization", authHeader(user)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  private Beach saveBeach(String code, String name) {
    return beachRepository.save(createBeachWithLocation(code, name, 129.1603, 35.1587));
  }
}
