package com.beachcheck.repository;

import com.beachcheck.domain.OutboxEvent;
import com.beachcheck.domain.OutboxEvent.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  /**
   * 폴링 대상 이벤트 조회 (PENDING 또는 FAILED_RETRIABLE이고 재시도 시간 도달)
   *
   * <p>Why: FOR UPDATE SKIP LOCKED로 다중 워커 환경에서 동일 이벤트 중복 처리를 방지한다. 각 워커는 다른 워커가 이미 선점한 이벤트를 건너뛰고,
   * 잠기지 않은 이벤트만 조회하여 병렬 처리를 가능하게 한다.
   *
   * <p>Policy: PESSIMISTIC_WRITE로 조회된 row를 잠그고, lock.timeout=-2로 SKIP LOCKED를 활성화한다. (Hibernate 6+:
   * -2 = SKIP LOCKED)
   *
   * @param now 현재 시간
   * @param pageable 페이징 정보 (pageSize만큼 조회)
   * @return 처리 대상 이벤트 목록 (잠기지 않은 이벤트만, createdAt 오름차순 · 동일 시 id 오름차순 정렬)
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
  @Query(
      """
      SELECT e FROM OutboxEvent e
      WHERE e.status IN ('PENDING', 'FAILED_RETRIABLE')
        AND COALESCE(e.nextRetryAt, e.createdAt) <= :now
      ORDER BY e.createdAt ASC, e.id ASC
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
