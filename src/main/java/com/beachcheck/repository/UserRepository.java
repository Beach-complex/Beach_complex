package com.beachcheck.repository;

import com.beachcheck.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  // TODO(OAuth): OAuth 연동을 위해 provider + providerUserId 기반 조회 메서드 추가.
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);
}
