package com.beachcheck.repository;

import com.beachcheck.domain.RefreshToken;
import com.beachcheck.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByToken(String token);

  @Modifying
  @Query("UPDATE RefreshToken rt SET rt.revoked = true Where rt.user = :user")
  void revokeAllByUser(@Param("user") User user);

  @Modifying
  @Query("DELETE RefreshToken rt WHERE rt.expiresAt < CURRENT_TIMESTAMP ")
  void deleteExpiredTokens();
}
