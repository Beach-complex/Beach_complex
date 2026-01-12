package com.beachcheck.repository;

import com.beachcheck.domain.Reservation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
  List<Reservation> findByUserId(UUID userId);

  Optional<Reservation> findByIdAndUserIdAndBeachId(UUID id, UUID userId, UUID beachId);

  /**
   * Why: 중복 체크의 호출 지점을 단일 메서드로 고정해 규칙 변이를 방지하기 위해. Policy: 존재 여부는 파생 쿼리 메서드 네이밍 규약을 따른다.
   * Contract(Input): userId, beachId, reservedAt을 전달한다. Contract(Output): true면 해당 조합의 예약이 존재한다.
   */
  boolean existsByUserIdAndBeachIdAndReservedAt(UUID userId, UUID beachId, Instant reservedAt);
}
