package com.beachcheck.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// TODO(OAuth): OAuth 로그인 요청 DTO 분리 또는 통합 시 구조 재정의.
public record LogInRequestDto(
    @NotBlank(message = "Email is required") @Email(message = "Invaild email format") String email,
    @NotBlank(message = "Password is required") String password) {

  /**
   * Why: ADR-009 PII 마스킹 — record 기본 toString이 평문 password/email을 그대로 노출하는 것을 차단.
   *
   * <p>Contract(Output): 필드 값을 포함하지 않는 상수 문자열만 반환.
   */
  @Override
  public String toString() {
    return "LogInRequestDto[***masked***]";
  }
}
