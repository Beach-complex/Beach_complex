package com.beachcheck.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendVerificationRequestDto(
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
        String email) {}
