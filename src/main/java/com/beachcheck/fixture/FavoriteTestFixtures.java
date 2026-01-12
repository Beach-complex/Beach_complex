package com.beachcheck.fixture;

import static com.beachcheck.domain.User.Role.*;
import static java.util.UUID.randomUUID;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;
import java.util.UUID;

import static com.beachcheck.domain.User.Role.USER;
import static java.util.UUID.randomUUID;

public class FavoriteTestFixtures {

  public static User createUser() {
    return createUser("test@example.com");
  }

  public static User createUser(String email) {
    User user = new User();
    user.setId(randomUUID());
    user.setEmail(email);
    user.setName("Test User");
    user.setRole(USER);
    user.setPassword("password");
    user.setEnabled(true);
    return user;
  }

  public static Beach createBeach(UUID id) {
    return createBeach(id, "해운대");
  }

  public static Beach createBeach(UUID id, String name) {
    Beach beach = new Beach();
    beach.setId(id);
    beach.setName(name);
    return beach;
  }

  public static UserFavorite createFavorite(User user, Beach beach) {
    return new UserFavorite(user, beach);
  }
}
