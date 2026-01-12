package com.beachcheck.dto.auth.response;

public record TokenResponseDto(String accessToken, String tokenType, long expiresIn) {
  public static TokenResponseDto of(String accessToken, long expiresIn) {
    return new TokenResponseDto(accessToken, "Bearer", expiresIn);
  }
}
