package com.beachcheck.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachcheck.auth.dto.request.LogInRequestDto;
import com.beachcheck.auth.dto.request.RefreshTokenRequestDto;
import com.beachcheck.auth.dto.request.ResendVerificationRequestDto;
import com.beachcheck.auth.dto.request.SignUpRequestDto;
import com.beachcheck.auth.dto.response.AuthResponseDto;
import com.beachcheck.auth.dto.response.TokenResponseDto;
import com.beachcheck.auth.dto.response.UserResponseDto;
import com.beachcheck.beach.dto.BeachConditionDto;
import com.beachcheck.beach.dto.BeachDto;
import com.beachcheck.beach.dto.BeachFacilityDto;
import com.beachcheck.beach.dto.request.BeachSearchRequestDto;
import com.beachcheck.external.congestion.CongestionCurrentResponse;
import com.beachcheck.global.exception.ApiErrorResponse;
import com.beachcheck.notification.domain.Notification.NotificationStatus;
import com.beachcheck.notification.domain.Notification.NotificationType;
import com.beachcheck.notification.dto.NotificationResponseDto;
import com.beachcheck.notification.dto.NotificationSendRequestDto;
import com.beachcheck.reservation.dto.ReservationCreateRequest;
import com.beachcheck.reservation.dto.ReservationResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("DTO 로깅 출력 카탈로그")
@ExtendWith(OutputCaptureExtension.class)
class DtoLoggingCatalogTest {

  private static final Logger log = LoggerFactory.getLogger(DtoLoggingCatalogTest.class);

  private static final String SAMPLE_EMAIL = "victim@example.com";
  private static final String SAMPLE_PASSWORD = "Password1!"; // gitleaks:allow 테스트 픽스처(실제 비밀번호 아님)
  private static final String SAMPLE_ACCESS_TOKEN = "sample-access-token-value";
  private static final String SAMPLE_REFRESH_TOKEN = "sample-refresh-token-value";
  private static final Instant SAMPLE_TIME = Instant.parse("2026-06-08T00:00:00Z");
  private static final UUID SAMPLE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID SAMPLE_BEACH_ID =
      UUID.fromString("22222222-2222-2222-2222-222222222222");

  private LoggingSystem loggingSystem;

  @AfterEach
  void cleanUp() {
    MDC.clear();
    if (loggingSystem != null) {
      loggingSystem.cleanUp();
    }
  }

  @Test
  @DisplayName("dev 프로파일 DTO 로그 샘플을 평문 파일로 남긴다")
  void devProfile_writesPlainTextDtoCatalog(CapturedOutput output) throws IOException {
    // given
    initializeLogging("dev");

    // when
    logDtoCatalog();

    // then
    writeReport("dto-catalog-dev.log", output);
    assertThat(output)
        .contains("[trace-catalog,user-catalog]")
        .contains("LogInRequestDto[****]")
        .contains("BeachSearchRequestDto")
        .doesNotContain(SAMPLE_EMAIL)
        .doesNotContain(SAMPLE_PASSWORD)
        .doesNotContain(SAMPLE_ACCESS_TOKEN)
        .doesNotContain(SAMPLE_REFRESH_TOKEN)
        .doesNotContain("\"@timestamp\"");
  }

  @Test
  @DisplayName("prod 프로파일 DTO 로그 샘플을 JSONL 파일로 남긴다")
  void prodProfile_writesJsonDtoCatalog(CapturedOutput output) throws IOException {
    // given
    initializeLogging("prod");

    // when
    logDtoCatalog();

    // then
    writeReport("dto-catalog-prod.jsonl", output);
    assertThat(output)
        .contains("\"@timestamp\"")
        .contains("\"traceId\":\"trace-catalog\"")
        .contains("\"spanId\":\"span-catalog\"")
        .contains("\"userId\":\"user-catalog\"")
        .contains("\"requestId\":\"request-catalog\"")
        .contains("LogInRequestDto[****]")
        .contains("BeachSearchRequestDto")
        .doesNotContain(SAMPLE_EMAIL)
        .doesNotContain(SAMPLE_PASSWORD)
        .doesNotContain(SAMPLE_ACCESS_TOKEN)
        .doesNotContain(SAMPLE_REFRESH_TOKEN);
  }

  private void initializeLogging(String profile) {
    loggingSystem = LoggingSystem.get(getClass().getClassLoader());
    loggingSystem.cleanUp();
    loggingSystem.beforeInitialize();

    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    loggingSystem.initialize(
        new LoggingInitializationContext(environment), "classpath:logback-spring.xml", null);
  }

  private void logDtoCatalog() {
    putCatalogMdc();
    for (Map.Entry<String, Object> dto : sampleDtos()) {
      log.info("dtoName={}, dto={}", dto.getKey(), dto.getValue());
    }
  }

  private void putCatalogMdc() {
    MDC.put("traceId", "trace-catalog");
    MDC.put("spanId", "span-catalog");
    MDC.put("userId", "user-catalog");
    MDC.put("requestId", "request-catalog");
  }

  private List<Map.Entry<String, Object>> sampleDtos() {
    UserResponseDto user =
        new UserResponseDto(SAMPLE_ID, SAMPLE_EMAIL, "홍길동VictimName", "USER", SAMPLE_TIME, null);
    TokenResponseDto token = TokenResponseDto.of(SAMPLE_ACCESS_TOKEN, 3600L);

    return List.of(
        Map.entry("LogInRequestDto", new LogInRequestDto(SAMPLE_EMAIL, SAMPLE_PASSWORD)),
        Map.entry("SignUpRequestDto", new SignUpRequestDto(SAMPLE_EMAIL, SAMPLE_PASSWORD, "홍길동")),
        Map.entry("RefreshTokenRequestDto", new RefreshTokenRequestDto(SAMPLE_REFRESH_TOKEN)),
        Map.entry("ResendVerificationRequestDto", new ResendVerificationRequestDto(SAMPLE_EMAIL)),
        Map.entry("UserResponseDto", user),
        Map.entry("TokenResponseDto", token),
        Map.entry(
            "AuthResponseDto",
            AuthResponseDto.of(SAMPLE_ACCESS_TOKEN, SAMPLE_REFRESH_TOKEN, 3600L, user)),
        Map.entry(
            "BeachDto",
            new BeachDto(
                SAMPLE_BEACH_ID,
                "BUSAN_HAEUNDAE",
                "해운대해수욕장",
                "OPEN",
                35.1587,
                129.1604,
                SAMPLE_TIME,
                "family",
                true)),
        Map.entry(
            "BeachConditionDto",
            new BeachConditionDto(
                SAMPLE_ID, SAMPLE_BEACH_ID, SAMPLE_TIME, 22.5, 0.7, "맑음", 35.1587, 129.1604)),
        Map.entry(
            "BeachFacilityDto",
            new BeachFacilityDto(SAMPLE_ID, SAMPLE_BEACH_ID, "샤워장", "SHOWER", 35.1588, 129.1605)),
        Map.entry(
            "BeachSearchRequestDto",
            new BeachSearchRequestDto("해운대", "family", 35.1587, 129.1604, 3.0)),
        Map.entry(
            "ReservationCreateRequest",
            new ReservationCreateRequest("2026-06-08T00:00:00Z", "event-123")),
        Map.entry(
            "ReservationResponse",
            new ReservationResponse(
                SAMPLE_ID, "CONFIRMED", SAMPLE_TIME, SAMPLE_BEACH_ID, "event-123", SAMPLE_TIME)),
        Map.entry(
            "NotificationSendRequestDto",
            new NotificationSendRequestDto(
                SAMPLE_ID, NotificationType.TEST, "테스트 알림", "스모크 테스트 메시지")),
        Map.entry(
            "NotificationResponseDto",
            new NotificationResponseDto(
                SAMPLE_ID,
                NotificationType.TEST,
                "테스트 알림",
                "스모크 테스트 메시지",
                NotificationStatus.SENT,
                SAMPLE_TIME,
                SAMPLE_TIME)),
        Map.entry(
            "ApiErrorResponse",
            new ApiErrorResponse("BAD_REQUEST", "요청 값이 올바르지 않습니다.", Map.of("field", "email"))),
        Map.entry("CongestionCurrentResponse", congestionResponse()));
  }

  private CongestionCurrentResponse congestionResponse() {
    return new CongestionCurrentResponse(
        "BUSAN_HAEUNDAE",
        "해운대해수욕장",
        new CongestionCurrentResponse.InputContext(
            SAMPLE_TIME, new CongestionCurrentResponse.WeatherInput(24.1, 0.0, 3.2), false),
        new CongestionCurrentResponse.OutputBlock(0.42, 42.0, "NORMAL", "rule-v1"),
        new CongestionCurrentResponse.OutputBlock(0.51, 51.0, "BUSY", "ai-v1"));
  }

  private void writeReport(String fileName, CapturedOutput output) throws IOException {
    Path reportPath = Path.of("build", "reports", "logging-smoke", fileName);
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, output.toString(), StandardCharsets.UTF_8);
  }
}
