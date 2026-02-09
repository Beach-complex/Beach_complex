package com.beachcheck.fixture;

public final class ApiRoutes {

  private ApiRoutes() {}

  public static final String BEACH_RESERVATIONS = "/api/beaches/{beachId}/reservations";
  public static final String BEACH_RESERVATION =
      "/api/beaches/{beachId}/reservations/{reservationId}";
  public static final String MY_RESERVATIONS = "/api/beaches/reservations";

  public static final String BEACHES = "/api/beaches";
  public static final String BEACH_CONDITIONS_RECENT = "/api/beaches/{beachId}/conditions/recent";
  public static final String BEACH_FACILITIES = "/api/beaches/{beachId}/facilities";

  public static final String AUTH_LOGIN = "/api/auth/login";
  public static final String AUTH_REFRESH = "/api/auth/refresh";
  public static final String AUTH_ME = "/api/auth/me";
}
