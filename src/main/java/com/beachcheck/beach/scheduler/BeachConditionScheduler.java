package com.beachcheck.beach.scheduler;

import com.beachcheck.beach.domain.Beach;
import com.beachcheck.beach.domain.BeachCondition;
import com.beachcheck.beach.repository.BeachConditionRepository;
import com.beachcheck.beach.repository.BeachRepository;
import com.beachcheck.external.congestion.CongestionClient;
import com.beachcheck.external.congestion.CongestionCurrentResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
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
  private static final String JOB_SPAN_NAME = "scheduler.beach-condition.refresh";
  private static final String ITEM_SPAN_NAME = "scheduler.beach-condition.item";
  private static final String SCHEDULER_NAME = "beachConditionRefresh";
  private static final String OUTCOME_ATTRIBUTE = "scheduler.job.outcome";
  private static final String ITEM_OUTCOME_ATTRIBUTE = "scheduler.item.outcome";
  private static final String ITEM_SKIP_REASON_ATTRIBUTE = "scheduler.item.skip.reason";
  private static final String SUCCESS = "success";
  private static final String SKIPPED = "skipped";
  private static final String ERROR = "error";
  private static final String MISSING_CODE = "missing_code";
  private static final String NO_RESPONSE = "no_response";
  private static final String SAFE_JOB_ERROR = "Beach condition refresh failed";
  private static final String SAFE_ITEM_ERROR = "Beach condition item processing failed";

  private final BeachRepository beachRepository;
  private final BeachConditionRepository beachConditionRepository;
  private final CongestionClient congestionClient;
  private final Clock clock;
  private final Tracer tracer;
  private final String mode;

  public BeachConditionScheduler(
      BeachRepository beachRepository,
      BeachConditionRepository beachConditionRepository,
      CongestionClient congestionClient,
      Clock clock,
      Tracer tracer,
      @Value("${app.congestion.mode:ai}") String mode) {
    this.beachRepository = beachRepository;
    this.beachConditionRepository = beachConditionRepository;
    this.congestionClient = congestionClient;
    this.clock = clock;
    this.tracer = tracer;
    this.mode = mode;
  }

  @Scheduled(cron = "0 0/30 * * * *")
  public void refreshConditions() {
    String jobId = UUID.randomUUID().toString();
    Span jobSpan =
        tracer
            .spanBuilder()
            .setNoParent()
            .name(JOB_SPAN_NAME)
            .tag("scheduler.name", SCHEDULER_NAME)
            .start();
    try {
      // MDC.clear()는 모든 키를 삭제하기 때문에, 아래와 같은 방식으로 필요한 키만 제거하도록 구현
      // schedulerName과 job은 직접 쓰지는 않지만, 리소스 생명주기를 try-with-resources에 맡기기 위해 선언한 변수
      try (MDC.MDCCloseable schedulerName = MDC.putCloseable("schedulerName", SCHEDULER_NAME);
          MDC.MDCCloseable job = MDC.putCloseable("jobId", jobId);
          Tracer.SpanInScope ignored = tracer.withSpan(jobSpan)) {
        refreshConditionsInScope(jobSpan);
        jobSpan.tag(OUTCOME_ATTRIBUTE, SUCCESS);
      }
    } catch (RuntimeException | Error exception) {
      markJobError(jobSpan);
      throw exception;
    } finally {
      jobSpan.end();
    }
  }

  private void refreshConditionsInScope(Span jobSpan) {
    log.info("예약된 해변 조건 새로고침 시작");

    List<Beach> beaches = beachRepository.findAll();
    for (Beach beach : beaches) {
      refreshBeach(beach, jobSpan);
    }
  }

  private void refreshBeach(Beach beach, Span jobSpan) {
    Span itemSpan = tracer.spanBuilder().setParent(jobSpan.context()).name(ITEM_SPAN_NAME).start();
    try (Tracer.SpanInScope ignored = tracer.withSpan(itemSpan)) {
      String code = beach.getCode();
      if (code == null || code.isBlank()) {
        log.warn("코드가 없는 해변을 건너뜁니다.");
        itemSpan.tag(ITEM_OUTCOME_ATTRIBUTE, SKIPPED);
        itemSpan.tag(ITEM_SKIP_REASON_ATTRIBUTE, MISSING_CODE);
        return;
      }

      CongestionCurrentResponse response = congestionClient.fetchCurrent(code);
      if (response == null) {
        itemSpan.tag(ITEM_OUTCOME_ATTRIBUTE, SKIPPED);
        itemSpan.tag(ITEM_SKIP_REASON_ATTRIBUTE, NO_RESPONSE);
        return;
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
      itemSpan.tag(ITEM_OUTCOME_ATTRIBUTE, SUCCESS);
    } catch (RuntimeException | Error exception) {
      markItemError(itemSpan);
      throw exception;
    } finally {
      itemSpan.end();
    }
  }

  private void markJobError(Span span) {
    span.tag(OUTCOME_ATTRIBUTE, ERROR);
    span.error(new IllegalStateException(SAFE_JOB_ERROR));
  }

  private void markItemError(Span span) {
    span.tag(ITEM_OUTCOME_ATTRIBUTE, ERROR);
    span.error(new IllegalStateException(SAFE_ITEM_ERROR));
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
