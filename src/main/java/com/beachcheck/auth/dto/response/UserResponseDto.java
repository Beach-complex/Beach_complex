package com.beachcheck.auth.dto.response;

import com.beachcheck.user.domain.User;
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

  /**
   * Why: ADR-009 PII 마스킹 — record 기본 toString이 email/name 평문을 노출하는 것을 차단.
   *
   * <p>Contract(Output): email/name은 가리되 추적에 유용한 id/role/createdAt/lastLoginAt은 유지.
   */
  @Override
  public String toString() {
    return "UserResponseDto[id="
        + id
        + ", email=***masked***, name=***masked***, role="
        + role
        + ", createdAt="
        + createdAt
        + ", lastLoginAt="
        + lastLoginAt
        + "]";
  }
}
