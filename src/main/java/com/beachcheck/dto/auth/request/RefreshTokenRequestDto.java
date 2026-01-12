package com.beachcheck.dto.auth.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
    @NotBlank(message = "Refresh token is required") String refreshToken) {}
