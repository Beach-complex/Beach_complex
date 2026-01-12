package com.beachcheck.dto.beach;

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
    Double longitude) {}
