package com.beachcheck.repository;

import com.beachcheck.domain.EmailVerificationToken;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationTokenCleanupRepository
    extends JpaRepository<EmailVerificationToken, UUID> {

  @Modifying(clearAutomatically = true)
  @Query(
      value = "delete from email_verification_tokens where user_id in (:userIds)",
      nativeQuery = true)
  int deleteAllByUserIds(@Param("userIds") Collection<UUID> userIds);
}
