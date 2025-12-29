package com.beachcheck.repository;

import com.beachcheck.domain.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UUID> {

    // 사용자별 찜 목록 조회
    List<UserFavorite> findByUserId(UUID userId);

    // 특정 사용자의 특정 해수욕장 찜 여부 확인
    boolean existsByUserIdAndBeachId(UUID userId, UUID beachId);

    // 특정 사용자의 특정 해수욕장 찜 레코드 조회
    Optional<UserFavorite> findByUserIdAndBeachId(UUID userId, UUID beachId);

    // 특정 사용자의 찜한 해수욕장 ID 목록 조회 (성능 최적화(JPQL-Projection))
    @Query("SELECT f.beach.id FROM UserFavorite f WHERE f.user.id = :userId")
    Set<UUID> findBeachIdsByUserId(@Param("userId") UUID userId);

    // 사용자의 찜 삭제
    void deleteByUserIdAndBeachId(UUID userId, UUID beachId);
}