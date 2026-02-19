package com.beachcheck.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;

public final class ApiErrorTestFixtures {

  private ApiErrorTestFixtures() {}

  public static ResultMatcher problemDetailStatus(ObjectMapper objectMapper, int expectedStatus) {
    return result -> {
      assertProblemDetailContentType(result);
      JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
      assertThat(json.path("status").asInt()).isEqualTo(expectedStatus);
    };
  }

  public static ResultMatcher problemDetail(
      ObjectMapper objectMapper, int expectedStatus, String expectedTitle, String expectedCode) {
    return result -> {
      assertProblemDetailContentType(result);
      JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
      assertThat(json.path("status").asInt()).isEqualTo(expectedStatus);
      assertThat(json.path("title").asText()).isEqualTo(expectedTitle);
      assertThat(json.path("code").asText()).isEqualTo(expectedCode);
    };
  }

  private static void assertProblemDetailContentType(MvcResult result) {
    String contentType = result.getResponse().getContentType();
    if (contentType == null
        || !MediaType.parseMediaType(contentType)
            .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)) {
      throw new AssertionError("Unexpected content type: " + contentType);
    }
  }
}
