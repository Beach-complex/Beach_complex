package com.beachcheck.dto.auth.response;

public record AuthResponseDto(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserResponseDto user) {
  public static AuthResponseDto of(
      String accessToken, String refreshToken, long expiresIn, UserResponseDto user) {
    return new AuthResponseDto(accessToken, refreshToken, "Bearer", expiresIn, user);
  }
}
