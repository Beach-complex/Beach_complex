package com.beachcheck.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.auth.dto.request.LogInRequestDto;
import com.beachcheck.auth.dto.request.RefreshTokenRequestDto;
import com.beachcheck.auth.dto.request.SignUpRequestDto;
import com.beachcheck.auth.dto.response.AuthResponseDto;
import com.beachcheck.auth.dto.response.TokenResponseDto;
import com.beachcheck.auth.dto.response.UserResponseDto;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("인증 record DTO toString PII 마스킹 — ADR-009")
class DtoToStringMaskingTest {

  // 테스트 더미 자격증명 — toString 결과에 이 값들이 노출되지 않는지 검증하는 데 쓴다.
  // TODO: 두 번째 테스트가 동일 더미 자격증명을 필요로 하면 support/fixture/AuthTestFixtures로 추출한다.
  private static final String SAMPLE_EMAIL = "victim@example.com";
  private static final String SAMPLE_PASSWORD = "Password1!"; // gitleaks:allow 테스트 픽스처(실제 비밀번호 아님)
  private static final String SAMPLE_NAME = "홍길동VictimName";
  private static final String SAMPLE_ACCESS_TOKEN = "sample-access-token-value";
  private static final String SAMPLE_REFRESH_TOKEN = "sample-refresh-token-value";

  @Nested
  @DisplayName("Request DTO — 전체 마스킹")
  class RequestDtos {

    @Test
    @DisplayName("LogInRequestDto.toString은 email/password를 노출하지 않는다")
    void logInRequest_doesNotLeakCredentials() {
      // given
      LogInRequestDto dto = new LogInRequestDto(SAMPLE_EMAIL, SAMPLE_PASSWORD);

      // when
      String result = dto.toString();

      // then
      assertThat(result).doesNotContain(SAMPLE_EMAIL).doesNotContain(SAMPLE_PASSWORD);
      assertThat(result).contains("LogInRequestDto").contains("masked");
    }

    @Test
    @DisplayName("SignUpRequestDto.toString은 email/password/name을 노출하지 않는다")
    void signUpRequest_doesNotLeakCredentials() {
      // given
      SignUpRequestDto dto = new SignUpRequestDto(SAMPLE_EMAIL, SAMPLE_PASSWORD, SAMPLE_NAME);

      // when
      String result = dto.toString();

      // then
      assertThat(result)
          .doesNotContain(SAMPLE_EMAIL)
          .doesNotContain(SAMPLE_PASSWORD)
          .doesNotContain(SAMPLE_NAME);
      assertThat(result).contains("SignUpRequestDto").contains("masked");
    }

    @Test
    @DisplayName("RefreshTokenRequestDto.toString은 refreshToken 본문을 노출하지 않는다")
    void refreshTokenRequest_doesNotLeakToken() {
      // given
      RefreshTokenRequestDto dto = new RefreshTokenRequestDto(SAMPLE_REFRESH_TOKEN);

      // when
      String result = dto.toString();

      // then
      assertThat(result).doesNotContain(SAMPLE_REFRESH_TOKEN);
      assertThat(result).contains("RefreshTokenRequestDto").contains("masked");
    }
  }

  @Nested
  @DisplayName("Response DTO — 부분 마스킹 (민감 필드만)")
  class ResponseDtos {

    @Test
    @DisplayName("TokenResponseDto.toString은 accessToken은 가리고 tokenType/expiresIn은 노출한다")
    void tokenResponse_masksAccessTokenButKeepsMetadata() {
      // given
      TokenResponseDto dto = TokenResponseDto.of(SAMPLE_ACCESS_TOKEN, 3600L);

      // when
      String result = dto.toString();

      // then
      assertThat(result).doesNotContain(SAMPLE_ACCESS_TOKEN);
      assertThat(result).contains("Bearer").contains("3600");
    }

    @Test
    @DisplayName("UserResponseDto.toString은 email/name은 가리고 id/role은 노출한다")
    void userResponse_masksEmailAndNameButKeepsId() {
      // given
      UUID id = UUID.randomUUID();
      UserResponseDto dto =
          new UserResponseDto(id, SAMPLE_EMAIL, SAMPLE_NAME, "USER", Instant.now(), Instant.now());

      // when
      String result = dto.toString();

      // then
      assertThat(result).doesNotContain(SAMPLE_EMAIL).doesNotContain(SAMPLE_NAME);
      assertThat(result).contains(id.toString()).contains("USER");
    }

    @Test
    @DisplayName(
        "AuthResponseDto.toString은 accessToken/refreshToken을 가리고, user 필드 체이닝으로도 email/name이 새지 않는다")
    void authResponse_masksAllTokensAndPropagatesUserMasking() {
      // given
      UserResponseDto user =
          new UserResponseDto(
              UUID.randomUUID(), SAMPLE_EMAIL, SAMPLE_NAME, "USER", Instant.now(), Instant.now());
      AuthResponseDto dto =
          AuthResponseDto.of(SAMPLE_ACCESS_TOKEN, SAMPLE_REFRESH_TOKEN, 3600L, user);

      // when
      String result = dto.toString();

      // then
      assertThat(result)
          .doesNotContain(SAMPLE_ACCESS_TOKEN)
          .doesNotContain(SAMPLE_REFRESH_TOKEN)
          .doesNotContain(SAMPLE_EMAIL)
          .doesNotContain(SAMPLE_NAME);
      assertThat(result).contains("Bearer").contains("3600");
    }
  }
}
