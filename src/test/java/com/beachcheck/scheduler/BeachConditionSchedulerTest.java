package com.beachcheck.scheduler;

import static com.beachcheck.fixture.BeachTestFixtures.createBeachWithLocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.beachcheck.client.CongestionClient;
import com.beachcheck.domain.Beach;
import com.beachcheck.domain.BeachCondition;
import com.beachcheck.dto.congestion.CongestionCurrentResponse;
import com.beachcheck.repository.BeachConditionRepository;
import com.beachcheck.repository.BeachRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Point;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BeachConditionScheduler 분기 테스트")
class BeachConditionSchedulerTest {

  private static final Instant FIXED_TIMESTAMP = Instant.parse("2026-03-06T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIMESTAMP, ZoneOffset.UTC);

  @Mock private BeachRepository beachRepository;
  @Mock private BeachConditionRepository beachConditionRepository;
  @Mock private CongestionClient congestionClient;

  @Nested
  @DisplayName("refreshConditions 메서드")
  class RefreshConditions {

    @Test
    @DisplayName("TC-SCH-01: 해변 코드가 null이면 건너뛴다")
    void tcSch01_skipWhenCodeIsNull() {
      // Given
      Beach beach = beach(null, "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));

      // When
      scheduler.refreshConditions();

      // Then
      then(beachRepository).should().findAll();
      then(congestionClient).shouldHaveNoInteractions();
      then(beachConditionRepository).shouldHaveNoInteractions();
      then(beachRepository).should(never()).save(any(Beach.class));
    }

    @Test
    @DisplayName("TC-SCH-02: 해변 코드가 비어 있으면 건너뛴다")
    void tcSch02_skipWhenCodeIsBlank() {
      // Given
      Beach beach = beach("   ", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));

      // When
      scheduler.refreshConditions();

      // Then
      then(congestionClient).shouldHaveNoInteractions();
      then(beachConditionRepository).shouldHaveNoInteractions();
      then(beachRepository).should(never()).save(any(Beach.class));
    }

    @Test
    @DisplayName("TC-SCH-03: 혼잡도 클라이언트가 null을 반환하면 저장하지 않는다")
    void tcSch03_skipWhenClientReturnsNull() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE")).willReturn(null);

      // When
      scheduler.refreshConditions();

      // Then
      then(congestionClient).should().fetchCurrent("HAE");
      then(beachConditionRepository).shouldHaveNoInteractions();
      then(beachRepository).should(never()).save(any(Beach.class));
    }

    @Test
    @DisplayName("TC-SCH-04: AI low를 free 상태로 매핑하고 상태 정보를 저장한다")
    void tcSch04_mapAiLowToFree() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE")).willReturn(responseWithAiLevel("low"));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getBeach()).isSameAs(beach);
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      assertThat(savedCondition.getWaterTemperatureCelsius()).isEqualTo(21.3);
      assertThat(savedCondition.getWaveHeightMeters()).isNull();
      assertThat(savedCondition.getWeatherSummary())
          .isEqualTo("temp:21.3C, rain:0.5mm, wind:3.2m/s");
      assertPointEquals(savedCondition.getObservationPoint(), beach.getLocation());
      assertThat(beach.getStatus()).isEqualTo("free");
      then(beachRepository).should().save(beach);
    }

    @Test
    @DisplayName("TC-SCH-05: AI medium을 normal 상태로 매핑한다")
    void tcSch05_mapAiMediumToNormal() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE")).willReturn(responseWithAiLevel("medium"));

      // When
      scheduler.refreshConditions();

      // Then
      assertThat(beach.getStatus()).isEqualTo("normal");
      then(beachRepository).should().save(beach);
      then(beachConditionRepository).should().save(any(BeachCondition.class));
    }

    @Test
    @DisplayName("TC-SCH-06: AI high를 busy 상태로 매핑한다")
    void tcSch06_mapAiHighToBusy() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE")).willReturn(responseWithAiLevel("high"));

      // When
      scheduler.refreshConditions();

      // Then
      assertThat(beach.getStatus()).isEqualTo("busy");
      then(beachRepository).should().save(beach);
      then(beachConditionRepository).should().save(any(BeachCondition.class));
    }

    @Test
    @DisplayName("TC-SCH-07: mode가 rule_based면 ruleBased 값을 사용한다")
    void tcSch07_useRuleBasedLevelWhenModeIsRuleBased() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("rule_based");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE"))
          .willReturn(responseWithInput(FIXED_TIMESTAMP, 21.3, 0.5, 3.2, "low", "high"));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getBeach()).isSameAs(beach);
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      then(congestionClient).should().fetchCurrent("HAE");
      assertThat(beach.getStatus()).isEqualTo("busy");
      then(beachRepository).should().save(beach);
    }

    @Test
    @DisplayName("TC-SCH-08: mode가 rule-based면 ruleBased 값을 사용한다")
    void tcSch08_useRuleBasedLevelWhenModeIsRuleBasedHyphen() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("rule-based");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE"))
          .willReturn(responseWithInput(FIXED_TIMESTAMP, 21.3, 0.5, 3.2, "high", "medium"));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getBeach()).isSameAs(beach);
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      then(congestionClient).should().fetchCurrent("HAE");
      assertThat(beach.getStatus()).isEqualTo("normal");
      then(beachRepository).should().save(beach);
    }

    @Test
    @DisplayName("TC-SCH-09: level 정보 블록이 없으면 상태 정보만 저장한다")
    void tcSch09_persistConditionWhenLevelSourceBlockIsMissing() {
      // Given
      Beach ruleBasedBeach = beach("GWANG", "OPEN", 129.18, 35.17);
      BeachConditionScheduler scheduler = schedulerWithMode("rule_based");
      given(beachRepository.findAll()).willReturn(List.of(ruleBasedBeach));
      given(congestionClient.fetchCurrent("GWANG"))
          .willReturn(responseWithInput(FIXED_TIMESTAMP, 21.3, 0.5, 3.2, "high", null));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getBeach()).isSameAs(ruleBasedBeach);
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      assertThat(ruleBasedBeach.getStatus()).isEqualTo("OPEN");
      then(beachRepository).should(never()).save(ruleBasedBeach);
    }

    @Test
    @DisplayName("TC-SCH-09-1: AI level 정보 블록이 없으면 상태 정보만 저장한다")
    void tcSch09_1_persistConditionWhenAiLevelSourceBlockIsMissing() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE"))
          .willReturn(responseWithInput(FIXED_TIMESTAMP, 21.3, 0.5, 3.2, null, null));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      then(beachRepository).should(never()).save(any(Beach.class));
    }

    @Test
    @DisplayName("TC-SCH-10: level 문자열이 지원되지 않으면 상태 정보만 저장한다")
    void tcSch10_persistConditionWhenLevelStringIsUnsupported() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE")).willReturn(responseWithAiLevel("extreme"));

      // When
      scheduler.refreshConditions();

      // Then
      capturedCondition();
      then(beachRepository).should(never()).save(any(Beach.class));
    }

    @Test
    @DisplayName("TC-SCH-11: 매핑 결과가 기존 상태와 같으면 beach를 저장하지 않는다")
    void tcSch11_doNotSaveBeachWhenMappedStatusMatchesIgnoringCase() {
      // Given
      Beach beach = beach("HAE", "FREE", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE")).willReturn(responseWithAiLevel("low"));

      // When
      scheduler.refreshConditions();

      // Then
      assertThat(beach.getStatus()).isEqualTo("FREE");
      then(beachConditionRepository).should().save(any(BeachCondition.class));
      then(beachRepository).should(never()).save(any(Beach.class));
    }

    @Test
    @DisplayName("TC-SCH-12: 건너뜀 또는 null-response 이후 다음 beach를 계속 처리한다")
    void tcSch12_continueProcessingLaterBeaches() {
      // Given
      Beach missingCode = beach("   ", "OPEN", 129.16, 35.15);
      Beach nullResponseBeach = beach("SONG", "OPEN", 129.17, 35.16);
      Beach validBeach = beach("GWANG", "OPEN", 129.18, 35.17);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll())
          .willReturn(List.of(missingCode, nullResponseBeach, validBeach));
      given(congestionClient.fetchCurrent("SONG")).willReturn(null);
      given(congestionClient.fetchCurrent("GWANG")).willReturn(responseWithAiLevel("high"));

      // When
      scheduler.refreshConditions();

      // Then
      then(congestionClient).should().fetchCurrent("SONG");
      then(congestionClient).should().fetchCurrent("GWANG");
      then(beachConditionRepository).should().save(any(BeachCondition.class));
      assertThat(validBeach.getStatus()).isEqualTo("busy");
      then(beachRepository).should().save(validBeach);
    }

    @Test
    @DisplayName("TC-SCH-13: 입력이 null이면 observedAt 기본값과 n/a 날씨 요약을 사용한다")
    void tcSch13_fallbackObservedAtAndWeatherSummaryWhenInputIsNull() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE")).willReturn(responseWithoutInput("low", null));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      assertThat(savedCondition.getWaterTemperatureCelsius()).isNull();
      assertThat(savedCondition.getWeatherSummary()).isEqualTo("temp:n/a, rain:n/a, wind:n/a");
    }

    @Test
    @DisplayName("TC-SCH-14: timestamp가 null이고 weather가 있으면 시각만 기본값으로 채운다")
    void tcSch14_fallbackObservedAtOnlyWhenTimestampIsNull() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE"))
          .willReturn(responseWithInput(null, 21.3, 0.5, 3.2, "low", null));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      assertThat(savedCondition.getWaterTemperatureCelsius()).isEqualTo(21.3);
      assertThat(savedCondition.getWeatherSummary())
          .isEqualTo("temp:21.3C, rain:0.5mm, wind:3.2m/s");
    }

    @Test
    @DisplayName("TC-SCH-15: weather가 null이면 timestamp만 유지하고 n/a 날씨 요약을 사용한다")
    void tcSch15_preserveTimestampWhenWeatherIsNull() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("ai");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE"))
          .willReturn(
              responseWithContext(
                  new CongestionCurrentResponse.InputContext(FIXED_TIMESTAMP, null, Boolean.FALSE),
                  "low",
                  null));

      // When
      scheduler.refreshConditions();

      // Then
      BeachCondition savedCondition = capturedCondition();
      assertThat(savedCondition.getObservedAt()).isEqualTo(FIXED_TIMESTAMP);
      assertThat(savedCondition.getWeatherSummary()).isEqualTo("temp:n/a, rain:n/a, wind:n/a");
    }

    @Test
    @DisplayName("TC-SCH-16: 예상 외 mode 값이면 AI 분기를 사용한다")
    void tcSch16_defaultToAiBranchForUnexpectedModeValues() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("custom");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE"))
          .willReturn(responseWithInput(FIXED_TIMESTAMP, 21.3, 0.5, 3.2, "high", "low"));

      // When
      scheduler.refreshConditions();

      // Then
      assertThat(beach.getStatus()).isEqualTo("busy");
      then(beachRepository).should().save(beach);
    }

    @Test
    @DisplayName("TC-SCH-17: rule_based mode 비교는 대소문자를 구분하지 않는다")
    void tcSch17_compareRuleBasedModeIgnoringCase() {
      // Given
      Beach beach = beach("HAE", "OPEN", 129.16, 35.15);
      BeachConditionScheduler scheduler = schedulerWithMode("RULE_BASED");
      given(beachRepository.findAll()).willReturn(List.of(beach));
      given(congestionClient.fetchCurrent("HAE"))
          .willReturn(responseWithInput(FIXED_TIMESTAMP, 21.3, 0.5, 3.2, "low", "high"));

      // When
      scheduler.refreshConditions();

      // Then
      assertThat(beach.getStatus()).isEqualTo("busy");
      then(beachRepository).should().save(beach);
    }
  }

  private BeachConditionScheduler schedulerWithMode(String mode) {
    return new BeachConditionScheduler(
        beachRepository, beachConditionRepository, congestionClient, FIXED_CLOCK, mode);
  }

  private Beach beach(String code, String status, double longitude, double latitude) {
    Beach beach = createBeachWithLocation(code, "Beach-" + UUID.randomUUID(), longitude, latitude);
    beach.setId(UUID.randomUUID());
    beach.setStatus(status);
    return beach;
  }

  private CongestionCurrentResponse responseWithAiLevel(String level) {
    return responseWithInput(FIXED_TIMESTAMP, 21.3, 0.5, 3.2, level, null);
  }

  private CongestionCurrentResponse responseWithoutInput(String aiLevel, String ruleLevel) {
    return responseWithContext(null, aiLevel, ruleLevel);
  }

  private CongestionCurrentResponse responseWithInput(
      Instant timestamp,
      Double tempC,
      Double rainMm,
      Double windMps,
      String aiLevel,
      String ruleLevel) {
    CongestionCurrentResponse.InputContext input =
        new CongestionCurrentResponse.InputContext(
            timestamp,
            new CongestionCurrentResponse.WeatherInput(tempC, rainMm, windMps),
            Boolean.FALSE);
    return responseWithContext(input, aiLevel, ruleLevel);
  }

  private CongestionCurrentResponse responseWithContext(
      CongestionCurrentResponse.InputContext input, String aiLevel, String ruleLevel) {
    return new CongestionCurrentResponse(
        "beach-id",
        "Beach Name",
        input,
        ruleLevel == null ? null : outputBlock(ruleLevel),
        aiLevel == null ? null : outputBlock(aiLevel));
  }

  private CongestionCurrentResponse.OutputBlock outputBlock(String level) {
    return new CongestionCurrentResponse.OutputBlock(10.0, 20.0, level, "v1");
  }

  private BeachCondition capturedCondition() {
    ArgumentCaptor<BeachCondition> captor = ArgumentCaptor.forClass(BeachCondition.class);
    then(beachConditionRepository).should().save(captor.capture());
    return captor.getValue();
  }

  private void assertPointEquals(Point actual, Point expected) {
    assertThat(actual).isNotNull();
    assertThat(actual.getX()).isEqualTo(expected.getX());
    assertThat(actual.getY()).isEqualTo(expected.getY());
  }
}
