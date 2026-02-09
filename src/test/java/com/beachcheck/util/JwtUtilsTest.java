package com.beachcheck.util;

import static com.beachcheck.fixture.UserTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.config.JwtProperties;
import com.beachcheck.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JWT 유틸 단위 테스트")
class JwtUtilsTest {

  private static final String TEST_JWT_SIGNING_KEY =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  private JwtUtils jwtUtils;
  private User user;

  @BeforeEach
  void setUp() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(TEST_JWT_SIGNING_KEY);
    properties.setAccessTokenExpiration(60_000L);
    properties.setRefreshTokenExpiration(120_000L);

    jwtUtils = new JwtUtils(properties);

    user = createUser("jwt-test@example.com", "JWT Test User");
  }

  @Test
  @DisplayName("access token은 인증 토큰으로 허용된다")
  void accessToken_isAccessToken_true() {
    String accessToken = jwtUtils.generateAccessToken(user);

    assertThat(jwtUtils.validateToken(accessToken)).isTrue();
    assertThat(jwtUtils.isAccessToken(accessToken)).isTrue();
  }

  @Test
  @DisplayName("refresh token은 인증 토큰으로 허용되지 않는다")
  void refreshToken_isAccessToken_false() {
    String refreshToken = jwtUtils.generateRefreshToken(user);

    assertThat(jwtUtils.validateToken(refreshToken)).isTrue();
    assertThat(jwtUtils.isAccessToken(refreshToken)).isFalse();
  }

  @Test
  @DisplayName("type 클레임이 없는 레거시 토큰은 access로 간주한다")
  void legacyTokenWithoutType_isAccessToken_true() {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + 60_000L);
    String legacyToken =
        Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(Keys.hmacShaKeyFor(TEST_JWT_SIGNING_KEY.getBytes(StandardCharsets.UTF_8)))
            .compact();

    assertThat(jwtUtils.validateToken(legacyToken)).isTrue();
    assertThat(jwtUtils.isAccessToken(legacyToken)).isTrue();
  }
}
