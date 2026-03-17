package com.beachcheck.domain;

import static com.beachcheck.fixture.UserTestFixtures.createUser;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EmailVerificationToken 단위 테스트")
class EmailVerificationTokenTest {

  private static final long EXPIRATION_MINUTES = 30L;

  @Test
  @DisplayName("issue로 발급한 토큰은 미사용 상태이고 만료 시각이 설정된다")
  void issue_createsUnusedTokenWithExpiration() {
    User user = createUser("token-user@test.com");
    Instant beforeIssue = Instant.now();

    EmailVerificationToken token =
        EmailVerificationToken.issue(user, "hashed-token", EXPIRATION_MINUTES);

    Instant afterIssue = Instant.now();

    assertThat(token.getUser()).isEqualTo(user);
    assertThat(token.getToken()).isEqualTo("hashed-token");
    assertThat(token.isUsed()).isFalse();
    assertThat(token.getUsedAt()).isNull();
    assertThat(token.getExpiresAt())
        .isBetween(
            beforeIssue.plusSeconds(EXPIRATION_MINUTES * 60),
            afterIssue.plusSeconds(EXPIRATION_MINUTES * 60));
  }

  @Test
  @DisplayName("만료 시각이 현재보다 이전이면 expired 상태다")
  void isExpired_returnsTrueWhenExpirationIsPast() {
    EmailVerificationToken token =
        EmailVerificationToken.of(
            createUser("expired@test.com"), "hashed-token", Instant.now().minusSeconds(1));

    assertThat(token.isExpired()).isTrue();
  }

  @Test
  @DisplayName("만료 시각이 현재보다 이후면 expired 상태가 아니다")
  void isExpired_returnsFalseWhenExpirationIsFuture() {
    EmailVerificationToken token =
        EmailVerificationToken.of(
            createUser("valid@test.com"), "hashed-token", Instant.now().plusSeconds(60));

    assertThat(token.isExpired()).isFalse();
  }

  @Test
  @DisplayName("createdAt을 주입한 토큰은 전달한 생성 시각을 유지한다")
  void of_withCreatedAt_preservesInjectedCreatedAt() {
    Instant createdAt = Instant.now().minusSeconds(180);
    EmailVerificationToken token =
        EmailVerificationToken.of(
            createUser("cooldown@test.com"),
            "hashed-token",
            Instant.now().plusSeconds(60),
            createdAt);

    assertThat(token.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  @DisplayName("markUsed 호출 전후로 사용 상태가 전이된다")
  void markUsed_transitionsTokenToUsed() {
    EmailVerificationToken token =
        EmailVerificationToken.of(
            createUser("used@test.com"), "hashed-token", Instant.now().plusSeconds(60));
    Instant beforeMarkUsed = Instant.now();

    assertThat(token.isUsed()).isFalse();

    token.markUsed();

    Instant afterMarkUsed = Instant.now();
    assertThat(token.isUsed()).isTrue();
    assertThat(token.getUsedAt()).isBetween(beforeMarkUsed, afterMarkUsed);
  }
}
