package com.beachcheck.repository;

import com.beachcheck.domain.Notification;
import com.beachcheck.domain.Notification.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  /**
   * 사용자별 알림 목록 조회 (페이징)
   *
   * <p>Why: 마이페이지에서 사용자의 알림 이력을 최신순으로 조회하기 위함
   *
   * @param userId 사용자 ID
   * @param pageable 페이징 정보
   * @return 알림 페이지
   */
  Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

  /**
   * 사용자별 알림 목록 조회 (전체)
   *
   * @param userId 사용자 ID
   * @return 알림 리스트
   */
  List<Notification> findByUserId(UUID userId);

  /**
   * 상태별 알림 조회 (관리자용)
   *
   * <p>Why: 관리자가 실패한 알림을 모니터링하기 위함
   *
   * @param status 알림 상태
   * @param pageable 페이징 정보
   * @return 알림 페이지
   */
  Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

  /**
   * 특정 기간 동안 발송 실패한 알림 개수 조회
   *
   * <p>Why: 알림 시스템 모니터링 및 장애 감지
   *
   * @param status 알림 상태
   * @param after 시작 시간
   * @return 실패한 알림 개수
   */
  long countByStatusAndCreatedAtAfter(NotificationStatus status, Instant after);

  /**
   * 오래된 알림 삭제 (배치 작업용)
   *
   * <p>Why: 일정 기간이 지난 알림 이력을 삭제하여 DB 용량 관리
   *
   * @param before 기준 시간 (이전 데이터 삭제)
   */
  void deleteByCreatedAtBefore(Instant before);
}
