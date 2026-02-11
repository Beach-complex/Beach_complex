package com.beachcheck.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.beachcheck.domain.EmailVerificationToken;
import com.beachcheck.domain.User;
import com.beachcheck.repository.EmailVerificationTokenRepository;
import com.beachcheck.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("이메일 인증 서비스 단위 테스트")
class EmailVerificationServiceTest {

  private static final String BASE_URL = "https://example.com/verify";
  private static final String VERIFICATION_LINK_PREFIX = BASE_URL + "?token=";
  private static final String USER_EMAIL = "user@test.com";
  private static final String MISSING_EMAIL = "missing@test.com";
  private static final String RAW_TOKEN = "raw-token";
  private static final String OLD_TOKEN = "old-token";
  private static final long EXP_MINUTES = 30L;
  private static final long COOLDOWN_MINUTES = 3L;
  private static final long VALID_TOKEN_LIFETIME_SECONDS = 60L;
  private static final long EXPIRED_TOKEN_SECONDS_AGO = 1L;
  private static final long RECENT_TOKEN_EXPIRES_IN_MINUTES = 10L;
  private static final long RECENT_TOKEN_CREATED_MINUTES_AGO = 1L;
  private static final long EXP_MIN_LOWER_BOUND = 29L;
  private static final long EXP_MIN_UPPER_BOUND = 30L;

  @Mock private AsyncEmailService asyncEmailService;
  @Mock private EmailVerificationTokenRepository tokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Captor private ArgumentCaptor<EmailVerificationToken> tokenCaptor;
  @Captor private ArgumentCaptor<EmailVerificationEvent> eventCaptor;
  @Captor private ArgumentCaptor<String> hashedTokenCaptor;

  private EmailVerificationService service;

  @BeforeEach
  void setUp() {
    service =
        new EmailVerificationService(
            asyncEmailService,
            tokenRepository,
            userRepository,
            eventPublisher,
            BASE_URL,
            EXP_MINUTES,
            COOLDOWN_MINUTES);
  }

  @Nested
  @DisplayName("인증 메일 발송")
  class SendVerificationTests {

    @Test
    @DisplayName("토큰 저장 후 이벤트 발행")
    void sendVerification_success_savesTokenAndPublishesEvent() {
      User user = user(USER_EMAIL, false);

      service.sendVerification(user);

      then(tokenRepository).should().save(tokenCaptor.capture());
      then(eventPublisher).should().publishEvent(eventCaptor.capture());

      EmailVerificationToken savedToken = tokenCaptor.getValue();
      EmailVerificationEvent event = eventCaptor.getValue();
      assertThat(event.email()).isEqualTo(USER_EMAIL);
      assertThat(event.verificationLink()).startsWith(VERIFICATION_LINK_PREFIX);

      String rawToken = extractRawToken(event.verificationLink());
      assertThat(rawToken).isNotBlank();
      assertThat(savedToken.getUser()).isEqualTo(user);
      assertThat(savedToken.getToken()).isEqualTo(sha256(rawToken));

      long diffMinutes = Duration.between(Instant.now(), savedToken.getExpiresAt()).toMinutes();
      assertThat(diffMinutes).isBetween(EXP_MIN_LOWER_BOUND, EXP_MIN_UPPER_BOUND);
    }
  }

  @Nested
  @DisplayName("토큰 검증")
  class VerifyTokenTests {

    @Test
    @DisplayName("없는 토큰이면 예외")
    void verifyToken_notFound_throws() {
      given(tokenRepository.findByToken(any(String.class))).willReturn(Optional.empty());

      assertThatThrownBy(() -> service.verifyToken(RAW_TOKEN))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("유효 하지 않은 인증 토큰");

      then(tokenRepository).should().findByToken(hashedTokenCaptor.capture());
      assertThat(hashedTokenCaptor.getValue()).isEqualTo(sha256(RAW_TOKEN));
      assertNoUserOrTokenSaveOperations();
    }

    @Test
    @DisplayName("이미 사용된 토큰이면 예외")
    void verifyToken_used_throws() {
      EmailVerificationToken token =
          new EmailVerificationToken(
              user(USER_EMAIL, false), sha256(RAW_TOKEN), Instant.now().plusSeconds(VALID_TOKEN_LIFETIME_SECONDS));
      token.markUsed();
      given(tokenRepository.findByToken(any(String.class))).willReturn(Optional.of(token));

      assertThatThrownBy(() -> service.verifyToken(RAW_TOKEN))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("이미 사용된 인증 토큰");

      assertNoUserOrTokenSaveOperations();
    }

    @Test
    @DisplayName("만료된 토큰이면 예외")
    void verifyToken_expired_throws() {
      EmailVerificationToken token =
          new EmailVerificationToken(
              user(USER_EMAIL, false), sha256(RAW_TOKEN), Instant.now().minusSeconds(EXPIRED_TOKEN_SECONDS_AGO));
      given(tokenRepository.findByToken(any(String.class))).willReturn(Optional.of(token));

      assertThatThrownBy(() -> service.verifyToken(RAW_TOKEN))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("만료된 인증 토큰");

      assertNoUserOrTokenSaveOperations();
    }

    @Test
    @DisplayName("유효 토큰이면 사용자 활성화 및 토큰 사용 처리")
    void verifyToken_success_enablesUserAndMarksTokenUsed() {
      User user = user(USER_EMAIL, false);
      EmailVerificationToken token =
          new EmailVerificationToken(
              user, sha256(RAW_TOKEN), Instant.now().plusSeconds(VALID_TOKEN_LIFETIME_SECONDS));
      given(tokenRepository.findByToken(any(String.class))).willReturn(Optional.of(token));

      service.verifyToken(RAW_TOKEN);

      assertThat(user.getEnabled()).isTrue();
      assertThat(token.isUsed()).isTrue();
      then(userRepository).should().save(user);
      then(tokenRepository).should().save(token);
    }
  }

  @Nested
  @DisplayName("인증 메일 재전송")
  class ResendVerificationTests {

    @Test
    @DisplayName("사용자가 없으면 무동작")
    void resendVerification_userNotFound_noOp() {
      given(userRepository.findByEmail(MISSING_EMAIL)).willReturn(Optional.empty());

      service.resendVerification(MISSING_EMAIL);

      then(tokenRepository).shouldHaveNoInteractions();
      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 활성화된 사용자는 무동작")
    void resendVerification_enabledUser_noOp() {
      User user = user(USER_EMAIL, true);
      given(userRepository.findByEmail(USER_EMAIL)).willReturn(Optional.of(user));

      service.resendVerification(USER_EMAIL);

      then(tokenRepository).shouldHaveNoInteractions();
      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("쿨다운 위반 시 예외")
    void resendVerification_cooldownViolation_throws() {
      User user = user(USER_EMAIL, false);
      EmailVerificationToken last =
          new EmailVerificationToken(
              user, sha256(OLD_TOKEN), Instant.now().plus(RECENT_TOKEN_EXPIRES_IN_MINUTES, ChronoUnit.MINUTES));
      // createdAt setter가 없어, 쿨다운(최근 생성) 상황을 테스트에서만 강제로 구성한다.
      ReflectionTestUtils.setField(
          last, "createdAt", Instant.now().minus(RECENT_TOKEN_CREATED_MINUTES_AGO, ChronoUnit.MINUTES));

      given(userRepository.findByEmail(USER_EMAIL)).willReturn(Optional.of(user));
      given(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()))
          .willReturn(Optional.of(last));

      assertThatThrownBy(() -> service.resendVerification(USER_EMAIL))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("최근에 발송");

      then(tokenRepository).should(never()).markAllUnusedAsUsed(any(UUID.class), any(Instant.class));
      assertNoUserOrTokenSaveOperations();
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("비활성 사용자면 쿨다운 확인 후 재전송")
    void resendVerification_success_marksOldAndPublishesEvent() {
      User user = user(USER_EMAIL, false);
      given(userRepository.findByEmail(USER_EMAIL)).willReturn(Optional.of(user));
      given(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId())).willReturn(Optional.empty());

      service.resendVerification(USER_EMAIL);

      then(tokenRepository).should().markAllUnusedAsUsed(eq(user.getId()), any(Instant.class));
      then(tokenRepository).should().save(tokenCaptor.capture());
      then(eventPublisher).should().publishEvent(eventCaptor.capture());

      EmailVerificationToken savedToken = tokenCaptor.getValue();
      EmailVerificationEvent event = eventCaptor.getValue();
      assertThat(event.verificationLink()).startsWith(VERIFICATION_LINK_PREFIX);
      String rawToken = extractRawToken(event.verificationLink());

      assertThat(event.email()).isEqualTo(USER_EMAIL);
      assertThat(savedToken.getToken()).isEqualTo(sha256(rawToken));
      assertThat(savedToken.getUser()).isEqualTo(user);
    }
  }

  private void assertNoUserOrTokenSaveOperations() {
    // 실패 경로에서는 save 부작용이 없어야 한다(조회/검증 호출은 허용).
    then(userRepository).should(never()).save(any());
    then(tokenRepository).should(never()).save(any(EmailVerificationToken.class));
  }

  private String extractRawToken(String verificationLink) {
    // 호출 전에 startsWith(prefix)로 링크 형식을 검증한 뒤 사용한다.
    return verificationLink.substring(VERIFICATION_LINK_PREFIX.length());
  }

  private User user(String email, boolean enabled) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail(email);
    user.setName("tester");
    user.setPassword("encoded");
    user.setEnabled(enabled);
    user.setRole(User.Role.USER);
    user.setAuthProvider(User.AuthProvider.EMAIL);
    return user;
  }

  private String sha256(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
