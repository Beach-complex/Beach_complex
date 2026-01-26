package com.beachcheck.dto.beach;

import com.beachcheck.domain.BeachCondition;
import com.beachcheck.util.GeometryUtils;
import java.time.Instant;
import java.util.UUID;

public record BeachConditionDto(
    UUID id,
    UUID beachId,
    Instant observedAt,
    Double waterTemperatureCelsius,
    Double waveHeightMeters,
    String weatherSummary,
    Double latitude,
    Double longitude) {
  public static BeachConditionDto from(BeachCondition condition) {
    return new BeachConditionDto(
        condition.getId(),
        condition.getBeach().getId(),
        condition.getObservedAt(),
        condition.getWaterTemperatureCelsius(),
        condition.getWaveHeightMeters(),
        condition.getWeatherSummary(),
        GeometryUtils.extractLatitude(condition.getObservationPoint()),
        GeometryUtils.extractLongitude(condition.getObservationPoint()));
  }
}
