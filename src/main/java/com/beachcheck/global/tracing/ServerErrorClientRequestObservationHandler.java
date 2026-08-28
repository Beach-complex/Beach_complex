package com.beachcheck.global.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler.TracingContext;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.stereotype.Component;

/**
 * Why: Spring Framework 6.1의 RestClient가 5xx 예외를 observation 종료 후 기록해 client span이 UNSET으로 남는 동작을
 * 보정한다.
 *
 * <p>Policy: HTTP 5xx 응답만 span error로 기록하며, 요청 예외와 응답 처리에는 개입하지 않는다.
 *
 * <p>Contract(Input): 응답이 기록된 HTTP client observation context
 *
 * <p>Contract(Output): HTTP 5xx client span의 ERROR 상태
 *
 * <p>TODO: Spring Framework 6.2 이상으로 업그레이드할 때 제거 가능 여부를 검토한다.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ServerErrorClientRequestObservationHandler
    implements ObservationHandler<ClientRequestObservationContext> {

  private static final String SERVER_ERROR_MESSAGE = "HTTP client received a 5xx response";

  @Override
  public void onStop(ClientRequestObservationContext context) {
    if (!isServerError(context.getResponse())) {
      return;
    }

    TracingContext tracingContext = context.get(TracingContext.class);
    Span span = tracingContext == null ? null : tracingContext.getSpan();
    if (span != null) {
      span.error(new IllegalStateException(SERVER_ERROR_MESSAGE));
    }
  }

  @Override
  public boolean supportsContext(Observation.Context context) {
    return context instanceof ClientRequestObservationContext;
  }

  private boolean isServerError(ClientHttpResponse response) {
    if (response == null) {
      return false;
    }

    try {
      return response.getStatusCode().is5xxServerError();
    } catch (IOException ignored) {
      return false;
    }
  }
}
