package com.beachcheck.support.fixture;

import com.beachcheck.beach.domain.Beach;
import com.beachcheck.user.domain.User;
import com.beachcheck.user.domain.UserFavorite;

public final class UserFavoriteTestFixtures {

  private UserFavoriteTestFixtures() {}

  public static UserFavorite createFavorite(User user, Beach beach) {
    return new UserFavorite(user, beach);
  }
}
