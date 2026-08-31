package com.beachcheck.global.tracing;

import io.micrometer.common.KeyValue;
import org.springframework.http.client.observation.ClientHttpObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.stereotype.Component;

/**
 * Why: HTTP client span의 URL query에 사용자 입력값이 수집되는 것을 방지한다.
 *
 * <p>Policy: 실제 요청 URI는 유지하고, {@code http.url} attribute에서 query만 제거한다.
 *
 * <p>Contract(Input): Spring HTTP client observation context
 *
 * <p>Contract(Output): query가 제거된 {@code http.url} attribute
 */
@Component
public class QuerylessClientRequestObservationConvention
    extends DefaultClientRequestObservationConvention {

  @Override
  protected KeyValue requestUri(ClientRequestObservationContext context) {
    if (context.getCarrier() == null) {
      return super.requestUri(context);
    }

    String url = context.getCarrier().getURI().toASCIIString();
    int queryStart = url.indexOf('?');
    String querylessUrl = queryStart >= 0 ? url.substring(0, queryStart) : url;
    return KeyValue.of(HighCardinalityKeyNames.HTTP_URL, querylessUrl);
  }
}
