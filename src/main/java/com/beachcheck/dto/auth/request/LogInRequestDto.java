package com.beachcheck.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// TODO(OAuth): OAuth 로그인 요청 DTO 분리 또는 통합 시 구조 재정의.
public record LogInRequestDto(
    @NotBlank(message = "Email is required") @Email(message = "Invaild email format") String email,
    @NotBlank(message = "Password is required") String password) {}
