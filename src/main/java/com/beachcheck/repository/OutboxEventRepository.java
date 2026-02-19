package com.beachcheck.repository;

import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.domain.OutboxEvent.OutboxEventStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * 폴링 대상 이벤트 조회 (PENDING 또는 FAILED_RETRIABLE이고 재시도 시간 도달)
   *
   * @param now 현재 시간
   * @param pageable 페이징 정보
   * @return 처리 대상 이벤트 목록 (createdAt 오름차순 정렬)
   */
  @Query(
      """
      SELECT e FROM OutboxEvent e
      WHERE e.status IN ('PENDING', 'FAILED_RETRIABLE')
        AND e.nextRetryAt <= :now
      ORDER BY e.createdAt ASC
      """)
  List<OutboxEvent> findPendingEvents(@Param("now") Instant now, Pageable pageable);

  /**
   * 상태별 이벤트 개수 조회 (모니터링용)
   *
   * @param status 이벤트 상태
   * @return 해당 상태의 이벤트 개수
   */
  long countByStatus(OutboxEventStatus status);
}
