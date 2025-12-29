package com.beachcheck.dto.beach;

import com.beachcheck.domain.Beach;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

public record BeachDto(
        UUID id,
        String code,
        String name,
        String status,
        double latitude,
        double longitude,
        Instant updatedAt,
        String tag,
        Boolean isFavorite
) {
    // 엔티티 -> DTO 변환용 정적 메서드
    public static BeachDto from(Beach beach, boolean isFavorite) {
        double lat = 0.0;
        double lon = 0.0;

        if (beach.getLocation() != null) {
            // WGS84(Point): X=경도(lon), Y=위도(lat)
            Point p = beach.getLocation();
            lon = p.getX();
            lat = p.getY();
        }

        return new BeachDto(
                beach.getId(),
                beach.getCode(),
                beach.getName(),
                beach.getStatus(),
                lat,
                lon,
                beach.getUpdatedAt(),
                beach.getTag(),
                isFavorite
        );
    }


}
