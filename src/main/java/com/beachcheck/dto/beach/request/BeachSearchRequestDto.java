package com.beachcheck.dto.beach.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

public record BeachSearchRequestDto(
        String q,
        String tag,

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        Double lat,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        Double lon,

        @Positive(message = "Radius must be positive")
        Double radiusKm
) {
    // 반경 검색에 필요한 모든 파라미터가 있는지 검증
    public boolean hasCompleteRadiusParams() {
        return lat != null && lon != null && radiusKm != null;
    }

    public boolean hasAnyRadiusParam() {
        return lat != null || lon != null || radiusKm != null;
    }

    // 반경 검색 요청 여부 검증 (부분 파라미터 거부)
    public void validateRadiusParams() {
        if (hasAnyRadiusParam() && !hasCompleteRadiusParams()) {
            throw new IllegalArgumentException(
                    "Radius search requires all three parameters: lat, lon, radiusKm"
            );
        }
    }
}
