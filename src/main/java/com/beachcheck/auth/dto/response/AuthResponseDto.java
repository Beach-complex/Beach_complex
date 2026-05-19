package com.beachcheck.auth.dto.response;

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

  /**
   * Why: ADR-009 PII 마스킹 — record 기본 toString이 raw accessToken/refreshToken을 노출하는 것을 차단.
   *
   * <p>Contract(Output): 토큰 본문은 가리되 tokenType/expiresIn/user는 그대로 노출 (UserResponseDto는 PII가 제외된 응답
   * 전용 DTO라는 전제).
   */
  @Override
  public String toString() {
    return "AuthResponseDto[accessToken=***masked***, refreshToken=***masked***, tokenType="
        + tokenType
        + ", expiresIn="
        + expiresIn
        + ", user="
        + user
        + "]";
  }
}
