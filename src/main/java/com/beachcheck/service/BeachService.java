package com.beachcheck.service;

import com.beachcheck.domain.Beach;
import com.beachcheck.domain.User;
import com.beachcheck.dto.beach.BeachDto;
import com.beachcheck.repository.BeachRepository;
//import com.beachcheck.util.GeometryUtils;
import jakarta.persistence.EntityNotFoundException;
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

    @Cacheable("beachSummaries")
    public List<BeachDto> findAll() {
        return beachRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public BeachDto findByCode(String code) {
        Beach beach = beachRepository.findByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("Beach with code " + code + " not found"));
        return toDto(beach);
    }

    private BeachDto toDto(Beach beach) {
        // return new BeachDto(
        //         beach.getId(),
        //         beach.getCode(),
        //         beach.getName(),
        //         beach.getStatus(),
        //         GeometryUtils.extractLatitude(beach.getLocation()),
        //         GeometryUtils.extractLongitude(beach.getLocation()),
        //         beach.getUpdatedAt()
        //         beach.getTag(),          // 없으면 null 로 바꿔도 됨
        //         Boolean.FALSE            // 찜 기능 붙이기 전까지 기본값
        // );
        return BeachDto.from(beach);
    }

    // 대소문자 구분없이 검색
    public List<BeachDto> search(String q, String tag) {
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
            rows = rows.stream()
                    .filter(b -> b.getTag() != null && b.getTag().equalsIgnoreCase(tt))
                    .toList();
        }

        return rows.stream().map(BeachDto::from).toList();
    }

    /**
     * 특정 위치로부터 반경 내 해변 검색
     *
     * @param longitude 경도
     * @param latitude 위도
     * @param radiusKm 반경 (킬로미터)
     * @return 거리순 해변 목록
     */
    public List<BeachDto> findNearby(double longitude, double latitude, double radiusKm) {
        // km를 미터로 변환
        double radiusMeters = radiusKm * 1000;

        return beachRepository.findBeachesWithinRadius(longitude, latitude, radiusMeters)
                .stream()
                .map(BeachDto::from)
                .toList();
    }

    /**
     * 사용자 인증 정보가 있으면 찜 여부를 포함한 Beach 목록 반환
     */
    public List<BeachDto> getBeachesWithFavorites(User user) {
        List<Beach> beaches = beachRepository.findAll();    // 조건 없음

        if (user != null) {
            // 사용자의 찜 목록 ID 가져오기
            Set<UUID> favoriteIds = favoriteService.getFavoriteBeachIds(user);

            // DTO 변환 시 찜 여부 포함
            return beaches.stream()
                    .map(beach -> BeachDto.from(beach, favoriteIds.contains(beach.getId())))
                    .toList();
        } else {
            // 비로그인 사용자는 모두 false
            return beaches.stream()
                    .map(BeachDto::from)
                    .toList();
        }
    }

    // TODO: Introduce aggregation with external wave monitoring service for enriched beach summaries.
}
