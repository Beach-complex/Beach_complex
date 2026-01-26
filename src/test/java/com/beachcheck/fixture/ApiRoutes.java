package com.beachcheck.fixture;

public final class ApiRoutes {

  private ApiRoutes() {}

  public static final String BEACH_RESERVATIONS = "/api/beaches/{beachId}/reservations";
  public static final String BEACH_RESERVATION =
      "/api/beaches/{beachId}/reservations/{reservationId}";
  public static final String MY_RESERVATIONS = "/api/beaches/reservations";
}
