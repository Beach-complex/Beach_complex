package com.beachcheck.db;

public final class DBConstraints {
  private DBConstraints() {}

  // TODO(OAuth): OAuth 연동을 위한 provider/provider_user_id 유니크 제약(예: uk_user_provider_subject) 추가 시
  // 상수화.
  public static final String UK_USER_BEACH = "uk_user_beach";
  public static final String UK_RESERVATION_USER_BEACH_TIME = "uk_reservation_user_beach_time";
}
