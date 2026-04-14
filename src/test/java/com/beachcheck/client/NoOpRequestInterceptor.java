package com.beachcheck.client;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;

/** Why: 테스트 환경에서 SigV4 서명 없이 RestClient를 생성하기 위한 Null Object 구현체. */
class NoOpRequestInterceptor implements CongestionInterceptor {

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    return execution.execute(request, body);
  }
}
