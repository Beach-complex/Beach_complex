package com.beachcheck.client;

import com.beachcheck.dto.congestion.CongestionCurrentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class CongestionClient {

  private static final Logger log = LoggerFactory.getLogger(CongestionClient.class);
  private final RestClient restClient;

  @Autowired
  public CongestionClient(
      @Value("${app.congestion.base-url}") String baseUrl,
      RestClient.Builder builder,
      CongestionInterceptor sigV4Interceptor,
      @Qualifier("congestionClientRequestFactory") ClientHttpRequestFactory requestFactory) {
    this(baseUrl, builder, sigV4Interceptor, requestFactory, true);
  }

  CongestionClient(
      String baseUrl, RestClient.Builder builder, CongestionInterceptor sigV4Interceptor) {
    this(baseUrl, builder, sigV4Interceptor, null, false);
  }

  private CongestionClient(
      String baseUrl,
      RestClient.Builder builder,
      CongestionInterceptor sigV4Interceptor,
      ClientHttpRequestFactory requestFactory,
      boolean useCustomRequestFactory) {
    RestClient.Builder clientBuilder =
        builder
            .baseUrl(baseUrl)
            .requestInterceptors(interceptors -> interceptors.add(sigV4Interceptor));
    if (useCustomRequestFactory) {
      clientBuilder.requestFactory(requestFactory);
    }
    this.restClient = clientBuilder.build();
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
      // SdkClientException(자격증명 오류 등)은 의도적으로 잡지 않는다.
      // 자격증명 문제는 모든 해변이 동일하게 실패하는 환경 문제이므로,
      // 첫 해변에서 스케줄러를 중단하는 hard-fail이 정책이다.
      log.warn("혼잡도 조회 실패 - beachCode={}", beachCode, ex);
      return null;
    }
  }
}
