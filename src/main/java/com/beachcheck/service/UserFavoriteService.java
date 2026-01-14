package com.beachcheck.service;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.domain.UserFavorite;
import com.beachcheck.repository.BeachRepository;
import com.beachcheck.repository.UserFavoriteRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserFavoriteService {

  private final UserFavoriteRepository favoriteRepository;
  private final BeachRepository beachRepository;

  public UserFavoriteService(
      UserFavoriteRepository favoriteRepository, BeachRepository beachRepository) {
    this.favoriteRepository = favoriteRepository;
    this.beachRepository = beachRepository;
  }

  /**
   * 찜 추가
   *
   * <p>Why: 사용자가 해수욕장을 찜 목록에 추가 Policy: Pre-check로 99% 중복 차단, DB UNIQUE 제약이 최종 안전망 Contract(Input):
   * user, beachId (존재하는 해수욕장) Contract(Output): 저장된 UserFavorite
   *
   * <p>동시성 처리 전략: 1. exists 체크: 대부분의 중복 요청을 빠르게 차단 (성능 최적화) 2. DB UNIQUE 제약: Race condition 발생 시 최종
   * 방어 3. GlobalExceptionHandler: 커밋 시점 DataIntegrityViolationException을 409 CONFLICT로 변환
   */
  @Transactional
  @CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")
  public UserFavorite addFavorite(User user, UUID beachId) {
    // Pre-check: 이미 찜했는지 확인 (동시 요청 대부분 차단)
    if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
      throw new IllegalStateException("이미 찜한 해수욕장입니다.");
    }

    Beach beach =
        beachRepository
            .findById(beachId)
            .orElseThrow(() -> new IllegalArgumentException("해수욕장을 찾을 수 없습니다."));

    UserFavorite favorite = new UserFavorite(user, beach);

    // save(): 배치 최적화 유지, 커밋 시점 예외는 GlobalExceptionHandler가 처리
    return favoriteRepository.save(favorite);
  }

  /** 찜 제거 */
  @Transactional
  @CacheEvict(value = "beachSummaries", key = "'user:' + #user.id")
  public void removeFavorite(User user, UUID beachId) {
    favoriteRepository.deleteByUserIdAndBeachId(user.getId(), beachId);
  }

  /** 찜 토글 (추가/제거) */
  @Transactional
  public boolean toggleFavorite(User user, UUID beachId) {
    if (favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId)) {
      removeFavorite(user, beachId);
      return false; // 제거됨
    } else {
      try {
        addFavorite(user, beachId);
        return true; // 추가됨
      } catch (IllegalStateException e) {
        // pre-check에서 중복 감지된 경우 (이미 찜한 상태로 간주)
        // 주의: DB 레벨 동시성 충돌(DataIntegrityViolationException)은 GlobalExceptionHandler에서 처리
        return true;
      }
    }
  }

  /** 사용자의 찜 목록 조회 */
  public List<Beach> getFavoriteBeaches(User user) {
    return favoriteRepository.findByUserId(user.getId()).stream()
        .map(UserFavorite::getBeach)
        .toList();
  }

  /** 사용자의 찜한 해수욕장 ID 목록 조회 (성능 최적화) */
  public Set<UUID> getFavoriteBeachIds(User user) {
    return favoriteRepository.findBeachIdsByUserId(user.getId());
  }

  /** 특정 해수욕장이 찜되어 있는지 확인 */
  public boolean isFavorite(User user, UUID beachId) {
    return favoriteRepository.existsByUserIdAndBeachId(user.getId(), beachId);
  }
}
