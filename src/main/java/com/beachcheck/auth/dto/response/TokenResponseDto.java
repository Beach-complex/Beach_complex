package com.beachcheck.auth.dto.response;

import com.beachcheck.global.util.MaskingUtils;

public record TokenResponseDto(String accessToken, String tokenType, long expiresIn) {
  public static TokenResponseDto of(String accessToken, long expiresIn) {
    return new TokenResponseDto(accessToken, "Bearer", expiresIn);
  }

  /**
   * Why: PII 마스킹 — record 기본 toString이 raw accessToken을 노출하는 것을 차단.
   *
   * <p>Contract(Output): accessToken은 가리되 디버깅에 필요한 tokenType/expiresIn은 유지.
   */
  @Override
  public String toString() {
    return "TokenResponseDto["
        + MaskingUtils.maskedField("accessToken")
        + ", tokenType="
        + tokenType
        + ", expiresIn="
        + expiresIn
        + "]";
  }
}
