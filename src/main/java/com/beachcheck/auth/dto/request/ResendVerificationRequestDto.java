package com.beachcheck.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequestDto(
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
        String email) {}
