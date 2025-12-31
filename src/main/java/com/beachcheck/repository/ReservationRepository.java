package com.beachcheck.repository;

import com.beachcheck.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
/**
 * Why: 예약 중복 여부를 저장소 질의로 확인해 도메인 규칙을 강제하기 위해.
 * Policy: 중복 판단은 userId+beachId+reservedAt 조합으로 수행한다.
 * Contract(Input): 중복 판단 기준 키를 사용한다.
 * Contract(Output): 중복 여부는 존재 질의로 판단한다.
 */

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByUserId(UUID userId);
    Optional<Reservation> findByIdAndUserIdAndBeachId(UUID id, UUID userId, UUID beachId);
    /**
     * Why: 중복 체크의 호출 지점을 단일 메서드로 고정해 규칙 변이를 방지하기 위해.
     * Policy: 존재 여부는 파생 쿼리 메서드 네이밍 규약을 따른다.
     * Contract(Input): userId와 beachId와 reservedAt을 전달한다.
     * Contract(Output): true면 해당 조합의 예약이 존재한다.
     */
    boolean existsByUserIdAndBeachIdAndReservedAt(UUID userId, UUID beachId, Instant reservedAt);
}
