package com.beachcheck.dto.auth.response;

import com.beachcheck.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponseDto(
    UUID id, String email, String name, String role, Instant createdAt, Instant lastLoginAt) {
  public static UserResponseDto from(User user) {
    return new UserResponseDto(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getRole().name(),
        user.getCreatedAt(),
        user.getLastLoginAt());
  }
}
