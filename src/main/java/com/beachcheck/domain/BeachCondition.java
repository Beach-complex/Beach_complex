package com.beachcheck.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "beach_conditions")
public class BeachCondition {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "beach_id", nullable = false)
  private Beach beach;

  @Column(name = "observed_at", nullable = false)
  private Instant observedAt;

  @Column(name = "water_temperature_celsius")
  private Double waterTemperatureCelsius;

  @Column(name = "wave_height_meters")
  private Double waveHeightMeters;

  @Column(name = "weather_summary", length = 512)
  private String weatherSummary;

  @JdbcTypeCode(SqlTypes.GEOMETRY)
  @Column(columnDefinition = "geometry(Point, 4326)")
  private Point observationPoint;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Beach getBeach() {
    return beach;
  }

  public void setBeach(Beach beach) {
    this.beach = beach;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  public void setObservedAt(Instant observedAt) {
    this.observedAt = observedAt;
  }

  public Double getWaterTemperatureCelsius() {
    return waterTemperatureCelsius;
  }

  public void setWaterTemperatureCelsius(Double waterTemperatureCelsius) {
    this.waterTemperatureCelsius = waterTemperatureCelsius;
  }

  public Double getWaveHeightMeters() {
    return waveHeightMeters;
  }

  public void setWaveHeightMeters(Double waveHeightMeters) {
    this.waveHeightMeters = waveHeightMeters;
  }

  public String getWeatherSummary() {
    return weatherSummary;
  }

  public void setWeatherSummary(String weatherSummary) {
    this.weatherSummary = weatherSummary;
  }

  public Point getObservationPoint() {
    return observationPoint;
  }

  public void setObservationPoint(Point observationPoint) {
    this.observationPoint = observationPoint;
  }
}
