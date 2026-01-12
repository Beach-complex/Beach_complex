package com.beachcheck.dto.auth.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LogInRequestDto(
    @NotBlank(message = "Email is required")
    @Email(message = "Invaild email format")
    String email,

    @NotBlank(message = "Password is required")
    String password
) { }
