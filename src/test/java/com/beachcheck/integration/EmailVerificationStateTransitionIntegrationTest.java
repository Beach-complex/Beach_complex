package com.beachcheck.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.base.IntegrationTest;
import com.beachcheck.domain.EmailVerificationToken;
import com.beachcheck.domain.User;
import com.beachcheck.repository.EmailVerificationTokenRepository;
import com.beachcheck.repository.UserRepository;
import com.beachcheck.service.EmailVerificationService;
import com.beachcheck.util.HashUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.email-verification.resend-cooldown-minutes=0")
class EmailVerificationStateTransitionIntegrationTest extends IntegrationTest {

  private static final long NON_EXPIRED_TOKEN_DAYS = 1L;

  @Autowired private EmailVerificationService emailVerificationService;
  @Autowired private UserRepository userRepository;
  @Autowired private EmailVerificationTokenRepository tokenRepository;

  @Test
  @DisplayName("유효 토큰 검증 시 사용자 활성화되고 토큰은 사용 처리된다")
  void verifyToken_validToken_updatesUserAndToken() {
    // given
    User user = saveDisabledUser("verify-" + UUID.randomUUID() + "@test.com");

    String rawToken = "raw-token-" + UUID.randomUUID();
    String hashed = HashUtils.sha256Hex(rawToken);
    EmailVerificationToken token =
        new EmailVerificationToken(
            user, hashed, Instant.now().plus(NON_EXPIRED_TOKEN_DAYS, ChronoUnit.DAYS));
    tokenRepository.save(token);
    entityManager.flush();
    entityManager.clear();

    // when
    emailVerificationService.verifyToken(rawToken);
    entityManager.flush();
    entityManager.clear();

    User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
    EmailVerificationToken usedToken = tokenRepository.findByToken(hashed).orElseThrow();

    // then
    assertThat(verifiedUser.getEnabled()).isTrue();
    assertThat(usedToken.getUsedAt()).isNotNull();
  }

  @Test
  @DisplayName("재전송 시 기존 미사용 토큰은 사용 처리되고 신규 토큰이 생성된다")
  void resendVerification_marksOldTokenUsedAndCreatesNewToken() {
    // given
    User user = saveDisabledUser("resend-" + UUID.randomUUID() + "@test.com");

    String oldRawToken = "old-token-" + UUID.randomUUID();
    String oldHashedToken = HashUtils.sha256Hex(oldRawToken);
    EmailVerificationToken oldToken =
        new EmailVerificationToken(
            user, oldHashedToken, Instant.now().plus(NON_EXPIRED_TOKEN_DAYS, ChronoUnit.DAYS));
    tokenRepository.save(oldToken);
    entityManager.flush();
    entityManager.clear();

    // when
    emailVerificationService.resendVerification(user.getEmail());
    entityManager.flush();
    entityManager.clear();

    EmailVerificationToken previousToken =
        tokenRepository.findByToken(oldHashedToken).orElseThrow();
    EmailVerificationToken reissuedToken =
        tokenRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElseThrow();

    // then
    assertThat(previousToken.getToken()).isEqualTo(oldHashedToken);
    assertThat(previousToken.getUsedAt()).isNotNull();

    assertThat(reissuedToken.getId()).isNotEqualTo(previousToken.getId());
    assertThat(reissuedToken.getToken()).isNotEqualTo(oldHashedToken);
    assertThat(reissuedToken.getUsedAt()).isNull();
  }

  private User saveDisabledUser(String email) {
    User user = new User();
    user.setEmail(email);
    user.setName("verification-state-tester");
    user.setPassword("encoded-password");
    user.setEnabled(false);
    user.setRole(User.Role.USER);
    user.setAuthProvider(User.AuthProvider.EMAIL);
    return userRepository.save(user);
  }
}
