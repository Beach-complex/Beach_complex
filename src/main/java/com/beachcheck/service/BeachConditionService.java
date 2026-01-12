package com.beachcheck.service;

import com.beachcheck.domain.BeachCondition;
import com.beachcheck.dto.beach.BeachConditionDto;
import com.beachcheck.repository.BeachConditionRepository;
import com.beachcheck.util.GeometryUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BeachConditionService {

  private static final Duration DEFAULT_LOOKBACK = Duration.ofHours(24);

  private final BeachConditionRepository beachConditionRepository;

  public BeachConditionService(BeachConditionRepository beachConditionRepository) {
    this.beachConditionRepository = beachConditionRepository;
  }

  @Cacheable(value = "conditionSnapshots", key = "#beachId")
  public List<BeachConditionDto> findRecentConditions(UUID beachId) {
    Instant threshold = Instant.now().minus(DEFAULT_LOOKBACK);
    return beachConditionRepository.findByBeachIdAndObservedAtAfter(beachId, threshold).stream()
        .map(this::toDto)
        .toList();
  }

  private BeachConditionDto toDto(BeachCondition condition) {
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

  // TODO: Pull condition telemetry from IoT sensors once MQTT bridge is finalized.
}
