package com.beachcheck.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.beachcheck.dto.congestion.CongestionCurrentResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("CongestionClient 단위 테스트")
class CongestionClientTest {

  private static final String BASE_URL = "https://example.com";
  private static final String BEACH_CODE = "GYEONGPO";

  @Test
  @DisplayName("정상 응답을 역직렬화해 반환한다")
  void fetchCurrent_returnsResponseBody() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    CongestionClient client = new CongestionClient(BASE_URL, builder, new NoOpRequestInterceptor());

    server
        .expect(requestTo(BASE_URL + "/congestion/current?beach_id=" + BEACH_CODE))
        .andExpect(method(GET))
        .andRespond(
            withSuccess(
                """
                {
                  "beach_id": "GYEONGPO",
                  "beach_name": "Gyeongpo Beach",
                  "input": {
                    "timestamp": "2026-03-10T00:00:00Z",
                    "weather": {
                      "temp_c": 21.5,
                      "rain_mm": 0.0,
                      "wind_mps": 3.2
                    },
                    "is_weekend_or_holiday": false
                  },
                  "rule_based": {
                    "score_raw": 0.42,
                    "score_pct": 42.0,
                    "level": "normal",
                    "model_version": "rule-v1"
                  },
                  "ai": {
                    "score_raw": 0.55,
                    "score_pct": 55.0,
                    "level": "busy",
                    "model_version": "ai-v2"
                  }
                }
                """,
                MediaType.APPLICATION_JSON));

    CongestionCurrentResponse response = client.fetchCurrent(BEACH_CODE);

    assertThat(response).isNotNull();
    assertThat(response.beachId()).isEqualTo(BEACH_CODE);
    assertThat(response.beachName()).isEqualTo("Gyeongpo Beach");
    assertThat(response.input()).isNotNull();
    assertThat(response.input().weather().tempC()).isEqualTo(21.5);
    assertThat(response.ruleBased().level()).isEqualTo("normal");
    assertThat(response.ai().modelVersion()).isEqualTo("ai-v2");
    server.verify();
  }

  @Test
  @DisplayName("500 응답이 발생하면 null fallback을 반환한다")
  void fetchCurrent_returnsNullWhenInternalServerErrorOccurs() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    CongestionClient client = new CongestionClient(BASE_URL, builder, new NoOpRequestInterceptor());

    server
        .expect(requestTo(BASE_URL + "/congestion/current?beach_id=" + BEACH_CODE))
        .andExpect(method(GET))
        .andRespond(withServerError());

    CongestionCurrentResponse response = client.fetchCurrent(BEACH_CODE);

    assertThat(response).isNull();
    server.verify();
  }
}
