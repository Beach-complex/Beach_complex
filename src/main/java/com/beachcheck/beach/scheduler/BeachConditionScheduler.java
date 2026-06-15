package com.beachcheck.beach.scheduler;

import com.beachcheck.beach.domain.Beach;
import com.beachcheck.beach.domain.BeachCondition;
import com.beachcheck.beach.repository.BeachConditionRepository;
import com.beachcheck.beach.repository.BeachRepository;
import com.beachcheck.external.congestion.CongestionClient;
import com.beachcheck.external.congestion.CongestionCurrentResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BeachConditionScheduler {

  private static final Logger log = LoggerFactory.getLogger(BeachConditionScheduler.class);

  private final BeachRepository beachRepository;
  private final BeachConditionRepository beachConditionRepository;
  private final CongestionClient congestionClient;
  private final Clock clock;
  private final String mode;

  public BeachConditionScheduler(
      BeachRepository beachRepository,
      BeachConditionRepository beachConditionRepository,
      CongestionClient congestionClient,
      Clock clock,
      @Value("${app.congestion.mode:ai}") String mode) {
    this.beachRepository = beachRepository;
    this.beachConditionRepository = beachConditionRepository;
    this.congestionClient = congestionClient;
    this.clock = clock;
    this.mode = mode;
  }

  @Scheduled(cron = "0 0/30 * * * *")
  public void refreshConditions() {

    // MDC.clear()는 모든 키를 삭제하기 때문에, 아래와 같은 방식으로 필요한 키만 제거하도록 구현
    // schedulerName과 job은 직접 쓰지는 않지만, 리소스 생명주기를 try-with-resources에 맡기기 위해 선언한 변수
    String jobId = UUID.randomUUID().toString();
    try (MDC.MDCCloseable schedulerName =
            MDC.putCloseable("schedulerName", "beachConditionRefresh");
        MDC.MDCCloseable job = MDC.putCloseable("jobId", jobId)) {
      refreshConditionsInScope();
    }
  }

  private void refreshConditionsInScope() {
    log.info("예약된 해변 조건 새로고침 시작");

    List<Beach> beaches = beachRepository.findAll();
    for (Beach beach : beaches) {
      String code = beach.getCode();
      if (code == null || code.isBlank()) {
        log.warn("코드가 없는 해변을 건너뜁니다. beachId={}", beach.getId());
        continue;
      }

      CongestionCurrentResponse response = congestionClient.fetchCurrent(code);
      if (response == null) {
        continue;
      }

      Instant observedAt = Instant.now(clock);
      Double tempC = null;
      Double rainMm = null;
      Double windMps = null;

      if (response.input() != null) {
        if (response.input().timestamp() != null) {
          observedAt = response.input().timestamp();
        }
        if (response.input().weather() != null) {
          tempC = response.input().weather().tempC();
          rainMm = response.input().weather().rainMm();
          windMps = response.input().weather().windMps();
        }
      }

      BeachCondition condition = new BeachCondition();
      condition.setBeach(beach);
      condition.setObservedAt(observedAt);
      condition.setWaterTemperatureCelsius(tempC);
      condition.setWaveHeightMeters(null);
      condition.setWeatherSummary(formatWeatherSummary(tempC, rainMm, windMps));
      condition.setObservationPoint(beach.getLocation());
      beachConditionRepository.save(condition);

      String level = resolveLevel(response);
      String status = mapStatus(level);
      if (status != null && !status.equalsIgnoreCase(beach.getStatus())) {
        beach.setStatus(status);
        beachRepository.save(beach);
      }
    }
  }

  private String resolveLevel(CongestionCurrentResponse response) {
    if ("rule_based".equalsIgnoreCase(mode) || "rule-based".equalsIgnoreCase(mode)) {
      return response.ruleBased() != null ? response.ruleBased().level() : null;
    }
    return response.ai() != null ? response.ai().level() : null;
  }

  private String mapStatus(String level) {
    if (level == null) return null;
    return switch (level.toLowerCase()) {
      case "low" -> "free";
      case "medium" -> "normal";
      case "high" -> "busy";
      default -> null;
    };
  }

  private String formatWeatherSummary(Double tempC, Double rainMm, Double windMps) {
    String temp = tempC == null ? "n/a" : String.format("%.1fC", tempC);
    String rain = rainMm == null ? "n/a" : String.format("%.1fmm", rainMm);
    String wind = windMps == null ? "n/a" : String.format("%.1fm/s", windMps);
    return String.format("temp:%s, rain:%s, wind:%s", temp, rain, wind);
  }
}
