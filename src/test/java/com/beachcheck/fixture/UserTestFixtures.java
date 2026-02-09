package com.beachcheck.fixture;

import static com.beachcheck.domain.User.Role.USER;
import static java.util.UUID.randomUUID;

import com.beachcheck.domain.User;
import org.springframework.security.crypto.password.PasswordEncoder;

public final class UserTestFixtures {

  private UserTestFixtures() {}

  public static User createUser() {
    return createUser("test@example.com");
  }

  public static User createUser(String email) {
    return createUser(email, "Test User");
  }

  public static User createUser(String email, String name) {
    User user = new User();
    user.setId(randomUUID());
    user.setEmail(email);
    user.setName(name);
    user.setRole(USER);
    user.setPassword("encoded_password");
    user.setEnabled(true);
    return user;
  }

  public static User createEmailLoginUser(
      String email, String name, String rawPassword, PasswordEncoder passwordEncoder) {
    User user = createUser(email, name);
    user.setPassword(passwordEncoder.encode(rawPassword));
    user.setAuthProvider(User.AuthProvider.EMAIL);
    user.setEnabled(true);
    return user;
  }
}
