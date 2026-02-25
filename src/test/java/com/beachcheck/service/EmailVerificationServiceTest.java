package com.beachcheck.service;

import static com.beachcheck.fixture.EmailVerificationTestFixtures.cooldownWindowToken;
import static com.beachcheck.fixture.EmailVerificationTestFixtures.expiredToken;
import static com.beachcheck.fixture.EmailVerificationTestFixtures.stubEmailUser;
import static com.beachcheck.fixture.EmailVerificationTestFixtures.usedToken;
import static com.beachcheck.fixture.EmailVerificationTestFixtures.validToken;
import static com.beachcheck.util.HashUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.beachcheck.domain.EmailVerificationToken;
import com.beachcheck.repository.EmailVerificationTokenRepository;
import com.beachcheck.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
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
  private static final long EXP_TOLERANCE_MINUTES = 1L;
  private static final long COOLDOWN_MINUTES = 3L;
  private static final long VALID_TOKEN_LIFETIME_SECONDS = 60L;
  private static final long EXPIRED_TOKEN_SECONDS_AGO = 1L;
  private static final long RECENT_TOKEN_EXPIRES_IN_MINUTES = 10L;
  private static final long RECENT_TOKEN_CREATED_MINUTES_AGO = 1L;
  private static final long EXP_MIN_LOWER_BOUND = EXP_MINUTES - EXP_TOLERANCE_MINUTES;
  private static final long EXP_MIN_UPPER_BOUND = EXP_MINUTES;

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
      var user = stubEmailUser(USER_EMAIL, false);

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
      assertThat(savedToken.getToken()).isEqualTo(sha256Hex(rawToken));

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
      assertThat(hashedTokenCaptor.getValue()).isEqualTo(sha256Hex(RAW_TOKEN));
      assertNoUserOrTokenSaveOperations();
    }

    @Test
    @DisplayName("이미 사용된 토큰이면 예외")
    void verifyToken_used_throws() {
      EmailVerificationToken token =
          usedToken(stubEmailUser(USER_EMAIL, false), RAW_TOKEN, VALID_TOKEN_LIFETIME_SECONDS);
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
          expiredToken(stubEmailUser(USER_EMAIL, false), RAW_TOKEN, EXPIRED_TOKEN_SECONDS_AGO);
      given(tokenRepository.findByToken(any(String.class))).willReturn(Optional.of(token));

      assertThatThrownBy(() -> service.verifyToken(RAW_TOKEN))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("만료된 인증 토큰");

      assertNoUserOrTokenSaveOperations();
    }

    @Test
    @DisplayName("유효 토큰이면 사용자 활성화 및 토큰 사용 처리")
    void verifyToken_success_enablesUserAndMarksTokenUsed() {
      var user = stubEmailUser(USER_EMAIL, false);
      EmailVerificationToken token = validToken(user, RAW_TOKEN, VALID_TOKEN_LIFETIME_SECONDS);
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
      var user = stubEmailUser(USER_EMAIL, true);
      given(userRepository.findByEmail(USER_EMAIL)).willReturn(Optional.of(user));

      service.resendVerification(USER_EMAIL);

      then(tokenRepository).shouldHaveNoInteractions();
      then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("쿨다운 위반 시 예외")
    void resendVerification_cooldownViolation_throws() {
      var user = stubEmailUser(USER_EMAIL, false);
      EmailVerificationToken last =
          cooldownWindowToken(
              user, OLD_TOKEN, RECENT_TOKEN_EXPIRES_IN_MINUTES, RECENT_TOKEN_CREATED_MINUTES_AGO);

      given(userRepository.findByEmail(USER_EMAIL)).willReturn(Optional.of(user));
      given(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()))
          .willReturn(Optional.of(last));

      assertThatThrownBy(() -> service.resendVerification(USER_EMAIL))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("최근에 발송");

      then(tokenRepository)
          .should(never())
          .markAllUnusedAsUsed(any(UUID.class), any(Instant.class));
      assertNoUserOrTokenSaveOperations();
      then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("비활성 사용자면 쿨다운 확인 후 재전송")
    void resendVerification_success_marksOldAndPublishesEvent() {
      var user = stubEmailUser(USER_EMAIL, false);
      given(userRepository.findByEmail(USER_EMAIL)).willReturn(Optional.of(user));
      given(tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()))
          .willReturn(Optional.empty());

      service.resendVerification(USER_EMAIL);

      then(tokenRepository).should().markAllUnusedAsUsed(eq(user.getId()), any(Instant.class));
      then(tokenRepository).should().save(tokenCaptor.capture());
      then(eventPublisher).should().publishEvent(eventCaptor.capture());

      EmailVerificationToken savedToken = tokenCaptor.getValue();
      EmailVerificationEvent event = eventCaptor.getValue();
      assertThat(event.verificationLink()).startsWith(VERIFICATION_LINK_PREFIX);
      String rawToken = extractRawToken(event.verificationLink());

      assertThat(event.email()).isEqualTo(USER_EMAIL);
      assertThat(savedToken.getToken()).isEqualTo(sha256Hex(rawToken));
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
}
