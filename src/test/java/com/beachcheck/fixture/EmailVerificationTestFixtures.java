package com.beachcheck.fixture;

import com.beachcheck.domain.EmailVerificationToken;
import com.beachcheck.domain.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

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
        user, sha256Hex(rawToken), Instant.now().plusSeconds(lifetimeSeconds));
  }

  public static EmailVerificationToken expiredToken(User user, String rawToken, long secondsAgo) {
    return EmailVerificationToken.of(
        user, sha256Hex(rawToken), Instant.now().minusSeconds(secondsAgo));
  }

  public static EmailVerificationToken usedToken(User user, String rawToken, long lifetimeSeconds) {
    EmailVerificationToken token = validToken(user, rawToken, lifetimeSeconds);
    token.markUsed();
    return token;
  }

  public static EmailVerificationToken cooldownWindowToken(
      User user, String rawToken, long expiresInMinutes, long createdMinutesAgo) {
    EmailVerificationToken token =
        EmailVerificationToken.of(
            user, sha256Hex(rawToken), Instant.now().plus(expiresInMinutes, ChronoUnit.MINUTES));
    // createdAt has no setter by design; set it in tests to emulate cooldown window.
    ReflectionTestUtils.setField(
        token, "createdAt", Instant.now().minus(createdMinutesAgo, ChronoUnit.MINUTES));
    return token;
  }

  public static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashed);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
