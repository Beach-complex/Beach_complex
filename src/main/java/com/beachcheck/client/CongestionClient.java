package com.beachcheck.client;

import com.beachcheck.dto.congestion.CongestionCurrentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CongestionClient {

  private static final Logger log = LoggerFactory.getLogger(CongestionClient.class);
  private final RestClient restClient;

  public CongestionClient(
      @Value("${app.congestion.base-url}") String baseUrl, RestClient.Builder builder) {
    this.restClient = builder.baseUrl(baseUrl).build();
  }

  public CongestionCurrentResponse fetchCurrent(String beachCode) {
    try {
      return restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder.path("/congestion/current").queryParam("beach_id", beachCode).build())
          .retrieve()
          .body(CongestionCurrentResponse.class);
    } catch (RestClientException ex) {
      log.warn("Failed to fetch congestion for beachCode={}", beachCode, ex);
      return null;
    }
  }
}
