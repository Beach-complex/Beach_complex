package com.beachcheck.service;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.dto.beach.BeachDto;
import com.beachcheck.repository.BeachRepository;
//import com.beachcheck.util.GeometryUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BeachService {

    private final BeachRepository beachRepository;
    private final UserFavoriteService favoriteService;

    public BeachService(BeachRepository beachRepository, UserFavoriteService favoriteService) {
        this.beachRepository = beachRepository;
        this.favoriteService = favoriteService;
    }

  @Cacheable(value = "beachSummaries", key = "'user:' + (#user?.id ?: 'anonymous')")
  public List<BeachDto> findAll(User user) {
    return toBeachDtoList(beachRepository.findAll(), user);
  }

  private BeachDto toBeachDto(Beach beach, User user) {
    if (user != null) {
      boolean isFavorite = favoriteService.isFavorite(user, beach.getId());
      return BeachDto.from(beach, isFavorite);
    }
    return BeachDto.from(beach);
  }

    /**
     * Beach 엔티티 리스트를 BeachDto 리스트로 변환 (찜 여부 포함해서 반환)
     */
    private List<BeachDto> toBeachDtoList(List<Beach> beaches, User user) {
        if (user != null) {
            Set<UUID> favoriteIds = favoriteService.getFavoriteBeachIds(user); // Set 으로 중복 제거 및 빠른 조회
            return beaches.stream()
                    // Set.contains() 각 O(1) × N번 = O(N) (List 사용 시 O(N×M) 이므로 비효율적)
                    .map(beach -> BeachDto.from(beach, favoriteIds.contains(beach.getId())))
                    .toList();
        } else {
            return beaches.stream()
                    .map(BeachDto::from)
                    .toList();
        }
    }

  // 대소문자 구분없이 검색
  public List<BeachDto> search(String q, String tag, User user) {
    String qq = (q == null || q.isBlank()) ? null : q.trim();
    String tt = (tag == null || tag.isBlank()) ? null : tag.trim();

    List<Beach> rows;
    if (qq == null) {
      rows = beachRepository.findAll();
    } else {
      // 태그 검사는 stream단계에서 뒤늦게
      rows = beachRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(qq, qq);
    }

    if (tt != null) {
      rows =
          rows.stream().filter(b -> b.getTag() != null && b.getTag().equalsIgnoreCase(tt)).toList();
    }

    return toBeachDtoList(rows, user);
  }

  /**
   * 특정 위치로부터 반경 내 해변 검색
   *
   * @param longitude 경도
   * @param latitude 위도
   * @param radiusKm 반경 (킬로미터)
   * @param user 사용자 (찜 여부 확인용, null 가능)
   * @return 거리순 해변 목록
   */
  public List<BeachDto> findNearby(double longitude, double latitude, double radiusKm, User user) {
    // km를 미터로 변환
    double radiusMeters = radiusKm * 1000;

    List<Beach> beaches =
        beachRepository.findBeachesWithinRadius(longitude, latitude, radiusMeters);
    return toBeachDtoList(beaches, user);
  }

    /**
     * 사용자 인증 정보가 있으면 찜 여부를 포함한 Beach 목록 반환
     */
    public List<BeachDto> getBeachesWithFavorites(User user) {
        List<Beach> beaches = beachRepository.findAll();    // 조건 없음

    return toBeachDtoList(beaches, user);
  }

  // TODO: Introduce aggregation with external wave monitoring service for enriched beach summaries.
}
