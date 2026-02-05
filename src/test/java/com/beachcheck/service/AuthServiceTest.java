package com.beachcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.beachcheck.domain.RefreshToken;
import com.beachcheck.domain.User;
import com.beachcheck.dto.auth.request.LogInRequestDto;
import com.beachcheck.dto.auth.request.SignUpRequestDto;
import com.beachcheck.dto.auth.response.AuthResponseDto;
import com.beachcheck.dto.auth.response.TokenResponseDto;
import com.beachcheck.dto.auth.response.UserResponseDto;
import com.beachcheck.exception.ApiException;
import com.beachcheck.exception.ErrorCode;
import com.beachcheck.repository.RefreshTokenRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.util.JwtUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("인증 서비스 단위 테스트")
class AuthServiceTest {
  // TODO(OAuth): OAuth 로그인/연동 플로우 추가 시 테스트 케이스 구조 분리 및 시나리오 확장.

  private static final String EMAIL = "test@example.com";
  private static final String DUP_EMAIL = "dup@example.com";
  private static final String RAW_PASS = "Password1!";
  private static final String NAME = "Tester";
  private static final String ACCESS_TOKEN = "access";
  private static final String REFRESH_TOKEN = "refresh";
  private static final Duration ACCESS_TOKEN_DURATION = Duration.ofHours(1);
  private static final Duration REFRESH_TOKEN_DURATION = Duration.ofDays(1);
  private static final long ACCESS_EXPIRES_IN_SECONDS = ACCESS_TOKEN_DURATION.getSeconds();
  private static final long REFRESH_EXPIRES_IN_MILLIS = REFRESH_TOKEN_DURATION.toMillis();
  private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant EXPIRED_AT = Instant.parse("2000-01-01T00:00:00Z");
  private static final Instant VALID_AT = Instant.parse("2099-01-01T00:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtils jwtUtils;
  @Mock private Clock clock;
  @Mock private EmailVerificationService emailVerificationService;
  @Captor private ArgumentCaptor<User> userCaptor;
  @Captor private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

  @InjectMocks private AuthService authService;

  @Nested
  @DisplayName("회원가입")
  class SignUpTests {

    @Test
    @DisplayName("비활성 사용자 생성 및 검증 메일 전송")
    void signUp_success_createsUserAndSendsVerification() {
      // Given: 신규 가입 요청
      SignUpRequestDto request = new SignUpRequestDto(EMAIL, RAW_PASS, NAME);

      given(userRepository.existsByEmail(EMAIL)).willReturn(false);
      given(passwordEncoder.encode(request.password())).willReturn("encoded");
      given(userRepository.save(any(User.class)))
          .willAnswer(
              inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
              });

      // When: 회원가입 호출
      UserResponseDto response = authService.signUp(request);

      // Then: 비활성 사용자 생성 및 검증 메일 전송
      then(userRepository).should().save(userCaptor.capture());
      User saved = userCaptor.getValue();
      assertThat(saved.getEmail()).isEqualTo(EMAIL);
      assertThat(saved.getPassword()).isEqualTo("encoded");
      assertThat(saved.getName()).isEqualTo(NAME);
      assertThat(saved.getRole()).isEqualTo(User.Role.USER);
      assertThat(saved.getEnabled()).isFalse();
      assertThat(saved.getAuthProvider()).isEqualTo(User.AuthProvider.EMAIL);

      assertThat(response.email()).isEqualTo(EMAIL);
      assertThat(response.name()).isEqualTo(NAME);
      assertThat(response.role()).isEqualTo(User.Role.USER.name());

      then(emailVerificationService).should().sendVerification(saved);
    }

    @Test
    @DisplayName("이메일 중복 예외")
    void signUp_emailExists_throws() {
      // Given: 이미 존재하는 이메일
      SignUpRequestDto request = new SignUpRequestDto(DUP_EMAIL, RAW_PASS, NAME);
      given(userRepository.existsByEmail(DUP_EMAIL)).willReturn(true);

      // When: 회원가입 호출
      assertThatThrownBy(() -> authService.signUp(request))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("이미 가입된 이메일");

      // Then: 저장/메일 전송 없음
      then(userRepository).should(never()).save(any());
      then(emailVerificationService).should(never()).sendVerification(any());
    }
  }

  @Nested
  @DisplayName("로그인")
  class LogInTests {

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("login_badCredentials_cases")
    @DisplayName("자격 증명 실패 예외")
    void logIn_badCredentials_throws(
        String caseName, String email, Optional<User> user, boolean matches) {
      // Given: 사용자 조회/비밀번호 검증 결과
      LogInRequestDto request = new LogInRequestDto(email, RAW_PASS);
      given(userRepository.findByEmail(request.email())).willReturn(user);
      if (user.isPresent()) {
        given(passwordEncoder.matches(request.password(), user.get().getPassword()))
            .willReturn(matches);
      }

      // When: 로그인 호출
      assertThatThrownBy(() -> authService.logIn(request))
          .isInstanceOf(BadCredentialsException.class);

      // Then: 실패 시 토큰/저장 부작용 없음
      then(jwtUtils).shouldHaveNoInteractions();
      then(refreshTokenRepository).should(never()).revokeAllByUser(any());
      then(refreshTokenRepository).should(never()).save(any());
      then(userRepository).should(never()).save(any());
    }

    static List<Arguments> login_badCredentials_cases() {
      return List.of(
          Arguments.of("사용자 없음", "missing@example.com", Optional.empty(), false),
          Arguments.of(
              "비밀번호 불일치",
              "user@example.com",
              Optional.of(buildUser("user@example.com", "encoded", true)),
              false));
    }

    @Test
    @DisplayName("비활성 계정 예외")
    void logIn_disabled_throwsIllegalState() {
      // Given: 비활성 계정
      User user = user("disabled@example.com", "encoded", false);
      LogInRequestDto request = new LogInRequestDto(user.getEmail(), RAW_PASS);
      given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
      given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);

      // When: 로그인 호출
      assertThatThrownBy(() -> authService.logIn(request))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("비활성화된 계정");

      // Then: 실패 시 토큰/저장 부작용 없음
      then(jwtUtils).shouldHaveNoInteractions();
      then(refreshTokenRepository).should(never()).revokeAllByUser(any());
      then(refreshTokenRepository).should(never()).save(any());
      then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("토큰 발급 및 마지막 로그인 시각 갱신")
    void logIn_success_generatesTokensAndUpdatesUser() {
      // Given: 정상 로그인
      User user = user("user@example.com", "encoded", true);
      LogInRequestDto request = new LogInRequestDto(user.getEmail(), RAW_PASS);

      given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
      given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);
      given(jwtUtils.generateAccessToken(user)).willReturn(ACCESS_TOKEN);
      given(jwtUtils.generateRefreshToken(user)).willReturn(REFRESH_TOKEN);
      given(jwtUtils.getAccessTokenExpiration()).willReturn(ACCESS_EXPIRES_IN_SECONDS);
      given(jwtUtils.getRefreshTokenExpirationMillis()).willReturn(REFRESH_EXPIRES_IN_MILLIS);
      given(clock.instant()).willReturn(FIXED_NOW);
      given(refreshTokenRepository.save(any(RefreshToken.class)))
          .willAnswer(invocation -> invocation.getArgument(0));
      assertThat(user.getLastLoginAt()).isNull();

      // When: 로그인 호출
      AuthResponseDto response = authService.logIn(request);

      // Then: 토큰 발급 및 마지막 로그인 시각 갱신
      then(refreshTokenRepository).should().revokeAllByUser(user);

      then(refreshTokenRepository).should().save(refreshTokenCaptor.capture());
      RefreshToken savedToken = refreshTokenCaptor.getValue();
      assertThat(savedToken.getUser()).isEqualTo(user);
      assertThat(savedToken.getToken()).isEqualTo(REFRESH_TOKEN);
      assertThat(savedToken.getExpiresAt()).isAfter(FIXED_NOW);
      assertThat(savedToken.getExpiresAt())
          .isAfter(FIXED_NOW.plusSeconds(ACCESS_EXPIRES_IN_SECONDS));
      assertThat(savedToken.getExpiresAt())
          .isBeforeOrEqualTo(FIXED_NOW.plusMillis(REFRESH_EXPIRES_IN_MILLIS));

      assertThat(user.getLastLoginAt()).isEqualTo(FIXED_NOW);

      assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
      assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(ACCESS_EXPIRES_IN_SECONDS);
    }
  }

  @Nested
  @DisplayName("토큰 갱신")
  class RefreshTests {

    @Test
    @DisplayName("토큰 없음 예외 (400 유지)")
    void refresh_tokenNotFound_throws() {
      // Given: 존재하지 않는 리프레시 토큰
      given(refreshTokenRepository.findByToken("missing")).willReturn(Optional.empty());

      // When: 토큰 갱신 호출
      assertThatThrownBy(() -> authService.refresh("missing"))
          .isInstanceOfSatisfying(
              ApiException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                assertThat(ex.getMessage()).contains("Invalid refresh token");
              });
    }

    @Test
    @DisplayName("취소된 토큰 예외 (400 유지)")
    void refresh_revoked_throws() {
      // Given: 취소된 리프레시 토큰
      RefreshToken token = new RefreshToken();
      token.setToken("revoked");
      token.setRevoked(true);
      token.setExpiresAt(VALID_AT);
      given(refreshTokenRepository.findByToken("revoked")).willReturn(Optional.of(token));

      // When: 토큰 갱신 호출
      assertThatThrownBy(() -> authService.refresh("revoked"))
          .isInstanceOfSatisfying(
              ApiException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                assertThat(ex.getMessage()).contains("Invalid refresh token");
              });
    }

    @Test
    @DisplayName("만료된 토큰 예외 (400 유지)")
    void refresh_expired_throws() {
      // Given: 만료된 리프레시 토큰
      RefreshToken token = new RefreshToken();
      token.setToken("expired");
      token.setRevoked(false);
      token.setExpiresAt(EXPIRED_AT);
      given(refreshTokenRepository.findByToken("expired")).willReturn(Optional.of(token));

      // When: 토큰 갱신 호출
      assertThatThrownBy(() -> authService.refresh("expired"))
          .isInstanceOfSatisfying(
              ApiException.class,
              ex -> {
                assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
                assertThat(ex.getMessage()).contains("Invalid refresh token");
              });
    }

    @Test
    @DisplayName("정상 토큰 갱신")
    void refresh_success_returnsAccessToken() {
      // Given: 정상 리프레시 토큰
      User user = user("user@example.com", "encoded", true);
      RefreshToken token =
          refreshTokenBuilder().token("ok").user(user).revoked(false).expiresAt(VALID_AT).build();

      given(refreshTokenRepository.findByToken("ok")).willReturn(Optional.of(token));
      given(jwtUtils.generateAccessToken(user)).willReturn("new-access");
      given(jwtUtils.getAccessTokenExpiration()).willReturn(ACCESS_EXPIRES_IN_SECONDS);

      // When: 토큰 갱신 호출
      TokenResponseDto response = authService.refresh("ok");

      // Then: 새 액세스 토큰 반환
      assertThat(response.accessToken()).isEqualTo("new-access");
      assertThat(response.tokenType()).isEqualTo("Bearer");
      assertThat(response.expiresIn()).isEqualTo(ACCESS_EXPIRES_IN_SECONDS);
    }
  }

  @Nested
  @DisplayName("로그아웃")
  class LogOutTests {

    @Test
    @DisplayName("토큰 없음 예외")
    void logOut_tokenNotFound_throws() {
      // Given: 존재하지 않는 리프레시 토큰
      given(refreshTokenRepository.findByToken("missing")).willReturn(Optional.empty());

      // When: 로그아웃 호출
      assertThatThrownBy(() -> authService.logOut("missing"))
          .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("토큰 취소 처리")
    void logOut_success_revokesToken() {
      // Given: 정상 리프레시 토큰
      RefreshToken token = refreshTokenBuilder().token("ok").revoked(false).build();
      given(refreshTokenRepository.findByToken("ok")).willReturn(Optional.of(token));

      // When: 로그아웃 호출
      authService.logOut("ok");

      // Then: 토큰 취소 처리
      assertThat(token.getRevoked()).isTrue();
      then(refreshTokenRepository).should().save(token);
    }
  }

  @Nested
  @DisplayName("현재 사용자 조회")
  class GetCurrentUserTests {

    @Test
    @DisplayName("사용자 없음 예외")
    void getCurrentUser_notFound_throws() {
      // Given: 존재하지 않는 사용자
      UUID userId = UUID.randomUUID();
      given(userRepository.findById(userId)).willReturn(Optional.empty());

      // When: 현재 사용자 조회
      assertThatThrownBy(() -> authService.getCurrentUser(userId))
          .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("사용자 응답 반환")
    void getCurrentUser_success() {
      // Given: 정상 사용자
      User user = user("user@example.com", "encoded", true);
      given(userRepository.findById(user.getId())).willReturn(Optional.of(user));

      // When: 현재 사용자 조회
      UserResponseDto response = authService.getCurrentUser(user.getId());

      // Then: 사용자 응답 반환
      assertThat(response.id()).isEqualTo(user.getId());
      assertThat(response.email()).isEqualTo(user.getEmail());
      assertThat(response.name()).isEqualTo(user.getName());
    }
  }

  private User user(String email, String password, boolean enabled) {
    return buildUser(email, password, enabled);
  }

  private static UserBuilder userBuilder() {
    return new UserBuilder();
  }

  private static User buildUser(String email, String password, boolean enabled) {
    return userBuilder().email(email).password(password).enabled(enabled).build();
  }

  private static RefreshTokenBuilder refreshTokenBuilder() {
    return new RefreshTokenBuilder();
  }

  private static class UserBuilder {
    private UUID id = UUID.randomUUID();
    private String email = EMAIL;
    private String password = RAW_PASS;
    private String name = NAME;
    private User.Role role = User.Role.USER;
    private boolean enabled = true;

    private UserBuilder email(String email) {
      this.email = email;
      return this;
    }

    private UserBuilder password(String password) {
      this.password = password;
      return this;
    }

    private UserBuilder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    private User build() {
      User user = new User();
      user.setId(id);
      user.setEmail(email);
      user.setPassword(password);
      user.setName(name);
      user.setRole(role);
      user.setEnabled(enabled);
      return user;
    }
  }

  private static class RefreshTokenBuilder {
    private UUID id = UUID.randomUUID();
    private User user;
    private String tokenValue = REFRESH_TOKEN;
    private Instant expiresAt = VALID_AT;
    private boolean revoked = false;

    private RefreshTokenBuilder user(User user) {
      this.user = user;
      return this;
    }

    private RefreshTokenBuilder token(String token) {
      this.tokenValue = token;
      return this;
    }

    private RefreshTokenBuilder expiresAt(Instant expiresAt) {
      this.expiresAt = expiresAt;
      return this;
    }

    private RefreshTokenBuilder revoked(boolean revoked) {
      this.revoked = revoked;
      return this;
    }

    private RefreshToken build() {
      RefreshToken token = new RefreshToken();
      token.setId(id);
      token.setUser(user);
      token.setToken(tokenValue);
      token.setExpiresAt(expiresAt);
      token.setRevoked(revoked);
      return token;
    }
  }
}
