package com.beachcheck.fixture;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;

public final class UserFavoriteTestFixtures {

  private UserFavoriteTestFixtures() {}

  public static UserFavorite createFavorite(User user, Beach beach) {
    return new UserFavorite(user, beach);
  }
}
