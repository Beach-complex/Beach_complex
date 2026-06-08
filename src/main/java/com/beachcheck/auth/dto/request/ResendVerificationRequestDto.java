package com.beachcheck.auth.dto.request;

import com.beachcheck.global.util.MaskingUtils;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequestDto(
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
        String email) {

  /**
   * Why: PII 마스킹 — record 기본 toString이 평문 email을 노출하는 것을 차단.
   *
   * <p>Contract(Output): 필드 값을 포함하지 않는 상수 문자열만 반환.
   */
  @Override
  public String toString() {
    return MaskingUtils.maskedRecord(getClass().getSimpleName());
  }
}
