package com.beachcheck.repository;

import com.beachcheck.domain.Beach;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeachRepository extends JpaRepository<Beach, UUID> {

  Optional<Beach> findByCode(String code);

  // @Query("""
  //   SELECT b FROM Beach b
  //   WHERE (:q IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(b.code) LIKE
  // LOWER(CONCAT('%', :q, '%')))
  //     AND (:tag IS NULL OR LOWER(b.tag) = LOWER(:tag))
  // """)

  // List<Beach> search(@Param("q") String q, @Param("tag") String tag);
  List<Beach> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(String name, String code);

  /**
   * 특정 좌표로부터 지정된 반경 내의 해변을 거리순으로 조회
   *
   * @param longitude 경도 (예: 129.1603)
   * @param latitude 위도 (예: 35.1587)
   * @param radiusMeters 반경 (미터 단위, 예: 10000 = 10km)
   * @return 거리순으로 정렬된 해변 목록
   */
  @Query(
      value =
          """
    SELECT b.*
    FROM beaches b
    WHERE ST_DWithin(
        b.location::geography,
        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography,
        :radiusMeters
    )
    ORDER BY ST_Distance(
        b.location::geography,
        ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
    )
    """,
      nativeQuery = true)
  List<Beach> findBeachesWithinRadius(
      @Param("longitude") double longitude,
      @Param("latitude") double latitude,
      @Param("radiusMeters") double radiusMeters);
}
