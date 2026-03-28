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
      @Value("${app.congestion.base-url}") String baseUrl,
      RestClient.Builder builder,
      CongestionInterceptor sigV4Interceptor) {
    this.restClient =
        builder
            .baseUrl(baseUrl)
            .requestInterceptors(interceptors -> interceptors.add(sigV4Interceptor))
            .build();
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
      log.warn("혼잡도 조회 실패 - beachCode={}", beachCode, ex);
      return null;
    }
  }
}
