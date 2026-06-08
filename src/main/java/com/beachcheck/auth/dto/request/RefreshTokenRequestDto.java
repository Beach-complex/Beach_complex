package com.beachcheck.auth.dto.request;

import com.beachcheck.global.util.MaskingUtils;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
    @NotBlank(message = "Refresh token is required") String refreshToken) {

  /**
   * Why: PII 마스킹 — record 기본 toString이 raw refreshToken을 노출하는 것을 차단.
   *
   * <p>Contract(Output): 필드 값을 포함하지 않는 상수 문자열만 반환.
   */
  @Override
  public String toString() {
    return MaskingUtils.maskedRecord(getClass().getSimpleName());
  }
}
