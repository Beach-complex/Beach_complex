package com.beachcheck.repository;

import com.beachcheck.domain.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationToken, UUID> {

  Optional<EmailVerificationToken> findByToken(String token);

  Optional<EmailVerificationToken> findTopByUserIdOrderByCreatedAtDesc(UUID userId);

  @Modifying
  @Query(
      "update EmailVerificationToken t set t.usedAt = :usedAt "
          + "where t.user.id = :userId and t.usedAt is null")
  int markAllUnusedAsUsed(@Param("userId") UUID userId, @Param("usedAt") Instant usedAt);
}
