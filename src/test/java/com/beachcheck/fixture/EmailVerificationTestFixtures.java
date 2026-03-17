package com.beachcheck.fixture;

import com.beachcheck.domain.EmailVerificationToken;
import com.beachcheck.domain.User;
import com.beachcheck.util.HashUtils;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class EmailVerificationTestFixtures {

  private EmailVerificationTestFixtures() {}

  public static User emailUser(String email, boolean enabled) {
    User user = new User();
    user.setEmail(email);
    user.setName("tester");
    user.setPassword("encoded");
    user.setEnabled(enabled);
    user.setRole(User.Role.USER);
    user.setAuthProvider(User.AuthProvider.EMAIL);
    return user;
  }

  public static User stubEmailUser(String email, boolean enabled) {
    User user = emailUser(email, enabled);
    user.setId(UUID.randomUUID());
    return user;
  }

  public static EmailVerificationToken validToken(
      User user, String rawToken, long lifetimeSeconds) {
    return EmailVerificationToken.of(
        user, HashUtils.sha256Hex(rawToken), Instant.now().plusSeconds(lifetimeSeconds));
  }

  public static EmailVerificationToken expiredToken(User user, String rawToken, long secondsAgo) {
    return EmailVerificationToken.of(
        user, HashUtils.sha256Hex(rawToken), Instant.now().minusSeconds(secondsAgo));
  }

  public static EmailVerificationToken usedToken(User user, String rawToken, long lifetimeSeconds) {
    EmailVerificationToken token = validToken(user, rawToken, lifetimeSeconds);
    token.markUsed();
    return token;
  }

  public static EmailVerificationToken cooldownWindowToken(
      User user, String rawToken, long expiresInMinutes, long createdMinutesAgo) {
    return EmailVerificationToken.of(
        user,
        HashUtils.sha256Hex(rawToken),
        Instant.now().plus(expiresInMinutes, ChronoUnit.MINUTES),
        Instant.now().minus(createdMinutesAgo, ChronoUnit.MINUTES));
  }
}
