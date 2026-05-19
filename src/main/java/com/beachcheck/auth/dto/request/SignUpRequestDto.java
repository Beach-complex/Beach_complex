package com.beachcheck.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// TODO(OAuth): OAuth 가입 요청 DTO 및 입력 검증 정책 분리 필요.
public record SignUpRequestDto(
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
    @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message =
                "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character")
        String password,
    @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name) {

  /**
   * Why: ADR-009 PII 마스킹 — record 기본 toString이 평문 password/email/name을 노출하는 것을 차단.
   *
   * <p>Contract(Output): 필드 값을 포함하지 않는 상수 문자열만 반환.
   */
  @Override
  public String toString() {
    return "SignUpRequestDto[***masked***]";
  }
}
