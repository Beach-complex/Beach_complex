package com.beachcheck.integration;

import static com.beachcheck.fixture.UniqueTestFixtures.uniqueEmail;
import static com.beachcheck.fixture.UserTestFixtures.createEmailLoginUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beachcheck.base.ApiTest;
import com.beachcheck.domain.User;
import com.beachcheck.fixture.ApiErrorTestFixtures;
import com.beachcheck.fixture.ApiRoutes;
import com.beachcheck.repository.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

@DisplayName("인증/인가 최소 통합 테스트")
class AuthSecurityIntegrationTest extends ApiTest {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private static final String JSON_KEY_EMAIL = "email";
  private static final String JSON_KEY_PASSWORD = "password";
  private static final String JSON_KEY_ACCESS_TOKEN = "accessToken";
  private static final String JSON_KEY_REFRESH_TOKEN = "refreshToken";
  private static final String JSON_KEY_TOKEN_TYPE = "tokenType";

  private static final String TOKEN_TYPE_BEARER = "Bearer";
  private static final String INVALID_REFRESH_TOKEN = "invalid-refresh-token";

  private static final String USER_NAME = "Auth IT User";
  private static final String RAW_PASSWORD = "Password1!";

  private static final String CODE_UNAUTHORIZED = "UNAUTHORIZED";
  private static final String TITLE_UNAUTHORIZED = "Authentication required";
  private static final String TITLE_INVALID_GRANT = "Invalid grant";

  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private User user;
  private String email;

  @BeforeEach
  void setUp() {
    email = uniqueEmail("auth_it");

    user =
        userRepository.save(createEmailLoginUser(email, USER_NAME, RAW_PASSWORD, passwordEncoder));
  }

  @Nested
  @DisplayName("보호 API 접근")
  class ProtectedApiAccessTests {

    @Test
    @DisplayName("TC1: 토큰 없이 내 정보 조회 시 401")
    void me_withoutToken_returnsUnauthorized() throws Exception {
      // Given: 인증 헤더 없음

      // When & Then: 인증 진입점에서 401 계약 응답
      mockMvc
          .perform(get(ApiRoutes.AUTH_ME))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", BEARER_PREFIX.trim()))
          .andExpect(
              ApiErrorTestFixtures.problemDetail(
                  objectMapper, 401, TITLE_UNAUTHORIZED, CODE_UNAUTHORIZED));
    }
  }

  @Nested
  @DisplayName("로그인 연계")
  class LoginFlowTests {

    @Test
    @DisplayName("TC2: 로그인 성공 후 내 정보 조회 시 200")
    void login_thenMe_returnsOk() throws Exception {
      // Given: 활성 사용자 계정

      // When: 로그인으로 액세스 토큰 발급
      String accessToken = loginAndGetAccessToken();

      // Then: 보호 API 접근 성공
      mockMvc
          .perform(get(ApiRoutes.AUTH_ME).header(AUTHORIZATION_HEADER, BEARER_PREFIX + accessToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(user.getId().toString()))
          .andExpect(jsonPath("$.email").value(email))
          .andExpect(jsonPath("$.name").value(USER_NAME));
    }
  }

  @Nested
  @DisplayName("리프레시 토큰")
  class RefreshTokenTests {

    @Test
    @DisplayName("TC3: 유효하지 않은 리프레시 토큰이면 400")
    void refresh_withInvalidToken_returnsBadRequest() throws Exception {
      // Given: 유효하지 않은 refresh token
      String requestBody = createRefreshRequestBody(INVALID_REFRESH_TOKEN);

      // When & Then: 요청 오류(400) 계약 응답
      mockMvc
          .perform(
              post(ApiRoutes.AUTH_REFRESH)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(
              ApiErrorTestFixtures.problemDetail(
                  objectMapper, 400, TITLE_INVALID_GRANT, "INVALID_GRANT"));
    }
  }

  private String loginAndGetAccessToken() throws Exception {
    String requestBody = createLoginRequestBody(email, RAW_PASSWORD);

    MvcResult loginResult =
        mockMvc
            .perform(
                post(ApiRoutes.AUTH_LOGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$." + JSON_KEY_ACCESS_TOKEN).isNotEmpty())
            .andExpect(jsonPath("$." + JSON_KEY_REFRESH_TOKEN).isNotEmpty())
            .andExpect(jsonPath("$." + JSON_KEY_TOKEN_TYPE).value(TOKEN_TYPE_BEARER))
            .andReturn();

    return objectMapper
        .readTree(loginResult.getResponse().getContentAsString())
        .path(JSON_KEY_ACCESS_TOKEN)
        .asText();
  }

  private String createLoginRequestBody(String loginEmail, String password) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(JSON_KEY_EMAIL, loginEmail, JSON_KEY_PASSWORD, password));
  }

  private String createRefreshRequestBody(String refreshToken) throws Exception {
    return objectMapper.writeValueAsString(Map.of(JSON_KEY_REFRESH_TOKEN, refreshToken));
  }
}
